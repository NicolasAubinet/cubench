package com.cube.nanotimer.gui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.cube.nanotimer.R;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.util.view.SolveStepBarView;
import com.cube.nanotimer.vo.SolveStep;
import java.util.List;

/**
 * The breakdown of the solve just finished: the step bar, with each step's name and time beneath it.
 * Shows the steps a smart cube saw, so it stays empty on a tap-timed solve.
 */
public class SolveStepBar extends LinearLayout {

  private static final int MAX_STEPS = 4;

  private final int[] colors;
  private final View[] cells = new View[MAX_STEPS];
  private final TextView[] names = new TextView[MAX_STEPS];
  private final TextView[] times = new TextView[MAX_STEPS];
  private final SolveStepBarView bar;

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
    int[] cellIds = {R.id.stepLegendCell0, R.id.stepLegendCell1, R.id.stepLegendCell2, R.id.stepLegendCell3};
    int[] nameIds = {R.id.tvStepName0, R.id.tvStepName1, R.id.tvStepName2, R.id.tvStepName3};
    int[] timeIds = {R.id.tvStepTime0, R.id.tvStepTime1, R.id.tvStepTime2, R.id.tvStepTime3};
    for (int i = 0; i < MAX_STEPS; i++) {
      cells[i] = findViewById(cellIds[i]);
      names[i] = findViewById(nameIds[i]);
      times[i] = findViewById(timeIds[i]);
    }
  }

  public void setSteps(List<SolveStep> steps) {
    bar.setSteps(steps, colors);
    for (int i = 0; i < MAX_STEPS; i++) {
      if (i >= steps.size()) {
        cells[i].setVisibility(GONE);
        continue;
      }
      SolveStep step = steps.get(i);
      cells[i].setVisibility(VISIBLE);
      // Plain name, no "(partial)" marker: the cell is a quarter of the screen, and a longer label
      // pushes the time out of it. The detail dialog has the room to say it.
      names[i].setText(Utils.toSmartCubeStepLocalizedName(getContext(), step.getName(), i));
      names[i].setTextColor(Utils.isUnfinishedTail(step.getName())
          ? ContextCompat.getColor(getContext(), R.color.gray600)
          : colors[i % colors.length]);
      times[i].setText(FormatterService.INSTANCE.formatSolveTime(step.getTotalMs()));
    }
  }
}
