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
 * The solves here are built backwards, as the Roux detector's are: each step's moves are chosen to
 * preserve what the steps after it have already solved, and the scramble is the whole thing
 * inverted. So playing the steps forward is a layer-by-layer solve by construction, and it is built
 * from the algorithms a beginner is actually taught.
 *
 * <p>Layer-by-layer needs no slice, so — unlike Roux — what the solver turns is what the cube
 * reports, and the moves are played in as they are written.
 */
public class LblStepDetectorTest {

  private static final String CROSS = "R2 F2 U R U'";

  /** Inserting a first-layer corner from the top: the beginner's repeated four. One per slot, each
   * named for the face it is turned on. */
  private static final String CORNER_R = "R U R' U'";
  private static final String CORNER_B = "B U B' U'";
  private static final String CORNER_L = "L U L' U'";
  private static final String CORNER_F = "F U F' U'";

  /** Sending a middle edge down out of the top, which restores the first layer behind it. */
  private static final String EDGE_FR = "U R U' R' U' F' U F";
  private static final String EDGE_RB = "U B U' B' U' R' U R";
  private static final String EDGE_BL = "U L U' L' U' B' U B";
  private static final String EDGE_LF = "U F U' F' U' L' U L";

  private static final String ORIENT_EDGES = "F R U R' U' F'";
  private static final String ORIENT_CORNERS = "R U R' U R U2 R'"; // sune
  private static final String PERMUTE_CORNERS = "R' F R' B2 R F' R' B2 R2"; // an A perm
  private static final String PERMUTE_EDGES = "R U' R U R U R U' R' U' R2"; // a U perm

  private static final String[] SOLVE = {
    CROSS,
    CORNER_R, CORNER_B, CORNER_L, CORNER_F,
    EDGE_FR, EDGE_RB, EDGE_BL, EDGE_LF,
    ORIENT_EDGES, ORIENT_CORNERS, PERMUTE_CORNERS, PERMUTE_EDGES,
  };

  /** The same solve keyholed: the fourth corner is left out so the empty slot can carry the second
   * layer's edges through, and goes in at the end. */
  private static final String[] KEYHOLED = {
    CROSS,
    CORNER_R, CORNER_B, CORNER_L,
    EDGE_FR, EDGE_RB, EDGE_BL,
    CORNER_F, EDGE_LF,
    ORIENT_EDGES, ORIENT_CORNERS, PERMUTE_CORNERS, PERMUTE_EDGES,
  };

  /** A solve that puts each corner in with its own edge, which is what layer-by-layer is not. */
  private static final String[] PAIRED = {
    CROSS,
    "U R U' R'", "U B U' B'", "U L U' L'", "U F U' F'",
    ORIENT_EDGES, ORIENT_CORNERS, PERMUTE_CORNERS, PERMUTE_EDGES,
  };

  private static final int EO = 0, CO = 1, CP = 2, EP = 3;

  private final CubieCube cube = new CubieCube();
  private final LblStepDetector detector = new LblStepDetector();

  private long timestampMs;

  @Test
  public void readsATextbookSolveAsItsFourSteps() {
    startFrom(SOLVE);
    assertNull(detector.getStepTimestampMs(0));

    play(CROSS);
    Long cross = detector.getStepTimestampMs(0);
    assertNotNull(cross);
    assertEquals("cross", detector.stepName(0));

    play(CORNER_R, CORNER_B, CORNER_L, CORNER_F);
    assertEquals("layer1", detector.stepName(1));
    assertEquals(4, detector.subStepCount(1));
    Long firstLayer = detector.getStepTimestampMs(1);
    assertTrue(cross < firstLayer);

    play(EDGE_FR, EDGE_RB, EDGE_BL, EDGE_LF);
    assertEquals("layer2", detector.stepName(2));
    assertEquals(4, detector.subStepCount(2));
    Long secondLayer = detector.getStepTimestampMs(2);
    assertTrue(firstLayer < secondLayer);
    assertFalse(detector.isComplete());

    play(ORIENT_EDGES, ORIENT_CORNERS, PERMUTE_CORNERS, PERMUTE_EDGES);
    assertEquals("ll", detector.stepName(3));
    assertTrue(secondLayer < detector.getStepTimestampMs(3));
    assertEquals(4, detector.stepCount());
    assertTrue(detector.isComplete());
    assertTrue(detector.matchesMethod());
    assertEquals(Face.D, detector.getCrossFace());
  }

