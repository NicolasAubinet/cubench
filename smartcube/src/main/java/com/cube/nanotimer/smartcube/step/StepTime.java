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
  private final List<PieceMark> pieceMarks;
  private final String wantedName;

  public StepTime(int stepIndex, String stepName, long recognitionMs, long executionMs,
      List<StepTime> subSteps) {
    this(stepIndex, stepName, recognitionMs, executionMs, subSteps, true);
  }

  public StepTime(int stepIndex, String stepName, long recognitionMs, long executionMs,
      List<StepTime> subSteps, boolean complete) {
    this(stepIndex, stepName, recognitionMs, executionMs, subSteps, complete,
        Collections.<PieceMark>emptyList());
  }

  public StepTime(int stepIndex, String stepName, long recognitionMs, long executionMs,
      List<StepTime> subSteps, boolean complete, List<PieceMark> pieceMarks) {
    this(stepIndex, stepName, recognitionMs, executionMs, subSteps, complete, pieceMarks, null);
  }

  public StepTime(int stepIndex, String stepName, long recognitionMs, long executionMs,
      List<StepTime> subSteps, boolean complete, List<PieceMark> pieceMarks, String wantedName) {
    this.stepIndex = stepIndex;
    this.stepName = stepName;
    this.recognitionMs = recognitionMs;
    this.executionMs = executionMs;
    this.complete = complete;
    this.subSteps = Collections.unmodifiableList(subSteps);
    this.pieceMarks = Collections.unmodifiableList(pieceMarks);
    this.wantedName = wantedName;
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

  /** Of the pieces this step's name is made of, which it put home. Empty for a name without any. */
  public List<PieceMark> getPieceMarks() {
    return pieceMarks;
  }

  /** The cycle the cube wanted here, where the step carries a wrong piece. Null everywhere else. */
  public String getWantedName() {
    return wantedName;
  }

  @Override
  public String toString() {
    return stepName + "(" + recognitionMs + "ms + " + executionMs + "ms"
        + (complete ? "" : ", unfinished") + ")";
  }
}
