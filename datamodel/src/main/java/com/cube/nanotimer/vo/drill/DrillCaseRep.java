package com.cube.nanotimer.vo.drill;

/**
 * One stored attempt at one case, split the way a solve's steps are so that a drill and a solve can
 * be read against each other: "your Gb is 2.9s in solves and 1.8s in drills" is the diagnosis that
 * the algorithm is known and the case is not recognised under pressure.
 *
 * <p>The times and the moves are the last attempt's, with {@code resetCount} saying how many went
 * before it, so a time reached on the third go is not read as a clean one.
 *
 * <p>A rep the user threw out is flagged rather than dropped. The row survives so the rep can be
 * put back, and so a coach reading these can still see it: a rep pruned by hand is itself a signal,
 * and a detector looking for times far outside the usual wants the ones that were.
 */
public class DrillCaseRep {

  private final int position;
  private final String caseCode;
  private final String scramble;
  private final String moves;
  private final long recognitionMs;
  private final long executionMs;
  private final int moveCount;
  private final int resetCount;
  private final boolean revealed;
  private final boolean abandoned;
  private boolean deleted;

  /**
   * @param position where in the drill this rep fell, since the rows are written as they finish
   * @param caseCode the case as a solve records it, {@code oll_21} or {@code pll_ga}
   * @param moves the turns with their offsets from the case going up, in the form solves use
   * @param revealed the algorithm was looked up part way through, so the time is real but is not a
   *     measure of knowing the case
   * @param abandoned given up on rather than solved, so the times are what it had reached
   */
  public DrillCaseRep(int position, String caseCode, String scramble, String moves,
      long recognitionMs, long executionMs, int moveCount, int resetCount, boolean revealed,
      boolean abandoned) {
    this.position = position;
    this.caseCode = caseCode;
    this.scramble = scramble;
    this.moves = moves;
    this.recognitionMs = recognitionMs;
    this.executionMs = executionMs;
    this.moveCount = moveCount;
    this.resetCount = resetCount;
    this.revealed = revealed;
    this.abandoned = abandoned;
  }

  /** A rep read back, which may have been thrown out since it was written. */
  public DrillCaseRep(int position, String caseCode, String scramble, String moves,
      long recognitionMs, long executionMs, int moveCount, int resetCount, boolean revealed,
      boolean abandoned, boolean deleted) {
    this(position, caseCode, scramble, moves, recognitionMs, executionMs, moveCount, resetCount,
        revealed, abandoned);
    this.deleted = deleted;
  }

  public int getPosition() {
    return position;
  }

  public String getCaseCode() {
    return caseCode;
  }

  public String getScramble() {
    return scramble;
  }

  public String getMoves() {
    return moves;
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

  public int getMoveCount() {
    return moveCount;
  }

  public int getResetCount() {
    return resetCount;
  }

  public boolean wasRevealed() {
    return revealed;
  }

  public boolean isAbandoned() {
    return abandoned;
  }

  /** Thrown out by the user: still here, and counted by nothing. */
  public boolean isDeleted() {
    return deleted;
  }

  public void setDeleted(boolean deleted) {
    this.deleted = deleted;
  }
}
