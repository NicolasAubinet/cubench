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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps the one thing worth keeping of a solve the user throws away: the last layer cases whose
 * algorithm went wrong in it ({@link CaseBotch}).
 *
 * <p>A solve gets discarded or deleted because something went wrong, and the case that went wrong
 * is the evidence a coach most wants. The time and the moves still go, as the user asked; only the
 * case, the verdict and the date stay behind, in {@code case_botched}, and count towards the case's
 * knowledge as a saved solve gone wrong would: the coach and its daily drill read them from there.
 *
 * <p>Every way a solve is thrown away comes here: the cross and the back button during a run, the
 * discard after a stop, and a delete from the timer or from the history. So does a solve kept
 * though it stopped inside a case (a DNF kept as one), since case knowledge leaves that step out;
 * deleting it later stores nothing twice.
 *
 * <p>Two kinds of case are read. The one a solve stopped inside, judged on what was turned in it.
 * And, for a thrown-away solve that ran to the end, each case it took in more than one algorithm: a
 * botch repaired and finished is the usual reason to throw a solve away. Those never say more than
 * the solve would have said kept: only a solve that came out solved and is not a DNF, only a step
 * in parts as the solve's own reading has it (kept, a lapse), and the verdict can still find it
 * clean (a listed first look on a case not learnt, one of the case's own algorithms that passes
 * through a look). A solve stopped short gives only its stopped case, even kept without a DNF.
 */
public final class CaseBotches {

  private static final String TAG = "CaseBotch";

  /** How many of the case's recent solves are read for the solver's own algorithms. */
  private static final int OWN_SOLVES_READ = 10;

  private CaseBotches() {
  }

  /** A case of the solve and what was turned in it, as read from the solve. */
  static final class Attempt {
    final String caseCode;
    /** What was turned in the case, or after its first look where one was done. */
    final String moves;
    /** The first of two looks, where one was done before the solve stopped. */
    final String firstLook;

    Attempt(String caseCode, String moves, String firstLook) {
      this.caseCode = caseCode;
      this.moves = moves;
      this.firstLook = firstLook;
    }
  }

  /**
   * Reads the thrown-away solve in the background, and stores each of its cases whose algorithm
   * went wrong. Safe to call for any solve: one no cube timed, or one whose cases all went in
   * clean, leaves nothing behind.
   */
  public static void record(SolveTime solve) {
    record(solve, true);
  }

  /**
   * As {@link #record}, for a solve kept though it stopped short: only the case it stopped inside,
   * since its finished steps are saved with it and already count.
   */
  public static void recordKept(SolveTime solve) {
    record(solve, false);
  }

