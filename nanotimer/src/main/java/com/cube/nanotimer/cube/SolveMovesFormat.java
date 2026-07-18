package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.model.CubeMove;
import java.util.ArrayList;
import java.util.List;

/**
 * The stored form of a solve's moves: notation and offset from the solve start, as in
 * {@code "D'@0 R@180 F'@410"}. Self-describing on purpose, so a database dump and the CSV export
 * both read as the solution they are. Which step a move belongs to is not stored: walking the
 * step durations against these offsets rebuilds the split exactly.
 */
public final class SolveMovesFormat {

  private static final char OFFSET_SEPARATOR = '@';

  private SolveMovesFormat() {
  }

  public static String format(List<CubeMove> moves, long solveStartMs) {
    StringBuilder sb = new StringBuilder();
    for (CubeMove move : moves) {
      if (sb.length() > 0) {
        sb.append(' ');
      }
      sb.append(move.getNotation()).append(OFFSET_SEPARATOR).append(move.getCubeTimestampMs() - solveStartMs);
    }
    return sb.toString();
  }

  /** Parses the stored form back. Skips anything malformed rather than losing the whole solution. */
  public static List<Move> parse(String stored) {
    List<Move> moves = new ArrayList<Move>();
    if (stored == null || stored.isEmpty()) {
      return moves;
    }
    for (String token : stored.trim().split(" +")) {
      int at = token.lastIndexOf(OFFSET_SEPARATOR);
      if (at <= 0) {
        continue;
      }
      try {
        moves.add(new Move(token.substring(0, at), Long.parseLong(token.substring(at + 1))));
      } catch (NumberFormatException e) {
        // a corrupted offset costs its move, not the rest of the solution
      }
    }
    return moves;
  }

  /** One quarter turn: the cube reports doubles as two, which {@link SolveSolution} folds back. */
  public static final class Move {

    private final String notation;
    private final long offsetMs;

    Move(String notation, long offsetMs) {
      this.notation = notation;
      this.offsetMs = offsetMs;
    }

    public String getNotation() {
      return notation;
    }

    public long getOffsetMs() {
      return offsetMs;
    }
  }
}
