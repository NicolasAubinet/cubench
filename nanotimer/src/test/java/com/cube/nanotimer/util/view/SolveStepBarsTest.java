package com.cube.nanotimer.util.view;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.cube.nanotimer.cube.SolveBreakdown;
import com.cube.nanotimer.vo.SolveStep;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * Which steps are drawn as the same thing. A blind solve is executed in whatever order the solver
 * remembers their memo in, so a piece type can come round more than once — and every stretch of it
 * has to read as that type rather than as a new step each time.
 */
public class SolveStepBarsTest {

  private static List<SolveStep> steps(String... names) {
    List<SolveStep> steps = new ArrayList<SolveStep>();
    for (int i = 0; i < names.length; i++) {
      steps.add(new SolveStep(i, names[i], 0, 1000, Collections.<SolveStep>emptyList()));
    }
    return steps;
  }

  /** A method whose steps are all named differently: each takes the next colour, as it always did. */
  @Test
  public void givesEveryDifferentlyNamedStepAColourOfItsOwn() {
    assertArrayEquals(new int[] {0, 1, 2, 3},
        SolveStepBars.colorSlots(steps("cross", "f2l", "oll", "pll")));
  }

  /** Solve 227's shape: an edge flip remembered after the corners had begun. */
  @Test
  public void drawsEveryStretchOfAPieceTypeInThatTypesColour() {
    assertArrayEquals(new int[] {0, 1, 2, 1, 2},
        SolveStepBars.colorSlots(steps("memo", "edges", "corners", "edges", "corners")));
  }

  @Test
  public void keepsAPieceTypeItsColourHoweverOftenItComesRound() {
    assertArrayEquals(new int[] {0, 1, 0, 1, 0, 2},
        SolveStepBars.colorSlots(steps("edges", "corners", "edges", "corners", "edges", "parity")));
  }

  /**
   * The user's own steps carry no names at all — they are labelled from the solve type, not from the
   * breakdown. Grouped by name they would collapse into a single colour and paint the whole solve
   * one block, which is the reason this is by name rather than merely by equality.
   */
  @Test
  public void leavesTheUsersOwnStepsOneColourEach() {
    assertArrayEquals(new int[] {0, 1, 2},
        SolveStepBars.colorSlots(SolveBreakdown.fromStepTimes(new Long[] {1000L, 2000L, 3000L})));
  }

  /** The tail is drawn in its own colour, so it neither groups nor takes a step's place. */
  @Test
  public void keepsTheTailOutOfTheGrouping() {
    assertArrayEquals(new int[] {0, 1, 0, 2},
        SolveStepBars.colorSlots(
            steps("edges", "corners", "edges", SolveBreakdown.UNFINISHED_STEP)));
  }

  /** Three stretches of edges of two seconds each are one legend entry reading "edges 6s". */
  @Test
  public void addsUpEveryStretchOfAStepIntoOneLegendEntry() {
    List<SolveStep> steps = new ArrayList<SolveStep>();
    steps.add(step("edges", 2000));
    steps.add(step("corners", 1000));
    steps.add(step("edges", 2000));
    steps.add(step("corners", 3000));
    steps.add(step("edges", 2000));

    List<SolveStepBars.LegendEntry> legend = SolveStepBars.legend(steps);
    assertEquals(2, legend.size()); // the bar draws five stretches; the legend says two steps
    assertEquals(0, legend.get(0).getStepIndex()); // named after the first stretch of its colour
    assertEquals(6000, legend.get(0).getTotalMs());
    assertEquals(1, legend.get(1).getStepIndex());
    assertEquals(4000, legend.get(1).getTotalMs());
  }

  /** However far a solve interleaves, the legend stays the size the method's step list is. */
  @Test
  public void keepsTheLegendToTheStepsTheMethodHas() {
    List<SolveStep> steps = new ArrayList<SolveStep>();
    for (int i = 0; i < 9; i++) {
      steps.add(step(i % 2 == 0 ? "edges" : "corners", 1000));
    }
    assertEquals(2, SolveStepBars.legend(steps).size());
  }

  /**
   * Solve 227 as the timer draws it: five stretches on the bar, three cells under it. The flip that
   * interrupted the corners is edge work and is added to the edges, where the solver spent it —
   * nothing is lost between the stretches, and nothing lands on the step that merely surrounded it.
   */
  @Test
  public void addsAnInterruptedStepsStretchesBackTogether() {
    List<SolveStep> steps = new ArrayList<SolveStep>();
    steps.add(step("memo", 23266));
    steps.add(step("edges", 11021));
    steps.add(step("corners", 6931));
    steps.add(step("edges", 4729)); // the flip, done after the corners had begun
    steps.add(step("corners", 11403));

    List<SolveStepBars.LegendEntry> legend = SolveStepBars.legend(steps);
    assertEquals(3, legend.size());
    assertEquals(23266, legend.get(0).getTotalMs());
    assertEquals(11021 + 4729, legend.get(1).getTotalMs());
    assertEquals(6931 + 11403, legend.get(2).getTotalMs());

    long accounted = 0;
    for (SolveStepBars.LegendEntry entry : legend) {
      accounted += entry.getTotalMs();
    }
    assertEquals(57350, accounted); // the whole solve, to the last millisecond
  }

  private static SolveStep step(String name, long totalMs) {
    return new SolveStep(0, name, 0, totalMs, Collections.<SolveStep>emptyList());
  }
}
