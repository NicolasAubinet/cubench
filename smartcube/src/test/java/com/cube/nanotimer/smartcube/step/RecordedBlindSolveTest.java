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

  private static final String SCRAMBLE = "B2 R2 B2 L2 U L2 U L2 F2 D' F' L' F' D' B' R B2 F2 U'";

  /** Notation and offset from the solve start, as stored. Memorisation ran to the first, at 42.5s. */
  private static final String MOVES =
      "y@42566 F@42566 F@42688 R'@42911 L@42925 x@42926 D'@43049 R'@43140 D@43279 L'@43383 "
          + "R@43391 x'@43392 F'@43547 R@43632 F'@43854 B@44543 U@44714 F@44934 D'@45110 U@45124 "
          + "L'@45250 L'@45338 D@45469 U'@45474 y@45475 F@45689 U'@45786 B'@46335 B@47146 "
          + "F'@47162 z@47163 L@47943 B'@48087 L'@48254 F@48410 B'@48621 F@48626 z'@48627 "
          + "B'@48746 R@48866 B@49107 R'@49298 B@49642 F'@49654 z@49655 B'@52384 U@52791 B@52952 "
          + "B@53056 R'@53260 L@53266 x@53267 U'@53464 U'@53580 R@53727 L'@53743 x'@53744 "
          + "U'@53957 B@54138 R'@56869 F'@57416 F'@57529 D@57675 U'@57679 y@57680 R@57841 "
          + "R@57948 D'@58084 U@58096 y'@58097 R@58599 B'@64752 U'@64945 B@65120 U@65278 B@65502 "
          + "F'@65564 z@65565 L'@65715 B'@65993 L@66207 F@66606 B@68378 D'@68521 B'@68680 "
          + "U'@68781 B@68920 D@69016 B'@69198 U@69421 D@69834 B@69977 B@70048 U@70209 B'@70318 "
          + "B'@70392 U'@70497 B@70598 B@70684 D'@70801 B'@70973 B'@71073 U@71183 z'@71361 "
          + "B@71361 B@71741 U'@71885 B'@72122 B'@72515 B@73731 U'@73886 D@73911 B'@74072 "
          + "U@74151 B@74241 D'@74352 B'@74442 U'@74555 B@74658 U@74801 B'@75090 U@81449 U@81604 "
          + "B@82495 U@82688 B'@82846 U@82979 B@83277 U'@83656 U'@83800 B'@84418 F@86074 F@86280 "
          + "U'@86415 F'@86586 F'@86685 R@87138 L'@87159 U@87388 U@87528 L@87635 R'@87636 "
          + "x@87637 U@87864 F'@88034 F'@88164";

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
    for (String token : SCRAMBLE.split(" ")) {
      apply(token);
    }
    detector.reset(new CubeState(cube.toFaceCube()), 0);
    for (String token : MOVES.trim().split("\s+")) {
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
