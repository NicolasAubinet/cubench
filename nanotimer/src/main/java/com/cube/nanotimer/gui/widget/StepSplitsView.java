package com.cube.nanotimer.gui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.cube.nanotimer.R;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.ScaleUtils;
import com.cube.nanotimer.util.view.ScalingLinearLayout;
import com.cube.nanotimer.util.view.SolveStepBars;
import com.cube.nanotimer.vo.SolveAverages;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * How a solve type timed in steps reads its averages: a row per window, holding each step's average
 * in the colour the legend above names it in, and the total the steps add up to.
 *
 * <p>A row is an average of each step taken separately, not a solve that ever happened. The legend
 * names the steps and the key names the window, so a row is read as the shape of an average rather
 * than as one more solve.
 */
public class StepSplitsView extends LinearLayout {

  private static final int[] WINDOWS = {
      R.string.ao5_label, R.string.ao12_label, R.string.ao50_label, R.string.ao100_label,
      R.string.life };

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
      for (int i = 0; i < stepNames.length; i++) {
        TextView cell =
            (TextView) inflater.inflate(R.layout.timer_step_split_value, values, false);
        cell.setTextColor(colors[i % colors.length]); // the colour is what ties it to its name
        ScalingLinearLayout.scaleLateSubtree(cell, scale);
        values.addView(cell);
      }
    }
  }

  /** A window that has not filled yet shows a dash in every column. */
  public void setAverages(SolveAverages averages) {
    List<List<Long>> windows = Arrays.asList(
        averages.getStepsAvgOf5(), averages.getStepsAvgOf12(), averages.getStepsAvgOf50(),
        averages.getStepsAvgOf100(), averages.getStepsAvgOfLifetime());
    for (int i = 0; i < rows.size(); i++) {
      bindRow(rows.get(i), windows.get(i));
    }
  }

  private void bindRow(View row, List<Long> steps) {
    ((TextView) row.findViewById(R.id.tvSplitTotal)).setText(time(total(steps)));

    LinearLayout values = (LinearLayout) row.findViewById(R.id.splitValues);
    for (int i = 0; i < values.getChildCount(); i++) {
      long step = steps == null || i >= steps.size() ? 0 : positive(steps.get(i));
      ((TextView) values.getChildAt(i)).setText(time(step));
    }
  }

  private static String time(long time) {
    return time > 0 ? FormatterService.INSTANCE.formatSolveTime(time) : "-";
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

  private static long positive(Long time) {
    return time == null || time < 0 ? 0 : time;
  }
}
