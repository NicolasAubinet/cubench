package com.cube.nanotimer.gui;

import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.cube.nanotimer.R;
import com.cube.nanotimer.cube.DrillCubeView;
import com.cube.nanotimer.cube.GyroReferenceListener;
import com.cube.nanotimer.cube.SmartCubeManager;
import com.cube.nanotimer.smartcube.drill.DrillRep;
import com.cube.nanotimer.smartcube.drill.DrillSession;
import com.cube.nanotimer.smartcube.drill.DrillSpec;
import com.cube.nanotimer.smartcube.model.CubeConnection;
import com.cube.nanotimer.smartcube.model.CubeConnectionListener;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeMoveListener;
import com.cube.nanotimer.smartcube.step.LastLayerScrambles;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.helper.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs a drill: the case stands on the screen's cube, the smart cube is only the input, and the
 * user's own cube ends up scrambled, which is expected and needs nothing done about it.
 *
 * <p><b>There is no control that starts or stops a rep, and none may be added.</b> The cube reports
 * both ends of one: a rep begins when the case is put up and ends the moment
 * {@code CubieCube.isSolved} comes back true. Case after case with nothing to press is most of what
 * makes this feel unlike a scramble list. The one button spends a rep rather than escaping it.
 *
 * <p><b>The next case goes up the instant the last one is solved</b>, with no pause to watch the
 * cube finish. Recognition is measured from the previous rep's last move, so any delay put in for
 * the sake of an animation would be added to every recognition figure the drill reports.
 */
