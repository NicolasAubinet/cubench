package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.model.CubeRotation;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;
import org.json.JSONObject;
import org.junit.Test;

/**
 * What the replay is handed for its gyro overlay, on a MoYu V11 report whose replay shook badly:
 * a quarter of its keyframes came in delivery batches, and every rotation turned the cube twice.
 */
public class ReplayGyroTest {

  private static final String SHAKY = "/reports/b56fe5cb-0547-4598-b5b1-752dabd22c64.json";
  private static final String ROUX = "/reports/a23413e6-8ae2-47bf-b46a-45674579c284.json";

  @Test
  public void aDeliveryBatchIsSpreadBackOverTheGapBeforeIt() throws Exception {
    String gyro = smartcube(SHAKY).getString("gyro");
    List<GyroTrackFormat.Keyframe> stored = GyroTrackFormat.parse(gyro).getKeyframes();
    List<GyroTrackFormat.Keyframe> poses = GyroTrackFormat.posesOf(gyro);
    assertEquals(stored.size(), poses.size());
    double before = jumped(stored);
    double after = jumped(poses);
    System.out.println("turning drawn in under 10 ms: " + Math.round(before) + " deg stored, "
        + Math.round(after) + " deg replayed");
    assertTrue("the report should carry batches at all: " + before, before > 500);
    assertTrue("batches should no longer jump: " + after, after < before / 20);
    assertEquals(stored.get(stored.size() - 1).getOffsetMs(),
        poses.get(poses.size() - 1).getOffsetMs());
  }

  @Test
  public void everyTokenLeavesTheFrameTheReconstructionRecorded() throws Exception {
    for (String report : new String[] {SHAKY, ROUX}) {
      String moves = smartcube(report).getString("moves");
      List<SolveSolution.FrameAt> frames = SolveSolution.framesOf(moves);
      List<SolveSolution.ReplayToken> tokens =
          SolveSolution.replayTokens(SolveSolution.timedSolution(moves), frames);
      CubeRotation before = CubeRotation.byNotation("");
      int frameChanges = 0;
      for (SolveSolution.ReplayToken token : tokens) {
        CubeRotation turn = SolveMovesFormat.isRotation(token.getNotation())
            ? CubeRotation.byNotation(token.getNotation()) : null;
        if (turn != null) {
          // Each token, a composite's halves included, turns the frame the way the player turns it.
          assertSame(report + " " + token.getNotation() + "@" + token.getOffsetMs(),
              before.then(turn), token.getFrame());
        }
        if (token.getHalfway() != null) {
          assertSame(token.getHalfway().then(CubeRotation.byNotation(
              token.getNotation().replace("2", ""))), token.getFrame());
        }
        if (token.getFrame() != before) {
          frameChanges++;
        }
        before = token.getFrame();
      }
      assertSame(frames.get(frames.size() - 1).getFrame(), before);
      assertTrue(report + " should reorient", frameChanges > 5);
    }
  }

  /** How far the cube turns between keyframes less than 10 ms apart, which playback shows as a jump. */
  private static double jumped(List<GyroTrackFormat.Keyframe> keyframes) {
    double degrees = 0;
    for (int i = 1; i < keyframes.size(); i++) {
      if (keyframes.get(i).getOffsetMs() - keyframes.get(i - 1).getOffsetMs() < 10) {
        degrees += keyframes.get(i).getOrientation()
            .angleToDegrees(keyframes.get(i - 1).getOrientation());
      }
    }
    return degrees;
  }

  private static JSONObject smartcube(String report) throws Exception {
    try (InputStream in = ReplayGyroTest.class.getResourceAsStream(report);
        Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
      return new JSONObject(scanner.next()).getJSONObject("smartcube");
    }
  }
}
