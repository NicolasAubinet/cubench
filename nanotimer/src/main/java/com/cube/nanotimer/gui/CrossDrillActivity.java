package com.cube.nanotimer.gui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.cube.nanotimer.R;
import com.cube.nanotimer.cube.CubePatternFormat;
import com.cube.nanotimer.cube.CubeStickering;
import com.cube.nanotimer.gui.widget.dialog.CrossSolutionsDialog;
import com.cube.nanotimer.scrambler.ScramblerService;
import com.cube.nanotimer.scrambler.cross.CrossFace;
import com.cube.nanotimer.scrambler.cross.CrossFormatter;
import com.cube.nanotimer.scrambler.cross.CrossSolvers;
import com.cube.nanotimer.scrambler.cross.CrossSolvers.FaceSolutions;
import com.cube.nanotimer.smartcube.drill.CrossDrillRep;
import com.cube.nanotimer.smartcube.drill.CrossDrillSession;
import com.cube.nanotimer.smartcube.drill.DrillSpec;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.view.DrillRepFlourish;
import com.cube.nanotimer.vo.CubeType;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs a cross drill: a whole scramble stands on the screen's cube, and the user has to build the
 * cross on one face in as few moves as there were.
 *
 * <p><b>It is a blind execution drill, not only a move counting one.</b> The scramble opens with the
 * four cross edges and the six centres in colour and everything else grey, and on the user's first
 * turn the <em>whole</em> cube goes grey. So the cross is planned while it can be read and built
 * from memory, which is what inspection actually asks of a solver. The centres are not decoration in
 * that first view: with them grey there is nothing to say which face an edge belongs to and the case
 * cannot be read at all.
 *
 * <p><b>A wrong cross is announced, not detected.</b> A built cross ends its own rep; a wrong one
 * never will, because there is no state that says the user thinks they are finished. The one button
 * is that announcement, and it is not a stop control: pressing it with the cross really built ends
 * the rep the same way turning into it would have.
 *
 * <p><b>Extra moves are a finish, not a miss.</b> The colours come back exactly as they do on a rep
 * that found the short way, and what is said is that there was a shorter one. The search runs the
 * moment the scramble is drawn, on a background thread, so the solutions are waiting by the time
 * anyone asks for them.
 */
public class CrossDrillActivity extends DrillScreenActivity {

  /** The drill to run, as its JSON text. */
  public static final String EXTRA_SPEC = "drillSpec";

  /**
   * How long the user may stop turning, mid-rep, before the screen says what it can see. Long
   * enough not to talk over somebody thinking, short enough to answer the question they are asking
   * themselves when they stop, which is "is that it?".
   */
  private static final long STALLED_MS = 3500;

  /** How many scrambles to refuse for having their cross already built before taking one anyway. */
  private static final int MAX_DEAL_ATTEMPTS = 5;

  private final CrossSolvers solvers = new CrossSolvers();
  private final Handler handler = new Handler(Looper.getMainLooper());

  private CrossDrillSession session;
  private CrossFace face;
  /** What this drill is called, for the summary once the bar that named it has gone. */
  private String label;

  /** The optimal solutions for the scramble on screen, or null while the search is still running. */
  private FaceSolutions solutions;
  /** Which rep a search belongs to, so one that lands late is dropped rather than shown. */
  private int repSeq;

  private TextView tvStatus;
  private Button btStart;
  /** The whole rep line, since the beat scales it. */
  private View lastRepRow;
  private Button btDone;
  private Button btSolutions;
  private View extrasRow;
  private View repWash;

