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
   * The sub-step's code, localized when displayed. A method whose parts have no identity worth
   * reporting (an F2L pair: the cube's slot numbering is not something the solver can act on) names
   * them all alike and lets the display number them by their completed order (the sub-step's rank).
   */
  String subStepName(int step, int subStep);

  /** Cube-clock time the sub-step was reached, or null while its step is still pending. */
  Long getSubStepTimestampMs(int step, int subStep);

  boolean isComplete();

  /**
   * Whether the observed milestones are consistent with this method having been used — reached in
   * the method's order, each before the solve finished, with any step skipped still counting. A
   * solve that does not match (a different method, or freestyle) gets no breakdown, only its total.
   *
   * <p>Also meaningful on a solve that stopped early: a prefix of the milestones, in order, is a
   * legitimate partial match. It is never relaxed into "store something anyway" — a prefix too short
   * to tell the methods apart does not match.
   */
  boolean matchesMethod();
}
