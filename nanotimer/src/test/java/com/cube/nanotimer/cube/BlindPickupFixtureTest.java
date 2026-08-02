package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.model.CubeRotation;
import com.cube.nanotimer.smartcube.model.OrientationHistory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/**
 * The pick-up grip against a real blind memorisation: solve 203, a 1:01.39 the solver turned with
 * red in front throughout, so the frame is {@code y} and they can say so.
 *
 * <p>The stored gyro track is what makes this testable at all. The solve that first showed the bug
 * was recorded before the track existed and can never be replayed — it named its buffers
 * {@code UR}/{@code UBR} through a grip nobody held, and all that survives of it is the wrong
 * answer.
 */
public class BlindPickupFixtureTest {

  /** Solve 203's first face turn. Everything before it is memorisation, with nothing turned. */
  private static final long FIRST_MOVE_MS = 25789;

  @Test
  public void theMemorisationOfASolveIsReadAsTheGripItWasHeldIn() {
    GyroTrackFormat.GyroTrack track = GyroTrackFormat.parse(fixture("blind203.txt"));
    GyroReference reference = new GyroReference();
    reference.anchor(track.getReference());

    OrientationHistory memo = new OrientationHistory();
    for (GyroTrackFormat.Keyframe keyframe : track.getKeyframes()) {
      if (keyframe.getOffsetMs() <= FIRST_MOVE_MS) {
        memo.onSample(keyframe.getOrientation(), keyframe.getOffsetMs());
      }
    }

    CubeRotation grip = reference.frameOver(memo.between(0, FIRST_MOVE_MS), FIRST_MOVE_MS);
    assertNotNull(grip);
    assertEquals("y", grip.getNotation());
  }

  /**
   * And it is a plurality, not a majority — the solver peeks at the cube while memorising, and more
   * than half of this memorisation is spent in some frame other than the one they solve in. Eleven
   * other frames appear. What carries it is that none of them is held anywhere near as long.
   */
  @Test
  public void theGripIsHeldLongerThanAnyOneWayOfPeekingAtIt() {
    GyroTrackFormat.GyroTrack track = GyroTrackFormat.parse(fixture("blind203.txt"));
    GyroReference reference = new GyroReference();
    reference.anchor(track.getReference());

    Map<String, Long> held = new HashMap<String, Long>();
    for (int i = 0; i < track.getKeyframes().size(); i++) {
      GyroTrackFormat.Keyframe keyframe = track.getKeyframes().get(i);
      if (keyframe.getOffsetMs() > FIRST_MOVE_MS) {
        break;
      }
      long ends = i + 1 < track.getKeyframes().size()
          ? Math.min(track.getKeyframes().get(i + 1).getOffsetMs(), FIRST_MOVE_MS)
          : FIRST_MOVE_MS;
      String frame = reference.frameOf(keyframe.getOrientation()).getNotation();
      Long seen = held.get(frame);
      held.put(frame, (seen == null ? 0 : seen) + ends - keyframe.getOffsetMs());
    }

    long grip = held.get("y");
    long runnerUp = 0;
    for (Map.Entry<String, Long> entry : held.entrySet()) {
      if (!entry.getKey().equals("y")) {
        runnerUp = Math.max(runnerUp, entry.getValue());
      }
    }
    assertTrue("eleven other frames were seen, got " + held.size(), held.size() > 6);
    assertTrue("the grip is a plurality, not a majority: " + grip, grip * 2 < FIRST_MOVE_MS);
    assertTrue("the grip beats the runner-up several times over: " + grip + " vs " + runnerUp,
        grip > runnerUp * 3);
  }

  private static String fixture(String name) {
    try (InputStream in = BlindPickupFixtureTest.class.getResourceAsStream("/gyro/" + name)) {
      assertNotNull("missing fixture " + name, in);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      for (int read; (read = in.read(buffer)) > 0; ) {
        out.write(buffer, 0, read);
      }
      return new String(out.toByteArray(), StandardCharsets.UTF_8).trim();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
