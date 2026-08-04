package com.cube.nanotimer.util.view;

import android.content.Context;
import android.content.res.TypedArray;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;
import com.cube.nanotimer.cube.SolveBreakdown;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.vo.SolveStep;
import com.cube.nanotimer.vo.SolveTime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a {@link SolveStepBarView} is fed, and the colours it paints with.
 *
 * <p>At row scale the bar is read at a glance rather than studied, so {@link #forRow} strips it to
 * one solid block per step: the parts a step was built in and the thinking inside each one are
 * detail the timer's full-width bar has room for and a list row does not. Which leaves the shape of
 * the solve, in the same colours, which is the whole point of putting it in the row.
 */
public final class SolveStepBars {

  private SolveStepBars() {
  }

  /**
   * Which colour of the palette each step is drawn from, as an index into {@link #stepColors}:
   * <b>by name</b>, in order of first appearance, so every stretch of a piece type is drawn as that
   * type however often the solve comes back to it. A blind solver who leaves a pair behind and
   * remembers it later gets one colour for their edges, not one per stretch.
   *
   * <p>A step with no name to be grouped by takes a slot of its own — the user's own steps are all
   * unnamed, and collapsing those would paint a whole solve one colour. So does the tail, which is
   * drawn in its own colour anyway; every caller decides that for itself, and this only says which
   * steps belong together.
   *
   * <p>A method whose steps are all named differently is unaffected: each takes the next slot, which
   * is what indexing by step position did.
   */
  public static int[] colorSlots(List<SolveStep> steps) {
    int[] slots = new int[steps.size()];
    List<String> named = new ArrayList<>();
    for (int i = 0; i < steps.size(); i++) {
      String name = steps.get(i).getName();
      boolean groupable = name != null && !name.isEmpty() && !Utils.isTailSegment(name);
      int slot = groupable ? named.indexOf(name) : -1;
      if (slot < 0) {
        slot = named.size();
        named.add(groupable ? name : null); // a null never matches, so it is a slot of its own
      }
      slots[i] = slot;
    }
    return slots;
  }

  /** One entry of a bar's legend: the step it is named after, its colour, and what it cost. */
  public static final class LegendEntry {
    private final int stepIndex;
    private final int colorSlot;
    private final long totalMs;

    LegendEntry(int stepIndex, int colorSlot, long totalMs) {
      this.stepIndex = stepIndex;
      this.colorSlot = colorSlot;
      this.totalMs = totalMs;
    }

    /** The step this entry takes its name from: the first the solve drew in this colour. */
    public int getStepIndex() {
      return stepIndex;
    }

    public int getColorSlot() {
      return colorSlot;
    }

    /** Every stretch drawn in this colour, added up. */
    public long getTotalMs() {
      return totalMs;
    }
  }

  /**
   * A solve's legend: one entry per colour rather than per step, so a step the solve came back to is
   * said once and carries all of it — three stretches of edges of two seconds each are one "edges
   * 6s". The bar still draws all three; the legend says what the step cost, not when it was paid.
   *
   * <p>Which also keeps the legend to the number of steps the method has, however often the solver
   * went back to one. Built to the stretches it would grow a row per four of them, and a solve that
   * interleaved freely would move the timer screen under the solver.
   */
  public static List<LegendEntry> legend(List<SolveStep> steps) {
    int[] slots = colorSlots(steps);
    List<LegendEntry> legend = new ArrayList<>();
    for (int i = 0; i < steps.size(); i++) {
      int at = -1;
      for (int cell = 0; cell < legend.size(); cell++) {
        if (legend.get(cell).colorSlot == slots[i]) {
          at = cell;
          break;
        }
      }
      if (at < 0) {
        legend.add(new LegendEntry(i, slots[i], steps.get(i).getTotalMs()));
      } else {
        LegendEntry entry = legend.get(at);
        legend.set(at, new LegendEntry(entry.stepIndex, entry.colorSlot,
            entry.totalMs + steps.get(i).getTotalMs()));
      }
    }
    return legend;
  }

  /** One colour per step, in step order, shared by every bar in the app. */
  public static int[] stepColors(Context context) {
    TypedArray array = context.getResources().obtainTypedArray(R.array.solve_step_colors);
    int[] colors = new int[array.length()];
    for (int i = 0; i < colors.length; i++) {
      colors[i] = array.getColor(i, 0);
    }
    array.recycle();
    return colors;
  }

  /**
   * Draws a history row's bar for this solve, and says whether there was anything to draw.
   *
   * <p>A solve broken down into steps gets those steps in the step colours. A cube-driven solve
   * whose milestones fitted no method has no steps to draw but is still a solve the cube read, so it
   * gets one neutral block: it stays told apart from a tap-timed solve without claiming a breakdown
   * it hasn't got. The block is the colour the bar already paints time that belongs to no step.
   */
  public static boolean paintRow(SolveStepBarView bar, SolveTime solveTime, int[] stepColors) {
    List<SolveStep> steps = forRow(solveTime);
    if (!steps.isEmpty()) {
      bar.setSteps(steps, stepColors);
      return true;
    }
    if (solveTime.getSmartcubeMoves() != null) {
      bar.setSteps(wholeSolve(solveTime), neutralColors(bar.getContext()));
      return true;
    }
    return false;
  }

  /**
   * The steps of a solve as a history row draws them, from whichever breakdown it has: the one a
   * smart cube read, or the one the user timed by hand. Empty when it has neither.
   */
  public static List<SolveStep> forRow(SolveTime solveTime) {
    if (solveTime.hasSmartcubeBreakdown()) {
      return flatten(solveTime.getSmartcubeSteps());
    }
    if (solveTime.hasSteps()) {
      return SolveBreakdown.fromStepTimes(solveTime.getStepsTimes());
    }
    return Collections.emptyList();
  }

  /** Drops the parts and the thinking, keeping each step's total. */
  private static List<SolveStep> flatten(List<SolveStep> steps) {
    List<SolveStep> flat = new ArrayList<>(steps.size());
    for (SolveStep step : steps) {
      flat.add(new SolveStep(step.getStepIndex(), step.getName(), 0, step.getTotalMs(),
        Collections.<SolveStep>emptyList()));
    }
    return flat;
  }

  /** The solve as one undivided block, for a bar with no steps to show. */
  private static List<SolveStep> wholeSolve(SolveTime solveTime) {
    long time = Math.max(1, solveTime.getTime()); // a DNF's time is negative, which would draw nothing
    return Collections.singletonList(
      new SolveStep(0, null, 0, time, Collections.<SolveStep>emptyList()));
  }

  private static int[] neutralColors(Context context) {
    return new int[] { ContextCompat.getColor(context, R.color.gray600) };
  }
}