public class DrillActivity extends NanoTimerActivity implements CubeMoveListener,
    CubeConnectionListener, GyroReferenceListener, DrillCubeView.ReadyListener {

  /** The drill to run, as its JSON text. Absent for the app's own default drill. */
  public static final String EXTRA_SPEC = "drillSpec";

  private static final int DEFAULT_REPS = 20;

  private DrillSession session;
  private DrillCubeView cubeView;

  private View runningLayout;
  private View summaryLayout;
  private TextView tvUnavailable;
  private ProgressBar pbCube;
  private TextView tvCase;
  private TextView tvLastRep;
  private TextView tvProgress;
  private Button btSkip;
  private WebView webView;

  /** The page has drawn the case, which is when a turn can be counted against it. */
  private boolean cubeReady;
  /** The drill is over, by its last rep or by the cube going away, and takes no more turns. */
  private boolean finished;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.drill_screen);

    runningLayout = findViewById(R.id.drillRunning);
    summaryLayout = findViewById(R.id.drillSummary);
    tvUnavailable = findViewById(R.id.tvDrillUnavailable);
    pbCube = findViewById(R.id.pbDrillCube);
    tvCase = findViewById(R.id.tvDrillCase);
    tvLastRep = findViewById(R.id.tvDrillLastRep);
    tvProgress = findViewById(R.id.tvDrillProgress);
    btSkip = findViewById(R.id.btDrillSkip);

    btSkip.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        DrillRep rep = session == null ? null : session.abandon();
        if (rep != null) {
          onRepFinished(rep);
        }
      }
    });
    findViewById(R.id.btDrillDone).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        finish();
      }
    });

    // Hands stay on the cube for a whole drill, so nothing here ever touches the screen.
    runningLayout.setKeepScreenOn(true);

    String json = getIntent().getStringExtra(EXTRA_SPEC);
    DrillSpec spec;
    try {
      // Refused rather than replaced: running a different drill from the one that was sent would
      // report reps against a prescription nobody made.
      spec = json == null ? defaultSpec() : DrillSpec.fromJson(json);
    } catch (IllegalArgumentException e) {
      showUnavailable(getString(R.string.drill_spec_unreadable));
      return;
    }
    setTitle(spec.getLabel() == null ? getString(R.string.drill_title) : spec.getLabel());
    session = new DrillSession(spec, Utils.getRandom());

    if (!session.isRunnable()) {
      showUnavailable(getString(R.string.drill_no_known_cases));
      return;
    }
    if (!SmartCubeManager.INSTANCE.isConnected()) {
      showUnavailable(getString(R.string.drill_needs_cube));
      return;
    }

    cubeView = new DrillCubeView(this, this);
    webView = findViewById(R.id.wvDrillCube);
    if (!cubeView.bind(webView)) {
      cubeView = null;
      showUnavailable(getString(R.string.drill_no_cube_view));
      return;
    }
    // A drill can be the first thing a session does, and the cube on screen only follows the
    // physical one once there is a grip to measure from. Fills an empty one; never re-takes.
    SmartCubeManager.INSTANCE.anchorGyroIfUnset();

    // The first case is queued before the page is up rather than waited for: the view holds it and
    // draws it as its first frame, so the drill never opens on a solved cube it then replaces.
    nextRep();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (cubeView != null) {
      cubeView.onResume();
      SmartCubeManager.INSTANCE.addMoveListener(this);
      SmartCubeManager.INSTANCE.addConnectionListener(this); // replays the connection at once
      SmartCubeManager.INSTANCE.addGyroReferenceListener(this);
      refreshGripHint();
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    SmartCubeManager.INSTANCE.removeMoveListener(this);
    SmartCubeManager.INSTANCE.removeConnectionListener(this);
    SmartCubeManager.INSTANCE.removeGyroReferenceListener(this);
    if (cubeView != null) {
      cubeView.onPause();
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (cubeView != null) {
      cubeView.destroy();
    }
  }

  /** The case is on screen, so it can be shown and turns can start counting against it. */
  @Override
  public void onCubeReady() {
    pbCube.setVisibility(View.GONE);
    webView.setVisibility(View.VISIBLE);
    cubeReady = true;
  }

  /**
   * A turn made before the case is on screen is dropped, not queued. Both cubes here miss it
   * equally, so they stay in step, and the physical cube's own state never mattered; timing a rep
   * against a case the user could not yet see would.
   */
  @Override
  public void onMove(CubeMove move) {
    if (!cubeReady || finished || session == null || session.getCurrentCase() == null) {
      return;
    }
    cubeView.move(move.getNotation());
    DrillRep rep = session.onMove(move);
    if (rep != null) {
      onRepFinished(rep);
    }
  }

  @Override
  public void onGyroReferenceChanged() {
    refreshGripHint();
  }

  /**
   * The line telling the user which way up to hold their cube, shown only while the cube on screen
   * is <em>not</em> following theirs. Once it follows, the face under their right hand is the face
   * on the right of the screen however they picked it up, and the line would be telling them to do
   * something that no longer matters.
   */
  private void refreshGripHint() {
    findViewById(R.id.tvDrillGrip)
        .setVisibility(cubeView.isFollowingGrip() ? View.GONE : View.VISIBLE);
  }

  /** A drill stopped at rep 6 of 20 is a result, so a cube that goes away ends it rather than
   * losing it. */
  @Override
  public void onConnection(CubeConnection connection) {
    if (session != null && !SmartCubeManager.INSTANCE.isConnected()) {
      showSummary();
    }
  }

  private void onRepFinished(DrillRep rep) {
    showLastRep(rep);
    nextRep();
  }

  private void nextRep() {
    if (!session.nextRep()) {
      showSummary();
      return;
    }
    cubeView.show(session.getCurrentScramble());
    tvCase.setText(Utils.toSmartCubeCaseHeadline(this, session.getCurrentCase()));
    tvProgress.setText(getString(R.string.drill_progress,
        session.getReps().size() + 1, session.getSpec().getReps()));
  }

  private void showLastRep(DrillRep rep) {
    if (rep.isAbandoned()) {
      tvLastRep.setText(R.string.drill_rep_skipped);
      return;
    }
    String total = FormatterService.INSTANCE.formatSolveTime(rep.getTotalMs());
    if (!rep.isRecognitionMeasured()) {
      // The first rep of a drill has no previous rep to measure the looking from, and says so
      // rather than calling it zero.
      tvLastRep.setText(getString(R.string.drill_rep_execution_only,
          FormatterService.INSTANCE.formatSolveTime(rep.getExecutionMs())));
      return;
    }
    tvLastRep.setText(getString(R.string.drill_rep_split, total,
        FormatterService.INSTANCE.formatSolveTime(rep.getRecognitionMs()),
        FormatterService.INSTANCE.formatSolveTime(rep.getExecutionMs())));
  }

  private void showSummary() {
    if (finished) {
      return; // a cube unplugged on the summary screen must not re-run this over its own figures
    }
    finished = true;
    runningLayout.setVisibility(View.GONE);
    summaryLayout.setVisibility(View.VISIBLE);

    List<DrillRep> reps = session.getReps();
    ((TextView) findViewById(R.id.tvDrillSummaryReps)).setText(getString(R.string.drill_summary_reps,
        reps.size(), session.getSpec().getReps()));

    // Skipped reps are counted apart rather than folded into a mean, the same rule the case stats
    // follow: a case you gave up on is not a slow time.
    long total = 0;
    long best = Long.MAX_VALUE;
    int timed = 0;
    for (DrillRep rep : reps) {
      if (rep.isAbandoned()) {
        continue;
      }
      long ms = rep.getTimedMs(session.getSpec().getType());
      total += ms;
      best = Math.min(best, ms);
      timed++;
    }
    TextView tvMean = findViewById(R.id.tvDrillSummaryMean);
    TextView tvBest = findViewById(R.id.tvDrillSummaryBest);
    if (timed == 0) {
      tvMean.setText(R.string.drill_summary_nothing_timed);
      tvBest.setVisibility(View.GONE);
      return;
    }
    tvMean.setText(getString(R.string.drill_summary_mean,
        FormatterService.INSTANCE.formatSolveTime(total / timed), timedHalfName()));
    tvBest.setVisibility(View.VISIBLE);
    tvBest.setText(getString(R.string.drill_summary_best,
        FormatterService.INSTANCE.formatSolveTime(best)));
  }

  /** The half the drill is judged on: the same reps either way, only the target moves. */
  private String timedHalfName() {
    return getString(session.getSpec().getType() == DrillSpec.Type.CASE_RECOGNITION
        ? R.string.drill_recognition : R.string.drill_execution);
  }

  private void showUnavailable(String message) {
    runningLayout.setVisibility(View.GONE);
    tvUnavailable.setVisibility(View.VISIBLE);
    tvUnavailable.setText(message);
  }

  /** Every PLL, until either the user picks a set or a coach sends one. */
  private DrillSpec defaultSpec() {
    List<String> cases = new ArrayList<String>();
    for (String code : LastLayerScrambles.cases()) {
      if (code.startsWith("pll_")) {
        cases.add(code);
      }
    }
    return new DrillSpec("local-pll", DrillSpec.Type.CASE_EXECUTION, DrillSpec.Delivery.VIRTUAL,
        cases, DrillSpec.Selection.ROUND_ROBIN, DEFAULT_REPS, 0, null);
  }
}
