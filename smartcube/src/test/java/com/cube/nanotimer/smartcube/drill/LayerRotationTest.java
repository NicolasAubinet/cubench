package com.cube.nanotimer.smartcube.drill;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

/** Relabelling whole scrambles, including the wide turns and slices the Roux scrambles end on. */
public class LayerRotationTest {

  private static String[] turned(String face, String... scramble) {
    return LayerRotation.toFace(scramble, face);
  }

  @Test
  public void leavesAScrambleForWhiteAlone() {
    String[] scramble = {"R", "U'", "m2"};
    assertSame(scramble, LayerRotation.toFace(scramble, "U"));
  }

  @Test
  public void turnsFaceAndWideTurnsWithTheirFaces() {
    assertArrayEquals(new String[] {"D'", "L2", "F", "l'"}, turned("D", "U'", "R2", "F", "r'"));
    assertArrayEquals(new String[] {"F", "R", "B'", "r2"}, turned("F", "U", "R", "D'", "r2"));
  }

  /** M turns with L: z2 sends L to R, so it turns the other way; x' leaves it where it was. */
  @Test
  public void turnsASliceTheWayItsFaceWentRound() {
    assertArrayEquals(new String[] {"m'", "m", "m2"}, turned("D", "m", "m'", "m2"));
    assertArrayEquals(new String[] {"m", "m'"}, turned("F", "m", "m'"));
    assertArrayEquals(new String[] {"m", "m'"}, turned("B", "m", "m'"));
  }

  /** z sends L to U, which E turns against; z' sends it to D, which E turns with. */
  @Test
  public void movesASliceOntoTheAxisItsFaceLandedOn() {
    assertArrayEquals(new String[] {"e'", "e2"}, turned("R", "m", "m2"));
    assertArrayEquals(new String[] {"e", "E'"}, turned("L", "m", "M'"));
  }

  @Test
  public void keepsTheLineFormTheDrillDealsIn() {
    assertEquals("D R' D'", LayerRotation.toFace("U L' U'", "D"));
  }
}
