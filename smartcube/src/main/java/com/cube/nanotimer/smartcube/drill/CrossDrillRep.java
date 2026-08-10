package com.cube.nanotimer.smartcube.drill;

import com.cube.nanotimer.smartcube.model.CubeMove;
import java.util.Collections;
import java.util.List;

/**
 * One attempt at one cross: how many moves it took against how few there were, and how the time
 * split between reading the scramble and turning it.
 *
 * <p><b>Moves are the result and the time is the note beside it.</b> A cross drilled here is not a
 * race: the whole point is to find the short solution, and a fast eight-move cross is worse practice
 * than a slow six-move one. The times are kept because planning is what this trains and a coach
 * reading the two together learns something neither says alone.
 *
 * <p>A rep that was announced finished without the cross being there is not scored on its moves at
 * all: they went somewhere else. {@link #isBuilt} is what says which kind of rep this is.
 */
public final class CrossDrillRep {

  private final String face;
  private final String scramble;
  private final List<CubeMove> moves;
  private final long shownAtMs;
  private final long planningMs;
  private final long executionMs;
  private final int moveCount;
  /**
   * Not final, and the only thing here that is not: the search that finds it runs beside the rep
   * rather than before it, so a rep finished in the first second of a drill can outrun its own
   * answer. {@link CrossDrillSession#setOptimalLength} fills it in when that happens.
   */
  private int optimalLength;
  private final boolean built;
  private final boolean planningExpired;

  CrossDrillRep(String face, String scramble, List<CubeMove> moves, long shownAtMs,
      long planningMs, long executionMs, int moveCount, int optimalLength, boolean built,
      boolean planningExpired) {
    this.face = face;
    this.scramble = scramble;
    this.moves = Collections.unmodifiableList(moves);
    this.shownAtMs = shownAtMs;
    this.planningMs = planningMs;
    this.executionMs = executionMs;
    this.moveCount = moveCount;
    this.optimalLength = optimalLength;
    this.built = built;
    this.planningExpired = planningExpired;
  }

  /** The face the cross was asked for, as its letter. */
  public String getFace() {
    return face;
  }

  /** The scramble the cross was to be found in, so a rep can be looked at again. */
  public String getScramble() {
    return scramble;
  }

  /**
   * The turns the user made, in order. The count is what the rep is scored on, but only the
   * sequence says where the moves over the shortest way went.
   */
  public List<CubeMove> getMoves() {
    return moves;
  }

  /** When the scramble went up, which the move timestamps are worth writing down against. */
  public long getShownAtMs() {
    return shownAtMs;
  }

  /** From the scramble appearing to the first turn, which is the looking this drill trains. */
  public long getPlanningMs() {
    return planningMs;
  }

  /** From the first turn to the last. */
  public long getExecutionMs() {
    return executionMs;
  }

  public long getTotalMs() {
    return planningMs + executionMs;
  }

  /** Quarter turns made, which is what the rep is scored on. */
  public int getMoveCount() {
    return moveCount;
  }

  /** The fewest moves this cross could have taken, or 0 while the search has not landed. */
  public int getOptimalLength() {
    return optimalLength;
  }

  void setOptimalLength(int optimalLength) {
    this.optimalLength = optimalLength;
  }

  /**
   * How many moves over the shortest way, or 0 for a rep that found it. Negative is impossible and
   * an unknown optimal reads as 0 rather than as a suspiciously good rep.
   */
  public int getExtraMoves() {
    return built && optimalLength > 0 ? Math.max(0, moveCount - optimalLength) : 0;
  }

  /**
   * Whether the cross was really there at the end. False for a rep the user announced finished when
   * it was not, which is the only way a wrong cross can end, since a wrong one never ends itself.
   */
  public boolean isBuilt() {
    return built;
  }

  /** Whether the planning limit ran out before the first turn, taking the colours with it. */
  public boolean isPlanningExpired() {
    return planningExpired;
  }

  @Override
  public String toString() {
    return "cross_" + face.toLowerCase(java.util.Locale.ROOT) + "(" + moveCount + "/"
        + optimalLength + " moves, " + planningMs + "ms + " + executionMs + "ms"
        + (built ? "" : ", not built") + ")";
  }
}
