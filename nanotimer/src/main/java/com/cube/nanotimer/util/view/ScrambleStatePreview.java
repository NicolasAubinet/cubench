package com.cube.nanotimer.util.view;

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
import android.widget.LinearLayout;

import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import com.cube.nanotimer.R;
import com.cube.nanotimer.util.ScrambleViewNotation;
import com.cube.nanotimer.vo.CubeType;

import org.json.JSONObject;

/**
 * The scramble drawn as the state it leaves the puzzle in, in the gap above the timer.
 *
 * <p>The same page the scramble dialog opens on demand ({@code assets/scramble/scramble.html}),
 * mounted instead of opened: the whole Java&rarr;JS surface is still one
 * {@code ntRender(key, scramble)} call, and a new scramble is that call again rather than a new
 * page. Bind once, then {@link #show} whenever the scramble changes.
 *
 * <p>It draws in the gap under the scramble, next to the moves it is a picture of. The gap is a
 * share of what the scramble and the card leave, never a height of its own, so nothing here can
 * push the scramble into the card below it; and it only takes that share while there is something
 * to put in it, so a puzzle that cannot be drawn leaves exactly the screen that was there before.
 * See {@link #balance}.
 *
 * <p>That gap is shared with the live cube, which draws in it instead whenever one is connected:
 * see {@link #setReplaced}.
 *
 * <p><b>Never for a blind solve type.</b> Not "not useful there": a picture of the scrambled state
 * is the memorisation, straight off the screen, without touching the cube. Hiding it is not enough,
 * so it is never inflated — see {@link #show}.
 */
public class ScrambleStatePreview {

  private static final String BASE_URL =
      "https://appassets.androidplatform.net/assets/scramble/scramble.html";

  /** Show it anyway if the page's "rendered" never arrives, rather than hide it for good. */
  private static final long RENDER_TIMEOUT_MS = 4000;

  /** The screen around it fades over 120ms when it stands down; this goes with it. */
  private static final long FADE_MS = 120;

  /**
   * The room one row of facelets needs before the diagram is worth drawing at all.
   *
   * <p>The gap is whatever the scramble and the card leave, and what that is worth depends on the
   * puzzle: a 3x3 net is nine rows of facelets and a 7x7 net is twenty one, in the same space and
   * from a scramble three times as long. Measuring the gap in rows rather than in dp is what tells
   * the two apart — a Pixel 9 Pro leaves a 3x3 about 207dp, which reads comfortably, and a 7x7
   * around 70dp, which is a smudge with the same name. The cases with no gap at all fall out of the
   * same rule: 360x640 leaves 38dp for a 3x3, and landscape 13dp, since the digits, the chip and a
   * three line scramble fill that column on their own. Nothing drawn beats something illegible.
   */
  private static final int MIN_ROW_DP = 5;

  /** A 3x3 net, and near enough for the puzzles whose nets are not made of squares. */
  private static final int DEFAULT_NET_ROWS = 9;

  private final Context context;
  private final View.OnTouchListener touchListener;

  private ViewStub stub;
  private View slot;
  private View foot;
  private View previewLayout;
  private WebView webView;
  /** The document has run, so {@code window.ntRender} exists and may be called. */
  private boolean pageLoaded;
  /** The page says it has drawn, which is when there is any point showing it. */
  private boolean pageReady;
  private boolean suppressed;
  /** A live cube holds the gap, so nothing is drawn here and nothing is ever built. */
  private boolean replaced;
  /** The gap has been through a layout, so its height is an answer rather than a zero. */
  private boolean measured;
  /** The gap is deep enough to draw in. Assumed until measured: it has to be given a size first. */
  private boolean roomEnough = true;
  /** What this puzzle's net needs, in pixels: see {@link #MIN_ROW_DP}. */
  private int minSlotPx;

  /** What to draw: a cubing.js renderer key and the scramble in its notation, or null for neither. */
  private String key;
  private String moves;
  /** The surface the diagram on screen was built for, so a resize can ask for it again. */
  private int drawnWidth;
  private int drawnHeight;

