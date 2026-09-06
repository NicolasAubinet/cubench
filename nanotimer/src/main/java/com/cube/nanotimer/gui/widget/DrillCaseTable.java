package com.cube.nanotimer.gui.widget;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.cube.nanotimer.R;
import com.cube.nanotimer.gui.widget.dialog.CaseAlgorithmsDialog;
import com.cube.nanotimer.smartcube.drill.DrillRep;
import com.cube.nanotimer.smartcube.drill.DrillRepOrder;
import com.cube.nanotimer.drill.DrillSpec;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.YesNoListener;
import com.cube.nanotimer.util.view.StepPalette;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.helper.TimeColorScale;
import com.cube.nanotimer.util.helper.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A finished drill, rep by rep: one line per attempt with its number in the drill, its case chart,
 * the case's name and what the attempt cost in recognition, execution and both together.
 *
 * <p>A line is the rep and not the case, so a case that came up four times has four lines. Averaging
 * them into one hid the two figures a drill is read for, which are the best of them and the worst,
 * and those are the whole reason for looking a case up again.
 *
 * <p><b>The column headings are the sort.</b> Tapping one ranks the table by that column, tapping it
 * again turns the table round, and the ranked column is the one at full strength so the ranking is
 * legible in the figures and not only in the heading. A control above the table would have had to
 * name the same three columns a second time. The <b>#</b> column ranks by the order the reps were
 * dealt in, which is the one a reader who remembers only that something went wrong near the end can
 * search; unlike the others it opens counting up, a numbered column that starts at the bottom
 * reading as a fault rather than a choice.
 *
 * <p>It opens on the half the drill was scored on, slowest first, because the rep that cost the most
 * is the one worth doing something about. Tapping a line opens that case's algorithms, which is
 * usually what a slow line is asking about.
 *
 * <p><b>A rep can be thrown out, by holding its line.</b> A rep where the cube was knocked, or where
 * the user stopped half way through, is not a measurement, and left in it fakes the figures it is
 * averaged into. A thrown-out rep keeps its line, struck through, and is counted by nothing: the
 * mean, the best, the reps figure and the colour scales are all recomputed without it. Tapping it
 * puts it back, which is why it is not simply removed, and the stored row survives too so that a
 * coach reading these later can still see what was pruned.
 *
 * <p>Under each name is the shape of that rep, its looking against its turning, in the two colours
 * the mean cell writes its own halves in. The three figures say how long a rep was; the bar is the
 * only thing that says where the time went without the reader working it out.
 *
 * <p>Every figure is written on the history screen's own green to red gradient, and each column
 * gets a scale of its own: recognition and execution are different sizes of number, so one scale
 * over all three would have painted a whole column green and another red for no reason but that.
 * The ends are the drill's own fastest and slowest rather than percentiles, since a drill is twenty
 * reps and trimming outliers out of twenty leaves the best of them looking ordinary.
 */
public class DrillCaseTable {

  /** What the screen around the table has to do when the reps stop being all of them. */
  public interface Listener {
    /**
     * A rep was thrown out or put back.
     *
     * @param position where it fell in the drill, which is what it is stored under
     * @param counted the reps that still count, for the figures above the table
     */
    void onRepsPruned(int position, boolean deleted, List<DrillRep> counted);
  }

  /** Ranks by the order the reps were dealt in rather than by any of their figures. */
  private static final int POSITION_COLUMN = 0;

  private static final DrillRepOrder.Key[] KEYS = {
      DrillRepOrder.Key.RECOGNITION, DrillRepOrder.Key.EXECUTION, DrillRepOrder.Key.TOTAL};
  private static final int[] HEADING_LABELS = {R.string.drill_summary_column_position,
      R.string.drill_summary_column_recognition, R.string.drill_summary_column_execution,
      R.string.drill_summary_column_total};
  // The number column opens counting up, unlike the times: a numbered column that starts at the
  // bottom reads as a fault rather than as a choice.
  private static final boolean[] OPENS_DESCENDING = {false, true, true, true};

  /** A thrown-out line: still legible, and plainly not one of the reps being read. */
  private static final float DELETED_ALPHA = 0.45f;

