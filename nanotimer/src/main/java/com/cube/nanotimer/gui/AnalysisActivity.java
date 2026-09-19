package com.cube.nanotimer.gui;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
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
import com.cube.nanotimer.cube.AlgorithmFiguresReader;
import com.cube.nanotimer.cube.SolveTypeMethod;
import com.cube.nanotimer.gui.widget.AnalysisCases;
import com.cube.nanotimer.gui.widget.AnalysisHelpDialog;
import com.cube.nanotimer.gui.widget.CaseRow;
import com.cube.nanotimer.gui.widget.CaseTableHeadings;
import com.cube.nanotimer.gui.widget.dialog.CaseAlgorithmsDialog;
import com.cube.nanotimer.gui.widget.SegmentedControl;
import com.cube.nanotimer.gui.widget.SmartCubeConnectDialog;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.session.AlgorithmFigures;
import com.cube.nanotimer.session.CaseKnowledge;
import com.cube.nanotimer.session.MethodStatistics;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.util.view.AlgorithmFiguresBarView;
import com.cube.nanotimer.util.view.DeltaBarView;
import com.cube.nanotimer.util.view.KnowledgeRingView;
import com.cube.nanotimer.util.view.SolveStepBarView;
import com.cube.nanotimer.util.view.StepPalette;
import com.cube.nanotimer.vo.CaseHistory;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveStep;
import com.cube.nanotimer.vo.SolveTime;
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
 *
 * <p>The step table's third column is the spread and not the worst. A maximum is owned by one
 * observation and never improves, so inside a window it only falls when the bad solve ages out and
 * over every solve it can never fall at all; a best has the same fragility and keeps its column
 * because a best is read as a record, where a worst is read as a description of how bad the step
 * routinely gets, which is not what a maximum measures.
 *
 * <p><b>Where a reader has no cube-read solve anywhere, the screen reads {@link AnalysisSample}
 * instead.</b> Someone who owns no cube cannot be told to go and do more solves with one, and a hub
 * that answers them with one grey sentence sells neither the cube nor itself. The condition is
 * about the reader and not about the window: a reader who owns a cube and has simply not used it on
 * this solve type is told exactly that, since for them the example would be somebody else's figures
 * standing over a screen they have a real version of. That is the arrangement never allowed here,
 * because an invented figure beside a real one is what makes the true number look invented.
 */
public class AnalysisActivity extends NanoTimerActivity {

  public static final String EXTRA_SOLVE_TYPE = "analysisSolveType";
  /** Which tab to open on, by ordinal, for a door that knows what it is pointing at. */
  public static final String EXTRA_TAB = "analysisTab";
  /** The family the Cases tab opens narrowed to, for a door that knows which step it came from. */
  public static final String EXTRA_FAMILY = "analysisFamily";

  public static final int TAB_SOLVE = 0;
  private static final int TAB_CASES = 1;
  public static final int TAB_PLAN = 2;

  /** What a plan says, in the order it says it, on a tab that cannot yet say any of it. */
  private static final int[] PLAN_POINTS = {R.string.analysis_plan_point_one,
      R.string.analysis_plan_point_two, R.string.analysis_plan_point_three};

  /** How the shares are written, which is a whole number of points either way. */
  private static final String PERCENT_FORMAT = "%d%%";

  /** The narrowest the delta scale ever gets, so a solver in proportion does not read as wild. */
  private static final int MIN_DELTA_SCALE = 6;

  // No count column: a step is reached in nearly every solve, so its count is the solve count and
  // ranking by it would rank nothing. The rows leave it out too.
  private static final int[] HEADING_LABELS = {0, R.string.drill_summary_cell_mean,
      R.string.drill_summary_cell_best, R.string.drill_stats_column_spread};
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