  /**
   * @param touchListener the timer screen's own, forwarded so the preview is not a dead zone —
   *     {@code CLAUDE.md} requires a tap anywhere in the timer to start or stop it, and a WebView
   *     swallows presses.
   */
  public ScrambleStatePreview(Context context, View.OnTouchListener touchListener) {
    this.context = context;
    this.touchListener = touchListener;
  }

  /**
   * Points the preview at a freshly laid-out screen. Safe to call again, and must be: the timer
   * takes its own configuration changes and rebuilds its content view by hand, so a rotation hands
   * over an entirely new stub. Whatever was inflated into the old layout is torn down here; nothing
   * is lost, since what to draw is held in Java and the page is reloaded from it.
   *
   * @param slot the gap the diagram draws in, watched for how much of it there is. A gap that
   *     collapses, or that is taken away entirely, reads here as no room and nothing is built.
   * @param foot the spacer under that gap, whose weight answers the slot's: see {@link #balance}
   */
  public void bind(ViewStub stub, View slot, View foot) {
    boolean relaidOut = (webView != null);
    if (relaidOut) {
      destroy();
    } else if (this.slot != null) {
      this.slot.removeOnLayoutChangeListener(slotLayout); // rotated before anything was built
    }
    this.stub = stub;
    this.slot = slot;
    this.foot = foot;
    measured = false;
    roomEnough = true;
    if (slot != null) {
      slot.addOnLayoutChangeListener(slotLayout);
      measureSlot(); // a rotation arrives with the new gap already laid out
    }
    balance();
    if (relaidOut) {
      build(); // the page loads itself and asks for the scramble in onPageFinished
    }
    refresh();
  }

  /**
   * Gives the gap its share of the screen only while there is a diagram to put in it.
   *
   * <p>The timer's free space is shared by three: the spacer above the digits, this gap, and the
   * spacer under it. With a diagram the split is 1:3:2, which centres the block — the foot is the
   * heavier of the two spacers because the digits carry their own leading above the glyphs, and
   * with the gaps equal the block reads low. With nothing to draw the gap takes nothing and the
   * two spacers halve the space between them, which is the screen as it was before any of this.
   *
   * <p>It is settled before the diagram is drawn and not touched again, so nothing here can move
   * the digits during a solve. Standing down for a run is a visibility, never a weight.
   *
   * <p>The live cube takes nearly all of it instead, 1:8:1, because it does not have the gap to
   * itself: the step bar and its legend ride at the foot of the same gap, and a solve's breakdown is
   * a third of what a diagram's share would be. Split 1:3:2 the cube came out smaller than it had
   * been before there was ever a diagram to share with, which is the whole complaint this answers.
   */
  private void balance() {
    if (replaced) {
      setWeight(slot, 8f);
      setWeight(foot, 1f);
      return;
    }
    boolean drawing = (key != null) && roomEnough;
    setWeight(slot, drawing ? 3f : 0f);
    setWeight(foot, drawing ? 2f : 1f);
  }

  private static void setWeight(View view, float weight) {
    if (view == null || !(view.getLayoutParams() instanceof LinearLayout.LayoutParams)) {
      return;
    }
    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
    if (params.weight != weight) {
      params.weight = weight;
      view.setLayoutParams(params);
    }
  }

