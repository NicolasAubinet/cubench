package com.cube.nanotimer.smartcube.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/**
 * The readings of the 2026-07-25 calibration: the cube held still with white up, then white down,
 * then green up. Measured rather than derived — the axis conventions here have been got backwards
 * twice, and only hardware settles them.
 */
public class CubeRotationGravityTest {

  private static final CubeOrientation WHITE_UP =
      new CubeOrientation(+0.9978, -0.0288, +0.0588, +0.0059);
  private static final CubeOrientation WHITE_DOWN =
      new CubeOrientation(-0.0033, +0.0313, +0.9994, -0.0149);
  private static final CubeOrientation GREEN_UP =
      new CubeOrientation(-0.0056, +0.0080, +0.6955, -0.7184);

  @Test
  public void readsTheUpFaceStraightOffASample() {
    // The cube's own letters, not colours: white is the face it calls U, green the one it calls F.
    assertEquals('U', CubeRotation.upFace(WHITE_UP));
    assertEquals('D', CubeRotation.upFace(WHITE_DOWN));
    assertEquals('F', CubeRotation.upFace(GREEN_UP));
  }

  /** A grip is a cube orientation plus however far off square it is held; gravity removes the tilt. */
  @Test
  public void takesTheTiltOutOfAGripAndLeavesTheYawAlone() {
    CubeOrientation square = new CubeOrientation(1, 0, 0, 0);
    CubeOrientation tilted = tiltedBy(square, 25); // held 25° off, same face still on top

    assertEquals('U', CubeRotation.upFace(tilted));
    assertTrue(CubeRotation.upright(tilted).angleToDegrees(square) < 0.01);
    assertTrue(CubeRotation.upright(square).angleToDegrees(square) < 0.01); // already square
  }

  /**
   * Why it matters: two grips tilted opposite ways put both their tilts into the rotation between
   * them, which is how a plain y came to sit 35° from any orientation and be refused as unclean.
   */
  @Test
  public void collapsesTwoTiltsThatWouldOtherwiseHideAQuarterTurn() {
    CubeOrientation square = new CubeOrientation(1, 0, 0, 0);
    CubeOrientation from = tiltedBy(square, 25);
    CubeOrientation to = tiltedBy(yawed(square), -25);

    assertNull(CubeRotation.nearest(from.deltaTo(to), 20));
    CubeRotation cleaned =
        CubeRotation.nearest(CubeRotation.upright(from).deltaTo(CubeRotation.upright(to)), 20);
    assertEquals("y", cleaned.getNotation());
  }

  /** A turn about the cube's R axis, which leaves white on top while tipping the cube off square. */
  private static CubeOrientation tiltedBy(CubeOrientation grip, double degrees) {
    double half = Math.toRadians(degrees) / 2;
    // cube (w, s, 0, 0) sits in the gyro's axes (R=+X, U=+Z, F=−Y) as (w, s, 0, 0)
    return grip.multiply(new CubeOrientation(Math.cos(half), Math.sin(half), 0, 0));
  }

  /** A quarter turn about the cube's U axis: the regrip the tilts were hiding. */
  private static CubeOrientation yawed(CubeOrientation grip) {
    double half = Math.toRadians(-90) / 2;
    return grip.multiply(new CubeOrientation(Math.cos(half), 0, 0, Math.sin(half)));
  }

  @Test
  public void offersTheFourGripsGravityCannotTellApart() {
    List<CubeRotation> candidates = CubeRotation.withFaceUp('D');

    assertEquals(4, candidates.size()); // the four yaws of a flipped cube
    for (CubeRotation candidate : candidates) {
      assertEquals('U', candidate.mapFace('D'));
    }
  }

  @Test
  public void everyFaceCanBeHeldUpFourWays() {
    for (char face : new char[] {'U', 'D', 'L', 'R', 'F', 'B'}) {
      assertEquals(4, CubeRotation.withFaceUp(face).size());
    }
  }

  @Test
  public void picksTheCandidateNearestAMeasuredGrip() {
    // Gravity narrows to four; the scramble grip decides the yaw among them.
    List<CubeRotation> candidates = CubeRotation.withFaceUp('D');
    CubeRotation flipped = CubeRotation.byNotation("x2");

    assertEquals("x2", flipped.nearestOf(candidates).getNotation());
    assertTrue(candidates.contains(CubeRotation.byNotation("z2")));
  }
}
