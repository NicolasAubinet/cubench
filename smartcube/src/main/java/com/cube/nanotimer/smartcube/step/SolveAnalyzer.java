package com.cube.nanotimer.smartcube.step;

import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;
import java.util.ArrayList;
import java.util.Collections;
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
  private final List<CubeMove> moves = new ArrayList<>();

  private long solveStartMs;
  private CubeMove pendingMove;

  public SolveAnalyzer(StepDetector detector) {
    this.detector = detector;
  }

  /** Begin a solve. {@code startTimestampMs} is the cube-clock time of the move that started it. */
  public void start(CubeState startState, long startTimestampMs) {
    moves.clear();
    solveStartMs = startTimestampMs;
    pendingMove = null;
    detector.reset(startState, startTimestampMs);
  }

  public void onMove(CubeMove move) {
    pendingMove = move;
    moves.add(move);
  }

  public void onState(CubeState state) {
    detector.onState(state, pendingMove);
    pendingMove = null;
  }

  /** The solve's moves in order, timestamped on the cube clock. */
  public List<CubeMove> getMoves() {
    return Collections.unmodifiableList(moves);
  }

  /** The origin the step durations are measured from, so a move's offset into the solve is
   * {@code getCubeTimestampMs() - getSolveStartMs()}. */
  public long getSolveStartMs() {
    return solveStartMs;
  }

  /**
   * The steps reached, in order, ending in the one the solve stopped inside if it holds any finished
   * parts. On an unfinished solve they stop short of the whole: the turning after the last milestone
   * belongs to no step, and is left for the display to derive from {@link #getStoppedStep()}.
   */
  public List<StepTime> getStepTimes() {
    List<StepTime> times = new ArrayList<>();
    long previousCompleteMs = solveStartMs;
    for (int step = 0; step < detector.stepCount(); step++) {
      Long completeMs = detector.getStepTimestampMs(step);
      if (completeMs == null) {
        StepTime partial = partialStep(step, previousCompleteMs);
        if (partial != null) {
          times.add(partial);
        }
        break;
      }
      List<StepTime> subSteps = splitSubSteps(step, previousCompleteMs, completeMs);
      times.add(subSteps.isEmpty()
          ? timeFor(step, step, detector.stepName(step), previousCompleteMs, completeMs, step == 0,
              subSteps)
          : sumOf(step, detector.stepName(step), subSteps, worthSplitting(subSteps), true));
      previousCompleteMs = completeMs;
    }
    return times;
  }

  /**
   * The step the solve was in when it stopped, or null when it ran to the end. The steps account for
   * the whole solve exactly when this is null, so it is also what says a tail has to be drawn.
   */
  public Integer getStoppedStep() {
    for (int step = 0; step < detector.stepCount(); step++) {
      if (detector.getStepTimestampMs(step) == null) {
        return step;
      }
    }
    return null;
  }

  /**
   * The step the solve stopped inside: the parts it did finish, ending at the last of them. Null when
   * it got nowhere, or has no parts to report (a botched PLL) — then it is all tail. A lone part is
   * kept rather than collapsed: it is the only record of a step that reached no milestone.
   */
  private StepTime partialStep(int step, long previousCompleteMs) {
    Long lastPartMs = null;
    for (int subStep = 0; subStep < detector.subStepCount(step); subStep++) {
      Long partMs = detector.getSubStepTimestampMs(step, subStep);
      if (partMs != null && (lastPartMs == null || partMs > lastPartMs)) {
        lastPartMs = partMs;
      }
    }
    if (lastPartMs == null) {
      return null;
    }
    List<StepTime> subSteps = splitSubSteps(step, previousCompleteMs, lastPartMs);
    return subSteps.isEmpty() ? null
        : sumOf(step, detector.stepName(step), subSteps, true, false);
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
      subSteps.add(timeFor(step, subStep, detector.subStepName(step, subStep), previousMs,
          subCompleteMs, step == 0 && i == 0, new ArrayList<>()));
      previousMs = subCompleteMs;
    }
    return subSteps;
  }

  private StepTime timeFor(int step, int index, String name, long previousCompleteMs, long completeMs,
      boolean includeStartMove, List<StepTime> subSteps) {
    Long firstMoveMs = firstMoveIn(step, previousCompleteMs, completeMs, includeStartMove);
    long recognitionMs = 0;
    long executionMs = 0;
    if (firstMoveMs != null) { // else it has no moves of its own: a skip
      recognitionMs = firstMoveMs - previousCompleteMs;
      executionMs = completeMs - firstMoveMs;
    }
    return new StepTime(index, name, recognitionMs, executionMs, subSteps);
  }

  private static StepTime sumOf(int step, String name, List<StepTime> subSteps, boolean split,
      boolean complete) {
    long recognitionMs = 0;
    long executionMs = 0;
    for (StepTime subStep : subSteps) {
      recognitionMs += subStep.getRecognitionMs();
      executionMs += subStep.getExecutionMs();
    }
    return new StepTime(step, name, recognitionMs, executionMs,
        split ? subSteps : new ArrayList<>(), complete);
  }

  /** Only one part actually done — a one-look OLL or PLL, or a step the scramble half-gave: the
   * step is the part, and splitting it would invent a structure the solve did not have. */
  private static boolean worthSplitting(List<StepTime> subSteps) {
    int done = 0;
    for (StepTime subStep : subSteps) {
      if (subStep.getTotalMs() > 0) {
        done++;
      }
    }
    return done > 1;
  }

  /**
   * The move a step's execution starts on: the first one after the step before it, skipping any AUF
   * the solver made to read the case — that alignment is part of recognising it. A step made only of
   * alignment moves (a skip left with just an AUF) still starts on the first of them.
   */
  private Long firstMoveIn(int step, long previousCompleteMs, long completeMs, boolean includeStart) {
    CubeMove first = null;
    for (CubeMove move : moves) {
      long timestampMs = move.getCubeTimestampMs();
      boolean withinStep = timestampMs > previousCompleteMs
          || (includeStart && timestampMs == previousCompleteMs);
      if (!withinStep || timestampMs > completeMs) {
        continue;
      }
      if (first == null) {
        first = move;
      }
      if (!detector.isAlignmentMove(step, move)) {
        return move.getCubeTimestampMs();
      }
    }
    return first == null ? null : first.getCubeTimestampMs();
  }

  public boolean isComplete() {
    return detector.isComplete();
  }

  /** Whether the solve matched the detector's method (§7b). Meaningful once {@link #isComplete()}. */
  public boolean matchesMethod() {
    return detector.matchesMethod();
  }
}
