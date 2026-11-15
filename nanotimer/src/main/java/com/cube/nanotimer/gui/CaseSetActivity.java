package com.cube.nanotimer.gui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.cube.nanotimer.App;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.cases.CaseGroup;
import com.cube.nanotimer.cases.CaseSet;
import com.cube.nanotimer.cases.CaseSets;
import com.cube.nanotimer.gui.widget.DrillStatsTable;
import com.cube.nanotimer.gui.widget.cases.CaseFilterBar;
import com.cube.nanotimer.gui.widget.cases.CaseSheet;
import com.cube.nanotimer.gui.widget.cases.CaseTile;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.session.CaseKnowledge;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.vo.CaseHistory;
import com.cube.nanotimer.vo.drill.DrillCaseStats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One set of the library: its cases as a grid of tiles, filtered by group and, once a smart cube
 * has read any of them, by standing. Sorted by the set's own order or by standing, it stays a grid;
 * sorted by a mean, it becomes the case table the drill screens read, since a grid has nowhere to
 * put a figure. A tap opens the case sheet, as everywhere.
 *
 * <p>The library may sort by the solver's measured figures, which are theirs to read for free;
 * picking what to drill from them is the coach's job, so there is no "drill the slowest" here.
 */
public class CaseSetActivity extends NanoTimerActivity implements DrillStatsTable.Listener {

  public static final String EXTRA_SET = "caseSet";
  /** One of the {@code SORT_} values to open on, in place of the one last used for the set. */
  public static final String EXTRA_SORT = "caseSetSort";

  public static final int SORT_ORDER = 0;
  public static final int SORT_STATUS = 1;
  public static final int SORT_SOLVE_MEAN = 2;
  public static final int SORT_DRILL_MEAN = 3;

  /** How many of the latest solves the solve means are read over. */
  private static final int SOLVES_READ = 1000;
  /** The narrowest a tile may be, which sets how many fit across. */
  private static final int TILE_MIN_DP = 80;

  /** A standing to filter on, or every case nothing is known about. */
  private static final CaseKnowledge.Status[] STATUS_FILTERS = {CaseKnowledge.Status.KNOWN,
      CaseKnowledge.Status.NEEDS_REVIEW, CaseKnowledge.Status.TO_LEARN, null};
  private static final int[] STATUS_LABELS = {R.string.known_algorithms_status_known,
      R.string.known_algorithms_status_review, R.string.known_algorithms_status_to_learn,
      R.string.analysis_knowledge_unseen};
  private static final int[] SORT_LABELS = {R.string.case_sort_order, R.string.case_sort_status,
      R.string.case_sort_solve_mean, R.string.case_sort_drill_mean};

