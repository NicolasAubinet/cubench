package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.model.CubeMove;
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
}
