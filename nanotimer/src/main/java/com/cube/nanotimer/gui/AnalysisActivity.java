package com.cube.nanotimer.gui;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.cube.nanotimer.App;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.coach.StepBaseline;
import com.cube.nanotimer.cube.SolveTypeMethod;
import com.cube.nanotimer.gui.widget.AnalysisCases;
import com.cube.nanotimer.gui.widget.CaseRow;
import com.cube.nanotimer.gui.widget.CaseTableHeadings;
import com.cube.nanotimer.gui.widget.dialog.CaseAlgorithmsDialog;
import com.cube.nanotimer.gui.widget.SegmentedControl;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.session.CaseKnowledge;
import com.cube.nanotimer.session.MethodStatistics;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.util.view.DeltaBarView;
import com.cube.nanotimer.util.view.SolveStepBarView;
import com.cube.nanotimer.util.view.StepPalette;
import com.cube.nanotimer.vo.CaseHistory;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveStep;
import com.cube.nanotimer.vo.SolveType;
import com.cube.nanotimer.vo.StepStats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Where the time goes: one solve type's last so many cube-read solves, read three ways.
 *
 * <p>Everything here is a number the app measured, or a sort or a subtraction of one. Nothing on
 * this screen is a judgement about what those numbers mean.
 */
public class AnalysisActivity extends NanoTimerActivity {

  public static final String EXTRA_SOLVE_TYPE = "analysisSolveType";
  /** Which tab to open on, by ordinal, for a door that knows what it is pointing at. */
  public static final String EXTRA_TAB = "analysisTab";
  /** The family the Cases tab opens narrowed to, for a door that knows which step it came from. */
  public static final String EXTRA_FAMILY = "analysisFamily";

  private static final int TAB_SOLVE = 0;
  private static final int TAB_CASES = 1;

  /** How the shares are written, which is a whole number of points either way. */
  private static final String PERCENT_FORMAT = "%d%%";

  /** The narrowest the delta scale ever gets, so a solver in proportion does not read as wild. */
  private static final int MIN_DELTA_SCALE = 6;

  // No count column: a step is reached in nearly every solve, so its count is the solve count and
  // ranking by it would rank nothing. The rows leave it out too.
  private static final int[] HEADING_LABELS = {0, R.string.drill_summary_cell_mean,
      R.string.drill_summary_cell_best, R.string.drill_stats_column_worst};
  private static final boolean[] OPENS_DESCENDING = {true, true, true, true};

  private SolveType solveType;
  private CubeMethod method;
  private AnalysisWindow window;
  private SegmentedControl tabs;
  private CaseTableHeadings headings;
  private StepPalette palette;
  private MenuItem windowItem;
  private int tab = TAB_SOLVE;
  private boolean loaded;
  private View solveRoot;
  private AnalysisCases cases;
  private MethodStatistics statistics;
  private List<CaseKnowledge> known = Collections.emptyList();

