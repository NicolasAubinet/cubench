package com.cube.nanotimer.gui;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuItemCompat;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import com.cube.nanotimer.App;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.Options.BigCubesNotation;
import com.cube.nanotimer.Options.InspectionMode;
import com.cube.nanotimer.R;
import com.cube.nanotimer.SoundManager;
import com.cube.nanotimer.cube.SmartCubeChip;
import com.cube.nanotimer.cube.SmartCubeTimerController;
import com.cube.nanotimer.gui.widget.HistoryDetailDialog;
import com.cube.nanotimer.gui.widget.InAppReviewManager;
import com.cube.nanotimer.gui.widget.ResultListener;
import com.cube.nanotimer.gui.widget.SessionDetailDialog;
import com.cube.nanotimer.gui.widget.SmartCubeConnectDialog;
import com.cube.nanotimer.gui.widget.TimeChangedHandler;
import com.cube.nanotimer.gui.widget.dialog.AddNewTimeDialog;
import com.cube.nanotimer.gui.widget.dialog.CommentSolveDialog;
import com.cube.nanotimer.gui.widget.dialog.CrossSolverDialog;
import com.cube.nanotimer.gui.widget.dialog.ScrambleViewDialog;
import com.cube.nanotimer.scrambler.ScramblerService;
import com.cube.nanotimer.scrambler.randomstate.RandomStateGenEvent;
import com.cube.nanotimer.scrambler.randomstate.RandomStateGenEvent.State;
import com.cube.nanotimer.scrambler.randomstate.RandomStateGenListener;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.session.CubeSession;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.ScrambleFormatterService;
import com.cube.nanotimer.util.ScrambleViewNotation;
import com.cube.nanotimer.util.YesNoListener;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.helper.GUIUtils;
import com.cube.nanotimer.util.helper.ScreenUtils;
import com.cube.nanotimer.util.helper.TimeColorScale;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.util.view.DigitalTextView;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.ScrambleType;
import com.cube.nanotimer.vo.SolveAverages;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveType;
import com.cube.nanotimer.vo.SolveTypeStep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class TimerActivity extends NanoTimerActivity implements ResultListener, TimeChangedHandler {

  private enum TimerState {STOPPED, RUNNING, INSPECTING}

  private TextView tvTimer;
  private TextView tvScramble;
  private TextView tvSolvesCount;
  private TextView tvAccuracy;
  private TextView tvTitle;
  private ViewGroup layout;
  private TableLayout sessionTimesLayout;
  private TableLayout timerStepsLayout;
  private View recordOverlayCard;
  private TextView tvRecordOverlayHeader;
  private TableLayout recordOverlayTable;
  private final Handler overlayHandler = new Handler();

  private CubeType cubeType;
  private SolveType solveType;
  private String[] currentScramble;
  private SolveTime lastSolveTime;
  private CubeSession cubeSession;
  private SolveAverages solveAverages;
  private SolveAverages prevSolveAverages;
  private int currentOrientation;
  private List<Long> stepsTimes = new ArrayList<Long>();
  private long stepStartTs;
  private List<Animation> animations = new ArrayList<Animation>();
  private boolean hasNewSession;
  private SolveAverageCallback solveAverageCallback = new SolveAverageCallback();

  private int solvesCount; // session solves count (or history solves count if no session exists)
  private int historySolvesCount;
  private ColorStateList defaultTextColor;
  private ColorStateList defaultTimerTextColor;
  private static final int MIN_TIMES_FOR_RECORD_NOTIFICATION = 12;

  private final long REFRESH_INTERVAL = 30;
  private Timer timer;
  private Timer holdToStartTimer;
  private Handler timerHandler = new Handler();
  private final Object holdToStartTimerSync = new Object();
  private final Object timerSync = new Object();
  private long timerStartTs;
  private volatile long holdToStartTs;
  private final long HOLD_TO_START_MIN_DURATION = 500;
  private volatile TimerState timerState = TimerState.STOPPED;
  private boolean showMenu = true;
  private SmartCubeChip smartCubeChip;
  private SmartCubeTimerController cubeTimerController;
  private boolean oversteppedInspection = false;
  private boolean reviewRequested = false; // at most one review request per timer session

  private long lastTimerStartTs;
  private long lastTimerStopTs;
  private boolean ignoreActionUp;
  private final long START_STOP_DELAY = 150; // to avoid stopping timer too quickly after a start
  private final long STOP_START_DELAY = 500; // to avoid starting timer too quickly after a stop

  private int inspectionTime;
  private InspectionMode inspectionMode;
  private boolean soundsEnabled;
  private boolean keepScreenOnWhenTimerOff;

  private int defaultBackgroundColor = R.color.graybg;
  private int pushedBackgroundColor = R.color.pushedbg;

  private RandomStateGenListener randomStateGenListener = new RandomStateGenListener() {
    @Override
    public void onStateUpdate(RandomStateGenEvent event) {
      if (event.getState() == State.GENERATED) {
        boolean foundScramble = getAndDisplayNewScramble();
        if (foundScramble) {
          ScramblerService.INSTANCE.removeRandomStateGenListener(this);
        }
      }
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    App.INSTANCE.setContext(this);
    setContentView(R.layout.timer_screen);
    setVolumeControlStream(AudioManager.STREAM_MUSIC);
    currentOrientation = getResources().getConfiguration().orientation;

    cubeType = (CubeType) getIntent().getSerializableExtra("cubeType");
    solveType = (SolveType) getIntent().getSerializableExtra("solveType");
    historySolvesCount = getIntent().getIntExtra("solvesCount", 0);
    if (cubeType == null || solveType == null) {
      finish();
      return;
    }
    cubeSession = new CubeSession();
    App.INSTANCE.getService().getSolveAverages(solveType, solveAverageCallback);

    smartCubeChip = new SmartCubeChip(this, this::openSmartCubeConnect);
    cubeTimerController = new SmartCubeTimerController(this::onCubeSolveCompleted);
    initActionBar();

    inspectionTime = Options.INSTANCE.getInspectionTime();
    inspectionMode = Options.INSTANCE.getInspectionMode();
    soundsEnabled = Options.INSTANCE.isInspectionSoundsEnabled();
    keepScreenOnWhenTimerOff = Options.INSTANCE.isKeepTimerScreenOnWhenTimerOff();

    initViews();

    defaultTextColor = tvSolvesCount.getTextColors();
    defaultTimerTextColor = tvTimer.getTextColors();
    resetTimer();
    setDefaultBannerText();

    if (!solveType.hasSteps()) {
      App.INSTANCE.getService().getSessionTimes(solveType, new DataCallback<List<Long>>() {
        @Override
        public void onData(List<Long> data) {
          cubeSession = new CubeSession(data);
          refreshSessionFields();
        }
      });
      App.INSTANCE.getService().getSessionStart(solveType, new DataCallback<Long>() {
        @Override
        public void onData(Long data) {
          hasNewSession = (data != null && data > 0);
        }
      });
      App.INSTANCE.getService().getSolvesCount(solveType, new DataCallback<Integer>() {
        @Override
        public void onData(final Integer data) {
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              setSolvesCount(data);
            }
          });
        }
      });
    }

    generateScramble();

    getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
      @Override
      public void handleOnBackPressed() {
        if (timerState == TimerState.RUNNING) {
          stopTimer(false);
          resetTimer();
        } else if (timerState == TimerState.INSPECTING) { // for automatic inspection mode
          stopInspectionTimer();
          resetTimer();
        } else {
          if (timer != null) {
            timer.cancel();
            timer.purge();
          }
          setEnabled(false);
          TimerActivity.this.getOnBackPressedDispatcher().onBackPressed();
        }
      }
    });
  }

  @Override
  protected void onResume() {
    super.onResume();
    App.INSTANCE.setContext(this);
    smartCubeChip.start();
    cubeTimerController.start();
    refreshSessionFields(); // Repaint the session times in case the coloring option changed in the settings.
  }

  @Override
  protected void onPause() {
    super.onPause();
    smartCubeChip.stop();
    cubeTimerController.stop();
  }

  // Auto-stop: fired when the connected cube reaches solved during a running solve.
  private void onCubeSolveCompleted() {
    if (timerState == TimerState.RUNNING && !solveType.hasSteps()) {
      stopTimer(true);
    }
  }

  private void initActionBar() {
    ActionBar actionBar = getSupportActionBar();
    actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
    View customView = getLayoutInflater().inflate(R.layout.textcentered_actionbar, null);
    actionBar.setCustomView(customView,
        new ActionBar.LayoutParams(ActionBar.LayoutParams.MATCH_PARENT, ActionBar.LayoutParams.MATCH_PARENT));
    actionBar.setDisplayHomeAsUpEnabled(true);

    // Drop the default inset between the nav button and the custom view so the title and the
    // cube chip get the full width (otherwise the title is clipped).
    Toolbar decorToolbar = findToolbar(getWindow().getDecorView());
    if (decorToolbar != null) {
      decorToolbar.setContentInsetStartWithNavigation(0);
      decorToolbar.setContentInsetsAbsolute(0, decorToolbar.getContentInsetEnd());
    }

    smartCubeChip.bind(customView.findViewById(R.id.smartCubeChip));
    smartCubeChip.setHideWhenDisconnected(true); // on the timer, only show when a cube is connected
  }

  private Toolbar findToolbar(View view) {
    if (view instanceof Toolbar) {
      return (Toolbar) view;
    }
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        Toolbar found = findToolbar(group.getChildAt(i));
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  private void initViews() {
    tvTimer = (TextView) findViewById(R.id.tvTimer);
    tvScramble = (TextView) findViewById(R.id.tvScramble);
    tvSolvesCount = (TextView) findViewById(R.id.tvSolvesCount);
    tvAccuracy = (TextView) findViewById(R.id.tvLifetimeAccuracy);
    tvTitle = (TextView) findViewById(R.id.tvTitle);
    sessionTimesLayout = (TableLayout) findViewById(R.id.sessionTimesLayout);
    TableLayout averagesLayout = (TableLayout) findViewById(R.id.averagesLayout);
    timerStepsLayout = (TableLayout) findViewById(R.id.timerStepsLayout);
    recordOverlayCard = findViewById(R.id.recordOverlayCard);
    tvRecordOverlayHeader = (TextView) findViewById(R.id.tvRecordOverlayHeader);
    recordOverlayTable = (TableLayout) findViewById(R.id.recordOverlayTable);

    if (currentOrientation == Configuration.ORIENTATION_PORTRAIT && cubeType == CubeType.SEVEN_BY_SEVEN
        && tvTimer instanceof DigitalTextView) {
      ((DigitalTextView) tvTimer).reduceBaseTextSize(5);
    }

    Float scrambleTextSize = getCubeTypeScrambleTextSize();
    if (scrambleTextSize != null) {
      tvScramble.setTextSize(TypedValue.COMPLEX_UNIT_PX, scrambleTextSize);
    }


    if (solveType.hasSteps()) {
      findViewById(R.id.sessionLayout).setVisibility(View.GONE);
      averagesLayout.setColumnCollapsed(2, true);
      timerStepsLayout.setVisibility(View.VISIBLE);
      if (solveType.getSteps().length <= 4) {
        timerStepsLayout.setColumnCollapsed(2, true);
        timerStepsLayout.setColumnCollapsed(3, true);
      }
      findViewById(R.id.trAvgOfLife).setVisibility(View.VISIBLE);
      hideUnneededStepFields();
    } else if (solveType.isBlind()) {
      findViewById(R.id.trAvgOfFive).setVisibility(View.GONE);
      findViewById(R.id.trLifetimeAccuracy).setVisibility(View.VISIBLE);
      findViewById(R.id.trBestMeanOfThree).setVisibility(View.VISIBLE);
      ((TextView) findViewById(R.id.tvAvgOf)).setText(R.string.success_avg);
      ((TextView) findViewById(R.id.tvBestOf)).setText(R.string.accuracy);
    } else {
      timerStepsLayout.setVisibility(View.GONE);
      findViewById(R.id.trAvgOfLife).setVisibility(View.GONE);
    }

    updateAveragesTableBackgroundColors();

    View actionBarLayout = findViewById(R.id.actionbarLayout);
    actionBarLayout.setOnTouchListener(layoutTouchListener);

    layout = (ViewGroup) findViewById(R.id.mainLayout);
    layout.setOnTouchListener(layoutTouchListener);
    if (timerState == TimerState.STOPPED) {
      setKeepScreenOn(keepScreenOnWhenTimerOff);
    } else {
      setKeepScreenOn(true);
    }
  }

  private void updateAveragesTableBackgroundColors() {
    TableLayout averagesTable = (TableLayout) findViewById(R.id.averagesLayout);

    int visibleTableRowsCount = 0;
    for (int i = 1; i < averagesTable.getChildCount(); i++) {
      View child = averagesTable.getChildAt(i);
      if (!(child instanceof TableRow)) {
        continue; // skip non-row children (e.g. the header divider)
      }
      TableRow tableRow = (TableRow) child;

      if (tableRow.getVisibility() == View.VISIBLE) {
        // Softened alternation: transparent / faint, instead of the cyan zebra.
        int backgroundColorId = (visibleTableRowsCount % 2 == 0) ? R.color.transparent : R.color.grid_background_1;
        tableRow.setBackgroundResource(backgroundColorId);

        visibleTableRowsCount++;
      }
    }
  }

  private Float getCubeTypeScrambleTextSize() {
    Float size;
    switch (cubeType) {
      case TWO_BY_TWO:
        size = 24f;
        break;
      case THREE_BY_THREE:
      case PYRAMINX:
      case SKEWB:
      case FTO:
        size = 22f;
        break;
      case FOUR_BY_FOUR:
      case FIVE_BY_FIVE:
      case SQUARE1:
        size = 21f;
        break;
      case CLOCK:
        size = 20f;
        break;
      case SIX_BY_SIX:
      case MEGAMINX:
        size = 18f;
        break;
      case SEVEN_BY_SEVEN:
        size = 15.5f;
        break;
      default:
        size = null;
        break;
    }
    if (Options.INSTANCE.getBigCubesNotation() == BigCubesNotation.RWUWFW) {
      // adjust size otherwise it is too large, and causes a bug when going from landscape mode to portrait mode
      switch (cubeType) {
        case FOUR_BY_FOUR:
        case FIVE_BY_FIVE:
        case SIX_BY_SIX:
        case SEVEN_BY_SEVEN:
          size -= 2;
      }
    }
    return size;
  }

  private void setDefaultBannerText() {
    StringBuilder sb = new StringBuilder();
    sb.append(cubeType.getName());

    if (!Utils.isDefaultSolveTypeName(solveType.getName())) {
      String localizedSolveTypeName = Utils.toSolveTypeLocalizedName(this, solveType.getName());
      sb.append(" (").append(localizedSolveTypeName).append(")");
    }
    setTitle(sb.toString(), defaultTextColor.getDefaultColor());
  }

  public void setTitle(String s) {
    tvTitle.setText(s);
  }

  public void setTitle(int res) {
    tvTitle.setText(res);
  }

  public synchronized void setTitle(String s, int textColor) {
    setTitle(s);
    setTitleColor(textColor);
  }

  @Override
  public void setTitleColor(int textColor) {
    tvTitle.setTextColor(textColor);
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    menu.findItem(R.id.itLastSolve).setVisible(showMenu && lastSolveTime != null);
    menu.findItem(R.id.itSessionDetails).setVisible(showMenu && hasNewSession);
    ScrambleType scrambleType = solveType.getScrambleType();
    boolean crossSolverAvailable = cubeType == CubeType.THREE_BY_THREE
        && (scrambleType == null || scrambleType.isDefault()); // null scramble type means the default full scramble
    menu.findItem(R.id.itCrossSolver).setVisible(showMenu && crossSolverAvailable);
    menu.findItem(R.id.itScrambleView).setVisible(showMenu && ScrambleViewNotation.getRenderKey(cubeType) != null);
    return super.onPrepareOptionsMenu(menu);
  }

  @Override
  @SuppressLint("RestrictedApi")
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.timer_menu, menu);
    if (menu instanceof MenuBuilder) {
      ((MenuBuilder) menu).setOptionalIconsVisible(true);
    }
    for (int i = 0; i < menu.size(); i++) {
      menu.getItem(i).setVisible(showMenu);
    }
    // The scramble-view icon is multi-coloured (a scrambled cube face); clear any
    // action-bar tint so its colours show instead of being flattened to one.
    MenuItemCompat.setIconTintList(menu.findItem(R.id.itScrambleView), null);
    if (solveType.hasSteps()) {
      menu.findItem(R.id.itSessionDetails).setVisible(false);
      menu.findItem(R.id.itNewSession).setVisible(false);
      menu.findItem(R.id.itAddTime).setVisible(false);
    }
    return true;
  }

  private void openSmartCubeConnect() {
    DialogUtils.showFragment(this, new SmartCubeConnectDialog());
  }

  private void showMenuButton(boolean show) {
    if (this.showMenu != show) {
      this.showMenu = show;
      supportInvalidateOptionsMenu();

      getSupportActionBar().setDisplayHomeAsUpEnabled(show);

      // Hide the cube chip while the timer runs so the whole action bar stays a tap target
      // (no dead zone); it reappears when the timer stops (if a cube is connected).
      smartCubeChip.setSuppressed(!show);
    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (timerState == TimerState.STOPPED) {
      switch (item.getItemId()) {
        case R.id.itPlusTwo:
          if (lastSolveTime == null) {
            DialogUtils.showShortInfoMessage(this, R.string.no_solve_for_action);
          } else if (!lastSolveTime.isDNF()) {
            boolean isPlusTwo = !lastSolveTime.isPlusTwo();
            lastSolveTime.setPlusTwo(isPlusTwo, true);
            App.INSTANCE.getService().saveTime(lastSolveTime, solveAverageCallback);
            tvTimer.setText(FormatterService.INSTANCE.formatSolveTime(lastSolveTime.getTime()));
            setTimerTextColor(lastSolveTime.getTime());
            cubeSession.setLastAsPlusTwo(isPlusTwo);
            refreshSessionFields();
          }
          break;
        case R.id.itDNF:
          if (lastSolveTime == null) {
            DialogUtils.showShortInfoMessage(this, R.string.no_solve_for_action);
          } else if (!lastSolveTime.isDNF()) {
            lastSolveTime.setTime(-1);
            App.INSTANCE.getService().saveTime(lastSolveTime, solveAverageCallback);
            tvTimer.setText(FormatterService.INSTANCE.formatSolveTime(lastSolveTime.getTime()));
            setTimerTextColor(lastSolveTime.getTime());
            cubeSession.setLastAsDNF();
            refreshSessionFields();
          }
          break;
        case R.id.itDelete:
          if (lastSolveTime != null) {
            App.INSTANCE.getService().deleteTime(lastSolveTime, solveAverageCallback);
            cubeSession.deleteLast();
            historySolvesCount--;
            setSolvesCount(solvesCount - 1);
            refreshSessionFields();
            resetTimer();
          } else {
            DialogUtils.showShortInfoMessage(this, R.string.no_solve_for_action);
          }
          break;
        case R.id.itLastSolve:
          if (lastSolveTime != null) {
            DialogUtils.showFragment(this, HistoryDetailDialog.newInstance(lastSolveTime, cubeType, this));
          }
          break;
        case R.id.itSessionDetails:
          DialogUtils.showFragment(this, SessionDetailDialog.newInstance(solveType));
          break;
        case R.id.itNewSession:
          DialogUtils.showYesNoConfirmation(this, getString(R.string.new_session_confirmation), new YesNoListener() {
            @Override
            public void onYes() {
              App.INSTANCE.getService().startNewSession(solveType, System.currentTimeMillis(), null);
              cubeSession.clearSession();
              setSolvesCount(0);
              refreshSessionFields();
              if (!hasNewSession) {
                hasNewSession = true;
              }
            }
          });
          break;
        case R.id.itAddTime:
          if (currentScramble != null) {
            String scramble = ScrambleFormatterService.INSTANCE.formatScrambleAsSingleLine(currentScramble, cubeType);
            AddNewTimeDialog dialog = AddNewTimeDialog.newInstance(this, solveType, scramble);
            DialogUtils.showFragment(this, dialog);
          } else {
            DialogUtils.showShortInfoMessage(this, R.string.can_not_add_time_while_generating);
          }
          break;
        case R.id.itCrossSolver:
          if (currentScramble != null) {
            String scramble = ScrambleFormatterService.INSTANCE.formatScrambleAsSingleLine(currentScramble, cubeType);
            DialogUtils.showFragment(this, CrossSolverDialog.newInstance(scramble));
          } else {
            DialogUtils.showShortInfoMessage(this, R.string.cross_no_scramble);
          }
          break;
        case R.id.itScrambleView:
          openScrambleView();
          break;
      }
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void onTimeChanged(final SolveTime solveTime) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        lastSolveTime = solveTime;
        tvTimer.setText(FormatterService.INSTANCE.formatSolveTime(solveTime.getTime()));
        setTimerTextColor(solveTime.getTime());
        cubeSession.setLastTime(solveTime.getTime());
        refreshSessionFields();
        App.INSTANCE.getService().getSolveAverages(solveType, solveAverageCallback);
      }
    });
  }

  @Override
  public void onTimeDeleted(SolveTime solveTime) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        cubeSession.deleteLast();
        historySolvesCount--;
        setSolvesCount(solvesCount - 1);
        refreshSessionFields();
        resetTimer();
        App.INSTANCE.getService().getSolveAverages(solveType, solveAverageCallback);
      }
    });
  }

  // Opens the scramble diagram for the current scramble.
  private void openScrambleView() {
    if (currentScramble != null) {
      String key = ScrambleViewNotation.getRenderKey(cubeType);
      String moves = ScrambleViewNotation.toCubingNotation(currentScramble, cubeType);
      String readable = ScrambleFormatterService.INSTANCE.formatScrambleAsSingleLine(currentScramble, cubeType);
      // When the diagram can't be drawn (a Clock pin notation), the dialog shows
      // this text; nudge the user toward the notation that does render.
      String fallback = (moves == null && cubeType == CubeType.CLOCK)
          ? getString(R.string.scramble_view_clock_notation_hint) + "\n\n" + readable
          : readable;
      DialogUtils.showFragment(this, ScrambleViewDialog.newInstance(key, moves, fallback));
    } else {
      DialogUtils.showShortInfoMessage(this, R.string.scramble_view_no_scramble);
    }
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    if (newConfig.orientation != currentOrientation) {
      currentOrientation = newConfig.orientation;
      String timerText = tvTimer.getText().toString();

      List<String> stepsText = new ArrayList<String>();
      if (solveType.hasSteps()) {
        for (int i = 0; i < timerStepsLayout.getChildCount(); i++) {
          TableRow tr = (TableRow) timerStepsLayout.getChildAt(i);
          for (int j = 0; j < tr.getChildCount(); j++) {
            stepsText.add(((TextView) tr.getChildAt(j)).getText().toString());
          }
        }
      }

      setContentView(R.layout.timer_screen);
      initViews();

      if (timerState == TimerState.STOPPED) {
        tvTimer.setText(timerText);
      }
      if (currentScramble != null) {
        tvScramble.setText(ScrambleFormatterService.INSTANCE.formatToColoredScramble(currentScramble, cubeType, currentOrientation));
      } else {
        tvScramble.setText(R.string.scramble_generating);
      }
      tvSolvesCount.setText(String.valueOf(solvesCount));

      refreshSessionFields();
      if (solveAverages != null) {
        refreshAvgFields(false);
      }

      if (solveType.hasSteps()) {
        Iterator<String> it = stepsText.iterator();
        for (int i = 0; i < timerStepsLayout.getChildCount(); i++) {
          TableRow tr = (TableRow) timerStepsLayout.getChildAt(i);
          for (int j = 0; j < tr.getChildCount(); j++) {
            ((TextView) tr.getChildAt(j)).setText(it.next());
          }
        }
      }
    }
  }

  @Override
  public void onResult(Object... params) {
    final SolveAverages solveAverages = (SolveAverages) params[0];
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        addTimeToUI(solveAverages.getSolveTime().getTime());
        generateScramble();
      }
    });
    solveAverageCallback.onData(solveAverages);
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_SPACE && event.getRepeatCount() == 0) {
      onTouchEvent(MotionEvent.ACTION_DOWN);
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_SPACE && event.getRepeatCount() == 0) {
      onTouchEvent(MotionEvent.ACTION_UP);
      return true;
    }
    return super.onKeyUp(keyCode, event);
  }

  private void refreshSessionFields() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        clearSessionTextViews();
        if (cubeSession == null) {
          return;
        }
        List<Long> sessionTimes = cubeSession.getTimes();
        if (sessionTimes.isEmpty()) {
          return;
        }
        switch (Options.INSTANCE.getSessionTimesColoring()) {
          case BEST_WORST:
            colorSessionBestWorst(sessionTimes);
            break;
          case ALL_DISPLAYED:
            colorSessionWithinDisplayed(sessionTimes);
            break;
          case MATCH_HISTORY:
            colorSessionMatchHistory();
            break;
          case NONE:
            colorSessionPlain(sessionTimes);
            break;
        }
      }
    });
  }

  // Classic coloring: only the best (green) and worst (red) of the session stand out.
  private void colorSessionBestWorst(List<Long> sessionTimes) {
    int bestInd = cubeSession.getBestTimeInd(solveType.isBlind());
    int worstInd = cubeSession.getWorstTimeInd(solveType.isBlind());
    for (int i = 0; i < sessionTimes.size(); i++) {
      GUIUtils.setSessionTimeCellText(getSessionTextView(i), sessionTimes.get(i), i, bestInd, worstInd);
    }
  }

  // Gradient coloring relative to the displayed times only (each of the 12 ranked among the 12).
  private void colorSessionWithinDisplayed(List<Long> sessionTimes) {
    TimeColorScale scale = new TimeColorScale(this);
    scale.setTimes(sessionTimes, false);
    colorSessionWithScale(sessionTimes, scale);
  }

  // No coloring: every time in the default text color.
  private void colorSessionPlain(List<Long> sessionTimes) {
    for (int i = 0; i < sessionTimes.size(); i++) {
      GUIUtils.setSessionTimeCellPlain(getSessionTextView(i), sessionTimes.get(i));
    }
  }

  // Gradient coloring (green=fast → white=median → red=slow) against the given scale.
  private void colorSessionWithScale(List<Long> sessionTimes, TimeColorScale scale) {
    for (int i = 0; i < sessionTimes.size(); i++) {
      long time = sessionTimes.get(i);
      GUIUtils.setSessionTimeCellColor(getSessionTextView(i), time, scale.colorFor(time, time < 0));
    }
  }

  // Builds the gradient from the same recent-solves window the history screen uses, then
  // colors the displayed session times against it (async DB fetch).
  private void colorSessionMatchHistory() {
    App.INSTANCE.getService().getLastSolveTimes(solveType, Options.INSTANCE.getColorSampleSize(), new DataCallback<List<Long>>() {
      @Override
      public void onData(final List<Long> historyTimes) {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            if (cubeSession == null) {
              return;
            }
            TimeColorScale scale = new TimeColorScale(TimerActivity.this);
            scale.setTimes(historyTimes);
            colorSessionWithScale(cubeSession.getTimes(), scale);
          }
        });
      }
    });
  }

  private void clearSessionTextViews() {
    for (int i = 0; i < sessionTimesLayout.getChildCount(); i++) {
      TableRow tableRow = (TableRow) sessionTimesLayout.getChildAt(i);
      for (int j = 0; j < tableRow.getChildCount(); j++) {
        TextView tr = (TextView) tableRow.getChildAt(j);
        tr.setText("");
      }
    }
  }

  private TextView getSessionTextView(int index) {
    int elementsCountPerLine = ((TableRow) sessionTimesLayout.getChildAt(0)).getChildCount();

    TableRow tableRow = (TableRow) sessionTimesLayout.getChildAt(index / elementsCountPerLine);
    return (TextView) tableRow.getChildAt(index % elementsCountPerLine);

//    View v = sessionTimesLayout.getChildAt(i); // for GridView
//    return (TextView) v;
  }

  private void startTimer() {
    long curTime = System.currentTimeMillis();
    resetTimerText();
    lastTimerStartTs = curTime;
    if (curTime - lastTimerStopTs < STOP_START_DELAY) {
      return;
    }
    timerStartTs = curTime;
    if (solveType.hasSteps()) {
      stepsTimes.clear();
      stepStartTs = timerStartTs;
    }
    timerStarted();
    timer = new Timer();
    if (Options.INSTANCE.isShowTimeWhenRunning()) {
      TimerTask timerTask = new TimerTask() {
        public void run() {
          timerHandler.post(new Runnable() {
            public void run() {
              synchronized (timerSync) {
                if (timerState == TimerState.RUNNING) {
                  updateTimerText(System.currentTimeMillis() - timerStartTs);
                }
              }
            }
          });
        }
      };
      timer.schedule(timerTask, 1, REFRESH_INTERVAL);
    } else {
      tvTimer.setText("--:--");
    }
    timerState = TimerState.RUNNING;
    cubeTimerController.arm();
  }

  private void stopTimer(boolean save) {
    long curTime = System.currentTimeMillis();
    if (curTime - lastTimerStartTs < START_STOP_DELAY) {
      return;
    }
    lastTimerStopTs = curTime;
    long time = (curTime - timerStartTs);
    timerState = TimerState.STOPPED;
    cubeTimerController.disarm();
    if (timer != null) {
      timer.cancel();
      timer.purge();
    }
    timerStopped();
    if (oversteppedInspection) {
      time += 2000; // add 2s to time if started solve after inspection time ended (for official inspection mode)
      oversteppedInspection = false;
    }
    // update time once more to get the ms right
    // (as all ms do not necessarily appear when timing, some are skipped due to refresh interval)
    updateTimerText(time);
    if (save) {
      saveTime(time);
    }
    generateScramble();
  }

  private void startInspectionTimer() {
    long curTime = System.currentTimeMillis();
    if (curTime - lastTimerStopTs < STOP_START_DELAY) {
      return;
    }
    timerStartTs = curTime;
    oversteppedInspection = false;
    enableScreenRotationChanges(false);
    timerStarted();
    resetTimerText();
    timerState = TimerState.INSPECTING;
    setTitle(R.string.inspection);
    clearAvgRecordStyle();
    timer = new Timer();
    TimerTask timerTask = new TimerTask() {
      public void run() {
        timerHandler.post(new Runnable() {
          public void run() {
            updateInspectionTimerText();
          }
        });
      }
    };
    timer.schedule(timerTask, 1, 1000);
  }

  private void stopInspectionTimer() {
    if (timer != null) {
      timer.cancel();
      timer.purge();
    }
    setDefaultBannerText();
    timerState = TimerState.STOPPED;
    enableScreenRotationChanges(true);
    timerStopped();
  }

  /**
   * Used to fix a problem in the way android handles its views, when the layout is clicked during "Hold and release" inspection.
   * When the orientation changes, the views are re-created and the layout is no longer considered as clicked.
   * This is the reason why the orientation should not be allowed to change during inspection.
   */
  private void enableScreenRotationChanges(boolean enable) {
    ScreenUtils.enableScreenRotationChanges(this, enable);
  }

  private void nextSolveStep() {
    long ts = System.currentTimeMillis();
    if (stepsTimes.size() < solveType.getSteps().length) {
      long time = ts - stepStartTs;
      stepsTimes.add(time);
      updateStepTimeText(stepsTimes.size() - 1, FormatterService.INSTANCE.formatSolveTime(time));
    }
    stepStartTs = ts;
  }

  private void resetTimer() {
    synchronized (timerSync) {
      if (timer != null) {
        timer.cancel();
        timer.purge();
      }
      lastSolveTime = null;
      timerStartTs = 0;
      resetTimerText();
    }
  }

  private void resetTimerText() {
    String defaultText = FormatterService.INSTANCE.formatSolveTime(0L);
    tvTimer.setText(defaultText);
    setTimerTextColor(0L);
    if (solveType.hasSteps()) {
      for (int i = 0; i < Options.INSTANCE.getMaxStepsCount(); i++) {
        int rowInd = i % 4;
        int colInd = (i < 4) ? 0 : 2;
        if (i < solveType.getSteps().length) {
          SolveTypeStep sts = solveType.getSteps()[i];
          ((TextView) ((TableRow) timerStepsLayout.getChildAt(rowInd)).getChildAt(colInd)).setText(sts.getName() + ":");
          updateStepTimeText(i, defaultText);
        }
      }
    }
  }

  private void hideUnneededStepFields() {
    // hide fields to center steps vertically
    if (solveType.getSteps().length < 4) {
      for (int i = solveType.getSteps().length; i < Options.INSTANCE.getMaxStepsCount(); i++) {
        int rowInd = i % 4;
        int colInd = (i < 4) ? 0 : 2;
        (((TableRow) timerStepsLayout.getChildAt(rowInd)).getChildAt(colInd)).setVisibility(View.GONE);
        (((TableRow) timerStepsLayout.getChildAt(rowInd)).getChildAt(colInd + 1)).setVisibility(View.GONE);
      }
    }
  }

  private void saveTime(long time) {
    SolveTime solveTime = new SolveTime();
    solveTime.setTime(time);
    solveTime.setTimestamp(System.currentTimeMillis());
    solveTime.setSolveType(solveType);

    String scramble = "";
    if (currentScramble != null) { // should never be null here, but let's make sure
      scramble = ScrambleFormatterService.INSTANCE.formatScrambleAsSingleLine(currentScramble, cubeType);
    }

    solveTime.setScramble(scramble);
    if (solveType.hasSteps()) {
      solveTime.setStepsTimes(stepsTimes.toArray(new Long[0]));
    }

    addTimeToUI(time);
    App.INSTANCE.getService().saveTime(solveTime, solveAverageCallback);

    if (time > 0 && !reviewRequested) { // ask for a review after a completed solve, never after a DNF
      reviewRequested = true;
      InAppReviewManager.maybeRequestReview(this, historySolvesCount);
    }
  }

  private void addTimeToUI(long time) {
    if (cubeSession != null) {
      cubeSession.addTime(time);
      historySolvesCount++;
      setSolvesCount(solvesCount + 1);
      refreshSessionFields();
    }
  }

  private void updateStepTimeText(int id, String time) {
    if (id >= 0 && id < solveType.getSteps().length) {
      int rowInd = id % 4;
      int colInd = (id < 4) ? 1 : 3;
      ((TextView) ((TableRow) timerStepsLayout.getChildAt(rowInd)).getChildAt(colInd)).setText(time);
    }
  }

  // Keeps the timer text grayed out for a DNF (time == -1), matching how DNFs are
  // shown everywhere else; any other value uses the default timer color.
  private void setTimerTextColor(long time) {
    if (time < 0) {
      tvTimer.setTextColor(getResources().getColor(R.color.dnf_time));
    } else {
      tvTimer.setTextColor(defaultTimerTextColor);
    }
  }

  private synchronized void updateTimerText(long curTime) {
    tvTimer.setText(FormatterService.INSTANCE.formatSolveTime(curTime));
    setTimerTextColor(curTime);
    if (solveType.hasSteps()) {
      updateStepTimeText(stepsTimes.size(),
        FormatterService.INSTANCE.formatSolveTime(System.currentTimeMillis() - stepStartTs));
    }
  }

  private synchronized void updateInspectionTimerText() {
    long curTime = System.currentTimeMillis() - timerStartTs;
    final int officialInspectionDnfTime = 2;
    int seconds = (int) (curTime / 1000);
    tvTimer.setText(String.valueOf(seconds));
    boolean automaticMode = (inspectionMode == InspectionMode.AUTOMATIC);
    SoundManager soundManager = App.INSTANCE.getSoundManager();

    if (soundsEnabled) {
      if (Options.INSTANCE.getInspectionSoundsType() == Options.InspectionSoundsType.CLASSIC) {
        if (inspectionTime > 0 && seconds > 0 && seconds >= inspectionTime - 3
        && (seconds < inspectionTime || (automaticMode && seconds == inspectionTime) || (inspectionMode == InspectionMode.OFFICIAL && seconds < inspectionTime + officialInspectionDnfTime))) {
          soundManager.playSound(this, R.raw.beep);
        }
      } else if (Options.INSTANCE.getInspectionSoundsType() == Options.InspectionSoundsType.OFFICIAL) {
        if (seconds == 8) {
          soundManager.playSound(this, R.raw.eight);
        } else if (seconds == 12) {
          soundManager.playSound(this, R.raw.twelve);
        } else if (automaticMode && seconds == inspectionTime) {
          soundManager.playSound(this, R.raw.beep);
        }
      }
    }

    boolean mustDnfTime = false;
    if (seconds >= inspectionTime) {
      if (automaticMode) {
        stopInspectionTimer();
        startTimer();
      } else if (inspectionMode == InspectionMode.OFFICIAL) {
        if (seconds == inspectionTime + officialInspectionDnfTime) {
          mustDnfTime = true;
        } else if (seconds >= inspectionTime) {
          tvTimer.setText(R.string.plus_two);
          oversteppedInspection = true;
        }
      } else {
        if (inspectionTime > 0) {
          mustDnfTime = true;
        }
      }
    }

    if (mustDnfTime) {
      stopInspectionTimer();
      layout.setBackgroundResource(defaultBackgroundColor);
      if (inspectionMode == InspectionMode.OFFICIAL) {
        synchronized (holdToStartTimerSync) {
          stopHoldToStartTimer();
          holdToStartTs = 0;
        }
        ignoreActionUp = true;
      }
      updateTimerText(-1); // DNF
      App.INSTANCE.getSoundManager().playSound(this, R.raw.error);
      saveTime(-1);
      generateScramble();
    }
  }

  private void startHoldToStartTimer() {
    holdToStartTs = System.currentTimeMillis();
    holdToStartTimer = new Timer();
    final Handler timerHandler = new Handler();
    TimerTask timerTask = new TimerTask() {
      public void run() {
        timerHandler.post(new Runnable() {
          @Override
          public void run() {
            synchronized (holdToStartTimerSync) {
              if (holdToStartTs > 0) {
                long remainingHoldTime = Math.max(0, HOLD_TO_START_MIN_DURATION - (System.currentTimeMillis() - holdToStartTs));
                setTitle(String.format("%.1f", ((float) remainingHoldTime / 1000)));
                if (remainingHoldTime == 0) {
                  stopHoldToStartTimer();
                  setTitle(getString(R.string.ready), getResources().getColor(R.color.green));
                }
              }
            }
          }
        });
      }
    };
    holdToStartTimer.schedule(timerTask, 1, REFRESH_INTERVAL);
  }

  private void stopHoldToStartTimer() {
    if (holdToStartTimer != null) {
      holdToStartTimer.cancel();
      holdToStartTimer.purge();
      holdToStartTimer = null;
    }
  }

  private void generateScramble() {
    if (cubeType != null) {
      boolean foundScramble = getAndDisplayNewScramble();
      if (!foundScramble) {
        tvScramble.setText(R.string.scramble_generating);
        // couldn't find scramble in cache (for special scrambles like f2l, edges only etc), wait for a GENERATED event to check again
        ScramblerService.INSTANCE.addRandomStateGenListener(randomStateGenListener);
      }
    }
  }

  private boolean getAndDisplayNewScramble() {
    boolean foundScramble = false;
    String[] scramble = ScramblerService.INSTANCE.getScramble(cubeType, solveType.getScrambleType());
    if (scramble != null) {
      currentScramble = scramble;
      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          tvScramble.setText(ScrambleFormatterService.INSTANCE.formatToColoredScramble(currentScramble, cubeType, currentOrientation));
        }
      });
      foundScramble = true;
    }
    return foundScramble;
  }

  private void timerStarted() {
    setKeepScreenOn(true);
    showMenuButton(false);
    dismissRecordOverlay();
  }

  private void dismissRecordOverlay() {
    overlayHandler.removeCallbacks(hideRecordOverlay);
    if (recordOverlayCard != null) {
      recordOverlayCard.animate().cancel();
      recordOverlayCard.setVisibility(View.GONE);
    }
  }

  private void timerStopped() {
    setKeepScreenOn(keepScreenOnWhenTimerOff);
    showMenuButton(true);
  }

  private void setKeepScreenOn(boolean keepOn) {
    layout.setKeepScreenOn(keepOn);
  }

  private void setSolvesCount(int solvesCount) {
    this.solvesCount = Math.max(0, solvesCount);
    tvSolvesCount.setText(String.valueOf(solvesCount));
  }

  private void refreshAvgFields(boolean showNotifications) {
    for (Animation a : animations) {
      a.cancel();
    }
    animations = new ArrayList<Animation>();

    final List<RecordInfo> records = new ArrayList<RecordInfo>();

    if (solveType.hasSteps()) {
      ((TextView) findViewById(R.id.tvAvgOfFive)).setText(
      FormatterService.INSTANCE.formatStepsTimes(solveAverages.getStepsAvgOf5()));
      ((TextView) findViewById(R.id.tvAvgOfTwelve)).setText(
      FormatterService.INSTANCE.formatStepsTimes(solveAverages.getStepsAvgOf12()));
      ((TextView) findViewById(R.id.tvAvgOfFifty)).setText(
      FormatterService.INSTANCE.formatStepsTimes(solveAverages.getStepsAvgOf50()));
      ((TextView) findViewById(R.id.tvAvgOfHundred)).setText(
      FormatterService.INSTANCE.formatStepsTimes(solveAverages.getStepsAvgOf100()));
      ((TextView) findViewById(R.id.tvAvgOfLife)).setText(
      FormatterService.INSTANCE.formatStepsTimes(solveAverages.getStepsAvgOfLifetime()));
    } else if (solveType.isBlind()) {
      tvAccuracy.setText(FormatterService.INSTANCE.formatPercentage(solveAverages.getLifetimeAccuracy()));
      refreshAvgField(R.id.tvMeanOfThree, solveAverages.getMeanOf3(), getString(R.string.NA));
      collectRecord(records, refreshAvgFieldWithRecord(R.id.tvBestMeanOfThree, solveAverages.getBestOf3(),
          (prevSolveAverages != null ? prevSolveAverages.getBestOf3() : null), getString(R.string.NA), showNotifications, "Mo3", 1, false));
      collectRecord(records, refreshAvgFieldWithRecord(R.id.tvLifetimeBest, solveAverages.getBestOfLifetime(),
          (prevSolveAverages != null ? prevSolveAverages.getBestOfLifetime() : null), getString(R.string.NA), showNotifications, getString(R.string.record_label_lifetime), 0, true));

      refreshAvgField(R.id.tvLifetimeAvg, solveAverages.getAvgOfLifetime(), getString(R.string.NA));
      refreshAvgField(R.id.tvAvgOfTwelve, solveAverages.getAvgOf12(), "-");
      refreshAvgField(R.id.tvAvgOfFifty, solveAverages.getAvgOf50(), "-");
      refreshAvgField(R.id.tvAvgOfHundred, solveAverages.getAvgOf100(), "-");
      ((TextView) findViewById(R.id.tvBestOfTwelve)).setText(
          FormatterService.INSTANCE.formatPercentage(solveAverages.getAccuracyOf12(), "-"));
      ((TextView) findViewById(R.id.tvBestOfFifty)).setText(
          FormatterService.INSTANCE.formatPercentage(solveAverages.getAccuracyOf50(), "-"));
      ((TextView) findViewById(R.id.tvBestOfHundred)).setText(
          FormatterService.INSTANCE.formatPercentage(solveAverages.getAccuracyOf100(), "-"));
    } else {
      refreshAvgField(R.id.tvAvgOfFive, solveAverages.getAvgOf5(), "-");
      refreshAvgField(R.id.tvAvgOfTwelve, solveAverages.getAvgOf12(), "-");
      refreshAvgField(R.id.tvAvgOfFifty, solveAverages.getAvgOf50(), "-");
      refreshAvgField(R.id.tvAvgOfHundred, solveAverages.getAvgOf100(), "-");
      refreshAvgField(R.id.tvLifetimeAvg, solveAverages.getAvgOfLifetime(), getString(R.string.NA));
      refreshAvgField(R.id.tvMeanOfThree, solveAverages.getMeanOf3(), getString(R.string.NA));

      collectRecord(records, refreshAvgFieldWithRecord(R.id.tvBestOfFive, solveAverages.getBestOf5(),
          (prevSolveAverages != null ? prevSolveAverages.getBestOf5() : null), "-", showNotifications, "Ao5", 2, false));
      collectRecord(records, refreshAvgFieldWithRecord(R.id.tvBestOfTwelve, solveAverages.getBestOf12(),
          (prevSolveAverages != null ? prevSolveAverages.getBestOf12() : null), "-", showNotifications, "Ao12", 3, false));
      collectRecord(records, refreshAvgFieldWithRecord(R.id.tvBestOfFifty, solveAverages.getBestOf50(),
          (prevSolveAverages != null ? prevSolveAverages.getBestOf50() : null), "-", showNotifications, "Ao50", 4, false));
      collectRecord(records, refreshAvgFieldWithRecord(R.id.tvBestOfHundred, solveAverages.getBestOf100(),
          (prevSolveAverages != null ? prevSolveAverages.getBestOf100() : null), "-", showNotifications, "Ao100", 5, false));
      collectRecord(records, refreshAvgFieldWithRecord(R.id.tvLifetimeBest, solveAverages.getBestOfLifetime(),
          (prevSolveAverages != null ? prevSolveAverages.getBestOfLifetime() : null), getString(R.string.NA), showNotifications, getString(R.string.record_label_lifetime), 0, true));
    }

    if (showNotifications) {
      showRecordsSummary(filterRecordsForNotification(records));
    }
  }

  // Applies the "New record panel" setting: ANY shows all records, PB_ONLY keeps only the
  // lifetime best single, NEVER shows nothing. Only gates the panel, not the in-table highlight.
  private List<RecordInfo> filterRecordsForNotification(List<RecordInfo> records) {
    switch (Options.INSTANCE.getRecordNotificationMode()) {
      case NEVER:
        return new ArrayList<RecordInfo>();
      case PB_ONLY:
        List<RecordInfo> pbOnly = new ArrayList<RecordInfo>();
        for (RecordInfo r : records) {
          if (r.isPB) {
            pbOnly.add(r);
          }
        }
        return pbOnly;
      default:
        return records;
    }
  }

  private String formatAvgField(Long f, String defaultValue) {
    return FormatterService.INSTANCE.formatSolveTime(f, defaultValue);
  }

  private void refreshAvgField(int fieldId, Long value, String defaultValue) {
    TextView tv = (TextView) findViewById(fieldId);
    tv.setText(formatAvgField(value, defaultValue));
    tv.setTextColor(defaultTextColor);
    tv.setTypeface(null, Typeface.BOLD);
  }

  private void clearAvgRecordStyle() {
    prevSolveAverages = null;
    List<TextView> tvs = new ArrayList<TextView>();
    tvs.add((TextView) findViewById(R.id.tvBestOfFive));
    tvs.add((TextView) findViewById(R.id.tvBestOfTwelve));
    tvs.add((TextView) findViewById(R.id.tvBestOfFifty));
    tvs.add((TextView) findViewById(R.id.tvBestOfHundred));
    tvs.add((TextView) findViewById(R.id.tvLifetimeBest));
    for (TextView tv : tvs) {
      tv.setTextColor(defaultTextColor);
      tv.setTypeface(null, Typeface.BOLD);
    }
    if (animations != null) {
      for (Animation a : animations) {
        a.cancel();
      }
    }
  }

  // Refreshes a "best of" field, animating it (and, for the PB, showing the banner) on a new
  // record. Returns a RecordInfo for the summary toast, or null when no record was set.
  private RecordInfo refreshAvgFieldWithRecord(int fieldId, Long value, Long previousValue, String defaultValue,
                                               boolean showNotifications,
                                               String recordLabel, int priority, boolean isPB) {
    refreshAvgField(fieldId, value, defaultValue);
    if (historySolvesCount > MIN_TIMES_FOR_RECORD_NOTIFICATION && previousValue != null && value != null && value < previousValue && !solveType.hasSteps()) {
      final int recordColor = getResources().getColor(R.color.new_record);
      final TextView tv = (TextView) findViewById(fieldId);
      tv.setTypeface(null, Typeface.BOLD);

      if (showNotifications) {
        final int defaultColor = defaultTextColor.getDefaultColor();

        // animate text view color
        Animation a = new Animation() {
          private int animationTimes = 3;
          private int animationStepsCounts = (animationTimes * 2) - 1;
          private int stepDurationMs = 1000 / animationStepsCounts;

          @Override
          protected void applyTransformation(float interpolatedTime, Transformation t) {
            int interpolatedTimeMs = (int) (interpolatedTime * 1000);
            int curStep = interpolatedTimeMs / stepDurationMs;
            int timeInStepMs = interpolatedTimeMs % stepDurationMs;
            float stepProgression = (float) timeInStepMs / stepDurationMs; // the +1 is to avoid division by 0
            if (curStep % 2 == 0) { // default to yellow
              tv.setTextColor(GUIUtils.getColorCodeBetween(defaultColor, recordColor, stepProgression));
            } else { // yellow to default
              tv.setTextColor(GUIUtils.getColorCodeBetween(recordColor, defaultColor, stepProgression));
            }
          }
        };
        a.setDuration(5000);
        tv.startAnimation(a);
        animations.add(a);
      } else {
        tv.setTextColor(recordColor);
      }
      return new RecordInfo(recordLabel, value, previousValue, priority, isPB);
    }
    return null;
  }

  private void collectRecord(List<RecordInfo> records, RecordInfo record) {
    if (record != null) {
      records.add(record);
    }
  }

  // Fills the record overlay (header + one aligned row per record: label, margin, prev best)
  // and animates it in. Rows live in the layout and are shown/hidden so scaling still applies.
  private void showRecordsSummary(List<RecordInfo> records) {
    if (records.isEmpty() || recordOverlayCard == null || recordOverlayTable == null) {
      return;
    }
    Collections.sort(records, new Comparator<RecordInfo>() {
      @Override
      public int compare(RecordInfo a, RecordInfo b) {
        return Integer.compare(a.priority, b.priority);
      }
    });
    tvRecordOverlayHeader.setText("🏆 " + getString(
        records.size() == 1 ? R.string.new_record : R.string.record_toast_header));
    for (int i = 0; i < recordOverlayTable.getChildCount(); i++) {
      TableRow row = (TableRow) recordOverlayTable.getChildAt(i);
      if (i < records.size()) {
        RecordInfo r = records.get(i);
        ((TextView) row.getChildAt(0)).setText(r.label);
        ((TextView) row.getChildAt(1)).setText(
            FormatterService.INSTANCE.formatSolveTimeDifference(r.previous - r.value));
        ((TextView) row.getChildAt(2)).setText(
            getString(R.string.record_overlay_prev, FormatterService.INSTANCE.formatSolveTime(r.previous)));
        row.setVisibility(View.VISIBLE);
      } else {
        row.setVisibility(View.GONE);
      }
    }
    showRecordOverlay();
  }

  // In-screen replacement for a toast (system toasts are capped at 2 lines on Android 12+).
  private void showRecordOverlay() {
    if (recordOverlayCard == null) {
      return;
    }
    overlayHandler.removeCallbacks(hideRecordOverlay);
    recordOverlayCard.animate().cancel();
    recordOverlayCard.setAlpha(0f);
    recordOverlayCard.setVisibility(View.VISIBLE);
    recordOverlayCard.animate().alpha(1f).setDuration(250);
    overlayHandler.postDelayed(hideRecordOverlay, 4000);
  }

  private final Runnable hideRecordOverlay = new Runnable() {
    @Override
    public void run() {
      if (recordOverlayCard == null) {
        return;
      }
      recordOverlayCard.animate().alpha(0f).setDuration(400).withEndAction(new Runnable() {
        @Override
        public void run() {
          recordOverlayCard.setVisibility(View.GONE);
        }
      });
    }
  };

  private static class RecordInfo {
    final String label;
    final long value;
    final long previous;
    final int priority; // lower = shown first
    final boolean isPB; // true for the lifetime best single (PB), false for average records

    RecordInfo(String label, long value, long previous, int priority, boolean isPB) {
      this.label = label;
      this.value = value;
      this.previous = previous;
      this.priority = priority;
      this.isPB = isPB;
    }
  }

  private boolean onTouchEvent(int parMotionEventAction) {
    if (currentScramble == null) {
      // don't allow to do anything if there is no scramble (can happen for special scramble types when scrambles are not yet generated)
      return false;
    }

    // change bg color
    if (parMotionEventAction == MotionEvent.ACTION_DOWN) {
      if (System.currentTimeMillis() - lastTimerStopTs >= STOP_START_DELAY) {
        layout.setBackgroundResource(pushedBackgroundColor);
      } else {
        return false; // to avoid receiving the ACTION_UP
      }
    } else if (parMotionEventAction == MotionEvent.ACTION_UP) {
      layout.setBackgroundResource(defaultBackgroundColor);
      if (ignoreActionUp) {
        ignoreActionUp = false;
        return true;
      }
    }
    // handle timer start/stop
    if (timerState == TimerState.RUNNING && parMotionEventAction == MotionEvent.ACTION_DOWN) {
      if (solveType.hasSteps()) {
        nextSolveStep();
        if (stepsTimes.size() == solveType.getSteps().length) {
          stopTimer(true);
        }
      } else {
        stopTimer(true);
      }
      ignoreActionUp = true; // to avoid starting timer again when releasing
    } else if (solveType.isBlind()) {
      if (parMotionEventAction == MotionEvent.ACTION_UP) {
        // no inspection for blind solve types
        startTimer();
      }
    } else if (inspectionMode == InspectionMode.HOLD_AND_RELEASE) {
      if (timerState == TimerState.STOPPED && parMotionEventAction == MotionEvent.ACTION_DOWN) {
        startInspectionTimer();
      } else if (timerState == TimerState.INSPECTING && parMotionEventAction == MotionEvent.ACTION_UP) {
        stopInspectionTimer();
        startTimer();
      }
    } else if (inspectionMode == InspectionMode.AUTOMATIC) {
      if (timerState == TimerState.STOPPED && parMotionEventAction == MotionEvent.ACTION_UP) {
        startInspectionTimer();
      } else if (timerState == TimerState.INSPECTING && parMotionEventAction == MotionEvent.ACTION_UP) {
        stopInspectionTimer();
        startTimer();
      }
    } else if (inspectionMode == InspectionMode.OFFICIAL) {
      if (parMotionEventAction == MotionEvent.ACTION_DOWN && ignoreActionUp) {
        ignoreActionUp = false;
      }
      if (timerState == TimerState.STOPPED && parMotionEventAction == MotionEvent.ACTION_UP && inspectionTime > 0) {
        startInspectionTimer();
      } else if (timerState == TimerState.INSPECTING || inspectionTime == 0) {
        synchronized (holdToStartTimerSync) {
          if (parMotionEventAction == KeyEvent.ACTION_DOWN) {
            startHoldToStartTimer();
          } else if (parMotionEventAction == MotionEvent.ACTION_UP) {
            stopHoldToStartTimer();
            if (System.currentTimeMillis() - holdToStartTs > HOLD_TO_START_MIN_DURATION) { // if screen pushed for long enough
              stopInspectionTimer();
              startTimer();
              setDefaultBannerText();
            } else {
              if (inspectionTime > 0) {
                setTitle(R.string.inspection);
              } else {
                setDefaultBannerText();
              }
            }
            holdToStartTs = 0;
          }
        }
      }
    }
    return true;
  }

  private class SolveAverageCallback extends DataCallback<SolveAverages> {
    @Override
    public synchronized void onData(final SolveAverages data) {
      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          prevSolveAverages = solveAverages;
          solveAverages = data;
          if (data.getSolveTime() != null) { // a plain averages refresh carries no solve; keep the one we have
            lastSolveTime = data.getSolveTime();
          }
          refreshAvgFields(true);
        }
      });
    }
  }

  private OnTouchListener layoutTouchListener = new OnTouchListener() {
    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
      return onTouchEvent(motionEvent.getAction());
    }
  };

}