  /** Between reps: the cube stands finished and the button takes the next scramble. */
  private boolean betweenReps;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.crossdrill_screen);
    bindDrillScreen();

    tvStatus = findViewById(R.id.tvDrillCrossStatus);
    lastRepRow = findViewById(R.id.llDrillLastRep);
    btDone = findViewById(R.id.btDrillCrossDone);
    btSolutions = findViewById(R.id.btDrillCrossSolutions);
    btStart = findViewById(R.id.btDrillCrossStart);
    extrasRow = findViewById(R.id.llDrillCrossExtras);
    repWash = findViewById(R.id.drillRepWash);

    btDone.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (betweenReps) {
          nextRep();
        } else {
          declareFinished();
        }
      }
    });
    btSolutions.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showSolutions();
      }
    });
    btStart.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        backToStart();
      }
    });
    findViewById(R.id.btDrillDone).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        finish();
      }
    });

    DrillSpec spec;
    try {
      spec = DrillSpec.fromJson(getIntent().getStringExtra(EXTRA_SPEC));
      session = new CrossDrillSession(spec);
      face = CrossFace.valueOf(spec.getCrossFace());
    } catch (RuntimeException e) {
      session = null; // half-read is not runnable, and nothing may take it for a drill in progress
      showUnavailable(getString(R.string.drill_spec_unreadable));
      return;
    }
    label = spec.getLabel() == null ? getString(R.string.drill_cross_title) : spec.getLabel();
    setTitle(label);

    startWhenCubeConnected();
  }

  @Override
  protected void startDrill() {
    nextRep();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    handler.removeCallbacksAndMessages(null);
    DrillRepFlourish.cancel(repWash, lastRepRow);
  }

  @Override
  protected boolean isDrillRunning() {
    return session != null;
  }

  /** The scramble is on screen, which is where the planning runs from. */
  @Override
  protected void onCaseVisible() {
    if (session.isRunning()) {
      startPlanning();
    }
  }

  /**
   * A turn made before the scramble is on screen is dropped, not queued. The first one of a rep
   * takes the colours with it: from there the cross is built from what was read, not from what can
   * still be seen.
   */
  @Override
  public void onMove(CubeMove move) {
    if (!cubeReady || finished || session == null) {
      return;
    }
    // The rep is over but the cube is still theirs to turn: this is where they try the short way,
    // or find out what their own extra move did. Nothing is timed and nothing is scored.
    if (!session.isRunning()) {
      cube.addMove(move.getNotation());
      session.explore(move);
      return;
    }
    boolean first = session.getMoveCount() == 0;
    cube.addMove(move.getNotation());
    CrossDrillRep rep = session.onMove(move);
    if (first) {
      handler.removeCallbacks(planningRanOut);
    }
    if (rep != null) {
      onRepFinished(rep); // which brings the colours back, so greying first would only flicker
      return;
    }
    if (first) {
      cube.setStickering(CubeStickering.allGrey());
    }
    showStatus(getString(R.string.drill_cross_moves, session.getMoveCount()));
    armStalledHint();
  }

  /** The user says they are finished. A cross that is really there ends the same way either way. */
  private void declareFinished() {
    CrossDrillRep rep = session.declareFinished();
    if (rep != null) {
      onRepFinished(rep);
    }
  }

  private void onRepFinished(CrossDrillRep rep) {
    handler.removeCallbacks(planningRanOut);
    handler.removeCallbacks(stalledHint);
    betweenReps = true;

    // The cross comes back and nothing else does. Every rep ends looking like the one thing the
    // drill was about, whether it was found the short way or the long one, and the four edges stay
    // lit through whatever is turned next so their own extra move can be seen undoing it.
    cube.setStickering(CubeStickering.crossAndCentres(session.getCrossEdges()));
    if (rep.isBuilt()) {
      DrillRepFlourish.play(repWash, lastRepRow);
    }
    showLastRep(rep);
    showBetweenReps(rep);
    btDone.setText(R.string.drill_cross_next);
    btStart.setVisibility(View.VISIBLE);
    refreshExtrasRow();
  }

  /**
   * What the rep cost in time, and the shortest way there was when it was not the way taken.
   *
   * <p>The solution is put in front of the user rather than behind a button: the rep is over, so
   * there is nothing left to give away, and a sequence you have to go and ask for is one you will
   * not try. It carries its length, since what the drill scores is a move count and the answer to
   * "how many did I go over by" should not have to be counted off the notation.
   */
  private void showBetweenReps(CrossDrillRep rep) {
    StringBuilder detail = new StringBuilder(getString(R.string.drill_cross_rep_times,
        FormatterService.INSTANCE.formatSolveTime(rep.getPlanningMs()),
        FormatterService.INSTANCE.formatSolveTime(rep.getExecutionMs())));
    boolean missedIt = !rep.isBuilt() || rep.getExtraMoves() > 0;
    if (missedIt && solutions != null && !solutions.solutions.isEmpty()) {
      detail.append("\n").append(getString(R.string.drill_cross_shortest,
          join(CrossFormatter.toCrossOnBottom(face, solutions.solutions.get(0))),
          solutions.length));
    }
    showStatus(detail.toString());
    btSolutions.setVisibility(
        missedIt && solutions != null && solutions.solutions.size() > 1 ? View.VISIBLE : View.GONE);
    refreshExtrasRow();
  }

  /** The row goes with its contents, or its top margin would leave a gap during a rep. */
  private void refreshExtrasRow() {
    extrasRow.setVisibility(
        btStart.getVisibility() == View.VISIBLE || btSolutions.getVisibility() == View.VISIBLE
            ? View.VISIBLE : View.GONE);
  }

  /**
   * Puts the scramble back as it was dealt, so the short way can be turned on it and watched. The
   * only rewind this drill has, and only between reps: mid-rep it would hand back the look the cube
   * going grey just took away.
   */
  private void backToStart() {
    session.resetToStart();
    cube.setState(CubePatternFormat.format(session.getFacelets()));
  }

  private void nextRep() {
    betweenReps = false;
    solutions = null;
    // The verdict belonged to the cross that is over. Left up, "That was not the cross" reads as a
    // verdict on the scramble about to be dealt.
    clearLastRep();
    btSolutions.setVisibility(View.GONE);
    btStart.setVisibility(View.GONE);
    refreshExtrasRow();
    btDone.setText(R.string.drill_cross_done);
    if (session.isFinished()) {
      showSummary();
      return;
    }
    showStatus(getString(R.string.drill_cross_dealing));
    dealScramble(0);
  }

  /**
   * Fetches a scramble off the UI thread and puts it up, redealing one whose cross happens to be
   * there already, since that is a rep of nothing. Bounded because a scrambler handing back nothing
   * would otherwise redeal for ever.
   */
  private void dealScramble(final int attempt) {
    final int seq = ++repSeq;
    new Thread(new Runnable() {
      @Override
      public void run() {
        final String scramble = nextScramble();
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            if (seq != repSeq || finished || isFinishing()) {
              return;
            }
            // No scramble to be had ends the drill where it stands rather than hanging on one:
            // the reps already done are the result, which is the rule everything else here follows.
            if (scramble == null || !session.nextRep(scramble)) {
              showSummary();
              return;
            }
            if (session.isCrossBuilt() && attempt < MAX_DEAL_ATTEMPTS) {
              dealScramble(attempt + 1);
              return;
            }
            showScramble(scramble);
          }
        });
      }
    }).start();
  }

  private String nextScramble() {
    String[] scramble = ScramblerService.INSTANCE.getScramble(CubeType.THREE_BY_THREE, null);
    if (scramble == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    for (String move : scramble) {
      sb.append(sb.length() == 0 ? "" : " ").append(move);
    }
    return sb.toString();
  }

  /** Four edges and six centres, and nothing else readable, which is the case to be planned. */
  private void showScramble(String scramble) {
    cube.setState(CubePatternFormat.format(session.getFacelets()));
    cube.setStickering(CubeStickering.crossAndCentres(session.getCrossEdges()));
    setProgress(session.getReps().size() + 1, session.getSpec().getReps());
    solveInBackground(scramble, repSeq);
    if (cubeReady) {
      startPlanning();
    }
  }

  /**
   * The search runs while the user looks, so the answer is waiting the moment they finish and
   * nobody is left watching a spinner having just done the work.
   */
  private void solveInBackground(final String scramble, final int seq) {
    new Thread(new Runnable() {
      @Override
      public void run() {
        final FaceSolutions found = solvers.solveFace(face, scramble);
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            if (seq != repSeq || finished) {
              return; // a search that landed after its own rep was replaced, or after the drill
            }
            solutions = found;
            session.setOptimalLength(found.length);
            if (betweenReps && !session.getReps().isEmpty()) {
              // Landed after its own rep ended, so the line it belongs under is already up.
              List<CrossDrillRep> reps = session.getReps();
              showLastRep(reps.get(reps.size() - 1));
              showBetweenReps(reps.get(reps.size() - 1));
            }
          }
        });
      }
    }).start();
  }

  private void startPlanning() {
    session.markCaseShown(System.currentTimeMillis());
    long limit = session.getSpec().getPlanningMs();
    handler.removeCallbacks(planningRanOut);
    if (limit > 0) {
      countdownFrom(limit);
      handler.postDelayed(planningRanOut, limit);
    } else {
      showStatus(getString(R.string.drill_cross_plan_it));
    }
  }

  /** Seconds left to look, redrawn each one, and only while nothing has been turned. */
  private void countdownFrom(final long remaining) {
    showStatus(getString(R.string.drill_cross_planning_left, (int) Math.ceil(remaining / 1000d)));
    if (remaining > 1000) {
      handler.postDelayed(new Runnable() {
        @Override
        public void run() {
          if (session.isRunning() && session.getMoveCount() == 0) {
            countdownFrom(remaining - 1000);
          }
        }
      }, 1000);
    }
  }

  /** Time is up: the colours go, exactly as a first turn would have taken them. */
  private final Runnable planningRanOut = new Runnable() {
    @Override
    public void run() {
      if (session.isRunning() && session.getMoveCount() == 0) {
        session.markPlanningExpired();
        cube.setStickering(CubeStickering.allGrey());
        showStatus(getString(R.string.drill_cross_planning_over));
      }
    }
  };

  private void armStalledHint() {
    handler.removeCallbacks(stalledHint);
    handler.postDelayed(stalledHint, STALLED_MS);
  }

  /**
   * They have stopped turning, which usually means they think they are done. Saying what the cube
   * really shows is the only thing the screen knows that they do not; it does not end the rep, since
   * announcing the finish stays theirs.
   */
  private final Runnable stalledHint = new Runnable() {
    @Override
    public void run() {
      if (session.isRunning() && session.getMoveCount() > 0 && !session.isCrossBuilt()) {
        showStatus(getString(R.string.drill_cross_not_yet, session.getMoveCount()));
      }
    }
  };

  private void showStatus(String text) {
    tvStatus.setText(text);
  }

  /** The rep that has just ended, scored on its moves against the fewest there were. */
  private void showLastRep(CrossDrillRep rep) {
    if (!rep.isBuilt()) {
      // Stood down rather than announced: there is no move count to read, only a verdict.
      setLastRep(null, 0, getString(R.string.drill_cross_rep_missed), true, null);
      return;
    }
    String moves = getString(R.string.drill_cross_rep_moves, rep.getMoveCount());
    String against = null;
    if (rep.getOptimalLength() > 0) {
      against = rep.getExtraMoves() == 0 ? getString(R.string.drill_cross_rep_shortest)
          : getString(R.string.drill_cross_rep_way_in, rep.getOptimalLength());
    }
    setLastRep(null, 0, moves, false, against);
  }

  private void showSolutions() {
    if (solutions == null) {
      return;
    }
    ArrayList<String> lines = new ArrayList<String>();
    for (String[] moves : solutions.solutions) {
      lines.add(join(CrossFormatter.toCrossOnBottom(face, moves)));
    }
    DialogUtils.showFragment(this,
        CrossSolutionsDialog.newInstance(lines, solutions.length));
  }

  private static String join(String[] moves) {
    StringBuilder sb = new StringBuilder();
    for (String move : moves) {
      if (!move.isEmpty()) {
        sb.append(sb.length() == 0 ? "" : " ").append(move);
      }
    }
    return sb.toString();
  }

  @Override
  protected void showSummary() {
    if (finished) {
      return; // a cube unplugged on the summary screen must not re-run this over its own figures
    }
    finished = true;
    handler.removeCallbacksAndMessages(null);
    showSummaryFor(label);

    List<CrossDrillRep> reps = session.getReps();
    setSummaryCell(0, getString(R.string.drill_summary_cell_reps), String.valueOf(reps.size()),
        getString(R.string.drill_summary_cell_of, session.getSpec().getReps()));

    // A rep whose cross was not there is counted apart rather than folded in: its moves went
    // somewhere else, and averaging them in would flatter a drill that kept missing.
    int built = 0;
    int optimal = 0;
    int extraTotal = 0;
    long planningTotal = 0;
    for (CrossDrillRep rep : reps) {
      if (!rep.isBuilt()) {
        continue;
      }
      built++;
      extraTotal += rep.getExtraMoves();
      planningTotal += rep.getPlanningMs();
      if (rep.getExtraMoves() == 0 && rep.getOptimalLength() > 0) {
        optimal++;
      }
    }
    if (built == 0) {
      showSummaryEmpty(R.string.drill_cross_summary_none_built);
      return;
    }
    String average = getString(R.string.drill_summary_cell_average);
    setSummaryCell(1, getString(R.string.drill_summary_cell_extra),
        String.format("%.1f", extraTotal / (double) built), average);
    setSummaryCell(2, getString(R.string.drill_summary_cell_planning),
        FormatterService.INSTANCE.formatSolveTime(planningTotal / built), average);
    setSummaryExtra(getString(R.string.drill_cross_summary_optimal, optimal, built));
  }
}