  private CaseSet set;
  private int sort;
  /** Index into {@link #STATUS_FILTERS}, or -1 for every case. */
  private int statusFilter = -1;
  private CaseFilterBar groups;
  private TextView statusChip;
  private TextView sortChip;
  private DrillStatsTable table;
  private final Map<String, CaseKnowledge.Status> statuses =
      new LinkedHashMap<String, CaseKnowledge.Status>();
  /** The figures a mean sort reads, null until they are in. */
  private List<DrillCaseStats> figures;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.case_set);
    set = CaseSets.byId(getIntent().getStringExtra(EXTRA_SET));
    if (set == null) {
      finish();
      return;
    }
    setTitle(set.title());
    sort = getIntent().getIntExtra(EXTRA_SORT,
        Options.INSTANCE.getDrillChoice(sortKey(), SORT_ORDER));
    table = new DrillStatsTable(this, this);

    LinearLayout filters = findViewById(R.id.llCaseSetFilters);
    LayoutInflater inflater = LayoutInflater.from(this);
    groups = new CaseFilterBar(this, set, group -> show());
    filters.addView(groups.chip(inflater, filters, R.layout.case_filter_chip_small));
    statusChip = (TextView) inflater.inflate(R.layout.case_filter_chip_small, filters, false);
    statusChip.setOnClickListener(v -> pickStatus());
    statusChip.setVisibility(View.GONE); // until a standing is known
    filters.addView(statusChip);
    sortChip = (TextView) inflater.inflate(R.layout.case_filter_chip_small, filters, false);
    sortChip.setOnClickListener(v -> pickSort());
    filters.addView(sortChip);
    paintChips();
  }

  /** Read again on the way back: a sheet may have starred, or a drill have measured, meanwhile. */
  @Override
  protected void onResume() {
    super.onResume();
    App.INSTANCE.getService().getCaseHistory(0, new DataCallback<CaseHistory>() {
      @Override
      public void onData(final CaseHistory history) {
        runOnUiThread(() -> {
          statuses.clear();
          for (CaseKnowledge known : history.getCases()) {
            if (set.contains(known.getCode())) {
              statuses.put(known.getCode(), known.getStatus());
            }
          }
          Set<String> known = new HashSet<String>();
          for (Map.Entry<String, CaseKnowledge.Status> one : statuses.entrySet()) {
            if (one.getValue() != null && one.getValue().isLearned()) {
              known.add(one.getKey());
            }
          }
          groups.known(known);
          statusChip.setVisibility(statuses.isEmpty() ? View.GONE : View.VISIBLE);
          load();
        });
      }
    });
  }

  private String sortKey() {
    return "case_set_sort_" + set.id();
  }

  /** The figures a mean sort needs, then the screen; the grid sorts need none. */
  private void load() {
    figures = null;
    DataCallback<List<DrillCaseStats>> shown = new DataCallback<List<DrillCaseStats>>() {
      @Override
      public void onData(final List<DrillCaseStats> data) {
        runOnUiThread(() -> {
          figures = data;
          show();
        });
      }
    };
    if (sort == SORT_SOLVE_MEAN) {
      App.INSTANCE.getService().getCaseSolveTable(SOLVES_READ, shown);
    } else if (sort == SORT_DRILL_MEAN) {
      App.INSTANCE.getService().getDrillCaseStats(0, shown);
    } else {
      show();
    }
  }

  /** Whether a case passes the group and standing filters. */
  private boolean passes(String code) {
    if (!set.contains(code) || !groups.shows(code)) {
      return false;
    }
    return statusFilter < 0 || statuses.get(code) == STATUS_FILTERS[statusFilter];
  }

  private void show() {
    paintChips();
    List<String> cases = new ArrayList<String>();
    for (String code : set.cases()) {
      if (passes(code)) {
        cases.add(code);
      }
    }
    int known = 0;
    for (String code : cases) {
      known += statuses.get(code) == CaseKnowledge.Status.KNOWN ? 1 : 0;
    }
    String count = getResources().getQuantityString(R.plurals.case_set_cases, cases.size(),
        cases.size());
    ((TextView) findViewById(R.id.tvCaseSetCount)).setText(statuses.isEmpty() ? count
        : count + "  ·  " + getString(R.string.library_known, known));

    boolean mean = sort == SORT_SOLVE_MEAN || sort == SORT_DRILL_MEAN;
    LinearLayout grid = findViewById(R.id.llCaseSetGrid);
    grid.removeAllViews();
    grid.setVisibility(mean ? View.GONE : View.VISIBLE);
    View section = findViewById(R.id.llDrillStatsSection);
    TextView empty = findViewById(R.id.tvCaseSetEmpty);
    empty.setVisibility(View.GONE);
    if (!mean) {
      section.setVisibility(View.GONE);
      if (sort == SORT_STATUS) {
        sortByStatus(cases);
      }
      drawGrid(grid, cases);
      return;
    }
    if (figures == null) {
      section.setVisibility(View.GONE);
      return; // still being read
    }
    List<DrillCaseStats> rows = new ArrayList<DrillCaseStats>();
    for (DrillCaseStats one : figures) {
      if (passes(one.getCaseCode())) {
        rows.add(one);
      }
    }
    section.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
    empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    empty.setText(R.string.case_set_no_times);
    table.setStats(rows);
  }

  /** Known first, then slipping, then to learn, then nothing known; the set's order within each. */
  private void sortByStatus(List<String> cases) {
    final List<String> order = set.cases();
    Collections.sort(cases, (a, b) -> {
      int byStatus = rank(statuses.get(a)) - rank(statuses.get(b));
      return byStatus != 0 ? byStatus : order.indexOf(a) - order.indexOf(b);
    });
  }

  private static int rank(CaseKnowledge.Status status) {
    for (int i = 0; i < STATUS_FILTERS.length; i++) {
      if (STATUS_FILTERS[i] == status) {
        return i;
      }
    }
    return STATUS_FILTERS.length;
  }

  /** Rows of tiles, as many across as fit at {@link #TILE_MIN_DP}, the last row padded out. */
  private void drawGrid(LinearLayout grid, List<String> cases) {
    float density = getResources().getDisplayMetrics().density;
    int widthDp = (int) (getResources().getDisplayMetrics().widthPixels / density) - 32;
    int columns = Math.max(3, widthDp / TILE_MIN_DP);
    int gap = (int) (8 * density);
    LayoutInflater inflater = LayoutInflater.from(this);
    LinearLayout row = null;
    for (int i = 0; i < cases.size(); i++) {
      if (i % columns == 0) {
        row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 0, 0, gap);
        grid.addView(row);
      }
      final String code = cases.get(i);
      CaseTile tile = CaseTile.inflate(inflater, row).show(set, code).status(statuses.get(code));
      tile.view().setOnClickListener(v -> DialogUtils.showFragment(this,
          CaseSheet.newInstance(code)));
      row.addView(tile.view(), cell(i % columns, gap));
    }
    for (int left = cases.isEmpty() ? 0 : (columns - cases.size() % columns) % columns; left > 0;
        left--) {
      View spacer = new View(this);
      row.addView(spacer, cell(1, gap));
    }
  }

  private static LinearLayout.LayoutParams cell(int column, int gap) {
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
        LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    params.leftMargin = column == 0 ? 0 : gap;
    return params;
  }

  private void paintChips() {
    statusChip.setText(getString(R.string.analysis_window_chip, statusFilter < 0
        ? getString(R.string.case_filter_status) : getString(STATUS_LABELS[statusFilter])));
    statusChip.setSelected(statusFilter >= 0);
    sortChip.setText(getString(R.string.analysis_window_chip, getString(SORT_LABELS[sort])));
    sortChip.setSelected(sort != SORT_ORDER);
  }

  private void pickStatus() {
    String[] labels = new String[STATUS_LABELS.length + 1];
    labels[0] = getString(R.string.case_filter_all_statuses);
    for (int i = 0; i < STATUS_LABELS.length; i++) {
      labels[i + 1] = getString(STATUS_LABELS[i]);
    }
    new AlertDialog.Builder(this, R.style.NanoTimerDialogTheme)
        .setTitle(R.string.case_filter_status)
        .setSingleChoiceItems(labels, statusFilter + 1, (dialog, which) -> {
          statusFilter = which - 1;
          dialog.dismiss();
          show();
        })
        .show();
  }

  private void pickSort() {
    String[] labels = new String[SORT_LABELS.length];
    for (int i = 0; i < labels.length; i++) {
      labels[i] = getString(SORT_LABELS[i]);
    }
    new AlertDialog.Builder(this, R.style.NanoTimerDialogTheme)
        .setTitle(R.string.case_sort_title)
        .setSingleChoiceItems(labels, sort, (dialog, which) -> {
          sort = which;
          Options.INSTANCE.setDrillChoice(sortKey(), sort);
          dialog.dismiss();
          load();
        })
        .show();
  }

  @Override
  public void onCasePicked(String caseCode) {
    DialogUtils.showFragment(this, CaseSheet.newInstance(caseCode));
  }
}
