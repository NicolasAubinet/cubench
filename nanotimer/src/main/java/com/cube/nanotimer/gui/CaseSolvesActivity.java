package com.cube.nanotimer.gui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import com.cube.nanotimer.App;
import com.cube.nanotimer.R;
import com.cube.nanotimer.gui.widget.CaseTableHeadings;
import com.cube.nanotimer.gui.widget.DrillStatCells;
import com.cube.nanotimer.gui.widget.HistoryDetailDialog;
import com.cube.nanotimer.gui.widget.LastLayerCaseView;
import com.cube.nanotimer.gui.widget.PairCaseView;
import com.cube.nanotimer.gui.widget.SolveNavigator;
import com.cube.nanotimer.gui.widget.TimeChangedHandler;
import com.cube.nanotimer.gui.widget.cases.CaseSheet;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.smartcube.step.CaseAlgorithms;
import com.cube.nanotimer.smartcube.step.LastLayerDiagram;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.helper.TimeColorScale;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.util.view.DrillSplitBarView;
import com.cube.nanotimer.util.view.StepPalette;
import com.cube.nanotimer.vo.CaseSolve;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One case, and the times in solves the case sheet's In solves card is the mean of: the same rows,
 * of every solve type, so the card and the list can never disagree. Each row opens its solve as the
 * history does, and the sheet can be swiped along the list.
 *
 * <p>Nothing is thrown out here, unlike a drill rep: a solve is removed from its own sheet, and the
 * list is read again whenever a solve opened from it changes.
 */
