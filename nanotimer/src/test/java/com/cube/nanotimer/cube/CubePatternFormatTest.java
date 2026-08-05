package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.Face;

import org.junit.Test;

/**
 * Pins the conversion to what cubing.js itself answers.
 *
 * <p>Every expectation below was printed by the bundled library (0.63.3) for the same scramble, with
 * {@code kpuzzle.defaultPattern().applyAlg(alg).patternData}. A state from solved makes a pattern
 * and a transformation numerically identical, so those arrays are what the player has to be handed.
 * If a cubing.js bump ever renumbers an orbit, these fail rather than the cube quietly drawing a
 * different state from the one in the hand.
 */
public class CubePatternFormatTest {

  @Test
  public void solvedIsTheIdentity() {
    assertEquals(
        pattern("0,1,2,3,4,5,6,7", "0,0,0,0,0,0,0,0",
            "0,1,2,3,4,5,6,7,8,9,10,11", "0,0,0,0,0,0,0,0,0,0,0,0"),
        CubePatternFormat.format(CubieCube.SOLVED_FACELET));
  }

  @Test
  public void sexyMove() {
    assertEquals(
        pattern("4,2,1,3,0,5,6,7", "2,2,0,0,2,0,0,0",
            "0,8,1,3,4,5,6,7,2,9,10,11", "0,0,0,0,0,0,0,0,0,0,0,0"),
        format("R U R' U'"));
  }

  @Test
  public void turnsOnEveryFace() {
    assertEquals(
        pattern("2,6,3,0,5,1,7,4", "0,2,1,1,1,2,1,1",
            "2,3,1,8,6,11,4,10,5,9,7,0", "0,0,1,0,0,1,0,0,0,0,1,1"),
        format("R U2 D' B D'"));
  }

  @Test
  public void fullScramble() {
    assertEquals(
        pattern("2,0,7,1,4,3,5,6", "2,1,0,0,1,0,2,0",
            "10,7,5,4,11,9,3,0,2,6,1,8", "1,1,0,0,1,0,1,1,0,0,1,0"),
        format("D2 F2 U' B2 U' R2 D R2 U' F2 D' L' D2 B' R U2 L' F' D2 R2"));
  }

  @Test
  public void secondFullScramble() {
    assertEquals(
        pattern("4,0,3,7,5,6,1,2", "2,0,0,0,1,1,1,1",
            "4,8,5,9,11,7,1,0,10,3,2,6", "0,1,1,1,1,0,1,1,1,1,1,1"),
        format("F R U' L2 B2 D F' B U2 R2"));
  }

  @Test
  public void nonsenseDrawsNothing() {
    assertNull(CubePatternFormat.format(null));
    assertNull(CubePatternFormat.format("UUU"));
    assertNull(CubePatternFormat.format(CubieCube.SOLVED_FACELET.replace('U', 'R')));
  }

  private static String format(String scramble) {
    CubieCube cube = new CubieCube();
    cube.fromFacelet(CubieCube.SOLVED_FACELET);
    for (String move : scramble.split(" ")) {
      Face face = Face.valueOf(move.substring(0, 1));
      int turns = move.endsWith("2") ? 2 : 1;
      for (int i = 0; i < turns; i++) {
        cube.applyMove(face, move.endsWith("'"));
      }
    }
    return CubePatternFormat.format(cube.toFaceCube());
  }

  private static String pattern(String cornerPieces, String cornerOrientations,
      String edgePieces, String edgeOrientations) {
    return "{\"CORNERS\":{\"permutation\":[" + cornerPieces + "],\"orientationDelta\":["
        + cornerOrientations + "]},\"EDGES\":{\"permutation\":[" + edgePieces
        + "],\"orientationDelta\":[" + edgeOrientations + "]},"
        + "\"CENTERS\":{\"permutation\":[0,1,2,3,4,5],\"orientationDelta\":[0,0,0,0,0,0]}}";
  }
}
