package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * The detector against real blindfolded solves, replayed from their scrambles and stored moves.
 *
 * <p>These are what the design was settled on and what it has to keep clearing. The synthetic
 * fixtures next door are clean three-cycles played end to end; a real solve is not, and every rule
 * this detector has was put there by one of these solves breaking an earlier one.
 *
 * <p>Rotation tokens are skipped — a whole-cube rotation moves no piece, and the cube reports it
 * only so the moves can be written the way the solver made them.
 */
public class RecordedBlindSolveTest {

  /** The last move of solve 146, and the only moment its cube was ever actually solved. */
  private static final long SOLVED_AT_MS = 88164;

  private final CubieCube cube = new CubieCube();
  private final BlindStepDetector detector = new BlindStepDetector();

  @Test
  public void readsTheAlgorithmsOfASolveAndThePieceTypeEachBelongsTo() {
    replay(RecordedBlindSolve.SCRAMBLE, RecordedBlindSolve.MOVES, Long.MAX_VALUE);

    assertTrue(detector.isComplete());
    assertTrue(detector.matchesMethod());
    assertEquals(3, detector.stepCount());
    assertEquals("memo", detector.stepName(0));
    assertEquals("edges", detector.stepName(1));
    assertEquals("corners", detector.stepName(2));
    // Memorisation holds no algorithm; the rest is one part per algorithm, named for what it solved.
    assertEquals(0, detector.subStepCount(0));
    assertEquals(6, detector.subStepCount(1));
    assertEquals(4, detector.subStepCount(2));
    assertEquals("DR+LU", detector.subStepName(1, 0));
    // A cycle's last algorithm puts home its two targets and the buffer with them; the buffer is
    // where the cycle started, so it is not one of the pieces the algorithm was for.
    assertEquals("UFL+ULB", detector.subStepName(2, 3));
    // The corners end where the cube came out, not where they first happened to line up.
    assertEquals(SOLVED_AT_MS, (long) detector.getStepTimestampMs(2));
  }

  /**
   * The corners come home 3.7 seconds before the end, while three edges are still out, and the
   * solver turns fourteen more moves after it. Counted rather than read as algorithms, that moment
   * says solved — and everything after it falls outside the breakdown.
   */
  @Test
  public void doesNotCallTheSolveFinishedWhereTheTwoTypesWereNeverHomeTogether() {
    replay(RecordedBlindSolve.SCRAMBLE, RecordedBlindSolve.MOVES, SOLVED_AT_MS - 1);

    assertFalse(detector.isComplete());
  }

  @Test
  public void readsASolveWithNoParityAsTwoPieceTypesAndNothingElse() {
    replay(RecordedBlindSolve.SCRAMBLE_163, RecordedBlindSolve.MOVES_163, Long.MAX_VALUE);

    assertTrue(detector.matchesMethod());
    assertEquals(3, detector.stepCount()); // an even permutation leaves no parity to fix
    assertEquals(6, detector.subStepCount(1));
    assertEquals(3, detector.subStepCount(2));
  }

  /** Solve 164 has an edge flip and a corner twist: algorithms that move two pieces, not three. */
  @Test
  public void countsAFlipAndATwistAsAlgorithmsOfTheirOwn() {
    replay(RecordedBlindSolve.SCRAMBLE_164, RecordedBlindSolve.MOVES_164, Long.MAX_VALUE);

    assertTrue(detector.matchesMethod());
    assertEquals(6, detector.subStepCount(1));
    assertEquals(4, detector.subStepCount(2));
    // Flipped and twisted where they stand: nothing was shot anywhere, so the buffer being among
    // them is the algorithm's doing and it is named.
    assertEquals("UF+BL", detector.subStepName(1, 5)); // the two edges flipped in place
    assertEquals("URF+DBL", detector.subStepName(2, 3)); // the two corners twisted
  }

  /**
   * Solve 165 is the awkward one: an odd scramble, so it ends on a parity — and part way through the
   * edges the solver spotted a mistake, undid the algorithm and did another. An undo is an algorithm
   * like any other and belongs to the piece type it interrupted; what it was worth is nothing, which
   * is what naming it says. Demanding that an algorithm make progress leaves it unread, and the
   * state everything after is compared against is then one the solve has abandoned.
   */
  @Test
  public void readsAParityAndTheAlgorithmsTheSolverUndid() {
    replay(RecordedBlindSolve.SCRAMBLE_165, RecordedBlindSolve.MOVES_165, Long.MAX_VALUE);

    assertTrue(detector.isComplete());
    assertTrue(detector.matchesMethod());
    assertEquals(4, detector.stepCount());
    assertEquals("parity", detector.stepName(3));
    assertEquals(1, detector.subStepCount(3));
    // The one algorithm that puts back two of each, which is all a parity ever is.
    assertEquals("UF+UR+URF+DLF", detector.subStepName(3, 0));

    List<String> edges = new ArrayList<String>();
    for (int part = 0; part < detector.subStepCount(1); part++) {
      edges.add(detector.subStepName(1, part));
    }
    assertEquals(8, edges.size());
    assertEquals(2, Collections.frequency(edges, "undo"));
  }

