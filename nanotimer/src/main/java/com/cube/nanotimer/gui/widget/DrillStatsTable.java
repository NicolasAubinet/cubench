package com.cube.nanotimer.gui.widget;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.cube.nanotimer.R;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.helper.TimeColorScale;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.util.view.StepPalette;
import com.cube.nanotimer.vo.drill.DrillCaseStats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every case that has been drilled in a window, one line each: how often it came up, what it
 * averaged, its best, and how far it swings around that average.
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
 * <p>Each column is coloured on its own green to red gradient, since a best and a spread are
 * different sizes of number and one scale over the three would paint a column green and another red
 * for no reason but that. The ends are the window's own fastest and slowest rather than percentiles:
 * a set of cases is dozens of lines, and trimming outliers out of dozens leaves the worst case,
 * which is the one being hunted, looking ordinary.
 *
 * <p>The third column is the spread and not the worst attempt, the same column and the same reading
 * as the Analysis step table's: a maximum is owned by one rep and never improves, and two tables a
 * reader crosses between must not head one column two ways.
 */
public class DrillStatsTable {

  /** Told which case the reader wants every attempt at. */
  public interface Listener {
    void onCasePicked(String caseCode);
  }

  /** Where the table opens: the mean, slowest first, which is the case to work on next. */
  private static final int DEFAULT_COLUMN = 1;

  private static final int[] HEADING_LABELS = {R.string.drill_stats_column_count,
      R.string.drill_summary_cell_mean, R.string.drill_summary_cell_best,
      R.string.drill_stats_column_spread};
  // Every column opens at its own interesting end, which for a count is the case drilled most, for
  // a time the slowest and for the spread the widest.
  private static final boolean[] OPENS_DESCENDING = {true, true, true, true};

  private final FragmentActivity activity;
  private final LinearLayout rows;
  private final Listener listener;
  private final List<DrillCaseStats> stats = new ArrayList<DrillCaseStats>();
  private final Map<DrillCaseStats, CaseRow> lines = new LinkedHashMap<DrillCaseStats, CaseRow>();
  private final TimeColorScale[] scales = new TimeColorScale[CaseRow.COLUMNS];
  private final StepPalette palette;
  private final CaseTableHeadings headings;

  /** Binds the headings. The table stands empty until it is given a window's cases. */
  public DrillStatsTable(FragmentActivity activity, Listener listener) {
    this.activity = activity;
    this.listener = listener;
    this.rows = activity.findViewById(R.id.llCaseTableRows);
    this.palette = StepPalette.cfop(activity);
    this.headings = new CaseTableHeadings(activity.findViewById(R.id.llDrillStatsSection),
        HEADING_LABELS, OPENS_DESCENDING, DEFAULT_COLUMN, new CaseTableHeadings.Listener() {
          @Override
          public void onRanked(int column, boolean descending) {
            refresh();
          }
        });
    this.headings.setLabel(R.string.drill_summary_cases);
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
      View line = CaseRow.inflate(inflater, rows);
      line.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          listener.onCasePicked(caseStats.getCaseCode());
        }
      });
      CaseRow row = new CaseRow(line);
      fill(row, caseStats);
      lines.put(caseStats, row);
    }
    refresh();
  }

  /** One gradient per column, over the cases the table is showing. */
  private void buildScales() {
    for (int column = 0; column < CaseRow.COLUMNS; column++) {
      List<Long> times = new ArrayList<Long>();
      for (DrillCaseStats caseStats : stats) {
        if (quotable(caseStats, column + 1)) {
          times.add(value(caseStats, column + 1));
        }
      }
      scales[column] = new TimeColorScale(activity);
      scales[column].setTimes(times, false);
    }
  }

  /** Ranks the table as it now stands, draws it, and says on the columns which ranking that is. */
  private void refresh() {
    final int sortedColumn = headings.column();
    final boolean slowestFirst = headings.descending();
    Collections.sort(stats, new Comparator<DrillCaseStats>() {
      @Override
      public int compare(DrillCaseStats a, DrillCaseStats b) {
        // A case that cannot quote the ranked column sits under the ones that can, whichever way
        // round the column is turned: it is not a steady case, it is one nothing can be said about.
        boolean quotableA = quotable(a, sortedColumn);
        boolean quotableB = quotable(b, sortedColumn);
        if (quotableA != quotableB) {
          return quotableA ? -1 : 1;
        }
        int order = Long.compare(value(a, sortedColumn), value(b, sortedColumn));
        // Cases that tie stay in the order the codes are in, which is the order they are learnt in.
        return order != 0 ? (slowestFirst ? -order : order)
            : a.getCaseCode().compareTo(b.getCaseCode());
      }
    });

    rows.removeAllViews();
    for (DrillCaseStats caseStats : stats) {
      CaseRow row = lines.get(caseStats);
      row.rank(sortedColumn);
      rows.addView(row.view());
    }
    headings.refresh();
  }

  private void fill(CaseRow row, DrillCaseStats caseStats) {
    row.count(String.valueOf(caseStats.getCount()))
        .chart(caseStats.getCaseCode())
        .name(Utils.toSmartCubeCaseHeadline(activity, caseStats.getCaseCode()))
        // One hue for the whole meter, so the line says which family it is even scrolled away from
        // the family it was picked under.
        .meter(caseStats.getMeanRecognitionMs(), caseStats.getMeanExecutionMs(),
            palette.colorFor(caseStats.getCaseCode()));

    for (int column = 0; column < CaseRow.COLUMNS; column++) {
      long time = value(caseStats, column + 1);
      if (quotable(caseStats, column + 1)) {
        row.value(column, FormatterService.INSTANCE.formatSolveTime(time),
            scales[column].colorFor(time, false));
      } else {
        row.value(column, activity.getString(R.string.NA),
            ContextCompat.getColor(activity, R.color.secondary_text));
      }
    }
  }

  /**
   * Whether a case can quote a column at all. A spread needs two reps to exist: one rep would read
   * 0.00, which is the best figure in the column and paints green, for a case nobody has measured.
   */
  private static boolean quotable(DrillCaseStats caseStats, int column) {
    return column != 3 || caseStats.getCount() >= 2;
  }

  /** What a column holds for a case, with the count read as a figure like the rest. */
  private static long value(DrillCaseStats caseStats, int column) {
    switch (column) {
      case 1:
        return caseStats.getMeanMs();
      case 2:
        return caseStats.getBestMs();
      case 3:
        return caseStats.getStdDevMs();
      default:
        return caseStats.getCount();
    }
  }
}
