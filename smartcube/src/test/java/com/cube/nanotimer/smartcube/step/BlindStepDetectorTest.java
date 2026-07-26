package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import org.junit.Test;

/**
 * The solve is anchored where the timer started, with the cube untouched, and the first turn closes
 * the memorisation. What follows is read off how many pieces of each type are solved, so the solves
 * here are built out of real three-cycles: each one takes three pieces of a single type back where
 * they belong and leaves everything else where it was, which is what a blind algorithm does and the
 * only property the detector is watching for.
 */
public class BlindStepDetectorTest {

  private static final String SCRAMBLE = "R U2 F' L D B2 R' U F2 D'";

  /** How long the solver spent memorising, in every fixture here. */
  private static final long MEMO_MS = 9_000;

  /** Edge three-cycles: a U-perm, and the same one set up to a different three, so neither of them
   * moves a corner. Written in face turns alone — a slice reaches the app as its two face turns, and
   * what a solver meant by it is a question for the moves, not for the pieces they put home. */
  private static final String EDGE_CYCLE_A = "R2 U R U R' U' R' U' R' U R'";
  private static final String EDGE_CYCLE_B = "F2 R2 U R U R' U' R' U' R' U R' F2";

  /** Corner three-cycles: an A-perm, and the same one set up elsewhere. No edge moves. */
  private static final String CORNER_CYCLE_A = "R' F R' B2 R F' R' B2 R2";
  private static final String CORNER_CYCLE_B = "U2 R' F R' B2 R F' R' B2 R2 U2";

  /** Two corners and two edges swapped in one algorithm: exactly what a parity leaves to fix. */
  private static final String T_PERM = "R U R' U' R' F R2 U' R' U' R U R' F'";

  private final CubieCube cube = new CubieCube();
  private final BlindStepDetector detector = new BlindStepDetector();

  private long timestampMs = 1_000;

  @Test
  public void memorisingEndsOnTheFirstTurnAndNotAtTheStart() {
    String[] solve = {EDGE_CYCLE_A, EDGE_CYCLE_B, CORNER_CYCLE_A, CORNER_CYCLE_B};
    long startMs = startFrom(solve); // ten seconds of the timer running before a finger touches it

    assertNull(detector.getStepTimestampMs(0));
    assertFalse(detector.isComplete());

    play(solve);

    Long memo = detector.getStepTimestampMs(0);
    assertNotNull(memo);
    assertEquals(MEMO_MS, memo - startMs); // the wait, not zero: the solve did not begin at a move
    assertTrue(memo < detector.getStepTimestampMs(1));
    assertTrue(detector.isComplete());
    assertTrue(detector.matchesMethod());
  }

  @Test
  public void keepsTheEndItFirstReachedWhenTheSolverTurnsOnPastIt() {
    // Blindfolded, nobody can see the cube came out solved. Turning on past the end — thinking an
    // orientation is still out — must not move the moment the solve was finished, or unfinish it.
    String[] solve = {EDGE_CYCLE_A, EDGE_CYCLE_B, CORNER_CYCLE_A, CORNER_CYCLE_B};
    startFrom(solve);
    play(solve);
    int steps = detector.stepCount();
    Long finished = detector.getStepTimestampMs(steps - 1);

    play("R U R'");

    assertEquals(steps, detector.stepCount());
    assertEquals(finished, detector.getStepTimestampMs(steps - 1));
    assertTrue(detector.isComplete()); // it reached solved; it is not solved now
  }

  @Test
  public void aSolveThatNeverCameOutIsMemorisedAllTheSame() {
    scramble();
    start(8_000);

    play("R U R' U'"); // it went wrong, and the solver stopped the timer on a cube still scrambled

    assertNotNull(detector.getStepTimestampMs(0));
    assertNull(detector.getStepTimestampMs(1));
    assertFalse(detector.isComplete());
    assertTrue(detector.matchesMethod()); // the memo is what was observed, and it did happen
  }

  @Test
  public void aCubeAlreadySolvedWhenTheTimerStartedIsNotASolveAlreadyOver() {
    start(3_000); // never scrambled

    assertFalse(detector.isComplete());
  }

  @Test
  public void readsThePieceTypesInTheOrderTheySolvedThem() {
    String[] solve = {EDGE_CYCLE_A, EDGE_CYCLE_B, CORNER_CYCLE_A, CORNER_CYCLE_B};
    startFrom(solve);
    play(solve);

    assertEquals(3, detector.stepCount()); // an even permutation leaves no parity to fix
    assertEquals("memo", detector.stepName(0));
    assertEquals("edges", detector.stepName(1));
    assertEquals("corners", detector.stepName(2));
    assertTrue(detector.getStepTimestampMs(1) < detector.getStepTimestampMs(2));
    assertTrue(detector.isComplete());
    assertTrue(detector.matchesMethod());
  }

  @Test
  public void namesThemTheOtherWayRoundForACornersFirstSolve() {
    String[] solve = {CORNER_CYCLE_A, CORNER_CYCLE_B, EDGE_CYCLE_A, EDGE_CYCLE_B};
    startFrom(solve);
    play(solve);

    assertEquals("corners", detector.stepName(1));
    assertEquals("edges", detector.stepName(2));
    assertTrue(detector.matchesMethod());
  }

