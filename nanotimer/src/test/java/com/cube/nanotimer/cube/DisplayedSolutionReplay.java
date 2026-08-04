package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import com.cube.nanotimer.smartcube.model.Face;

/**
 * Follows a reconstruction the way a reader would: takes what the screen shows, turns a scrambled
 * cube by it, and says whether it came out solved. Nothing else checks this — the other capture
 * tests walk the raw stored stream, which cannot be wrong in this way, while a slice or a wide is
 * only assembled on the way out. It reads the tables backwards rather than borrowing
 * {@code SolveSolution}'s working, so agreeing with it means something.
 */
final class DisplayedSolutionReplay {

  private static final String FACES = "UDLRFB";

  private final CubieCube cube = new CubieCube();
  private CubeRotation frame = CubeRotation.byNotation("");

  /** Whether the cube is solved after following {@code displayed} from {@code scramble}. */
  static boolean solves(String scramble, String displayed) {
    DisplayedSolutionReplay replay = new DisplayedSolutionReplay();
    replay.scramble(scramble);
    for (String token : displayed.trim().split(" +")) {
      replay.turn(token);
    }
    return replay.cube.isSolved();
  }

  private void scramble(String scramble) {
    cube.fromFacelet(CubieCube.SOLVED_FACELET);
    for (String token : scramble.trim().split("\\s+")) {
      applyFace(token.substring(0, 1), token.endsWith("'"), token.endsWith("2") ? 2 : 1);
    }
  }

  private void turn(String token) {
    if (SolveMovesFormat.isRotation(token)) {
      frame = frame.then(CubeRotation.byNotation(token)); // moves no stickers, only the letters
      return;
    }
    String move = token.substring(0, 1) + (token.endsWith("'") ? "'" : "");
    for (int i = 0; i < (token.endsWith("2") ? 2 : 1); i++) {
      if (Character.isLowerCase(token.charAt(0))) {
        wide(move);
      } else if ("MES".indexOf(token.charAt(0)) >= 0) {
        slice(move);
      } else {
        applyInFrame(move);
      }
    }
  }

  /**
   * A wide is its far face and the core carried round. The spin composes as it stands, <b>not</b>
   * {@code seenFrom} the frame: a name read off the screen is already the solver's. Conjugating it
   * twice is invisible while the frame is the identity, and wrong everywhere else.
   */
  private void wide(String wide) {
    for (String face : everyFaceTurn()) {
      String spin = Wides.spinFor(face);
      if (wide.equals(Wides.forFaceAndSpin(face, spin))) {
        applyInFrame(face);
        frame = frame.then(CubeRotation.byNotation(spin));
        return;
      }
    }
    throw new IllegalArgumentException("not a wide: " + wide);
  }

  /** A slice is its two opposite faces and the core carried round with them. */
  private void slice(String slice) {
    for (String a : everyFaceTurn()) {
      for (String b : everyFaceTurn()) {
        String[] found = Slices.forPair(a, b);
        if (found != null && found[0].equals(slice)) {
          applyInFrame(a);
          applyInFrame(b);
          frame = frame.then(CubeRotation.byNotation(found[1]));
          return;
        }
      }
    }
    throw new IllegalArgumentException("not a slice: " + slice);
  }

  /** The letters on screen are the solver's and the cube's are fixed: read the frame backwards. */
  private void applyInFrame(String move) {
    char wanted = move.charAt(0);
    for (int i = 0; i < FACES.length(); i++) {
      char face = FACES.charAt(i);
      if (frame.mapFace(face) == wanted) {
        applyFace(String.valueOf(face), move.endsWith("'"), 1);
        return;
      }
    }
    throw new IllegalArgumentException("no face maps to " + move);
  }

  private void applyFace(String face, boolean prime, int times) {
    for (int i = 0; i < times; i++) {
      cube.applyMove(Face.valueOf(face), prime);
    }
  }

  private static String[] everyFaceTurn() {
    return new String[] {"U", "U'", "D", "D'", "L", "L'", "R", "R'", "F", "F'", "B", "B'"};
  }
}
