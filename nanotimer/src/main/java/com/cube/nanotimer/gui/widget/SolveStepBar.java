package com.cube.nanotimer.gui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.cube.nanotimer.R;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.util.view.SolveStepBarView;
import com.cube.nanotimer.vo.SolveStep;
import java.util.ArrayList;
import java.util.List;

/**
 * The breakdown of the solve just finished: the step bar, with each step's name and time beneath it.
 * The steps are a method's or the user's own, and how many there are is theirs to decide — the
 * legend is built to the count. Empty on a tap-timed solve, which no steps were seen for.
 */
public class SolveStepBar extends LinearLayout {

  /** Past this a legend row would be unreadably narrow, so it wraps onto the next one. */
  private static final int MAX_CELLS_PER_ROW = 4;

  private final int[] colors;
  private final List<View> cells = new ArrayList<View>();
  private final LinearLayout legend;
  private final SolveStepBarView bar;
  private ValueAnimator revealAnimator;

  public SolveStepBar(Context context, AttributeSet attributes) {
    super(context, attributes);
    setOrientation(VERTICAL);
    LayoutInflater.from(context).inflate(R.layout.solve_step_bar, this);

    TypedArray stepColors = getResources().obtainTypedArray(R.array.solve_step_colors);
    colors = new int[stepColors.length()];
    for (int i = 0; i < colors.length; i++) {
      colors[i] = stepColors.getColor(i, 0);
    }
    stepColors.recycle();

    bar = findViewById(R.id.solveStepBarView);
    legend = findViewById(R.id.stepLegend);
    buildLegend(MAX_CELLS_PER_ROW); // a row from the start, so reserved space is the height it will have
  }

  /**
   * Lays the legend out for a solve of this many steps before there is one to show, so the space the
   * timer reserves is the height the bar ends up needing and the screen never shifts under a solve.
   */
  public void prepareLegend(int stepCount) {
    buildLegend(stepCount);
  }

  /** The steps a method was solved in, labelled by their codes. */
  public void setSteps(List<SolveStep> steps) {
    setSteps(steps, null);
  }

  /**
   * @param stepNames the names to label the steps with, as the user wrote them; null to label them
   *     as the method step codes they are
   */
  public void setSteps(List<SolveStep> steps, String[] stepNames) {
    bar.setSteps(steps, colors);
    buildLegend(steps.size());
    for (int i = 0; i < steps.size(); i++) {
      SolveStep step = steps.get(i);
      TextView name = cells.get(i).findViewById(R.id.tvStepName);
      if (stepNames != null) {
        // A name of the user's own can run long, and a cell is a share of the screen: bound it so it
        // is the name that gives way rather than the time being pushed out of the cell. A method's
        // own labels are short by construction, and stay unbounded.
        name.setMaxEms(9);
        name.setEllipsize(TextUtils.TruncateAt.END);
      }
      name.setText(stepNames != null && i < stepNames.length ? stepNames[i]
          : Utils.toSmartCubeStepLocalizedName(getContext(), step.getName(), i));
      name.setTextColor(Utils.isTailSegment(step.getName())
          ? ContextCompat.getColor(getContext(), R.color.gray600)
          : colors[i % colors.length]);
      ((TextView) cells.get(i).findViewById(R.id.tvStepTime))
          .setText(FormatterService.INSTANCE.formatSolveTime(step.getTotalMs()));
    }
  }

  /**
   * One cell per step, in rows of at most four: how many steps there are is the method's business
   * (or the user's), so the legend is built to the count rather than the count fitted to a layout.
   * Rebuilt only when the count changes, which for a given solve type is next to never.
   */
  private void buildLegend(int stepCount) {
    if (cells.size() == stepCount) {
      return;
    }
    cells.clear();
    legend.removeAllViews();
    LayoutInflater inflater = LayoutInflater.from(getContext());
    int rows = (stepCount + MAX_CELLS_PER_ROW - 1) / MAX_CELLS_PER_ROW;
    int perRow = rows > 1 ? MAX_CELLS_PER_ROW : stepCount;
    for (int row = 0; row < rows; row++) {
      LinearLayout rowLayout = new LinearLayout(getContext());
      rowLayout.setOrientation(HORIZONTAL);
      legend.addView(rowLayout, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
      for (int i = 0; i < perRow; i++) {
        View cell = inflater.inflate(R.layout.solve_step_legend_cell, rowLayout, false);
        rowLayout.addView(cell);
        if (cells.size() < stepCount) {
          cells.add(cell);
        } else {
          // A last row shorter than the others keeps its empty cells, so every row stays in columns.
          cell.setVisibility(INVISIBLE);
        }
      }
    }
  }

  /** Sweeps the bar in left-to-right; used to make a finished smart-cube solve feel less abrupt. */
  public void animateIn() {
    if (revealAnimator != null) {
      revealAnimator.cancel();
    }
    bar.setProgress(0f);
    revealAnimator = ValueAnimator.ofFloat(0f, 1f);
    revealAnimator.setDuration(400);
    revealAnimator.setInterpolator(new DecelerateInterpolator());
    revealAnimator.addUpdateListener(a -> bar.setProgress((float) a.getAnimatedValue()));
    revealAnimator.start();
  }
}
