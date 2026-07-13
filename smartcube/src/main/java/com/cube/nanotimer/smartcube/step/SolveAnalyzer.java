package com.cube.nanotimer.smartcube.step;

import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a solve's move and state stream into a per-step breakdown, by pairing a {@link StepDetector}'s
 * boundaries with the moves that produced them. Each step is split at its first move: the wait before
 * it is recognition, the turning after it is execution.
 *
 * <p>Feed every move to {@link #onMove} and the state it produced to {@link #onState}, in that order.
 * The split is derived from the boundaries on demand, so it stays correct when confirming the cross
 * face revises a step the detector had already reported. Pure — the timer screen owns the wiring.
 */
public final class SolveAnalyzer {

  private final StepDetector detector;
  private final List<Long> moveTimestampsMs = new ArrayList<>();

  private long solveStartMs;
  private CubeMove pendingMove;

  public SolveAnalyzer(StepDetector detector) {
    this.detector = detector;
  }

  /** Begin a solve. {@code startTimestampMs} is the cube-clock time of the move that started it. */
  public void start(CubeState startState, long startTimestampMs) {
    moveTimestampsMs.clear();
    solveStartMs = startTimestampMs;
    pendingMove = null;
    detector.reset(startState, startTimestampMs);
  }

  public void onMove(CubeMove move) {
    pendingMove = move;
    moveTimestampsMs.add(move.getCubeTimestampMs());
  }

  public void onState(CubeState state) {
    detector.onState(state, pendingMove);
    pendingMove = null;
  }

  /** The steps reached so far, in order. */
  public List<StepTime> getStepTimes() {
    List<StepTime> times = new ArrayList<>();
    long previousCompleteMs = solveStartMs;
    for (int step = 0; step < detector.stepCount(); step++) {
      Long completeMs = detector.getStepTimestampMs(step);
      if (completeMs == null) {
        break;
      }
      Long firstMoveMs = firstMoveAfter(previousCompleteMs, step == 0);
      long recognitionMs = 0;
      long executionMs = 0;
      if (firstMoveMs != null && firstMoveMs <= completeMs) { // else the step has no moves: a skip
        recognitionMs = firstMoveMs - previousCompleteMs;
        executionMs = completeMs - firstMoveMs;
      }
      times.add(new StepTime(step, detector.stepName(step), recognitionMs, executionMs));
      previousCompleteMs = completeMs;
    }
    return times;
  }

  /** The first move of a step: the first one past the previous step, which the solve's own move opens. */
  private Long firstMoveAfter(long previousCompleteMs, boolean includeStart) {
    for (Long timestampMs : moveTimestampsMs) {
      if (timestampMs > previousCompleteMs || (includeStart && timestampMs == previousCompleteMs)) {
        return timestampMs;
      }
    }
    return null;
  }

  public boolean isComplete() {
    return detector.isComplete();
  }
}
