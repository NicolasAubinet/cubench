package com.cube.nanotimer.scrambler.cross;

/**
 * Display-only formatter that presents a cross solution <em>cross-on-bottom</em>.
 *
 * <p>The per-face search runs in the standard frame (no edge-orientation reframing). To show a
 * non-D solution as a cuber would hold it (cross color down), we prepend a canonical whole-cube
 * rotation that brings the face to D, and relabel each move letter into that rotated frame. Turn
 * modifiers ({@code '}, {@code 2}) are preserved because a whole-cube rotation keeps a face turn's
 * sense.
 *
 * <p>Canonical rotations: D = none, U = z2, F = x', B = x, R = z, L = z'.
 */
public class CrossFormatter {

  private CrossFormatter() {
  }

  /**
   * Returns the solution as it should be displayed cross-on-bottom: for D, unchanged; otherwise the
   * rotation token followed by the relabeled moves.
   */
  public static String[] toCrossOnBottom(CrossFace face, String[] solution) {
    if (face == CrossFace.D) {
      return solution;
    }
    String[] out = new String[solution.length + 1];
    out[0] = rotationPrefix(face);
    for (int i = 0; i < solution.length; i++) {
      out[i + 1] = relabel(face, solution[i]);
    }
    return out;
  }

  /**
   * The same relabeling without the rotation token in front, for a screen that writes every row it
   * has in this one frame and so has nothing to announce the change of frame to. A rotation printed
   * among the moves is a token that cannot be turned: it is not a move, and a screen that steps
   * through the moves one at a time would have to step over it.
   */
  public static String[] movesOnBottom(CrossFace face, String[] moves) {
    if (face == CrossFace.D) {
      return moves;
    }
    String[] out = new String[moves.length];
    for (int i = 0; i < moves.length; i++) {
      out[i] = relabel(face, moves[i]);
    }
    return out;
  }

  /** One move as it reads cross-on-bottom: the same turn of the same face, spelled from there. */
  public static String moveOnBottom(CrossFace face, String move) {
    return face == CrossFace.D ? move : relabel(face, move);
  }

  /** The whole-cube rotation that brings {@code face} to the bottom. */
  public static String rotationPrefix(CrossFace face) {
    switch (face) {
      case U: return "z2";
      case F: return "x'";
      case B: return "x";
      case R: return "z";
      case L: return "z'";
      case D:
      default: return "";
    }
  }

  // Relabels a single move (letter + modifier) into the rotated, cross-on-bottom frame.
  private static String relabel(CrossFace face, String move) {
    char letter = move.charAt(0);
    String modifier = move.substring(1);
    return mapLetter(face, letter) + modifier;
  }

  // The face a given face-letter occupies after the cube is rotated to put `face` on the bottom.
  private static char mapLetter(CrossFace face, char letter) {
    switch (face) {
      case U: // z2: U<->D, R<->L, F/B fixed
        switch (letter) {
          case 'U': return 'D';
          case 'D': return 'U';
          case 'R': return 'L';
          case 'L': return 'R';
          default: return letter; // F, B
        }
      case F: // x': U->F->D->B->U
        switch (letter) {
          case 'U': return 'F';
          case 'F': return 'D';
          case 'D': return 'B';
          case 'B': return 'U';
          default: return letter; // R, L
        }
      case B: // x: F->U->B->D->F
        switch (letter) {
          case 'F': return 'U';
          case 'U': return 'B';
          case 'B': return 'D';
          case 'D': return 'F';
          default: return letter; // R, L
        }
      case R: // z: U->R->D->L->U
        switch (letter) {
          case 'U': return 'R';
          case 'R': return 'D';
          case 'D': return 'L';
          case 'L': return 'U';
          default: return letter; // F, B
        }
      case L: // z': U->L->D->R->U
        switch (letter) {
          case 'U': return 'L';
          case 'L': return 'D';
          case 'D': return 'R';
          case 'R': return 'U';
          default: return letter; // F, B
        }
      case D:
      default:
        return letter;
    }
  }
}
