package com.cube.nanotimer.gui.widget.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.gui.widget.NanoTimerDialogFragment;

import org.json.JSONObject;

/**
 * On-demand diagram of the current scramble's solved-into state, so cubers can check they
 * scrambled correctly. Hosts a transparent WebView that renders via the vendored cubing.js bundle
 * (see {@code assets/scramble/scramble.html} and {@code ScrambleViewNotation}).
 *
 * <p>Two views of the same scramble, chosen by the chips under it: the flat net, which shows all
 * six faces at once, and a 3D cube the viewer can drag round. The cube is what makes a scramble
 * readable in a front face that is not green — a blind solver holding red front can turn it there
 * rather than translate the net in their head — so which one was last used is remembered
 * ({@link Options#isScrambleView3d}). Puzzles cubing.js only draws flat get no chips at all.</p>
 *
 * <p>The page is served through {@link WebViewAssetLoader} from the secure
 * {@code https://appassets.androidplatform.net/} origin (not {@code file://}), and the whole
 * Java&lt;-&gt;JS surface is a single {@code ntRender(key, scramble, mode, puzzleId)} call.
 * If the WebView is unavailable or the page fails to load, we fall back to showing the
 * scramble as text.</p>
 */
public class ScrambleViewDialog extends NanoTimerDialogFragment {

  private static final String ARG_KEY = "key";
  private static final String ARG_SCRAMBLE = "scramble";
  private static final String ARG_FALLBACK = "fallback";
  private static final String ARG_PUZZLE_3D = "puzzle3d";

  private static final String BASE_URL = "https://appassets.androidplatform.net/assets/scramble/scramble.html";

  // Hide the spinner anyway if the JS "rendered" signal never arrives (e.g. an old
  // WebView where detection fails), so it can't spin forever.
  private static final long RENDER_TIMEOUT_MS = 4000;

  private WebView webView;
  private ProgressBar progressBar;
  private TextView chip2d;
  private TextView chip3d;

  private String key;
  private String scramble;
  private String puzzle3d;
  private boolean threeD;
  /** Whether the page has run its document, so {@code ntRender} exists to be called again. */
  private boolean pageLoaded;

  private final Runnable hideProgressRunnable = new Runnable() {
    @Override
    public void run() {
      hideProgress();
    }
  };

  /**
   * @param renderKey      cubing.js renderer key (see {@code ScrambleViewNotation}).
   * @param cubingScramble scramble in cubing.js notation, or {@code null} if it can't
   *                       be drawn (then only the text fallback is shown).
   * @param fallbackText   text to show when the diagram is unavailable.
   * @param puzzleId3d     cubing.js puzzle id for the 3D view, or {@code null} for a puzzle that
   *                       only draws flat (see {@code ScrambleViewNotation.get3DPuzzleId}).
   */
  public static ScrambleViewDialog newInstance(String renderKey, String cubingScramble,
      String fallbackText, String puzzleId3d) {
    ScrambleViewDialog frag = new ScrambleViewDialog();
    Bundle args = new Bundle();
    args.putString(ARG_KEY, renderKey);
    args.putString(ARG_SCRAMBLE, cubingScramble);
    args.putString(ARG_FALLBACK, fallbackText);
    args.putString(ARG_PUZZLE_3D, puzzleId3d);
    frag.setArguments(args);
    return frag;
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    key = getArguments().getString(ARG_KEY);
    scramble = getArguments().getString(ARG_SCRAMBLE);
    puzzle3d = getArguments().getString(ARG_PUZZLE_3D);
    final String fallbackText = getArguments().getString(ARG_FALLBACK);

    View view = LayoutInflater.from(getActivity()).inflate(R.layout.scrambleview_dialog, null);
    webView = view.findViewById(R.id.wvScramble);
    progressBar = view.findViewById(R.id.pbScramble);
    final TextView fallback = view.findViewById(R.id.tvScrambleFallback);

    boolean renderable = scramble != null && !scramble.isEmpty();
    threeD = renderable && puzzle3d != null && Options.INSTANCE.isScrambleView3d();

    if (!renderable) {
      // Not renderable (e.g. a Clock pin notation) — go straight to text.
      showFallback(fallback, fallbackText);
    } else if (setupWebView(fallback, fallbackText)) {
      setUpModeChips(view);
    } else {
      showFallback(fallback, fallbackText);
    }

    return new AlertDialog.Builder(getActivity(), R.style.NanoTimerDialogTheme)
        .setTitle(R.string.scramble_view)
        .setView(view)
        .setPositiveButton(R.string.close, null)
        .create();
  }