  @Test
  public void namesEachPieceOfTheLayersBySlot() {
    startFrom(SOLVE);
    play(SOLVE);

    assertEquals("corner_fr", detector.subStepName(1, 0));
    assertEquals("edge_br", detector.subStepName(2, 0));
    for (int part = 0; part < 4; part++) {
      assertTrue(detector.subStepName(1, part).startsWith("corner_"));
      assertTrue(detector.subStepName(2, part).startsWith("edge_"));
    }
  }

  @Test
  public void splitsTheLastLayerIntoItsFourParts() {
    startFrom(SOLVE);
    play(CROSS, CORNER_R, CORNER_B, CORNER_L, CORNER_F, EDGE_FR, EDGE_RB, EDGE_BL, EDGE_LF);
    assertEquals(4, detector.subStepCount(3));
    assertNull(detector.getSubStepTimestampMs(3, EO));

    play(ORIENT_EDGES);
    Long edgesOriented = detector.getSubStepTimestampMs(3, EO);
    assertNotNull(edgesOriented);
    assertNull(detector.getSubStepTimestampMs(3, CO));

    play(ORIENT_CORNERS);
    Long cornersOriented = detector.getSubStepTimestampMs(3, CO);
    assertTrue(edgesOriented < cornersOriented);
    assertNull(detector.getSubStepTimestampMs(3, CP));

    play(PERMUTE_CORNERS);
    Long cornersPermuted = detector.getSubStepTimestampMs(3, CP);
    assertTrue(cornersOriented < cornersPermuted);
    assertNull(detector.getSubStepTimestampMs(3, EP));

    play(PERMUTE_EDGES);
    assertEquals(detector.getStepTimestampMs(3), detector.getSubStepTimestampMs(3, EP));
  }

  @Test
  public void readsTheLastLayerPartsInWhateverOrderTheyWereDone() {
    // The same four parts, permuting the edges before the corners: the order beginners differ on.
    String[] solve = {
      CROSS,
      CORNER_R, CORNER_B, CORNER_L, CORNER_F,
      EDGE_FR, EDGE_RB, EDGE_BL, EDGE_LF,
      ORIENT_EDGES, ORIENT_CORNERS, PERMUTE_EDGES, PERMUTE_CORNERS,
    };
    startFrom(solve);
    play(solve);

    assertTrue(detector.isComplete());
    assertTrue(detector.matchesMethod());
    assertTrue(detector.getSubStepTimestampMs(3, EP) < detector.getSubStepTimestampMs(3, CP));
  }

  @Test
  public void readsAKeyholedSolveAsTwoStretchesOfTheFirstLayer() {
    startFrom(KEYHOLED);
    play(KEYHOLED);

    assertTrue(detector.isComplete());
    assertTrue(detector.matchesMethod());
    assertEquals(5, detector.stepCount());
    assertEquals("cross", detector.stepName(0));
    assertEquals("layer1", detector.stepName(1));
    assertEquals("layer2", detector.stepName(2));
    assertEquals("layer1", detector.stepName(3)); // the corner held back for the keyhole
    assertEquals("ll", detector.stepName(4));

    assertEquals(3, detector.subStepCount(1));
    assertEquals(1, detector.subStepCount(3));
    assertTrue(detector.getStepTimestampMs(1) < detector.getStepTimestampMs(2));
    assertTrue(detector.getStepTimestampMs(2) <= detector.getStepTimestampMs(3));
  }

  @Test
  public void keepsAPieceLandingWithAnotherInTheStretchAlreadyOpen() {
    // The keyholed solve's last edge lands on the same move as the corner. One move is one step, so
    // it stays with the second layer rather than opening a stretch of its own for that single move.
    startFrom(KEYHOLED);
    play(KEYHOLED);

    assertEquals(4, detector.subStepCount(2));
    assertEquals(detector.getStepTimestampMs(2), detector.getStepTimestampMs(3));
  }

  @Test
  public void doesNotReadAnHPermsEdgesAsPermuted() {
    // Some turn of the layer does place all four edges — a half turn — but it is the one that takes
    // the corners out, and the edges are as unsolved as they look.
    detector.reset(new CubeState(exchanged(Cubies.EDGES)), 0);

    int lastLayer = detector.stepCount() - 1;
    assertEquals("ll", detector.stepName(lastLayer));
    assertNull(detector.getSubStepTimestampMs(lastLayer, EP));
    assertNotNull(detector.getSubStepTimestampMs(lastLayer, CP)); // home where they stand
  }

