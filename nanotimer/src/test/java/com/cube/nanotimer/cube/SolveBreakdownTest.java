package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import com.cube.nanotimer.vo.SolveStep;
import com.cube.nanotimer.vo.SolveTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class SolveBreakdownTest {

  private static SolveStep step(int index, String name, long recognitionMs, long executionMs) {
    return new SolveStep(index, name, recognitionMs, executionMs, new ArrayList<SolveStep>());
  }

  /** Cross 1.0s then F2L 2.0s: 3.0s accounted for. */
  private static List<SolveStep> twoSteps() {
    return Arrays.asList(step(0, "cross", 200, 800), step(1, "f2l", 500, 1500));
  }

  /** The breakdown of a stored solve, the way the history dialog builds it. */
  private static List<SolveStep> tailedSteps(SolveTime solveTime) {
    return SolveBreakdown.withUnfinishedTail(solveTime.getSmartcubeSteps(),
        solveTime.getSmartcubeStoppedStep(), SolveBreakdown.solvingDurationMs(solveTime),
        solveTime.getSmartcubeMoves());
  }

  @Test
  public void addsNoTailToASolveThatRanToTheEnd() {
    List<SolveStep> steps = SolveBreakdown.withUnfinishedTail(twoSteps(), null, 9000, "R@0");

    assertEquals(2, steps.size()); // the steps already account for all of it
    assertEquals("cross", steps.get(0).getName());
    assertEquals("f2l", steps.get(1).getName());
  }

  @Test
  public void neverHandsBackTheCallersOwnList() {
    // The timer passes the list it goes on to save, so the result has to be a copy either way:
    // aliasing on some inputs and not others is how a caller's mutation reaches the database.
    List<SolveStep> steps = twoSteps();

    assertNotSame(steps, SolveBreakdown.withUnfinishedTail(steps, null, 9000, "R@0"));
    assertNotSame(steps, SolveBreakdown.withUnfinishedTail(steps, 1, 5000, "R@1000 U@3400"));
  }

  @Test
  public void fillsTheGapBetweenTheLastMilestoneAndTheStop() {
    // Stopped at 5s with 3s of steps: 2s belongs to no step. The first move after the milestone is
    // at 3.4s, so 400ms of it was thinking and the remaining 1.6s turning.
    List<SolveStep> steps = SolveBreakdown.withUnfinishedTail(twoSteps(), 1, 5000, "R@1000 U@3400 F@3900");

    assertEquals(3, steps.size());
    SolveStep tail = steps.get(2);
    assertEquals(SolveBreakdown.UNFINISHED_STEP, tail.getName());
    assertEquals(400, tail.getRecognitionMs());
    assertEquals(1600, tail.getExecutionMs());
    assertEquals(5000, twoSteps().get(0).getTotalMs() + twoSteps().get(1).getTotalMs()
        + tail.getTotalMs()); // steps and tail account for the whole solve again
  }

  @Test
  public void countsATailWithNoMovesAsThinking() {
    // It stopped without turning anything after the last milestone: 2s of staring at the cube.
    List<SolveStep> steps = SolveBreakdown.withUnfinishedTail(twoSteps(), 1, 5000, "R@1000 U@2900");

    SolveStep tail = steps.get(2);
    assertEquals(2000, tail.getRecognitionMs());
    assertEquals(0, tail.getExecutionMs());
  }

  @Test
  public void fallsBackToTheLastMoveWhenADnfTookTheTimeAway() {
    // A DNF replaces the time with a sentinel, so nothing records how long the solve ran. The last
    // move is the floor: the tail covers the turning, and misses only the staring after it.
    SolveTime solveTime = new SolveTime();
    solveTime.setTime(-1);
    solveTime.setSmartcubeSteps(twoSteps());
    solveTime.setSmartcubeStoppedStep(1);
    solveTime.setSmartcubeMoves("R@1000 U@3400 F@4800");

    assertEquals(4800, SolveBreakdown.solvingDurationMs(solveTime));

    List<SolveStep> steps = tailedSteps(solveTime);
    assertEquals(3, steps.size());
    SolveStep tail = steps.get(2);
    assertEquals(400, tail.getRecognitionMs()); // 3.0s of steps, first move after at 3.4s
    assertEquals(1400, tail.getExecutionMs()); // through to the last move at 4.8s
  }

  @Test
  public void doesNotCountAPlusTwoPenaltyAsTurning() {
    // The stored time carries the 2s penalty; the solve was not being turned for it, so counting it
    // would show 2s of tail that never happened — and disagree with what the timer screen drew.
    SolveTime solveTime = new SolveTime();
    solveTime.setTime(7000);
    solveTime.setPlusTwo(true, false);
    solveTime.setSmartcubeSteps(twoSteps());
    solveTime.setSmartcubeStoppedStep(1);
    solveTime.setSmartcubeMoves("R@1000 U@3400");

    assertEquals(5000, SolveBreakdown.solvingDurationMs(solveTime)); // 7s less the penalty

    SolveStep tail = tailedSteps(solveTime).get(2);
    assertEquals(2000, tail.getTotalMs()); // 5s of solving less the 3s of steps, not 7s less 3s
  }

  @Test
  public void addsNoTailWhenTheClocksDisagree() {
    // The timer stopped fractionally before the last milestone reached us over BLE. That skew is not
    // a segment worth drawing, and must never become a negative one.
    List<SolveStep> steps = SolveBreakdown.withUnfinishedTail(twoSteps(), 1, 2950, "R@1000");

    assertEquals(2, steps.size());
  }
}
