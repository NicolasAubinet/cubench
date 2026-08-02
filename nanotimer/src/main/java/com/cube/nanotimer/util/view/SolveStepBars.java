package com.cube.nanotimer.util.view;

import android.content.Context;
import android.content.res.TypedArray;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;
import com.cube.nanotimer.cube.SolveBreakdown;
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
