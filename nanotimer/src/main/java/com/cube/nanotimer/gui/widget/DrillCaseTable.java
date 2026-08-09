package com.cube.nanotimer.gui.widget;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.cube.nanotimer.R;
import com.cube.nanotimer.gui.widget.dialog.CaseAlgorithmsDialog;
import com.cube.nanotimer.smartcube.drill.DrillRep;
import com.cube.nanotimer.smartcube.drill.DrillRepOrder;
import com.cube.nanotimer.smartcube.drill.DrillSpec;
import com.cube.nanotimer.smartcube.step.LastLayerDiagram;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.helper.Utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A finished drill, rep by rep: one line per attempt with its case chart, the case's name and what
 * the attempt cost in recognition, execution and both together.
 *
 * <p>A line is the rep and not the case, so a case that came up four times has four lines. Averaging
 * them into one hid the two figures a drill is read for, which are the best of them and the worst,
 * and those are the whole reason for looking a case up again.
 *
 * <p><b>The column headings are the sort.</b> Tapping one ranks the table by that column, tapping it
 * again turns the table round, and the ranked column is the one written in white so the ranking is
 * legible in the figures and not only in the heading. A control above the table would have had to
 * name the same three columns a second time.
 *
 * <p>It opens on the half the drill was scored on, slowest first, because the rep that cost the most
 * is the one worth doing something about. Tapping a line opens that case's algorithms, which is
 * usually what a slow line is asking about.
 */
public class DrillCaseTable {

  private static final DrillRepOrder.Key[] KEYS = {
      DrillRepOrder.Key.RECOGNITION, DrillRepOrder.Key.EXECUTION, DrillRepOrder.Key.TOTAL};
  private static final int[] HEADING_IDS = {
      R.id.tvDrillSortRecognition, R.id.tvDrillSortExecution, R.id.tvDrillSortTotal};
  private static final int[] HEADING_LABELS = {R.string.drill_summary_column_recognition,
      R.string.drill_summary_column_execution, R.string.drill_summary_column_total};
  private static final int[] VALUE_IDS = {
      R.id.tvDrillCaseRecognition, R.id.tvDrillCaseExecution, R.id.tvDrillCaseTotal};

  /** Which way round the table is, said on the ranked heading. */
  private static final String SLOWEST_FIRST = "▾";
  private static final String QUICKEST_FIRST = "▴";

  private final FragmentActivity activity;
  private final LinearLayout rows;
  private final List<DrillRep> reps;
  private final Map<DrillRep, View> lines = new LinkedHashMap<DrillRep, View>();

  private int sortedColumn;
  private boolean slowestFirst = true;

  /**
   * Fills the case table of a finished drill.
   *
   * @param reps the drill's reps, skipped ones included
   * @param type what the drill was scored on, which is the column it opens sorted by
   */
  public DrillCaseTable(FragmentActivity activity, List<DrillRep> reps, DrillSpec.Type type) {
    this.activity = activity;
    this.rows = activity.findViewById(R.id.llDrillCaseRows);
    this.reps = new ArrayList<DrillRep>(reps);
    this.sortedColumn = type == DrillSpec.Type.CASE_RECOGNITION ? 0 : 1;

    LayoutInflater inflater = LayoutInflater.from(activity);
    for (final DrillRep rep : this.reps) {
      View line = inflater.inflate(R.layout.drill_case_row, rows, false);
      fill(line, rep);
      line.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          DialogUtils.showFragment(activity, CaseAlgorithmsDialog.newInstance(rep.getCaseCode()));
        }
      });
      lines.put(rep, line);
    }
    for (int column = 0; column < HEADING_IDS.length; column++) {
      final int picked = column;
      activity.findViewById(HEADING_IDS[column]).setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          // The same heading again turns the table round, since finding the quickest rep is the
          // other half of the question and it is the same three columns either way.
          slowestFirst = sortedColumn == picked ? !slowestFirst : true;
          sortedColumn = picked;
          refresh();
        }
      });
    }
    activity.findViewById(R.id.llDrillCasesSection)
        .setVisibility(this.reps.isEmpty() ? View.GONE : View.VISIBLE);
    refresh();
  }

  /** Ranks the table as it now stands, and says on the columns which ranking that is. */
  private void refresh() {
    DrillRepOrder.sort(reps, KEYS[sortedColumn], slowestFirst);
    rows.removeAllViews();
    for (DrillRep rep : reps) {
      View line = lines.get(rep);
      for (int column = 0; column < VALUE_IDS.length; column++) {
        ((TextView) line.findViewById(VALUE_IDS[column])).setTextColor(
            color(column == sortedColumn ? R.color.white : R.color.secondary_text));
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

  private void fill(View line, DrillRep rep) {
    String code = rep.getCaseCode();
    ((LastLayerCaseView) line.findViewById(R.id.vDrillCaseChart))
        .setDiagram(LastLayerDiagram.forCase(code));
    ((TextView) line.findViewById(R.id.tvDrillCaseName))
        .setText(Utils.toSmartCubeCaseHeadline(activity, code));

    for (int column = 0; column < VALUE_IDS.length; column++) {
      TextView value = line.findViewById(VALUE_IDS[column]);
      // A rep that was given up on has no time worth printing: the seconds before giving up say
      // nothing about the case, which is also why it is ranked nowhere.
      value.setText(rep.isAbandoned() ? activity.getString(R.string.drill_case_no_time)
          : FormatterService.INSTANCE.formatSolveTime(DrillRepOrder.timeMs(rep, KEYS[column])));
    }

    TextView note = line.findViewById(R.id.tvDrillCaseNote);
    String text = note(rep);
    note.setText(text);
    note.setVisibility(text == null ? View.GONE : View.VISIBLE);
  }

  /** What has to be said about a rep before its figures are read, or null when nothing has. */
  private String note(DrillRep rep) {
    List<String> parts = new ArrayList<String>();
    if (rep.isAbandoned()) {
      parts.add(activity.getString(R.string.drill_rep_skipped));
    }
    // A time reached by looking the algorithm up is real but is not a measure of knowing the case,
    // which is the whole of what the table is read for.
    if (rep.wasRevealed()) {
      parts.add(activity.getString(R.string.drill_case_revealed));
    }
    if (parts.isEmpty()) {
      return null;
    }
    String separator = activity.getString(R.string.drill_case_note_separator);
    StringBuilder sb = new StringBuilder();
    for (String part : parts) {
      sb.append(sb.length() == 0 ? "" : separator).append(part);
    }
    return sb.toString();
  }

  private int color(int colorResId) {
    return ContextCompat.getColor(activity, colorResId);
  }
}
