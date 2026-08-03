package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.OrientationHistory;
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

  /**
   * The latest reading wins. Anchoring is asked for at moments that mean it — a fresh connection, or
   * the solver saying how they are holding it — so the newest answer is the one to keep.
   */
  @Test
  public void anchoringAgainMovesTheReference() {
    GyroReference reference = anchoredAt(REST);
    reference.anchor(aboutU(90));
    assertEquals("", reference.frameOf(aboutU(90)).getNotation());
    assertEquals("y'", reference.frameOf(REST).getNotation());
  }

  /** A reading that is not there anchors nothing, and leaves whatever stood before it standing. */
  @Test
  public void nothingIsAnchoredByAReadingThatIsNotThere() {
    GyroReference reference = new GyroReference();
    reference.anchor(null);
    assertFalse(reference.isSet());
    assertNull(reference.get());

    GyroReference anchored = anchoredAt(REST);
    anchored.anchor(null);
    assertEquals("", anchored.frameOf(REST).getNotation());
  }

  /** The gyro zero it was measured against went with the connection. */
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

  /**
   * The grip a stretch agrees on, not the one its last reading gives. A blind solve was named
   * through the scramble's own grip because the single reading it was taken from landed while the
   * hands were settling; the seconds either side of it all said {@code y}.
   */
  @Test
  public void theFrameOfAStretchIsTheOneItWasHeldIn() {
    GyroReference reference = anchoredAt(REST);
    OrientationHistory readings = new OrientationHistory();
    readings.onSample(aboutU(90), 1000);
    readings.onSample(aboutU(90), 2000);
    readings.onSample(REST, 3000); // a peek, and then the first move comes before it is turned back

    assertEquals("y", reference.frameOver(readings.between(0, 3500), 3500).getNotation());
  }

  /**
   * Weighted by how long each reading stood, not by how many there were. Readings pile up while the
   * cube is being turned, so a burst during a peek must not outvote a grip held far longer.
   */
  @Test
  public void aBurstOfReadingsDoesNotOutvoteTheGripTheyTurnedBackTo() {
    GyroReference reference = anchoredAt(REST);
    OrientationHistory readings = new OrientationHistory();
    readings.onSample(aboutU(90), 0); // held ten seconds
    for (int i = 0; i < 8; i++) {
      readings.onSample(REST, 10_000 + i * 20); // then flicked away and back, eight readings in 160ms
    }
    readings.onSample(aboutU(90), 10_160);

    assertEquals("y", reference.frameOver(readings.between(0, 11_000), 11_000).getNotation());
  }

  @Test
  public void aStretchWithNothingReadableInItHasNoFrame() {
    OrientationHistory readings = new OrientationHistory();
    readings.onSample(REST, 1000);
    assertNull(new GyroReference().frameOver(readings.between(0, 2000), 2000)); // never anchored
    assertNull(anchoredAt(REST).frameOver(new OrientationHistory().between(0, 2000), 2000));
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
