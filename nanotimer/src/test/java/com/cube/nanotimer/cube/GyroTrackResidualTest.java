package com.cube.nanotimer.cube;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.OrientationHistory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * That the pose handed to the renderer is the <em>residual</em>, and not the whole physical
 * orientation over again.
 *
 * <p>The invariant is what makes this checkable at all: the replay already animates the solver's
 * rotation tokens, and those tokens were themselves resolved from this same gyro stream. So once
 * they are taken back out, what is left can only be the solver's tilts and peeks — tens of degrees,
 * never a quarter turn. Compose it the wrong way round and the residual carries the full rotation
 * <em>twice</em>, which this catches immediately.
 */
public class GyroTrackResidualTest {

  /**
   * Measured on the Roux capture: cancelling takes the median pose from 169° to 44°. The bound is
   * set where it is because 44° is NOT a tilt — it is as good as the reconstruction is. The gyro
   * frame is right for about 78% of moves, so roughly a fifth of keyframes sit a quarter turn from
   * where the reconstruction put the cube, and no composition here can recover those. What this
   * pins is that the cancelling happens at all.
   */
  private static final double CANCELLED_MEDIAN_LIMIT = 90;

  /** Without cancelling the pose carries the whole reorientation twice and is nearly always inverted. */
  private static final double UNCANCELLED_MEDIAN_FLOOR = 120;

  @Test
  public void theReconstructionsOwnFrameIsCancelledOutOfThePose() {
    // roux140, not cfop159: that capture's solver never reoriented the cube at all (0 rotation
    // tokens over 90 moves), so it cannot exercise the cancelling. Roux rotates constantly.
    Capture capture = new Capture("roux140.txt");
    List<SolveSolution.FrameAt> frames = SolveSolution.framesOf(capture.storedMoves);
    assertTrue("the capture should reorient at all", frames.size() > 0);

    double cancelled = medianAngle(GyroTrackFormat.posesOf(capture.track, frames));
    double uncancelled = medianAngle(GyroTrackFormat.posesOf(capture.track));
    System.out.println("gyro pose: median " + Math.round(cancelled) + " deg cancelled, "
        + Math.round(uncancelled) + " deg uncancelled, over " + frames.size() + " frame changes");

    assertTrue("the frame should be cancelled out: " + cancelled,
        cancelled < CANCELLED_MEDIAN_LIMIT);
    // Not vacuous: without it the same track is nearly a half turn out for most of the solve, which
    // on screen puts the last layer under the wrong face.
    assertTrue("the uncancelled pose should be far worse: " + uncancelled,
        uncancelled > UNCANCELLED_MEDIAN_FLOOR);
  }

  private static double medianAngle(List<GyroTrackFormat.Keyframe> poses) {
    CubeOrientation identity = new CubeOrientation(1, 0, 0, 0);
    List<Double> angles = new ArrayList<Double>();
    for (GyroTrackFormat.Keyframe pose : poses) {
      angles.add(pose.getOrientation().angleToDegrees(identity));
    }
    java.util.Collections.sort(angles);
    return angles.get(angles.size() / 2);
  }

  /** The stored stream and the gyro track a capture would have been recorded as. */
  private static final class Capture {

    private final String storedMoves;
    private final String track;

    private Capture(String fixture) {
      OrientationHistory history = new OrientationHistory();
      CubeOrientation reference = null;
      long lastMs = 0;
      for (String line : lines(fixture)) {
        String[] parts = line.split(" ");
        if (parts[0].equals("reference")) {
          reference = orientation(parts, 1);
        } else if (parts[0].equals("sample")) {
          long atMs = Long.parseLong(parts[1]);
          history.onSample(orientation(parts, 2), atMs);
          lastMs = Math.max(lastMs, atMs);
        }
      }
      // The same reference the tracker anchors on, uprighted the way GyroReference stores it.
      storedMoves = new RecordedGyroReplay(fixture).getStoredMoves();
      track = GyroTrackFormat.format(history.between(0, lastMs),
          com.cube.nanotimer.smartcube.model.CubeRotation.upright(reference), 0);
      assertNotNull(track);
    }
  }

  private static CubeOrientation orientation(String[] parts, int from) {
    return new CubeOrientation(Double.parseDouble(parts[from]), Double.parseDouble(parts[from + 1]),
        Double.parseDouble(parts[from + 2]), Double.parseDouble(parts[from + 3]));
  }

  private static List<String> lines(String fixture) {
    List<String> lines = new ArrayList<String>();
    try (InputStream in = GyroTrackResidualTest.class.getResourceAsStream("/gyro/" + fixture)) {
      assertNotNull("no such capture fixture: " + fixture, in);
      BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
      for (String line = reader.readLine(); line != null; line = reader.readLine()) {
        lines.add(line);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return lines;
  }
}
