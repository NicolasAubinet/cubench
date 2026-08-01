package com.cube.nanotimer.gui.widget.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import com.cube.nanotimer.R;
import com.cube.nanotimer.cube.SolveMovesFormat;
import com.cube.nanotimer.cube.SolveSolution;
import com.cube.nanotimer.gui.widget.NanoTimerDialogFragment;
import com.cube.nanotimer.util.FormatterService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Plays a recorded smart-cube solve back on a 3D cube: the scramble as the starting state, then the
 * solve's own moves at the millisecond offsets they were made at.
 *
 * <p>Hosts a transparent WebView rendering through the vendored cubing.js bundle (see
 * {@code assets/scramble/replay.html}), served through {@link WebViewAssetLoader} from the secure
 * {@code https://appassets.androidplatform.net/} origin — the same arrangement as
 * {@link ScrambleViewDialog}, sharing the same {@code bundle.js}.</p>
 *
 * <p>The moves are the solver-frame stream ({@link SolveSolution#timedSolution}), so slices read as
 * {@code M/E/S} and the solver's own rotations turn the cube on screen. Playback is timed from the
 * stored offsets rather than a constant tempo: the pauses in a solve are the part worth watching.</p>
 */
public class SolveReplayDialog extends NanoTimerDialogFragment {

  private static final String ARG_PUZZLE = "puzzle";
  private static final String ARG_SCRAMBLE = "scramble";
  private static final String ARG_MOVES = "moves";
  private static final String ARG_TIMED_MS = "timedMs";

  private static final String BASE_URL =
      "https://appassets.androidplatform.net/assets/scramble/replay.html";

  // Drop the spinner anyway if the JS "ready" signal never arrives, so it can't spin forever.
  private static final long READY_TIMEOUT_MS = 6000;

  // A beat on the scrambled cube before it starts turning: opening straight into the first moves
  // gives no chance to take in the state they are being made from.
  private static final long LEAD_IN_MS = 800;

  // Cycled by tapping the speed label. 1x first so a replay opens at the speed it happened. These
  // double as JS number literals, so the label and ntReplaySpeed() can never disagree.
  private static final String[] SPEEDS = {"1", "0.5", "0.25", "2"};

  private WebView webView;
  private ProgressBar progressBar;
  private ImageButton playButton;
  private TextView positionLabel;
  private TextView speedLabel;
  private View controlsRow;
  private TextView fallbackView;
  private String fallbackText;

  private long solveMs;     // what the replay must read as, floors or not
  private String totalText; // invariant for the dialog: formatted once, not on every state update
  private boolean playing;
  private boolean transportPainted;
  private int speedIndex;

  private final Runnable hideProgressRunnable = new Runnable() {
    @Override
    public void run() {
      hideProgress();
    }
  };

  private final Runnable autoPlayRunnable = new Runnable() {
    @Override
    public void run() {
      evaluate("window.ntReplayPlay();");
    }
  };

  /**
   * @param puzzleId the cubing.js puzzle id for the player ({@code ScrambleViewNotation.getPuzzleId}),
   *                 which is NOT the {@code getRenderKey} diagram id.
   * @param scramble  the solve's scramble, in cubing.js notation, or {@code null} if it cannot be
   *                  rendered (then only the text fallback is shown).
   * @param moves     the solve's stored move stream ({@code SolveTime.getSmartcubeMoves()}).
   * @param timedMs   what the clock actually measured, penalties removed — the replay reads as
   *                  this so it agrees with the time above it. 0 when there is none (a DNF), and
   *                  then the moves' own span stands in.
   */
  public static SolveReplayDialog newInstance(String puzzleId, String scramble, String moves,
      long timedMs) {
    SolveReplayDialog frag = new SolveReplayDialog();
    Bundle args = new Bundle();
    args.putString(ARG_PUZZLE, puzzleId);
    args.putString(ARG_SCRAMBLE, scramble);
    args.putString(ARG_MOVES, moves);
    args.putLong(ARG_TIMED_MS, timedMs);
    frag.setArguments(args);
    return frag;
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    final String scramble = getArguments().getString(ARG_SCRAMBLE);
    final String puzzleId = getArguments().getString(ARG_PUZZLE);

    View view = LayoutInflater.from(getActivity()).inflate(R.layout.solvereplay_dialog, null);
    webView = view.findViewById(R.id.wvReplay);
    progressBar = view.findViewById(R.id.pbReplay);
    playButton = view.findViewById(R.id.buReplayPlay);
    positionLabel = view.findViewById(R.id.tvReplayPosition);
    speedLabel = view.findViewById(R.id.buReplaySpeed);
    controlsRow = view.findViewById(R.id.replayControls);
    fallbackView = view.findViewById(R.id.tvReplayFallback);
    fallbackText = getString(R.string.solve_replay_unavailable);

    List<SolveMovesFormat.Move> moves =
        SolveSolution.timedSolution(getArguments().getString(ARG_MOVES));
    // Seeded here so the label reads sensibly even if the page never signals ready; the page owns
    // the timeline and overrides it in onReady. Measured from the first turn, as the page does —
    // a blind solve's memorisation is not part of what gets replayed.
    solveMs = coveredMs(moves, getArguments().getLong(ARG_TIMED_MS));
    totalText = FormatterService.INSTANCE.formatSolveTime(solveMs);

    if (scramble == null || scramble.isEmpty() || moves.isEmpty()
        || !setupWebView(puzzleId, scramble, moves)) {
      showFallback();
    } else {
      setUpControls();
    }

    return new AlertDialog.Builder(getActivity(), R.style.NanoTimerDialogTheme)
        .setTitle(R.string.solve_replay)
        .setView(view)
        .setPositiveButton(R.string.close, null)
        .create();
  }

  /**
   * How long the replay covers. The timer runs a little wider than the turning — it stops when the
   * cube reads solved, not on the last turn — so the moves' own span comes up short of the time on
   * screen, which reads as the wrong solve. The measured time is used where it is credible, less
   * whatever ran before the first turn: nothing before that is replayed, which on a blind solve is
   * the whole memorisation.
   */
  static long coveredMs(List<SolveMovesFormat.Move> moves, long timedMs) {
    if (moves.isEmpty()) {
      return 0;
    }
    long first = moves.get(0).getOffsetMs();
    long last = moves.get(moves.size() - 1).getOffsetMs();
    return timedMs > last ? timedMs - first : last - first;
  }

  private void setUpControls() {
    updateTransport(false);
    updateSpeed();
    playButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        webView.removeCallbacks(autoPlayRunnable); // the user's choice beats the pending auto-start
        evaluate(playing ? "window.ntReplayPause();" : "window.ntReplayPlay();");
      }
    });
    speedLabel.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        speedIndex = (speedIndex + 1) % SPEEDS.length;
        evaluate("window.ntReplaySpeed(" + SPEEDS[speedIndex] + ");");
        updateSpeed();
      }
    });
  }

  private boolean setupWebView(final String puzzleId, final String scramble,
      final List<SolveMovesFormat.Move> moves) {
    try {
      final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
          .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(getActivity()))
          .build();

      WebSettings settings = webView.getSettings();
      settings.setJavaScriptEnabled(true);
      webView.setBackgroundColor(Color.TRANSPARENT);

      webView.addJavascriptInterface(new Bridge(), "NTBridge");

      webView.setWebViewClient(new WebViewClientCompat() {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest request) {
          return assetLoader.shouldInterceptRequest(request.getUrl());
        }

        @Override
        public void onPageFinished(WebView v, String url) {
          load(puzzleId, scramble, moves);
          v.postDelayed(hideProgressRunnable, READY_TIMEOUT_MS);
        }

        @Override
        public void onReceivedError(WebView v, WebResourceRequest request,
            androidx.webkit.WebResourceErrorCompat error) {
          if (request.isForMainFrame()) {
            showFallback();
          }
        }
      });

      webView.loadUrl(BASE_URL);
      return true;
    } catch (Throwable t) {
      return false; // e.g. no WebView implementation installed on this device
    }
  }

  /** The whole Java-&gt;JS handover: the starting state, then the moves with their offsets. */
  private void load(String puzzleId, String scramble, List<SolveMovesFormat.Move> moves) {
    JSONArray arr = new JSONArray();
    try {
      for (SolveMovesFormat.Move move : moves) {
        JSONObject o = new JSONObject();
        o.put("m", move.getNotation());
        o.put("t", move.getOffsetMs());
        arr.put(o);
      }
    } catch (JSONException e) {
      return; // nothing sane to play; the spinner times out and the cube stays at the scramble
    }
    evaluate("window.ntReplayLoad(" + JSONObject.quote(puzzleId) + "," + JSONObject.quote(scramble)
        + "," + arr + "," + solveMs + ");");
  }

  private void evaluate(String js) {
    if (webView != null) {
      webView.evaluateJavascript(js, null);
    }
  }

  private void updatePosition(long ms) {
    if (positionLabel != null) {
      positionLabel.setText(getString(R.string.replay_position,
          FormatterService.INSTANCE.formatSolveTime(ms), totalText));
    }
  }

  /** Only on a change: {@code setImageResource} reloads the drawable even when the id is the same. */
  private void updateTransport(boolean isPlaying) {
    if (playButton == null || (transportPainted && playing == isPlaying)) {
      return;
    }
    transportPainted = true;
    playing = isPlaying;
    playButton.setImageResource(playing ? R.drawable.ic_replay_pause : R.drawable.ic_replay_play);
    playButton.setContentDescription(getString(playing ? R.string.replay_pause : R.string.replay_play));
  }

  private void updateSpeed() {
    speedLabel.setText(getString(R.string.replay_speed, SPEEDS[speedIndex]));
  }

  /** Called from the page thread; every method hops to the UI thread before touching a view. */
  private final class Bridge {

    @JavascriptInterface
    public void onReady() {
      post(new Runnable() {
        @Override
        public void run() {
          if (webView == null) {
            return; // the dialog went away between the page signalling and this running
          }
          webView.removeCallbacks(hideProgressRunnable);
          hideProgress();
          updatePosition(0);
          // A replay opened is a replay meant to be watched, but not from the very first frame.
          webView.postDelayed(autoPlayRunnable, LEAD_IN_MS);
        }
      });
    }

    @JavascriptInterface
    public void onState(final int ms, final boolean isPlaying) {
      post(new Runnable() {
        @Override
        public void run() {
          updatePosition(ms);
          updateTransport(isPlaying);
        }
      });
    }

    private void post(Runnable r) {
      if (webView != null) {
        webView.post(r);
      }
    }
  }

  private void showFallback() {
    hideProgress();
    if (webView != null) {
      webView.setVisibility(View.GONE);
    }
    if (controlsRow != null) {
      controlsRow.setVisibility(View.GONE);
    }
    if (fallbackView == null) {
      return; // a load error arriving after the dialog was torn down
    }
    fallbackView.setText(fallbackText);
    fallbackView.setVisibility(View.VISIBLE);
  }

  private void hideProgress() {
    if (progressBar != null) {
      progressBar.setVisibility(View.GONE);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    if (webView != null) {
      webView.onResume();
    }
  }

  @Override
  public void onPause() {
    // Stop the transport *and* the renderer: twisty-player keeps a WebGL context drawing otherwise.
    evaluate("window.ntReplayPause();");
    if (webView != null) {
      webView.onPause();
    }
    super.onPause();
  }

  @Override
  public void onDestroyView() {
    if (webView != null) {
      webView.removeCallbacks(hideProgressRunnable);
      webView.removeCallbacks(autoPlayRunnable);
      // Detach before destroying: destroying in place can strand the native renderer.
      ViewGroup parent = (ViewGroup) webView.getParent();
      if (parent != null) {
        parent.removeView(webView);
      }
      webView.destroy();
      webView = null;
    }
    progressBar = null;
    playButton = null;
    positionLabel = null;
    speedLabel = null;
    controlsRow = null;
    fallbackView = null;
    super.onDestroyView();
  }
}
