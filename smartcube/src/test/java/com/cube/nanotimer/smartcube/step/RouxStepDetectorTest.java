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
 * The solves here are built backwards: each step's moves are chosen to preserve what the steps after
 * it have already solved, and the scramble is the whole thing inverted. So playing the steps forward
 * is a Roux solve by construction, whatever the actual moves are.
 *
 * <p>They are written the way the solver turns them and translated into what the cube reports, which
 * is what makes them worth running. A slice is reported as its two face turns, and — because the
 * core has turned with them — every face turn after it arrives under a different letter. Both are
 * exactly what the detector has to see through.
 */
public class RouxStepDetectorTest {

  private static final String SUNE = "R U R' U R U2 R'";
  private static final String T_PERM = "R U R' U' R' F R2 U' R' U' R U R' F'"; // its own inverse

  /**
   * First block, second block, orienting the corners, permuting them, then the six last edges in
   * their three parts: orienting them, placing the two beside the blocks, and cycling the rest.
   */
  private static final String[] SOLVE = {
    "U R' F R", "R U R' U M U' R U R'", SUNE, T_PERM, "M2 U M", "U M' U2 M", "M2",
  };

  /** A case whose corners come out oriented and permuted on the same move. */
  private static final String ONE_LOOK_CORNERS = "R U R' U' R' F R F'";

  private static final int FIRST_BLOCK = 0, SECOND_BLOCK = 1, ORIENT = 2, PERMUTE = 3;
  private static final int EO = 4, SIDE_EDGES = 5, LAST_FOUR = 6;

  private final CubieCube cube = new CubieCube();
  private final RouxStepDetector detector = new RouxStepDetector();

  /** Quarter turns of drift the slices have put between the solver's frame and the cube's. */
  private int drift;
  private long timestampMs;

  @Test
  public void reachesTheStepsInOrderThroughASolve() {
    startFrom(SOLVE);
    assertNull(detector.getStepTimestampMs(RouxStepDetector.FB));

    play(SOLVE[FIRST_BLOCK]);
    Long firstBlock = detector.getStepTimestampMs(RouxStepDetector.FB);
    assertNotNull(firstBlock);
    assertNull(detector.getStepTimestampMs(RouxStepDetector.SB));

    play(SOLVE[SECOND_BLOCK]);
    Long secondBlock = detector.getStepTimestampMs(RouxStepDetector.SB);
    assertNotNull(secondBlock);
    assertTrue(firstBlock < secondBlock);
    assertNull(detector.getStepTimestampMs(RouxStepDetector.CMLL));

    play(SOLVE[ORIENT], SOLVE[PERMUTE]);
    Long corners = detector.getStepTimestampMs(RouxStepDetector.CMLL);
    assertNotNull(corners);
    assertTrue(secondBlock < corners);
    assertFalse(detector.isComplete());

    play(SOLVE[EO], SOLVE[SIDE_EDGES], SOLVE[LAST_FOUR]);
    Long lastEdges = detector.getStepTimestampMs(RouxStepDetector.LSE);
    assertNotNull(lastEdges);
    assertTrue(corners < lastEdges);
    assertTrue(detector.isComplete());
    assertTrue(detector.matchesMethod());

    // The blocks were built to the left of the down face, and the steps keep the times they were
    // first reached at even though the ones after them disturb them in passing.
    assertEquals(Face.D, detector.getDownFace());
    assertEquals(Face.L, detector.getLeftFace());
    assertEquals(firstBlock, detector.getStepTimestampMs(RouxStepDetector.FB));
    assertEquals(secondBlock, detector.getStepTimestampMs(RouxStepDetector.SB));
  }

  @Test
  public void splitsTheLastStepIntoItsThreeParts() {
    startFrom(SOLVE);
    play(SOLVE[FIRST_BLOCK], SOLVE[SECOND_BLOCK], SOLVE[ORIENT], SOLVE[PERMUTE]);
    assertNull(detector.getSubStepTimestampMs(RouxStepDetector.LSE, 0));

    play(SOLVE[EO]);
    Long oriented = detector.getSubStepTimestampMs(RouxStepDetector.LSE, 0);
    assertNotNull(oriented);

    play(SOLVE[SIDE_EDGES]);
    Long sideEdges = detector.getSubStepTimestampMs(RouxStepDetector.LSE, 1);
    assertNotNull(sideEdges);
    assertTrue(oriented <= sideEdges);

    play(SOLVE[LAST_FOUR]);
    Long finished = detector.getSubStepTimestampMs(RouxStepDetector.LSE, 2);
    assertEquals(detector.getStepTimestampMs(RouxStepDetector.LSE), finished);
    assertTrue(sideEdges <= finished);
  }

