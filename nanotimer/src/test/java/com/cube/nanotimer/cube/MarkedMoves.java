package com.cube.nanotimer.cube;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A displayed row read back with the cancelled moves fenced in {@code ~}, so one assertion covers
 * both the sequence and what is struck out of it. The row is the scope, as it is on the screen:
 * {@link #of} is the step's own row, {@link #ofPart} one of the rows folded under it.
 */
final class MarkedMoves {

  private MarkedMoves() {
  }

  static String of(SolveSolution.Step step) {
    return render(step.getGroups());
  }

  static String ofPart(SolveSolution.Step step, int part) {
    return render(Collections.singletonList(step.getGroups().get(part)));
  }

  private static String render(List<List<SolveSolution.Token>> groups) {
    Set<SolveSolution.Token> cancelled = SolveSolution.cancelledIn(groups);
    StringBuilder sb = new StringBuilder();
    for (List<SolveSolution.Token> group : groups) {
      for (SolveSolution.Token token : group) {
        if (sb.length() > 0) {
          sb.append(' ');
        }
        String notation = token.getNotation();
        sb.append(cancelled.contains(token) ? "~" + notation + "~" : notation);
      }
    }
    return sb.toString();
  }
}