public class CaseSolvesActivity extends NanoTimerActivity
    implements TimeChangedHandler, SolveNavigator {

  public static final String EXTRA_CASE = "caseSolvesCase";
  /** How many of the case's latest times are listed: the In solves card's own window. */
  public static final String EXTRA_LAST = "caseSolvesLast";

  private static final int[] VALUE_IDS = {R.id.tvDrillAttemptRecognition,
      R.id.tvDrillAttemptExecution, R.id.tvDrillAttemptTotal};
  private static final int[] HEADING_LABELS = {0, R.string.drill_summary_column_recognition,
      R.string.drill_summary_column_execution, R.string.drill_summary_column_total};
  private static final boolean[] OPENS_DESCENDING = {false, true, true, true};

  private String caseCode;
  private int last;
  private LinearLayout rows;
  private CaseTableHeadings headings;
  private final List<CaseSolve> solves = new ArrayList<CaseSolve>();
  private final TimeColorScale[] scales = new TimeColorScale[VALUE_IDS.length];
  private int hue;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.case_solves);
    setTitle(R.string.case_sheet_in_solves);

    caseCode = getIntent().getStringExtra(EXTRA_CASE);
    last = getIntent().getIntExtra(EXTRA_LAST, 50);
    hue = StepPalette.cfop(this).colorFor(caseCode);
    rows = findViewById(R.id.llCaseSolvesRows);

    showCase();
    findViewById(R.id.llCaseSolvesHeader).setOnClickListener(
        v -> DialogUtils.showFragment(this, CaseSheet.newInstance(caseCode)));
    headings = new CaseTableHeadings(findViewById(R.id.llCaseSolvesSection), HEADING_LABELS,
        OPENS_DESCENDING, CaseTableHeadings.LABEL_COLUMN, (column, descending) -> order());
    headings.setLabel(R.string.case_solves_label);
    headings.rankableLabel(true); // newest first
    headings.setAccent(hue);
    load();
  }

  private void showCase() {
    ((TextView) findViewById(R.id.tvCaseSolvesName))
        .setText(Utils.toSmartCubeCaseHeadline(this, caseCode));
    LastLayerCaseView chart = findViewById(R.id.vCaseSolvesChart);
    if (CaseAlgorithms.isPair(caseCode)) {
      chart.setVisibility(View.GONE);
      String picture = CaseAlgorithms.pairPicture(caseCode);
      if (picture != null) {
        PairCaseView pair = findViewById(R.id.vCaseSolvesPair);
        pair.setVisibility(View.VISIBLE);
        pair.setPicture(picture);
      }
    } else {
      chart.setDiagram(LastLayerDiagram.forCase(caseCode));
    }
  }

  private void load() {
    App.INSTANCE.getService().getCaseSolveList(caseCode, last,
        new DataCallback<List<CaseSolve>>() {
          @Override
          public void onData(final List<CaseSolve> data) {
            runOnUiThread(() -> {
              if (isFinishing()) {
                return;
              }
              solves.clear();
              solves.addAll(data);
              show();
            });
          }
        });
  }

  private void show() {
    boolean anything = !solves.isEmpty();
    findViewById(R.id.llCaseSolvesSection).setVisibility(anything ? View.VISIBLE : View.GONE);
    findViewById(R.id.llDrillSummaryCells).setVisibility(anything ? View.VISIBLE : View.GONE);
    TextView empty = findViewById(R.id.tvCaseSolvesEmpty);
    empty.setVisibility(anything ? View.GONE : View.VISIBLE);
    empty.setText(R.string.case_solves_empty);

    Set<Integer> types = new HashSet<Integer>();
    for (CaseSolve one : solves) {
      types.add(one.getSolve().getSolveType().getId());
    }
    TextView window = findViewById(R.id.tvCaseSolvesWindow);
    window.setText(types.size() > 1
        ? getResources().getQuantityString(R.plurals.case_solves_window_types, types.size(),
            types.size())
        : solves.isEmpty() ? "" : solves.get(0).getSolve().getSolveType().getName());

    rows.removeAllViews();
    if (!anything) {
      return;
    }
    buildScales();
    showFigures();
    LayoutInflater inflater = LayoutInflater.from(this);
    for (final CaseSolve one : solves) {
      View line = inflater.inflate(R.layout.drill_case_attempt_row, rows, false);
      ((TextView) line.findViewById(R.id.tvDrillAttemptTotal))
          .setTypeface(ResourcesCompat.getFont(this, R.font.roboto_bold));
      line.setTag(one);
      line.setOnClickListener(v -> open(one.getSolve()));
      fill(line, one, types.size() > 1);
      rows.addView(line);
    }
    order();
  }

  /** Puts the lines in the order the headings ask for. */
  private void order() {
    final int column = headings.column();
    final boolean descending = headings.descending();
    List<View> lines = new ArrayList<View>();
    for (int i = 0; i < rows.getChildCount(); i++) {
      lines.add(rows.getChildAt(i));
    }
    Collections.sort(lines, (a, b) -> {
      CaseSolve one = (CaseSolve) a.getTag();
      CaseSolve other = (CaseSolve) b.getTag();
      int order = column == CaseTableHeadings.LABEL_COLUMN
          ? Long.compare(one.getSolve().getTimestamp(), other.getSolve().getTimestamp())
          : Long.compare(value(one, column - 1), value(other, column - 1));
      return descending ? -order : order;
    });
    rows.removeAllViews();
    for (View line : lines) {
      rows.addView(line);
    }
    headings.refresh();
  }

  private void buildScales() {
    for (int column = 0; column < VALUE_IDS.length; column++) {
      List<Long> times = new ArrayList<Long>();
      for (CaseSolve one : solves) {
        times.add(value(one, column));
      }
      scales[column] = new TimeColorScale(this);
      scales[column].setTimes(times, false);
    }
  }

  /** The drill screens' three cells, over the same rows as the In solves card. */
  private void showFigures() {
    long total = 0;
    long recognition = 0;
    long best = Long.MAX_VALUE;
    long worst = 0;
    for (CaseSolve one : solves) {
      total += one.getTotalMs();
      recognition += one.getRecognitionMs();
      best = Math.min(best, one.getTotalMs());
      worst = Math.max(worst, one.getTotalMs());
    }
    int count = solves.size();
    FormatterService format = FormatterService.INSTANCE;

    setCell(R.id.tvDrillCellKeyOne, R.id.tvDrillCellValueOne, R.id.tvDrillCellSubOne,
        getString(R.string.case_solves_cell_count), String.valueOf(count), "");
    ((TextView) findViewById(R.id.tvDrillCellKeyTwo)).setText(R.string.drill_summary_cell_mean);
    DrillStatCells.nameHalves(this, hue);
    ((TextView) findViewById(R.id.tvDrillMeanRecognition))
        .setText(format.formatSolveTime(recognition / count));
    ((TextView) findViewById(R.id.tvDrillMeanExecution))
        .setText(format.formatSolveTime((total - recognition) / count));
    setCell(R.id.tvDrillCellKeyThree, R.id.tvDrillCellValueThree, R.id.tvDrillCellSubThree,
        getString(R.string.drill_summary_cell_best), format.formatSolveTime(best),
        getString(R.string.drill_case_stats_cell_worst, format.formatSolveTime(worst)));
  }

  /** @param named whether the list spans solve types, so each row says which one it is from */
  private void fill(View line, CaseSolve one, boolean named) {
    SolveTime solve = one.getSolve();
    FormatterService format = FormatterService.INSTANCE;
    ((TextView) line.findViewById(R.id.tvDrillAttemptWhen))
        .setText(format.formatSessionStart(solve.getTimestamp()));
    for (int column = 0; column < VALUE_IDS.length; column++) {
      TextView value = line.findViewById(VALUE_IDS[column]);
      long time = value(one, column);
      value.setText(format.formatSolveTime(time));
      value.setTextColor(scales[column].colorFor(time, false));
    }
    ((DrillSplitBarView) line.findViewById(R.id.vDrillAttemptBar))
        .setSplit(one.getRecognitionMs(), one.getExecutionMs(), hue);

    TextView note = line.findViewById(R.id.tvDrillAttemptNote);
    String time = format.formatSolveTime(solve);
    note.setText(named ? getString(R.string.case_solves_note_type, time,
        solve.getSolveType().getName()) : getString(R.string.case_solves_note, time));
    note.setVisibility(View.VISIBLE);
  }

  private void open(SolveTime solve) {
    DialogUtils.showFragment(this, HistoryDetailDialog.newInstance(solve,
        CubeType.getCubeType(solve.getSolveType().getCubeTypeId()), this, this));
  }

  /** A solve opened from here was changed: a DNF leaves the list, a +2 does not move the case. */
  @Override
  public void onTimeChanged(SolveTime solveTime) {
    load();
  }

  @Override
  public void onTimeDeleted(SolveTime solveTime) {
    load();
  }

  @Override
  public void onBlindMethodChanged(SolveType solveType) {
    // no blind solve reads a case
  }

  /** The solve next to this one in the list as it is ordered now, the same solve's twin skipped. */
  @Override
  public SolveTime getNeighbourSolve(SolveTime solveTime, int direction) {
    int at = -1;
    for (int i = 0; i < rows.getChildCount(); i++) {
      if (((CaseSolve) rows.getChildAt(i).getTag()).getSolve().getId() == solveTime.getId()) {
        at = i;
        if (direction < 0) {
          break; // the first of a pair's two rows, so going up leaves both behind
        }
      }
    }
    int next = at + direction;
    return at < 0 || next < 0 || next >= rows.getChildCount() ? null
        : ((CaseSolve) rows.getChildAt(next).getTag()).getSolve();
  }

  private static long value(CaseSolve one, int column) {
    switch (column) {
      case 0:
        return one.getRecognitionMs();
      case 1:
        return one.getExecutionMs();
      default:
        return one.getTotalMs();
    }
  }

  private void setCell(int keyId, int valueId, int subId, String key, String value, String sub) {
    ((TextView) findViewById(keyId)).setText(key);
    ((TextView) findViewById(valueId)).setText(value);
    ((TextView) findViewById(subId)).setText(sub);
  }
}
