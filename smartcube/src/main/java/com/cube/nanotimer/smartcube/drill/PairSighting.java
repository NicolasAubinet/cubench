package com.cube.nanotimer.smartcube.drill;

/** One F2L pair as a solve turned it, what {@link PairWeights} ranks the pairs from. */
public final class PairSighting {

  private final String caseCode;
  private final long timeMs;
  private final int usualMoves;
  private final boolean undone;
  private final boolean stopped;

  /**
   * @param caseCode the pair's case, in any slot ({@code pair_27_bl})
   * @param usualMoves how many moves the shortest listed algorithm takes, 0 for none to compare
   * @param undone whether side turns were messed up and taken back
   * @param stopped whether it had a stop inside its turning
   */
  public PairSighting(String caseCode, long timeMs, int usualMoves, boolean undone,
      boolean stopped) {
    this.caseCode = caseCode;
    this.timeMs = timeMs;
    this.usualMoves = usualMoves;
    this.undone = undone;
    this.stopped = stopped;
  }

  public String getCaseCode() {
    return caseCode;
  }

  public long getTimeMs() {
    return timeMs;
  }

  public int getUsualMoves() {
    return usualMoves;
  }

  public boolean isUndone() {
    return undone;
  }

  public boolean isStopped() {
    return stopped;
  }
}
