package com.cube.nanotimer.gui.widget;

import android.app.Activity;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;
import com.cube.nanotimer.drill.DrillSpec;
import com.cube.nanotimer.smartcube.drill.DrillRep;
import com.cube.nanotimer.smartcube.drill.DrillRepOrder;
import com.cube.nanotimer.util.FormatterService;

import java.util.List;

/**
 * The running drill's three figures ({@code drill_live_cells}): the mean of the reps so far, the
 * mean of their reading, and the best. Skipped and practice reps are left out, as the summary leaves
 * them out of its own. A look-ahead drill's mean and best are of the pause, which is what it trains.
 */
public final class DrillLiveCells {

  private DrillLiveCells() {
  }

  public static void show(Activity activity, List<DrillRep> reps, DrillSpec.Type type) {
    DrillRepOrder.Key key = DrillRepOrder.readBy(type);
    boolean pause = key == DrillRepOrder.Key.PAUSE;
    ((TextView) activity.findViewById(R.id.tvDrillLiveMeanKey)).setText(
        pause ? R.string.drill_summary_cell_mean_pause : R.string.drill_summary_cell_mean);
    ((TextView) activity.findViewById(R.id.tvDrillLiveRecognitionKey)).setText(
        pause ? R.string.drill_lookahead_before : R.string.drill_summary_column_recognition);
    ((TextView) activity.findViewById(R.id.tvDrillLiveBestKey)).setText(
        pause ? R.string.drill_summary_cell_best_pause : R.string.drill_summary_cell_best);
    long total = 0;
    long recognition = 0;
    long best = Long.MAX_VALUE;
    int timed = 0;
    for (DrillRep rep : reps) {
      if (rep.isAbandoned() || rep.isPractice()) {
        continue;
      }
      long time = DrillRepOrder.timeMs(rep, key);
      total += time;
      recognition += rep.getRecognitionMs();
      best = Math.min(best, time);
      timed++;
    }
    if (timed == 0) {
      return; // the layout's dashes stand until there is a figure
    }
    FormatterService format = FormatterService.INSTANCE;
    ((TextView) activity.findViewById(R.id.tvDrillLiveMean)).setText(
        format.formatSolveTime(total / timed));
    ((TextView) activity.findViewById(R.id.tvDrillLiveRecognition)).setText(
        format.formatSolveTime(recognition / timed));
    TextView bestView = activity.findViewById(R.id.tvDrillLiveBest);
    bestView.setText(format.formatSolveTime(best));
    bestView.setTextColor(ContextCompat.getColor(activity, R.color.green_soft));
  }
}