  @Test
  public void splitsTwoLookCornersIntoOrientingAndPermuting() {
    startFrom(SOLVE);
    play(SOLVE[FIRST_BLOCK], SOLVE[SECOND_BLOCK]);

    play(SOLVE[ORIENT]); // orienting them leaves them still to permute
    Long oriented = detector.getSubStepTimestampMs(RouxStepDetector.CMLL, 0);
    assertNotNull(oriented);
    assertNull(detector.getSubStepTimestampMs(RouxStepDetector.CMLL, 1));
    assertNull(detector.getStepTimestampMs(RouxStepDetector.CMLL));

    play(SOLVE[PERMUTE]);
    Long permuted = detector.getSubStepTimestampMs(RouxStepDetector.CMLL, 1);
    assertNotNull(permuted);
    assertTrue(oriented < permuted);
    assertEquals(permuted, detector.getStepTimestampMs(RouxStepDetector.CMLL));
  }

  @Test
  public void reachesBothCornerPartsOnTheSameMoveWhenTheyAreDoneInOneLook() {
    String[] solve = {SOLVE[FIRST_BLOCK], SOLVE[SECOND_BLOCK], ONE_LOOK_CORNERS,
        SOLVE[EO], SOLVE[SIDE_EDGES], SOLVE[LAST_FOUR]};
    startFrom(solve);
    play(solve[0], solve[1], solve[2]);

    Long corners = detector.getStepTimestampMs(RouxStepDetector.CMLL);
    assertNotNull(corners);
    assertEquals(corners, detector.getSubStepTimestampMs(RouxStepDetector.CMLL, 0));
    assertEquals(corners, detector.getSubStepTimestampMs(RouxStepDetector.CMLL, 1));
  }

  @Test
  public void datesACornerPartThatWasAlreadyRightToTheStartOfTheStep() {
    // A case that only needs orienting: the corners come out of the blocks already permuted, so
    // that part of the step costs nothing rather than being credited with the whole look.
    String[] solve = {SOLVE[FIRST_BLOCK], SOLVE[SECOND_BLOCK], SUNE,
        SOLVE[EO], SOLVE[SIDE_EDGES], SOLVE[LAST_FOUR]};
    startFrom(solve);
    play(solve[0], solve[1]);
    Long blocks = detector.getStepTimestampMs(RouxStepDetector.SB);

    play(solve[2]);
    assertEquals(blocks, detector.getSubStepTimestampMs(RouxStepDetector.CMLL, 1));
    assertEquals(detector.getStepTimestampMs(RouxStepDetector.CMLL),
        detector.getSubStepTimestampMs(RouxStepDetector.CMLL, 0));
  }

  @Test
  public void findsBlocksBuiltOnAnyPairOfFaces() {
    // The same solve held a quarter turn away: the blocks are built either side of the front face.
    startTilted(SOLVE);
    for (String step : SOLVE) {
      playTilted(step);
    }

    assertTrue(detector.isComplete());
    assertTrue(detector.matchesMethod());
    assertEquals(Face.F, detector.getDownFace());
    assertEquals(Face.L, detector.getLeftFace());
  }

  @Test
  public void matchesOnAPrefixWhenTheSolveStopsPartWay() {
    startFrom(SOLVE);
    play(SOLVE[FIRST_BLOCK], SOLVE[SECOND_BLOCK]);

    assertFalse(detector.isComplete());
    assertTrue(detector.matchesMethod()); // both blocks, with the middle slice still open
    assertNull(detector.getStepTimestampMs(RouxStepDetector.CMLL));
  }

  @Test
  public void doesNotMatchOnASingleBlock() {
    startFrom(SOLVE);
    play(SOLVE[FIRST_BLOCK]);

    assertNotNull(detector.getStepTimestampMs(RouxStepDetector.FB));
    assertFalse(detector.matchesMethod()); // every method builds a block eventually
  }

  @Test
  public void doesNotMatchACfopSolve() {
    // The CFOP detector's own solve: cross, a pair, OLL, PLL. Its first two layers contain both
    // Roux blocks, so only the edges left through the middle tell the two methods apart.
    apply(T_PERM, SUNE, "R U' R'", "F'");
    detector.reset(state(), timestampMs);

    play("F", "R U R'", "R U2 R' U' R U' R'", T_PERM);

    assertTrue(detector.isComplete());
    assertFalse(detector.matchesMethod());
  }