  private static void record(final SolveTime solve, final boolean thrownAway) {
    if (solve == null || solve.getSmartcubeMoves() == null || solve.getSmartcubeMoves().isEmpty()
        || solve.getSolveType() == null) {
      return;
    }
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          List<Attempt> attempts = attemptsIn(solve, thrownAway);
          if (attempts.isEmpty()) {
            Log.i(TAG, "solve has no last layer case to judge");
          }
          for (Attempt attempt : attempts) {
            judgeAndStore(solve, attempt);
          }
        } catch (RuntimeException e) {
          Log.w(TAG, "could not read the discarded solve", e); // a lost signal, never a crash
        }
      }
    }).start();
  }

  private static void judgeAndStore(final SolveTime solve, final Attempt attempt) {
    App.INSTANCE.getService().isCaseLearned(attempt.caseCode, new DataCallback<Boolean>() {
      @Override
      public void onData(final Boolean learned) {
        if (attempt.firstLook != null) {
          firstLookDone(solve, attempt, Boolean.TRUE.equals(learned));
          return;
        }
        App.INSTANCE.getService().getCaseSolves(CaseExecutions.codesFor(attempt.caseCode),
            OWN_SOLVES_READ, new DataCallback<List<SolveTime>>() {
              @Override
              public void onData(final List<SolveTime> solves) {
                new Thread(new Runnable() { // replaying them is not work for the database thread
                  @Override
                  public void run() {
                    try {
                      judge(solve, attempt, solves, Boolean.TRUE.equals(learned));
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

  /** Two-looked only on a learnt case, and once the second look has really started. */
  private static void firstLookDone(SolveTime solve, Attempt attempt, boolean learned) {
    boolean second = turnCount(attempt.moves) >= CaseBotch.MIN_TURNS;
    Log.i(TAG, attempt.caseCode + (learned ? " (learnt)" : " (not learnt)")
        + (learned && second ? " " + CaseBotch.Verdict.TWO_LOOKED : " first look, no verdict")
        + ": " + attempt.firstLook + " | " + attempt.moves);
    if (learned && second) {
      store(solve, attempt.caseCode, CaseBotch.Verdict.TWO_LOOKED, null);
    }
  }

  private static void judge(SolveTime solve, Attempt attempt, List<SolveTime> solves,
      boolean learned) {
    CaseBotch botch = CaseBotch.judge(attempt.caseCode, attempt.moves,
        own(solves, attempt.caseCode), learned);
    Log.i(TAG, attempt.caseCode + (learned ? " (learnt) " : " ") + botch.getVerdict()
        + (botch.getMisreadAs() == null ? "" : " as " + botch.getMisreadAs())
        + ": " + attempt.moves);
    if (botch.counts()) {
      store(solve, attempt.caseCode, botch.getVerdict(), botch.getMisreadAs());
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
   * The cases of the solve to judge: the one it stopped inside, and where {@code finishedToo}, each
   * one a finished solve took in more than one algorithm. Empty where it cannot be read again.
   */
  static List<Attempt> attemptsIn(SolveTime solve, boolean finishedToo) {
    List<Attempt> attempts = new ArrayList<Attempt>();
    CubeMethod method = methodOf(solve);
    CaseExecutions.Replay replay = method == null ? null : CaseExecutions.replayAs(solve, method);
    if (replay == null) {
      return attempts;
    }
    Attempt stopped = stoppedIn(solve, replay, method);
    if (stopped != null) {
      attempts.add(stopped);
    }
    if (finishedToo && solve.getTime() > 0) { // kept, a DNF's steps say nothing either
      attempts.addAll(finishedIn(replay, method, inPartsAsRead(solve)));
    }
    return attempts;
  }

  /** The last layer case the solve stopped inside, or null: see {@link #attemptsIn}. */
  static Attempt stoppedIn(SolveTime solve) {
    CubeMethod method = methodOf(solve);
    CaseExecutions.Replay replay = method == null ? null : CaseExecutions.replayAs(solve, method);
    return replay == null ? null : stoppedIn(solve, replay, method);
  }

  /** The method a solve's cases are read in, or null for one whose cases are not read. */
  private static CubeMethod methodOf(SolveTime solve) {
    CubeMethod method = SolveTypeMethod.of(solve.getSolveType());
    return method == CubeMethod.CFOP || method == CubeMethod.ROUX ? method : null;
  }

  /**
   * The last layer case the solve stopped inside, with the moves turned in it, or null: a solve that
   * ran to the end, or stopped in another step.
   */
  private static Attempt stoppedIn(SolveTime solve, CaseExecutions.Replay replay,
      CubeMethod method) {
    if (replay.getReread().getStoppedStep() == null) {
      return null;
    }
    String caseCode = CaseExecutions.caseOfStep(replay.getReread().getStoppedStepName());
    if (!judgedIn(caseCode, method)) {
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
    String moves = turned.get(turned.size() - 1).getPartMoves(0);
    for (int i = 0; i < steps.size() - 1; i++) { // the tail carries the stopped index too
      if (steps.get(i).getStepIndex() == stoppedStep) {
        // a finished part: a first look leaves the tail its second, anything else was this case's try
        String part = turned.get(i).getPartMoves(0);
        if (turnCount(part) >= CaseBotch.MIN_TURNS) { // not a look done on arrival (a Sune CMLL)
          return CaseBotch.isFirstLook(caseCode, part) ? new Attempt(caseCode, moves, part)
              : new Attempt(caseCode, part, null);
        }
      }
    }
    return moves.trim().isEmpty() ? null : new Attempt(caseCode, moves, null);
  }

  /**
   * The cases the solve's own reading, as saved or about to be, has in parts: those a kept solve
   * counts as lapses. A replay can cut a step differently, so it alone never adds one.
   */
  private static Set<String> inPartsAsRead(SolveTime solve) {
    Set<String> codes = new HashSet<String>();
    if (solve.getSmartcubeSteps() != null) {
      for (SolveStep step : solve.getSmartcubeSteps()) {
        if (step.getSubSteps() != null && step.getSubSteps().size() >= 2) {
          codes.add(step.getName());
        }
      }
    }
    return codes;
  }

  /**
   * Each last layer case a solve that ran to the end and came out solved took in parts, as read
   * again and as read at the time, with all its parts' moves; none for a solve stopped short.
   */
  private static List<Attempt> finishedIn(CaseExecutions.Replay replay, final CubeMethod method,
      final Set<String> inPartsAsRead) {
    final List<Attempt> inParts = new ArrayList<Attempt>();
    boolean whole = CaseExecutions.readSteps(replay, new CaseExecutions.StepReader() {
      @Override
      public void read(SolveStep step, SolveSolution.Step turned) {
        String caseCode = CaseExecutions.caseOfStep(step.getName());
        if (!judgedIn(caseCode, method) || step.getSubSteps().size() < 2
            || !inPartsAsRead.contains(caseCode)) {
          return; // kept, a step in one part is unaided: nothing to judge
        }
        StringBuilder moves = new StringBuilder(); // not getMoves(): its separator is for display
        for (int i = 0; i < turned.getGroups().size(); i++) {
          String part = turned.getPartMoves(i);
          if (!part.trim().isEmpty()) {
            moves.append(moves.length() == 0 ? "" : " ").append(part);
          }
        }
        inParts.add(new Attempt(caseCode, moves.toString(), null));
      }
    });
    return whole ? inParts : Collections.<Attempt>emptyList();
  }

  /** Whether the case is one a solve in this method is judged on: CMLL for Roux, OLL and PLL else. */
  private static boolean judgedIn(String caseCode, CubeMethod method) {
    return caseCode != null && (method == CubeMethod.ROUX) == CmllCases.isCase(caseCode);
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
      if (shown.getTimes() > 1) { // shownFrom keeps its top row even when turned once
        own.add(shown.getExecuted());
      }
    }
    return own;
  }
}
