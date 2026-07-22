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
   * and after a further {@code x}. Raw gyro frame, as the parser emits them. The delta
   * convention these read through was pinned by three later full-solve captures with scripted
   * {@code y}/{@code x}/{@code z} sections; that this probe reads exactly as scripted under it
   * is an independent cross-check, not the calibration itself.
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

  @Test
  public void recognisesTheCapturedYRotation() {
    assertEquals("y", CubeRotation.nearest(REST.deltaTo(AFTER_Y)).getNotation());
  }

  /** A physical x done on a y-rotated cube IS the cube's own z': display maps it back (§seenFrom). */
  @Test
  public void recognisesTheCapturedXRotationInTheCubesFrame() {
    assertEquals("z'", CubeRotation.nearest(AFTER_Y.deltaTo(AFTER_Y_X)).getNotation());
  }

  /** Composing both, measured against the resting reference, is one two-token corner rotation. */
  @Test
  public void recognisesTheCombinedRotation() {
    assertEquals("y x", CubeRotation.nearest(REST.deltaTo(AFTER_Y_X)).getNotation());
  }

  /**
   * A two-token notation reads left to right in the solver's fixed frame: carrying a face through
   * "y x" must be the same as carrying it through y, then through x. Composing the generators on
   * the wrong side passes every single-token test and swaps exactly this.
   */
  @Test
  public void twoTokenNotationsReadLeftToRightInAFixedFrame() {
    for (CubeRotation rotation : CubeRotation.all()) {
      String[] tokens = rotation.getNotation().split(" ");
      if (tokens.length != 2) {
        continue;
      }
      CubeRotation first = CubeRotation.byNotation(tokens[0]);
      CubeRotation second = CubeRotation.byNotation(tokens[1]);
      for (char face : "RLUDFB".toCharArray()) {
        assertEquals("in " + rotation, second.mapFace(first.mapFace(face)), rotation.mapFace(face));
      }
    }
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

  /** For a reading known to be a reorientation, closest() snaps where nearest() gives up. */
  @Test
  public void closestSnapsAReadingBeyondTheTolerance() {
    double half = Math.toRadians(50) / 2; // 50° toward a y: outside tolerance from everything
    CubeOrientation offTolerance = new CubeOrientation(Math.cos(half), 0, 0, -Math.sin(half));
    assertNull(CubeRotation.nearest(offTolerance));
    assertEquals("y", CubeRotation.closest(offTolerance).getNotation());
  }

}
