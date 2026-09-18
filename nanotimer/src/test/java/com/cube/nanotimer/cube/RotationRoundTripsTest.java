package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveStep;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class RotationRoundTripsTest {

  private static SolveSolution shown(String stored) {
    return shown(stored, null);
  }

  private static SolveSolution shown(String stored, CubeMethod method) {
    SolveStep step = new SolveStep(0, "cross", 0, 1000, Collections.<SolveStep>emptyList());
    return SolveSolution.from(stored, Arrays.asList(step), method);
  }

  @Test
  public void aTiltDuringATurnIsFolded() {
    assertEquals("U", shown("y@0 U@10 y'@20").getSteps().get(0).getMoves());
  }

  @Test
  public void severalTurnsOnTheAxisAreFoldedToo() {
    assertEquals("U D' E", shown("y@0 U@10 D'@20 E@30 y'@40").getSteps().get(0).getMoves());
  }

  @Test
  public void aHalfTurnRotationTakenBackIsFolded() {
    assertEquals("R M'", shown("x2@0 R@10 M'@20 x2@30").getSteps().get(0).getMoves());
  }

  /** {@code x U x'} turns what the solver sees as another face, so it is a regrip and stays. */
  @Test
  public void aRoundTripThatChangesALetterStays() {
    String moves = shown("x@0 U@10 x'@20").getSteps().get(0).getMoves();

    assertEquals(moves, 3, moves.split(" ").length);
    assertEquals(moves, "x", moves.split(" ")[0]);
  }

  @Test
  public void oneTurnOffTheAxisKeepsTheWholeRoundTrip() {
    String moves = shown("y@0 U@10 R@20 y'@30").getSteps().get(0).getMoves();

    assertEquals(moves, 4, moves.split(" ").length);
  }

  /** A bare rotation and its undo is struck out as a cancelled pair, not folded away. */
  @Test
  public void aWobbleWithNothingInsideStays() {
    assertEquals("y y'", shown("y@0 y'@10").getSteps().get(0).getMoves());
  }

  @Test
  public void nestedRoundTripsFoldFromTheInside() {
    assertEquals("U U' D",
        shown("y@0 U@10 y@20 U'@30 y'@40 D@50 y'@60").getSteps().get(0).getMoves());
  }

  /** A tilt splitting a half turn: once it is gone, the two quarters read as the U2 they were. */
  @Test
  public void aTurnSplitByATiltReadsAsOneHalfTurn() {
    SolveSolution solution = shown("U@0 y@5 U@10 y'@20");

    assertEquals("U2", solution.getSteps().get(0).getMoves());
    assertEquals(1, solution.getMoveCount());
  }

  @Test
  public void theFoldedSolutionStillSolves() {
    String moves = shown("R@0 y2@100 U@200 D'@400 y2@500 R'@600").getSteps().get(0).getMoves();

    assertEquals("R U D' R'", moves);
    assertTrue(DisplayedSolutionReplay.solves("R D U' R'", moves));
  }
}
