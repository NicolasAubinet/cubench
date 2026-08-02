package com.cube.nanotimer.cube;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.os.SystemClock;
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
public class LiveCubeView implements CubeConnectionListener, CubeMoveListener, CubeStateListener {

  private static final String BASE_URL =
      "https://appassets.androidplatform.net/assets/scramble/live.html";

  /** Beyond this many moves since the seed, the alg is folded back into the setup (see compact). */
  private static final int COMPACT_AFTER_MOVES = 60;

  /** How wrong the mirror looks when it knows it is wrong. */
  private static final float DESYNCED_ALPHA = 0.3f;

  /** Show the cube anyway if the page's "ready" never arrives, rather than hide it for good. */
  private static final long READY_TIMEOUT_MS = 6000;

  /** Two gyro periods, so each tick of {@link #anchorWhenStill} sees a genuinely new reading. */
  private static final long STILL_POLL_MS = 100;

  /** How far the cube may drift between two ticks and still count as held still. */
  private static final double STILL_DEGREES = 8.0;

  /** Long enough for a slice to finish, short enough not to leave a fresh cube unanchored. */
  private static final long STILL_TIMEOUT_MS = 2000;

  private final Context context;
  private final View.OnTouchListener touchListener;

  private ViewStub stub;
  private View topSpacer;
  private View cubeLayout;
  private WebView webView;
  /** The document has run, so {@code window.ntLive*} exist and may be called. */
  private boolean pageLoaded;
  /** The page says it has drawn a cube, which is when there is any point showing it. */
  private boolean pageReady;
  private boolean suppressed;

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
   * The grip the cube was in when it was last seeded: that is what square means on screen, and
   * every pose is measured from it.
   *
   * <p>Its own rather than the solve's shared {@link GyroReference} (§6.5), because it answers a
   * different question — the solve's reference names the grip a scramble was followed in and must
   * not move, while this one re-anchors every time the cube comes back to solved. Volatile because
   * the page polls it from its own thread.
   */
  private volatile CubeOrientation reference;

  /** Whether the page is following the orientation, which it only does with a reference to follow. */
  private boolean gyroOn;

  /** The previous tick's reading, and when the wait began: see {@link #anchorWhenStill}. */
  private CubeOrientation stillCandidate;
  private long stillSince;

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
   * @param stub the placeholder to inflate the cube into, or null where the layout has none
   * @param topSpacer the gap the cube stands in for, hidden while it is up. Null where the layout
   *     keeps no such gap (landscape, which gives the cube a fixed height instead).
   */
  public void bind(ViewStub stub, View topSpacer) {
    this.stub = stub;
    this.topSpacer = topSpacer;
  }

  public void start() {
    SmartCubeManager.INSTANCE.addConnectionListener(this); // replays the connection at once
    SmartCubeManager.INSTANCE.addMoveListener(this);
    SmartCubeManager.INSTANCE.addStateListener(this); // and the current state, which seeds it
    if (webView != null) {
      webView.onResume();
      evaluate("window.ntLiveGyro(" + gyroOn + ");");
    }
  }

  public void stop() {
    SmartCubeManager.INSTANCE.removeConnectionListener(this);
    SmartCubeManager.INSTANCE.removeMoveListener(this);
    SmartCubeManager.INSTANCE.removeStateListener(this);
    if (webView != null) {
      evaluate("window.ntLiveGyro(false);"); // stop the render loop, not just the readings
      webView.onPause(); // twisty-player keeps a WebGL context drawing otherwise
    }
  }

  public void destroy() {
    if (webView != null) {
      webView.removeCallbacks(readyTimeout);
      webView.removeCallbacks(anchorWhenStill);
      ViewGroup parent = (ViewGroup) webView.getParent();
      if (parent != null) {
        parent.removeView(webView); // detach first: destroying in place can strand the renderer
      }
      webView.destroy();
      webView = null;
    }
    cubeLayout = null;
    stub = null;
  }

