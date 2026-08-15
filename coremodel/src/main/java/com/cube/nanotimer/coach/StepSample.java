package com.cube.nanotimer.coach;

/**
 * One occurrence of a step code: what a single solve, or a single drill rep, spent on it.
 *
 * <p>The tallies the screens read are computed in SQL, which cannot throw a junk occurrence out
 * because it never sees them one at a time. The coach payload has to, so it reads the occurrences
 * themselves and does its own arithmetic ({@link StepTallies}).
 */
public class StepSample {

  private final String code;
  private final long timeMs;
  private final long recognitionMs;
  private final boolean part;
  private final long solveId;

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
    this.code = code;
    this.timeMs = timeMs;
    this.recognitionMs = recognitionMs;
    this.part = part;
    this.solveId = solveId;
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
}
