package com.cube.nanotimer.gui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.cube.nanotimer.R;
import com.cube.nanotimer.cube.CubePatternFormat;
import com.cube.nanotimer.smartcube.drill.DrillRep;
import com.cube.nanotimer.smartcube.drill.DrillSession;
import com.cube.nanotimer.smartcube.drill.DrillSpec;
import com.cube.nanotimer.cube.SmartCubeManager;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.util.view.DrillRepFlourish;

import java.util.List;

/**
 * Runs a case drill: the case stands on the screen's cube, the smart cube is only the input, and the
 * user's own cube ends up scrambled, which is expected and needs nothing done about it.
 *
 * <p><b>There is no control that starts or stops a rep, and none may be added.</b> The cube reports
 * both ends of one: a rep begins when the case is put up and ends the moment
 * {@code CubieCube.isSolved} comes back true. Case after case with nothing to press is most of what
 * makes this feel unlike a scramble list. Neither button is an exception: one spends the rep in
 * front of the user, the other deals it again.
 *
 * <p><b>The case on screen is never named.</b> Naming it would hand over the answer, and a
 * recognition drill would be timing reading instead. A case is named once its rep is over, beside
 * that rep's time, and stands there while the next one is worked so it can be read without the
 * screen stopping to show it. It belongs beside the time and nowhere else: set under the cube at
 * headline size it read as a caption for the cube, and so as the name of the case being looked at.
 */
public class DrillActivity extends DrillScreenActivity {

  /** The drill to run, as its JSON text. */
  public static final String EXTRA_SPEC = "drillSpec";

  /**
   * The face to deal the last layer onto, as its letter. Not on the spec: which colour a solver
   * finishes on is theirs, and a drill prescribed from outside has no way of knowing it.
   */
  public static final String EXTRA_LAYER_FACE = "drillLayerFace";

  /**
   * How long the solved cube stays up before the next case replaces it. Zero: the green wash is
   * what says the case was finished, and it reads as an ending without stopping for one. The
   * timing does not care either way, since recognition runs from the case being shown.
   */
  private static final long REP_HOLD_MS = 0;

  private DrillSession session;

