package com.cube.nanotimer.cube;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.Face;
import com.cube.nanotimer.smartcube.model.OrientationHistory;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class RepRotationsTest {

  private static final long SHOWN = 1000;

  @Test
  public void aCubeHeldStillWasNotRotated() {
    assertFalse(rotated(Arrays.asList(turn(Face.R, 1500), turn(Face.U, 1700), turn(Face.R, 1900)),
        history(0, 0)));
  }

  @Test
  public void aQuarterTurnBetweenTwoTurnsIsARotation() {
    assertTrue(rotated(Arrays.asList(turn(Face.R, 1500), turn(Face.R, 2500)), history(2000, 90)));
  }

  @Test
  public void aRotationBeforeTheFirstTurnCounts() {
    assertTrue(rotated(Collections.singletonList(turn(Face.R, 2500)), history(1500, -90)));
  }

  /** The core turns a quarter inside a top-layer turn: a d, which is a turn, not a rotation. */
  @Test
  public void aQuarterInsideATopTurnIsAD() {
    assertFalse(rotated(Arrays.asList(turn(Face.R, 1500), turn(Face.U, 2500), turn(Face.R, 2800)),
        history(2400, 90)));
  }

  /** The same quarter long before the top turn was the hands, then a turn of the top. */
  @Test
  public void aQuarterWellBeforeATopTurnIsARotation() {
    assertTrue(rotated(Arrays.asList(turn(Face.R, 1500), turn(Face.U, 3000)), history(2000, 90)));
  }

  @Test
  public void aSmallWobbleIsNotARotation() {
    assertFalse(rotated(Arrays.asList(turn(Face.R, 1500), turn(Face.R, 2500)), history(2000, 30)));
  }

  @Test
  public void noGyroSaysNothing() {
    assertFalse(RepRotations.rotated(Collections.singletonList(turn(Face.R, 1500)), SHOWN, 0,
        Collections.<OrientationHistory.Sample>emptyList(), 'U'));
  }

  private static boolean rotated(List<CubeMove> moves, List<OrientationHistory.Sample> samples) {
    return RepRotations.rotated(moves, SHOWN, 0, samples, 'U');
  }

  private static CubeMove turn(Face face, long atMs) {
    return new CubeMove(face, false, atMs, atMs);
  }

  /** Readings every 50 ms, level, turning {@code degrees} about up over the 100 ms from {@code atMs}. */
  private static List<OrientationHistory.Sample> history(long atMs, double degrees) {
    OrientationHistory history = new OrientationHistory();
    for (long t = 0; t <= 5000; t += 50) {
      double share = Math.max(0, Math.min(1, (t - atMs) / 100.0));
      double half = Math.toRadians(degrees * share) / 2;
      history.onSample(new CubeOrientation(Math.cos(half), 0, 0, Math.sin(half)), t);
    }
    return history.between(0, 5000);
  }
}
