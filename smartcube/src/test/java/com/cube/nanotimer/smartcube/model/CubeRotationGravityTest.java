package com.cube.nanotimer.smartcube.model;

import static org.junit.Assert.assertEquals;
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
