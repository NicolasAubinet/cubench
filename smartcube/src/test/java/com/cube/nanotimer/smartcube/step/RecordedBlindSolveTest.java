package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import org.junit.Test;

/**
 * A real blindfolded solve off the owner's cube (2026-07-26), replayed from its scramble and the
 * move stream it recorded. Kept because the synthetic fixtures next door are built from clean
 * three-cycles and a real solve is not: the counts swing on nearly every move, since an algorithm
 * takes half the cube apart on the way and puts it back only at the end.
 *
 * <p>Rotation tokens are skipped — a whole-cube rotation moves no piece, and the cube reports it
 * only so the moves can be written the way the solver made them.
 */
public class RecordedBlindSolveTest {

  /** The last move, and the only moment the cube was ever actually solved. */
  private static final long SOLVED_AT_MS = 88164;

  private final CubieCube cube = new CubieCube();
  private final BlindStepDetector detector = new BlindStepDetector();

  @Test
  public void theSolveIsOverOnlyOnItsLastMove() {
    replayUntil(Long.MAX_VALUE);

    assertTrue(detector.isComplete());
  }

  /**
   * The corners come home 3.7 seconds before the end, while three edges are still out, and the
   * solver turns fourteen more moves after it. Read off the best each type has ever reached, that
   * moment says solved — the edges having been briefly home earlier, in the middle of some other
   * algorithm. It is not, and the solve must not be closed there: everything after it would fall
   * outside the breakdown, as a stretch of the solve that belonged to no step.
   */
  @Test
  public void doesNotCallTheSolveFinishedWhereTheTwoTypesWereNeverHomeTogether() {
    replayUntil(SOLVED_AT_MS - 1);

    assertFalse(detector.isComplete());
  }

  private void replayUntil(long lastOffsetMs) {
    for (String token : RecordedBlindSolve.SCRAMBLE.split(" ")) {
      apply(token);
    }
    detector.reset(new CubeState(cube.toFaceCube()), 0);
    for (String token : RecordedBlindSolve.MOVES.trim().split("\s+")) {
      String notation = token.substring(0, token.indexOf('@'));
      long offsetMs = Long.parseLong(token.substring(token.indexOf('@') + 1));
      if (offsetMs > lastOffsetMs) {
        return;
      }
      if ("xyz".indexOf(notation.charAt(0)) >= 0) {
        continue;
      }
      Face face = Face.valueOf(notation.substring(0, 1));
      boolean prime = notation.endsWith("'");
      cube.applyMove(face, prime);
      detector.onState(new CubeState(cube.toFaceCube()), new CubeMove(face, prime, offsetMs));
    }
  }

  private void apply(String token) {
    Face face = Face.valueOf(token.substring(0, 1));
    for (int i = 0; i < (token.endsWith("2") ? 2 : 1); i++) {
      cube.applyMove(face, token.endsWith("'"));
    }
  }
}
