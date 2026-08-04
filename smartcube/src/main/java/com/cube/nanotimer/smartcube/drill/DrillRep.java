package com.cube.nanotimer.smartcube.drill;

/**
 * One attempt at one case: what it was, how long the looking and the turning took, and how many
 * moves it cost. Split the same way a solve's steps are, so a drill and a solve can be read against
 * each other.
 *
 * <p>Recognition is the gap from the end of the previous rep to the first move of this one, which
 * leaves the first rep of a drill with nothing to measure from. That rep reports zero and says so
 * through {@link #isRecognitionMeasured}, the same distinction a solve's first step draws: zero
 * means unmeasured here, not instant.
 */
public final class DrillRep {

  private final String caseCode;
  private final String scramble;
  private final long recognitionMs;
  private final long executionMs;
  private final int moveCount;
  private final boolean recognitionMeasured;
  private final boolean abandoned;

  DrillRep(String caseCode, String scramble, long recognitionMs, long executionMs, int moveCount,
      boolean recognitionMeasured, boolean abandoned) {
    this.caseCode = caseCode;
    this.scramble = scramble;
    this.recognitionMs = recognitionMs;
    this.executionMs = executionMs;
    this.moveCount = moveCount;
    this.recognitionMeasured = recognitionMeasured;
    this.abandoned = abandoned;
  }

  /** The case as a solve records it, {@code oll_21} or {@code pll_ga}. */
  public String getCaseCode() {
    return caseCode;
  }

  /** What the virtual cube was set up with, so a rep can be looked at again. */
  public String getScramble() {
    return scramble;
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

  /** Whether the rep had a previous one to measure its recognition from. */
  public boolean isRecognitionMeasured() {
    return recognitionMeasured;
  }

  /** Quarter turns, so a rep that took twice the moves reads as fumbled rather than slow. */
  public int getMoveCount() {
    return moveCount;
  }

  /** Given up on rather than solved, so its times are what it had reached. */
  public boolean isAbandoned() {
    return abandoned;
  }

  /** The half the drill's target applies to. */
  public long getTimedMs(DrillSpec.Type type) {
    return type == DrillSpec.Type.CASE_RECOGNITION ? recognitionMs : executionMs;
  }

  @Override
  public String toString() {
    return caseCode + "(" + recognitionMs + "ms + " + executionMs + "ms, " + moveCount
        + " moves" + (abandoned ? ", abandoned" : "") + ")";
  }
}