  @Test
  public void aRouxSolveCanFitCfopToo() {
    // Worth pinning, because it was a surprise, and a real solve found it before any test did: CFOP
    // is not rejected structurally. Its cross face is picked from all six, and a first block builds
    // three of the left face's four cross edges — the fourth landing mid-last-step — so on that
    // face a cross really does precede its own first two layers, by most of the solve. Which method
    // such a solve is stored as is settled by how narrow each fit is, not here.
    String[] solve = {SOLVE[FIRST_BLOCK], SOLVE[SECOND_BLOCK], SOLVE[ORIENT], SOLVE[PERMUTE],
        SOLVE[EO], SOLVE[SIDE_EDGES], "U2 M2 U2 M2"};
    CFOPStepDetector cfop = new CFOPStepDetector();
    apply(invert(join(solve)));
    cfop.reset(state(), timestampMs);
    for (String step : solve) {
      for (String token : model(step)) {
        Face face = Face.valueOf(token.substring(0, 1));
        boolean prime = token.endsWith("'");
        for (int i = 0; i < (token.endsWith("2") ? 2 : 1); i++) {
          cube.applyMove(face, prime);
          timestampMs += 100;
          cfop.onState(state(), new CubeMove(face, prime, timestampMs));
        }
      }
    }

    assertTrue(cfop.isComplete());
    assertTrue(cfop.getStepTimestampMs(CFOPStepDetector.CROSS)
        < cfop.getStepTimestampMs(CFOPStepDetector.F2L));
    assertTrue(cfop.matchesMethod());
  }

  @Test
  public void readsThatSameSolveAsRoux() {
    String[] solve = {SOLVE[FIRST_BLOCK], SOLVE[SECOND_BLOCK], SOLVE[ORIENT], SOLVE[PERMUTE],
        SOLVE[EO], SOLVE[SIDE_EDGES], "U2 M2 U2 M2"};
    startFrom(solve);
    play(solve);

    assertTrue(detector.isComplete());
    assertTrue(detector.matchesMethod());
  }

  /** Scramble the cube with the inverse of the solve, then arm the detector on the result. */
  private void startFrom(String... solve) {
    apply(invert(join(solve)));
    detector.reset(state(), timestampMs);
  }

  private void startTilted(String... solve) {
    apply(tilt(invert(join(solve))));
    detector.reset(state(), timestampMs);
  }

  /** Play the moves into the detector, one quarter turn per state, 100ms apart. */
  private void play(String... moves) {
    for (String token : model(join(moves))) {
      Face face = Face.valueOf(token.substring(0, 1));
      boolean prime = token.endsWith("'");
      for (int i = 0; i < (token.endsWith("2") ? 2 : 1); i++) {
        cube.applyMove(face, prime);
        timestampMs += 100;
        detector.onState(state(), new CubeMove(face, prime, timestampMs));
      }
    }
  }

  private void playTilted(String moves) {
    play(tilt(moves));
  }

  private void apply(String... moves) {
    for (String token : model(join(moves))) {
      Face face = Face.valueOf(token.substring(0, 1));
      for (int i = 0; i < (token.endsWith("2") ? 2 : 1); i++) {
        cube.applyMove(face, token.endsWith("'"));
      }
    }
  }

  private CubeState state() {
    return new CubeState(cube.toFaceCube());
  }

  private static String join(String... moves) {
    return String.join(" ", moves);
  }

  /**
   * What the cube reports for moves the solver makes: a slice as the two face turns it registers —
   * the core turned, not a face — and every later turn under the letter of the face the core has
   * since carried it to.
   */
  private String[] model(String moves) {
    java.util.List<String> reported = new java.util.ArrayList<>();
    for (String token : moves.trim().split("\\s+")) {
      if (token.charAt(0) == 'M') {
        reported.add(token.endsWith("2") ? "R2" : token.endsWith("'") ? "R'" : "R");
        reported.add(token.endsWith("2") ? "L2" : token.endsWith("'") ? "L" : "L'");
        drift = (drift + (token.endsWith("2") ? 2 : token.endsWith("'") ? 3 : 1)) % 4;
      } else {
        reported.add(shift(token, drift));
      }
    }
    return reported.toArray(new String[0]);
  }

  /** The face a slice's turn of the core has carried the solver's own face to. */
  private static String shift(String token, int quarters) {
    int face = "UBDF".indexOf(token.charAt(0));
    return face == -1 ? token
        : "UBDF".charAt((face + quarters) % 4) + token.substring(1);
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

  /** The same moves held a quarter turn towards the solver: the front face becomes the up one. */
  private static String tilt(String moves) {
    StringBuilder tilted = new StringBuilder();
    for (char character : moves.toCharArray()) {
      int face = "FUBD".indexOf(character);
      tilted.append(face == -1 ? character : "UBDF".charAt(face));
    }
    return tilted.toString();
  }
}