  /**
   * ⚠️ The answer is acted on <b>after</b> the pass that gave it, never inside it. Inflating the
   * stub adds a child to the very view being laid out, and the layout that asks for is dropped on
   * the floor: the diagram is then attached, sized 0&times;0, and never measured, since a resting
   * screen has no further pass to catch it.
   */
  private final View.OnLayoutChangeListener slotLayout = new View.OnLayoutChangeListener() {
    @Override
    public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
      if (measureSlot()) {
        v.post(buildOutOfLayout);
      }
    }
  };

  private final Runnable buildOutOfLayout = new Runnable() {
    @Override
    public void run() {
      balance();
      build();
      refresh();
    }
  };

  /** @return whether the answer changed */
  private boolean measureSlot() {
    // A gap that is not on screen is not a gap: a GONE slot measures zero and nothing is built.
    int height = (slot == null || slot.getVisibility() != View.VISIBLE) ? 0 : slot.getHeight();
    if (height == 0) {
      // Not laid out, or laid out with no share to measure because there is nothing to draw. Either
      // way it is not an answer, and reading it as one would take the gap away for good.
      return false;
    }
    boolean room = height >= minSlot();
    if (measured && room == roomEnough) {
      return false;
    }
    measured = true;
    roomEnough = room;
    return true;
  }

  private int minSlot() {
    return (minSlotPx > 0) ? minSlotPx
      : (int) (DEFAULT_NET_ROWS * MIN_ROW_DP * context.getResources().getDisplayMetrics().density);
  }

  /** How many rows of facelets the puzzle's net is drawn in, which is what has to stay legible. */
  private static int netRows(CubeType cubeType) {
    if (cubeType == null) {
      return DEFAULT_NET_ROWS;
    }
    switch (cubeType) {
      case TWO_BY_TWO:     return 6;
      case FOUR_BY_FOUR:   return 12;
      case FIVE_BY_FIVE:   return 15;
      case SIX_BY_SIX:     return 18;
      case SEVEN_BY_SEVEN: return 21;
      default:             return DEFAULT_NET_ROWS;
    }
  }

  /** Builds the page, once there is both something to draw and somewhere measured to draw it. */
  private void build() {
    if (key == null || replaced || !measured || !roomEnough) {
      return;
    }
    inflate();
    load();
  }

  /**
   * Hands over the scramble to draw, or takes the drawing away.
   *
   * @param blind a blind solve type, for which nothing is ever drawn and nothing is ever built
   */
  public void show(CubeType cubeType, String[] scramble, boolean blind) {
    String renderKey = blind ? null : ScrambleViewNotation.getRenderKey(cubeType);
    // Null for a Clock pin notation, which cubing.js cannot parse. The dialog falls back to text;
    // here the scramble is already on screen a few pixels below, so the gap simply stays a gap.
    String notation = (renderKey == null || scramble == null)
        ? null : ScrambleViewNotation.toCubingNotation(scramble, cubeType);
    if (notation == null || notation.isEmpty()) {
      renderKey = null;
    }
    key = renderKey;
    moves = notation;
    int needed = (int) (netRows(cubeType) * MIN_ROW_DP
        * context.getResources().getDisplayMetrics().density);
    if (needed != minSlotPx) {
      minSlotPx = needed;
      measured = false; // the gap was weighed against another puzzle's net
      roomEnough = true;
      measureSlot();
    }
    balance();
    build();
    refresh();
  }

  /** Force-hide the preview: the screen has stood down for a solve. */
  public void setSuppressed(boolean suppressed) {
    this.suppressed = suppressed;
    refresh();
  }

  /**
   * Hands the gap over to the live cube, or takes it back: the two draw in the same place and only
   * ever one of them is up. A cube enforces the scramble move by move, so the picture of it has
   * nothing left to say — and side by side neither was large enough to read.
   *
   * <p>The gap keeps its share of the screen either way, so connecting a cube swaps what is in it
   * without moving anything around it.
   */
  public void setReplaced(boolean replaced) {
    if (this.replaced == replaced) {
      return;
    }
    this.replaced = replaced;
    balance();
    build(); // a cube that connected first is what leaves the page unbuilt; this is where it is built
    refresh();
  }

  public void start() {
    if (webView != null) {
      webView.onResume();
    }
  }

  public void stop() {
    if (webView != null) {
      webView.onPause();
    }
  }

  public void destroy() {
    if (slot != null) {
      slot.removeOnLayoutChangeListener(slotLayout);
      slot = null;
    }
    foot = null;
    if (webView != null) {
      webView.removeCallbacks(renderTimeout);
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
    drawnWidth = 0;
    drawnHeight = 0;
    previewLayout = null;
    stub = null;
  }

  private void inflate() {
    if (webView != null || stub == null) {
      return;
    }
    try {
      // ⚠️ Do NOT scale this subtree (ScalingLinearLayout.scaleLateSubtree). The stub was there for
      // the one scaling pass, and inflate() hands its already-scaled params to the view it puts in
      // its place; scaling again would square the factor.
      previewLayout = stub.inflate();
      stub = null;
      webView = previewLayout.findViewById(R.id.wvStatePreview);
      setUpWebView();
    } catch (Throwable t) {
      // e.g. no WebView implementation installed. Swallowed on purpose: this is a feature that
      // simply never appears, and the gap it draws in was already a gap.
      Log.w("StatePreview", "could not inflate the state preview", t);
      webView = null;
      previewLayout = null;
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
    webView.addOnLayoutChangeListener(webViewLayout);

    webView.addJavascriptInterface(new Bridge(), "NTBridge");
    if (isDebuggable()) {
      WebView.setWebContentsDebuggingEnabled(true);
      webView.setWebChromeClient(new WebChromeClient() {
        @Override
        public boolean onConsoleMessage(android.webkit.ConsoleMessage message) {
          Log.d("StatePreview", message.message() + " (line " + message.lineNumber() + ")");
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
        load(); // the page starts empty; this is what puts a diagram in it
      }

      @Override
      public void onReceivedError(WebView v, WebResourceRequest request,
          androidx.webkit.WebResourceErrorCompat error) {
        if (request.isForMainFrame()) {
          pageLoaded = false;
          pageReady = false;
          refresh(); // nothing can be drawn, and the scramble text says it all anyway
        }
      }
    });

    webView.loadUrl(BASE_URL);
  }

  /**
   * The one Java&rarr;JS call. Quoted, never concatenated into a JS string literal: a prime move is
   * spelled U', and the apostrophe would close the literal and make a syntax error of the call.
   *
   * <p>⚠️ <b>Held until the surface has been laid out.</b> The page draws an SVG sized to the
   * viewport it finds, and a WebView that has not been through a layout pass reports 0&times;0: the
   * diagram is then built at no size and stays that way, since nothing later asks it to be built
   * again. The page finishing loading is <em>not</em> that moment — it beats the layout that
   * inflating the stub asked for. The listener below is what actually sends it.
   */
  private void load() {
    if (!pageLoaded || key == null) {
      return; // onPageFinished sends it instead — Java holds what to draw, so nothing is lost
    }
    int width = webView.getWidth();
    int height = webView.getHeight();
    if (width == 0 || height == 0) {
      return; // the layout listener sends it the moment there is a surface to draw into
    }
    drawnWidth = width;
    drawnHeight = height;
    webView.evaluateJavascript(
        "window.ntRender(" + JSONObject.quote(key) + "," + JSONObject.quote(moves) + ");", null);
    webView.removeCallbacks(renderTimeout);
    webView.postDelayed(renderTimeout, RENDER_TIMEOUT_MS);
  }

  /** The surface arrived, or changed size: the diagram is built for the size it is given. */
  private final View.OnLayoutChangeListener webViewLayout = new View.OnLayoutChangeListener() {
    @Override
    public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
      if (webView != null && (webView.getWidth() != drawnWidth || webView.getHeight() != drawnHeight)) {
        load();
      }
    }
  };

  /**
   * ⚠️ <b>INVISIBLE, never GONE.</b> A GONE WebView is never laid out, so the page would draw into a
   * 0&times;0 viewport. Nothing is gained by it either: the spacer this sits in holds its own space
   * whether or not there is a diagram in it, which is what keeps the digits where they are.
   */
  private void refresh() {
    if (previewLayout == null) {
      return;
    }
    boolean visible = (key != null) && !replaced && measured && roomEnough && !suppressed && pageReady;
    previewLayout.animate().cancel();
    if (visible) {
      previewLayout.setVisibility(View.VISIBLE);
      previewLayout.animate().alpha(1f).setDuration(FADE_MS).start();
    } else if (previewLayout.getVisibility() != View.VISIBLE) {
      previewLayout.setAlpha(0f); // never shown yet, so there is nothing to fade
    } else {
      previewLayout.animate().alpha(0f).setDuration(FADE_MS)
          .withEndAction(new Runnable() {
            @Override
            public void run() {
              previewLayout.setVisibility(View.INVISIBLE);
            }
          }).start();
    }
  }

  /** BuildConfig is not generated for this module, and the manifest flag says the same thing. */
  private boolean isDebuggable() {
    return (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
  }

  /** The page never said it had drawn. Show it regardless: an empty one is transparent anyway. */
  private final Runnable renderTimeout = new Runnable() {
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
    public void onRendered() {
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
          webView.removeCallbacks(renderTimeout);
          pageReady = true;
          refresh();
        }
      });
    }
  }
}
