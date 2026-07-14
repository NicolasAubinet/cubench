package com.cube.nanotimer.smartcube.step;

import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;
import java.util.List;

/**
 * Watches the live cube state during a solve and reports when each step of a solving method
 * (CFOP, Roux, ZZ...) is reached. Implementations are pure — state in, step boundaries out.
 */
public interface StepDetector {

  /** Start a new solve from the scrambled state. Steps already complete are dated to the start. */
  void reset(CubeState startState, long startTimestampMs);

  /** Feed one state; returns the steps whose completion time changed, in step order. */
  List<StepBoundaryEvent> onState(CubeState state, CubeMove lastMove);

  int stepCount();

  String stepName(int index);

  /** Cube-clock time at which the step was reached, or null while it is still pending. */
  Long getStepTimestampMs(int index);

  /**
   * True when the move only realigns the last layer to read a case (an AUF), rather than building
   * the step. Such moves opening a step count as recognition, not execution.
   */
  boolean isAlignmentMove(int step, CubeMove move);

  /** The parts a step is built in, if any: they complete in whatever order the solver works in. */
  int subStepCount(int step);

  /**
   * @param position the part's rank in the order the solver completed them, so a method whose parts
   *     have no identity worth reporting (an F2L pair: the cube's slot numbering is not something the
   *     solver can act on) can name them by when they were built instead
   */
  String subStepName(int step, int subStep, int position);

  /** Cube-clock time the sub-step was reached, or null while its step is still pending. */
  Long getSubStepTimestampMs(int step, int subStep);

  boolean isComplete();
}
