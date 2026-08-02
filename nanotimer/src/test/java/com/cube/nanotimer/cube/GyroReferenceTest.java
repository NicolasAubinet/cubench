package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.model.CubeOrientation;
import org.junit.Test;

/**
 * The reference itself, which used to be {@link RotationTracker}'s and is now shared with the
 * stored gyro track. These assertions moved here with it.
 */
public class GyroReferenceTest {

  private static final CubeOrientation REST = new CubeOrientation(1, 0, 0, 0);

  @Test
  public void withoutAReferenceOrAReadingThereIsNoFrameToRead() {
    assertNull(new GyroReference().frameOf(REST)); // never anchored
    assertNull(anchoredAt(REST).frameOf(null)); // anchored, but the cube has no gyro
  }

  /** The first reading wins: the one grip whose label can be asked of the solver. */
  @Test
  public void theFirstReadingIsTheReferenceAndLaterOnesDoNotMoveIt() {
    GyroReference reference = anchoredAt(REST);
    reference.anchor(aboutU(90));
    assertEquals("y", reference.frameOf(aboutU(90)).getNotation());
  }

  @Test
  public void nothingIsAnchoredByAReadingThatIsNotThere() {
    GyroReference reference = new GyroReference();
    reference.anchor(null);
    assertFalse(reference.isSet());
    assertNull(reference.get());
  }

  /** A cube set down mid-scramble may be picked back up any way up. */
  @Test
  public void restartingForgetsIt() {
    GyroReference reference = anchoredAt(REST);
    assertTrue(reference.isSet());
    reference.restart();
    assertFalse(reference.isSet());
    assertNull(reference.frameOf(aboutU(90)));
  }

  /** What is handed to the stored track: the reference itself, uprighted, not the raw reading. */
  @Test
  public void theStoredReferenceIsTheUprightedGrip() {
    CubeOrientation tilted = new CubeOrientation(0.9848, 0.1736, 0, 0); // 20° off square
    GyroReference reference = anchoredAt(tilted);
    assertTrue(reference.get().angleToDegrees(tilted) > 1); // the tilt was taken out
    assertEquals("", reference.frameOf(tilted).getNotation()); // and the grip is still its own frame
  }

  private static GyroReference anchoredAt(CubeOrientation grip) {
    GyroReference reference = new GyroReference();
    reference.anchor(grip);
    return reference;
  }

  private static CubeOrientation aboutU(double degrees) {
    // The gyro's zero multiplies on the left and deltas on the right, as RotationTrackerTest has it.
    double half = Math.toRadians(-degrees) / 2;
    return REST.multiply(new CubeOrientation(Math.cos(half), 0, 0, Math.sin(half)));
  }
}
