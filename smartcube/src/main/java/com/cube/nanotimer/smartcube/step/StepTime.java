package com.cube.nanotimer.smartcube.step;

/**
 * How long one step of a solve took, split into the pause before its first move (recognition)
 * and the turning that followed (execution). A skipped step is zero throughout.
 */
public final class StepTime {

  private final int stepIndex;
  private final String stepName;
  private final long recognitionMs;
  private final long executionMs;

  public StepTime(int stepIndex, String stepName, long recognitionMs, long executionMs) {
    this.stepIndex = stepIndex;
    this.stepName = stepName;
    this.recognitionMs = recognitionMs;
    this.executionMs = executionMs;
  }

  public int getStepIndex() {
    return stepIndex;
  }

  public String getStepName() {
    return stepName;
  }

  public long getRecognitionMs() {
    return recognitionMs;
  }

  public long getExecutionMs() {
    return executionMs;
  }

  public long getTotalMs() {
    return recognitionMs + executionMs;
  }

  @Override
  public String toString() {
    return stepName + "(" + recognitionMs + "ms + " + executionMs + "ms)";
  }
}
