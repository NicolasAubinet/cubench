package com.cube.nanotimer.cube;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import com.cube.nanotimer.R;
import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeConnection;
import com.cube.nanotimer.smartcube.model.CubeConnectionListener;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeMoveListener;
import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.CubeStateListener;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The connected smart cube, mirrored on screen as it is turned: a 3D cube in a WebView, fed the
 * move stream and the physical orientation.
 *
 * <p>Inflated from a {@link ViewStub} only once a cube is connected, so a user without one pays
 * neither the WebView nor the ~1 MB cubing.js parse — the same rule the app-bar chip follows.
 * Bind once, then {@link #start()} / {@link #stop()} from the activity's resume/pause and
 * {@link #destroy()} from its onDestroy.
 *
 * <p><b>⚠️ The pose is the RAW gyro reading, and a slice must not be taken out of it.</b> The cube
 * reports a slice as its two opposite faces and nothing else, so the state drawn here is the one the
 * core sees — and the core is where the gyro sits. Drawing a core-frame state at the core's own pose
 * is what makes the two agree: {@code pair · spin = slice} is satisfied by the pose itself. Taking
 * the spin out was tried, and it is a real correction to the wrong quantity — it recovers the
 * <em>shell's</em> pose (measured over the {@code roux140} capture's 19 confirmed slices: 7.1° of
 * residual against 92.8° uncorrected), which would only pair with a shell-frame state, which this
 * is not.
 *
 * <p><b>The player takes an alg, never facelets</b>, so the mirror cannot be pointed at an
 * arbitrary state: it seeds whenever the cube reports itself <em>solved</em> and rides the move
 * stream from there. That is no restriction in practice, because the timer already refuses to arm
 * until the cube is solved. Should a move be missed, the twin below notices (the cube sends its
 * whole state after every turn) and the cube on screen dims rather than lying, until the next
 * solved state re-seeds it.
 */
public class LiveCubeView
    implements CubeConnectionListener, CubeMoveListener, CubeStateListener, GyroReferenceListener {

  private static final String BASE_URL =
      "https://appassets.androidplatform.net/assets/scramble/live.html";

  /** Beyond this many moves since the seed, the alg is folded back into the setup (see compact). */
  private static final int COMPACT_AFTER_MOVES = 60;

  /** How wrong the mirror looks when it knows it is wrong. */
  private static final float DESYNCED_ALPHA = 0.3f;

  /** Show the cube anyway if the page's "ready" never arrives, rather than hide it for good. */
  private static final long READY_TIMEOUT_MS = 6000;

  private final Context context;
  private final View.OnTouchListener touchListener;

  private ViewStub stub;
  private View topSpacer;
  private View cubeLayout;
  private View veil;
  private WebView webView;
  /** The document has run, so {@code window.ntLive*} exist and may be called. */
  private boolean pageLoaded;
  /** The page says it has drawn a cube, which is when there is any point showing it. */
  private boolean pageReady;
  private boolean obscured;

  /** What the cube on screen is showing: the setup it was seeded with, plus the moves since. */
  private String baseAlg = "";
  private final List<String> movesSinceSeed = new ArrayList<String>();

  /** The same cube, turned in Java, so a lost move can be seen rather than silently drawn wrong. */
  private final CubieCube twin = new CubieCube();
  private boolean inSync;

  /**
   * Whether the cube on screen has ever been pointed at a state the physical one was really in.
   *
   * <p>Until it has, there is nothing to show and nothing to dim: entering the timer with a
   * scrambled cube used to draw a solved one at a third brightness, which is honest but reads as a
   * grey film over the whole thing and lasts until the next solve. Showing nothing says the same
   * thing without saying it in grey.
   */
  private boolean seeded;

  /**
   * The grip the pose is measured from: the session's, taken once by {@link SmartCubeManager} and
   * shared with the frames the replay is spelled in, so the two never disagree.
   *
   * <p>⚠️ <b>This view must not take a reference of its own, and nothing here may re-take this
   * one.</b> Both were tried. Re-anchoring redefines "however you are holding it right now" as
   * square, so the cube on screen snapped to white-top-green-front whatever was really in the hand —
   * once at every seed, and later once at every scramble's first move. The whole point of a mirror
   * is that it does not do that. Volatile inside, because the page polls it from its own thread.
   */
  private final GyroReference gyroReference = SmartCubeManager.INSTANCE.getGyroReference();

  /** Whether the page is following the orientation, which it only does with a reference to follow. */
  private boolean gyroOn;

  /**
   * @param touchListener the timer screen's own, forwarded so the cube is not a dead zone —
   *     {@code CLAUDE.md} requires a tap anywhere in the timer to start or stop it, and a WebView
   *     swallows presses. May be null outside the timer.
   */
  public LiveCubeView(Context context, View.OnTouchListener touchListener) {
    this.context = context;
    this.touchListener = touchListener;
  }

  /**
   * Points the cube at a freshly laid-out screen. Safe to call again, and must be: the timer takes
   * its own configuration changes ({@code configChanges="orientation|screenSize"}) and rebuilds its
   * content view by hand, so a rotation hands over an entirely new stub.
   *
   * <p>⚠️ Whatever was inflated into the <em>old</em> layout is torn down here. It is off the window
   * the moment {@code setContentView} runs, but it is not dead: it holds a WebGL context and its
   * page goes on polling the bridge for the life of the activity. Left in place it also blocks
   * {@link #inflate}, so the cube never comes back and {@link #refresh} drives the orphan while
   * hiding the new spacer — a gap on screen where the cube should be. Nothing is lost by rebuilding:
   * Java holds the whole state and the page is reloaded from it.
   *
   * @param stub the placeholder to inflate the cube into, or null where the layout has none
   * @param topSpacer the gap the cube stands in for, hidden while it is up, or null where the
   *     layout keeps no such gap
   */
  public void bind(ViewStub stub, View topSpacer) {
    boolean relaidOut = webView != null;
    if (relaidOut) {
      destroy();
    }
    this.stub = stub;
    this.topSpacer = topSpacer;
    if (relaidOut && SmartCubeManager.INSTANCE.isConnected()) {
      inflate();
      refresh();
    }
  }

  public void start() {
    SmartCubeManager.INSTANCE.addConnectionListener(this); // replays the connection at once
    SmartCubeManager.INSTANCE.addMoveListener(this);
    SmartCubeManager.INSTANCE.addStateListener(this); // and the current state, which seeds it
    SmartCubeManager.INSTANCE.addGyroReferenceListener(this);
    if (webView != null) {
      webView.onResume();
      // Read afresh: the reference can have been taken, re-taken or lost while the screen was away.
      gyroOn = gyroReference.isSet();
      evaluate("window.ntLiveGyro(" + gyroOn + ");");
    }
  }

  public void stop() {
    SmartCubeManager.INSTANCE.removeConnectionListener(this);
    SmartCubeManager.INSTANCE.removeMoveListener(this);
    SmartCubeManager.INSTANCE.removeStateListener(this);
    SmartCubeManager.INSTANCE.removeGyroReferenceListener(this);
    if (webView != null) {
      evaluate("window.ntLiveGyro(false);"); // stop the render loop, not just the readings
      webView.onPause(); // twisty-player keeps a WebGL context drawing otherwise
    }
  }

  public void destroy() {
    if (webView != null) {
      webView.removeCallbacks(readyTimeout);
      ViewGroup parent = (ViewGroup) webView.getParent();
      if (parent != null) {
        parent.removeView(webView); // detach first: destroying in place can strand the renderer
      }
      webView.destroy();
      webView = null;
    }
    // The page went with it, so nothing may be evaluated and nothing has been drawn. Left true,
    // a rebuild would think the new page was already up and send it nothing.
    pageLoaded = false;
    pageReady = false;
    cubeLayout = null;
    veil = null;
    stub = null;
  }

  /**
   * Veils the cube: it keeps its place on screen, under a cover that says why it cannot be read.
   *
   * <p>Taking it off screen instead was what this used to do, and it cost the timer its layout —
   * the spacer came back, everything below it moved, and the screen shifted twice per blind
   * attempt. A cover also answers the question the empty space raised, which is why the cube went.
   */
  public void setObscured(boolean obscured) {
    this.obscured = obscured;
    refresh();
  }

  @Override
  public void onConnection(CubeConnection connection) {
    if (!SmartCubeManager.INSTANCE.isConnected()) {
      // The cube was turned freely while it was away, so what is held here is only the state it
      // was last seen in. Forgetting it is what stops the next connection opening on a confident
      // full-brightness cube that happens to be a solve out of date.
      seeded = false;
    } else {
      inflate();
      // A cube that connects already solved never reports a change, so seed off what it holds. A
      // state that is not solved cannot be pointed at, so the stickers wait for one that is; the
      // pose does not wait, since the connection anchors the reference either way.
      CubeState state = SmartCubeManager.INSTANCE.getCurrentState();
      if (state != null && state.isSolved()) {
        seed();
      }
    }
    refresh();
  }

  @Override
  public void onMove(CubeMove move) {
    twin.applyMove(move.getFace(), move.isPrime());
    movesSinceSeed.add(move.getNotation());
    evaluate("window.ntLiveMove(" + JSONObject.quote(move.getNotation()) + ");");
  }

  /** The reference arrived, was re-taken, or went with the cube: the page follows it or stops. */
  @Override
  public void onGyroReferenceChanged() {
    refreshGyro();
  }

  @Override
  public void onState(CubeState state) {
    if (state.isSolved()) {
      seed(); // the one state the player can be pointed at, and the natural moment to compact
      return;
    }
    boolean agrees = !seeded || twin.toFaceCube().equals(state.getFacelets());
    if (agrees != inSync) {
      inSync = agrees;
      refresh();
    }
    if (inSync && movesSinceSeed.size() >= COMPACT_AFTER_MOVES) {
      compact();
    }
  }

  /** Points the cube on screen at solved, which is where the physical one is. */
  private void seed() {
    // Already showing exactly this, with nothing turned since: handing the page the same alg again
    // only makes the player rebuild for nothing, and a rebuild is what the pose write has to
    // survive (see poseStale in live.html).
    boolean alreadyShown = seeded && inSync && baseAlg.isEmpty() && movesSinceSeed.isEmpty();
    twin.fromFacelet(CubieCube.SOLVED_FACELET);
    baseAlg = "";
    movesSinceSeed.clear();
    // ⚠️ Seeding the STATE does not re-take the reference, and must not. Re-anchoring here is what
    // made the cube snap to white-top-green-front the instant a solve finished, whatever was really
    // in the hand: the orientation was right for the whole solve and wrong from the moment it ended.
    inSync = true;
    seeded = true;
    if (!alreadyShown) {
      load();
      refresh();
    }
  }

  /**
   * Starts or stops the page's render loop as the reference comes and goes.
   *
   * <p>A cube with no gyro must not leave the loop running for a pose that never changes: nothing
   * to follow, nothing to draw.
   */
  private void refreshGyro() {
    boolean on = gyroReference.isSet();
    if (on != gyroOn) {
      gyroOn = on;
      evaluate("window.ntLiveGyro(" + gyroOn + ");");
    }
  }

  /**
   * Folds the moves so far into the setup alg. The animated alg grows for as long as the cube goes
   * unsolved, and everything the player derives is derived over the whole of it; a session spent
   * turning without ever solving would otherwise grow without bound. Nothing moves on screen: the
   * state either side of a compaction is the same state.
   */
  private void compact() {
    StringBuilder sb = new StringBuilder(baseAlg);
    for (String move : movesSinceSeed) {
      if (sb.length() > 0) {
        sb.append(' ');
      }
      sb.append(move);
    }
    baseAlg = sb.toString();
    movesSinceSeed.clear();
    load();
  }

  /**
   * Hands the whole state over: the setup alg, then whatever has been turned since.
   *
   * <p>⚠️ Gated on the document having run, <b>never</b> on the page having signalled ready. The
   * page only signals once it has been given a cube to draw, so waiting for that here is a deadlock:
   * this is the call that would have caused it.
   */
  private void load() {
    if (!pageLoaded) {
      return; // onPageFinished sends it instead — Java holds the whole state, so nothing is lost
    }
    // ⚠️ Quoted, never concatenated into a JS string literal: a prime move is spelled U', and the
    // apostrophe closes the literal and makes a syntax error of the whole call.
    evaluate("window.ntLiveReset(" + JSONObject.quote(baseAlg) + ");");
    for (String move : movesSinceSeed) {
      evaluate("window.ntLiveMove(" + JSONObject.quote(move) + ");");
    }
    // Pushed rather than compared: the page is fresh and knows nothing of what was on before it.
    gyroOn = gyroReference.isSet();
    evaluate("window.ntLiveGyro(" + gyroOn + ");");
  }

  private void inflate() {
    if (webView != null || stub == null) {
      return;
    }
    try {
      // ⚠️ Do NOT scale this subtree (ScalingLinearLayout.scaleLateSubtree). A late subtree needs
      // it, but this one is not late: the stub was there for the one scaling pass, its height was
      // scaled then, and inflate() hands that same params object to the view it puts in its place.
      // Scaling again would square the factor — 200px becomes a whole screen on a 1080 phone.
      cubeLayout = stub.inflate();
      stub = null;
      webView = cubeLayout.findViewById(R.id.wvLiveCube);
      veil = cubeLayout.findViewById(R.id.liveCubeVeil);
      setUpWebView();
    } catch (Throwable t) {
      // e.g. no WebView implementation installed. Said out loud: swallowed, this is a feature that
      // simply never appears and gives nobody a thread to pull.
      Log.w("LiveCube", "could not inflate the live cube", t);
      // Both, not just the WebView: a layout left behind here is never drawn into, but refresh
      // would still reserve its space and hide the spacer — a gap with nothing in it, for good.
      webView = null;
      cubeLayout = null;
      veil = null;
    }
  }

  private void setUpWebView() {
    final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(context))
        .build();

    WebSettings settings = webView.getSettings();
    settings.setJavaScriptEnabled(true);
    webView.setBackgroundColor(Color.TRANSPARENT);
    // The timer screen has no dead zones: a press goes where a press anywhere else would go, and
    // true stops the WebView's own gesture handling from swallowing the rest of the gesture.
    if (touchListener != null) {
      webView.setOnTouchListener(new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent event) {
          touchListener.onTouch(view, event);
          return true;
        }
      });
    }
    webView.setFocusable(false); // the space bar starts the timer; focus here would eat it
    webView.setFocusableInTouchMode(false);

    webView.addJavascriptInterface(new Bridge(), "NTBridge");
    if (isDebuggable()) {
      WebView.setWebContentsDebuggingEnabled(true);
      webView.setWebChromeClient(new WebChromeClient() {
        @Override
        public boolean onConsoleMessage(android.webkit.ConsoleMessage message) {
          Log.d("LiveCube", message.message() + " (line " + message.lineNumber() + ")");
          return true;
        }
      });
    }

    webView.setWebViewClient(new WebViewClientCompat() {
      @Override
      public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest request) {
        return assetLoader.shouldInterceptRequest(request.getUrl());
      }

      @Override
      public void onPageFinished(WebView v, String url) {
        pageLoaded = true;
        load(); // the page starts empty; this is what puts a cube in it
        v.postDelayed(readyTimeout, READY_TIMEOUT_MS);
      }

      @Override
      public void onReceivedError(WebView v, WebResourceRequest request,
          androidx.webkit.WebResourceErrorCompat error) {
        if (request.isForMainFrame()) {
          pageLoaded = false;
          pageReady = false;
          refresh(); // nothing can be drawn: take the space back rather than leave a hole
        }
      }
    });

    webView.loadUrl(BASE_URL);
  }

  /**
   * Shown only with a cube connected, and dimmed when it knows it is wrong.
   *
   * <p>⚠️ <b>INVISIBLE while the page comes up, never GONE.</b> A GONE WebView is never laid out,
   * so the player would be built into a 0×0 viewport and stay that size once shown — space on
   * screen with nothing in it. INVISIBLE gives the page its real size a beat before it has anything
   * to draw, which costs a moment of reserved space and is the reason the layout jump is small.
   */
  private void refresh() {
    if (cubeLayout == null) {
      return;
    }
    boolean connected = SmartCubeManager.INSTANCE.isConnected();
    // Veiled counts as shown: the cover is what the space is for, and it is over the cube whether
    // or not there is yet a cube under it.
    boolean visible = connected && (obscured || (pageReady && seeded));
    cubeLayout.setVisibility(visible ? View.VISIBLE : (connected ? View.INVISIBLE : View.GONE));
    if (veil != null) {
      veil.setVisibility(obscured ? View.VISIBLE : View.GONE);
    }
    if (topSpacer != null) {
      // The cube stands in the gap rather than above it: both weighted the same, so showing both
      // pushed the timer down and left the cube marooned at the top of the screen.
      topSpacer.setVisibility(connected ? View.GONE : View.VISIBLE);
    }
    if (webView != null) {
      webView.setAlpha(inSync ? 1f : DESYNCED_ALPHA);
    }
  }

  /** BuildConfig is not generated for this module, and the manifest flag says the same thing. */
  private boolean isDebuggable() {
    return (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
  }

  private void evaluate(String js) {
    if (webView != null && pageLoaded) {
      webView.evaluateJavascript(js, null);
    }
  }

  /** The page never said it had drawn. Show it regardless: an empty one is transparent anyway. */
  private final Runnable readyTimeout = new Runnable() {
    @Override
    public void run() {
      if (!pageReady) {
        pageReady = true;
        refresh();
      }
    }
  };

  /** Called from the page's own thread. */
  private final class Bridge {

    @JavascriptInterface
    public void onReady() {
      WebView view = webView; // read once: this is the JS thread, destroy() nulls it
      if (view == null) {
        return;
      }
      view.post(new Runnable() {
        @Override
        public void run() {
          if (webView == null) {
            return;
          }
          webView.removeCallbacks(readyTimeout);
          pageReady = true;
          refresh();
        }
      });
    }

    /**
     * How the cube is held, in its own axes, as {@code "w,x,y,z"} — or empty for a cube with no
     * gyro, no reading yet, or no reference to measure from.
     *
     * <p>Polled from the page's render loop rather than pushed, which is what
     * {@code SmartCube.getOrientation()} is documented for: the gyro runs at ~20 Hz, faster than
     * any consumer needs. Runs on the JS thread, and touches only volatile state.
     */
    @JavascriptInterface
    public String orientation() {
      CubeOrientation pose = CubeRotation.continuousFrame(
          gyroReference.get(), SmartCubeManager.INSTANCE.getOrientation());
      return pose == null ? ""
          : pose.getW() + "," + pose.getX() + "," + pose.getY() + "," + pose.getZ();
    }
  }
}
