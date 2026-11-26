package com.cube.nanotimer.gui;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.cube.nanotimer.App;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.cases.CaseGroup;
import com.cube.nanotimer.cases.CaseSet;
import com.cube.nanotimer.cases.CaseSets;
import com.cube.nanotimer.gui.widget.CaseListHero;
import com.cube.nanotimer.gui.widget.DrillStatsTable;
import com.cube.nanotimer.gui.widget.SegmentedControl;
import com.cube.nanotimer.gui.widget.cases.CaseFilterBar;
import com.cube.nanotimer.gui.widget.cases.CaseSheet;
import com.cube.nanotimer.gui.widget.cases.CaseTile;
import com.cube.nanotimer.gui.widget.dialog.DrillPresetsDialog;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.session.CaseKnowledge;
import com.cube.nanotimer.util.DrillCasePreset;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.util.view.StepPalette;
import com.cube.nanotimer.util.view.ViewSegments;
import com.cube.nanotimer.vo.CaseHistory;
import com.cube.nanotimer.vo.drill.DrillCaseStats;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One set of the library, and the one screen every list of its cases is: browsed from the library,
 * picked from for a drill, and read as Drill stats.
 *
 * <p><b>Browsing</b>, its cases are a grid of tiles, filtered by group and, once a smart cube has
 * read any of them, by standing; each choice in both pickers counts the cases it would show under
 * the other filter. Several standings may be ticked at once; the chip names them, the last ones
 * folded into a "+1" once they outgrow the room beside the group chip. The sort is an app bar
 * icon, apart from the filters, lit while the set is out of its own order. Sorted by standing, the grid splits under one heading per standing; sorted by
 * a mean, it becomes the case table the drill screens read, since a grid has nowhere to put a
 * figure. A tap opens the case sheet, as everywhere. The library may sort by the solver's measured
 * figures; picking what to drill from them is the coach's job, so nothing here offers to.
 *
 * <p><b>Drill stats</b> is this screen sorted by drill mean: a strip of periods appears over the
 * table, and the drill cells over it count every rep in the period, not a mean of case means.
 *
 * <p><b>Picking</b> ({@link #pick}, {@link #learn}) is the same grid in pick mode: a tap picks or
 * lets go, a hold opens the sheet, All and None act on the cases shown, and Only these keeps just
 * those. Every tap writes through, so a pick survives a rotation or a Back with the same answer.
 * An unpicked case is turned down rather than hidden, so it can still be found to pick again.
 * Choosing what to learn opens on every standing but Known.
 */
public class CaseSetActivity extends NanoTimerActivity
    implements DrillStatsTable.Listener, DrillPresetsDialog.Listener {

  public static final String EXTRA_SET = "caseSet";
  /** One of the {@code SORT_} values to open on, in place of the one last used for the set. */
  public static final String EXTRA_SORT = "caseSetSort";
  private static final String EXTRA_PICK = "caseSetPick";
  private static final String EXTRA_SUGGESTED = "caseSetSuggested";
  private static final String EXTRA_NOT_STANDARD = "caseSetNotStandard";

  public static final int SORT_ORDER = 0;
  public static final int SORT_STATUS = 1;
  public static final int SORT_SOLVE_MEAN = 2;
  public static final int SORT_DRILL_MEAN = 3;
  private static final String EYEBROW_SEPARATOR = " · ";

  /** How many of the latest solves the solve means are read over. */
  private static final int SOLVES_READ = 1000;
  /** The narrowest a tile may be, which sets how many fit across. */
  private static final int TILE_MIN_DP = 80;
  private static final String KEY_WINDOW = "stats_window";
  private static final String KEY_STATUSES = "caseSetStatuses";
  private static final String KEY_NOT_STANDARD = "caseSetNotStandard";
  /** A standing the group picked leaves empty: still pickable, but read as holding nothing. */
  private static final float EMPTY_ALPHA = 0.4f;

  /** A standing to filter on, or every case nothing is known about. */
  private static final CaseKnowledge.Status[] STATUS_FILTERS = {CaseKnowledge.Status.KNOWN,
      CaseKnowledge.Status.NEEDS_REVIEW, CaseKnowledge.Status.TO_LEARN, null};
  private static final int[] STATUS_LABELS = {R.string.known_algorithms_status_known,
      R.string.known_algorithms_status_review, R.string.known_algorithms_status_to_learn,
      R.string.analysis_knowledge_unseen};
  /** What a learn pick opens on: every standing but Known, as indices into STATUS_FILTERS. */
  private static final int[] LEARN_STATUSES = {1, 2, 3};
  /** The library's progress bar shades, so a standing has one colour wherever it is drawn. */
  private static final int[] STATUS_COLOURS = {R.color.case_status_known,
      R.color.case_status_review, R.color.case_status_learn, R.color.case_status_unseen};
  private static final int[] SORT_LABELS = {R.string.case_sort_order, R.string.case_sort_status,
      R.string.case_sort_solve_mean, R.string.case_sort_drill_mean};

  private CaseSet set;
  private int sort;
  /** Indices into {@link #STATUS_FILTERS} ticked; empty for every case, never all of them. */
  private final Set<Integer> statusFilter = new HashSet<Integer>();
  /** What the screen opens on: a learn pick leaves Known out, everything else shows every case. */
  private final Set<Integer> defaultStatuses = new HashSet<Integer>();
  private CaseFilterBar groups;
  private View groupChip;
  private TextView statusChip;
  private TextView unusualChip;
  /** The cases the Analysis card counted as not standard, when it opened this screen on them. */
  private final Set<String> notStandard = new HashSet<String>();
  private boolean notStandardOnly;
  private MenuItem sortItem;
  private DrillStatsTable table;
  private CaseListHero hero;
  private SegmentedControl window;
  private final Map<String, CaseKnowledge.Status> statuses =
      new LinkedHashMap<String, CaseKnowledge.Status>();
  /** The figures a mean sort reads, null until they are in. */
  private List<DrillCaseStats> figures;
  private int loadId;
  /** The cases the grid shows now, in its order. */
  private final List<String> shown = new ArrayList<String>();

  /** Picking: the drill family prefix ({@code "oll_"}), or null when browsing. */
  private String family;
  /** Picking what to learn next rather than a drill's cases: the coach's suggestion, else null. */
  private List<String> suggested;
  private final Set<String> picked = new LinkedHashSet<String>();
  private List<DrillCasePreset> presets;

  /** The drill picker: a family's cases to pick from, written through to the drill's pick. */
  public static Intent pick(Context context, String family) {
    Intent intent = new Intent(context, CaseSetActivity.class);
    intent.putExtra(EXTRA_SET, CaseSets.ofFamily(family).id());
    intent.putExtra(EXTRA_PICK, family);
    return intent;
  }

  /** The same picker choosing the cases to learn next, "Suggested" in the place of "All". */
  public static Intent learn(Context context, String family, List<String> suggested) {
    Intent intent = pick(context, family);
    intent.putStringArrayListExtra(EXTRA_SUGGESTED, new ArrayList<String>(suggested));
    return intent;
  }

  /** The set narrowed to the cases the Analysis card counted as not standard. */
  public static Intent notStandard(Context context, String setId, Collection<String> cases) {
    Intent intent = new Intent(context, CaseSetActivity.class);
    intent.putExtra(EXTRA_SET, setId);
    intent.putStringArrayListExtra(EXTRA_NOT_STANDARD, new ArrayList<String>(cases));
    return intent;
  }

  /** Drill stats: the family's set sorted by drill mean. */
  public static Intent drillStats(Context context, String family) {
    Intent intent = new Intent(context, CaseSetActivity.class);
    intent.putExtra(EXTRA_SET, CaseSets.ofFamily(family).id());
    intent.putExtra(EXTRA_SORT, SORT_DRILL_MEAN);
    return intent;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.case_set);
    set = CaseSets.byId(getIntent().getStringExtra(EXTRA_SET));
    if (set == null) {
      finish();
      return;
    }
    family = getIntent().getStringExtra(EXTRA_PICK);
    suggested = getIntent().getStringArrayListExtra(EXTRA_SUGGESTED);
    sort = isPicking() ? SORT_ORDER : getIntent().getIntExtra(EXTRA_SORT,
        Options.INSTANCE.getDrillChoice(sortKey(), SORT_ORDER));
    if (sort == SORT_STATUS && !hasStandings()) {
      sort = SORT_ORDER;
    }
    table = new DrillStatsTable(this, this);

    LinearLayout filters = findViewById(R.id.llCaseSetFilters);
    LayoutInflater inflater = LayoutInflater.from(this);
    groups = new CaseFilterBar(this, set, group -> show());
    groupChip = groups.chip(inflater, filters, R.layout.case_filter_chip_small);
    filters.addView(groupChip);
    statusChip = (TextView) inflater.inflate(R.layout.case_filter_chip_small, filters, false);
    statusChip.setOnClickListener(v -> pickStatus());
    statusChip.setVisibility(View.GONE); // until a standing is known
    filters.addView(statusChip);
    unusualChip = (TextView) inflater.inflate(R.layout.case_filter_chip_small, filters, false);
    unusualChip.setText(R.string.analysis_algorithms_not_standard);
    float density = getResources().getDisplayMetrics().density;
    Drawable cross = DrawableCompat.wrap(
        ContextCompat.getDrawable(this, R.drawable.ic_cancel_solve).mutate());
    DrawableCompat.setTint(cross, ContextCompat.getColor(this, R.color.lightblue));
    cross.setBounds(0, 0, (int) (16 * density), (int) (16 * density));
    unusualChip.setCompoundDrawablesRelative(null, null, cross, null);
    unusualChip.setCompoundDrawablePadding((int) (6 * density));
    unusualChip.setPaddingRelative(unusualChip.getPaddingStart(), 0, (int) (10 * density), 0);
    unusualChip.setOnClickListener(v -> {
      notStandardOnly = false; // only the Analysis card turns it on
      show();
    });
    filters.addView(unusualChip);
    List<String> handed = getIntent().getStringArrayListExtra(EXTRA_NOT_STANDARD);
    if (handed != null) {
      notStandard.addAll(handed);
    }
    // The room for the names is only known once the group chip is laid out.
    filters.addOnLayoutChangeListener(
        (v, l, t, r, b, ol, ot, or, ob) -> statusChip.post(this::paintChips));
    setUpWindow();
    if (isPicking()) {
      setUpPicking();
    } else {
      setTitle(sort == SORT_DRILL_MEAN && getIntent().hasExtra(EXTRA_SORT)
          ? getString(R.string.drill_stats_title) + " · " + set.title() : set.title());
    }
    if (savedInstanceState != null && savedInstanceState.containsKey(KEY_STATUSES)) {
      statusFilter.clear();
      statusFilter.addAll(savedInstanceState.getIntegerArrayList(KEY_STATUSES));
    }
    notStandardOnly = savedInstanceState == null ? !notStandard.isEmpty()
        : savedInstanceState.getBoolean(KEY_NOT_STANDARD);
    paintChips();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putIntegerArrayList(KEY_STATUSES, new ArrayList<Integer>(statusFilter));
    outState.putBoolean(KEY_NOT_STANDARD, notStandardOnly);
  }

  private boolean isPicking() {
    return family != null;
  }

  private boolean isLearning() {
    return suggested != null;
  }

  private String sortKey() {
    return "case_set_sort_" + set.id();
  }

  /** The period strip Drill stats always had, shown while the drill mean sort is on. */
  private void setUpWindow() {
    DrillStatsWindow[] windows = DrillStatsWindow.values();
    String[] labels = new String[windows.length];
    for (int i = 0; i < windows.length; i++) {
      labels[i] = getString(windows[i].getLabelId());
    }
    window = new SegmentedControl(this, (LinearLayout) findViewById(R.id.llDrillStatsWindow),
        labels, index -> {
          Options.INSTANCE.setDrillChoice(KEY_WINDOW, index);
          load();
        });
    window.setSelection(Math.min(windows.length - 1, Math.max(0,
        Options.INSTANCE.getDrillChoice(KEY_WINDOW, DrillStatsWindow.ALL.ordinal()))));
  }

  private DrillStatsWindow window() {
    return DrillStatsWindow.values()[window.getSelection()];
  }

  private void setUpPicking() {
    setTitle(isLearning() ? (set == CaseSets.OLL ? R.string.coach_learn_next
        : R.string.coach_learn_next_pll)
        : set == CaseSets.OLL ? R.string.drill_cases_title_oll
        : set == CaseSets.CMLL ? R.string.drill_cases_title_cmll : R.string.drill_cases_title_pll);
    if (isLearning()) {
      Set<String> own = Options.INSTANCE.getLearnPick(family);
      picked.addAll(own == null ? suggested : own);
      for (int status : LEARN_STATUSES) {
        defaultStatuses.add(status);
      }
      statusFilter.addAll(defaultStatuses);
    } else {
      Set<String> stored = Options.INSTANCE.getDrillCases(family);
      picked.addAll(stored == null ? set.cases() : stored);
    }
    presets = Options.INSTANCE.getDrillCasePresets(family);
    findViewById(R.id.llCaseSetPick).setVisibility(View.VISIBLE);
    findViewById(R.id.tvCaseSetCount).setVisibility(View.GONE);
    findViewById(R.id.tvCaseSetHint).setVisibility(View.VISIBLE);
    View grid = findViewById(R.id.llCaseSetGrid);
    grid.setPadding(0, (int) (10 * getResources().getDisplayMetrics().density), 0, 0);
    TextView all = findViewById(R.id.tvCasesAll);
    if (isLearning()) {
      all.setText(R.string.learn_pick_suggested);
    }
    all.setOnClickListener(v -> pickAll(true));
    findViewById(R.id.tvCasesNone).setOnClickListener(v -> pickAll(false));
    findViewById(R.id.tvCasesOnly).setOnClickListener(v -> {
      picked.clear();
      picked.addAll(shown);
      save();
    });
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    if (isPicking() && !isLearning()) {
      getMenuInflater().inflate(R.menu.case_set_pick_menu, menu);
      paintPresets(menu.findItem(R.id.itCasePresets));
    } else if (!isPicking()) {
      getMenuInflater().inflate(R.menu.case_set_menu, menu);
      sortItem = menu.findItem(R.id.itCaseSort);
      paintSort();
    }
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == R.id.itCaseSort) {
      pickSort();
      return true;
    }
    if (item.getItemId() == R.id.itCasePresets) {
      DrillPresetsDialog.show(this, family, picked, this);
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  /** The bookmark fills in the family's colour while the pick stands at a preset. */
  private void paintPresets(MenuItem item) {
    if (item == null) {
      return;
    }
    DrillCasePreset preset = DrillCasePreset.matching(presets, picked);
    item.setIcon(preset == null ? R.drawable.ic_preset : R.drawable.ic_preset_on);
    DrawableCompat.setTint(DrawableCompat.wrap(item.getIcon().mutate()), preset != null
        ? familyColour() : ContextCompat.getColor(this, R.color.secondary_text));
  }

  private int familyColour() {
    return StepPalette.cfop(this).colorForCase(set.id() + "_");
  }

  @Override
  public void onPresetApplied(Set<String> presetCases) {
    picked.clear();
    picked.addAll(presetCases);
    save();
  }

  @Override
  public void onPresetsChanged() {
    presets = Options.INSTANCE.getDrillCasePresets(family);
    show();
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

  /** Nothing chosen to learn is no choice: back to following the suggestion. */
  @Override
  protected void onPause() {
    super.onPause();
    if (isLearning() && picked.isEmpty()) {
      Options.INSTANCE.setLearnPick(family, null);
    }
  }

  /** The figures a mean sort needs, then the screen; the grid sorts need none. */
  private void load() {
    figures = null;
    final int id = ++loadId;
    DataCallback<List<DrillCaseStats>> shownWhenIn = new DataCallback<List<DrillCaseStats>>() {
      @Override
      public void onData(final List<DrillCaseStats> data) {
        runOnUiThread(() -> {
          if (id == loadId) {
            figures = data;
            show();
          }
        });
      }
    };
    if (sort == SORT_SOLVE_MEAN) {
      App.INSTANCE.getService().getCaseSolveTable(SOLVES_READ, shownWhenIn);
    } else if (sort == SORT_DRILL_MEAN) {
      App.INSTANCE.getService().getDrillCaseStats(window().since(), shownWhenIn);
    } else {
      show();
    }
  }

  /** Whether a case passes the group and standing filters. */
  private boolean passes(String code) {
    return passes(code, true, true);
  }

  /** The same, leaving out the group or the standing filter, to count what picking one would show. */
  private boolean passes(String code, boolean byGroup, boolean byStatus) {
    if (!set.contains(code) || byGroup && !groups.shows(code)
        || notStandardOnly && !notStandard.contains(code)) {
      return false;
    }
    if (isLearning() && byStatus && !statusNarrowed() && picked.contains(code)) {
      return true; // a picked one stays, whatever its standing, so it can be let go
    }
    return !byStatus || statusFilter.isEmpty() || statusFilter.contains(rank(statuses.get(code)));
  }

  private void show() {
    paintChips();
    List<String> anyGroup = new ArrayList<String>();
    for (String code : set.cases()) {
      if (passes(code, false, true)) {
        anyGroup.add(code);
      }
    }
    groups.matching(anyGroup);
    shown.clear();
    for (String code : set.cases()) {
      if (passes(code)) {
        shown.add(code);
      }
    }
    if (sort == SORT_STATUS) {
      sortByStatus(shown);
    }
    int known = 0;
    for (String code : shown) {
      known += statuses.get(code) == CaseKnowledge.Status.KNOWN ? 1 : 0;
    }
    String count = getResources().getQuantityString(R.plurals.case_set_cases, shown.size(),
        shown.size());
    ((TextView) findViewById(R.id.tvCaseSetCount)).setText(statuses.isEmpty() ? count
        : count + "  ·  " + getString(R.string.library_known, known));
    if (isPicking()) {
      showPick();
    }

    boolean mean = sort == SORT_SOLVE_MEAN || sort == SORT_DRILL_MEAN;
    findViewById(R.id.llDrillStatsWindow).setVisibility(
        sort == SORT_DRILL_MEAN ? View.VISIBLE : View.GONE);
    LinearLayout grid = findViewById(R.id.llCaseSetGrid);
    grid.removeAllViews();
    grid.setVisibility(mean ? View.GONE : View.VISIBLE);
    View section = findViewById(R.id.llDrillStatsSection);
    View cells = findViewById(R.id.llCaseSetHero);
    TextView empty = findViewById(R.id.tvCaseSetEmpty);
    empty.setVisibility(View.GONE);
    cells.setVisibility(View.GONE);
    if (!mean) {
      section.setVisibility(View.GONE);
      drawGrid(grid);
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
    boolean anything = !rows.isEmpty();
    section.setVisibility(anything ? View.VISIBLE : View.GONE);
    empty.setVisibility(anything ? View.GONE : View.VISIBLE);
    empty.setText(sort == SORT_DRILL_MEAN && window().since() != 0
        ? R.string.drill_stats_empty_window : sort == SORT_DRILL_MEAN
        ? R.string.drill_stats_empty : R.string.case_set_no_times);
    if (anything) {
      showCells(rows);
    }
    table.setStats(rows);
  }

  /**
   * The card over a mean table, from every time in it rather than from the cases: a mean of case
   * means would weigh a case seen twice as heavily as one seen forty times.
   */
  private void showCells(List<DrillCaseStats> rows) {
    findViewById(R.id.llCaseSetHero).setVisibility(View.VISIBLE);
    int count = 0;
    long total = 0;
    long recognition = 0;
    long best = Long.MAX_VALUE;
    String bestCase = null;
    for (DrillCaseStats stats : rows) {
      count += stats.getCount();
      total += stats.getTotalMs();
      recognition += stats.getRecognitionMs();
      if (stats.getBestMs() < best) {
        best = stats.getBestMs();
        bestCase = stats.getCaseCode();
      }
    }
    if (hero == null) {
      hero = new CaseListHero(findViewById(R.id.llCaseSetHero), familyColour());
    }
    boolean drills = sort == SORT_DRILL_MEAN;
    hero.setEyebrow(drills ? getString(R.string.case_sheet_in_drills) + EYEBROW_SEPARATOR
        + getString(window().getLongLabelId()) : getString(R.string.case_sheet_in_solves));
    hero.setMean(recognition / count, (total - recognition) / count);
    hero.setCount(getString(drills ? R.string.drill_summary_cell_reps
            : R.string.case_solves_cell_count), count,
        getResources().getQuantityString(R.plurals.drill_stats_cell_cases, rows.size(),
            rows.size()));
    hero.setBest(best, null, bestCase);
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

  /**
   * The grid; for a sectioned set one grid per group under its name and count; sorted by standing,
   * one per standing instead.
   */
  private void drawGrid(LinearLayout grid) {
    LayoutInflater inflater = LayoutInflater.from(this);
    if (sort != SORT_STATUS && set.sectioned()) {
      for (CaseGroup group : set.groups()) {
        List<String> inGroup = new ArrayList<String>();
        for (String code : shown) {
          if (group.contains(code)) {
            inGroup.add(code);
          }
        }
        if (!inGroup.isEmpty()) {
          TextView header = (TextView) inflater.inflate(R.layout.case_set_section, grid, false);
          header.setText(getString(R.string.known_algorithms_status_count,
              group.getName(this), inGroup.size()));
          grid.addView(header);
          drawTiles(grid, inGroup);
        }
      }
      return;
    }
    if (sort != SORT_STATUS) {
      drawTiles(grid, shown);
      return;
    }
    for (int i = 0; i < STATUS_FILTERS.length; i++) {
      List<String> standing = new ArrayList<String>();
      for (String code : shown) {
        if (statuses.get(code) == STATUS_FILTERS[i]) {
          standing.add(code);
        }
      }
      if (standing.isEmpty()) {
        continue;
      }
      TextView header = (TextView) inflater.inflate(R.layout.case_set_section, grid, false);
      header.setText(getString(R.string.known_algorithms_status_count,
          getString(STATUS_LABELS[i]), standing.size()));
      header.setCompoundDrawablesRelativeWithIntrinsicBounds(swatch(i), null, null, null);
      grid.addView(header);
      drawTiles(grid, standing);
    }
  }

  /** A standing's colour as a small rounded square, as the Status picker and the headers show it. */
  private GradientDrawable swatch(int status) {
    float density = getResources().getDisplayMetrics().density;
    GradientDrawable swatch = new GradientDrawable();
    swatch.setColor(ContextCompat.getColor(this, STATUS_COLOURS[status]));
    swatch.setCornerRadius(3 * density);
    int size = (int) (12 * density);
    swatch.setSize(size, size);
    return swatch;
  }

  /** Rows of tiles, as many across as fit at {@link #TILE_MIN_DP}, the last row padded out. */
  private void drawTiles(LinearLayout grid, List<String> cases) {
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
      row.addView(tile(inflater, row, cases.get(i)), cell(i % columns, gap));
    }
    for (int left = cases.isEmpty() ? 0 : (columns - cases.size() % columns) % columns; left > 0;
        left--) {
      row.addView(new Space(this), cell(1, gap)); // a plain View would take all the height offered
    }
  }

  /** Browsing, a tap opens the sheet. Picking, a tap picks and a hold opens the sheet. */
  private View tile(LayoutInflater inflater, LinearLayout row, final String code) {
    CaseTile tile = CaseTile.inflate(inflater, row).show(set, code);
    View view = tile.view();
    if (!isPicking()) {
      view.setOnClickListener(v -> DialogUtils.showFragment(this, CaseSheet.newInstance(code)));
      return view;
    }
    tile.picked(picked.contains(code));
    if (isLearning() && statuses.get(code) == CaseKnowledge.Status.KNOWN) {
      tile.name(getString(R.string.learn_pick_known_case, set.name(code)));
    }
    view.setOnClickListener(v -> {
      if (!picked.remove(code)) {
        picked.add(code);
      }
      save();
    });
    view.setOnLongClickListener(v -> {
      DialogUtils.showFragment(this, CaseSheet.newInstance(code));
      return true;
    });
    return view;
  }

  private static LinearLayout.LayoutParams cell(int column, int gap) {
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
        LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    params.leftMargin = column == 0 ? 0 : gap;
    return params;
  }

  /** With a group shown, only its cases are picked or let go; the rest keep their state. */
  private void pickAll(boolean all) {
    boolean narrowed = narrowed();
    List<String> scope = narrowed ? new ArrayList<String>(shown) : set.cases();
    picked.removeAll(scope);
    if (all) {
      for (String code : isLearning() ? suggested : set.cases()) {
        if (scope.contains(code)) {
          picked.add(code);
        }
      }
    }
    save();
  }

  /**
   * Everything ticked is stored as no choice at all, so a case added later is drilled too. A learn
   * pick matching the suggestion is stored the same way, so it keeps following it.
   */
  private void save() {
    if (isLearning()) {
      boolean following = picked.equals(new HashSet<String>(suggested));
      Options.INSTANCE.setLearnPick(family, following ? null : inSetOrder(picked));
    } else {
      Options.INSTANCE.setDrillCases(family,
          picked.size() == set.cases().size() ? null : inSetOrder(picked));
    }
    show();
  }

  private Set<String> inSetOrder(Set<String> codes) {
    Set<String> ordered = new LinkedHashSet<String>();
    for (String code : set.cases()) {
      if (codes.contains(code)) {
        ordered.add(code);
      }
    }
    return ordered;
  }

  /** The count, the preset the pick stands at, All / None, and Only these while narrowed. */
  private void showPick() {
    DrillCasePreset preset = isLearning() ? null : DrillCasePreset.matching(presets, picked);
    int total = set.cases().size();
    String number = String.valueOf(picked.size());
    String text = preset == null ? getString(R.string.drill_cases_count, picked.size(), total)
        : getString(R.string.drill_cases_count_preset, picked.size(), total, preset.getName());
    SpannableString spanned = new SpannableString(text);
    int at = text.indexOf(number);
    if (at >= 0) {
      spanned.setSpan(new ForegroundColorSpan(familyColour()), at, at + number.length(),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    ((TextView) findViewById(R.id.tvCasesCount)).setText(spanned);

    boolean narrowed = narrowed();
    int pickedShown = 0;
    for (String code : shown) {
      pickedShown += picked.contains(code) ? 1 : 0;
    }
    ViewSegments.style((TextView) findViewById(R.id.tvCasesAll), narrowed
        ? !isLearning() && pickedShown == shown.size()
        : isLearning() ? Options.INSTANCE.getLearnPick(family) == null : picked.size() == total);
    ViewSegments.style((TextView) findViewById(R.id.tvCasesNone),
        narrowed ? pickedShown == 0 : picked.isEmpty());
    TextView only = findViewById(R.id.tvCasesOnly);
    boolean offersOnly = narrowed && !shown.isEmpty()
        && !(pickedShown == shown.size() && picked.size() == shown.size());
    only.setVisibility(offersOnly ? View.VISIBLE : View.GONE);
    only.setText(getString(R.string.case_set_only_these, shown.size()));
    invalidateOptionsMenu();
  }

  private void paintChips() {
    CharSequence label = statusLabel();
    if (!label.toString().equals(statusChip.getText().toString())) {
      statusChip.setText(label); // only on a change: setting it relays out, which calls back here
    }
    statusChip.setSelected(!statusFilter.isEmpty());
    unusualChip.setSelected(true);
    unusualChip.setVisibility(notStandardOnly ? View.VISIBLE : View.GONE);
  }

  /** "Status" for every case, else the standings ticked, as many named as fit beside the group. */
  private CharSequence statusLabel() {
    if (statusFilter.isEmpty()) {
      return getString(R.string.analysis_window_chip, getString(R.string.case_filter_status));
    }
    StringBuilder names = new StringBuilder();
    for (int i = 0; i < STATUS_FILTERS.length; i++) {
      if (statusFilter.contains(i)) {
        names.append(names.length() == 0 ? "" : ", ").append(getString(STATUS_LABELS[i]));
      }
    }
    String arrow = getString(R.string.analysis_window_chip, "");
    View row = (View) statusChip.getParent().getParent();
    float room = row.getWidth() - row.getPaddingLeft() - row.getPaddingRight()
        - groupChip.getWidth() - 2 * chipMargin() - statusChip.getPaddingLeft()
        - (unusualChip.getVisibility() == View.VISIBLE ? unusualChip.getWidth() + chipMargin() : 0)
        - statusChip.getPaddingRight() - statusChip.getPaint().measureText(arrow);
    CharSequence fitted = row.getWidth() == 0 ? names : TextUtils.commaEllipsize(names,
        statusChip.getPaint(), Math.max(0, room), "+1", "+%d");
    return getString(R.string.analysis_window_chip,
        fitted.length() == 0 ? TextUtils.ellipsize(names, statusChip.getPaint(), Math.max(0, room),
            TextUtils.TruncateAt.END) : fitted);
  }

  private int chipMargin() {
    return ((ViewGroup.MarginLayoutParams) groupChip.getLayoutParams()).rightMargin;
  }

  /** The sort icon lights up in the accent while the set is not in its own order. */
  private void paintSort() {
    if (sortItem == null) {
      return;
    }
    DrawableCompat.setTint(DrawableCompat.wrap(sortItem.getIcon().mutate()),
        ContextCompat.getColor(this, sort == SORT_ORDER ? R.color.white : R.color.lightblue));
  }

  /**
   * Every standing with its colour and how many cases it holds under the group picked. A tap ticks
   * or unticks one; from every case it ticks just that one, and ticking them all is "All" again.
   */
  private void pickStatus() {
    final int[] counts = new int[STATUS_FILTERS.length];
    int all = 0;
    for (String code : set.cases()) {
      if (passes(code, true, false)) {
        all++;
        counts[rank(statuses.get(code))]++;
      }
    }
    LayoutInflater inflater = LayoutInflater.from(this);
    View body = inflater.inflate(R.layout.case_status_picker, null);
    LinearLayout rows = body.findViewById(R.id.llStatusRows);
    final List<View> rowViews = new ArrayList<View>();
    for (int i = -1; i < STATUS_FILTERS.length; i++) {
      View row = inflater.inflate(R.layout.case_status_row, rows, false);
      View swatch = row.findViewById(R.id.vStatusSwatch);
      if (i < 0) {
        swatch.setVisibility(View.INVISIBLE); // keeps "All" in line with the names under it
      } else {
        swatch.setBackground(swatch(i));
      }
      ((TextView) row.findViewById(R.id.tvStatusName)).setText(
          i < 0 ? R.string.case_filter_all_statuses : STATUS_LABELS[i]);
      ((TextView) row.findViewById(R.id.tvStatusCount)).setText(
          String.valueOf(i < 0 ? all : counts[i]));
      final int status = i;
      row.setOnClickListener(v -> {
        toggleStatus(status);
        paintStatusRows(rowViews, counts);
        show();
      });
      rows.addView(row);
      rowViews.add(row);
    }
    paintStatusRows(rowViews, counts);
    new AlertDialog.Builder(this, R.style.NanoTimerDialogTheme)
        .setTitle(R.string.case_filter_status)
        .setView(body)
        .setPositiveButton(R.string.close, null)
        .show();
  }


  /** Whether any filter leaves cases out, so All / None act on just the shown. */
  private boolean narrowed() {
    return groups.getGroup() != null || statusNarrowed() || notStandardOnly;
  }

  /** Whether the standing filter departs from the default, so All / None act on just the shown. */
  private boolean statusNarrowed() {
    return !statusFilter.equals(defaultStatuses);
  }

  private void toggleStatus(int status) {
    if (status < 0) {
      statusFilter.clear();
    } else if (!statusFilter.remove(status)) {
      statusFilter.add(status);
      if (statusFilter.size() == STATUS_FILTERS.length) {
        statusFilter.clear();
      }
    }
  }

  /** Rows ticked as the filter stands; an empty standing is dimmed unless ticked. */
  private void paintStatusRows(List<View> rowViews, int[] counts) {
    for (int i = -1; i < STATUS_FILTERS.length; i++) {
      View row = rowViews.get(i + 1);
      boolean ticked = i < 0 ? statusFilter.isEmpty() : statusFilter.contains(i);
      row.setSelected(ticked);
      ((CheckBox) row.findViewById(R.id.cbStatusTick)).setChecked(ticked);
      row.setAlpha(i >= 0 && counts[i] == 0 && !ticked ? EMPTY_ALPHA : 1f);
    }
  }

  /** Whether the set's cases can have a standing: an F2L pair never does. */
  private boolean hasStandings() {
    return set.picture() != CaseSet.Picture.PAIR;
  }

  private void pickSort() {
    final List<Integer> offered = new ArrayList<Integer>();
    for (int i = 0; i < SORT_LABELS.length; i++) {
      if (i != SORT_STATUS || hasStandings()) {
        offered.add(i);
      }
    }
    String[] labels = new String[offered.size()];
    for (int i = 0; i < labels.length; i++) {
      labels[i] = getString(SORT_LABELS[offered.get(i)]);
    }
    new AlertDialog.Builder(this, R.style.NanoTimerDialogTheme)
        .setTitle(R.string.case_sort_title)
        .setSingleChoiceItems(labels, offered.indexOf(sort), (dialog, which) -> {
          sort = offered.get(which);
          Options.INSTANCE.setDrillChoice(sortKey(), sort);
          dialog.dismiss();
          paintSort();
          load();
        })
        .show();
  }

  @Override
  public void onCasePicked(String caseCode) {
    DialogUtils.showFragment(this, CaseSheet.newInstance(caseCode));
  }
}
