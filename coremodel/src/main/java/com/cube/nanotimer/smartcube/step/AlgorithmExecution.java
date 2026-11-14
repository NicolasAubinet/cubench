package com.cube.nanotimer.smartcube.step;

/** How an execution stands against the algorithms its case is usually turned with. */
public final class AlgorithmExecution {

  private final boolean unusual;
  private final int moves;
  private final int usualMoves;

  AlgorithmExecution(boolean unusual, int moves, int usualMoves) {
    this.unusual = unusual;
    this.moves = moves;
    this.usualMoves = usualMoves;
  }

  /** Whether hardly anybody turns this: a rare spelling, or one the table has not got at all. */
  public boolean isUnusual() {
    return unusual;
  }

  /** How many turns it takes. */
  public int getMoves() {
    return moves;
  }

  /** How many the shortest algorithm in use takes, or 0 where there is nothing to compare with. */
  public int getUsualMoves() {
    return usualMoves;
  }

  /** Whether it takes more turns than the shortest algorithm people use: the actionable half. */
  public boolean isLonger() {
    return usualMoves > 0 && moves > usualMoves;
  }
}
