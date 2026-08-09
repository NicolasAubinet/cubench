package com.cube.nanotimer.vo;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * One step of a smart cube solve's method breakdown, split into the thinking before its moves
 * (recognition) and the turning itself (execution). A step built in parts carries them as sub-steps,
 * in the order they were completed. The datamodel counterpart of the smart cube analyzer's step time,
 * decoupled from it so it can be persisted and read back without the smartcube module.
 */
public class SolveStep implements Serializable {

  private final int stepIndex;
  private final String name; // step code (ex: "cross", "pair"), localized when displayed
  private final long recognitionMs;
  private final long executionMs;
  private final boolean complete;
  private final List<SolveStep> subSteps;
  // What became of each piece the name is made of. Read from the solve rather than stored with
  // it, so a solve that cannot be read back simply has none.
  private final List<PieceMark> pieceMarks;

  public SolveStep(int stepIndex, String name, long recognitionMs, long executionMs,
      List<SolveStep> subSteps) {
    this(stepIndex, name, recognitionMs, executionMs, subSteps, true);
  }

  public SolveStep(int stepIndex, String name, long recognitionMs, long executionMs,
      List<SolveStep> subSteps, boolean complete) {
    this(stepIndex, name, recognitionMs, executionMs, subSteps, complete,
        Collections.<PieceMark>emptyList());
  }

  public SolveStep(int stepIndex, String name, long recognitionMs, long executionMs,
      List<SolveStep> subSteps, boolean complete, List<PieceMark> pieceMarks) {
    this.stepIndex = stepIndex;
    this.name = name;
    this.recognitionMs = recognitionMs;
    this.executionMs = executionMs;
    this.complete = complete;
    this.subSteps = Collections.unmodifiableList(subSteps);
    this.pieceMarks = Collections.unmodifiableList(pieceMarks);
  }

  public int getStepIndex() {
    return stepIndex;
  }

  public String getName() {
    return name;
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

  /** False when the solve stopped inside this step: only its finished parts are here, and it ends
   * at the last of them rather than at the step's own milestone. */
  public boolean isComplete() {
    return complete;
  }

  /** The parts this step was built in, oldest first. Empty when it has none. */
  public List<SolveStep> getSubSteps() {
    return subSteps;
  }

  /**
   * Of the pieces this step's name is made of, which of them it put home — right slot and right way
   * round — in the order they are said. A blind algorithm's, and empty for every other name.
   */
  public List<PieceMark> getPieceMarks() {
    return pieceMarks;
  }
}