  /**
   * An odd permutation leaves each type sitting on a pair, and one algorithm swaps both back. The
   * parity algorithm is also the case the counts alone would get wrong: it disturbs a good half of
   * what is already solved while it runs, so a milestone read off the state of the moment would be
   * retracted by the very step that is resolving it.
   */
  @Test
  public void addsAParityStepWhenTheSolveEndedOnOne() {
    String[] solve = {EDGE_CYCLE_A, EDGE_CYCLE_B, CORNER_CYCLE_A, CORNER_CYCLE_B, T_PERM};
    startFrom(solve);
    play(solve);

    assertEquals(4, detector.stepCount());
    assertEquals("edges", detector.stepName(1));
    assertEquals("corners", detector.stepName(2));
    assertEquals("parity", detector.stepName(3));
    // The two blocks of cycles kept the times they finished at, through the algorithm that followed.
    assertTrue(detector.getStepTimestampMs(1) < detector.getStepTimestampMs(2));
    assertTrue(detector.getStepTimestampMs(2) < detector.getStepTimestampMs(3));
    assertTrue(detector.matchesMethod());
  }

  @Test
  public void putsTheParityBetweenTheTypesWhenItWasDoneThere() {
    String[] solve = {EDGE_CYCLE_A, EDGE_CYCLE_B, T_PERM, CORNER_CYCLE_A, CORNER_CYCLE_B};
    startFrom(solve);
    play(solve);

    assertEquals(4, detector.stepCount());
    assertEquals("edges", detector.stepName(1));
    assertEquals("parity", detector.stepName(2));
    assertEquals("corners", detector.stepName(3));
    assertTrue(detector.matchesMethod());
  }

  @Test
  public void doesNotMatchASolveThatFinishedBothTypesTogether() {
    // A scramble undone by its own inverse: the cube falls solved all at once, so neither piece type
    // was ever finished while the other was still waiting. Which is also what a sighted solve done
    // on a blind solve type looks like — its last algorithm places the last of both.
    scramble();
    start(4_000);

    play(invert(SCRAMBLE));

    assertTrue(detector.isComplete());
    assertFalse(detector.matchesMethod());
  }

  @Test
  public void aSolveStoppedInsideItsFirstPieceTypeContradictsNothing() {
    String[] solve = {EDGE_CYCLE_A, EDGE_CYCLE_B, CORNER_CYCLE_A, CORNER_CYCLE_B};
    startFrom(solve);
    play(EDGE_CYCLE_A); // one algorithm of the edges, and then it stopped

    // Memorisation, the edges as far as they got, and the turning that reached no further.
    assertEquals(3, detector.stepCount());
    assertEquals("edges", detector.stepName(1));
    assertEquals(1, detector.subStepCount(1));
    assertNull(detector.getStepTimestampMs(2));
    assertFalse(detector.isComplete());
    assertTrue(detector.matchesMethod()); // a prefix in order is still a prefix in order
  }

  /** Scramble with the inverse of the whole solve, then start the timer and memorise. */
  private long startFrom(String... solve) {
    for (String token : invert(join(solve)).split(" ")) {
      apply(token);
    }
    return start(MEMO_MS);
  }

  private static String join(String... moves) {
    return String.join(" ", moves);
  }

  private void scramble() {
    for (String token : SCRAMBLE.split(" ")) {
      apply(token);
    }
  }

  /** Start the solve, then let the given memorisation pass before any move. Returns the start. */
  private long start(long memoMs) {
    long startMs = timestampMs;
    detector.reset(state(), startMs);
    detector.onState(state(), null); // the cube reports its state; no move has been made
    timestampMs += memoMs;
    return startMs;
  }

  private void play(String... moves) {
    for (String token : join(moves).trim().split("\\s+")) {
      Face face = Face.valueOf(token.substring(0, 1));
      boolean prime = token.endsWith("'");
      for (int i = 0; i < (token.endsWith("2") ? 2 : 1); i++) {
        cube.applyMove(face, prime);
        detector.onState(state(), new CubeMove(face, prime, timestampMs));
        timestampMs += 100; // the first turn lands exactly where the memorisation ended
      }
    }
  }

  private void apply(String token) {
    Face face = Face.valueOf(token.substring(0, 1));
    for (int i = 0; i < (token.endsWith("2") ? 2 : 1); i++) {
      cube.applyMove(face, token.endsWith("'"));
    }
  }

  private CubeState state() {
    return new CubeState(cube.toFaceCube());
  }

  private static String invert(String moves) {
    String[] tokens = moves.trim().split("\\s+");
    StringBuilder inverted = new StringBuilder();
    for (int i = tokens.length - 1; i >= 0; i--) {
      String token = tokens[i];
      inverted.append(token.endsWith("2") ? token
          : token.endsWith("'") ? token.substring(0, token.length() - 1) : token + "'");
      inverted.append(' ');
    }
    return inverted.toString().trim();
  }
}
