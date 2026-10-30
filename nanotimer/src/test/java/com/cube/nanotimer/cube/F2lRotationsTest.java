package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.coach.PairRotation;
import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.vo.SolveStep;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class F2lRotationsTest {

  // Cross to 1000 ms, then three pairs of 2000 ms each.
  private static final List<SolveStep> STEPS = Arrays.asList(
      new SolveStep(0, "cross", 0, 1000, Collections.<SolveStep>emptyList()),
      new SolveStep(1, "f2l", 1200, 4800, Arrays.asList(
          part("pair_27"), part("pair_9"), part("pair_a16"))));

  // Pair 1 rotates between R' and R; pair 2 peeks, then turns a d; pair 3 rotates before its first
  // turn. The cross into pair 1 rotates too, which is not counted.
  private static final String MOVES = "D@500 R@1200 U@1400 R'@1600 R@2400 U'@2600 R'@2800 "
      + "F@3500 U@3700 F'@4000 y'@4199 U@4200 U'@4400 R@5600 U@5800 R'@6000";

  @Test
  public void countsRotationsFromTheGyroByWhereTheyFall() {
    List<PairRotation> pairs = F2lRotations.read(7, MOVES, STEPS,
        Arrays.asList(0L, 700L, 2000L, 3800L, 3900L, 4150L, 5300L),
        Arrays.asList(yaw(0), yaw(90), yaw(180), yaw(270), yaw(180), yaw(270), yaw(360)));

    assertEquals(3, pairs.size());
    PairRotation first = pairs.get(0);
    assertEquals(7, first.getSolveId());
    assertEquals("pair_27", first.getCaseCode());
    assertTrue(first.isBasic());
    assertEquals(1, first.getRotations());
    assertGaps(0, 0, 0, 0, first.getStart()); // the cross into the first pair is left out
    assertGaps(1, 800, 4, 800, first.getInside());

    PairRotation second = pairs.get(1);
    assertEquals(0, second.getRotations()); // a peek comes back, and a d turns the core itself
    assertGaps(0, 0, 1, 700, second.getStart());
    assertGaps(0, 0, 3, 900, second.getInside());

    PairRotation third = pairs.get(2);
    assertFalse(third.isBasic());
    assertEquals(1, third.getRotations());
    assertGaps(1, 1200, 0, 0, third.getStart());
    assertGaps(0, 0, 2, 400, third.getInside());
  }

  @Test
  public void leavesALostLookOutOfTheGaps() {
    List<PairRotation> pairs = F2lRotations.read(7,
        MOVES.replace("R@5600 U@5800 R'@6000", "R@8400 U@8600 R'@8800"),
        Arrays.asList(STEPS.get(0), new SolveStep(1, "f2l", 0, 8800, Arrays.asList(
            part("pair_27"), part("pair_9"), new SolveStep(2, "pair_a16", 3500, 1300,
                Collections.<SolveStep>emptyList())))),
        Arrays.asList(0L, 5300L), Arrays.asList(yaw(0), yaw(90)));

    assertEquals(1, pairs.get(2).getRotations());
    assertGaps(0, 0, 0, 0, pairs.get(2).getStart()); // 4000 ms: counted, but not as a gap
  }

  @Test
  public void readsNothingWithoutATrack() {
    assertTrue(F2lRotations.read(7, MOVES, STEPS, null).isEmpty());
  }

  @Test
  public void twistIsTakenAboutTheCubesOwnUpAxis() {
    // Held tipped 35° towards the solver, a quarter turn about its own up axis is still 90°, where
    // the twist about the world's vertical reads about 80°.
    CubeOrientation tipped = about(1, 0, 0, 35);
    CubeOrientation turned = tipped.multiply(about(0, 0, 1, 90));

    assertEquals(90, F2lRotations.upTwist(tipped, turned), 0.5);
    assertEquals(-90, F2lRotations.upTwist(turned, tipped), 0.5);
  }

  @Test
  public void anglesWrapAround() {
    assertEquals(20, F2lRotations.angleBetween(170, -170), 1e-9);
    assertEquals(0, F2lRotations.angleBetween(180, -180), 1e-9);
  }

  private static SolveStep part(String name) {
    return new SolveStep(0, name, 400, 1600, Collections.<SolveStep>emptyList());
  }

  private static CubeOrientation yaw(double degrees) {
    return about(0, 0, 1, degrees);
  }

  private static CubeOrientation about(double x, double y, double z, double degrees) {
    double half = Math.toRadians(degrees) / 2;
    return new CubeOrientation(Math.cos(half), x * Math.sin(half), y * Math.sin(half),
        z * Math.sin(half));
  }

  private static void assertGaps(int rotated, long rotatedMs, int plain, long plainMs,
      PairRotation.Gaps gaps) {
    assertEquals(new ArrayList<Long>(Arrays.asList((long) rotated, rotatedMs, (long) plain,
        plainMs)), new ArrayList<Long>(Arrays.asList((long) gaps.getRotated(),
        gaps.getRotatedMs(), (long) gaps.getPlain(), gaps.getPlainMs())));
  }
}