  /** Force-hide the cube regardless of the connection. */
  public void setSuppressed(boolean suppressed) {
    this.suppressed = suppressed;
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
      // A cube that connects already solved never reports a change, so seed off what it holds.
      CubeState state = SmartCubeManager.INSTANCE.getCurrentState();
      if (state != null && state.isSolved()) {
        seed();
      } else {
        // Not a state the cube can be pointed at, so the stickers must wait for a solved one — but
        // the pose need not. Anchored here, the cube follows the hands from the moment it appears
        // rather than sitting dim and dead until the first turn or the resync button.
        anchor();
      }
    }
    refresh();
  }

  @Override
  public void onMove(CubeMove move) {
    twin.applyMove(move.getFace(), move.isPrime());
    movesSinceSeed.add(move.getNotation());
    evaluate("window.ntLiveMove(" + JSONObject.quote(move.getNotation()) + ");");
    if (reference == null) {
      // The seed found no reading — the first ones can arrive after the connection. Take the grip
      // the cube is being turned in now instead, rather than never following it at all.
      anchor();
    }
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
    twin.fromFacelet(CubieCube.SOLVED_FACELET);
    baseAlg = "";
    movesSinceSeed.clear();
    // ⚠️ The old reference stands until anchorWhenStill has a still one: clearing it here left the
    // cube dead square in the hand for the whole of the wait.
    anchor();
    inSync = true;
    seeded = true;
    load();
    refresh();
  }

  /**
   * However the cube is being held now is what square means from here on, so a peek reads as a peek
   * rather than as the grip the last solve happened to end in.
   *
   * <p>⚠️ <b>Deliberately NOT uprighted</b>, unlike every other anchor in the app — and this is the
   * one place that is right. Uprighting squares a reading up to the nearest of the 24 so the grip
   * can be <em>named</em>; the tilt it removes is then shown as the pose, which is what a replay
   * wants (§6.7). A mirror has no name to spell: it is asked to match a cube in the hand right now,
   * so the anchor must read as exactly square at the moment it is taken. Uprighted, pressing "my
   * cube is solved" left the cube on screen sitting a few degrees off — the tilt of the grip it was
   * anchored in.
   *
   * <p>Which grip that is, though, is decided by {@link #anchorWhenStill} rather than read here:
   * "now" is often the middle of a turn.
   */
  private void anchor() {
    CubeOrientation reading = SmartCubeManager.INSTANCE.getOrientation();
    if (reading == null) {
      return; // no gyro on this cube, or nothing read yet: the cube on screen simply stays square
    }
    if (webView == null) {
      setReference(reading); // nothing to poll on, and nothing on screen to be wrong
      return;
    }
    stillCandidate = null;
    stillSince = SystemClock.uptimeMillis();
    webView.removeCallbacks(anchorWhenStill);
    webView.post(anchorWhenStill);
  }

  /**
   * Takes the reference off the first reading with the cube <em>at rest</em>, not off whatever it
   * reads right now.
   *
   * <p>⚠️ <b>This is the whole of the fast-slice bug.</b> A seed fires on the state packet, and the
   * cube calls a turn done the moment it registers the last quarter turn — measured on hardware,
   * with 25° to 110° of core rotation still to come. Anchoring there pins the frame to a spinning
   * core, and since a reference stands until the next seed, the cube stays that far out for good:
   * moving it does nothing (the frame is wrong, not the reading), and only re-solving it fixes it,
   * and then only if that seed happens to land on a still cube.
   *
   * <p>Polled at 100 ms, which is two gyro periods, so every tick is a genuinely new sample rather
   * than the same one read twice — that would read as perfectly still and is the trap this is
   * shaped around. A hand turning the cube over moves it well under {@code STILL_DEGREES} in that
   * time; a core mid-slice moves an order of magnitude more.
   */
  private final Runnable anchorWhenStill = new Runnable() {
    @Override
    public void run() {
      CubeOrientation reading = SmartCubeManager.INSTANCE.getOrientation();
      if (reading == null || webView == null) {
        return;
      }
      boolean still =
          stillCandidate != null && stillCandidate.angleToDegrees(reading) < STILL_DEGREES;
      stillCandidate = reading;
      if (still) {
        setReference(reading);
      } else if (SystemClock.uptimeMillis() - stillSince < STILL_TIMEOUT_MS) {
        webView.postDelayed(this, STILL_POLL_MS);
      } else if (reference == null) {
        // Never installed over a reference that already works: a cube that is simply never held
        // still is better followed from a stale frame than from a knowingly mid-turn one.
        setReference(reading);
      }
    }
  };

  private void setReference(CubeOrientation reading) {
    reference = reading;
    if (!gyroOn) {
      gyroOn = true;
      evaluate("window.ntLiveGyro(true);");
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
    // A cube with no gyro must not leave the page's render loop running for a pose that never
    // changes: nothing to follow, nothing to draw.
    gyroOn = reference != null;
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
      setUpWebView();
    } catch (Throwable t) {
      // e.g. no WebView implementation installed. Said out loud: swallowed, this is a feature that
      // simply never appears and gives nobody a thread to pull.
      Log.w("LiveCube", "could not inflate the live cube", t);
      webView = null;
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
    boolean connected = !suppressed && SmartCubeManager.INSTANCE.isConnected();
    boolean visible = connected && pageReady && seeded;
    cubeLayout.setVisibility(visible ? View.VISIBLE : (connected ? View.INVISIBLE : View.GONE));
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
      CubeOrientation pose =
          CubeRotation.continuousFrame(reference, SmartCubeManager.INSTANCE.getOrientation());
      return pose == null ? ""
          : pose.getW() + "," + pose.getX() + "," + pose.getY() + "," + pose.getZ();
    }
  }
}