  /** The steps as the query returned them, in solving order, and what the table is ranked on. */
  private final List<StepStats> steps = new ArrayList<StepStats>();
  private final Map<StepStats, CaseRow> lines = new LinkedHashMap<StepStats, CaseRow>();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.analysis_screen);
    setTitle(R.string.analysis_title);

    solveType = (SolveType) getIntent().getSerializableExtra(EXTRA_SOLVE_TYPE);
    method = SolveTypeMethod.of(solveType);
    window = AnalysisWindow.of(Options.INSTANCE.getAnalysisWindow(
        AnalysisWindow.HUNDRED.ordinal()));
    palette = StepPalette.cfop(this);

    ActionBar bar = getSupportActionBar();
    if (bar != null) {
      // The context is what the screen is about rather than a control, so it rides on the app bar.
      bar.setSubtitle(getString(R.string.analysis_context,
          Utils.toSolveTypeLocalizedName(this, solveType.getName()),
          getString(SolveTypeMethod.nameOf(method))));
    }

    tabs = new SegmentedControl(this, (LinearLayout) findViewById(R.id.llAnalysisTabs),
        new String[] {getString(R.string.analysis_tab_solve), getString(R.string.analysis_tab_cases),
            getString(R.string.analysis_tab_plan)},
        new SegmentedControl.Listener() {
          @Override
          public void onSegmentPicked(int index) {
            showTab(index);
          }
        });
    solveRoot = findViewById(R.id.llAnalysisSolve);
    headings = new CaseTableHeadings(solveRoot, HEADING_LABELS,
        OPENS_DESCENDING, 1, new CaseTableHeadings.Listener() {
          @Override
          public void onRanked(int column, boolean descending) {
            rankSteps();
          }
        });
    headings.setLabel(R.string.analysis_column_step);
    cases = new AnalysisCases(this, findViewById(R.id.llAnalysisCases), casesListener());
    cases.setFamily(getIntent().getStringExtra(EXTRA_FAMILY));
    ((TextView) findViewById(R.id.tvAnalysisDeltaLabel))
        .setText(getString(R.string.analysis_delta_label, getString(SolveTypeMethod.nameOf(method))));

    tabs.setSelection(getIntent().getIntExtra(EXTRA_TAB, TAB_SOLVE));
    showTab(tabs.getSelection());
    load();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.analysis_menu, menu);
    windowItem = menu.findItem(R.id.itAnalysisWindow);
    TextView chip = (TextView) windowItem.getActionView();
    chip.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        askForWindow();
      }
    });
    showWindow();
    return super.onCreateOptionsMenu(menu);
  }

  /** Which slice of history the figures are read from, on the same line as what they are about. */
  private void showWindow() {
    if (windowItem != null) {
      ((TextView) windowItem.getActionView())
          .setText(getString(R.string.analysis_window_chip, getString(window.getLabelId())));
    }
  }

  private void askForWindow() {
    AnalysisWindow[] windows = AnalysisWindow.values();
    String[] labels = new String[windows.length];
    for (int i = 0; i < labels.length; i++) {
      labels[i] = getString(windows[i].getLabelId());
    }
    new AlertDialog.Builder(this, R.style.NanoTimerDialogTheme)
        .setTitle(R.string.analysis_window_title)
        .setSingleChoiceItems(labels, window.ordinal(), new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
            window = AnalysisWindow.values()[which];
            Options.INSTANCE.setAnalysisWindow(window.ordinal());
            showWindow();
            load();
          }
        })
        .show();
  }

  private void showTab(int tab) {
    boolean moved = this.tab != tab;
    this.tab = tab;
    showWhatIsReadable();
    if (moved) {
      // A tab is a different question, so it starts at its own first answer rather than at
      // whatever depth the last one was left scrolled to.
      final ScrollView scroll = findViewById(R.id.svAnalysis);
      scroll.post(new Runnable() {
        @Override
        public void run() {
          scroll.scrollTo(0, 0);
        }
      });
    }
  }

  /**
   * What the picked tab can show, or the line saying why it cannot. Both are decided here rather
   * than where they are caused, because the read is asynchronous and a window changed on one tab
   * lands while another is picked.
   */
  private void showWhatIsReadable() {
    // Only the Solve tab is built. The other two are drawn on their own, and until they are the
    // control still has to say what the hub will hold rather than pretend to two tabs.
    boolean solve = tab == TAB_SOLVE;
    boolean readable = loaded && !steps.isEmpty();
    solveRoot.setVisibility(solve && readable ? View.VISIBLE : View.GONE);
    findViewById(R.id.llAnalysisCases)
        .setVisibility(tab == TAB_CASES && readable ? View.VISIBLE : View.GONE);
    TextView empty = findViewById(R.id.tvAnalysisEmpty);
    boolean unbuilt = tab != TAB_SOLVE && tab != TAB_CASES;
    // Nothing at all until the first read lands, rather than a moment of "you have no solves".
    empty.setVisibility(unbuilt || (loaded && !readable) ? View.VISIBLE : View.GONE);
    empty.setText(unbuilt ? R.string.analysis_tab_soon : R.string.analysis_empty);
  }

  private void load() {
    // No solves asked for: what the Cases tab wants from the history is the statuses, and the
    // solves are only there for a screen that reads the moves back out of them.
    App.INSTANCE.getService().getCaseHistory(0, new DataCallback<CaseHistory>() {
      @Override
      public void onData(final CaseHistory history) {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            known = history.getCases();
            showCases();
          }
        });
      }
    });
    App.INSTANCE.getService().getMethodStatistics(solveType, method, window.solves(),
        new DataCallback<MethodStatistics>() {
          @Override
          public void onData(final MethodStatistics statistics) {
            runOnUiThread(new Runnable() {
              @Override
              public void run() {
                show(statistics);
              }
            });
          }
        });
  }

  private void show(MethodStatistics statistics) {
    this.statistics = statistics;
    steps.clear();
    steps.addAll(statistics.getFamilies());
    // The palette is the method's own step order, so a Roux first block is the colour a cross is.
    palette = StepPalette.of(this, familyCodes());

    loaded = true;
    showWhatIsReadable();
    if (steps.isEmpty()) {
      return;
    }
    showHero(statistics);
    showDeltas();
    // Before the step table: which families have cases is what decides a step row's chevron, and
    // it is known from the statistics alone, so it does not wait on the case history.
    showCases();
    showSteps();
  }

  /** The Cases tab, which waits on two reads and is drawn by whichever of them lands second. */
  private void showCases() {
    if (statistics != null) {
      cases.show(statistics, known, palette);
    }
  }

  private AnalysisCases.Listener casesListener() {
    return new AnalysisCases.Listener() {
      @Override
      public void onCasePicked(String caseCode) {
        CaseAlgorithmsDialog.newInstance(caseCode).show(getSupportFragmentManager(), "case");
      }

      @Override
      public void onDrillPicked(List<String> caseCodes) {
        startActivity(DrillSetupActivity.drillOf(AnalysisActivity.this, caseCodes,
            getString(R.string.analysis_drill_title)));
      }
    };
  }

  /** The mean of the whole solve, and what it was made of. */
  private void showHero(MethodStatistics statistics) {
    long total = 0;
    long best = 0;
    for (StepStats step : steps) {
      total += step.getMeanMs();
      best += step.getBestMs();
    }
    ((TextView) findViewById(R.id.tvAnalysisMean))
        .setText(FormatterService.INSTANCE.formatSolveTime(total));
    // Of the steps rather than of the solve: a solve is timed from its first move and its steps do
    // not have to add up to it, so calling this the solve's own mean would be a different figure.
    ((TextView) findViewById(R.id.tvAnalysisMeanOf)).setText(getResources()
        .getQuantityString(R.plurals.analysis_mean_of, statistics.getSolveCount(),
            statistics.getSolveCount()));
    ((TextView) findViewById(R.id.tvAnalysisBest))
        .setText(FormatterService.INSTANCE.formatSolveTime(best));

    List<SolveStep> segments = new ArrayList<SolveStep>();
    int[] colors = new int[steps.size()];
    for (int i = 0; i < steps.size(); i++) {
      StepStats step = steps.get(i);
      segments.add(new SolveStep(i, step.getCode(), step.getMeanRecognitionMs(), step.getMeanMs(),
          Collections.<SolveStep>emptyList()));
      colors[i] = palette.colorFor(step.getCode());
    }
    ((SolveStepBarView) findViewById(R.id.vAnalysisComposition)).setSteps(segments, colors);
    showLegend(total);
  }

  /** Every segment named on the legend, which is part of what pays for the bright palette. */
  private void showLegend(long total) {
    LinearLayout legend = findViewById(R.id.llAnalysisLegend);
    legend.removeAllViews();
    LayoutInflater inflater = LayoutInflater.from(this);
    for (StepStats step : steps) {
      View cell = inflater.inflate(R.layout.analysis_legend_cell, legend, false);
      cell.findViewById(R.id.vAnalysisLegendSwatch)
          .setBackgroundColor(palette.colorFor(step.getCode()));
      ((TextView) cell.findViewById(R.id.tvAnalysisLegendName)).setText(nameOf(step));
      ((TextView) cell.findViewById(R.id.tvAnalysisLegendShare))
          .setText(percent(total == 0 ? 0 : (double) step.getMeanMs() / total));
      ((TextView) cell.findViewById(R.id.tvAnalysisLegendTime))
          .setText(FormatterService.INSTANCE.formatSolveTime(step.getMeanMs()));
      legend.addView(cell);
    }
  }

  /**
   * Each step's share against what that step usually takes. The card goes away entirely for a method
   * nobody has published figures for, rather than measuring a Roux solve against somebody else's
   * steps.
   */
  private void showDeltas() {
    Map<String, Double> expected =
        StepBaseline.shares(method.getCode().toLowerCase(Locale.US), familyCodes());
    View card = findViewById(R.id.llAnalysisDeltaCard);
    card.setVisibility(expected.isEmpty() ? View.GONE : View.VISIBLE);
    if (expected.isEmpty()) {
      return;
    }

    // Over the steps the table knows, so a step it has no figure for cannot flatter the rest.
    long measured = 0;
    for (StepStats step : steps) {
      if (expected.containsKey(step.getCode())) {
        measured += step.getMeanMs();
      }
    }
    Map<String, Double> deltas = new LinkedHashMap<String, Double>();
    double widest = 0;
    for (StepStats step : steps) {
      Double share = expected.get(step.getCode());
      if (share == null) {
        continue;
      }
      double actual = measured == 0 ? 0 : (double) step.getMeanMs() / measured;
      double delta = (actual - share.doubleValue()) * 100;
      deltas.put(step.getCode(), Double.valueOf(delta));
      widest = Math.max(widest, Math.abs(delta));
    }
    double scale = Math.max(MIN_DELTA_SCALE, Math.ceil(widest));

    LinearLayout rows = findViewById(R.id.llAnalysisDeltaRows);
    rows.removeAllViews();
    LayoutInflater inflater = LayoutInflater.from(this);
    for (StepStats step : steps) {
      Double delta = deltas.get(step.getCode());
      if (delta == null) {
        continue;
      }
      View row = inflater.inflate(R.layout.analysis_delta_row, rows, false);
      ((TextView) row.findViewById(R.id.tvAnalysisDeltaName)).setText(nameOf(step));
      ((DeltaBarView) row.findViewById(R.id.vAnalysisDeltaBar))
          .setDelta((float) (delta.doubleValue() / scale), palette.colorFor(step.getCode()));
      TextView value = row.findViewById(R.id.tvAnalysisDeltaValue);
      value.setText(signed(delta.doubleValue()));
      // Under is not good news and over is not bad: which side of the rule it is on is the fact,
      // and the verdict colours mean something else on this screen.
      value.setTextColor(ContextCompat.getColor(this,
          delta.doubleValue() >= 0 ? R.color.white : R.color.secondary_text));
      rows.addView(row);
    }
  }

  private void showSteps() {
    LinearLayout rows = solveRoot.findViewById(R.id.llCaseTableRows);
    rows.removeAllViews();
    lines.clear();
    LayoutInflater inflater = LayoutInflater.from(this);
    for (StepStats step : steps) {
      CaseRow row = new CaseRow(CaseRow.inflate(inflater, rows));
      int hue = palette.colorFor(step.getCode());
      row.noCount()
          .dot(hue)
          .name(nameOf(step))
          .meter(step.getMeanRecognitionMs(), step.getMeanExecutionMs(), hue)
          .value(0, FormatterService.INSTANCE.formatSolveTime(step.getMeanMs()),
              ContextCompat.getColor(this, R.color.white))
          .value(1, FormatterService.INSTANCE.formatSolveTime(step.getBestMs()),
              ContextCompat.getColor(this, R.color.secondary_text))
          .value(2, FormatterService.INSTANCE.formatSolveTime(step.getWorstMs()),
              ContextCompat.getColor(this, R.color.secondary_text));
      // The chevron is a promise, so only a step whose cases the next tab can list carries one.
      if (cases.holds(step.getCode())) {
        final String family = step.getCode();
        row.chevron();
        row.view().setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            cases.setFamily(family);
            tabs.setSelection(TAB_CASES);
            showTab(TAB_CASES);
            showCases();
          }
        });
      }
      lines.put(step, row);
    }
    rankSteps();
  }

  /** Ranks the step table by whichever heading was last asked for. */
  private void rankSteps() {
    final int column = headings.column();
    final boolean descending = headings.descending();
    List<StepStats> ranked = new ArrayList<StepStats>(steps);
    Collections.sort(ranked, new java.util.Comparator<StepStats>() {
      @Override
      public int compare(StepStats a, StepStats b) {
        int order = Long.compare(value(a, column), value(b, column));
        return descending ? -order : order;
      }
    });
    LinearLayout rows = solveRoot.findViewById(R.id.llCaseTableRows);
    rows.removeAllViews();
    for (StepStats step : ranked) {
      CaseRow row = lines.get(step);
      row.rank(column);
      rows.addView(row.view());
    }
    headings.refresh();
  }

  private static long value(StepStats step, int column) {
    switch (column) {
      case 1:
        return step.getMeanMs();
      case 2:
        return step.getBestMs();
      case 3:
        return step.getWorstMs();
      default:
        return step.getCount();
    }
  }

  /** The step codes in solving order, which is what both the palette and the baseline key on. */
  private List<String> familyCodes() {
    List<String> codes = new ArrayList<String>();
    for (StepStats step : steps) {
      codes.add(step.getCode());
    }
    return codes;
  }

  private String nameOf(StepStats step) {
    return Utils.toSmartCubeStepLocalizedName(this, step.getCode(), steps.indexOf(step));
  }

  private String percent(double share) {
    return String.format(Locale.getDefault(), PERCENT_FORMAT,
        Integer.valueOf((int) Math.round(share * 100)));
  }

  private String signed(double points) {
    return getString(points >= 0 ? R.string.analysis_delta_over : R.string.analysis_delta_under,
        FormatterService.INSTANCE.formatFloat(Math.abs(points), 1));
  }
}
