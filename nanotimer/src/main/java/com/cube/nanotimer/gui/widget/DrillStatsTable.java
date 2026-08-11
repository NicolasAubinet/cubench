package com.cube.nanotimer.gui.widget;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.cube.nanotimer.R;
import com.cube.nanotimer.smartcube.step.LastLayerDiagram;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.helper.TimeColorScale;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.util.view.DrillSplitBarView;
import com.cube.nanotimer.vo.drill.DrillCaseStats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every case that has been drilled in a window, one line each: how often it came up, what it
 * averaged, and the two ends it swung between.
 *
 * <p>A line is the case and not the rep, which is the whole difference between this table and the
 * one a finished drill ends on. There a case dealt four times is four attempts and the reader is
 * looking for the one that went wrong; here it is one case with four reps behind it and the reader
 * is looking for the case to work on next. The reps are still reachable, by tapping the line.
 *
 * <p><b>The column headings are the sort</b>, as they are on a finished drill, and for the same
 * reason: a control above the table would have had to name the same four things a second time. The
 * ranked column stands at full strength and the others fall back, so the ranking is legible in the
 * figures and not only in the heading. Every column opens at its own interesting end, which for a
 * count is the case drilled most and for a time is the slowest, and tapping it again turns it round.
 *
 * <p>Under each name is where that case's time goes, its looking against its turning, written as the
 * two figures with the bar they make between them. The columns say how long a case takes; this is
 * the only thing that says which half of it is the problem, and it is the reason a case can be read
 * here at all rather than only compared.
 *
 * <p>Each column is coloured on its own green to red gradient, since a best and a worst are
 * different sizes of number and one scale over the three would paint a column green and another red
 * for no reason but that. The ends are the window's own fastest and slowest rather than percentiles:
 * a set of cases is dozens of lines, and trimming outliers out of dozens leaves the worst case,
 * which is the one being hunted, looking ordinary.
 */
public class DrillStatsTable {

  /** Told which case the reader wants every attempt at. */
  public interface Listener {
    void onCasePicked(String caseCode);
  }

  /** Ranks by how often the case came up rather than by what it cost. */
  private static final int COUNT_COLUMN = 0;
  /** Where the table opens: the mean, slowest first, which is the case to work on next. */
  private static final int DEFAULT_COLUMN = 1;

  private static final int[] HEADING_IDS = {R.id.tvDrillStatsSortCount, R.id.tvDrillStatsSortMean,
      R.id.tvDrillStatsSortBest, R.id.tvDrillStatsSortWorst};
  private static final int[] HEADING_LABELS = {R.string.drill_stats_column_count,
      R.string.drill_summary_cell_mean, R.string.drill_summary_cell_best,
      R.string.drill_stats_column_worst};
  private static final int[] VALUE_IDS = {R.id.tvDrillStatsMean, R.id.tvDrillStatsBest,
      R.id.tvDrillStatsWorst};

  /** Which way round the table is, said on the ranked heading. */
  private static final String SLOWEST_FIRST = "▾";
  private static final String QUICKEST_FIRST = "▴";

  /** What a column that is not the ranked one is worth: still coloured, but standing back. */
  private static final float UNRANKED_ALPHA = 0.6f;

  private final FragmentActivity activity;
  private final LinearLayout rows;
  private final Listener listener;
  private final List<DrillCaseStats> stats = new ArrayList<DrillCaseStats>();
  private final Map<DrillCaseStats, View> lines = new LinkedHashMap<DrillCaseStats, View>();
  private final TimeColorScale[] scales = new TimeColorScale[VALUE_IDS.length];

  private int sortedColumn = DEFAULT_COLUMN;
  private boolean slowestFirst = true;

