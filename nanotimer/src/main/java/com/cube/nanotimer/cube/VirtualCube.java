package com.cube.nanotimer.cube;

import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.CubeRotation;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A 3D cube drawn in a WebView: the page ({@code assets/scramble/live.html}), the bridge it is
 * driven through, and nothing else. What the cube should be showing is its owner's business.
 *
 * <p>Pointed at a <em>state</em> rather than at a scramble, so any cube can be drawn whether or not
 * anybody knows the moves that made it (see {@link CubePatternFormat}), with turns riding on top of
 * that state as they are made. It follows the connected cube's gyro when asked, which is what makes
 * the one on the timer a mirror rather than a diagram.
 *
 * <p>The state and the turns since are kept here so the page can be handed them again: it is
 * reloaded on an error, and a WebView that has not run its document yet cannot be told anything.
 */
public class VirtualCube implements GyroReferenceListener {

  private static final CubeOrientation IDENTITY = new CubeOrientation(1, 0, 0, 0);

  public interface ReadyListener {
    /** The page has drawn a cube, which is when there is any point showing it. */
    void onCubeDrawn();
  }

  private static final String BASE_URL =
      "https://appassets.androidplatform.net/assets/scramble/live.html";

  /** Show the cube anyway if the page's "ready" never arrives, rather than hide it for good. */
  private static final long READY_TIMEOUT_MS = 6000;

  private final WebView webView;
  private final ReadyListener readyListener;

  /**
   * The grip the pose is measured from: the session's, taken once by {@link SmartCubeManager} and
   * shared with the frames the replay is spelled in, so the two never disagree.
   *
   * <p>⚠️ <b>Nothing here may take a reference of its own, or re-take this one.</b> Both were tried.
   * Re-anchoring redefines "however you are holding it right now" as square, so the cube on screen
   * snapped to white-top-green-front whatever was really in the hand — once at every seed, and later
   * once at every scramble's first move. The whole point of a mirror is that it does not do that.
   */
  private final GyroReference gyroReference = SmartCubeManager.INSTANCE.getGyroReference();

  /** The document has run, so {@code window.ntLive*} exist and may be called. */
  private boolean pageLoaded;
  private boolean drawn;
  private boolean destroyed;
  private boolean gyroWanted;
  private boolean gyroOn;
  /** How it stands while it is following nothing, or null for square. */
  private CubeOrientation hold;
  private boolean paused;

  /** What the cube is showing: the state it was pointed at, plus the turns made since. */
  private String pattern;
  private final List<String> movesSinceState = new ArrayList<String>();
  /** Which of its stickers keep their colour, or null for all of them. */
  private String stickering;
  /** How far back the camera stands, or 0 to leave the page on its own default. */
  private double cameraDistance;
  /** Whether the cube is drawn standing in a pool of shadow. */
  private boolean floor;
  /** Where the camera stands while there is no grip to follow, or null for square on. */
  private double[] view;
  /** How far below the middle of its box the cube sits, or null for the page's own default. */
  private Double nudge;

  /**
   * @param touchListener the host screen's own, forwarded so the cube is not a dead zone — a
   *     WebView swallows presses, and {@code CLAUDE.md} requires a tap anywhere in the timer to
   *     start or stop it. May be null where a press on the cube should do nothing.
   */
  public VirtualCube(WebView webView, View.OnTouchListener touchListener,
      ReadyListener readyListener) {
    this.webView = webView;
    this.readyListener = readyListener;
    // Subscribed here rather than left to each owner: the reference is taken a couple of seconds
    // after the cube connects, so a cube built before that read "no gyro" and stayed square for as
    // long as it lived — which is what a sheet opened on a fresh connection did until reopened.
    SmartCubeManager.INSTANCE.addGyroReferenceListener(this);
    setUp(touchListener);
  }

  /** The reference arrived, was re-taken, or went with the cube: the page follows it or stops. */
  @Override
  public void onGyroReferenceChanged() {
    refreshGyro();
  }

  /** Whether the page has drawn a cube. Until it has, showing the view only reserves empty space. */
  public boolean isDrawn() {
    return drawn;
  }

  /**
   * Points the cube at a state, in the form {@link CubePatternFormat} writes. Whatever was turned
   * since the last one is cleared: the state is the whole truth, not a correction to it.
   */
  public void setState(String pattern) {
    this.pattern = pattern;
    movesSinceState.clear();
    if (pattern != null) {
      evaluate("window.ntLiveState(" + JSONObject.quote(pattern) + ");");
    }
  }

  /** One turn, drawn turning at the speed a mirror needs: as fast as hands. */
  public void addMove(String notation) {
    addMove(notation, 0);
  }

