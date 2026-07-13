package com.cube.nanotimer.smartcube.step;

import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;
import java.util.ArrayList;
import java.util.Comparator;
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
      List<StepTime> subSteps = splitSubSteps(step, previousCompleteMs, completeMs);
      times.add(subSteps.isEmpty()
          ? timeFor(step, detector.stepName(step), previousCompleteMs, completeMs, step == 0, subSteps)
          : sumOf(step, detector.stepName(step), subSteps));
      previousCompleteMs = completeMs;
    }
    return times;
  }

  /** A step's parts, oldest first: each one's thinking and turning, measured from the one before. */
  private List<StepTime> splitSubSteps(int step, long previousCompleteMs, long completeMs) {
    List<Integer> order = new ArrayList<>();
    for (int subStep = 0; subStep < detector.subStepCount(step); subStep++) {
      if (detector.getSubStepTimestampMs(step, subStep) != null) {
        order.add(subStep);
      }
    }
    order.sort(Comparator.comparing(subStep -> detector.getSubStepTimestampMs(step, subStep)));

    List<StepTime> subSteps = new ArrayList<>();
    long previousMs = previousCompleteMs;
    for (int i = 0; i < order.size(); i++) {
      int subStep = order.get(i);
      boolean last = i == order.size() - 1;
      // The last part completes with the step itself, so the parts always account for all of it.
      long subCompleteMs = last
          ? completeMs
          : Math.max(previousMs, detector.getSubStepTimestampMs(step, subStep));
      subSteps.add(timeFor(subStep, detector.subStepName(step, subStep), previousMs, subCompleteMs,
          step == 0 && i == 0, new ArrayList<>()));
      previousMs = subCompleteMs;
    }
    return subSteps;
  }

  private StepTime timeFor(int index, String name, long previousCompleteMs, long completeMs,
      boolean includeStartMove, List<StepTime> subSteps) {
    Long firstMoveMs = firstMoveAfter(previousCompleteMs, includeStartMove);
    long recognitionMs = 0;
    long executionMs = 0;
    if (firstMoveMs != null && firstMoveMs <= completeMs) { // else it has no moves of its own: a skip
      recognitionMs = firstMoveMs - previousCompleteMs;
      executionMs = completeMs - firstMoveMs;
    }
    return new StepTime(index, name, recognitionMs, executionMs, subSteps);
  }

  private static StepTime sumOf(int step, String name, List<StepTime> subSteps) {
    long recognitionMs = 0;
    long executionMs = 0;
    for (StepTime subStep : subSteps) {
      recognitionMs += subStep.getRecognitionMs();
      executionMs += subStep.getExecutionMs();
    }
    return new StepTime(step, name, recognitionMs, executionMs, subSteps);
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
