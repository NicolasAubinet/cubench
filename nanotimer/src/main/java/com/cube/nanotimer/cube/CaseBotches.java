package com.cube.nanotimer.cube;

import android.util.Log;

import com.cube.nanotimer.App;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.smartcube.step.CaseBotch;
import com.cube.nanotimer.smartcube.step.CmllCases;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveStep;
import com.cube.nanotimer.vo.SolveTime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Keeps the one thing worth keeping of a solve the user throws away: the last layer case it stopped
 * inside, when that case's algorithm went wrong ({@link CaseBotch}).
 *
 * <p>A solve gets discarded or deleted because something went wrong, and the case that went wrong
 * is the evidence a coach most wants. The time and the moves still go, as the user asked; only the
 * case, the verdict and the date stay behind, in {@code case_botched}.
 *
 * <p>Every way a solve is thrown away comes here: the cross and the back button during a run, the
 * discard after a stop, and a delete from the timer or from the history.
 */
public final class CaseBotches {

  private static final String TAG = "CaseBotch";

  /** How many of the case's recent solves are read for the solver's own algorithms. */
  private static final int OWN_SOLVES_READ = 10;

  private CaseBotches() {
  }

  /** The case a solve stopped inside and what was turned in it, as read from the solve. */
  static final class Stopped {
    final String caseCode;
    /** What was turned in the case, or null where a first look was already done in it. */
    final String moves;
    /** The first of two looks, where one was done before the solve stopped. */
    final String firstLook;

    Stopped(String caseCode, String moves, String firstLook) {
      this.caseCode = caseCode;
      this.moves = moves;
      this.firstLook = firstLook;
    }
  }