  /**
   * A commutator is named by its two targets, and a target is a sticker rather than a piece. Solve
   * 163's last corner algorithm puts three home — two targets and the buffer closing the cycle
   * behind them — and is named by the two, in the order they were shot.
   */
  @Test
  public void namesAnAlgorithmByTheStickersItsTargetsWereShotTo() {
    replay(RecordedBlindSolve.SCRAMBLE_163, RecordedBlindSolve.MOVES_163, Long.MAX_VALUE);

    assertEquals("BUL+UBR", detector.subStepName(2, 2));
    // The same on the edges: three pieces home, two of them targets.
    assertEquals("UB+UR", detector.subStepName(1, 5));
    // A cycle break solves only its second target -- the first receives the buffer's piece, which
    // does not belong there. Both were shot at, so both are named.
    assertEquals("RU+LU", detector.subStepName(1, 1));
  }

  /**
   * Solve 184 went wrong on its opening algorithm, before any piece type had got anywhere. That
   * leaves an algorithm gaining nothing with no stretch behind it to belong to, and standing alone
   * it opened the solve with a step that is not a piece type — it belongs to the stretch it
   * precedes. A cycle broken into on the first algorithm of a clean solve does the same thing.
   *
   * <p>It also shows an edge shot to <em>both</em> its stickers: flipped where it stands, it is two
   * targets on one piece, and separating them by piece rather than by sticker loses one of them.
   */
  @Test
  public void keepsAMisfiredOpeningInsideTheStretchItPrecedes() {
    replay(RecordedBlindSolve.SCRAMBLE_184, RecordedBlindSolve.MOVES_184, Long.MAX_VALUE);

    assertEquals(4, detector.stepCount()); // memo, edges, corners, parity -- and nothing else
    assertEquals("edges", detector.stepName(1));
    assertEquals("undo", detector.subStepName(1, 0)); // the misfire, inside the edges it precedes
    assertEquals("RF+FR", detector.subStepName(1, 1)); // one edge, both its stickers
    for (int step = 1; step < detector.stepCount(); step++) {
      for (int part = 0; part < detector.subStepCount(step); part++) {
        String name = detector.subStepName(step, part);
        assertTrue("every algorithm names what it was shot at: " + name,
            name.equals("undo") || name.contains("+"));
      }
    }
  }

  /**
   * With no frame established the names are spelled as the cube reports them: wrong about the
   * holding frame, and so about the sticker and the buffer, but it is all a solve can be read as
   * when nothing says how the cube was picked up.
   */
  @Test
  public void spellsTheNamesAsReportedUntilTheFrameIsKnown() {
    replay(RecordedBlindSolve.SCRAMBLE_163, RecordedBlindSolve.MOVES_163, Long.MAX_VALUE,
        Integer.valueOf(BlindTargets.UNKNOWN_FRAME));

    assertEquals("UFL+ULB+UBR", detector.subStepName(2, 2)); // the buffer among them, unnamed as such
    assertEquals("UR+UL+UB", detector.subStepName(1, 5));
  }

  /**
   * The pickup rotation is taken from the solve itself: the gyro writes it as the rotation token
   * standing before the first face turn, and every one of these solves opens on {@code y} — the
   * scramble turned green in front, the solve turned red in front. Nothing is told to the test that
   * the app does not have, so the frame, its inverse and the names are all proved together.
   */
  private void replay(String scramble, String moves, long lastOffsetMs) {
    replay(scramble, moves, lastOffsetMs, null);
  }

  private void replay(String scramble, String moves, long lastOffsetMs, Integer frame) {
    if (frame != null) {
      detector.setHoldingFrame(frame);
    }
    boolean turned = false;
    for (String token : moves.trim().split("\\s+")) {
      String notation = token.substring(0, token.indexOf('@'));
      if ("xyz".indexOf(notation.charAt(0)) < 0) {
        break; // the first face turn: any rotation after this one is the solve's, not the pick-up
      }
      if (frame == null) {
        detector.setPickupRotation(CubeRotation.byNotation(notation));
      }
      turned = true;
    }
    assertTrue("expected a pick-up rotation before the first turn", turned);
    for (String token : scramble.trim().split("\\s+")) {
      apply(token);
    }
    detector.reset(new CubeState(cube.toFaceCube()), 0);
    for (String token : moves.trim().split("\\s+")) {
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
