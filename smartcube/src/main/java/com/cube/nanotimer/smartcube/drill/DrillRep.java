package com.cube.nanotimer.smartcube.drill;

/**
 * One attempt at one case: what it was, how long the looking and the turning took, and how many
 * moves it cost. Split the same way a solve's steps are, so a drill and a solve can be read against
 * each other.
 *
 * <p>Recognition is the gap from the case appearing on screen to the first move of the algorithm, so
 * every rep has one, the first included. It used to run from the end of the previous rep instead,
 * which left that first rep with nothing to measure from and charged any pause between reps to the
 * user. The AUF turns that only square a case up to be read are looking too, and so land in it; the
 * AUF that finishes one is the case being solved, and stays in the execution.
 */
public final class DrillRep {

  private final String caseCode;
  private final String scramble;
  private final long recognitionMs;
  private final long executionMs;
  private final int moveCount;
  private final int resetCount;
  private final boolean revealed;
  private final boolean abandoned;

  DrillRep(String caseCode, String scramble, long recognitionMs, long executionMs, int moveCount,
      int resetCount, boolean revealed, boolean abandoned) {
    this.caseCode = caseCode;
    this.scramble = scramble;
    this.recognitionMs = recognitionMs;
    this.executionMs = executionMs;
    this.moveCount = moveCount;
    this.resetCount = resetCount;
    this.revealed = revealed;
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

  /** Quarter turns, so a rep that took twice the moves reads as fumbled rather than slow. */
  public int getMoveCount() {
    return moveCount;
  }

  /**
   * How many times the user put this case back to the start before finishing it. The times are the
   * last attempt's, so this is what says whether they are a clean run or the one that finally came
   * off, which a coach reading a fast time ought to know.
   */
  public int getResetCount() {
    return resetCount;
  }

  /**
   * The user looked the algorithm up part way through this one. Its times are real but they are not
   * a measure of knowing the case, which is what anything aggregating them has to know.
   */
  public boolean wasRevealed() {
    return revealed;
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
        + " moves" + (revealed ? ", revealed" : "") + (abandoned ? ", abandoned" : "") + ")";
  }
}