  /** What the query returned, kept apart from what is drawn: the sample stands in front of it. */
  private MethodStatistics read;
  private List<CaseKnowledge> readKnown = Collections.emptyList();
  /** The standard-algorithms card's figures, null until the window's solves have been read again. */
  private Map<String, AlgorithmFigures> algorithmFigures;
  /**
   * Whether a cube has ever read a solve of theirs, of any solve type. Null until that read lands.
   * It is what separates the two readers an empty window otherwise looks the same to: one who owns
   * no cube, for whom the example is the only way to see what the screen is, and one who owns one
   * and has simply not used it here, for whom it is somebody else's figures over their own screen.
   */
  private Boolean hasOwnSolves;
  /** Whether this solve type's scrambles make a whole solve, which is what a breakdown reads. */
  private boolean wholeSolve;
  /** Whether a smart cube can read this puzzle at all, which only a 3x3 can. */
  private boolean readablePuzzle;
  /** Whether they asked to see their own instead, which is an empty screen and honestly so. */
  private boolean leftSample;
  private boolean ctaDismissed;
  private int[] chipPadding = {0, 0, 0, 0};

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
    // A trainer's scramble sets up one step rather than a solve, so a solve of it has no cross to
    // measure and no PLL to reach: read as a whole solve it would print a breakdown of nothing.
    wholeSolve = solveType.getScrambleType() == null || solveType.getScrambleType().isDefault();
    readablePuzzle = solveType.getCubeTypeId() == CubeType.THREE_BY_THREE.getId();
    window = AnalysisWindow.of(Options.INSTANCE.getAnalysisWindow(
        AnalysisWindow.HUNDRED.ordinal()));
    palette = StepPalette.cfop(this);

    showContext();

    tabs = new SegmentedControl(this, (LinearLayout) findViewById(R.id.llAnalysisTabs),
        new String[] {getString(R.string.analysis_tab_solve), getString(R.string.analysis_tab_cases),
            getString(R.string.analysis_tab_plan)},
        new SegmentedControl.Listener() {
          @Override
          public void onSegmentPicked(int index) {
            showTab(index);
          }
        });
    // The one tab that will cost money is otherwise the one with no sign on it.
    tabs.setSegmentMarked(TAB_PLAN, true);
    solveRoot = findViewById(R.id.llAnalysisSolve);
    headings = new CaseTableHeadings(solveRoot, HEADING_LABELS,
        OPENS_DESCENDING, CaseTableHeadings.LABEL_COLUMN, new CaseTableHeadings.Listener() {
          @Override
          public void onRanked(int column, boolean descending) {
            rankSteps();
          }
        });
    headings.setLabel(R.string.analysis_column_step);
    headings.rankableLabel(false); // by step is solving order, cross first, as the delta card reads
    cases = new AnalysisCases(this, findViewById(R.id.llAnalysisCases), casesListener());
    cases.setFamily(getIntent().getStringExtra(EXTRA_FAMILY));
    showPlanPoints();
    wireSample();

