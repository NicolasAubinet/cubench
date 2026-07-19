package com.cube.nanotimer.smartcube.step;

import java.util.Collections;
import java.util.List;

/**
 * How long one step of a solve took, split into the thinking before its moves (recognition) and the
 * turning itself (execution). A step built in parts carries them as sub-steps, in the order they were
 * completed, and its own recognition is the sum of theirs — so the pauses between the parts count as
 * thinking rather than turning. A skipped step is zero throughout.
 *
 * <p>A step the solve stopped inside is {@link #isComplete() incomplete}: it holds only the parts that
 * were finished, and ends at the last of them rather than at the step's own milestone.
 */
public final class StepTime {

  private final int stepIndex;
  private final String stepName;
  private final long recognitionMs;
  private final long executionMs;
  private final boolean complete;
  private final List<StepTime> subSteps;

  public StepTime(int stepIndex, String stepName, long recognitionMs, long executionMs,
      List<StepTime> subSteps) {
    this(stepIndex, stepName, recognitionMs, executionMs, subSteps, true);
  }

  public StepTime(int stepIndex, String stepName, long recognitionMs, long executionMs,
      List<StepTime> subSteps, boolean complete) {
    this.stepIndex = stepIndex;
    this.stepName = stepName;
    this.recognitionMs = recognitionMs;
    this.executionMs = executionMs;
    this.complete = complete;
    this.subSteps = Collections.unmodifiableList(subSteps);
  }

  public int getStepIndex() {
    return stepIndex;
  }

  public String getStepName() {
    return stepName;
  }

  /** Thinking time: the pauses before this step's moves, and between its parts. */
  public long getRecognitionMs() {
    return recognitionMs;
  }

  /** Turning time. */
  public long getExecutionMs() {
    return executionMs;
  }

  public long getTotalMs() {
    return recognitionMs + executionMs;
  }

  /** False when the solve stopped inside this step: only its finished parts are here. */
  public boolean isComplete() {
    return complete;
  }

  /** The parts this step was built in, oldest first. Empty when it has none. */
  public List<StepTime> getSubSteps() {
    return subSteps;
  }

  @Override
  public String toString() {
    return stepName + "(" + recognitionMs + "ms + " + executionMs + "ms"
        + (complete ? "" : ", unfinished") + ")";
  }
}
