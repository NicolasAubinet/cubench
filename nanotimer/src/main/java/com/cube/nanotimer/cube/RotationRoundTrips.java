package com.cube.nanotimer.cube;

import com.cube.nanotimer.cube.SolveMovesFormat.Move;
import java.util.ArrayList;
import java.util.List;

/**
 * Drops a rotation the solver took straight back when nothing between the two needed it: {@code y U
 * y'} is written {@code U}, {@code y U D' U y'} is written {@code U D' U}.
 *
 * <p>A hand tilting the cube past the 45° cliff during a {@code U} prints a rotation and its undo
 * around the move. Nothing measured tells that tilt from a deliberate one-move regrip: the angle off
 * the lattice (16.8° median against 17.8°) and the time spent in the new frame (100% against 100%)
 * are the same for both, since it is the same motion. What can be told is whether the rotation
 * changed anything the reader sees. A rotation about the axis of every face turned inside it leaves
 * each of their letters as it was, so folding it loses nothing, whether it was a tilt or a regrip.
 * Over six recorded solves that is 26 of the 55 round trips. One whose moves change letter
 * ({@code x U x'} is really an {@code F}) is a proper regrip and stays written.
 *
 * <p>Folded where a reconstruction is read, like the frame itself, never in the stored stream: a
 * replay still turns with the solver.
 */
final class RotationRoundTrips {

  private RotationRoundTrips() {
  }

  /** {@code moves} with every such round trip taken out, innermost first. */
  static List<Move> fold(List<Move> moves) {
    List<Move> folded = new ArrayList<Move>(moves);
    boolean changed = true;
    while (changed) {
      changed = false;
      for (int i = 0; i < folded.size() && !changed; i++) {
        int back = takenBackAt(folded, i);
        if (back > 0) {
          folded.remove(back);
          folded.remove(i);
          changed = true;
        }
      }
    }
    return folded;
  }

  /** Where the rotation at {@code i} is undone with only turns on its axis between, or -1. */
  private static int takenBackAt(List<Move> moves, int i) {
    String rotation = moves.get(i).getNotation();
    if (!SolveMovesFormat.isRotation(rotation) || rotation.indexOf(' ') >= 0) {
      return -1; // a reorientation of several rotations is about no one axis
    }
    char axis = rotation.charAt(0);
    int j = i + 1;
    while (j < moves.size() && axisOf(moves.get(j).getNotation()) == axis) {
      j++;
    }
    // At least one turn inside: a bare rotation and its undo is a wobble, already struck out as one.
    return j > i + 1 && j < moves.size() && inverts(rotation, moves.get(j).getNotation()) ? j : -1;
  }

  /** The rotation axis a turn is about, or 0 for a rotation or anything unknown. */
  private static char axisOf(String notation) {
    if (notation.isEmpty()) {
      return 0;
    }
    switch (notation.charAt(0)) {
      case 'R': case 'L': case 'M': case 'r': case 'l':
        return 'x';
      case 'U': case 'D': case 'E': case 'u': case 'd':
        return 'y';
      case 'F': case 'B': case 'S': case 'f': case 'b':
        return 'z';
      default:
        return 0;
    }
  }

  private static boolean inverts(String a, String b) {
    String inverse = a.endsWith("2") ? a
        : (a.endsWith("'") ? a.substring(0, a.length() - 1) : a + "'");
    return inverse.equals(b);
  }
}
