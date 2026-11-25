package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.gui.widget.dialog.SolveReplayDialog;
import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import org.json.JSONObject;
import org.junit.Test;

/**
 * What the replay is handed for its gyro overlay, on a MoYu V11 report whose replay shook badly:
 * a quarter of its keyframes came in delivery batches, and the track drawn as kept trembled.
 */
public class ReplayGyroTest {

  private static final String SHAKY = "/reports/b56fe5cb-0547-4598-b5b1-752dabd22c64.json";
  private static final String ROUX = "/reports/a23413e6-8ae2-47bf-b46a-45674579c284.json";

  @Test
  public void aDeliveryBatchIsSpreadBackOverTheGapBeforeIt() throws Exception {
    String gyro = smartcube(SHAKY).getString("gyro");
    List<GyroTrackFormat.Keyframe> stored = GyroTrackFormat.parse(gyro).getKeyframes();
    List<GyroTrackFormat.Keyframe> poses = GyroTrackFormat.unbatched(stored);
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
  public void theReplayedTrackHasNoTremorAndStaysWithTheCube() throws Exception {
    for (String report : new String[] {SHAKY, ROUX}) {
      String gyro = smartcube(report).getString("gyro");
      List<GyroTrackFormat.Keyframe> recorded =
          GyroTrackFormat.unbatched(GyroTrackFormat.posesOf(gyro));
      List<GyroTrackFormat.Keyframe> replayed = GyroTrackFormat.replayPosesOf(gyro, null);
      double shakeBefore = 0;
      double shakeAfter = 0;
      List<Double> off = new ArrayList<Double>();
      CubeOrientation[] r = new CubeOrientation[3];
      CubeOrientation[] p = new CubeOrientation[3];
      long end = recorded.get(recorded.size() - 1).getOffsetMs();
      for (long t = 0; t <= end; t += 10) {
        r[0] = r[1];
        r[1] = r[2];
        r[2] = at(recorded, t);
        p[0] = p[1];
        p[1] = p[2];
        p[2] = at(replayed, t);
        if (r[0] != null) {
          shakeBefore += turned(r);
          shakeAfter += turned(p);
        }
        off.add(r[2].angleToDegrees(p[2]));
      }
      Collections.sort(off);
      double median = off.get(off.size() / 2);
      double p90 = off.get(off.size() * 9 / 10);
      System.out.printf("%s: direction changes %.0f -> %.0f deg, off the cube median %.1f p90 %.1f%n",
          report, shakeBefore, shakeAfter, median, p90);
      assertTrue(report + " should shake far less", shakeAfter < shakeBefore / 3);
      assertTrue(report + " should stay with the cube: " + median, median < 4);
      assertTrue(report + " should stay with the cube: " + p90, p90 < 10);
    }
  }

  @Test
  public void aTrackWithNoReferenceOpensInTheGripTheReconstructionRead() throws Exception {
    JSONObject smartcube = smartcube(SHAKY);
    String gyro = smartcube.getString("gyro");
    byte[] bytes = java.util.Base64.getDecoder().decode(gyro);
    java.util.Arrays.fill(bytes, 1, 9, (byte) 0); // the reference, as an unanchored session stores it
    String unanchored = java.util.Base64.getEncoder().withoutPadding().encodeToString(bytes);
    String moves = smartcube.getString("moves");
    CubeRotation grip = SolveReplayDialog.openingGrip(SolveSolution.timedSolution(moves),
        SolveSolution.framesOf(moves));
    assertEquals("y2 x'", grip.getNotation());

    List<GyroTrackFormat.Keyframe> anchored = GyroTrackFormat.replayPosesOf(gyro, null);
    List<GyroTrackFormat.Keyframe> held = GyroTrackFormat.replayPosesOf(unanchored, grip);
    List<GyroTrackFormat.Keyframe> bare = GyroTrackFormat.replayPosesOf(unanchored, null);
    double opening = held.get(0).getOrientation().angleToDegrees(grip.quaternion());
    assertTrue("should open in the grip: " + opening, opening < 10); // smoothed with what follows
    double heldOff = medianOff(held, anchored);
    double bareOff = medianOff(bare, anchored);
    System.out.printf("unanchored track: median %.0f deg off with the grip, %.0f without%n",
        heldOff, bareOff);
    // Not zero: the solver's real pick-up sat a little off square, and the reference knew it.
    assertTrue("should open where the anchored track does: " + heldOff, heldOff < 25);
    assertTrue("without the grip the whole replay is turned: " + bareOff, bareOff > 90);
  }

  @Test
  public void theRawMovesPlayedWithTheGyroSolveTheScramble() throws Exception {
    for (String report : new String[] {SHAKY, ROUX}) {
      JSONObject solve = solve(report);
      StringBuilder raw = new StringBuilder();
      for (SolveMovesFormat.Move move
          : SolveReplayDialog.rawMoves(smartcube(report).getString("moves"))) {
        assertTrue(move.getNotation(), !SolveMovesFormat.isRotation(move.getNotation()));
        raw.append(move.getNotation()).append(' ');
      }
      assertTrue(report, DisplayedSolutionReplay.solves(solve.getString("scramble").trim(),
          raw.toString().trim()));
    }
  }

  private static double medianOff(List<GyroTrackFormat.Keyframe> a,
      List<GyroTrackFormat.Keyframe> b) {
    List<Double> off = new ArrayList<Double>();
    for (int i = 0; i < a.size(); i++) {
      off.add(a.get(i).getOrientation().angleToDegrees(b.get(i).getOrientation()));
    }
    Collections.sort(off);
    return off.get(off.size() / 2);
  }

  /** How much the path bends at its middle point: direction changes, which read as shaking. */
  private static double turned(CubeOrientation[] q) {
    return q[0].angleToDegrees(q[1]) + q[1].angleToDegrees(q[2]) - q[0].angleToDegrees(q[2]);
  }

  /** The pose drawn at {@code t}, interpolated as the page does. */
  private static CubeOrientation at(List<GyroTrackFormat.Keyframe> k, long t) {
    int i = 0;
    while (i + 1 < k.size() && k.get(i + 1).getOffsetMs() <= t) {
      i++;
    }
    CubeOrientation a = k.get(i).getOrientation();
    if (i + 1 >= k.size()) {
      return a;
    }
    CubeOrientation b = k.get(i + 1).getOrientation();
    double span = k.get(i + 1).getOffsetMs() - k.get(i).getOffsetMs();
    double f = span <= 0 ? 1 : (t - k.get(i).getOffsetMs()) / span;
    double s = a.dot(b) < 0 ? -f : f;
    double w = (1 - f) * a.getW() + s * b.getW();
    double x = (1 - f) * a.getX() + s * b.getX();
    double y = (1 - f) * a.getY() + s * b.getY();
    double z = (1 - f) * a.getZ() + s * b.getZ();
    double n = Math.sqrt(w * w + x * x + y * y + z * z);
    return new CubeOrientation(w / n, x / n, y / n, z / n);
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
    return json(report).getJSONObject("smartcube");
  }

  private static JSONObject solve(String report) throws Exception {
    return json(report).getJSONObject("solve");
  }

  private static JSONObject json(String report) throws Exception {
    try (InputStream in = ReplayGyroTest.class.getResourceAsStream(report);
        Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
      return new JSONObject(scanner.next());
    }
  }
}
