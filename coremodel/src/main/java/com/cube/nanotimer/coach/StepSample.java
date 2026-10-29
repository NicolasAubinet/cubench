package com.cube.nanotimer.coach;

/**
 * One occurrence of a step code: what a single solve, or a single drill rep, spent on it.
 *
 * <p>The screens and the coach payload both read occurrences one at a time rather than as a SQL sum,
 * so that {@link StepTallies} can throw a junk one out.
 */
public class StepSample {

  private final String code;
  private final long timeMs;
  private final long recognitionMs;
  private final boolean part;
  private final long solveId;
  private final Integer partIndex;
  private final Integer stepIndex;

  /**
   * @param code the step code as the breakdown stores it, {@code cross} or {@code pll_gb}
   * @param recognitionMs the share of {@code timeMs} spent before the first move, 0 for a step
   *     timed from its own first move
   * @param part true when the code is one of the pieces a step was built from (an F2L slot, one of
   *     the algorithms a last layer step took) rather than a step of the method
   * @param solveId which solve the occurrence belongs to, so two parts of one solve can be told from
   *     one part each of two solves; a drill rep is an occurrence on its own and has no solve
   */
  public StepSample(String code, long timeMs, long recognitionMs, boolean part, long solveId) {
    this(code, timeMs, recognitionMs, part, solveId, null);
  }

  /**
   * @param partIndex where a part came in its step, 0 for the first one finished, or null for a
   *     step of the method, a drill rep, or a part whose place was not read
   */
  public StepSample(String code, long timeMs, long recognitionMs, boolean part, long solveId,
      Integer partIndex) {
    this(code, timeMs, recognitionMs, part, solveId, partIndex, null);
  }

  /**
   * @param stepIndex which step of its solve the occurrence is, or is a part of, 0 for the first;
   *     null for a drill rep or anything not read from a solve
   */
  public StepSample(String code, long timeMs, long recognitionMs, boolean part, long solveId,
      Integer partIndex, Integer stepIndex) {
    this.code = code;
    this.timeMs = timeMs;
    this.recognitionMs = recognitionMs;
    this.part = part;
    this.solveId = solveId;
    this.partIndex = partIndex;
    this.stepIndex = stepIndex;
  }

  public String getCode() {
    return code;
  }

  public long getTimeMs() {
    return timeMs;
  }

  public long getRecognitionMs() {
    return recognitionMs;
  }

  public boolean isPart() {
    return part;
  }

  public long getSolveId() {
    return solveId;
  }

  /** Where the part came in its step, 0 for the first, or null when that is not known. */
  public Integer getPartIndex() {
    return partIndex;
  }

  /** Which step of its solve this is, or is a part of, or null when not read from a solve. */
  public Integer getStepIndex() {
    return stepIndex;
  }
}
