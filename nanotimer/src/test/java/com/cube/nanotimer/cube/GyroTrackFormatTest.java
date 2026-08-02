package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.OrientationHistory;
import java.util.List;
import org.junit.Test;

public class GyroTrackFormatTest {

  private static final CubeOrientation REST = new CubeOrientation(1, 0, 0, 0);

  @Test
  public void aTrackRoundTripsThroughItsStoredForm() {
    OrientationHistory history = new OrientationHistory();
    history.onSample(REST, 1000);
    history.onSample(aboutU(90), 1200);
    String stored = GyroTrackFormat.format(history.between(0, 9999), REST, 1000);

    GyroTrackFormat.GyroTrack track = GyroTrackFormat.parse(stored);
    assertNotNull(track);
    assertEquals(2, track.getKeyframes().size());
    assertEquals(0, track.getKeyframes().get(0).getOffsetMs());
    assertEquals(200, track.getKeyframes().get(1).getOffsetMs());
    assertTrue(track.getKeyframes().get(1).getOrientation().angleToDegrees(aboutU(90)) < 0.1);
  }

  /** int16 per component: the quantisation has to sit far below the threshold that selects them. */
  @Test
  public void quantisingAQuaternionCostsAHundredthOfADegree() {
    OrientationHistory history = new OrientationHistory();
    CubeOrientation odd = aboutU(37.4);
    history.onSample(odd, 0);
    GyroTrackFormat.GyroTrack track =
        GyroTrackFormat.parse(GyroTrackFormat.format(history.between(0, 1), REST, 0));
    assertTrue(track.getKeyframes().get(0).getOrientation().angleToDegrees(odd) < 0.01);
  }

  /** The reference is stored beside the readings, never composed into them. */
  @Test
  public void theReferenceComesBackAsItWentIn() {
    OrientationHistory history = new OrientationHistory();
    history.onSample(REST, 0);
    CubeOrientation reference = aboutU(90);
    GyroTrackFormat.GyroTrack track =
        GyroTrackFormat.parse(GyroTrackFormat.format(history.between(0, 1), reference, 0));
    assertTrue(track.getReference().angleToDegrees(reference) < 0.01);
    assertTrue(track.getKeyframes().get(0).getOrientation().angleToDegrees(REST) < 0.01);
  }

  /** A cube with no gyro, and a solve on a scramble that was never followed to anchor a reference. */
  @Test
  public void noReadingsMeansNoTrackAndNoReferenceIsStillATrack() {
    OrientationHistory empty = new OrientationHistory();
    assertNull(GyroTrackFormat.format(empty.between(0, 1000), REST, 0));

    OrientationHistory history = new OrientationHistory();
    history.onSample(REST, 0);
    GyroTrackFormat.GyroTrack track =
        GyroTrackFormat.parse(GyroTrackFormat.format(history.between(0, 1), null, 0));
    assertNull(track.getReference());
    assertEquals(1, track.getKeyframes().size());
  }

  /**
   * A cube held still emits nothing between its ends. Measured on real solves this saves almost
   * nothing — a solve has hardly any still stretches — but a blind solve's memorisation is one.
   */
  @Test
  public void aStillCubeIsTwoKeyframes() {
    OrientationHistory history = new OrientationHistory();
    for (int at = 0; at <= 10_000; at += 50) {
      history.onSample(REST, at);
    }
    GyroTrackFormat.GyroTrack track =
        GyroTrackFormat.parse(GyroTrackFormat.format(history.between(0, 10_000), REST, 0));
    assertEquals(2, track.getKeyframes().size());
    assertEquals(10_000, track.getKeyframes().get(1).getOffsetMs());
  }

  /** A gap wider than a record's uint16 holds the pose across it rather than compressing time. */
  @Test
  public void aStillnessLongerThanAGapCanHoldKeepsTheTimeline() {
    OrientationHistory history = new OrientationHistory();
    history.onSample(REST, 0);
    history.onSample(REST, 200_000);
    GyroTrackFormat.GyroTrack track =
        GyroTrackFormat.parse(GyroTrackFormat.format(history.between(0, 200_000), REST, 0));
    List<GyroTrackFormat.Keyframe> keyframes = track.getKeyframes();
    assertEquals(200_000, keyframes.get(keyframes.size() - 1).getOffsetMs());
  }

  /** Turning past the threshold is kept; drifting inside it is not. */
  @Test
  public void onlyMotionPastTheThresholdIsKept() {
    assertEquals(2, keyframesTurning(0.5, 4)); // a 4° drift, in steps of noise: just the two ends
    assertEquals(5, keyframesTurning(20, 90)); // 0/20/40/60/80: every step is past the threshold
  }

  /** Nothing that is not a track may read as one, or the assertions above prove nothing. */
  @Test
  public void nothingElseParsesAsATrack() {
    assertNull(GyroTrackFormat.parse(null));
    assertNull(GyroTrackFormat.parse(""));
    assertNull(GyroTrackFormat.parse("R@0 U'@180")); // a move stream
    assertNull(GyroTrackFormat.parse("AAAA")); // well-formed base64, far too short
    assertNull(GyroTrackFormat.parse("not base64 at all !!"));

    OrientationHistory history = new OrientationHistory();
    history.onSample(REST, 0);
    String stored = GyroTrackFormat.format(history.between(0, 1), REST, 0);
    assertNull(GyroTrackFormat.parse(stored.substring(0, stored.length() - 3))); // truncated
    assertNull(GyroTrackFormat.parse("B" + stored.substring(1))); // another version's byte
  }

  private static int keyframesTurning(double stepDegrees, double totalDegrees) {
    OrientationHistory history = new OrientationHistory();
    for (int i = 0; i * stepDegrees <= totalDegrees; i++) {
      history.onSample(aboutU(i * stepDegrees), i * 50);
    }
    return GyroTrackFormat.parse(GyroTrackFormat.format(history.between(0, 99_999), REST, 0))
        .getKeyframes().size();
  }

  private static CubeOrientation aboutU(double degrees) {
    double half = Math.toRadians(degrees) / 2;
    return new CubeOrientation(Math.cos(half), 0, Math.sin(half), 0);
  }
}