  /**
   * One turn, drawn turning over {@code turnMs}, or at the mirror's own speed for 0. A screen that
   * steps through a sequence for somebody to watch wants a turn slow enough to see happen, which is
   * several times what a mirror can afford: the mirror must never be seen lagging the hands.
   *
   * <p>Per move rather than per screen, since the screen that steps is also mirroring: only the
   * stepped turns may be slow.
   */
  public void addMove(String notation, int turnMs) {
    movesSinceState.add(notation);
    // ⚠️ Quoted, never concatenated into a JS string literal: a prime move is spelled U', and the
    // apostrophe closes the literal and makes a syntax error of the whole call.
    evaluate("window.ntLiveMove(" + JSONObject.quote(notation) + "," + turnMs + ");");
  }

  /**
   * Which stickers keep their colour, as {@link CubeStickering} writes it. Kept apart from the
   * state: the two change on their own schedules, and a cube greyed mid-rep is still the same cube.
   */
  public void setStickering(String mask) {
    this.stickering = mask;
    if (mask != null) {
      evaluate("window.ntLiveStickering(" + JSONObject.quote(mask) + ");");
    }
  }

  /**
   * How far back the camera stands, which is how much of its box the cube fills. Smaller is closer.
   * The page's own default suits a small box; a screen that gives the cube a large well has to ask
   * for less, or the cube sits small in the middle of it.
   */
  public void setCameraDistance(double distance) {
    cameraDistance = distance;
    evaluate("window.ntLiveCamera(" + distance + ");");
  }

  /**
   * How far below the middle of its box the cube is drawn, as a percentage of the box height. The
   * page's default corrects a bias measured in the timer's box, which is short and wide; the same
   * percentage in a well twice as tall is twice the correction and lands the cube low. A screen
   * with a well of its own shape measures its own.
   */
  public void setNudge(double percent) {
    nudge = percent;
    evaluate("window.ntLiveNudge(" + percent + ");");
  }

  /**
   * Stands the cube in a pool of shadow, for a screen that gives it no surface of its own: without
   * one a cube on a bare mat reads as hovering. It is the same pool the scramble's diagram is drawn
   * in, so taking that diagram's place does not take the ground with it.
   *
   * <p>⚠️ Drawn for the page's <em>default</em> camera distance. A screen that moves the camera and
   * wants a floor too has to move the one in {@code live.html} with it.
   */
  public void setFloor(boolean floor) {
    this.floor = floor;
    evaluate("window.ntLiveFloor(" + floor + ");");
  }

  /**
   * Where the camera should stand while this cube has <em>no</em> grip to follow, in degrees:
   * latitude above the cube, or below it when negative, and longitude round it. A screen showing a
   * cube with no gyroscope has to give the user some way to see the other side of it, since nothing
   * that cube reports says how it is being held.
   *
   * <p>Held while there is a grip: a mirror drawn from anywhere but square on is not a mirror, so
   * the page keeps this until the gyro goes and puts it back the moment it does.
   */
  public void setView(double latitude, double longitude) {
    view = new double[] {latitude, longitude};
    evaluate("window.ntLiveView(" + latitude + "," + longitude + ");");
  }

  /**
   * How the cube stands while it has <em>no</em> grip to follow: a whole-cube rotation, in the
   * cube's own axes. Square unless a screen asks otherwise.
   *
   * <p>For a screen that writes its moves in a rotated frame. Standing the cube in that frame is
   * what makes the two agree: a move written {@code L} then turns the face the user is looking at
   * on the left. Held on the pose and never on the state, so nothing the cube reports undoes it.
   *
   * @param rotation the rotation to stand at, or null to stand square
   */
  public void setHold(CubeOrientation rotation) {
    hold = rotation;
    evaluate(holdCall());
  }

  private String holdCall() {
    CubeOrientation q = hold == null ? IDENTITY : hold;
    return "window.ntLiveHold(" + q.getW() + "," + q.getX() + "," + q.getY() + "," + q.getZ() + ");";
  }

  /** Whether this cube should follow the physical one's orientation, if there is one to follow. */
  public void setGyroFollowing(boolean following) {
    gyroWanted = following;
    refreshGyro();
  }

  /**
   * Starts or stops the page's render loop as the reference comes and goes.
   *
   * <p>A cube with no gyro must not leave that loop running for a pose that never changes: nothing
   * to follow, nothing to draw. Nor a cube whose screen is away, which is what {@code paused} is.
   */
  private void refreshGyro() {
    boolean on = gyroWanted && !paused && gyroReference.isSet();
    if (on != gyroOn) {
      gyroOn = on;
      evaluate("window.ntLiveGyro(" + gyroOn + ");");
    }
  }

