package com.cube.nanotimer.smartcube.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class CubeRotationTest {

  /**
   * Orientations captured off a real V10 (probe 4.11a): the cube at rest, after a {@code y},
   * and after a further {@code x}. Raw gyro frame, as the parser emits them.
   */
  private static final CubeOrientation REST = new CubeOrientation(0.99661, -0.08238, -0.00879, 0.00311);
  private static final CubeOrientation AFTER_Y = new CubeOrientation(0.73423, -0.00566, -0.00588, -0.67894);
  private static final CubeOrientation AFTER_Y_X = new CubeOrientation(0.48876, -0.51064, -0.51134, -0.48876);

  @Test
  public void thereAreExactlyTwentyFourOrientations() {
    assertEquals(24, CubeRotation.all().size());
  }

  @Test
  public void everyOrientationIsDistinct() {
    Set<String> notations = new HashSet<>();
    for (CubeRotation rotation : CubeRotation.all()) {
      assertTrue("duplicate: " + rotation, notations.add(rotation.getNotation()));
    }
  }

  @Test
  public void notationsAreMinimal() {
    for (CubeRotation rotation : CubeRotation.all()) {
      int tokens = rotation.isIdentity() ? 0 : rotation.getNotation().split(" ").length;
      assertTrue("not minimal: " + rotation, tokens <= 2); // 24 orientations are all within 2
    }
  }

  @Test
  public void identityIsReachedByNoRotation() {
    CubeRotation none = CubeRotation.nearest(new CubeOrientation(1, 0, 0, 0));
    assertNotNull(none);
    assertTrue(none.isIdentity());
  }

  /** The capture's first transition was a physical y; the second an x. */
  @Test
  public void recognisesTheCapturedYRotation() {
    assertEquals("y", CubeRotation.nearest(REST.deltaTo(AFTER_Y)).getNotation());
  }

  @Test
  public void recognisesTheCapturedXRotation() {
    assertEquals("x", CubeRotation.nearest(AFTER_Y.deltaTo(AFTER_Y_X)).getNotation());
  }

  /** Composing both, measured against the resting reference, is the two-token sequence. */
  @Test
  public void recognisesTheCombinedRotation() {
    assertEquals("y x", CubeRotation.nearest(REST.deltaTo(AFTER_Y_X)).getNotation());
  }

  /** Face turns leave orientation alone: the capture's R turns drifted under 6 degrees. */
  @Test
  public void smallDriftIsNotARotation() {
    CubeRotation none = CubeRotation.nearest(REST.deltaTo(REST));
    assertNotNull(none);
    assertTrue(none.isIdentity());
  }

  /** A reading taken mid-turn matches nothing rather than snapping to a wrong answer. */
  @Test
  public void halfwayThroughATurnIsUnknown() {
    double half = Math.toRadians(45) / 2; // 45 degrees: squarely between two orientations
    CubeOrientation midTurn = new CubeOrientation(Math.cos(half), 0, 0, -Math.sin(half));
    assertNull(CubeRotation.nearest(midTurn));
  }
}
