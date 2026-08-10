package com.cube.nanotimer.vo.drill;

/**
 * One stored attempt at one cross, scored on moves against the fewest there were, with the time as
 * the note beside it: a fast eight-move cross is worse practice than a slow six-move one.
 *
 * <p>Kept apart from {@link DrillCaseRep} rather than folded in beside it, because the two never
 * aggregate together. A case rep tallies into a mean per case; a cross rep belongs in no mean, since
 * every one of its scrambles is a different question.
 */
public class DrillCrossRep {

  private final int position;
  private final String face;
  private final String scramble;
  private final String moves;
  private final long planningMs;
  private final long executionMs;
  private final int moveCount;
  /**
   * Not final, and the only thing here that is not: the search that finds it runs beside the rep,
   * so a cross solved in two moves outruns its own answer. The row is written when the rep ends
   * either way, and takes the length when it lands.
   */
  private int optimalLength;
  private final boolean built;
  private final boolean planningExpired;

  /**
   * @param face the face the cross was asked for, as its letter
   * @param moves the turns with their offsets from the scramble going up, in the form solves use.
   *     The count is the score, but only the sequence says where the extra moves went
   * @param optimalLength the fewest moves the cross could have taken, or 0 where the search that
   *     finds it never landed
   * @param built whether the cross was really there, false for a rep the user announced finished
   *     when it was not, which is the only way a wrong cross can end
   */
  public DrillCrossRep(int position, String face, String scramble, String moves, long planningMs,
      long executionMs, int moveCount, int optimalLength, boolean built, boolean planningExpired) {
    this.position = position;
    this.face = face;
    this.scramble = scramble;
    this.moves = moves;
    this.planningMs = planningMs;
    this.executionMs = executionMs;
    this.moveCount = moveCount;
    this.optimalLength = optimalLength;
    this.built = built;
    this.planningExpired = planningExpired;
  }

  public int getPosition() {
    return position;
  }

  public String getFace() {
    return face;
  }

  public String getScramble() {
    return scramble;
  }

  public String getMoves() {
    return moves;
  }

  public long getPlanningMs() {
    return planningMs;
  }

  public long getExecutionMs() {
    return executionMs;
  }

  public long getTotalMs() {
    return planningMs + executionMs;
  }

  public int getMoveCount() {
    return moveCount;
  }

  public int getOptimalLength() {
    return optimalLength;
  }

  public void setOptimalLength(int optimalLength) {
    this.optimalLength = optimalLength;
  }

  /** How many moves over the shortest way, or 0 for a rep that found it or was never built. */
  public int getExtraMoves() {
    return built && optimalLength > 0 ? Math.max(0, moveCount - optimalLength) : 0;
  }

  public boolean isBuilt() {
    return built;
  }

  public boolean isPlanningExpired() {
    return planningExpired;
  }
}
