package com.cube.nanotimer.gui.widget.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.content.res.TypedArray;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import com.cube.nanotimer.App;
import com.cube.nanotimer.R;
import com.cube.nanotimer.cube.GyroTrackFormat;
import com.cube.nanotimer.cube.SolveMovesFormat;
import com.cube.nanotimer.cube.SolveSolution;
import com.cube.nanotimer.gui.widget.NanoTimerDialogFragment;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.view.SolveStepBarView;
import com.cube.nanotimer.vo.SolveStep;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
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
  private static final String ARG_STEPS = "steps";
  private static final String ARG_SOLVE_ID = "solveId";

  private static final String BASE_URL =
      "https://appassets.androidplatform.net/assets/scramble/replay.html";

  // Drop the spinner anyway if the JS "ready" signal never arrives, so it can't spin forever.
  private static final long READY_TIMEOUT_MS = 6000;

  // A beat on the scrambled cube before it starts turning: opening straight into the first moves
  // gives no chance to take in the state they are being made from.
  private static final long LEAD_IN_MS = 800;

  // Shown side by side, slowest first, so any of them is one tap away. These double as JS number
  // literals, so the chip and ntReplaySpeed() can never disagree.
  private static final String[] SPEEDS = {"0.25", "0.5", "1", "2"};
  private static final int[] SPEED_IDS = {
      R.id.buReplaySpeed0, R.id.buReplaySpeed1, R.id.buReplaySpeed2, R.id.buReplaySpeed3};
  // A replay opens at the speed it happened.
  private static final int DEFAULT_SPEED = 2;

  private WebView webView;
  private ProgressBar progressBar;
  private ImageButton playButton;
  private TextView positionLabel;
  private TextView totalLabel;
  private final TextView[] speedChips = new TextView[SPEEDS.length];
  private View gyroRow;
  private SwitchCompat gyroSwitch;
  private View controlsRow;
  private TextView fallbackView;
  private String fallbackText;

  private SolveStepBarView bar;
  private long solveMs;     // what the replay must read as, floors or not
  private long startOffsetMs; // where the first turn sits in the solve: the bar draws the whole of
                              // it, but a replay starts here (on a blind solve, past the memo)
  private long barTotalMs;
  private String totalText; // invariant for the dialog: formatted once, not on every state update
  private boolean playing;
  private boolean transportPainted;
  private int speedIndex = DEFAULT_SPEED;
  private boolean gyroShown; // the track is off until asked for: the square cube is the honest default
  private boolean pageReady;  // the page has defined its functions; before that, evaluate() is lost
  private String pendingGyroJs; // the track, waiting for the page if it got here first

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
   * @param steps     the solve's steps, drawn as the scrubber; null or empty for no scrubber.
   * @param solveId   the solve's id, which the gyro track is fetched by. The track is not carried
   *                  in with the solve: it is kilobytes, and only a replay ever wants it.
   */
  public static SolveReplayDialog newInstance(String puzzleId, String scramble, String moves,
      long timedMs, ArrayList<SolveStep> steps, int solveId) {
    SolveReplayDialog frag = new SolveReplayDialog();
    Bundle args = new Bundle();
    args.putString(ARG_PUZZLE, puzzleId);
    args.putString(ARG_SCRAMBLE, scramble);
    args.putString(ARG_MOVES, moves);
    args.putLong(ARG_TIMED_MS, timedMs);
    args.putSerializable(ARG_STEPS, steps);
    args.putInt(ARG_SOLVE_ID, solveId);
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
    totalLabel = view.findViewById(R.id.tvReplayTotal);
    for (int i = 0; i < SPEEDS.length; i++) {
      speedChips[i] = view.findViewById(SPEED_IDS[i]);
    }
    gyroRow = view.findViewById(R.id.rowReplayGyro);
    gyroSwitch = view.findViewById(R.id.swReplayGyro);
    controlsRow = view.findViewById(R.id.replayControls);
    fallbackView = view.findViewById(R.id.tvReplayFallback);
    bar = view.findViewById(R.id.replayBar);
    fallbackText = getString(R.string.solve_replay_unavailable);

    List<SolveMovesFormat.Move> moves =
        SolveSolution.timedSolution(getArguments().getString(ARG_MOVES));
    // Seeded here so the label reads sensibly even if the page never signals ready; the page owns
    // the timeline and overrides it in onReady. Measured from the first turn, as the page does —
    // a blind solve's memorisation is not part of what gets replayed.
    startOffsetMs = moves.isEmpty() ? 0 : moves.get(0).getOffsetMs();
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

  /** The bar draws the whole solve; the replay only covers what follows the first turn. */
  @SuppressWarnings("unchecked")
  private void setUpBar() {
    ArrayList<SolveStep> steps = (ArrayList<SolveStep>) getArguments().getSerializable(ARG_STEPS);
    if (steps == null || steps.isEmpty()) {
      return;
    }
    barTotalMs = 0;
    for (SolveStep step : steps) {
      barTotalMs += step.getTotalMs();
    }
    if (barTotalMs <= 0) {
      return;
    }
    TypedArray palette = getResources().obtainTypedArray(R.array.solve_step_colors);
    int[] colors = new int[palette.length()];
    for (int i = 0; i < colors.length; i++) {
      colors[i] = palette.getColor(i, 0);
    }
    palette.recycle();

    bar.setSteps(steps, colors);
    bar.setVisibility(View.VISIBLE);
    bar.setOnSeekListener(new SolveStepBarView.OnSeekListener() {
      @Override
      public void onSeek(float fraction) {
        if (webView == null) {
          return;
        }
        // The bar is in the solve's own time; the replay clock starts at the first turn.
        long into = Math.max(0, Math.round(fraction * barTotalMs) - startOffsetMs);
        webView.removeCallbacks(autoPlayRunnable); // a deliberate jump beats the pending auto-start
        evaluate("window.ntReplaySeek(" + Math.min(into, solveMs) + ");");
      }
    });
    updateBar(0);
  }

  private void updateBar(long positionMs) {
    if (bar != null && barTotalMs > 0) {
      bar.setPlayhead((startOffsetMs + positionMs) / (float) barTotalMs);
    }
  }

  private void setUpControls() {
    setUpBar();
    totalLabel.setText(getString(R.string.replay_total, totalText)); // invariant: set once, not per tick
    updateTransport(false);
    updateSpeed();
    playButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        webView.removeCallbacks(autoPlayRunnable); // the user's choice beats the pending auto-start
        evaluate(playing ? "window.ntReplayPause();" : "window.ntReplayPlay();");
      }
    });
    for (int i = 0; i < speedChips.length; i++) {
      final int index = i;
      speedChips[i].setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          if (index == speedIndex) {
            return;
          }
          speedIndex = index;
          evaluate("window.ntReplaySpeed(" + SPEEDS[speedIndex] + ");");
          updateSpeed();
        }
      });
    }
    // The whole row is the target, and the switch itself is not clickable — the solve type
    // dialog's idiom, so a tap anywhere on the row reads the same way there as here.
    gyroRow.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        gyroShown = !gyroShown;
        evaluate("window.ntReplayGyroShow(" + gyroShown + ");");
        updateGyro();
      }
    });
    loadGyroTrack();
  }

  /**
   * Fetches the solve's gyro track and hands it over, revealing the toggle if there is one. Async
   * and entirely optional: a solve without a track (recorded before it was kept, or on a cube with
   * no gyro) simply never shows the control, and everything else about the replay is unaffected.
   */
  private void loadGyroTrack() {
    final int solveId = getArguments().getInt(ARG_SOLVE_ID);
    if (solveId <= 0) {
      return;
    }
    App.INSTANCE.getService().getGyroTrack(solveId, new DataCallback<String>() {
      @Override
      public void onData(final String track) {
        if (webView == null) {
          return;
        }
        webView.post(new Runnable() {
          @Override
          public void run() {
            showGyroTrack(track);
          }
        });
      }
    });
  }

  private void showGyroTrack(String track) {
    if (webView == null || gyroRow == null) {
      return; // the dialog went away while the track was being read
    }
    // The reconstruction's own frame, cancelled out of the pose: the replay already animates the
    // solver's rotations, so the raw pose would turn the cube a second time.
    List<GyroTrackFormat.Keyframe> poses = GyroTrackFormat.posesOf(track,
        SolveSolution.framesOf(getArguments().getString(ARG_MOVES)));
    if (poses.isEmpty()) {
      return;
    }
    JSONArray arr = new JSONArray();
    try {
      for (GyroTrackFormat.Keyframe pose : poses) {
        CubeOrientation q = pose.getOrientation();
        JSONObject o = new JSONObject();
        o.put("t", pose.getOffsetMs() - startOffsetMs); // the page dates everything from the first turn
        o.put("q", new JSONArray(new double[] {q.getW(), q.getX(), q.getY(), q.getZ()}));
        arr.put(o);
      }
    } catch (JSONException e) {
      return; // the replay stands without it
    }
    // The database beats the page: a track read in a few milliseconds would otherwise be handed to
    // a page that has not defined ntReplayGyro yet, and evaluate() drops it without a word. Held
    // until onReady, which is the only moment the page is known to be listening.
    pendingGyroJs = "window.ntReplayGyro(" + arr + ");";
    sendGyroTrack();
  }

  private void sendGyroTrack() {
    if (!pageReady || pendingGyroJs == null || gyroRow == null) {
      return;
    }
    evaluate(pendingGyroJs);
    pendingGyroJs = null;
    evaluate("window.ntReplayGyroShow(" + gyroShown + ");"); // the page starts square; keep it in step
    gyroRow.setVisibility(View.VISIBLE);
    updateGyro();
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
      if (isDebuggable()) {
        // The page's own console, in logcat. The replay is the one screen whose faults are all on
        // the JS side, and it has no other way of saying so.
        WebView.setWebContentsDebuggingEnabled(true);
        webView.setWebChromeClient(new WebChromeClient() {
          @Override
          public boolean onConsoleMessage(android.webkit.ConsoleMessage message) {
            Log.d("SolveReplay", message.message() + " (line " + message.lineNumber() + ")");
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

  /** BuildConfig is not generated for this module, and the manifest flag says the same thing. */
  private boolean isDebuggable() {
    return (getActivity().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
  }

  private void evaluate(String js) {
    if (webView != null) {
      webView.evaluateJavascript(js, null);
    }
  }

  private void updatePosition(long ms) {
    if (positionLabel != null) {
      positionLabel.setText(FormatterService.INSTANCE.formatSolveTime(ms));
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

  /** The chosen speed carries the accent pill; the rest stay the quiet one. */
  private void updateSpeed() {
    for (int i = 0; i < speedChips.length; i++) {
      boolean chosen = i == speedIndex;
      speedChips[i].setText(getString(R.string.replay_speed, SPEEDS[i]));
      speedChips[i].setBackgroundResource(
          chosen ? R.drawable.row_chip_accent : R.drawable.row_chip);
      speedChips[i].setTextColor(getResources().getColor(
          chosen ? R.color.lightblue : R.color.secondary_text));
      speedChips[i].setSelected(chosen);
    }
  }

  private void updateGyro() {
    if (gyroSwitch != null) {
      gyroSwitch.setChecked(gyroShown);
    }
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
          pageReady = true;
          sendGyroTrack(); // held here if the track was read before the page was up
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
          updateBar(ms);
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
    totalLabel = null;
    gyroRow = null;
    gyroSwitch = null;
    controlsRow = null;
    fallbackView = null;
    bar = null;
    super.onDestroyView();
  }
}