  private TextView tvLastRep;
  private TextView tvProgress;
  private View repWash;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.drill_screen);
    bindDrillScreen();

    tvLastRep = findViewById(R.id.tvDrillLastRep);
    tvProgress = findViewById(R.id.tvDrillProgress);
    repWash = findViewById(R.id.drillRepWash);

    findViewById(R.id.btDrillSkip).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        DrillRep rep = session == null ? null : session.abandon();
        if (rep != null) {
          // No wash and no hold: nothing was solved to dwell on, and the user asked to move on.
          showLastRep(rep);
          nextRep();
        }
      }
    });
    findViewById(R.id.btDrillReset).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        resetRep();
      }
    });
    findViewById(R.id.btDrillDone).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        finish();
      }
    });

    String json = getIntent().getStringExtra(EXTRA_SPEC);
    DrillSpec spec;
    try {
      // Refused rather than replaced: running a different drill from the one that was sent would
      // report reps against a prescription nobody made.
      spec = DrillSpec.fromJson(json);
    } catch (RuntimeException e) {
      showUnavailable(getString(R.string.drill_spec_unreadable));
      return;
    }
    setTitle(spec.getLabel() == null ? getString(R.string.drill_title) : spec.getLabel());
    session = new DrillSession(spec, Utils.getRandom(), null,
        getIntent().getStringExtra(EXTRA_LAYER_FACE));

    if (!session.isRunnable()) {
      showUnavailable(getString(R.string.drill_no_known_cases));
      return;
    }
    if (!SmartCubeManager.INSTANCE.isConnected()) {
      showUnavailable(getString(R.string.drill_needs_cube));
      return;
    }
    if (!createCube()) {
      return;
    }

    // The first case is queued before the page is up rather than waited for: the view holds it and
    // draws it as its first frame, so the drill never opens on a solved cube it then replaces.
    nextRep();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    tvLastRep.removeCallbacks(showNextCase);
    DrillRepFlourish.cancel(repWash, tvLastRep);
  }

  @Override
  protected boolean isDrillRunning() {
    return session != null;
  }

  /** The case is on screen, which is where the first rep's recognition runs from. */
  @Override
  protected void onCaseVisible() {
    session.markCaseShown(System.currentTimeMillis());
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
    cube.addMove(move.getNotation());
    DrillRep rep = session.onMove(move);
    if (rep != null) {
      onRepFinished(rep);
    }
  }

  /**
   * Holds the solved cube for a beat before the next case goes up.
   *
   * <p>Not decoration. A case that was replaced the instant it was solved read as though the user
   * had done something wrong, because the only thing they saw was the cube changing under them. The
   * beat costs them nothing: recognition runs from when the next case is <em>shown</em>, so the
   * hold is charged to nobody.
   */
  private void onRepFinished(DrillRep rep) {
    showLastRep(rep);
    DrillRepFlourish.play(repWash, tvLastRep);
    tvLastRep.removeCallbacks(showNextCase);
    tvLastRep.postDelayed(showNextCase, REP_HOLD_MS);
  }

  private final Runnable showNextCase = new Runnable() {
    @Override
    public void run() {
      nextRep();
    }
  };

  /**
   * Deals the same case again, for one botched by a slip rather than by not knowing it. Spends no
   * rep, and there is no hold: the user is waiting on the case they asked to have back.
   */
  private void resetRep() {
    if (session.getCurrentCase() == null) {
      return; // between reps, with the finished cube still up
    }
    session.resetRep();
    cube.setState(CubePatternFormat.format(session.getFacelets()));
    if (cubeReady) {
      session.markCaseShown(System.currentTimeMillis());
    }
  }

  private void nextRep() {
    if (!session.nextRep()) {
      showSummary();
      return;
    }
    cube.setState(CubePatternFormat.format(session.getFacelets()));
    // ⚠️ The case now on screen is deliberately not named anywhere. Only the cube says what it is.
    tvProgress.setText(getString(R.string.drill_progress,
        session.getReps().size() + 1, session.getSpec().getReps()));
    // Only once the case is really up: before the page has drawn, the first one is not yet in
    // front of anybody, and onCubeDrawn is what says it is.
    if (cubeReady) {
      session.markCaseShown(System.currentTimeMillis());
    }
  }

  /**
   * The rep that has just ended, named and timed together. Naming it is only safe here: the case is
   * over, so it can say what was done, or for a skipped one what it was the user could not place.
   */
  private void showLastRep(DrillRep rep) {
    String result;
    if (rep.isAbandoned()) {
      result = getString(R.string.drill_rep_skipped);
    } else {
      result = getString(R.string.drill_rep_split,
          FormatterService.INSTANCE.formatSolveTime(rep.getTotalMs()),
          FormatterService.INSTANCE.formatSolveTime(rep.getRecognitionMs()),
          FormatterService.INSTANCE.formatSolveTime(rep.getExecutionMs()));
    }
    tvLastRep.setText(getString(R.string.drill_rep_line,
        Utils.toSmartCubeCaseHeadline(this, rep.getCaseCode()), result));
  }

  @Override
  protected void showSummary() {
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
    long recognitionTotal = 0;
    long executionTotal = 0;
    long best = Long.MAX_VALUE;
    int timed = 0;
    for (DrillRep rep : reps) {
      if (rep.isAbandoned()) {
        continue;
      }
      recognitionTotal += rep.getRecognitionMs();
      executionTotal += rep.getExecutionMs();
      best = Math.min(best, rep.getTimedMs(session.getSpec().getType()));
      timed++;
    }
    TextView tvMean = findViewById(R.id.tvDrillSummaryMean);
    TextView tvOtherMean = findViewById(R.id.tvDrillSummaryOtherMean);
    TextView tvBest = findViewById(R.id.tvDrillSummaryBest);
    if (timed == 0) {
      tvMean.setText(R.string.drill_summary_nothing_timed);
      tvOtherMean.setVisibility(View.GONE);
      tvBest.setVisibility(View.GONE);
      return;
    }
    // Both halves, always: reading a case is half of what a drill trains, and a mean of the half
    // the target happens to name says nothing about whether the other one is where the time went.
    boolean recognitionDrill = session.getSpec().getType() == DrillSpec.Type.CASE_RECOGNITION;
    long timedTotal = recognitionDrill ? recognitionTotal : executionTotal;
    long otherTotal = recognitionDrill ? executionTotal : recognitionTotal;
    tvMean.setText(getString(R.string.drill_summary_mean,
        FormatterService.INSTANCE.formatSolveTime(timedTotal / timed), halfName(recognitionDrill)));
    tvOtherMean.setVisibility(View.VISIBLE);
    tvOtherMean.setText(getString(R.string.drill_summary_mean,
        FormatterService.INSTANCE.formatSolveTime(otherTotal / timed), halfName(!recognitionDrill)));
    tvBest.setVisibility(View.VISIBLE);
    tvBest.setText(getString(R.string.drill_summary_best,
        FormatterService.INSTANCE.formatSolveTime(best), halfName(recognitionDrill)));
  }

  /** Named on every figure now that two are shown: which half a time is of is no longer implied. */
  private String halfName(boolean recognition) {
    return getString(recognition ? R.string.drill_recognition : R.string.drill_execution);
  }
}