  @Test
  public void doesNotReadADiagonalSwapAsPermutedCorners() {
    detector.reset(new CubeState(exchanged(Cubies.CORNERS)), 0);

    int lastLayer = detector.stepCount() - 1;
    assertNull(detector.getSubStepTimestampMs(lastLayer, CP));
    assertNotNull(detector.getSubStepTimestampMs(lastLayer, EP));
  }

  @Test
  public void refusesASolveThatPutsEachCornerInWithItsEdge() {
    startFrom(PAIRED);
    play(PAIRED);

    assertTrue(detector.isComplete());
    assertFalse(detector.matchesMethod()); // the layers were never built one before the other
  }

  @Test
  public void matchesOnAPrefixWhenTheSolveStopsPartWay() {
    startFrom(SOLVE);
    play(CROSS, CORNER_R, CORNER_B, CORNER_L);

    assertFalse(detector.isComplete());
    assertTrue(detector.matchesMethod()); // three corners in, with the second layer untouched
    assertNull(detector.getStepTimestampMs(detector.stepCount() - 1)); // the step it stopped in
  }

  @Test
  public void doesNotMatchOnACrossAlone() {
    startFrom(SOLVE);
    play(CROSS);

    assertNotNull(detector.getStepTimestampMs(0));
    assertFalse(detector.matchesMethod()); // every method builds a cross eventually
  }

  @Test
  public void findsTheCrossOnWhicheverFaceItWasBuiltOn() {
    // The same solve held a quarter turn towards the solver: the cross is built on the front face.
    apply(tilt(invert(join(SOLVE))));
    detector.reset(state(), timestampMs);
    for (String step : SOLVE) {
      play(tilt(step));
    }

    assertTrue(detector.isComplete());
    assertTrue(detector.matchesMethod());
    assertEquals(Face.F, detector.getCrossFace());
  }

  @Test
  public void datesACornerTheScrambleAlreadyGaveWithTheCross() {
    // A solve with only three corners to insert: the scramble handed the fourth, and it costs
    // nothing rather than being credited to a solver who never turned for it. Three in before the
    // second layer is also exactly what the method asks for, so the solve still matches.
    String[] solve = {
      CROSS,
      CORNER_B, CORNER_L, CORNER_F,
      EDGE_FR, EDGE_RB, EDGE_BL, EDGE_LF,
      ORIENT_EDGES, ORIENT_CORNERS, PERMUTE_CORNERS, PERMUTE_EDGES,
    };
    startFrom(solve);
    play(CROSS);

    Long cross = detector.getStepTimestampMs(0);
    assertEquals("layer1", detector.stepName(1));
    assertEquals(cross, detector.getSubStepTimestampMs(1, 0));
    assertEquals(cross, detector.getStepTimestampMs(1));

    play(CORNER_B, CORNER_L, CORNER_F, EDGE_FR, EDGE_RB, EDGE_BL, EDGE_LF,
        ORIENT_EDGES, ORIENT_CORNERS, PERMUTE_CORNERS, PERMUTE_EDGES);
    assertTrue(detector.isComplete());
    assertTrue(detector.matchesMethod());
  }

  /** The solved cube with each of these pieces moved two slots along, the way a half turn of their
   * layer would leave them: the edges make an H perm, the corners a diagonal swap. */
  private static String exchanged(int[][] pieces) {
    char[] facelets = Cubies.SOLVED.toCharArray();
    for (int slot = 0; slot < 4; slot++) {
      int[] from = pieces[(slot + 2) % 4];
      for (int facelet = 0; facelet < pieces[slot].length; facelet++) {
        facelets[pieces[slot][facelet]] = Cubies.SOLVED.charAt(from[facelet]);
      }
    }
    return new String(facelets);
  }

  /** Scramble the cube with the inverse of the solve, then arm the detector on the result. */
  private void startFrom(String... solve) {
    apply(invert(join(solve)));
    detector.reset(state(), timestampMs);
  }

  /** Play the moves into the detector, one quarter turn per state, 100ms apart. */
  private void play(String... moves) {
    for (String token : join(moves).trim().split("\\s+")) {
      Face face = Face.valueOf(token.substring(0, 1));
      boolean prime = token.endsWith("'");
      for (int i = 0; i < (token.endsWith("2") ? 2 : 1); i++) {
        cube.applyMove(face, prime);
        timestampMs += 100;
        detector.onState(state(), new CubeMove(face, prime, timestampMs));
      }
    }
  }

  private void apply(String moves) {
    for (String token : moves.trim().split("\\s+")) {
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
