package com.cube.nanotimer.coach;

/**
 * How one solve's cross was turned against the fewest moves it could take: both in HTM, counted as
 * the cross drill counts them, so a card and the drill it opens agree on what a move is.
 *
 * <p>An X-cross puts a pair in with the cross, so its moves are a pair's too: it is kept, marked by
 * {@link #getSlotsWithCross}, and the figure leaves it out rather than calling it a long cross.
 */
public final class CrossTurn {

  private final long solveId;
  private final int optimalMoves;
  private final int turnedMoves;
  private final int slotsWithCross;

  /** @param slotsWithCross F2L slots already solved as the cross completed: 1 for an X-cross */
  public CrossTurn(long solveId, int optimalMoves, int turnedMoves, int slotsWithCross) {
    this.solveId = solveId;
    this.optimalMoves = optimalMoves;
    this.turnedMoves = turnedMoves;
    this.slotsWithCross = slotsWithCross;
  }

  public long getSolveId() {
    return solveId;
  }

  public int getOptimalMoves() {
    return optimalMoves;
  }

  public int getTurnedMoves() {
    return turnedMoves;
  }

  public int getSlotsWithCross() {
    return slotsWithCross;
  }

  public boolean isXCross() {
    return slotsWithCross > 0;
  }

  public int getExtraMoves() {
    return Math.max(0, turnedMoves - optimalMoves);
  }
}
