package com.cube.nanotimer.cube;

import com.cube.nanotimer.cube.SolveMovesFormat.Move;
import com.cube.nanotimer.vo.SolveStep;
import com.cube.nanotimer.vo.SolveTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Assembles the step lists a solve is displayed through: the user's own steps from their tap times,
 * and the cube's own, completed by adding back what is deliberately not stored — the tail
 * of a solve the cube never saw finish. The steps run back to back from the start, so whatever the
 * solve lasted beyond their total is turning that reached no milestone — a botched PLL, a blind
 * attempt that came out wrong. Deriving it keeps the step table free of a row that is not a step.
 */
public final class SolveBreakdown {

  /** Name code of the tail, localized when displayed. */
  public static final String UNFINISHED_STEP = "unfinished";

  private SolveBreakdown() {
  }

  /**
   * How long a stored solve was spent turning, which its recorded time is not: a +2 inflates it by a
   * penalty, and a DNF replaces it with a sentinel. A DNF falls back to its last move — a floor, since
   * nothing records the staring after it. What both the tail and the turn rate are measured against.
   */
  public static long solvingDurationMs(SolveTime solveTime) {
    if (!solveTime.isDNF()) {
      return solveTime.getTime() - (solveTime.isPlusTwo() ? SolveTime.PLUS_TWO_PENALTY_MS : 0);
    }
    return lastMoveOffsetMs(solveTime.getSmartcubeMoves());
  }

  /**
   * @param solveDurationMs what the solve spent turning, from {@link #solvingDurationMs} for a stored
   *     solve, or straight off the timer for the one just finished
   * @param stoppedStep the step the solve stopped in, null when it ran to the end (then the steps
   *     already account for all of it and the list is returned untouched)
   */
  public static List<SolveStep> withUnfinishedTail(List<SolveStep> steps, Integer stoppedStep,
      long solveDurationMs, String moves) {
    if (steps == null) {
      return null;
    }
    if (stoppedStep == null || steps.isEmpty()) { // always a copy, so no caller can alias the input
      return new ArrayList<SolveStep>(steps);
    }
    long accountedMs = 0;
    int index = 0;
    for (SolveStep step : steps) {
      accountedMs += step.getTotalMs();
      index = step.getStepIndex() + 1;
    }
    long tailMs = solveDurationMs - accountedMs;
    if (tailMs <= 0) { // the timer stopped before the last milestone reached us: only clock skew
      return new ArrayList<SolveStep>(steps);
    }
    long recognitionMs = recognitionOf(moves, accountedMs, tailMs);
    List<SolveStep> result = new ArrayList<SolveStep>(steps);
    result.add(new SolveStep(index, UNFINISHED_STEP, recognitionMs, tailMs - recognitionMs,
        new ArrayList<SolveStep>()));
    return result;
  }

  /**
   * The user's own steps as a breakdown, so the recorded moves can be split at the taps that ended
   * them. The taps are the only boundaries, which is what lets this work without knowing what a step
   * means — no name, and no thinking/turning split: a tap says when a step ended, nothing more.
   *
   * <p>Approximate by design, unlike the state-derived method split: a tap lands after the move it
   * follows, and it is timed on the phone's clock while the moves are timed on the cube's.
   */
  public static List<SolveStep> fromStepTimes(Long[] stepTimes) {
    List<SolveStep> steps = new ArrayList<SolveStep>();
    if (stepTimes == null) {
      return steps;
    }
    for (int i = 0; i < stepTimes.length; i++) {
      long durationMs = stepTimes[i] == null ? 0 : stepTimes[i];
      steps.add(new SolveStep(i, "", 0, durationMs, new ArrayList<SolveStep>()));
    }
    return steps;
  }

  private static long lastMoveOffsetMs(String moves) {
    List<Move> parsed = SolveMovesFormat.parse(moves);
    return parsed.isEmpty() ? 0 : parsed.get(parsed.size() - 1).getOffsetMs();
  }

  /** The wait before the tail's first move, so a long stare reads as thinking rather than turning. */
  private static long recognitionOf(String moves, long fromMs, long tailMs) {
    for (Move move : SolveMovesFormat.parse(moves)) {
      if (move.getOffsetMs() > fromMs) {
        return Math.max(0, Math.min(tailMs, move.getOffsetMs() - fromMs));
      }
    }
    return tailMs; // it turned nothing after the milestone: all of it was staring
  }
}
