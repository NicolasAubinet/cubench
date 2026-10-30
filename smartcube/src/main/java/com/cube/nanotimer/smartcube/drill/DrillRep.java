package com.cube.nanotimer.smartcube.drill;

import com.cube.nanotimer.drill.DrillSpec;

import com.cube.nanotimer.smartcube.model.CubeMove;
import java.util.Collections;
import java.util.List;

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
  private final List<CubeMove> moves;
  private final long shownAtMs;
  private final long recognitionMs;
  private final long executionMs;
  private final int moveCount;
  private final int resetCount;
  private final boolean revealed;
  private final boolean abandoned;
  private final boolean practice;
  private final boolean offTaught;
  private final Long pairOneMs;
  private final Long pauseMs;
  private final boolean rotated;

  DrillRep(String caseCode, String scramble, List<CubeMove> moves, long shownAtMs,
      long recognitionMs, long executionMs, int moveCount, int resetCount, boolean revealed,
      boolean abandoned) {
    this(caseCode, scramble, moves, shownAtMs, recognitionMs, executionMs, moveCount, resetCount,
        revealed, abandoned, false);
  }

  DrillRep(String caseCode, String scramble, List<CubeMove> moves, long shownAtMs,
      long recognitionMs, long executionMs, int moveCount, int resetCount, boolean revealed,
      boolean abandoned, boolean practice) {
    this(caseCode, scramble, moves, shownAtMs, recognitionMs, executionMs, moveCount, resetCount,
        revealed, abandoned, practice, false);
  }

  DrillRep(String caseCode, String scramble, List<CubeMove> moves, long shownAtMs,
      long recognitionMs, long executionMs, int moveCount, int resetCount, boolean revealed,
      boolean abandoned, boolean practice, boolean offTaught) {
    this(caseCode, scramble, moves, shownAtMs, recognitionMs, executionMs, moveCount, resetCount,
        revealed, abandoned, practice, offTaught, null, null);
  }

  DrillRep(String caseCode, String scramble, List<CubeMove> moves, long shownAtMs,
      long recognitionMs, long executionMs, int moveCount, int resetCount, boolean revealed,
      boolean abandoned, boolean practice, boolean offTaught, Long pairOneMs, Long pauseMs) {
    this(caseCode, scramble, moves, shownAtMs, recognitionMs, executionMs, moveCount, resetCount,
        revealed, abandoned, practice, offTaught, pairOneMs, pauseMs, false);
  }

  DrillRep(String caseCode, String scramble, List<CubeMove> moves, long shownAtMs,
      long recognitionMs, long executionMs, int moveCount, int resetCount, boolean revealed,
      boolean abandoned, boolean practice, boolean offTaught, Long pairOneMs, Long pauseMs,
      boolean rotated) {
    this.rotated = rotated;
    this.caseCode = caseCode;
    this.pairOneMs = pairOneMs;
    this.pauseMs = pauseMs;
    this.scramble = scramble;
    this.moves = Collections.unmodifiableList(moves);
    this.shownAtMs = shownAtMs;
    this.recognitionMs = recognitionMs;
    this.executionMs = executionMs;
    this.moveCount = moveCount;
    this.resetCount = resetCount;
    this.revealed = revealed;
    this.abandoned = abandoned;
    this.practice = practice;
    this.offTaught = offTaught;
  }

  /** The case as a solve records it, {@code oll_21} or {@code pll_ga}. */
  public String getCaseCode() {
    return caseCode;
  }

  /** What the virtual cube was set up with, so a rep can be looked at again. */
  public String getScramble() {
    return scramble;
  }

  /** The turns that solved it, in order, so the algorithm used can be read back off the rep. */
  public List<CubeMove> getMoves() {
    return moves;
  }

  /**
   * The turns as notation, the way the cube reported them, for the algorithm matcher: it reads a
   * slice arriving as two outer turns and a regrip it never saw.
   */
  public String getTurned() {
    return notation(moves);
  }

  static String notation(List<CubeMove> moves) {
    StringBuilder turned = new StringBuilder();
    for (CubeMove move : moves) {
      turned.append(turned.length() == 0 ? "" : " ").append(move.getNotation());
    }
    return turned.toString();
  }

  /** When the case went up, which the move timestamps are worth writing down against. */
  public long getShownAtMs() {
    return shownAtMs;
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

  /**
   * Turned in practice mode, against an algorithm on screen: counted for the coach, but no time of
   * the case and nothing towards retiring it.
   */
  public boolean isPractice() {
    return practice;
  }

  /** Solved in a learning drill, but not with the algorithm being learned. */
  public boolean isOffTaught() {
    return offTaught;
  }

  /** A back-slot rep the cube was turned in, which the gyro saw. False where nothing could tell. */
  public boolean isRotated() {
    return rotated;
  }

  /**
   * A look-ahead rep's first pair: its first turn to the first slot coming in. Null for any other
   * drill, and for a rep given up on before a slot was in.
   */
  public Long getPairOneMs() {
    return pairOneMs;
  }

  /**
   * A look-ahead rep's pause: the first slot coming in to the next turn, read as a solve reads the
   * recognition before a pair. Null for any other drill, and for a rep given up on before that turn.
   */
  public Long getPauseMs() {
    return pauseMs;
  }

  /** A look-ahead rep's second pair: the turn after the pause to the last turn. Null as the pause. */
  public Long getPairTwoMs() {
    return pairOneMs == null || pauseMs == null ? null
        : Long.valueOf(Math.max(0, executionMs - pairOneMs - pauseMs));
  }

  /**
   * The half the drill's target applies to. A look-ahead rep is judged from the case going up to
   * the second pair's first turn, or on the whole rep where it never got that far.
   */
  public long getTimedMs(DrillSpec.Type type) {
    if (type == DrillSpec.Type.F2L_LOOKAHEAD) {
      return pauseMs == null ? getTotalMs() : recognitionMs + pairOneMs + pauseMs;
    }
    return type == DrillSpec.Type.CASE_RECOGNITION ? recognitionMs : executionMs;
  }

  @Override
  public String toString() {
    return caseCode + "(" + recognitionMs + "ms + " + executionMs + "ms, " + moveCount
        + " moves" + (pauseMs != null ? ", pair " + pairOneMs + "ms, pause " + pauseMs + "ms" : "")
        + (revealed ? ", revealed" : "") + (abandoned ? ", abandoned" : "")
        + (practice ? ", practice" : "")
        + (offTaught ? ", off the taught algorithm" : "") + ")";
  }
}
