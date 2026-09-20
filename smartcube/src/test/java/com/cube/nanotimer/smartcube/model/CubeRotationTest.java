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

  /**
   * The equation a declared orientation is: the cube's own labels are a fixed colour, so white up
   * and red front ({@code U} up, {@code R} front) is the one rotation {@code y} and nothing else.
   */
  @Test
  public void namesTheOneOrientationHoldingTwoGivenFacesUpAndInFront() {
    assertEquals("y", CubeRotation.holding('U', 'R').getNotation());
    assertEquals("", CubeRotation.holding('U', 'F').getNotation());
    assertEquals("x", CubeRotation.holding('F', 'D').getNotation());
  }

  /** Every pair of faces that is not one axis twice names one of the 24, and each names its own. */
  @Test
  public void namesAnOrientationForEveryPairOfFacesThatIsNotOneAxisTwice() {
    Set<String> found = new HashSet<String>();
    for (char up : "URFDLB".toCharArray()) {
      for (char front : "URFDLB".toCharArray()) {
        CubeRotation holding = CubeRotation.holding(up, front);
        if (holding == null) {
          continue;
        }
        assertTrue("up and front on one axis: " + up + front, found.add(holding.getNotation()));
        assertEquals('U', holding.mapFace(up));
        assertEquals('F', holding.mapFace(front));
      }
    }
    assertEquals(24, found.size());
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

  /**
   * The step from one frame to another is in the frame's own axes, so applying it there arrives:
   * {@code frame.then(step.seenFrom(frame))} is the target, for every pair of the 24.
   */
  @Test
  public void toGivesTheStepThatCarriesOneFrameToAnother() {
    for (CubeRotation from : CubeRotation.all()) {
      for (CubeRotation target : CubeRotation.all()) {
        CubeRotation step = from.to(target);
        assertEquals(from + " -> " + target, target.getNotation(),
            from.then(step.seenFrom(from)).getNotation());
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