  /**
   * ⚠️ <b>The page is told to paint again.</b> Android is free to drop what a WebView had drawn
   * while its screen was away, and a page that renders on demand never draws it back: the cube is
   * missing, its pool of shadow is not (that is CSS), and it stays missing until something happens
   * to turn it. Which is "sometimes the cube is gone when I come back to the timer".
   */
  public void onResume() {
    paused = false;
    webView.onResume();
    // Read afresh: the reference can have been taken, re-taken or lost while the screen was away.
    refreshGyro();
    evaluate("window.ntLiveRedraw();");
  }

  public void onPause() {
    paused = true;
    refreshGyro(); // stop the render loop, not just the readings
    webView.onPause(); // twisty-player keeps a WebGL context drawing otherwise
  }

  public void destroy() {
    SmartCubeManager.INSTANCE.removeGyroReferenceListener(this);
    webView.removeCallbacks(readyTimeout);
    ViewGroup parent = (ViewGroup) webView.getParent();
    if (parent != null) {
      parent.removeView(webView); // detach first: destroying in place can strand the renderer
    }
    webView.destroy();
    // The page went with it, so nothing may be evaluated and nothing has been drawn.
    pageLoaded = false;
    drawn = false;
    destroyed = true;
  }

  private void setUp(final View.OnTouchListener touchListener) {
    final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(webView.getContext()))
        .build();

    WebSettings settings = webView.getSettings();
    settings.setJavaScriptEnabled(true);
    webView.setBackgroundColor(Color.TRANSPARENT);
    if (touchListener != null) {
      // True, so the WebView's own gesture handling does not swallow the rest of the gesture.
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
        public boolean onConsoleMessage(ConsoleMessage message) {
          Log.d("VirtualCube", message.message() + " (line " + message.lineNumber() + ")");
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
        replay(); // the page starts empty; this is what puts a cube in it
        v.postDelayed(readyTimeout, READY_TIMEOUT_MS);
      }

      @Override
      public void onReceivedError(WebView v, WebResourceRequest request,
          androidx.webkit.WebResourceErrorCompat error) {
        if (request.isForMainFrame()) {
          pageLoaded = false;
          drawn = false;
          readyListener.onCubeDrawn(); // nothing can be drawn: let the owner take the space back
        }
      }
    });

    webView.loadUrl(BASE_URL);
  }

  /** Hands a fresh page everything it missed: the state, then whatever has been turned since. */
  private void replay() {
    // Before the state, so the first frame is drawn at the distance this screen asked for rather
    // than at the page's default and then jumping.
    if (cameraDistance > 0) {
      evaluate("window.ntLiveCamera(" + cameraDistance + ");");
    }
    if (view != null) {
      evaluate("window.ntLiveView(" + view[0] + "," + view[1] + ");");
    }
    if (nudge != null) {
      evaluate("window.ntLiveNudge(" + nudge + ");");
    }
    if (floor) {
      evaluate("window.ntLiveFloor(true);");
    }
    if (pattern == null) {
      return;
    }
    evaluate("window.ntLiveState(" + JSONObject.quote(pattern) + ");");
    if (stickering != null) {
      evaluate("window.ntLiveStickering(" + JSONObject.quote(stickering) + ");");
    }
    for (String move : movesSinceState) {
      evaluate("window.ntLiveMove(" + JSONObject.quote(move) + ");");
    }
    // Pushed rather than compared: the page is fresh and knows nothing of what was on before it.
    // The hold goes first, since ntLiveGyro(false) is what stands the cube at it.
    evaluate(holdCall());
    evaluate("window.ntLiveGyro(" + gyroOn + ");");
  }

  /** BuildConfig is not generated for this module, and the manifest flag says the same thing. */
  private boolean isDebuggable() {
    return (webView.getContext().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
  }

  private void evaluate(String js) {
    if (pageLoaded && !destroyed) {
      webView.evaluateJavascript(js, null);
    }
  }

  /** The page never said it had drawn. Show it regardless: an empty one is transparent anyway. */
  private final Runnable readyTimeout = new Runnable() {
    @Override
    public void run() {
      if (!drawn) {
        drawn = true;
        readyListener.onCubeDrawn();
      }
    }
  };

  /** Called from the page's own thread. */
  private final class Bridge {

    @JavascriptInterface
    public void onReady() {
      webView.post(new Runnable() {
        @Override
        public void run() {
          if (destroyed) {
            return; // this is the JS thread's message, and the cube may have gone since
          }
          webView.removeCallbacks(readyTimeout);
          drawn = true;
          readyListener.onCubeDrawn();
        }
      });
    }

    /**
     * How the cube is held, in its own axes, as {@code "w,x,y,z"} — or empty for a cube with no
     * gyro, no reading yet, or no reference to measure from.
     *
     * <p>Polled from the page's render loop rather than pushed, which is what
     * {@code SmartCube.getOrientation()} is documented for: a gyro reports slower than the screen
     * draws. How much slower differs by brand, so the page times the readings rather than assuming
     * a rate. Runs on the JS thread, and touches only volatile state.
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
