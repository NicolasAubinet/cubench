package com.cube.nanotimer.smartcube.step;

/**
 * Emitted when a solving method's step is first reached.
 */
public final class StepBoundaryEvent {

  private final int stepIndex;
  private final long cubeTimestampMs;

  public StepBoundaryEvent(int stepIndex, long cubeTimestampMs) {
    this.stepIndex = stepIndex;
    this.cubeTimestampMs = cubeTimestampMs;
  }

  public int getStepIndex() {
    return stepIndex;
  }

  /** The move timestamp (cube clock) at which the step became complete. */
  public long getCubeTimestampMs() {
    return cubeTimestampMs;
  }

  @Override
  public String toString() {
    return "StepBoundaryEvent(" + stepIndex + " @ " + cubeTimestampMs + "ms)";
  }
}
