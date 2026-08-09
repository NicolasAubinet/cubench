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
import com.cube.nanotimer.util.ScaleUtils;
import com.cube.nanotimer.util.ScrambleViewNotation;
import com.cube.nanotimer.vo.CubeType;

import org.json.JSONObject;

/**
 * The scramble drawn as the state it leaves the puzzle in, in the gap above the timer.
 *
 * <p>The same page the scramble dialog opens on demand ({@code assets/scramble/scramble.html}),
 * mounted instead of opened: the whole Java&rarr;JS surface is still one
 * {@code ntRender(key, scramble, mode, puzzleId, style)} call, and a new scramble is that call
 * again rather than a new page. Bind once, then {@link #show} whenever the scramble changes.
 *
 * <p>The state is drawn <b>as a cube</b>, in the three quarter view the connected cube uses in
 * this same box, standing in a pool of shadow the page draws under it. The flat net is kept only
 * for the two puzzles cubing.js will not draw any other way, Clock and Square-1
 * ({@link ScrambleViewNotation#get3DPuzzleId} returns null for those). Drawing the object rather
 * than its unfolding also buys legibility: a net stacks three faces down the box and a cube shows
 * two, so a facelet comes out half as large again in the same height.
 *
 * <p>It draws in the gap under the scramble, next to the moves it is a picture of, at one size on
 * every screen of the app: a share of what was left over made it a different size under a solve
 * type whose statistics card is shorter, which reads as a bug rather than as a screen fitting
 * itself. The screen pays for that size out of the air around the block, and where there is not
 * enough of it the picture is not drawn at all — see {@link #fit}.
 *
 * <p>The box is shared with the live cube, which draws in it instead whenever one is connected:
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
   * How tall the picture is, in the px the timer layouts are authored in (scaled to the screen like
   * every other length there).
   *
   * <p>One height for every solve type and every puzzle, because the alternative was a share of
   * what the rest of the screen left: the statistics card is shorter for a solve type timed in
   * steps than for a plain one, so the same cube came out visibly larger under one than the other.
   *
   * <p>Sized off the air rather than off the tightest screen. A 1080x2400 phone was carrying 297dp
   * of gap it did nothing with, 128 over the digits and 169 under the picture; this takes that down
   * to 223 and hands the difference here, which turns a 205 by 154dp net into a 193 by 204dp cube
   * standing in a pool of shadow. A third taller than the net and no wider, which is as far as it
   * goes on purpose: the picture is the anchor of the screen, not the subject of it, and a cube
   * given every pixel the air could spare read as the thing being timed rather than as a note about
   * it. {@link #FOOT_WEIGHT} splits what is left.
   *
   * <p>⚠️ It is therefore <b>no longer the largest that fits everywhere</b>, and it was not meant to
   * be: a screen that cannot pay for it draws nothing at all rather than something small (see
   * {@link #fit}). Measured on the two screens that decide it, both on a fresh launch. A 7x7 on a
   * 1080x2400 480dpi phone still fits, which is the case that used to only just fit and where the
   * system bars, being dp, take 50px more than they do at 420dpi. A 360x640dp phone no longer does,
   * at any puzzle: it has about 280px spare against the 372 this asks of it there, so that screen
   * keeps its air and loses the picture, whichever way the setting is left.
   */
  private static final int PICTURE_PX = 222;

  /**
   * What the two spacers must keep between them for the picture to be worth asking for, in the same
   * px. Below this the block would sit against the scramble above it and the card below it.
   */
  private static final int MIN_AIR_PX = 26;

  /** How much heavier the spacer under the block is than the one over it: see {@link #fit}. */
  private static final float FOOT_WEIGHT = 1.4f;

  /**
   * The room one row of facelets needs before the diagram is worth drawing at all.
   *
   * <p>What a picture of a given height is worth depends on the puzzle: a 3x3 net is nine rows of
   * facelets and a 7x7 net is twenty one, drawn in the same box. Nothing drawn beats something
   * illegible.
   */
  private static final int MIN_ROW_DP = 5;

  /** A 3x3 net, and near enough for the puzzles whose nets are not made of squares. */
  private static final int DEFAULT_NET_ROWS = 9;

  private final Context context;
  private final View.OnTouchListener touchListener;

  private ViewStub stub;
  private View cell;
  private View head;
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
  /** The screen has been through a layout, so what it holds is an answer rather than a zero. */
  private boolean measured;
  /** The screen has room for the picture. Assumed until measured: it has to be asked for first. */
  private boolean roomEnough = true;
  /** What this puzzle's net needs, in pixels: see {@link #MIN_ROW_DP}. */
  private int minPicturePx;

  /** What to draw: a cubing.js renderer key and the scramble in its notation, or null for neither. */
  private String key;
  private String moves;
  /** The cubing.js puzzle id to draw as a cube, or null for the two puzzles that stay flat. */
  private String puzzle3d;
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
   * @param cell the box the picture is drawn in, whose height this class sets
   * @param head the spacer above the digits, and @param foot the one under the block: what the two
   *     of them hold is the air the picture is asked to be paid out of, so they are what says
   *     whether there is room for one. The foot is also evened up with the head when there is
   *     nothing in the gap at all: see {@link #fit}.
   */
  public void bind(ViewStub stub, View cell, View head, View foot) {
    boolean relaidOut = (webView != null);
    if (relaidOut) {
      destroy();
    } else {
      unwatch(this.cell); // rotated before anything was built
      unwatch(this.head);
      unwatch(this.foot);
    }
    this.stub = stub;
    this.cell = cell;
    this.head = head;
    this.foot = foot;
    measured = false;
    roomEnough = true;
    // All three, because what says whether there is room is what the three of them hold between
    // them, and any one of them can be the one that changes.
    watch(cell);
    watch(head);
    watch(foot);
    measureRoom(); // a rotation arrives with the new screen already laid out
    fit();
    if (relaidOut) {
      build(); // the page loads itself and asks for the scramble in onPageFinished
    }
    refresh();
  }

  /**
   * Gives the picture its height, and the spacers what is left.
   *
   * <p>The height is {@link #PICTURE_PX} whenever there is something to put in the box and the
   * screen can pay for it, and nothing at all otherwise, so the box is the same size under every
   * solve type and the air around it is what varies instead.
   *
   * <p>With nothing in the band the foot is evened up with the head, which is the screen as it was
   * before any of this. With something in it the foot stays the heavier of the two: what shows
   * above the digits is the head spacer plus the leading the glyphs carry inside their own box, and
   * the two gaps only read as equal when the one below is larger by that much.
   *
   * <p>⚠️ That "much" is a <b>fixed</b> number of pixels, the leading, and {@link #FOOT_WEIGHT} is a
   * ratio, so the two only agree at one size of gap. It was 2 when the picture was a small net and
   * there was half again as much air to divide; at that ratio a bigger picture threw 120px at a
   * 70px problem and left the block riding high. Measured on a 1080x2400 screen, this lands the two
   * within about 12px of each other, and drifts by well under the leading across everything from a
   * three line scramble to a ten line one.
   *
   * <p>It is settled before the picture is drawn and not touched again, so nothing here can move
   * the digits during a solve. Standing down for a run is a visibility, never a height.
   *
   * <p>⚠️ <b>Nothing is asked for until the screen has been measured</b>, and that is not only about
   * having an answer. {@code ScalingLinearLayout} multiplies every child's layout height by the
   * screen's scale on its first pass, once, and the height set here is already in those px — so a
   * height handed over before that pass is scaled a second time. Measured, the box then reported
   * 1122px where it had been given 499, which is a number that fits any screen: the answer froze at
   * "there is room", and sideways, where there is not, the picture was drawn overflowing its column
   * with the cube cut off at the foot. Left at nothing until then, the box is measured for what the
   * screen really has spare, and every height this class sets lands after the pass that would have
   * doubled it.
   */
  private void fit() {
    boolean showing = (replaced || key != null) && measured && roomEnough;
    setHeight(cell, showing ? picturePx() : 0);
    setWeight(foot, showing ? FOOT_WEIGHT : 1f);
  }

  private int picturePx() {
    return (int) (PICTURE_PX * ScaleUtils.getScale(context));
  }

  private static void setHeight(View view, int height) {
    if (view == null || view.getLayoutParams() == null || view.getLayoutParams().height == height) {
      return;
    }
    ViewGroup.LayoutParams params = view.getLayoutParams();
    params.height = height;
    view.setLayoutParams(params);
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

  private void watch(View view) {
    if (view != null) {
      view.addOnLayoutChangeListener(bandLayout);
    }
  }

  private void unwatch(View view) {
    if (view != null) {
      view.removeOnLayoutChangeListener(bandLayout);
    }
  }

  /**
   * ⚠️ Read and acted on <b>after</b> the pass, never inside it, for two reasons. The spacers are
   * laid out either side of the box, so inside the pass one of the three is still holding the
   * height it had before it — which reads as a screen with no room on it. And inflating the stub
   * adds a child to the very view being laid out, so the layout that asks for is dropped on the
   * floor: the diagram is then attached, sized 0&times;0, and never measured, since a resting
   * screen has no further pass to catch it.
   */
  private final View.OnLayoutChangeListener bandLayout = new View.OnLayoutChangeListener() {
    @Override
    public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
      v.post(settle);
    }
  };

  /** Cheap to run for nothing: it stops at the first read that says the answer has not changed. */
  private final Runnable settle = new Runnable() {
    @Override
    public void run() {
      if (measureRoom()) {
        fit();
        build();
        refresh();
      }
    }
  };

  /**
   * Whether the screen can pay for the picture, read off the two spacers plus whatever the box is
   * already holding.
   *
   * <p>⚠️ <b>Room once given is never taken back</b> until {@link #bind} or a new puzzle asks the
   * question again. The sum of the three was meant to hold whatever the box takes out of the
   * spacers, so that granting the box could not change the answer to the question that granted it.
   * It does not: the band holds the step bar too, and a connected cube fills it, so the sum falls
   * between one pass and the next (measured: 617px, then 454px, on the pass after the box was
   * given its height). The second answer then took back what the first gave, the box collapsed to
   * nothing, and the mirror in it had nowhere to draw. Growing into room is still allowed, since
   * that cannot chase itself.
   *
   * @return whether the answer changed
   */
  private boolean measureRoom() {
    if (cell == null || head == null || foot == null) {
      return false;
    }
    if (measured && roomEnough) {
      return false;
    }
    int spare = head.getHeight() + foot.getHeight() + cell.getHeight();
    if (spare == 0) {
      return false; // not laid out yet, and reading that as an answer would settle for nothing
    }
    // The mirror is asked for on its own terms: it is a cube on a transparent page, carrying its
    // own margin, where the net is drawn edge to edge and needs air held back for it. Its legibility
    // floor is the net's too and never binds, a cube only ever drawing for the 3x3 it is turning.
    boolean room = replaced ? spare >= picturePx()
        : spare >= picturePx() + (int) (MIN_AIR_PX * ScaleUtils.getScale(context))
            && picturePx() >= minPicture();
    if (measured && room == roomEnough) {
      return false;
    }
    measured = true;
    roomEnough = room;
    return true;
  }

  private int minPicture() {
    return (minPicturePx > 0) ? minPicturePx
      : (int) (DEFAULT_NET_ROWS * MIN_ROW_DP * context.getResources().getDisplayMetrics().density);
  }

  /**
   * How many rows of facelets the picture is drawn in, which is what has to stay legible.
   *
   * <p>A net stacks three faces down the box, U over the middle band over D, so an n&times;n costs
   * 3n rows. A cube in three quarter view shows two, the top face over the front one, so the same
   * puzzle costs 2n and its facelets come out half as large again in the same height.
   */
  private static int pictureRows(CubeType cubeType, boolean cube) {
    int faces = cube ? 2 : 3;
    if (cubeType == null) {
      return faces * 3;
    }
    switch (cubeType) {
      case TWO_BY_TWO:     return faces * 2;
      case FOUR_BY_FOUR:   return faces * 4;
      case FIVE_BY_FIVE:   return faces * 5;
      case SIX_BY_SIX:     return faces * 6;
      case SEVEN_BY_SEVEN: return faces * 7;
      default:             return faces * 3;
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
    puzzle3d = (renderKey == null) ? null : ScrambleViewNotation.get3DPuzzleId(cubeType);
    minPicturePx = (int) (pictureRows(cubeType, puzzle3d != null) * MIN_ROW_DP
        * context.getResources().getDisplayMetrics().density);
    // Asked afresh for every scramble, which is what bounds how long an answer stands: room is not
    // taken back within one (see measureRoom), and a solve type whose card grew is a new one.
    measured = false;
    roomEnough = true;
    measureRoom();
    fit();
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
   * <p>The box keeps its size either way, so connecting a cube swaps what is in it without moving
   * anything around it.
   */
  public void setReplaced(boolean replaced) {
    if (this.replaced == replaced) {
      return;
    }
    this.replaced = replaced;
    // The box is asked for on different terms by the two of them (see measureRoom), so whichever is
    // taking it over puts the question again rather than inheriting the other's answer.
    measured = false;
    roomEnough = true;
    measureRoom();
    fit();
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
    unwatch(cell);
    unwatch(head);
    unwatch(foot);
    cell = null;
    head = null;
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
   * <p>Asks for the cube wherever there is one to ask for, and always in the page's "mat"
   * dressing: this box sits on the timer's bare mat, which is what that style is for. With no cube
   * to ask for the mode is still sent, and the page falls through to the flat net on its own.
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
    webView.evaluateJavascript("window.ntRender(" + JSONObject.quote(key) + ","
        + JSONObject.quote(moves) + ",\"3d\","
        + (puzzle3d == null ? "null" : JSONObject.quote(puzzle3d)) + ",\"mat\");", null);
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
