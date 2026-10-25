package com.cube.nanotimer.gui;

import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
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
import com.cube.nanotimer.coach.CoachPayloadBuilder;
import com.cube.nanotimer.coach.StepBaseline;
import com.cube.nanotimer.coach.StoredCoachPlan;
import com.cube.nanotimer.cube.AlgorithmFiguresReader;
import com.cube.nanotimer.cube.CrossPauses;
import com.cube.nanotimer.cube.SolveReading;
import com.cube.nanotimer.cube.SolveReadings;
import com.cube.nanotimer.cube.SolveTypeMethod;
import com.cube.nanotimer.gui.widget.AnalysisCases;
import com.cube.nanotimer.gui.widget.AnalysisHelpDialog;
import com.cube.nanotimer.gui.widget.CaseRow;
import com.cube.nanotimer.gui.widget.CaseTableHeadings;
import com.cube.nanotimer.gui.widget.CoachPlanStatus;
import com.cube.nanotimer.gui.widget.CoachPlanView;
import com.cube.nanotimer.gui.widget.dialog.CaseAlgorithmsDialog;
import com.cube.nanotimer.gui.widget.dialog.DrillCasesDialog;
import com.cube.nanotimer.gui.widget.SegmentedControl;
import com.cube.nanotimer.gui.widget.SmartCubeConnectDialog;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.session.AlgorithmFigures;
import com.cube.nanotimer.session.CaseKnowledge;
import com.cube.nanotimer.session.MethodStatistics;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.coach.CoachAccess;
import com.cube.nanotimer.util.coach.CoachBilling;
import com.cube.nanotimer.util.coach.CoachClient;
import com.cube.nanotimer.util.coach.CoachPlanWorker;
import com.cube.nanotimer.util.coach.CoachPlans;
import com.cube.nanotimer.util.coach.CoachPractice;
import com.cube.nanotimer.util.coach.CoachRead;
import com.cube.nanotimer.util.coach.CoachReply;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.util.view.AlgorithmFiguresBarView;
import com.cube.nanotimer.util.view.DeltaBarView;
import com.cube.nanotimer.util.view.KnowledgeRingView;
import com.cube.nanotimer.util.view.SolveStepBarView;
import com.cube.nanotimer.util.view.SolveStepBars;
import com.cube.nanotimer.util.view.StepPalette;
import com.cube.nanotimer.vo.CaseHistory;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveType;
import com.cube.nanotimer.vo.StepStats;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
public class AnalysisActivity extends NanoTimerActivity implements DrillCasesDialog.Listener {

  public static final String EXTRA_SOLVE_TYPE = "analysisSolveType";
  /** Which tab to open on, by ordinal, for a door that knows what it is pointing at. */
  public static final String EXTRA_TAB = "analysisTab";
  /** The family the Cases tab opens narrowed to, for a door that knows which step it came from. */
  public static final String EXTRA_FAMILY = "analysisFamily";

  public static final int TAB_SOLVE = 0;
  private static final int TAB_CASES = 1;
  public static final int TAB_PLAN = 2;


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
  private CoachPlanView planView;
  /** Null in a build made without a coach to ask. */
  private CoachClient coachClient;
  private CoachRead.Listener readListener;
  /** Whether this screen has asked the service for practice sets yet: once is enough. */
  private boolean practiceAsked;
  /** The cases cleared in learning drills when the sets were asked for: a new one asks again. */
  private Set<String> learnedAtAsk = Collections.emptySet();
  /** The practice sets on show, drawn again when the user changes the cases to learn. */
  private List<CoachPractice.Set> practiceSets = Collections.emptyList();
  private CoachPlanStatus planStatus;
  private CoachBilling billing;
  private CoachBilling.Listener billingListener;
  private MethodStatistics statistics;
  private List<StepStats> oneAlgorithmCases = Collections.emptyList();
  private List<CaseKnowledge> known = Collections.emptyList();

  /** What the query returned, kept apart from what is drawn: the sample stands in front of it. */
  private MethodStatistics read;
  private List<StepStats> readOneAlgorithm = Collections.emptyList();
  private List<CaseKnowledge> readKnown = Collections.emptyList();
  private boolean knownRead;
  /** The standard-algorithms card's figures, null until the window's solves have been read again. */
  private Map<String, AlgorithmFigures> algorithmFigures;
  /** The cross's mean stops over the window, read with the algorithm figures; 0 until then. */
  private long crossPauseMs;
  /** Whether that read is on its way, which is what the card shows in place of its rows. */
  private boolean algorithmsReading;
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
    planView = new CoachPlanView(this, (LinearLayout) findViewById(R.id.llAnalysisPlanAreas),
        solveType);
    wireRead();
    wireSample();

