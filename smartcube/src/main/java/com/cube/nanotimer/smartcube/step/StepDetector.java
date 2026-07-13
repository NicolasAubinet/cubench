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

  /** The parts a step is built in, if any: they complete in whatever order the solver works in. */
  int subStepCount(int step);

  String subStepName(int step, int subStep);

  /** Cube-clock time the sub-step was reached, or null while its step is still pending. */
  Long getSubStepTimestampMs(int step, int subStep);

  boolean isComplete();
}
