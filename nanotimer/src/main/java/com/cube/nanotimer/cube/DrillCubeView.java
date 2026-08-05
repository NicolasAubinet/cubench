package com.cube.nanotimer.cube;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.util.Log;
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
 * The case a drill is asking for, drawn on a cube the smart cube turns.
 *
 * <p>Deliberately not {@link LiveCubeView}. That one mirrors the cube in the user's hands, and can
 * only ever be pointed at solved because it seeds off the physical cube reporting itself so. A
 * drill points its cube at an arbitrary case and is allowed to, because a case is built by applying
 * a scramble to a solved cube and the page takes exactly that: an alg from solved.
 *
 * <p><b>It follows the grip, the same way the timer's mirror does</b>, off the same session
 * reference. The drill would be correct without it — a turn is reported against the cube's own
 * centres, so the check never needed a frame — but a cube pinned square shows one face and hides
 * five, and the user cannot turn it to look. Following the grip means the face under their right
 * hand is the face on the right of the screen, whatever way up they picked the cube up.
 *
 * <p>⚠️ <b>The reference is read, never taken and never re-taken here.</b> Re-anchoring redefines
 * "however you are holding it right now" as square, which would snap the cube straight mid-drill.
 * Filling an empty one is the activity's job, once, through {@code anchorGyroIfUnset}.
 *
 * <p>Nothing is sent to the page until there is a case to send: an empty alg would put a solved cube
 * on screen, and the first case would land over the top of it a moment later.
 */
public class DrillCubeView implements GyroReferenceListener {

  private static final String BASE_URL =
      "https://appassets.androidplatform.net/assets/scramble/live.html";

  /** Show the cube anyway if the page's "ready" never arrives, rather than hide it for good. */
  private static final long READY_TIMEOUT_MS = 6000;

  /** Told when there is something on screen worth showing, so the caller can drop its spinner. */
  public interface ReadyListener {
    void onCubeReady();
  }

  private final Context context;
  private final ReadyListener readyListener;

  private WebView webView;
  /** The document has run, so {@code window.ntLive*} exist and may be called. */
  private boolean pageLoaded;
  private boolean pageReady;

  /** The case's scramble, plus whatever has been turned since. Null until there is a case. */
  private String setupAlg;

  /** The session's grip, shared with the timer's mirror so the two never disagree. */
  private final GyroReference gyroReference = SmartCubeManager.INSTANCE.getGyroReference();
  /** Whether the page is following the orientation, which it only does with a reference. */
  private boolean gyroOn;
  private final List<String> movesSinceSetup = new ArrayList<String>();

  public DrillCubeView(Context context, ReadyListener readyListener) {
    this.context = context;
    this.readyListener = readyListener;
  }

  /** @return false if there is no WebView on this device, which leaves the drill unrunnable */
  public boolean bind(WebView webView) {
    this.webView = webView;
    try {
      setUpWebView();
      return true;
    } catch (Throwable t) {
      // e.g. no WebView implementation installed. The caller says so; the drill cannot go on
      // without a cube to look at, since the cube is the whole of what a drill shows.
      Log.w("DrillCube", "could not set up the drill cube", t);
      this.webView = null;
      return false;
    }
  }

  /**
   * Points the cube at a case, given as the alg that sets it up from solved. Safe before the page
   * is up: the case is held and sent as soon as there is somewhere to send it.
   */
  public void show(String setupAlg) {
    this.setupAlg = setupAlg;
    movesSinceSetup.clear();
    load();
  }

  /** One quarter turn, as the cube reported it. */
  public void move(String notation) {
    movesSinceSetup.add(notation);
    evaluate("window.ntLiveMove(" + JSONObject.quote(notation) + ");");
  }

  public void onResume() {
    SmartCubeManager.INSTANCE.addGyroReferenceListener(this);
    if (webView != null) {
      webView.onResume();
      // Read afresh: the reference can have been taken or lost while the screen was away.
      gyroOn = gyroReference.isSet();
      evaluate("window.ntLiveGyro(" + gyroOn + ");");
    }
  }

  public void onPause() {
    SmartCubeManager.INSTANCE.removeGyroReferenceListener(this);
    if (webView != null) {
      evaluate("window.ntLiveGyro(false);"); // stop the render loop, not just the readings
      webView.onPause(); // twisty-player keeps a WebGL context drawing otherwise
    }
  }

  /** Whether the cube on screen is turning with the physical one, or standing square. */
  public boolean isFollowingGrip() {
    return gyroReference.isSet();
  }

  /** The reference arrived or went with the cube: the page follows it or stops. */
  @Override
  public void onGyroReferenceChanged() {
    boolean on = gyroReference.isSet();
    if (on != gyroOn) {
      gyroOn = on;
      evaluate("window.ntLiveGyro(" + gyroOn + ");");
    }
  }

  public void destroy() {
    if (webView != null) {
      webView.removeCallbacks(readyTimeout);
      android.view.ViewGroup parent = (android.view.ViewGroup) webView.getParent();
      if (parent != null) {
        parent.removeView(webView); // detach first: destroying in place can strand the renderer
      }
      webView.destroy();
      webView = null;
    }
    pageLoaded = false;
    pageReady = false;
  }

  /** ⚠️ Gated on the document having run, never on ready: ready is what this call brings about. */
  private void load() {
    if (!pageLoaded || setupAlg == null) {
      return; // onPageFinished sends it instead, and Java holds the whole state
    }
    // ⚠️ Quoted, never concatenated: a prime move is spelled U' and the apostrophe would close the
    // JS string literal and make a syntax error of the call.
    evaluate("window.ntLiveReset(" + JSONObject.quote(setupAlg) + ");");
    for (String move : movesSinceSetup) {
      evaluate("window.ntLiveMove(" + JSONObject.quote(move) + ");");
    }
    // Pushed rather than compared: a fresh page knows nothing of what was on before it.
    gyroOn = gyroReference.isSet();
    evaluate("window.ntLiveGyro(" + gyroOn + ");");
  }

  private void setUpWebView() {
    final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(context))
        .build();

    WebSettings settings = webView.getSettings();
    settings.setJavaScriptEnabled(true);
    webView.setBackgroundColor(Color.TRANSPARENT);
    webView.setFocusable(false);
    webView.setFocusableInTouchMode(false);

    webView.addJavascriptInterface(new Bridge(), "NTBridge");
    if (isDebuggable()) {
      WebView.setWebContentsDebuggingEnabled(true);
      webView.setWebChromeClient(new WebChromeClient() {
        @Override
        public boolean onConsoleMessage(android.webkit.ConsoleMessage message) {
          Log.d("DrillCube", message.message() + " (line " + message.lineNumber() + ")");
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
    });

    webView.loadUrl(BASE_URL);
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

  /** The page never said it had drawn. Go on regardless, rather than hold the screen for good. */
  private final Runnable readyTimeout = new Runnable() {
    @Override
    public void run() {
      markReady();
    }
  };

  private void markReady() {
    if (pageReady) {
      return;
    }
    pageReady = true;
    if (webView != null) {
      webView.removeCallbacks(readyTimeout);
    }
    if (readyListener != null) {
      readyListener.onCubeReady();
    }
  }

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
          if (webView != null) {
            markReady();
          }
        }
      });
    }

    /**
     * How the cube is held, in its own axes, as {@code "w,x,y,z"} — or empty for a cube with no
     * gyro, no reading yet, or no reference to measure from.
     *
     * <p>Polled from the page's render loop rather than pushed, which is what
     * {@code SmartCube.getOrientation()} is documented for. Runs on the JS thread, and touches
     * only volatile state.
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
