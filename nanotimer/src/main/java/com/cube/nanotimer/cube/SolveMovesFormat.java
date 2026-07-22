package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.model.CubeMove;
import java.util.ArrayList;
import java.util.Collections;
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
    return format(moves, Collections.<RotationTracker.Rotation>emptyList(), solveStartMs);
  }

  /**
   * The moves with the solver's whole-cube rotations merged in by time. A rotation is written as
   * its own token ({@code "y@1200"}) ahead of the move it preceded, so the stored stream stays the
   * raw cube-frame one: the face letters are untouched and a replay can simply skip the rotations.
   */
  public static String format(List<CubeMove> moves, List<RotationTracker.Rotation> rotations,
      long solveStartMs) {
    StringBuilder sb = new StringBuilder();
    int next = 0;
    for (CubeMove move : moves) {
      long moveOffset = move.getCubeTimestampMs() - solveStartMs;
      while (next < rotations.size()
          && rotations.get(next).getTimestampMs() - solveStartMs <= moveOffset) {
        RotationTracker.Rotation rotation = rotations.get(next++);
        for (String token : rotation.getNotation().split(" ")) {
          append(sb, token, rotation.getTimestampMs() - solveStartMs);
        }
      }
      append(sb, move.getNotation(), moveOffset);
    }
    for (; next < rotations.size(); next++) { // rotations after the last move, if the solve ended on one
      RotationTracker.Rotation rotation = rotations.get(next);
      for (String token : rotation.getNotation().split(" ")) {
        append(sb, token, rotation.getTimestampMs() - solveStartMs);
      }
    }
    return sb.toString();
  }

  private static void append(StringBuilder sb, String notation, long offsetMs) {
    if (sb.length() > 0) {
      sb.append(' ');
    }
    sb.append(notation).append(OFFSET_SEPARATOR).append(offsetMs);
  }

  /** Whole-cube rotations are lowercase, face turns uppercase — the cube reports only faces. */
  public static boolean isRotation(String notation) {
    if (notation.isEmpty()) {
      return false;
    }
    char axis = notation.charAt(0);
    return axis == 'x' || axis == 'y' || axis == 'z';
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