  /** Binds the headings. The table stands empty until it is given a window's cases. */
  public DrillStatsTable(FragmentActivity activity, Listener listener) {
    this.activity = activity;
    this.listener = listener;
    this.rows = activity.findViewById(R.id.llDrillStatsRows);
    for (int column = 0; column < HEADING_IDS.length; column++) {
      final int picked = column;
      activity.findViewById(HEADING_IDS[column]).setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          // The same heading again turns the table round; a fresh one opens at its biggest, which
          // is the case drilled most for the count and the slowest case for a time.
          slowestFirst = sortedColumn != picked || !slowestFirst;
          sortedColumn = picked;
          refresh();
        }
      });
    }
  }

  /** Shows a window's cases, keeping whatever ranking the reader had put the table in. */
  public void setStats(List<DrillCaseStats> windowCases) {
    stats.clear();
    lines.clear();
    if (windowCases != null) {
      stats.addAll(windowCases);
    }
    buildScales();
    // Drawn once per window: ranking the table again reorders these lines rather than rebuilding
    // them, and a family is up to 57 of them.
    LayoutInflater inflater = LayoutInflater.from(activity);
    for (final DrillCaseStats caseStats : stats) {
      View line = inflater.inflate(R.layout.drill_stats_row, rows, false);
      line.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          listener.onCasePicked(caseStats.getCaseCode());
        }
      });
      fill(line, caseStats);
      lines.put(caseStats, line);
    }
    refresh();
  }

  /** One gradient per column, over the cases the table is showing. */
  private void buildScales() {
    for (int column = 0; column < VALUE_IDS.length; column++) {
      List<Long> times = new ArrayList<Long>();
      for (DrillCaseStats caseStats : stats) {
        times.add(value(caseStats, column + 1));
      }
      scales[column] = new TimeColorScale(activity);
      scales[column].setTimes(times, false);
    }
  }

  /** Ranks the table as it now stands, draws it, and says on the columns which ranking that is. */
  private void refresh() {
    Collections.sort(stats, new Comparator<DrillCaseStats>() {
      @Override
      public int compare(DrillCaseStats a, DrillCaseStats b) {
        int order = Long.compare(value(a, sortedColumn), value(b, sortedColumn));
        // Cases that tie stay in the order the codes are in, which is the order they are learnt in.
        return order != 0 ? (slowestFirst ? -order : order)
            : a.getCaseCode().compareTo(b.getCaseCode());
      }
    });

    rows.removeAllViews();
    for (DrillCaseStats caseStats : stats) {
      View line = lines.get(caseStats);
      // The ranked column at full strength and the others standing back, since the colours are the
      // same gradient throughout and something has to say which one the list is in.
      line.findViewById(R.id.tvDrillStatsCount)
          .setAlpha(sortedColumn == COUNT_COLUMN ? 1f : UNRANKED_ALPHA);
      for (int column = 0; column < VALUE_IDS.length; column++) {
        line.findViewById(VALUE_IDS[column])
            .setAlpha(column + 1 == sortedColumn ? 1f : UNRANKED_ALPHA);
      }
      rows.addView(line);
    }

    for (int column = 0; column < HEADING_IDS.length; column++) {
      TextView heading = activity.findViewById(HEADING_IDS[column]);
      String label = activity.getString(HEADING_LABELS[column]);
      boolean ranked = column == sortedColumn;
      heading.setText(ranked ? activity.getString(R.string.drill_summary_column_sorted, label,
          slowestFirst ? SLOWEST_FIRST : QUICKEST_FIRST) : label);
      heading.setTextColor(color(ranked ? R.color.white : R.color.secondary_text));
    }
  }

  private void fill(View line, DrillCaseStats caseStats) {
    ((LastLayerCaseView) line.findViewById(R.id.vDrillStatsChart))
        .setDiagram(LastLayerDiagram.forCase(caseStats.getCaseCode()));
    ((TextView) line.findViewById(R.id.tvDrillStatsName))
        .setText(Utils.toSmartCubeCaseHeadline(activity, caseStats.getCaseCode()));

    ((TextView) line.findViewById(R.id.tvDrillStatsCount))
        .setText(String.valueOf(caseStats.getCount()));

    for (int column = 0; column < VALUE_IDS.length; column++) {
      TextView value = line.findViewById(VALUE_IDS[column]);
      long time = value(caseStats, column + 1);
      value.setText(FormatterService.INSTANCE.formatSolveTime(time));
      value.setTextColor(scales[column].colorFor(time, false));
    }

    ((TextView) line.findViewById(R.id.tvDrillStatsRecognition))
        .setText(FormatterService.INSTANCE.formatSolveTime(caseStats.getMeanRecognitionMs()));
    ((TextView) line.findViewById(R.id.tvDrillStatsExecution))
        .setText(FormatterService.INSTANCE.formatSolveTime(caseStats.getMeanExecutionMs()));
    ((DrillSplitBarView) line.findViewById(R.id.vDrillStatsBar))
        .setSplit(caseStats.getMeanRecognitionMs(), caseStats.getMeanExecutionMs());
  }

  /** What a column holds for a case, with the count read as a figure like the rest. */
  private static long value(DrillCaseStats caseStats, int column) {
    switch (column) {
      case 1:
        return caseStats.getMeanMs();
      case 2:
        return caseStats.getBestMs();
      case 3:
        return caseStats.getWorstMs();
      default:
        return caseStats.getCount();
    }
  }

  private int color(int colorResId) {
    return ContextCompat.getColor(activity, colorResId);
  }
}