    showTabStrip();
    tabs.setSelection(onlySolve() ? TAB_SOLVE : getIntent().getIntExtra(EXTRA_TAB, TAB_SOLVE));
    showTab(tabs.getSelection());
    loadKnowledge();
    loadOwnSolves();
    loadPlan();
    load();
  }

  @Override
  protected void onResume() {
    super.onResume();
    planStatus.refresh(); // days pass, and the solve types may have changed behind this screen
    if (practiceAsked && !learnedAtAsk.equals(Options.INSTANCE.getLearnedCases())) {
      practiceAsked = false; // a learning drill cleared a case, so the next ones are due
      showRead();
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    CoachRead.of(solveType).removeListener(readListener);
    if (billing != null) {
      billing.removeListener(billingListener);
    }
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
    // Plan reads no window: it shows the plan kept for the solve type, or what a plan is.
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
   * the drawer dims its rows for a type that fails it rather than opening a hub whose whole content
   * is the reason it is empty.
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
            knownRead = true;
            // Which read lands first is not fixed, and the sample owns both halves or neither.
            if (!sampling()) {
              known = readKnown;
              showCases();
            }
            if (findViewById(R.id.llAnalysisPractice).getVisibility() == View.VISIBLE
                && findViewById(R.id.tvAnalysisPracticeLocked).getVisibility() == View.VISIBLE) {
              planView.setPracticeTeaser((LinearLayout) findViewById(R.id.llAnalysisPracticeSets),
                  learnNextOffered());
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
            App.INSTANCE.getService().getOneAlgorithmCaseStatistics(solveType, method,
                window.solves(), new DataCallback<List<StepStats>>() {
                  @Override
                  public void onData(final List<StepStats> oneAlgorithm) {
                    runOnUiThread(new Runnable() {
                      @Override
                      public void run() {
                        show(statistics, oneAlgorithm);
                      }
                    });
                  }
                });
          }
        });
  }

  /**
   * Reads every solve in the window that has not been read before, off the service's own thread,
   * and drops an answer that lands after the window has changed again. What it had to read is
   * handed back to be kept, so opening this tab again reads nothing.
   */
  private void loadAlgorithmFigures() {
    algorithmFigures = null;
    crossPauseMs = 0;
    final AnalysisWindow asked = window;
    CubeMethod solved = SolveTypeMethod.of(solveType);
    algorithmsReading = solved == CubeMethod.CFOP && readAsSolve();
    showAlgorithmFigures();
    if (!algorithmsReading) {
      return;
    }
    App.INSTANCE.getService().getMethodSolves(solveType, solved, window.solves(),
        new DataCallback<List<SolveTime>>() {
          @Override
          public void onData(final List<SolveTime> solves) {
            App.INSTANCE.getService().getSolveReadings(SolveReadings.idsOf(solves),
                SolveReading.VERSION, new DataCallback<Map<Long, String>>() {
                  @Override
                  public void onData(Map<Long, String> kept) { // off the main thread already
                    SolveReadings readings = new SolveReadings(kept);
                    final Map<String, AlgorithmFigures> figures =
                        AlgorithmFiguresReader.readFrom(solves, readings);
                    final long crossPause = CrossPauses.meanOf(solves, readings);
                    App.INSTANCE.getService().saveSolveReadings(readings.unkept(),
                        SolveReading.VERSION, null);
                    runOnUiThread(new Runnable() {
                      @Override
                      public void run() {
                        if (asked == window && !isFinishing()) {
                          algorithmFigures = figures;
                          algorithmsReading = false;
                          showAlgorithmFigures();
                          crossPauseMs = crossPause;
                          showComposition();
                        }
                      }
                    });
                  }
                });
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
    boolean shown = (any || algorithmsReading) && !sampling();
    card.setVisibility(shown ? View.VISIBLE : View.GONE);
    LinearLayout rows = findViewById(R.id.llAnalysisAlgorithmsRows);
    ViewGroup key = findViewById(R.id.llAnalysisAlgorithmsKey);
    // The replay takes a moment, so the card waits in place rather than arriving under a table the
    // reader has already scrolled past.
    findViewById(R.id.llAnalysisAlgorithmsReading)
        .setVisibility(algorithmsReading ? View.VISIBLE : View.GONE);
    rows.setVisibility(algorithmsReading ? View.GONE : View.VISIBLE);
    key.setVisibility(algorithmsReading ? View.GONE : View.VISIBLE);
    if (!shown || algorithmsReading) {
      return;
    }
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

  private void show(MethodStatistics statistics, List<StepStats> oneAlgorithm) {
    read = statistics;
    readOneAlgorithm = oneAlgorithm;
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
    oneAlgorithmCases = sampling ? AnalysisSample.oneAlgorithmCases() : readOneAlgorithm;
    known = sampling ? AnalysisSample.knowledge() : readKnown;
    // The example solves CFOP whatever the reader's own type resolves to, and a figure keyed on
    // the method has to name the solve being drawn rather than the one that is not there.
    method = sampling ? CubeMethod.CFOP : SolveTypeMethod.of(solveType);
    showTabStrip(); // the example solves CFOP, so it has the other two tabs even here
    showContext();
    showWindow();
    showSample();
    showRead();
    ((TextView) findViewById(R.id.tvAnalysisDeltaLabel)).setText(
        getString(R.string.analysis_delta_label, getString(SolveTypeMethod.nameOf(method))));

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

  private void wireRead() {
    coachClient = CoachClient.configured(this);
    billing = CoachBilling.of(this);
    planStatus = new CoachPlanStatus(this, (ViewGroup) findViewById(R.id.llAnalysisPlan), solveType,
        coachClient, billing);
    final CoachRead read = CoachRead.of(solveType);
    readListener = new CoachRead.Listener() {
      @Override
      public void onReadChanged(CoachRead changed) {
        showRead();
        if (changed.getState() == CoachRead.State.IDLE) {
          practiceAsked = false; // the new plan's drilled cases leave the practice sets
          loadPlan(); // idle after reading means a plan was saved
        }
      }
    };
    read.setListener(readListener);
    findViewById(R.id.buAnalysisPlanAskNow).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        CoachPlanWorker.askNow(AnalysisActivity.this, solveType);
      }
    });
    wireBilling();
  }

  private void wireBilling() {
    if (billing == null) {
      return;
    }
    billingListener = new CoachBilling.Listener() {
      @Override
      public void onBillingChanged() {
        showRead(); // a purchase changes what the tab offers, and Play's prices what it says
      }
    };
    billing.addListener(billingListener);
    findViewById(R.id.buAnalysisPlanCopyPurchase).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        String token = CoachAccess.of(AnalysisActivity.this).getPurchaseToken();
        if (token != null) {
          DialogUtils.copyToClipboard(AnalysisActivity.this, "purchase token", token,
              R.string.coach_id_copied);
        }
      }
    });
  }

  private boolean debuggable() {
    return (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
  }

  /**
   * Whether the tab says what Play last answered. A release does only when built for it, since a
   * debug build is not a package Play sells to.
   */
  private boolean purchaseDebugOffered() {
    return billing != null
        && (debuggable() || getResources().getBoolean(R.bool.coach_purchase_debug));
  }

  /**
   * What an ask changes on the tab: the status row, and the practice where there is a coach and
   * something of theirs to read. The coach reads CFOP only, and never the example: a plan written
   * off somebody else's figures would be kept as theirs.
   */
  private void showRead() {
    planStatus.refresh();
    showPractice(coachClient != null && readAsSolve() && loaded && !sampling()
        && SolveTypeMethod.of(solveType) == CubeMethod.CFOP);
    showAskNow();
  }

  /**
   * Debug builds only: the button that runs the weekly job now, and how its last ask ended. What
   * Play last answered sits under it.
   */
  private void showAskNow() {
    boolean offered = debuggable() && coachClient != null && CoachPlans.eligible(solveType);
    findViewById(R.id.llAnalysisPlanDebug)
        .setVisibility(offered || purchaseDebugOffered() ? View.VISIBLE : View.GONE);
    findViewById(R.id.buAnalysisPlanAskNow).setVisibility(offered ? View.VISIBLE : View.GONE);
    CoachRead read = CoachRead.of(solveType);
    boolean failed = offered && read.getState() == CoachRead.State.FAILED;
    TextView status = findViewById(R.id.tvAnalysisPlanDebugStatus);
    status.setVisibility(failed ? View.VISIBLE : View.GONE);
    if (failed) {
      status.setText(failureLine(read));
    }
    showPurchaseDebug();
  }

  /** What Play last said and what the coach is being sent, for whoever is testing a purchase. */
  private void showPurchaseDebug() {
    boolean offered = purchaseDebugOffered();
    findViewById(R.id.llAnalysisPlanPurchaseDebug).setVisibility(offered ? View.VISIBLE : View.GONE);
    if (!offered) {
      return;
    }
    String token = CoachAccess.of(this).getPurchaseToken();
    String sent = token == null ? "No purchase to send"
        : coachClient.accessEnded() ? "Purchase kept, and the coach says it has ended"
        : "Purchase sent with every request";
    String said = billing.getStatus();
    ((TextView) findViewById(R.id.tvAnalysisPlanPurchaseStatus))
        .setText(said.isEmpty() ? sent : sent + "\n" + said);
    findViewById(R.id.buAnalysisPlanCopyPurchase)
        .setVisibility(token != null ? View.VISIBLE : View.GONE);
  }

  /**
   * The standing practice under the plan: the sets last kept at once, then asked for afresh once a
   * screen, which costs the service no model call.
   */
  private void showPractice(boolean offered) {
    View preview = findViewById(R.id.llAnalysisPlanPreview);
    if (!offered) {
      preview.setVisibility(View.GONE);
      findViewById(R.id.llAnalysisPractice).setVisibility(View.GONE);
      return;
    }
    // A plan is never drawn as an example: what the install would get is drawn blank instead.
    boolean locked = !coachClient.hasAccess();
    if (locked && preview.getVisibility() != View.VISIBLE) {
      planView.setPreview(preview);
    }
    preview.setVisibility(locked ? View.VISIBLE : View.GONE);
    if (locked) {
      practiceAsked = false; // the sets are asked for once the coach lets the install in
      showPracticeLocked(true);
      findViewById(R.id.llAnalysisPractice).setVisibility(View.VISIBLE);
      planView.setPracticeTeaser((LinearLayout) findViewById(R.id.llAnalysisPracticeSets),
          learnNextOffered());
      return;
    }
    if (practiceAsked) {
      LinearLayout sets = findViewById(R.id.llAnalysisPracticeSets);
      findViewById(R.id.llAnalysisPractice).setVisibility(
          sets.getChildCount() == 0 ? View.GONE : View.VISIBLE);
      return;
    }
    practiceAsked = true;
    learnedAtAsk = Options.INSTANCE.getLearnedCases();
    drawPractice(CoachPractice.kept(solveType));
    CoachPractice.refresh(coachClient, solveType, method, new CoachPractice.Listener() {
      @Override
      public void onPractice(List<CoachPractice.Set> sets) {
        if (!isFinishing()) {
          drawPractice(sets);
        }
      }

      @Override
      public void onAccessEnded() {
        if (!isFinishing()) {
          showRead();
        }
      }
    });
  }

  /** @param locked whether the section is the teaser, which the chip says and no text explains */
  private void showPracticeLocked(boolean locked) {
    findViewById(R.id.tvAnalysisPracticeLocked).setVisibility(locked ? View.VISIBLE : View.GONE);
    findViewById(R.id.tvAnalysisPracticeBody).setVisibility(locked ? View.GONE : View.VISIBLE);
  }

  @Override
  public void onDrillCasesPicked(String family) {
    if (!isFinishing() && !practiceSets.isEmpty()) {
      drawPractice(practiceSets);
    }
  }

  private void drawPractice(List<CoachPractice.Set> sets) {
    practiceSets = sets;
    showPracticeLocked(false);
    LinearLayout into = findViewById(R.id.llAnalysisPracticeSets);
    planView.setPractice(into, sets);
    findViewById(R.id.llAnalysisPractice)
        .setVisibility(into.getChildCount() == 0 ? View.GONE : View.VISIBLE);
  }

  /**
   * The step whose cases the teaser shows to learn next, or null: PLL while one has been seen never
   * done in one algorithm, as the service puts PLL first; otherwise OLL while one has, or none has
   * been seen known at all.
   */
  private String learnNextOffered() {
    if (!knownRead) {
      return null; // drawn again once read, rather than shown and then taken away
    }
    int knownOlls = 0;
    boolean ollToLearn = false;
    for (CaseKnowledge stepCase : readKnown) {
      boolean toLearn = stepCase.getStatus() == CaseKnowledge.Status.TO_LEARN;
      if (toLearn && "pll".equals(stepCase.getCaseSet())) {
        return "pll";
      }
      if ("oll".equals(stepCase.getCaseSet())) {
        ollToLearn |= toLearn;
        if (stepCase.getStatus() == CaseKnowledge.Status.KNOWN) {
          knownOlls++;
        }
      }
    }
    return ollToLearn || knownOlls == 0 ? "oll" : null;
  }

  /** What went wrong, grouped by what the reader can do about it rather than by what broke. */
  private String failureLine(CoachRead read) {
    CoachReply failure = read.getFailure();
    switch (failure.getKind()) {
      case UNREACHABLE:
        return getString(R.string.coach_refused_unreachable);
      case UNREADABLE:
        return getString(R.string.coach_refused_update);
      default:
        break;
    }
    switch (failure.getRefusal()) {
      case INSUFFICIENT_DATA:
        int more = CoachPayloadBuilder.PLAN_FLOOR - read.getSolveCount();
        // Unknown, or refused with enough solves anyway: no count to give that would be true.
        if (read.getSolveCount() < 0 || more <= 0) {
          return getString(R.string.coach_refused_thin);
        }
        return getResources().getQuantityString(R.plurals.coach_refused_too_few, more,
            CoachPayloadBuilder.PLAN_FLOOR, read.getSolveCount(), more);
      case WEEKLY_CAP_REACHED:
        return read.getAskAgainAt() == 0 ? getString(R.string.coach_refused_cap_undated)
            : getString(R.string.coach_refused_cap,
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(read.getAskAgainAt())));
      case RATE_LIMITED:
        return getString(R.string.coach_refused_busy);
      case PLAN_REJECTED:
        return getString(R.string.coach_refused_rejected);
      case UNAUTHORIZED:
        return getString(R.string.coach_refused_device);
      case NO_ACCESS:
        return getString(R.string.coach_subscription_ended);
      case PAYLOAD_INVALID:
      case PAYLOAD_TOO_LARGE:
        return getString(R.string.coach_refused_update);
      case PAYLOAD_TOO_NEW:
        // The service is the older of the two, so updating the app would only widen the gap.
        return getString(R.string.coach_refused_unavailable);
      default:
        // The service's own trouble, and any code added after this app was built.
        return getString(R.string.coach_refused_unavailable);
    }
  }

  /** The plan last written for this solve type. */
  private void loadPlan() {
    App.INSTANCE.getService().getCoachPlan(solveType, new DataCallback<StoredCoachPlan>() {
      @Override
      public void onData(final StoredCoachPlan stored) {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            showPlan(stored);
          }
        });
      }
    });
  }

  /**
   * A kept plan in place of the explainer. It is shown even where the history has since changed:
   * it was a true reading when written, and its date says as of when.
   */
  private void showPlan(StoredCoachPlan stored) {
    // A written plan's card takes the countdown to the next one.
    planStatus.dockIn(stored == null ? null : (ViewGroup) findViewById(R.id.llAnalysisPlanDock));
    showRead();
    planView.setPlan(stored);
    int written = stored == null ? View.GONE : View.VISIBLE;
    findViewById(R.id.llAnalysisPlanWritten).setVisibility(written);
    findViewById(R.id.llAnalysisPlanAreas).setVisibility(written);
    findViewById(R.id.llAnalysisPlanAbout).setVisibility(stored == null ? View.VISIBLE : View.GONE);
    if (stored == null) {
      findViewById(R.id.tvAnalysisPlanUncited).setVisibility(View.GONE);
      return;
    }
    planView.setHero(findViewById(R.id.llAnalysisPlanWritten), stored);
    // A figure nobody sent and a case nobody has are the same complaint to a reader, so one line.
    int invented = stored.uncited().size() + stored.unknownCodes().size();
    TextView tvUncited = findViewById(R.id.tvAnalysisPlanUncited);
    tvUncited.setVisibility(invented == 0 ? View.GONE : View.VISIBLE);
    tvUncited.setText(getString(R.string.coach_uncited, invented));
  }

  /** The Cases tab, which waits on two reads and is drawn by whichever of them lands second. */
  private void showCases() {
    if (statistics != null) {
      cases.show(statistics, oneAlgorithmCases, known, palette, method);
    }
  }

  private AnalysisCases.Listener casesListener() {
    return new AnalysisCases.Listener() {
      @Override
      public void onCasePicked(String caseCode) {
        CaseAlgorithmsDialog.newInstance(caseCode).show(getSupportFragmentManager(), "case");
      }

      @Override
      public void onRecommendedDrills() {
        tabs.setSelection(TAB_PLAN);
        showTab(TAB_PLAN);
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

    showComposition();
    showLegend(total);
  }

  /** The bar of the steps' means, the cross's stops paled once its solves have been read. */
  private void showComposition() {
    if (steps.isEmpty()) {
      return;
    }
    int[] colors = new int[steps.size()];
    for (int i = 0; i < steps.size(); i++) {
      colors[i] = palette.colorFor(steps.get(i).getCode());
    }
    // Never over the example: its steps are invented, and the stops would be the reader's own.
    ((SolveStepBarView) findViewById(R.id.vAnalysisComposition)).setSteps(
        SolveStepBars.averages(steps, sampling() ? 0 : crossPauseMs), colors);
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