    showTabStrip();
    tabs.setSelection(onlySolve() ? TAB_SOLVE : getIntent().getIntExtra(EXTRA_TAB, TAB_SOLVE));
    showTab(tabs.getSelection());
    loadKnowledge();
    loadOwnSolves();
    load();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.analysis_menu, menu);
    windowItem = menu.findItem(R.id.itAnalysisWindow);
    TextView chip = (TextView) windowItem.getActionView();
    // A background carries its own padding, and a shape has none, so the chip's is kept by hand.
    chipPadding = new int[] {chip.getPaddingLeft(), chip.getPaddingTop(), chip.getPaddingRight(),
        chip.getPaddingBottom()};
    chip.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (sampleOffered()) {
          askForMode();
        } else {
          askForWindow();
        }
      }
    });
    showWindow();
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == R.id.itAnalysisHelp) {
      DialogUtils.showFragment(this, AnalysisHelpDialog.newInstance(tab, sampling()));
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  /**
   * Which slice of history the figures are read from, on the same line as what they are about.
   *
   * <p>Over the sample it is whose figures they are instead. There is nothing to window: the sample
   * is one hundred-solve tally rather than a hundred solves, so a control offering to read fifty of
   * them would be promising a filter it cannot run.
   */
  private void showWindow() {
    if (windowItem == null) {
      return;
    }
    // A trainer has no window worth offering: no slice of it will ever hold a step.
    windowItem.setVisible(readAsSolve());
    if (!readAsSolve()) {
      return;
    }
    boolean sampling = sampling();
    TextView chip = (TextView) windowItem.getActionView();
    int label;
    if (sampleOffered()) {
      label = sampling ? R.string.analysis_sample_chip : R.string.analysis_sample_mine;
    } else {
      label = window.getLabelId();
    }
    chip.setText(getString(R.string.analysis_window_chip, getString(label)));
    chip.setBackgroundResource(sampling ? R.drawable.analysis_sample_chip : R.drawable.row_chip);
    chip.setPadding(chipPadding[0], chipPadding[1], chipPadding[2], chipPadding[3]);
    chip.setTextColor(ContextCompat.getColor(this,
        sampling ? R.color.sample_ink : R.color.secondary_text));
  }

  /** What the screen is about, which over the sample is whose solves rather than which of theirs. */
  private void showContext() {
    ActionBar bar = getSupportActionBar();
    if (bar == null) {
      return;
    }
    // The context is what the screen is about rather than a control, so it rides on the app bar.
    String name = Utils.toSolveTypeLocalizedName(this, solveType.getName());
    if (sampling()) {
      bar.setSubtitle(getString(R.string.analysis_sample_subtitle));
    } else if (readAsSolve()) {
      bar.setSubtitle(getString(R.string.analysis_context, name,
          getString(SolveTypeMethod.nameOf(method))));
    } else {
      // Naming a method here would claim these solves are read as one, which is the whole point of
      // what this screen is refusing to do for a trainer.
      bar.setSubtitle(name);
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

  /**
   * Whose figures the hub is reading. Offered only where their own window holds nothing, and their
   * own is still one tap away, which is what stops the sample being something done to them.
   */
  private void askForMode() {
    String[] labels = {getString(R.string.analysis_sample_chip),
        getString(R.string.analysis_sample_mine)};
    new AlertDialog.Builder(this, R.style.NanoTimerDialogTheme)
        .setTitle(R.string.analysis_sample_showing)
        .setSingleChoiceItems(labels, sampling() ? 0 : 1, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
            leftSample = which == 1;
            draw();
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
    // Plan is the one tab that reads nothing: it says what a plan is, and there are none to have.
    boolean measured = tab == TAB_SOLVE || tab == TAB_CASES;
    boolean readable = loaded && !steps.isEmpty();
    solveRoot.setVisibility(tab == TAB_SOLVE && readable ? View.VISIBLE : View.GONE);
    findViewById(R.id.llAnalysisCases)
        .setVisibility(tab == TAB_CASES && readable ? View.VISIBLE : View.GONE);
    findViewById(R.id.llAnalysisPlan).setVisibility(tab == TAB_PLAN ? View.VISIBLE : View.GONE);
    // Nothing at all until the first read lands, rather than a moment of "you have no solves".
    TextView empty = (TextView) findViewById(R.id.tvAnalysisEmpty);
    empty.setVisibility(measured && loaded && !readable ? View.VISIBLE : View.GONE);
    empty.setText(emptyLine());
  }

  /**
   * Whether a solve of this type is something a cube reads end to end, which is what every figure
   * on the Solve tab is a share of. A puzzle no cube turns and a scramble that sets up one step
   * both fail it, and the screen says which rather than sending the reader off to solve more.
   */
  private boolean readAsSolve() {
    return canAnalyse(solveType);
  }

  /**
   * Whether this solve type has a breakdown to offer at all, asked before the screen is opened:
   * the drawer leaves its row out for a type that fails it rather than opening a hub whose whole
   * content is the reason it is empty.
   *
   * <p>Three ways to fail, and none of them is a matter of solving more. A cube only turns a 3x3.
   * A trainer's scramble sets up one step, so there is no whole solve to take shares of. And a type
   * that times its own steps already has a breakdown, the solver's own: reading a second one off
   * the cube would put two different answers to one question in the same app.
   */
  public static boolean canAnalyse(SolveType solveType) {
    return solveType != null
        && solveType.getCubeTypeId() == CubeType.THREE_BY_THREE.getId()
        && (solveType.getScrambleType() == null || solveType.getScrambleType().isDefault())
        && !solveType.hasSteps();
  }

  /** The strip goes away entirely where there is only one tab to pick. */
  private void showTabStrip() {
    boolean only = onlySolve();
    findViewById(R.id.llAnalysisTabs).setVisibility(only ? View.GONE : View.VISIBLE);
    if (only && tab != TAB_SOLVE) {
      tabs.setSelection(TAB_SOLVE); // the control is hidden, not forgotten: it can come back
      showTab(TAB_SOLVE);
    }
  }

  /**
   * Whether the hub is this solve type's Solve tab and nothing else. A blind solve is read as a
   * memo and the pieces it solved: there is no set to deal a case from, so the Cases tab can only
   * ever say so, and a plan is written out of cases. One tab is not a choice, so the strip goes.
   */
  private boolean onlySolve() {
    return method == CubeMethod.BLIND;
  }

  /**
   * Why there is nothing to show, in the order the reasons rule each other out. Every reason
   * {@link #canAnalyse} refuses a type for has a line here, so a hub opened for one anyway says
   * which rather than telling the reader to go and solve more, which could never help.
   */
  private int emptyLine() {
    if (!readablePuzzle) {
      return R.string.analysis_empty_puzzle;
    }
    if (!wholeSolve) {
      return R.string.analysis_empty_scramble;
    }
    return solveType.hasSteps() ? R.string.analysis_empty_steps : R.string.analysis_empty;
  }

  /**
   * What the solver knows, read once for the life of the screen. It does not depend on the window
   * the figures are read over: whether a case goes in unaided is a fact about the solver.
   *
   * <p>No solves asked for. What the Cases tab wants from the history is the statuses, and the
   * solves come along only for a screen that reads the moves back out of them.
   */
  private void loadKnowledge() {
    App.INSTANCE.getService().getCaseHistory(0, new DataCallback<CaseHistory>() {
      @Override
      public void onData(final CaseHistory history) {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            readKnown = history.getCases();
            // Which read lands first is not fixed, and the sample owns both halves or neither.
            if (!sampling()) {
              known = readKnown;
              showCases();
            }
          }
        });
      }
    });
  }

  /** Asked once for the life of the screen: owning a cube is not a fact about the window. */
  private void loadOwnSolves() {
    App.INSTANCE.getService().hasAnySmartcubeSolve(new DataCallback<Boolean>() {
      @Override
      public void onData(final Boolean any) {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            hasOwnSolves = any;
            // Which read lands first is not fixed, and this one can turn the example off.
            if (loaded) {
              draw();
            }
          }
        });
      }
    });
  }

  private void load() {
    loadAlgorithmFigures();
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

  /**
   * Replays every solve in the window, so it runs on a thread of its own rather than holding up the
   * service, and a read that lands after the window has changed again is dropped.
   */
  private void loadAlgorithmFigures() {
    algorithmFigures = null;
    showAlgorithmFigures();
    final AnalysisWindow asked = window;
    CubeMethod solved = SolveTypeMethod.of(solveType);
    if (solved != CubeMethod.CFOP || !readAsSolve()) {
      return;
    }
    App.INSTANCE.getService().getMethodSolves(solveType, solved, window.solves(),
        new DataCallback<List<SolveTime>>() {
          @Override
          public void onData(final List<SolveTime> solves) {
            new Thread(new Runnable() {
              @Override
              public void run() {
                final Map<String, AlgorithmFigures> figures =
                    AlgorithmFiguresReader.readFrom(solves);
                runOnUiThread(new Runnable() {
                  @Override
                  public void run() {
                    if (asked == window && !isFinishing()) {
                      algorithmFigures = figures;
                      showAlgorithmFigures();
                    }
                  }
                });
              }
            }).start();
          }
        });
  }

  private void showAlgorithmFigures() {
    View card = findViewById(R.id.llAnalysisAlgorithmsCard);
    boolean any = false;
    if (algorithmFigures != null) {
      for (AlgorithmFigures figures : algorithmFigures.values()) {
        any |= figures.getNotSeen() < figures.getSetSize();
      }
    }
    // Never over the example: its steps are invented, and these would be the reader's own.
    boolean shown = any && !sampling();
    card.setVisibility(shown ? View.VISIBLE : View.GONE);
    if (!shown) {
      return;
    }
    LinearLayout rows = findViewById(R.id.llAnalysisAlgorithmsRows);
    rows.removeAllViews();
    LayoutInflater inflater = LayoutInflater.from(this);
    for (Map.Entry<String, AlgorithmFigures> family : algorithmFigures.entrySet()) {
      AlgorithmFigures figures = family.getValue();
      View row = inflater.inflate(R.layout.analysis_algorithms_row, rows, false);
      ((TextView) row.findViewById(R.id.tvAnalysisAlgorithmsName))
          .setText(Utils.toSmartCubeStepLocalizedName(this, family.getKey(), 0));
      ((AlgorithmFiguresBarView) row.findViewById(R.id.vAnalysisAlgorithmsBar)).setCounts(
          figures.getStandard(), figures.getNotStandard(), figures.getSetSize());
      ((TextView) row.findViewById(R.id.tvAnalysisAlgorithmsCount)).setText(getString(
          R.string.analysis_algorithms_count, figures.getStandard(), figures.getSetSize()));
      ((TextView) row.findViewById(R.id.tvAnalysisAlgorithmsExtra))
          .setText(String.valueOf(figures.getExtraMoves()));
      rows.addView(row);
    }

    ViewGroup key = findViewById(R.id.llAnalysisAlgorithmsKey);
    key.removeAllViews();
    int hue = ContextCompat.getColor(this, R.color.lightblue);
    addAlgorithmsKey(key, inflater, hue, R.string.analysis_algorithms_standard);
    addAlgorithmsKey(key, inflater, hue & 0x00FFFFFF | KnowledgeRingView.SEEN_ALPHA << 24,
        R.string.analysis_algorithms_not_standard);
    addAlgorithmsKey(key, inflater, ContextCompat.getColor(this, R.color.hero_inset),
        R.string.analysis_algorithms_not_seen);
  }

  private void addAlgorithmsKey(ViewGroup key, LayoutInflater inflater, int color, int name) {
    View entry = inflater.inflate(R.layout.analysis_algorithms_key, key, false);
    entry.findViewById(R.id.vAnalysisAlgorithmsKeySwatch).setBackgroundColor(color);
    ((TextView) entry.findViewById(R.id.tvAnalysisAlgorithmsKeyName)).setText(name);
    key.addView(entry);
  }

  private void show(MethodStatistics statistics) {
    read = statistics;
    loaded = true;
    draw();
  }

  /**
   * Whether the example is on offer at all. Three things have to hold: this solve type makes whole
   * solves, its window holds none of them, and no cube has ever read one of theirs anywhere. The
   * last is what keeps an invented figure away from anybody who has a real one to compare it with.
   */
  private boolean sampleOffered() {
    return readAsSolve() && loaded && read != null && read.getFamilies().isEmpty()
        && Boolean.FALSE.equals(hasOwnSolves);
  }

  /** Everything the figures decide, off whichever set of them is being read. */
  private void draw() {
    boolean sampling = sampling();
    statistics = sampling ? AnalysisSample.statistics() : read;
    known = sampling ? AnalysisSample.knowledge() : readKnown;
    // The example solves CFOP whatever the reader's own type resolves to, and a figure keyed on
    // the method has to name the solve being drawn rather than the one that is not there.
    method = sampling ? CubeMethod.CFOP : SolveTypeMethod.of(solveType);
    showTabStrip(); // the example solves CFOP, so it has the other two tabs even here
    showContext();
    showWindow();
    showSample();
    ((TextView) findViewById(R.id.tvAnalysisDeltaLabel)).setText(
        getString(R.string.analysis_delta_label, getString(SolveTypeMethod.nameOf(method))));
    ((TextView) findViewById(R.id.tvAnalysisDeltaLimit)).setText(
        sampling ? R.string.analysis_delta_limit_sample : R.string.analysis_delta_limit);

    steps.clear();
    steps.addAll(statistics.getFamilies());
    // The palette is the method's own step order, so a Roux first block is the colour a cross is.
    palette = StepPalette.of(this, familyCodes());

    showWhatIsReadable();
    if (steps.isEmpty()) {
      return;
    }
    showHero(statistics);
    showDeltas();
    showAlgorithmFigures();
    // Before the step table: which families have cases is what decides a step row's chevron, and
    // it is known from the statistics alone, so it does not wait on the case history.
    showCases();
    showSteps();
  }

  /** Whether the example is standing in for a window of the reader's that holds none. */
  private boolean sampling() {
    return sampleOffered() && !leftSample;
  }

  private void wireSample() {
    findViewById(R.id.buAnalysisSampleConnect).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        DialogUtils.showFragment(AnalysisActivity.this, new SmartCubeConnectDialog());
      }
    });
    findViewById(R.id.buAnalysisSampleBrowse).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        ctaDismissed = true;
        showSample();
      }
    });
  }

  /**
   * The badge and the one thing there is to do about it. The badge sits above the tabs and outside
   * the scroll, because it is a caveat on every figure under it rather than a card among them.
   */
  private void showSample() {
    boolean sampling = sampling();
    findViewById(R.id.llAnalysisSampleBanner).setVisibility(sampling ? View.VISIBLE : View.GONE);
    if (sampling) {
      ((TextView) findViewById(R.id.tvAnalysisSampleBanner)).setText(
          getString(R.string.analysis_sample_banner, statistics.getSolveCount()));
    }
    // At the foot of Solve, so the whole reading is walked before anything is asked for.
    findViewById(R.id.llAnalysisSampleCta)
        .setVisibility(sampling && !ctaDismissed ? View.VISIBLE : View.GONE);
  }

  /**
   * What a plan would say, which is all this tab can say. The three points are the shapes a plan
   * takes, never an example of one: a plan the app did not write, drawn beside figures it did, is
   * the one arrangement that can make a true number look invented.
   */
  private void showPlanPoints() {
    LinearLayout points = findViewById(R.id.llAnalysisPlanPoints);
    LayoutInflater inflater = LayoutInflater.from(this);
    for (int i = 0; i < PLAN_POINTS.length; i++) {
      View point = inflater.inflate(R.layout.analysis_plan_point, points, false);
      ((TextView) point.findViewById(R.id.tvAnalysisPointRank)).setText(String.valueOf(i + 1));
      ((TextView) point.findViewById(R.id.tvAnalysisPointText)).setText(PLAN_POINTS[i]);
      points.addView(point);
    }
  }

  /** The Cases tab, which waits on two reads and is drawn by whichever of them lands second. */
  private void showCases() {
    if (statistics != null) {
      cases.show(statistics, known, palette, method);
    }
  }

  private AnalysisCases.Listener casesListener() {
    return new AnalysisCases.Listener() {
      @Override
      public void onCasePicked(String caseCode) {
        CaseAlgorithmsDialog.newInstance(caseCode).show(getSupportFragmentManager(), "case");
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
      segments.add(new SolveStep(i, step.getCode(), step.getMeanRecognitionMs(),
          step.getMeanExecutionMs(), Collections.<SolveStep>emptyList()));
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
    // Whether any step opens its cases at all: where none does, no row keeps room for a chevron.
    boolean anyOpens = false;
    for (StepStats step : steps) {
      anyOpens |= cases.holds(step.getCode());
    }
    headings.reserveChevron(anyOpens);
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
          .value(2, quotable(step, 3) ? FormatterService.INSTANCE.formatSolveTime(step.getStdDevMs())
                  : getString(R.string.NA),
              ContextCompat.getColor(this, R.color.secondary_text));
      // The chevron is a promise, so only a step whose cases the next tab can list carries one.
      // The others keep its room, or the figures would not line up down the table.
      boolean opens = cases.holds(step.getCode());
      if (anyOpens) {
        row.chevron(opens);
      }
      if (opens) {
        final String family = step.getCode();
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
        // A step that cannot quote the ranked column sits under the ones that can, whichever way
        // round the column is turned, as it does on the Cases tab.
        boolean quotableA = quotable(a, column);
        boolean quotableB = quotable(b, column);
        if (quotableA != quotableB) {
          return quotableA ? -1 : 1;
        }
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

  /** A spread needs two solves to exist: off one, 0.00 would read as the steadiest step there is. */
  private static boolean quotable(StepStats step, int column) {
    return column != 3 || step.getCount() >= 2;
  }

  private long value(StepStats step, int column) {
    if (column == CaseTableHeadings.LABEL_COLUMN) {
      return steps.indexOf(step); // the query returns the steps in solving order
    }
    switch (column) {
      case 1:
        return step.getMeanMs();
      case 2:
        return step.getBestMs();
      case 3:
        return step.getStdDevMs();
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