  /**
   * Reads the thrown-away solve in the background, and stores its case if the algorithm went wrong.
   * Safe to call for any solve: one no cube timed, or one that did not stop in a last layer case,
   * leaves nothing behind.
   */
  public static void record(final SolveTime solve) {
    if (solve == null || solve.getSmartcubeMoves() == null || solve.getSmartcubeMoves().isEmpty()
        || solve.getSolveType() == null) {
      return;
    }
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          judgeAndStore(solve);
        } catch (RuntimeException e) {
          Log.w(TAG, "could not read the discarded solve", e); // a lost signal, never a crash
        }
      }
    }).start();
  }

  private static void judgeAndStore(final SolveTime solve) {
    final Stopped stopped = stoppedIn(solve);
    if (stopped == null) {
      Log.i(TAG, "discarded solve did not stop inside a last layer case");
      return;
    }
    App.INSTANCE.getService().isCaseLearned(stopped.caseCode, new DataCallback<Boolean>() {
      @Override
      public void onData(final Boolean learned) {
        if (stopped.moves == null) {
          firstLookDone(solve, stopped, Boolean.TRUE.equals(learned));
          return;
        }
        App.INSTANCE.getService().getCaseSolves(CaseExecutions.codesFor(stopped.caseCode),
            OWN_SOLVES_READ, new DataCallback<List<SolveTime>>() {
              @Override
              public void onData(final List<SolveTime> solves) {
                new Thread(new Runnable() { // replaying them is not work for the database thread
                  @Override
                  public void run() {
                    try {
                      judge(solve, stopped, solves, Boolean.TRUE.equals(learned));
                    } catch (RuntimeException e) {
                      Log.w(TAG, "could not judge the discarded solve", e);
                    }
                  }
                }).start();
              }
            });
      }
    });
  }

  private static void firstLookDone(SolveTime solve, Stopped stopped, boolean learned) {
    Log.i(TAG, stopped.caseCode + (learned ? " (learnt) " + CaseBotch.Verdict.TWO_LOOKED
        : " first look done, not learnt") + ": " + stopped.firstLook);
    if (learned) {
      store(solve, stopped.caseCode, CaseBotch.Verdict.TWO_LOOKED, null);
    }
  }

  private static void judge(SolveTime solve, Stopped stopped, List<SolveTime> solves,
      boolean learned) {
    CaseBotch botch = CaseBotch.judge(stopped.caseCode, stopped.moves,
        own(solves, stopped.caseCode), learned);
    Log.i(TAG, stopped.caseCode + (learned ? " (learnt) " : " ") + botch.getVerdict()
        + (botch.getMisreadAs() == null ? "" : " as " + botch.getMisreadAs())
        + ": " + stopped.moves);
    if (botch.counts()) {
      store(solve, stopped.caseCode, botch.getVerdict(), botch.getMisreadAs());
    }
  }

  private static void store(SolveTime solve, String caseCode, CaseBotch.Verdict verdict,
      String misreadAs) {
    // the solve's own time: a solve deleted from the history months later botched back then
    long atMs = solve.getTimestamp() > 0 ? solve.getTimestamp() : System.currentTimeMillis();
    App.INSTANCE.getService().recordCaseBotched(caseCode, verdict.code(), misreadAs,
        solve.getSolveType().getId(), atMs, null);
  }

  /**
   * The last layer case the solve stopped inside, with the moves turned in it, or null: a solve that
   * ran to the end, stopped in another step, or cannot be read again.
   */
  static Stopped stoppedIn(SolveTime solve) {
    CubeMethod method = SolveTypeMethod.of(solve.getSolveType());
    if (method != CubeMethod.CFOP && method != CubeMethod.ROUX) {
      return null;
    }
    CaseExecutions.Replay replay = CaseExecutions.replayAs(solve, method);
    if (replay == null || replay.getReread().getStoppedStep() == null) {
      return null;
    }
    String caseCode = CaseExecutions.caseOfStep(replay.getReread().getStoppedStepName());
    if (caseCode == null || (method == CubeMethod.ROUX) != CmllCases.isCase(caseCode)) {
      return null;
    }
    int stoppedStep = replay.getReread().getStoppedStep();
    List<SolveStep> steps = replay.getSteps();
    if (steps.isEmpty()) { // a last layer scramble stopped in its first case: all of it is that case
      steps = Collections.singletonList(new SolveStep(stoppedStep, SolveBreakdown.UNFINISHED_STEP,
          0, SolveBreakdown.solvingDurationMs(solve), new ArrayList<SolveStep>()));
    }
    if (!SolveBreakdown.UNFINISHED_STEP.equals(steps.get(steps.size() - 1).getName())) {
      return null; // no turning after the last milestone
    }
    SolveSolution solution = SolveSolution.from(replay.getMoves(), steps,
        replay.getReread().getMethod());
    List<SolveSolution.Step> turned = solution.getSteps();
    if (turned.size() != steps.size()) {
      return null;
    }
    for (int i = 0; i < steps.size() - 1; i++) { // the tail carries the stopped index too
      if (steps.get(i).getStepIndex() == stoppedStep) {
        // a two-look case with its first look done: the tail is not the case it was handed
        String firstLook = turned.get(i).getPartMoves(0);
        if (turnCount(firstLook) >= CaseBotch.MIN_TURNS) { // not a look done on arrival (a Sune CMLL)
          return new Stopped(caseCode, null, firstLook);
        }
      }
    }
    String moves = turned.get(turned.size() - 1).getPartMoves(0);
    return moves.trim().isEmpty() ? null : new Stopped(caseCode, moves, null);
  }

  private static int turnCount(String moves) {
    try {
      return CaseBotch.turnCount(moves);
    } catch (RuntimeException e) {
      return 0;
    }
  }

  /**
   * The solver's own executions of the case, from the recent solves it came up in. One turned once
   * is left out, as the case sheet leaves it out: a misread rebuilt into a solve is not a habit.
   */
  static List<String> own(List<SolveTime> solves, String caseCode) {
    if (solves == null || solves.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> own = new ArrayList<String>();
    for (CaseExecutions.Shown shown
        : CaseExecutions.shownFrom(caseCode, CaseExecutions.spreadFrom(solves, caseCode))) {
      own.add(shown.getExecuted());
    }
    return own;
  }
}
