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
 * the memorisation. The cube is scrambled by a sequence and solved by its inverse — what was solved
 * matters nothing here, only when the turning began and when it came out.
 */
public class BlindStepDetectorTest {

  private static final String SCRAMBLE = "R U2 F' L D B2 R' U F2 D'";

  private final CubieCube cube = new CubieCube();
  private final BlindStepDetector detector = new BlindStepDetector();

  private long timestampMs = 1_000;

  @Test
  public void memorisingEndsOnTheFirstTurnAndExecutionOnTheSolvedCube() {
    scramble();
    long startMs = start(10_000); // ten seconds of the timer running before a finger touches it

    assertNull(detector.getStepTimestampMs(BlindStepDetector.MEMO));
    assertFalse(detector.isComplete());

    play(invert(SCRAMBLE));

    Long memo = detector.getStepTimestampMs(BlindStepDetector.MEMO);
    Long execution = detector.getStepTimestampMs(BlindStepDetector.EXECUTION);
    assertNotNull(memo);
    assertEquals(10_000, memo - startMs); // the wait, not zero: the solve did not begin at a move
    assertTrue(memo < execution);
    assertTrue(detector.isComplete());
    assertTrue(detector.matchesMethod());
  }

  @Test
  public void keepsTheEndItFirstReachedWhenTheSolverTurnsOnPastIt() {
    // Blindfolded, nobody can see the cube came out solved. Turning on past the end — thinking an
    // orientation is still out — must not move the moment the solve was finished, or unfinish it.
    scramble();
    start(5_000);
    play(invert(SCRAMBLE));
    Long execution = detector.getStepTimestampMs(BlindStepDetector.EXECUTION);

    play("R U R'");

    assertEquals(execution, detector.getStepTimestampMs(BlindStepDetector.EXECUTION));
    assertTrue(detector.isComplete()); // it reached solved; it is not solved now
  }

  @Test
  public void aSolveThatNeverCameOutIsMemorisedAllTheSame() {
    scramble();
    start(8_000);

    play("R U R' U'"); // it went wrong, and the solver stopped the timer on a cube still scrambled

    assertNotNull(detector.getStepTimestampMs(BlindStepDetector.MEMO));
    assertNull(detector.getStepTimestampMs(BlindStepDetector.EXECUTION));
    assertFalse(detector.isComplete());
    assertTrue(detector.matchesMethod()); // the memo is what was observed, and it did happen
  }

  @Test
  public void aCubeAlreadySolvedWhenTheTimerStartedIsNotASolveAlreadyOver() {
    start(3_000); // never scrambled

    assertNull(detector.getStepTimestampMs(BlindStepDetector.EXECUTION));
    assertFalse(detector.isComplete());
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

  private void play(String moves) {
    for (String token : moves.trim().split("\\s+")) {
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