  private final FragmentActivity activity;
  private final LinearLayout rows;
  private final List<DrillRep> reps;
  /** The reps in the order they were dealt, which is what the # column ranks by and prints. */
  private final List<DrillRep> dealt;
  private final Map<DrillRep, Integer> positions = new HashMap<DrillRep, Integer>();
  private final Set<DrillRep> deleted = new HashSet<DrillRep>();
  private final Map<DrillRep, CaseRow> lines = new LinkedHashMap<DrillRep, CaseRow>();
  private final TimeColorScale[] scales = new TimeColorScale[KEYS.length];
  private final StepPalette palette;
  private final Listener listener;

  private CaseTableHeadings headings;

  /**
   * Fills the case table of a finished drill.
   *
   * @param reps the drill's reps, skipped ones included, in the order they were dealt
   * @param type what the drill was scored on, which is the column it opens sorted by
   * @param listener told when a rep is thrown out or put back, or null for a table nobody prunes
   */
  public DrillCaseTable(FragmentActivity activity, List<DrillRep> reps, DrillSpec.Type type,
      Listener listener) {
    this.activity = activity;
    this.listener = listener;
    this.rows = activity.findViewById(R.id.llCaseTableRows);
    this.palette = StepPalette.cfop(activity);
    this.reps = new ArrayList<DrillRep>(reps);
    this.dealt = new ArrayList<DrillRep>(reps);
    for (int i = 0; i < dealt.size(); i++) {
      positions.put(dealt.get(i), i);
    }
    buildScales();

    LayoutInflater inflater = LayoutInflater.from(activity);
    for (final DrillRep rep : this.reps) {
      View line = CaseRow.inflate(inflater, rows);
      line.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          // A thrown-out line is put back by the same tap that would otherwise ask about the case.
          // Undoing is not destructive, so unlike throwing one out it asks nothing first.
          if (deleted.contains(rep)) {
            setDeleted(rep, false);
          } else {
            DialogUtils.showFragment(activity, CaseAlgorithmsDialog.newInstance(rep.getCaseCode()));
          }
        }
      });
      line.setOnLongClickListener(new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
          return askToDelete(rep);
        }
      });
      CaseRow row = new CaseRow(line);
      lines.put(rep, row);
      fill(row, rep);
    }
    headings = new CaseTableHeadings(activity.findViewById(R.id.llDrillCasesSection),
        HEADING_LABELS, OPENS_DESCENDING, type == DrillSpec.Type.CASE_RECOGNITION ? 1 : 2,
        new CaseTableHeadings.Listener() {
          @Override
          public void onRanked(int column, boolean descending) {
            refresh();
          }
        });
    headings.setLabel(R.string.drill_summary_cases);
    activity.findViewById(R.id.llDrillCasesSection)
        .setVisibility(this.reps.isEmpty() ? View.GONE : View.VISIBLE);
    refresh();
  }

  /** The reps that count: everything the user has not thrown out. */
  public List<DrillRep> counted() {
    List<DrillRep> counted = new ArrayList<DrillRep>();
    for (DrillRep rep : dealt) {
      if (!deleted.contains(rep)) {
        counted.add(rep);
      }
    }
    return counted;
  }

  /**
   * Asks before throwing a rep out, naming which one so that a line held by accident is not lost.
   * Putting one back is not destructive and asks nothing.
   */
  private boolean askToDelete(final DrillRep rep) {
    // A hold on a struck line puts it back, as a tap does. Said here rather than left to fall
    // through to the tap, since asking to throw out what is already out is what must not happen.
    if (deleted.contains(rep)) {
      setDeleted(rep, false);
      return true;
    }
    // Carries its unit here where it stands in a sentence, unlike in the table where the column
    // heading says what the figure is. A rep with no time says so instead, and takes no unit.
    String time = rep.isAbandoned() ? activity.getString(R.string.drill_rep_skipped)
        : activity.getString(R.string.drill_case_seconds,
            FormatterService.INSTANCE.formatSolveTime(rep.getTotalMs()));
    String message = activity.getString(R.string.drill_case_remove_message,
        Utils.toSmartCubeCaseHeadline(activity, rep.getCaseCode()), number(rep), time);
    DialogUtils.showDestructiveConfirmDialog(activity, R.string.drill_case_remove_title, message,
        R.string.drill_case_remove, R.string.cancel, new YesNoListener() {
          @Override
          public void onYes() {
            setDeleted(rep, true);
          }
        });
    return true;
  }

  /**
   * Throws a rep out of every figure, or puts it back. The scales are rebuilt with it: a rep thrown
   * out for being wild was also the end of the gradient every other rep was coloured against.
   */
  private void setDeleted(DrillRep rep, boolean gone) {
    if (gone) {
      deleted.add(rep);
    } else {
      deleted.remove(rep);
    }
    buildScales();
    for (Map.Entry<DrillRep, CaseRow> line : lines.entrySet()) {
      fill(line.getValue(), line.getKey());
    }
    refresh();
    if (listener != null) {
      listener.onRepsPruned(positions.get(rep), gone, counted());
    }
  }

  /** One gradient per column, over the reps that count and have a time to be ranked among. */
  private void buildScales() {
    for (int column = 0; column < KEYS.length; column++) {
      List<Long> times = new ArrayList<Long>();
      for (DrillRep rep : reps) {
        if (!rep.isAbandoned() && !deleted.contains(rep)) {
          times.add(DrillRepOrder.timeMs(rep, KEYS[column]));
        }
      }
      scales[column] = new TimeColorScale(activity);
      scales[column].setTimes(times, false);
    }
  }

  /** Ranks the table as it now stands, and says on the columns which ranking that is. */
  private void refresh() {
    int sortedColumn = headings.column();
    boolean slowestFirst = headings.descending();
    if (sortedColumn == POSITION_COLUMN) {
      reps.clear();
      reps.addAll(dealt);
      if (slowestFirst) {
        Collections.reverse(reps);
      }
    } else {
      DrillRepOrder.sort(reps, KEYS[sortedColumn - 1], slowestFirst);
    }
    rows.removeAllViews();
    for (DrillRep rep : reps) {
      CaseRow row = lines.get(rep);
      row.rank(sortedColumn);
      rows.addView(row.view());
    }
    headings.refresh();
  }

  private void fill(CaseRow row, DrillRep rep) {
    String code = rep.getCaseCode();
    boolean gone = deleted.contains(rep);
    row.count(String.valueOf(number(rep)))
        .chart(code)
        .name(Utils.toSmartCubeCaseHeadline(activity, code))
        .note(note(rep, gone))
        .struck(gone, DELETED_ALPHA);

    for (int column = 0; column < CaseRow.COLUMNS; column++) {
      // A rep given up on has no time worth printing, which is also why it is ranked nowhere. A
      // thrown-out one keeps its figures, being what it was judged on, but is out of the gradient.
      if (rep.isAbandoned()) {
        row.value(column, activity.getString(R.string.drill_case_no_time),
            color(R.color.secondary_text));
      } else {
        long time = DrillRepOrder.timeMs(rep, KEYS[column]);
        row.value(column, FormatterService.INSTANCE.formatSolveTime(time),
            gone ? color(R.color.secondary_text) : scales[column].colorFor(time, false));
      }
    }

    // Where the rep's time went. A rep with no time to divide has no shape, so it shows none.
    row.shape(rep.getRecognitionMs(), rep.getExecutionMs(), palette.colorFor(code))
        .hideBar(rep.isAbandoned());
  }

  /** Where the rep fell in the drill, counted as a person counts: the first one is 1. */
  private int number(DrillRep rep) {
    return positions.get(rep) + 1;
  }

  /** What has to be said about a rep before its figures are read, or null when nothing has. */
  private String note(DrillRep rep, boolean gone) {
    List<String> parts = new ArrayList<String>();
    // First, because it is the one that says the rest of the line is not being counted. It carries
    // the way back too: nothing else on screen says a struck line can be put back.
    if (gone) {
      parts.add(activity.getString(R.string.drill_case_removed));
    }
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
