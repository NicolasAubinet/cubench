package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.vo.CubeMethod;
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
    return SolveBreakdown.withTail(solveTime.getSmartcubeSteps(),
        solveTime.getSmartcubeStoppedStep(), SolveBreakdown.solvingDurationMs(solveTime),
        solveTime.getSmartcubeMoves(), solveTime.getSmartcubeMethod());
  }

  @Test
  public void addsNoTailToASolveThatRanToTheEnd() {
    List<SolveStep> steps = SolveBreakdown.withTail(twoSteps(), null, 9000, "R@0", CubeMethod.CFOP);

    assertEquals(2, steps.size()); // the steps already account for all of it
    assertEquals("cross", steps.get(0).getName());
    assertEquals("f2l", steps.get(1).getName());
  }

  /**
   * Nothing stops a blind solve when the cube comes out solved — the solver cannot see it — so the
   * time between the last milestone and the tap is real, and belongs to no step. A sighted solve
   * that ran to the end never has one: the cube stopped it there.
   */
  @Test
  public void givesAFinishedBlindSolveTheTimeItTookToStopIt() {
    List<SolveStep> steps = SolveBreakdown.withTail(twoSteps(), null, 5000, "R@1000 U@2900",
        CubeMethod.BLIND);

    assertEquals(3, steps.size());
    SolveStep gap = steps.get(2);
    assertEquals(SolveBreakdown.GAP_STEP, gap.getName());
    assertEquals(2000, gap.getTotalMs());
    // Turning nothing, and counted as turning all the same: the solve was over, so what the tail
    // holds is the stop being made rather than a step being thought about.
    assertEquals(0, gap.getRecognitionMs());
    assertEquals(2000, gap.getExecutionMs());
    assertEquals(2, SolveBreakdown.withTail(twoSteps(), null, 5000, "R@1000", CubeMethod.CFOP).size());
  }

  @Test
  public void neverHandsBackTheCallersOwnList() {
    // The timer passes the list it goes on to save, so the result has to be a copy either way:
    // aliasing on some inputs and not others is how a caller's mutation reaches the database.
    List<SolveStep> steps = twoSteps();

    assertNotSame(steps, SolveBreakdown.withTail(steps, null, 9000, "R@0", CubeMethod.CFOP));
    assertNotSame(steps, SolveBreakdown.withTail(steps, 1, 5000, "R@1000 U@3400", CubeMethod.CFOP));
  }

  @Test
  public void fillsTheGapBetweenTheLastMilestoneAndTheStop() {
    // Stopped at 5s with 3s of steps: 2s belongs to no step. The first move after the milestone is
    // at 3.4s, so 400ms of it was thinking and the remaining 1.6s turning.
    List<SolveStep> steps = SolveBreakdown.withTail(twoSteps(), 1, 5000, "R@1000 U@3400 F@3900", CubeMethod.CFOP);

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
    List<SolveStep> steps = SolveBreakdown.withTail(twoSteps(), 1, 5000, "R@1000 U@2900", CubeMethod.CFOP);

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
  public void turnsTapTimesIntoStepsTheMovesCanBeSplitAt() {
    // The tap times are durations, so the steps run back to back and the moves fall where the taps
    // put them: memo through 5.0s, execution after it.
    List<SolveStep> steps = SolveBreakdown.fromStepTimes(new Long[] {5000L, 3000L});

    assertEquals(2, steps.size());
    assertEquals(5000, steps.get(0).getTotalMs());
    assertEquals(3000, steps.get(1).getTotalMs());
    assertEquals(1, steps.get(1).getStepIndex());
    // A tap says when a step ended and nothing else: no name to show, and no thinking/turning split.
    assertEquals("", steps.get(0).getName());
    assertEquals(0, steps.get(0).getRecognitionMs());

    SolveSolution solution = SolveSolution.from("R@0 U@4000 F@6000", steps);
    assertEquals("R U", solution.getSteps().get(0).getMoves());
    assertEquals("F", solution.getSteps().get(1).getMoves());
  }

  @Test
  public void givesARunningSolveASlotForEveryStepStillToCome() {
    List<SolveStep> steps = SolveBreakdown.inProgress(new Long[] {6000L, 2000L}, 4);

    assertEquals(4, steps.size());
    assertEquals(6000, steps.get(0).getTotalMs());
    assertEquals(2000, steps.get(1).getTotalMs());
    // A slot is worth what the steps before it averaged, so the bar stays full width throughout.
    assertEquals(4000, steps.get(2).getTotalMs());
    assertEquals(4000, steps.get(3).getTotalMs());
  }

  @Test
  public void givesTheStepsOfAJustStartedSolveTheSameWidth() {
    List<SolveStep> steps = SolveBreakdown.inProgress(new Long[0], 3);

    assertEquals(3, steps.size());
    assertEquals(steps.get(0).getTotalMs(), steps.get(2).getTotalMs());
    assertTrue(steps.get(0).getTotalMs() > 0); // a step of no width is skipped by the bar
  }

  @Test
  public void hasNoStepsToSplitAtWhenTheSolveHasNoTapTimes() {
    assertEquals(0, SolveBreakdown.fromStepTimes(null).size());
    assertEquals(0, SolveBreakdown.fromStepTimes(new Long[0]).size());
  }

  @Test
  public void addsNoTailWhenTheClocksDisagree() {
    // The timer stopped fractionally before the last milestone reached us over BLE. That skew is not
    // a segment worth drawing, and must never become a negative one.
    List<SolveStep> steps = SolveBreakdown.withTail(twoSteps(), 1, 2950, "R@1000", CubeMethod.CFOP);

    assertEquals(2, steps.size());
  }
}
