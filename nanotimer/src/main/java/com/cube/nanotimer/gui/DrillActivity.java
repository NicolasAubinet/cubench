package com.cube.nanotimer.gui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.cube.nanotimer.R;
import com.cube.nanotimer.cube.CubePatternFormat;
import com.cube.nanotimer.smartcube.drill.DrillRep;
import com.cube.nanotimer.smartcube.drill.DrillSession;
import com.cube.nanotimer.smartcube.drill.DrillSpec;
import com.cube.nanotimer.gui.widget.DrillCaseTable;
import com.cube.nanotimer.gui.widget.dialog.CaseAlgorithmsDialog;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.helper.DialogUtils;
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
 * that rep's time, and goes again with the case it belongs to: left standing into the next rep it
 * reads as naming the case being worked, which is the thing this screen is careful about. It
 * belongs beside the time and nowhere else, since set under the cube at headline size it read as a
 * caption for the cube, and so again as the name of the case being looked at.
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
   * How long the solved cube and the rep's line stay up before the next case replaces them.
   *
   * <p>The length of the green beat, so the wash lands on the case it belongs to rather than over
   * its successor, and so the line naming that case is readable before it goes. It has to go: left
   * standing into the next rep it reads as naming the case being worked, which is the one thing
   * this screen refuses to do. The timing does not care either way, since recognition runs from
   * when the next case is <em>shown</em>, so the hold is charged to nobody.
   */
  private static final long REP_HOLD_MS = DrillRepFlourish.BEAT_MS;

  /** The family a case belongs to, which is the colour its name is written in everywhere. */
  private static final String FAMILY_OLL = "oll_";

  private DrillSession session;
  /** What this drill is called, for the summary once the bar that named it has gone. */
  private String label;

  /** The whole rep line, since the beat scales it and the hold is posted to it. */
  private View lastRepRow;
  private View repWash;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.drill_screen);
    bindDrillScreen();

    lastRepRow = findViewById(R.id.llDrillLastRep);
    repWash = findViewById(R.id.drillRepWash);

    findViewById(R.id.btDrillSkip).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        DrillRep rep = session == null ? null : session.abandon();
        if (rep != null) {
          // No wash: nothing was solved to dwell on. The hold stays, since what the user could not
          // place is the one thing worth reading off a skipped rep and the line goes with the case.
          showLastRep(rep);
          holdThenNext();
        }
      }
    });
    findViewById(R.id.btDrillAlgorithm).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showAlgorithms();
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
    label = spec.getLabel() == null ? getString(R.string.drill_title) : spec.getLabel();
    setTitle(label);
    session = new DrillSession(spec, Utils.getRandom(), null,
        getIntent().getStringExtra(EXTRA_LAYER_FACE));

    if (!session.isRunnable()) {
      showUnavailable(getString(R.string.drill_no_known_cases));
      return;
    }
    startWhenCubeConnected();
  }

  /**
   * The first case is queued before the page is up rather than waited for: the view holds it and
   * draws it as its first frame, so the drill never opens on a solved cube it then replaces.
   */
  @Override
  protected void startDrill() {
    nextRep();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    lastRepRow.removeCallbacks(showNextCase);
    DrillRepFlourish.cancel(repWash, lastRepRow);
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
    DrillRepFlourish.play(repWash, lastRepRow);
    holdThenNext();
  }

  private void holdThenNext() {
    lastRepRow.removeCallbacks(showNextCase);
    lastRepRow.postDelayed(showNextCase, REP_HOLD_MS);
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

  /**
   * The algorithms for the case in front of the user, which is the one thing this screen otherwise
   * refuses to say. Someone who has drawn a case they cannot place is not learning anything by
   * staring at it, and the alternative was leaving the drill to go and look it up.
   *
   * <p>The rep is not stopped or forgiven. It goes on being timed and is marked as looked up, so a
   * time reached this way is not later read as knowing the case. Between reps there is no case up
   * and nothing to show.
   */
  private void showAlgorithms() {
    String currentCase = session == null ? null : session.getCurrentCase();
    if (currentCase == null) {
      return;
    }
    session.markRevealed();
    DialogUtils.showFragment(this, CaseAlgorithmsDialog.newInstance(currentCase));
  }

  private void nextRep() {
    if (!session.nextRep()) {
      showSummary();
      return;
    }
    cube.setState(CubePatternFormat.format(session.getFacelets()));
    // ⚠️ The case now on screen is deliberately not named anywhere. Only the cube says what it is,
    // which is also why the line about the rep that is over goes with the case that is over.
    clearLastRep();
    setProgress(session.getReps().size() + 1, session.getSpec().getReps());
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
    String name = Utils.toSmartCubeCaseHeadline(this, rep.getCaseCode());
    int colour = rep.getCaseCode() != null && rep.getCaseCode().startsWith(FAMILY_OLL)
        ? R.color.step_oll : R.color.step_pll;
    if (rep.isAbandoned()) {
      setLastRep(name, colour, getString(R.string.drill_rep_skipped), true, null);
      return;
    }
    setLastRep(name, colour, FormatterService.INSTANCE.formatSolveTime(rep.getTotalMs()), false,
        getString(R.string.drill_rep_split,
            FormatterService.INSTANCE.formatSolveTime(rep.getRecognitionMs()),
            FormatterService.INSTANCE.formatSolveTime(rep.getExecutionMs())));
  }

  @Override
  protected void showSummary() {
    if (finished) {
      return; // a cube unplugged on the summary screen must not re-run this over its own figures
    }
    finished = true;
    showSummaryFor(label);

    List<DrillRep> reps = session.getReps();
    setSummaryCell(0, getString(R.string.drill_summary_cell_reps), String.valueOf(reps.size()),
        getString(R.string.drill_summary_cell_of, session.getSpec().getReps()));
    // Before the figures give up: a drill where every case was skipped still has something to
    // report, which is which cases they were.
    new DrillCaseTable(this, reps, session.getSpec().getType());

    // Skipped reps are counted apart rather than folded into a mean, the same rule the case stats
    // follow: a case you gave up on is not a slow time.
    long recognitionTotal = 0;
    long executionTotal = 0;
    long best = Long.MAX_VALUE;
    String bestCase = null;
    int timed = 0;
    for (DrillRep rep : reps) {
      if (rep.isAbandoned()) {
        continue;
      }
      recognitionTotal += rep.getRecognitionMs();
      executionTotal += rep.getExecutionMs();
      if (rep.getTotalMs() < best) {
        best = rep.getTotalMs();
        bestCase = rep.getCaseCode();
      }
      timed++;
    }
    if (timed == 0) {
      showSummaryEmpty(R.string.drill_summary_nothing_timed);
      return;
    }
    // Both halves, side by side and the same size, whichever one the drill was scored on. Reading
    // a case is half of what a drill trains, and the half the target happens to name says nothing
    // about whether the other one is where the time went.
    ((TextView) findViewById(R.id.tvDrillCellKeyTwo)).setText(R.string.drill_summary_cell_mean);
    ((TextView) findViewById(R.id.tvDrillMeanRecognition))
        .setText(FormatterService.INSTANCE.formatSolveTime(recognitionTotal / timed));
    ((TextView) findViewById(R.id.tvDrillMeanExecution))
        .setText(FormatterService.INSTANCE.formatSolveTime(executionTotal / timed));
    // The best rep is the best of both halves together, and says which case it was: a time with no
    // case attached was the one figure on this screen that could not be acted on.
    setSummaryCell(2, getString(R.string.drill_summary_cell_best),
        FormatterService.INSTANCE.formatSolveTime(best),
        Utils.toSmartCubeCaseHeadline(this, bestCase));
  }
}
