package com.cube.nanotimer.util.view;

import android.content.Context;
import android.content.res.TypedArray;

import com.cube.nanotimer.R;
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
   * The steps of a solve as a history row draws them, from whichever breakdown it has: the one a
   * smart cube read, or the one the user timed by hand. Empty when it has neither.
   */
  public static List<SolveStep> forRow(SolveTime solveTime) {
    if (solveTime.hasSmartcubeBreakdown()) {
      return flatten(solveTime.getSmartcubeSteps());
    }
    if (solveTime.hasSteps()) {
      return fromStepTimes(solveTime.getStepsTimes());
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

  /** The steps of a solve type timed by hand, which have a duration and nothing else. */
  private static List<SolveStep> fromStepTimes(Long[] stepTimes) {
    List<SolveStep> steps = new ArrayList<>(stepTimes.length);
    for (int i = 0; i < stepTimes.length; i++) {
      long time = stepTimes[i] == null ? 0 : stepTimes[i];
      steps.add(new SolveStep(i, null, 0, Math.max(0, time), Collections.<SolveStep>emptyList()));
    }
    return steps;
  }
}
