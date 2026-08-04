package com.cube.nanotimer.cube;

import java.util.List;

/**
 * A displayed step read back with the cancelled moves fenced in {@code ~}, so one assertion covers
 * both the sequence and what is struck out of it.
 */
final class MarkedMoves {

  private MarkedMoves() {
  }

  static String of(SolveSolution.Step step) {
    StringBuilder sb = new StringBuilder();
    for (List<SolveSolution.Token> group : step.getGroups()) {
      for (SolveSolution.Token token : group) {
        if (sb.length() > 0) {
          sb.append(' ');
        }
        String notation = token.getNotation();
        sb.append(token.isCancelled() ? "~" + notation + "~" : notation);
      }
    }
    return sb.toString();
  }
}