  /** Shown only when there are two views to choose between, and something to draw in them. */
  private void setUpModeChips(View view) {
    if (puzzle3d == null) {
      return;
    }
    view.findViewById(R.id.scrambleViewModes).setVisibility(View.VISIBLE);
    chip2d = view.findViewById(R.id.buScrambleView2d);
    chip3d = view.findViewById(R.id.buScrambleView3d);
    chip2d.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        setThreeD(false);
      }
    });
    chip3d.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        setThreeD(true);
      }
    });
    updateModeChips();
  }

  private void setThreeD(boolean wanted) {
    if (threeD == wanted) {
      return;
    }
    threeD = wanted;
    Options.INSTANCE.setScrambleView3d(wanted);
    updateModeChips();
    if (pageLoaded) {
      // Building a 3D scene is not instant, so the spinner goes back up for it exactly as it does
      // on the first draw — the page signals again when the new drawing is painted.
      showProgress();
      render();
      webView.postDelayed(hideProgressRunnable, RENDER_TIMEOUT_MS);
    }
  }

  /** The chosen view is filled and outlined in the accent; the other stays the quiet pill. */
  private void updateModeChips() {
    if (chip2d == null) {
      return;
    }
    chip2d.setBackgroundResource(threeD ? R.drawable.row_chip : R.drawable.row_chip_selected);
    chip2d.setTextColor(getResources().getColor(threeD ? R.color.secondary_text : R.color.white));
    chip3d.setBackgroundResource(threeD ? R.drawable.row_chip_selected : R.drawable.row_chip);
    chip3d.setTextColor(getResources().getColor(threeD ? R.color.white : R.color.secondary_text));
  }

  private boolean setupWebView(final TextView fallback, final String fallbackText) {
    try {
      final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
          .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(getActivity()))
          .build();

      WebSettings settings = webView.getSettings();
      settings.setJavaScriptEnabled(true);

      // Blend onto the dialog's card background.
      webView.setBackgroundColor(Color.TRANSPARENT);

      // A drag on the 3D cube turns the camera, and an ancestor that scrolls would otherwise take
      // the vertical half of that gesture off it the moment it looked like a scroll.
      webView.setOnTouchListener(new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
          if (v.getParent() != null) {
            v.getParent().requestDisallowInterceptTouchEvent(true);
          }
          return false; // the WebView still handles the gesture: this only claims it
        }
      });

      // JS calls NTBridge.onRendered() once the diagram is actually drawn, so
      // we keep the spinner up until then (avoids a blank gap after page load).
      webView.addJavascriptInterface(new Object() {
        @JavascriptInterface
        public void onRendered() {
          if (webView != null) {
            webView.post(new Runnable() {
              @Override
              public void run() {
                webView.removeCallbacks(hideProgressRunnable);
                hideProgress();
              }
            });
          }
        }
      }, "NTBridge");

      webView.setWebViewClient(new WebViewClientCompat() {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest request) {
          return assetLoader.shouldInterceptRequest(request.getUrl());
        }

        @Override
        public void onPageFinished(WebView v, String url) {
          // Kick off rendering. The spinner stays until JS signals the diagram is
          // drawn (NTBridge.onRendered), with a timeout as a safety net.
          pageLoaded = true;
          render();
          v.postDelayed(hideProgressRunnable, RENDER_TIMEOUT_MS);
        }

        @Override
        public void onReceivedError(WebView v, WebResourceRequest request,
            androidx.webkit.WebResourceErrorCompat error) {
          if (request.isForMainFrame()) {
            // The bundle/page itself failed to load — degrade to the text scramble.
            showFallback(fallback, fallbackText);
          }
        }
      });

      webView.loadUrl(BASE_URL);
      return true;
    } catch (Throwable t) {
      // e.g. no WebView implementation installed/updatable on this device.
      return false;
    }
  }

  // The entire Java->JS call: the puzzle key, the scramble, and which of the two views to draw.
  private void render() {
    if (webView == null) {
      return;
    }
    String js = "window.ntRender(" + JSONObject.quote(key) + "," + JSONObject.quote(scramble)
        + "," + JSONObject.quote(threeD ? "3d" : "2d")
        + "," + (puzzle3d == null ? "null" : JSONObject.quote(puzzle3d)) + ");";
    webView.evaluateJavascript(js, null);
  }

  private void showFallback(TextView fallback, String fallbackText) {
    hideProgress();
    if (webView != null) {
      webView.setVisibility(View.GONE);
    }
    fallback.setText(fallbackText);
    fallback.setVisibility(View.VISIBLE);
  }

  private void showProgress() {
    if (progressBar != null) {
      progressBar.setVisibility(View.VISIBLE);
    }
  }

  private void hideProgress() {
    if (progressBar != null) {
      progressBar.setVisibility(View.GONE);
    }
  }

  @Override
  public void onDestroyView() {
    if (webView != null) {
      webView.removeCallbacks(hideProgressRunnable);
      webView.destroy();
      webView = null;
    }
    progressBar = null;
    chip2d = null;
    chip3d = null;
    super.onDestroyView();
  }
}
