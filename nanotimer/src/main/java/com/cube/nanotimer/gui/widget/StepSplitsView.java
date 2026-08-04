package com.cube.nanotimer.gui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.cube.nanotimer.R;
import com.cube.nanotimer.cube.SolveBreakdown;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.ScaleUtils;
import com.cube.nanotimer.util.view.ScalingLinearLayout;
import com.cube.nanotimer.util.view.SolveStepBarView;
import com.cube.nanotimer.util.view.SolveStepBars;
import com.cube.nanotimer.vo.SolveAverages;
import com.cube.nanotimer.vo.SolveStep;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * How a solve type timed in steps reads its averages: a row per window, holding each step's average
 * in the colour the legend names it in, the total, and under them the same step bar the rest of the
 * app draws a solve with.
 *
 * <p>The five bars share one length scale, so the slowest window fills the width and the others are
 * read against it — which is the comparison the block exists for, and what the numbers on their own
 * cannot say. The numbers are the other half: a bar that is a share of another row states no time of
 * its own.
 *
 * <p>A row is an average of each step taken separately, not a solve that ever happened. The legend
 * names the steps and the key names the window, so the row is read as the shape of an average rather
 * than as one more solve.
 */
public class StepSplitsView extends LinearLayout {

  private static final int[] WINDOWS = {
      R.string.ao5_label, R.string.ao12_label, R.string.ao50_label, R.string.ao100_label,
      R.string.life };

  /**
   * Past this many steps the row goes back to the bar alone. Four columns already come down to
   * ~54px each in landscape; a fifth leaves the times shrunk past reading, and a name over them
   * ellipsized to nothing.
   */
  private static final int MAX_STEPS_WITH_VALUES = 4;

  private final int[] colors;
  private final LinearLayout legend;
  private final List<View> rows = new ArrayList<View>();

  public StepSplitsView(Context context, AttributeSet attributes) {
    super(context, attributes);
    setOrientation(VERTICAL);
    colors = SolveStepBars.stepColors(context);

    LayoutInflater inflater = LayoutInflater.from(context);
    inflater.inflate(R.layout.timer_step_splits, this);
    legend = (LinearLayout) findViewById(R.id.splitsLegend);
    for (int window : WINDOWS) {
      View row = inflater.inflate(R.layout.timer_step_split_row, this, false);
      ((TextView) row.findViewById(R.id.tvSplitKey)).setText(window);
      addView(row);
      rows.add(row);
    }
  }

  /** @param stepNames the steps of the solve type, in order, as the user named them */
  public void setStepNames(String[] stepNames) {
    float scale = ScaleUtils.getScale(getContext());
    LayoutInflater inflater = LayoutInflater.from(getContext());
    boolean withValues = stepNames.length <= MAX_STEPS_WITH_VALUES;

    legend.removeAllViews();
    for (int i = 0; i < stepNames.length; i++) {
      TextView cell = new TextView(getContext(), null, 0, R.style.SplitLegendCellPx);
      cell.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
      cell.setText(stepNames[i]);
      cell.setTextColor(colors[i % colors.length]);
      // Built after the timer layout scaled itself, so it would otherwise draw at the raw px size.
      ScalingLinearLayout.scaleLateSubtree(cell, scale);
      legend.addView(cell);
    }

    for (View row : rows) {
      LinearLayout values = (LinearLayout) row.findViewById(R.id.splitValues);
      values.removeAllViews();
      values.setVisibility(withValues ? VISIBLE : GONE);
      if (!withValues) {
        continue;
      }
      for (int i = 0; i < stepNames.length; i++) {
        TextView cell =
            (TextView) inflater.inflate(R.layout.timer_step_split_value, values, false);
        cell.setTextColor(colors[i % colors.length]); // the colour is what ties it to its name
        ScalingLinearLayout.scaleLateSubtree(cell, scale);
        values.addView(cell);
      }
    }
  }

  /** Draws every window against the slowest of them. A window with too few solves shows no bar. */
  public void setAverages(SolveAverages averages) {
    List<List<Long>> windows = Arrays.asList(
        averages.getStepsAvgOf5(), averages.getStepsAvgOf12(), averages.getStepsAvgOf50(),
        averages.getStepsAvgOf100(), averages.getStepsAvgOfLifetime());
    long[] totals = new long[windows.size()];
    long longest = 0;
    for (int i = 0; i < windows.size(); i++) {
      totals[i] = total(windows.get(i));
      longest = Math.max(longest, totals[i]);
    }
    for (int i = 0; i < rows.size(); i++) {
      bindRow(rows.get(i), windows.get(i), totals[i], longest);
    }
  }

  private void bindRow(View row, List<Long> steps, long total, long longest) {
    SolveStepBarView bar = (SolveStepBarView) row.findViewById(R.id.splitBar);
    bar.setSteps(total > 0 ? SolveBreakdown.fromStepTimes(asStepTimes(steps))
        : Collections.<SolveStep>emptyList(), colors);
    setBarShare(bar, longest > 0 ? total / (float) longest : 0f);
    ((TextView) row.findViewById(R.id.tvSplitTotal))
        .setText(total > 0 ? FormatterService.INSTANCE.formatSolveTime(total) : "-");

    LinearLayout values = (LinearLayout) row.findViewById(R.id.splitValues);
    for (int i = 0; i < values.getChildCount(); i++) {
      long step = steps == null || i >= steps.size() ? 0 : positive(steps.get(i));
      ((TextView) values.getChildAt(i))
          .setText(step > 0 ? FormatterService.INSTANCE.formatSolveTime(step) : "-");
    }
  }

  private void setBarShare(SolveStepBarView bar, float share) {
    LayoutParams params = (LayoutParams) bar.getLayoutParams();
    if (params.weight != share) {
      params.weight = share;
      bar.setLayoutParams(params);
    }
  }

  /** Null for a window that has not filled yet; a step with no average counts as nothing. */
  private static long total(List<Long> steps) {
    long total = 0;
    if (steps != null) {
      for (Long step : steps) {
        total += positive(step);
      }
    }
    return total;
  }

  private static Long[] asStepTimes(List<Long> steps) {
    Long[] times = new Long[steps.size()];
    for (int i = 0; i < times.length; i++) {
      times[i] = positive(steps.get(i));
    }
    return times;
  }

  private static long positive(Long time) {
    return time == null || time < 0 ? 0 : time;
  }
}
