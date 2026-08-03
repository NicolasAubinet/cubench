package com.cube.nanotimer.gui;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
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
import android.view.ViewStub;
import android.view.animation.Animation;
import android.view.animation.OvershootInterpolator;
import android.view.animation.Transformation;
import android.animation.ValueAnimator;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import com.cube.nanotimer.App;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.Options.BigCubesNotation;
import com.cube.nanotimer.Options.InspectionMode;
import com.cube.nanotimer.Options.SessionTimesDisplay;
import com.cube.nanotimer.R;
import com.cube.nanotimer.SoundManager;
import com.cube.nanotimer.cube.LiveCubeView;
import com.cube.nanotimer.cube.ScrambleFollower;
import com.cube.nanotimer.cube.SmartCubeChip;
import com.cube.nanotimer.cube.SolveBreakdown;
import com.cube.nanotimer.cube.SmartCubeSolveController;
import com.cube.nanotimer.cube.SolveSolution;
import com.cube.nanotimer.cube.SolveStepConverter;
import com.cube.nanotimer.cube.SolveTypeMethod;
import com.cube.nanotimer.gui.widget.HistoryDetailDialog;
import com.cube.nanotimer.gui.widget.InAppReviewManager;
import com.cube.nanotimer.gui.widget.ResultListener;
import com.cube.nanotimer.gui.widget.SessionDetailDialog;
import com.cube.nanotimer.gui.widget.SmartCubeConnectDialog;
import com.cube.nanotimer.gui.widget.SolveStepBar;
import com.cube.nanotimer.gui.widget.TimeChangedHandler;
import com.cube.nanotimer.gui.widget.dialog.AddNewTimeDialog;
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
import com.cube.nanotimer.util.view.EnterAnimation;
import com.cube.nanotimer.util.view.FocusDot;
import com.cube.nanotimer.util.view.FocusTransition;
import com.cube.nanotimer.util.view.InspectionRingView;
import com.cube.nanotimer.util.view.ParticleView;
import com.cube.nanotimer.util.view.PuzzleIcons;
import com.cube.nanotimer.util.view.ScrambleFollowAnimator;
import com.cube.nanotimer.util.view.SessionBarsView;
import com.cube.nanotimer.util.view.SolveTypeIcons;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.ScrambleType;
import com.cube.nanotimer.vo.SolveAverages;
import com.cube.nanotimer.vo.SolveStep;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class TimerActivity extends NanoTimerActivity implements ResultListener, TimeChangedHandler {

  private enum TimerState {STOPPED, RUNNING, INSPECTING}

  private TextView tvTimer;
  private TextView tvScramble;
  private TextView tvSolvesCount;
  private TextView tvBanner;
  private View identityRow;
  private View identityStrip;
  private ViewGroup layout;
  private View timerBox;
  private View scrambleBox;
  private View sessionLayout;
  private FocusDot focusDot;
  private TableLayout sessionTimesLayout;
  private SessionBarsView sessionBars;
  private int sessionTimeColor; // the grid's own text colour, which an uncoloured bar takes too
  private TextView tvVerdictChip;

  /** The statistics cell each record belongs to, by {@link RecordInfo#priority}. */
  private static final int[] RECORD_TILE_BY_PRIORITY = {
    R.id.footerPbCell, R.id.statTileOne, R.id.statTileOne,
    R.id.statTileTwo, R.id.statTileThree, R.id.statTileFour,
  };

  private CubeType cubeType;
  private SolveType solveType;
  private String[] currentScramble;
  private SolveTime lastSolveTime;
  private List<SolveStep> lastSolveSteps = Collections.emptyList(); // the cube's breakdown of lastSolveTime, if it saw it
  private String lastSolveMoves = ""; // its moves, which outlive the breakdown when no method matched
  private String lastSolveGyroTrack; // the small rotations it was turned with, null without a gyro
  private CubeMethod lastSolveMethod; // the method its milestones fitted, null when they fitted none
  private Integer lastSolveStoppedStep; // the step it stopped in, null when the cube saw it finish
  // What the step bar and the line under it are showing, kept so a rotation can draw them again:
  // the views are rebuilt from scratch, and the solve they described is not re-read from anywhere.
  private List<SolveStep> shownSteps = Collections.emptyList();
  private String[] shownStepNames;
  private CharSequence shownStats;
  private boolean discardWhenSaved; // discard confirmed while the solve was still being saved
  private boolean recordPending; // the stopped solve is still waiting on the cube before it is saved
  private boolean skipRecordPanel; // suppress the record panel on the next refresh (a discard-bound stop, or a delete)
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
  private ColorStateList secondaryTextColor;
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
  private LiveCubeView liveCube;
  private SmartCubeSolveController solveController;
  private SolveStepBar solveStepBar;
  private TextView tvSolveStats; // "N moves · X.X TPS" shown under the bar after a smart-cube solve
  private ParticleView particleView; // full-screen confetti overlay, fired on a personal best
  private InspectionRingView inspectionRing; // full-screen overlay, drawn only while inspecting
  private FocusTransition focusTransition; // the ring leaving and the digits arriving, as one move
  private ScrambleFollowAnimator scrambleAnimator; // subtle per-move zoom during a cube follow
  private boolean oversteppedInspection = false;
  private boolean reviewRequested = false; // at most one review request per timer session

  private long lastTimerStartTs;
  private long lastTimerStopTs;
  private boolean ignoreActionUp;
  private final long START_STOP_DELAY = 150; // to avoid stopping timer too quickly after a start
  private final long STOP_START_DELAY = 500; // to avoid starting timer too quickly after a stop

  private int inspectionTime;
  private int lastInspectionSecond = -1; // the ring is redrawn far oftener than the count changes
  private InspectionMode inspectionMode;
  private boolean soundsEnabled;
  private boolean keepScreenOnWhenTimerOff;

  private int groundColor = R.color.graybg; // the ground at rest; a running solve drops it a shade
  private int pushedGroundColor = R.color.pushedbg; // the same ground with a finger on it
  private boolean surfacePressed; // and whether there is one on it

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
    solveController = new SmartCubeSolveController(new SolveControllerListener());
    liveCube = new LiveCubeView(this, layoutTouchListener);
    initActionBar();

    inspectionTime = Options.INSTANCE.getInspectionTime();
    inspectionMode = Options.INSTANCE.getInspectionMode();
    soundsEnabled = Options.INSTANCE.isInspectionSoundsEnabled();
    keepScreenOnWhenTimerOff = Options.INSTANCE.isKeepTimerScreenOnWhenTimerOff();

    addOverlays();
    initViews();

    // Sampled from a statistics value, which is the colour a plain (non-record) one is drawn in.
    defaultTextColor = ((TextView) findViewById(R.id.tvAvgOfFive)).getTextColors();
    secondaryTextColor = ((TextView) findViewById(R.id.tvBestOfFive)).getTextColors();
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
    solveController.start();
    liveCube.start();
    applySessionStripChoice(); // both this and the coloring below may have changed in the settings
    refreshSessionFields();
  }

  @Override
  protected void onPause() {
    super.onPause();
    smartCubeChip.stop();
    solveController.stop();
    liveCube.stop();
  }

  @Override
  protected void onDestroy() {
    if (liveCube != null) { // onCreate finishes early without a solve type, and never builds one
      liveCube.destroy();
    }
    if (focusDot != null) {
      focusDot.hide();
    }
    if (focusTransition != null) { // nothing left to fade on a screen that is going
      focusTransition.ringGone();
    }
    super.onDestroy();
  }

  private class SolveControllerListener implements SmartCubeSolveController.Listener {
    @Override
    public void onCubeAutoStart() {
      if (timerState == TimerState.INSPECTING) {
        stopInspectionTimer();
        startTimer();
      } else if (timerState == TimerState.STOPPED) {
        startTimer();
      }
    }

    @Override
    public void onCubeAutoStop() {
      if (timerState != TimerState.RUNNING) {
        return;
      }
      if (!solveType.hasSteps()) {
        stopTimer(true);
      } else if (stepsTimes.size() == solveType.getSteps().length - 1) {
        // Only the last step can end on solved: stopping from an earlier one would have to invent
        // times for the steps that were never tapped. Behind on taps, the user simply taps as before.
        nextSolveStep();
        stopTimer(true);
      }
    }

    @Override
    public void onScrambleFollowChanged() {
      renderScramble();
      reserveStepBreakdownSpace();
      refreshLiveCubeSuppression(); // fires on the first followed move, which is when it must go
    }
  }

  private void renderScramble() {
    if (currentScramble == null) {
      scrambleAnimator.reset();
      tvScramble.setText(R.string.scramble_generating);
      return;
    }
    switch (solveController.getFollowMode()) {
      case NEEDS_SOLVE:
        scrambleAnimator.reset();
        tvScramble.setText(R.string.smart_cube_solve_first);
        break;
      case SOLVING:
        scrambleAnimator.reset();
        tvScramble.setText(R.string.smart_cube_solving);
        break;
      case FOLLOWING:
        if (solveController.isReadyToSolve()) {
          scrambleAnimator.reset();
          tvScramble.setText(R.string.smart_cube_ready_to_solve);
        } else if (solveController.isWrong()) {
          scrambleAnimator.reset();
          tvScramble.setText(ScrambleFormatterService.INSTANCE.formatReverseMoves(
              getString(R.string.smart_cube_undo), solveController.getReverseMoves()));
        } else {
          // The animator owns the per-move zoom; it sets the (already coloured) progress text itself.
          int doneCount = solveController.getDoneCount();
          scrambleAnimator.show(ScrambleFormatterService.INSTANCE.formatScrambleWithProgress(
              currentScramble, cubeType, currentOrientation, doneCount), doneCount);
        }
        break;
      default:
        scrambleAnimator.reset();
        tvScramble.setText(ScrambleFormatterService.INSTANCE.formatToColoredScramble(
            currentScramble, cubeType, currentOrientation));
        break;
    }
  }

  /**
   * The bar carries nothing but its buttons: the strip below it names the screen. Its empty space
   * still starts and stops the timer, so the bar is not a dead zone the way a plain toolbar is.
   */
  private void initActionBar() {
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setTitle("");

    Toolbar decorToolbar = findToolbar(getWindow().getDecorView());
    if (decorToolbar != null) {
      decorToolbar.setOnTouchListener(layoutTouchListener);
    }
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

  /**
   * The two things drawn over the whole screen rather than in it. Both are non-clickable, so the
   * timer stays one tap target, and both idle drawing nothing. {@code setContentView} empties the
   * content parent, so a rotation has to put them back.
   */
  private void addOverlays() {
    particleView = new ParticleView(this); // the personal-best confetti
    addContentView(particleView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    inspectionRing = new InspectionRingView(this);
    addContentView(inspectionRing, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    focusTransition = new FocusTransition(inspectionRing); // a rotation brings a new ring with it
  }

  private void initViews() {
    tvTimer = (TextView) findViewById(R.id.tvTimer);
    tvScramble = (TextView) findViewById(R.id.tvScramble);
    tvSolvesCount = (TextView) findViewById(R.id.tvSolvesCount);
    tvBanner = (TextView) findViewById(R.id.tvBanner);
    identityRow = findViewById(R.id.identityRow);
    identityStrip = findViewById(R.id.identityStrip);
    timerBox = findViewById(R.id.timerBox);
    scrambleBox = findViewById(R.id.scrambleBox);
    sessionLayout = findViewById(R.id.sessionLayout);
    if (focusDot != null) { // a rotation leaves the old one animating a view that is gone
      focusDot.hide();
    }
    focusDot = new FocusDot(findViewById(R.id.focusDot));
    focusDot.setColor(getResources().getColor(identityColor()));
    sessionTimesLayout = (TableLayout) findViewById(R.id.sessionTimesLayout);
    sessionBars = (SessionBarsView) findViewById(R.id.sessionBars);
    sessionTimeColor = getSessionTextView(0).getCurrentTextColor(); // read before any is recoloured
    tvVerdictChip = (TextView) findViewById(R.id.tvVerdictChip);
    solveStepBar = (SolveStepBar) findViewById(R.id.solveStepBar);
    tvSolveStats = (TextView) findViewById(R.id.tvSolveStats); // absent in landscape, so always null-check
    scrambleAnimator = new ScrambleFollowAnimator(tvScramble);

    if (currentOrientation == Configuration.ORIENTATION_PORTRAIT && cubeType == CubeType.SEVEN_BY_SEVEN
        && tvTimer instanceof DigitalTextView) {
      ((DigitalTextView) tvTimer).reduceBaseTextSize(5);
    }

    Float scrambleTextSize = getCubeTypeScrambleTextSize();
    if (scrambleTextSize != null) {
      tvScramble.setTextSize(TypedValue.COMPLEX_UNIT_PX, scrambleTextSize);
    }


    setUpStatisticsBlocks();
    fillIdentityStrip();

    layout = (ViewGroup) findViewById(R.id.mainLayout);
    layout.setOnTouchListener(layoutTouchListener);

    // The cube takes the same listener the action bar does, so it is not a dead zone either.
    liveCube.bind((ViewStub) findViewById(R.id.stubLiveCube), findViewById(R.id.timerTopSpace));
    if (timerState == TimerState.STOPPED) {
      setKeepScreenOn(keepScreenOnWhenTimerOff);
    } else {
      setKeepScreenOn(true);
    }
    // A rotation mid solve rebuilds the screen in its resting state, so put the focus back.
    applyFocus(timerState != TimerState.STOPPED, standsInForDigits());
  }

  /**
   * A solve is the one thing this screen exists for, so while one runs it is all that is on screen:
   * the digits, the cross that abandons them, and nothing else. Hidden rather than removed — the
   * block below carries the layout's weight, and taking it out would slide the time down mid solve.
   *
   * @param standIn draw the breathing dot instead of the digits, for a solve timed without them
   */
  private void applyFocus(boolean on, boolean standIn) {
    boolean inspecting = (timerState == TimerState.INSPECTING);
    if (on) {
      focusTransition.panelsOut(identityStrip, scrambleBox, sessionLayout);
    } else {
      focusTransition.panelsIn(identityStrip, scrambleBox, sessionLayout);
    }
    // The ring carries the count while inspecting, and the dot stands in for a solve timed blind.
    tvTimer.setVisibility(on && (standIn || inspecting) ? View.INVISIBLE : View.VISIBLE);
    groundColor = on ? R.color.timer_focus_bg : R.color.graybg;
    pushedGroundColor = on ? R.color.timer_focus_pushedbg : R.color.pushedbg;
    applyGround();
    if (on && standIn) {
      focusDot.show();
    } else {
      focusDot.hide();
    }
    if (inspecting) {
      inspectionRing.start(inspectionTime); // told what it counts before it is opened out
      focusTransition.ringIn();
    } else {
      focusTransition.ringOut();
    }
    // The digits arrive as the ring leaves; a solve ending wants them as they are, at once.
    if (on && tvTimer.getVisibility() == View.VISIBLE) {
      focusTransition.digitsIn(tvTimer);
    } else {
      focusTransition.digitsRest(tvTimer);
    }
    centreOnSurface(on);
  }

  /**
   * The digits sit at the centre of the band the layout leaves them, which is above the middle of
   * the screen once the block below is only holding its place. While the screen is stripped down
   * they take the middle of the timer's own surface instead, and the ring takes the same point, so
   * a solve and the inspection before it do not sit in different places. A translation rather than
   * a layout change: nothing below is measured again, so the digits still cannot drift mid solve.
   */
  private void centreOnSurface(boolean on) {
    if (!on) {
      // Back to where the layout keeps them. The ring holds its own centre until it has finished
      // leaving, which it owns.
      focusTransition.digitsTo(timerBox, 0f, 0f);
      return;
    }
    if (layout.getWidth() > 0) {
      centreNow(false); // in this frame, so the digits are never drawn where the layout left them
      return;
    }
    layout.post(new Runnable() { // nothing is measured yet: the first layout, or after a rotation
      @Override
      public void run() {
        if (timerState != TimerState.STOPPED) { // the solve ended before the layout settled
          centreNow(true); // a screen being rebuilt has no movement to make
        }
      }
    });
  }

  /**
   * Puts the digits and the ring on the middle of the surface. Sideways the timer has only half
   * the screen, and in a solve the other half is empty, so the box moves across as well as down.
   */
  private void centreNow(boolean settle) {
    int[] at = new int[2];
    layout.getLocationInWindow(at);
    float centreX = at[0] + layout.getWidth() / 2f;
    float centreY = at[1] + layout.getHeight() / 2f;
    timerBox.getLocationInWindow(at);
    // The box may be part way through a move of its own, so take its translation back out: what is
    // wanted is where it has to end up, not a step on from wherever it happens to be.
    float restX = at[0] - timerBox.getTranslationX();
    float restY = at[1] - timerBox.getTranslationY();
    float toX = centreX - (restX + timerBox.getWidth() / 2f);
    float toY = centreY - (restY + timerBox.getHeight() / 2f);
    if (settle) {
      focusTransition.digitsAt(timerBox, toX, toY);
    } else {
      focusTransition.digitsTo(timerBox, toX, toY);
    }
    inspectionRing.getLocationInWindow(at);
    inspectionRing.setCenterY(centreY - at[1]);
  }

  // Held as state rather than only reacted to: the ground changes under a finger that never moved,
  // since in the hold and release mode the whole inspection is one press.
  private void applyGround() {
    layout.setBackgroundResource(surfacePressed ? pushedGroundColor : groundColor);
  }

  /** True while a solve is being timed with the time itself kept back. */
  private boolean standsInForDigits() {
    return timerState == TimerState.RUNNING && !Options.INSTANCE.isShowTimeWhenRunning();
  }

  /**
   * The hue this screen wears. A plain solve type takes the app accent, which says nothing about
   * what is being timed, so under one the puzzle lends its own.
   */
  private int identityColor() {
    int color = SolveTypeIcons.colorForSolveType(solveType);
    return color == R.color.solvetype_plain ? PuzzleIcons.colorForCubeType(cubeType) : color;
  }

  /**
   * Picks which statistics blocks this solve type uses, and what each cell is called. Every
   * variant draws the same surfaces: a cell always holds a window, its value, and one secondary
   * value whose meaning the solve type sets (the best normally, the success rate when blind).
   */
  private void setUpStatisticsBlocks() {
    boolean steps = solveType.hasSteps();
    boolean blind = solveType.isBlind();

    findViewById(R.id.sessionHeader).setVisibility(steps ? View.GONE : View.VISIBLE);
    applySessionStripChoice();
    findViewById(R.id.statTilesLayout).setVisibility(steps ? View.GONE : View.VISIBLE);
    findViewById(R.id.stepSplitsLayout).setVisibility(steps ? View.VISIBLE : View.GONE);
    // A stepped solve type reads its averages as splits, which is the whole footer said better.
    findViewById(R.id.statFooterRow).setVisibility(steps ? View.GONE : View.VISIBLE);
    // A hairline only earns its place between two blocks that are both there.
    findViewById(R.id.statTilesRule).setVisibility(steps ? View.GONE : View.VISIBLE);
    findViewById(R.id.statFooterRule).setVisibility(steps ? View.GONE : View.VISIBLE);

    // The first cell counts a blind attempt in threes, and everything else in fives.
    findViewById(R.id.tvAvgOfFive).setVisibility(blind ? View.GONE : View.VISIBLE);
    findViewById(R.id.tvBestOfFive).setVisibility(blind ? View.GONE : View.VISIBLE);
    findViewById(R.id.tvMeanOfThree).setVisibility(blind ? View.VISIBLE : View.GONE);
    findViewById(R.id.tvBestMeanOfThree).setVisibility(blind ? View.VISIBLE : View.GONE);

    ((TextView) findViewById(R.id.tvStatKeyOne)).setText(blind ? R.string.mo3_label : R.string.ao5_label);
    ((TextView) findViewById(R.id.tvStatKeyTwo)).setText(R.string.ao12_label);
    ((TextView) findViewById(R.id.tvStatKeyThree)).setText(R.string.ao50_label);
    ((TextView) findViewById(R.id.tvStatKeyFour)).setText(R.string.ao100_label);

    if (steps) {
      solveStepBar.prepareLegend(solveType.getSteps().length); // the bar will draw these steps
    }
  }

  /** Which of the two session strips is on. A solve type with steps takes neither. */
  private void applySessionStripChoice() {
    boolean steps = solveType.hasSteps();
    boolean bars = Options.INSTANCE.getSessionTimesDisplay() == SessionTimesDisplay.BARS;
    sessionTimesLayout.setVisibility(!steps && !bars ? View.VISIBLE : View.GONE);
    sessionBars.setVisibility(!steps && bars ? View.VISIBLE : View.GONE);
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

  /** Names the puzzle and the solve type, each in its own colour, as the rest of the app does. */
  private void fillIdentityStrip() {
    ImageView mark = (ImageView) findViewById(R.id.imgIdentityPuzzle);
    mark.setImageResource(PuzzleIcons.forCubeType(cubeType));
    mark.setColorFilter(getResources().getColor(PuzzleIcons.colorForCubeType(cubeType)));

    ((TextView) findViewById(R.id.tvIdentityPuzzle)).setText(cubeType.getName());

    int solveTypeColor = getResources().getColor(SolveTypeIcons.colorForSolveType(solveType));
    TextView pill = (TextView) findViewById(R.id.tvIdentitySolveType);
    pill.setTextColor(solveTypeColor);
    pill.setBackgroundTintList(ColorStateList.valueOf(washOf(solveTypeColor)));
    pill.setText(Utils.toSolveTypeLocalizedName(this, solveType.getName()));
  }

  /** The same hue, quiet enough to sit behind text of it. */
  private static int washOf(int color) {
    return (0x2E << 24) | (color & 0xFFFFFF);
  }

  /**
   * The strip is the screen's banner. At rest it names what is being timed; while a hold is being
   * counted down it says that instead, which is the feedback the action bar title used to carry.
   */
  private void showBanner(CharSequence text, int textColor) {
    tvBanner.setText(text);
    tvBanner.setTextColor(textColor);
    tvBanner.setVisibility(View.VISIBLE);
    identityRow.setVisibility(View.INVISIBLE);
    // Even mid inspection: a hold has nowhere else to speak. Whole, too, since the strip may be
    // part way through standing down for the solve.
    identityStrip.animate().cancel();
    identityStrip.setAlpha(1f);
    identityStrip.setVisibility(View.VISIBLE);
  }

  private void setDefaultBannerText() {
    tvBanner.setVisibility(View.INVISIBLE);
    identityRow.setVisibility(View.VISIBLE);
    identityStrip.setVisibility(timerState == TimerState.STOPPED ? View.VISIBLE : View.INVISIBLE);
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    // The label says which way the tap goes; a DNF with no time to restore keeps offering "DNF".
    menu.findItem(R.id.itDNF).setTitle(
        (lastSolveTime != null && lastSolveTime.canUndoDNF()) ? R.string.undo_dnf : R.string.DNF);
    menu.findItem(R.id.itSessionDetails).setVisible(showMenu && hasNewSession);
    menu.findItem(R.id.itCrossSolver).setVisible(showMenu && isCrossSolverAvailable());
    menu.findItem(R.id.itScrambleView).setVisible(showMenu && ScrambleViewNotation.getRenderKey(cubeType) != null);
    return super.onPrepareOptionsMenu(menu);
  }

  private boolean isCrossSolverAvailable() {
    return cubeType == CubeType.THREE_BY_THREE && scramblesTheWholeCube();
  }

  /** Whether the solve type scrambles the whole cube, rather than a last layer or an F2L case.
   * A null scramble type is the ordinary one, and by far the commonest: it means the full scramble. */
  private boolean scramblesTheWholeCube() {
    ScrambleType scrambleType = solveType.getScrambleType();
    return scrambleType == null || scrambleType.isDefault();
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
    if (solveType.hasSteps()) {
      menu.findItem(R.id.itSessionDetails).setVisible(false);
      menu.findItem(R.id.itNewSession).setVisible(false);
      menu.findItem(R.id.itAddTime).setVisible(false);
    }
    setUpQuickAction(menu);
    // The chip sits with the other items now, greyed until a cube is paired, as on the history
    // screen. The loop above is what takes it away during a run, so the bar stays a tap target.
    MenuItem smartCubeItem = menu.findItem(R.id.itSmartCube);
    smartCubeChip.bind(smartCubeItem != null ? smartCubeItem.getActionView() : null);
    return true;
  }

  /** Promotes the solve type's chosen action to the action bar; the rest stay in the overflow menu. */
  private void setUpQuickAction(Menu menu) {
    int itemId = getQuickActionItemId();
    if (itemId == 0) {
      return;
    }
    MenuItem quickActionItem = menu.findItem(itemId);
    quickActionItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
    if (itemId == R.id.itScrambleView) {
      // The action bar gets the coloured icon, which needs its tint cleared; the overflow keeps
      // the flat white one from the menu XML.
      quickActionItem.setIcon(R.drawable.ic_scramble_view);
      MenuItemCompat.setIconTintList(quickActionItem, null);
    }
  }

  /** The menu item to promote, or 0 when the solve type wants none or its choice does not apply here. */
  private int getQuickActionItemId() {
    switch (solveType.getQuickAction()) {
      case SCRAMBLE_VIEW:
        return ScrambleViewNotation.getRenderKey(cubeType) != null ? R.id.itScrambleView : 0;
      case PLUS_TWO:
        return R.id.itPlusTwo;
      case DNF:
        return R.id.itDNF;
      case DELETE:
        return R.id.itDelete;
      case LAST_SOLVE:
        return R.id.itLastSolve;
      case ADD_TIME:
        return solveType.hasSteps() ? 0 : R.id.itAddTime;
      case CROSS_SOLVER:
        return isCrossSolverAvailable() ? R.id.itCrossSolver : 0;
      default:
        return 0;
    }
  }

  private void openSmartCubeConnect() {
    DialogUtils.showFragment(this, new SmartCubeConnectDialog());
  }

  private void showMenuButton(boolean show) {
    if (this.showMenu != show) {
      this.showMenu = show;
      supportInvalidateOptionsMenu();

      // The navigation slot stays live through a run, as a cross: phones without a back button
      // still need a way to abandon a solve.
      getSupportActionBar().setHomeAsUpIndicator(
          show ? null : ContextCompat.getDrawable(this, R.drawable.ic_cancel_solve));

      // The menu items go with it (the loop in onCreateOptionsMenu), so the rest of the bar is
      // empty during a run and stays a tap target rather than a row of dead controls.
      smartCubeChip.setSuppressed(!show);
      refreshLiveCubeSuppression();
    }
  }

  /**
   * A blind attempt is not to be watched, so the live cube goes for the whole of one: from the
   * first scramble move applied to the cube, through the memorisation and the execution, until the
   * solve ends. The scramble is the part that matters most — what the mirror would otherwise show
   * is the case the solver is about to memorise.
   *
   * <p>Between attempts it comes back, which is where a mirror earns its keep: checking the cube is
   * where the app thinks it is. Nothing is hidden for a sighted solve type.
   */
  private void refreshLiveCubeSuppression() {
    liveCube.setSuppressed(
        solveType.isBlind() && (!showMenu || solveController.isAttemptUnderway()));
  }

  /** A stop tap like any other: it ends the solve, and only then asks whether to keep the time. */
  private void cancelPressed() {
    if (solveType.hasSteps() && stepsTimes.size() < solveType.getSteps().length - 1) {
      stopTimer(false);
      resetTimer();
      return;
    }
    if (solveType.hasSteps()) {
      nextSolveStep(); // the tap closes the last step, exactly as it would anywhere else on screen
    }
    // Don't show the record panel for a solve that will probably get canceled
    skipRecordPanel = true;
    stopTimer(true);
    if (timerState == TimerState.STOPPED) { // a stop too soon after the start is ignored
      confirmDiscardSolve();
    } else {
      skipRecordPanel = false; // nothing was stopped, so no solve is coming to suppress
    }
  }

  /** Offers to throw away the solve that was just stopped and saved. */
  private void confirmDiscardSolve() {
    DialogUtils.showConfirmCancelDialog(this, R.string.discard_solve_title, R.string.discard_solve_confirmation,
        R.string.discard_solve, R.string.keep_solve, new YesNoListener() {
          @Override
          public void onYes() {
            if (lastSolveTime != null && !recordPending) {
              deleteLastSolve();
            } else {
              // Not written yet — and while a record is pending, lastSolveTime is still the solve
              // before it, which must not be the one discarded. Drop this one when it lands.
              discardWhenSaved = true;
            }
          }
        });
  }

  private void deleteLastSolve() {
    if (lastSolveTime == null) {
      DialogUtils.showShortInfoMessage(this, R.string.no_solve_for_action);
      return;
    }
    forgetRecordsOfDeletedSolve();
    App.INSTANCE.getService().deleteTime(lastSolveTime, solveAverageCallback);
    cubeSession.deleteLast();
    historySolvesCount--;
    setSolvesCount(solvesCount - 1);
    refreshSessionFields();
    resetTimer();
    hideStepBreakdown(); // the breakdown belonged to the solve that is now gone
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home && timerState != TimerState.STOPPED) {
      // The cross: a stop tap like any other, which then asks whether to keep the time.
      if (timerState == TimerState.RUNNING) {
        cancelPressed();
      } else {
        stopInspectionTimer();
        resetTimer();
      }
      return true;
    }
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
          } else {
            toggleLastSolveDNF();
          }
          break;
        case R.id.itDelete:
          deleteLastSolve();
          break;
        case R.id.itLastSolve:
          if (lastSolveTime != null) {
            DialogUtils.showFragment(this,
                HistoryDetailDialog.newInstance(lastSolveTime, cubeType, this));
          } else {
            DialogUtils.showShortInfoMessage(this, R.string.no_solve_for_action);
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

  /** Every assignment goes through here so the DNF item's label follows the solve it acts on. */
  private void setLastSolveTime(SolveTime solveTime) {
    lastSolveTime = solveTime;
    supportInvalidateOptionsMenu();
  }

  /**
   * Marks the last solve as a DNF, or takes that DNF back when it still knows the time it
   * replaced. A DNF with nothing to restore is left alone: the tap does nothing.
   */
  private void toggleLastSolveDNF() {
    if (lastSolveTime.canUndoDNF()) {
      lastSolveTime.undoDNF();
      cubeSession.setLastTime(lastSolveTime.getTime());
    } else if (!lastSolveTime.isDNF()) {
      lastSolveTime.setDNF();
      cubeSession.setLastAsDNF();
    } else {
      return;
    }
    App.INSTANCE.getService().saveTime(lastSolveTime, solveAverageCallback);
    tvTimer.setText(FormatterService.INSTANCE.formatSolveTime(lastSolveTime.getTime()));
    setTimerTextColor(lastSolveTime.getTime());
    refreshSessionFields();
    supportInvalidateOptionsMenu(); // the menu item now offers the other direction
  }

  @Override
  public void onTimeChanged(final SolveTime solveTime) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        setLastSolveTime(solveTime);
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
        forgetRecordsOfDeletedSolve();
        cubeSession.deleteLast();
        historySolvesCount--;
        setSolvesCount(solvesCount - 1);
        refreshSessionFields();
        resetTimer();
        hideStepBreakdown(); // the breakdown belonged to the solve that is now gone
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

      setContentView(R.layout.timer_screen);
      addOverlays();
      initViews();

      if (timerState == TimerState.STOPPED) {
        tvTimer.setText(timerText);
      }
      renderScramble();
      setSolvesCount(solvesCount);
      restoreStepBreakdown();

      refreshSessionFields();
      if (solveAverages != null) {
        refreshAvgFields(false, false);
      }
    }
  }

  @Override
  public void onResult(Object... params) {
    final SolveAverages solveAverages = (SolveAverages) params[0];
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        lastSolveSteps = Collections.emptyList(); // a hand-entered time is now the last solve, and no cube saw it
        lastSolveMoves = "";
        lastSolveGyroTrack = null;
        lastSolveMethod = null;
        lastSolveStoppedStep = null;
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
        refreshSolvesCountLabel();
        List<Long> sessionTimes = (cubeSession == null) ? Collections.<Long>emptyList() : cubeSession.getTimes();
        if (sessionTimes.isEmpty()) {
          sessionBars.setTimes(sessionTimes, new int[0]);
          return;
        }
        switch (Options.INSTANCE.getSessionTimesColoring()) {
          case BEST_WORST:
            showSessionTimes(sessionTimes, bestWorstColors(sessionTimes));
            break;
          case ALL_DISPLAYED: {
            // Each of the 12 ranked among the 12.
            TimeColorScale scale = new TimeColorScale(TimerActivity.this);
            scale.setTimes(sessionTimes, false);
            showSessionTimes(sessionTimes, scaleColors(sessionTimes, scale));
            break;
          }
          case MATCH_HISTORY:
            colorSessionMatchHistory();
            break;
          case NONE:
            showSessionTimes(sessionTimes, plainColors(sessionTimes));
            break;
        }
      }
    });
  }

  /** The two strips draw the same times in the same colours; only ever one of them is on. */
  private void showSessionTimes(List<Long> sessionTimes, int[] colors) {
    for (int i = 0; i < sessionTimes.size(); i++) {
      GUIUtils.setSessionTimeCellColor(getSessionTextView(i), sessionTimes.get(i), colors[i]);
    }
    sessionBars.setTimes(sessionTimes, colors);
  }

  // Classic coloring: only the best (green) and worst (red) of the session stand out.
  private int[] bestWorstColors(List<Long> sessionTimes) {
    int bestInd = cubeSession.getBestTimeInd(solveType.isBlind());
    int worstInd = cubeSession.getWorstTimeInd(solveType.isBlind());
    int[] colors = new int[sessionTimes.size()];
    for (int i = 0; i < colors.length; i++) {
      if (sessionTimes.get(i) < 0) {
        colors[i] = getResources().getColor(R.color.dnf_time);
      } else if (i == bestInd) {
        colors[i] = getResources().getColor(R.color.green);
      } else if (i == worstInd) {
        colors[i] = getResources().getColor(R.color.red);
      } else {
        colors[i] = sessionTimeColor;
      }
    }
    return colors;
  }

  // No coloring: every time in the default color, except a DNF, grayed out as it is everywhere.
  private int[] plainColors(List<Long> sessionTimes) {
    int[] colors = new int[sessionTimes.size()];
    for (int i = 0; i < colors.length; i++) {
      colors[i] = sessionTimes.get(i) < 0 ? getResources().getColor(R.color.dnf_time) : sessionTimeColor;
    }
    return colors;
  }

  // Gradient coloring (green=fast → white=median → red=slow) against the given scale.
  private int[] scaleColors(List<Long> sessionTimes, TimeColorScale scale) {
    int[] colors = new int[sessionTimes.size()];
    for (int i = 0; i < colors.length; i++) {
      long time = sessionTimes.get(i);
      colors[i] = scale.colorFor(time, time < 0);
    }
    return colors;
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
            List<Long> sessionTimes = cubeSession.getTimes();
            showSessionTimes(sessionTimes, scaleColors(sessionTimes, scale));
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
    // A discard still waiting on its save is dropped rather than carried into this solve, so a
    // save that never lands can never take the wrong time with it.
    discardWhenSaved = false;
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
    timerState = TimerState.RUNNING; // set before the screen stands down: the focus state reads it
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
    }
    solveController.onTimerStarted();
  }

  private void stopTimer(boolean save) {
    long curTime = System.currentTimeMillis();
    if (curTime - lastTimerStartTs < START_STOP_DELAY) {
      return;
    }
    lastTimerStopTs = curTime;
    final long solveDuration = (curTime - timerStartTs);
    timerState = TimerState.STOPPED;
    if (timer != null) {
      timer.cancel();
      timer.purge();
    }
    timerStopped();
    long time = solveDuration;
    if (oversteppedInspection) {
      time += SolveTime.PLUS_TWO_PENALTY_MS; // started the solve after inspection ran out (official inspection mode)
      oversteppedInspection = false;
    }
    // update time once more to get the ms right
    // (as all ms do not necessarily appear when timing, some are skipped due to refresh interval)
    updateTimerText(time);
    playSolveCompletionFlourish(time);
    // The time is shown the instant the solve ends; what the cube read of it can land a moment
    // later, since a slice ending the solve is only confirmed once the gyro has settled past it.
    final long timeToSave = time;
    recordPending = true;
    solveController.onTimerStopped(() -> recordStoppedSolve(solveDuration, timeToSave, save));
  }

  /**
   * The half of a stop that needs the cube's reading of the solve, so it can run a moment late.
   * The new scramble is generated here too: applying it resets the trackers this reads.
   *
   * @param solveDurationMs what the timer measured, before any penalty
   * @param timeToSave what gets stored, penalty included
   */
  private void recordStoppedSolve(long solveDurationMs, long timeToSave, boolean save) {
    recordPending = false;
    showStepBreakdown(solveDurationMs);
    if (save) {
      saveTime(timeToSave);
    }
    generateScramble();
  }

  // A small pop and accent flash on the time when a smart cube stops the solve, so the finish
  // registers as an event. Tap-timed solves are left exactly as they were, and a DNF gets nothing.
  private void playSolveCompletionFlourish(long time) {
    if (time < 0 || !solveController.isCubeDriven()) {
      return;
    }
    tvTimer.animate().cancel();
    tvTimer.setScaleX(1.18f);
    tvTimer.setScaleY(1.18f);
    tvTimer.animate().scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(new OvershootInterpolator());

    final int accent = getResources().getColor(R.color.lightblue);
    final int endColor = defaultTimerTextColor.getDefaultColor();
    ValueAnimator flash = ValueAnimator.ofFloat(0f, 1f);
    flash.setDuration(500);
    flash.addUpdateListener(a -> tvTimer.setTextColor(
        GUIUtils.getColorCodeBetween(accent, endColor, (float) a.getAnimatedValue())));
    flash.start();
  }

  private void startInspectionTimer() {
    long curTime = System.currentTimeMillis();
    if (curTime - lastTimerStopTs < STOP_START_DELAY) {
      return;
    }
    timerStartTs = curTime;
    oversteppedInspection = false;
    enableScreenRotationChanges(false);
    timerState = TimerState.INSPECTING; // set before the screen stands down: the focus state reads it
    timerStarted();
    resetTimerText();
    clearAvgRecordStyle();
    timer = new Timer();
    TimerTask timerTask = new TimerTask() {
      public void run() {
        timerHandler.post(new Runnable() {
          public void run() {
            updateInspection();
          }
        });
      }
    };
    lastInspectionSecond = -1;
    timer.schedule(timerTask, 1, REFRESH_INTERVAL); // the ring sweeps; the second below still steps
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
      showStepsSoFar();
    }
    stepStartTs = ts;
  }

  /**
   * Fills the bar as the solve goes, one segment per step taken. The same bar then stays for the
   * finished solve, so the steps are read the same way throughout rather than in a list that gives
   * way to something else at the end.
   */
  private void showStepsSoFar() {
    drawStepBar(SolveBreakdown.fromStepTimes(stepsTimes.toArray(new Long[0])), stepNames());
  }

  private void resetTimer() {
    synchronized (timerSync) {
      if (timer != null) {
        timer.cancel();
        timer.purge();
      }
      setLastSolveTime(null);
      lastSolveSteps = Collections.emptyList();
      lastSolveMoves = "";
      lastSolveGyroTrack = null;
      lastSolveMethod = null;
      lastSolveStoppedStep = null;
      timerStartTs = 0;
      resetTimerText();
    }
  }

  private void resetTimerText() {
    String defaultText = FormatterService.INSTANCE.formatSolveTime(0L);
    tvTimer.setText(defaultText);
    setTimerTextColor(0L);
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
    if (!solveTime.isDNF()) {
      // A solve type with its own steps is read through those alone, so the method's are not
      // recorded: nothing would ever show them, and stored is worth keeping equal to shown.
      if (!lastSolveSteps.isEmpty() && !solveType.hasSteps()) {
        solveTime.setSmartcubeMethod(lastSolveMethod);
        solveTime.setSmartcubeSteps(lastSolveSteps);
        solveTime.setSmartcubeStoppedStep(lastSolveStoppedStep); // null unless it stopped short
      }
      if (!lastSolveMoves.isEmpty()) { // the cube timed it, whether or not a method matched
        solveTime.setSmartcubeMoves(lastSolveMoves);
        solveTime.setSmartcubeGyroTrack(lastSolveGyroTrack); // null unless the cube has a gyro
      }
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
  }

  private synchronized void updateInspection() {
    long curTime = System.currentTimeMillis() - timerStartTs;
    final int officialInspectionDnfTime = 2;
    inspectionRing.setElapsed(curTime);
    int seconds = (int) (curTime / 1000);
    if (seconds == lastInspectionSecond) {
      return; // everything below happens once a second, whatever the ring is drawn at
    }
    lastInspectionSecond = seconds;
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
          inspectionRing.setLabel(getString(R.string.plus_two));
          oversteppedInspection = true;
        }
      } else {
        if (inspectionTime > 0) {
          mustDnfTime = true;
        }
      }
    }

    if (mustDnfTime) {
      stopInspectionTimer(); // which puts the ground back, even with the finger still down
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
                showBanner(String.format("%.1f", ((float) remainingHoldTime / 1000)),
                    getResources().getColor(R.color.white));
                if (remainingHoldTime == 0) {
                  stopHoldToStartTimer();
                  showBanner(getString(R.string.ready), getResources().getColor(R.color.green));
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
          boolean is3x3 = (cubeType == CubeType.THREE_BY_THREE);
          boolean followable = is3x3 && ScrambleFollower.canFollow(currentScramble);
          solveController.setScramble(currentScramble, is3x3, followable, solveType.isBlind(),
              SolveTypeMethod.of(solveType));
        }
      });
      foundScramble = true;
    }
    return foundScramble;
  }

  private void timerStarted() {
    setKeepScreenOn(true);
    showMenuButton(false);
    clearVerdict();
    hideStepBreakdown();
    applyFocus(true, standsInForDigits());
  }

  /** @param solveDurationMs what the timer measured, before any penalty: a +2 is not solving time */
  private void showStepBreakdown(long solveDurationMs) {
    lastSolveSteps = SolveStepConverter.toSolveSteps(solveController.getStepTimes());
    lastSolveMethod = solveController.getMethod();
    lastSolveStoppedStep = solveController.getStoppedStep();
    lastSolveMoves = solveController.getSolveMoves(); // captured before the early return: a solve with no breakdown still has moves
    lastSolveGyroTrack = solveController.getGyroTrack();
    if (!scramblesTheWholeCube()) {
      // A partial scramble leaves most milestones already reached, so every method fits it and none
      // is told apart. The moves are still worth keeping; the breakdown would be invented.
      lastSolveSteps = Collections.emptyList();
      lastSolveMethod = null;
      lastSolveStoppedStep = null;
    }
    if (solveType.hasSteps()) {
      showManualStepBreakdown(solveDurationMs);
      return;
    }
    if (lastSolveSteps.isEmpty()) { // no cube drove it, or its milestones fitted no method
      hideStepBreakdown();
      return;
    }
    // The tail is drawn but never stored, so lastSolveSteps stays the form that gets saved.
    List<SolveStep> barSteps = SolveBreakdown.withTail(lastSolveSteps, lastSolveStoppedStep,
        solveDurationMs, lastSolveMoves, lastSolveMethod);
    drawStepBar(SolveBreakdown.withoutTail(barSteps), null);
    solveStepBar.animateIn(); // a small sweep-in, so a finished cube solve feels less abrupt
    // The stats read the whole thing, gap included, so the move count is the same number the detail
    // sheet shows — only the bar leaves it out.
    showSolveStats(SolveSolution.from(lastSolveMoves, barSteps));
  }

  /**
   * On a solve type with its own steps, those steps are the breakdown: the bar draws them, under the
   * names they were given. The method's own steps are still recorded, but they say nothing this
   * screen is about — the user's split replaces them wherever the solve is shown.
   */
  private void showManualStepBreakdown(long solveDurationMs) {
    List<SolveStep> steps = SolveBreakdown.fromStepTimes(stepsTimes.toArray(new Long[0]));
    if (steps.isEmpty()) { // the solve ended before a step was taken
      hideStepBreakdown();
      return;
    }
    drawStepBar(steps, stepNames());
    solveStepBar.animateIn();
    // The steps were timed by tapping, so there are only moves to count when a cube drove them too;
    // with none, this hides itself and the bar stands alone.
    showSolveStats(SolveSolution.from(lastSolveMoves, steps));
  }

  private String[] stepNames() {
    String[] names = new String[solveType.getSteps().length];
    for (int i = 0; i < names.length; i++) {
      names[i] = solveType.getSteps()[i].getName();
    }
    return names;
  }

  // Reveals the solve's move count and turn rate under the bar; hidden when the solve carries no moves.
  // Read even in landscape, where there is no line to show it in, so rotating into portrait finds
  // this solve's stats there rather than the last portrait one's.
  private void showSolveStats(SolveSolution solution) {
    shownStats = solution.isEmpty() ? null : solveStats(solution);
    if (tvSolveStats == null) {
      return;
    }
    if (shownStats == null) {
      tvSolveStats.setVisibility(View.INVISIBLE);
      return;
    }
    tvSolveStats.setText(shownStats);
    tvSolveStats.setAlpha(0f);
    tvSolveStats.setVisibility(View.VISIBLE);
    tvSolveStats.animate().alpha(1f).setDuration(250);
  }

  private CharSequence solveStats(SolveSolution solution) {
    StringBuilder stats = new StringBuilder()
        .append(getString(R.string.breakdown_moves_count, solution.getMoveCount())).append(" · ")
        .append(getString(R.string.breakdown_tps, FormatterService.INSTANCE.formatTps(solution.getTps())));
    // A blind solve is executed as a run of algorithms, and how many it took is a number the solver
    // reads their solve by. A sighted method's parts are not that -- an F2L is four pairs by
    // definition, so counting them says nothing.
    if (lastSolveMethod == CubeMethod.BLIND && solution.getPartCount() > 0) {
      stats.append(" · ").append(getString(R.string.breakdown_algs, solution.getPartCount()));
    }
    return stats;
  }

  /** Draws the bar, and remembers what it drew so a rotation can put the same thing back. */
  private void drawStepBar(List<SolveStep> steps, String[] stepNames) {
    shownSteps = steps;
    shownStepNames = stepNames;
    solveStepBar.setSteps(steps, stepNames);
    solveStepBar.setVisibility(View.VISIBLE);
  }

  /**
   * Puts the breakdown back after the layout was rebuilt for a new orientation. Without this a
   * rotation mid-solve or after one leaves an empty legend where the solve's steps were.
   */
  private void restoreStepBreakdown() {
    if (shownSteps.isEmpty()) {
      reserveStepBreakdownSpace();
      return;
    }
    solveStepBar.setSteps(shownSteps, shownStepNames);
    solveStepBar.setVisibility(View.VISIBLE);
    if (tvSolveStats != null && shownStats != null) { // absent in landscape
      tvSolveStats.setText(shownStats);
      tvSolveStats.setVisibility(View.VISIBLE);
    }
  }

  private void hideStepBreakdown() {
    shownSteps = Collections.emptyList();
    shownStepNames = null;
    shownStats = null;
    int visibility = canBreakDownSolves() ? View.INVISIBLE : View.GONE;
    solveStepBar.setVisibility(visibility);
    if (tvSolveStats != null) {
      tvSolveStats.animate().cancel();
      if (visibility == View.INVISIBLE && tvSolveStats.length() == 0) {
        tvSolveStats.setText(" "); // reserve one line so a finished solve never nudges the bar
      }
      tvSolveStats.setVisibility(visibility);
    }
  }

  /** Keep the bar's height reserved while a cube is connected, so a solve never shifts the layout. */
  private void reserveStepBreakdownSpace() {
    if (solveStepBar.getVisibility() != View.VISIBLE) { // never hide the solve just finished
      hideStepBreakdown();
    }
  }

  private boolean canBreakDownSolves() {
    return solveController.isCubeDriven();
  }

  /**
   * A deleted solve takes its records with it: the panel goes and the cells it lit go back to
   * their normal colour. The refresh the deletion triggers is not allowed to announce a record
   * either, since dropping a bad solve can leave an average better than it was.
   */
  private void forgetRecordsOfDeletedSolve() {
    clearVerdict();
    skipRecordPanel = true;
  }

  private void clearVerdict() {
    tvVerdictChip.setVisibility(View.INVISIBLE);
    clearRecordCells();
  }

  private void timerStopped() {
    setKeepScreenOn(keepScreenOnWhenTimerOff);
    showMenuButton(true);
    applyFocus(false, false);
  }

  private void setKeepScreenOn(boolean keepOn) {
    layout.setKeepScreenOn(keepOn);
  }

  private void setSolvesCount(int solvesCount) {
    this.solvesCount = Math.max(0, solvesCount);
    refreshSolvesCountLabel();
  }

  /**
   * The bars carry no numbers, so beside them the header names the window they are rather than the
   * session's own size. Said once quietly: it is read on the first session and known after that.
   */
  private void refreshSolvesCountLabel() {
    int shown = (cubeSession == null) ? 0 : cubeSession.getTimes().size();
    if (sessionBars.getVisibility() == View.VISIBLE && shown > 0) {
      tvSolvesCount.setText(getString(R.string.window_last_n, shown));
    } else {
      tvSolvesCount.setText(solvesCount + " " + getString(R.string.solves));
    }
  }

  /**
   * @param showNotifications false while a deletion or a discard is settling, so nothing is announced
   * @param forNewSolve true only when a solve has just landed, which is when the verdict speaks
   */
  private void refreshAvgFields(boolean showNotifications, boolean forNewSolve) {
    for (Animation a : animations) {
      a.cancel();
    }
    animations = new ArrayList<Animation>();

    final List<RecordInfo> records = new ArrayList<RecordInfo>();

    if (solveType.hasSteps()) {
      ((TextView) findViewById(R.id.tvSplitsOfFive)).setText(
      FormatterService.INSTANCE.formatStepsTimes(solveAverages.getStepsAvgOf5()));
      ((TextView) findViewById(R.id.tvSplitsOfTwelve)).setText(
      FormatterService.INSTANCE.formatStepsTimes(solveAverages.getStepsAvgOf12()));
      ((TextView) findViewById(R.id.tvSplitsOfFifty)).setText(
      FormatterService.INSTANCE.formatStepsTimes(solveAverages.getStepsAvgOf50()));
      ((TextView) findViewById(R.id.tvSplitsOfHundred)).setText(
      FormatterService.INSTANCE.formatStepsTimes(solveAverages.getStepsAvgOf100()));
      ((TextView) findViewById(R.id.tvAvgOfLife)).setText(
      FormatterService.INSTANCE.formatStepsTimes(solveAverages.getStepsAvgOfLifetime()));
    } else if (solveType.isBlind()) {
      refreshAvgField(R.id.tvMeanOfThree, solveAverages.getMeanOf3(), getString(R.string.NA));
      RecordInfo bestMo3 = refreshAvgFieldWithRecord(R.id.tvBestMeanOfThree, solveAverages.getBestOf3(),
          (prevSolveAverages != null ? prevSolveAverages.getBestOf3() : null), getString(R.string.NA), showNotifications, "Mo3", 1, false);
      collectRecord(records, bestMo3);
      labelAsBest(R.id.tvBestMeanOfThree, solveAverages.getBestOf3(), bestMo3 == null);
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

      RecordInfo best5 = refreshAvgFieldWithRecord(R.id.tvBestOfFive, solveAverages.getBestOf5(),
          (prevSolveAverages != null ? prevSolveAverages.getBestOf5() : null), "-", showNotifications, "Ao5", 2, false);
      RecordInfo best12 = refreshAvgFieldWithRecord(R.id.tvBestOfTwelve, solveAverages.getBestOf12(),
          (prevSolveAverages != null ? prevSolveAverages.getBestOf12() : null), "-", showNotifications, "Ao12", 3, false);
      RecordInfo best50 = refreshAvgFieldWithRecord(R.id.tvBestOfFifty, solveAverages.getBestOf50(),
          (prevSolveAverages != null ? prevSolveAverages.getBestOf50() : null), "-", showNotifications, "Ao50", 4, false);
      RecordInfo best100 = refreshAvgFieldWithRecord(R.id.tvBestOfHundred, solveAverages.getBestOf100(),
          (prevSolveAverages != null ? prevSolveAverages.getBestOf100() : null), "-", showNotifications, "Ao100", 5, false);
      collectRecord(records, best5);
      collectRecord(records, best12);
      collectRecord(records, best50);
      collectRecord(records, best100);
      collectRecord(records, refreshAvgFieldWithRecord(R.id.tvLifetimeBest, solveAverages.getBestOfLifetime(),
          (prevSolveAverages != null ? prevSolveAverages.getBestOfLifetime() : null), getString(R.string.NA), showNotifications, getString(R.string.record_label_lifetime), 0, true));

      // The secondary line of a cell says what it is, since on its own a bare time reads as another average.
      labelAsBest(R.id.tvBestOfFive, solveAverages.getBestOf5(), best5 == null);
      labelAsBest(R.id.tvBestOfTwelve, solveAverages.getBestOf12(), best12 == null);
      labelAsBest(R.id.tvBestOfFifty, solveAverages.getBestOf50(), best50 == null);
      labelAsBest(R.id.tvBestOfHundred, solveAverages.getBestOf100(), best100 == null);
    }

    // The bars are measured against the same Ao12 the cell beside them shows.
    sessionBars.setAverage(solveType.hasSteps() ? null : solveAverages.getAvgOf12());

    if (showNotifications && forNewSolve) {
      List<RecordInfo> toNotify = filterRecordsForNotification(records);
      showVerdict(toNotify);
      celebratePbIfAny(toNotify);
    }
  }

  // Confetti for an actual personal best only (the lifetime best single, never an average), and
  // only among the records the notification setting lets through — so "never notify" stays quiet.
  private void celebratePbIfAny(List<RecordInfo> records) {
    if (particleView == null) {
      return;
    }
    for (RecordInfo r : records) {
      if (r.isPB) {
        particleView.burst();
        return;
      }
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

  /**
   * Names a cell's secondary value as the best one, leaving an absent value as its placeholder.
   *
   * @param plain false while this value is being shown as a new record, which owns its styling
   */
  private void labelAsBest(int fieldId, Long value, boolean plain) {
    TextView tv = (TextView) findViewById(fieldId);
    if (value != null && value > 0) {
      tv.setText(getString(R.string.timer_best_value, FormatterService.INSTANCE.formatSolveTime(value)));
    }
    if (plain) {
      tv.setTextColor(secondaryTextColor);
      tv.setTypeface(null, Typeface.NORMAL);
    }
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

  /**
   * What the solve was worth, said once and only when there is something to say: a record, a
   * session best or worst, or a DNF. A delta on every solve would be noise whatever the number,
   * and the bars below already carry the "beat your recent times" job.
   *
   * <p>Records also light the cell that holds them, which is how the other three are announced.
   */
  private void showVerdict(List<RecordInfo> records) {
    Collections.sort(records, new Comparator<RecordInfo>() {
      @Override
      public int compare(RecordInfo a, RecordInfo b) {
        return Integer.compare(a.priority, b.priority);
      }
    });
    for (RecordInfo r : records) {
      highlightRecordCell(r.priority);
    }
    if (!records.isEmpty()) {
      RecordInfo best = records.get(0);
      String beat = FormatterService.INSTANCE.formatSolveTime(best.previous - best.value);
      String previous = FormatterService.INSTANCE.formatSolveTime(best.previous);
      if (best.isPB) { // gold is for a lifetime best alone, or it stops meaning anything
        showVerdictChip(getString(R.string.verdict_personal_best, beat, previous), R.color.new_record);
        tvTimer.setTextColor(getResources().getColor(R.color.new_record));
      } else {
        showVerdictChip(getString(R.string.verdict_record, best.label, beat, previous), R.color.green_soft);
      }
      return;
    }
    if (lastSolveTime != null && lastSolveTime.isDNF()) {
      showVerdictChip(getString(R.string.DNF), R.color.dnf_time);
      return;
    }
    if (isSessionExtreme(true)) {
      showVerdictChip(getString(R.string.verdict_session_best) + againstAverage(), R.color.green_soft);
      return;
    }
    if (isSessionExtreme(false)) {
      showVerdictChip(getString(R.string.verdict_session_worst) + againstAverage(), R.color.danger_text);
      return;
    }
    tvVerdictChip.setVisibility(View.INVISIBLE);
  }

  /**
   * Whether the solve just finished is the best of the session, or the worst of it. A session of
   * one or two is asked nothing: everything in it is both. Nor is a solve type timed in steps,
   * whose session is never loaded, so what it holds is only this sitting.
   */
  private boolean isSessionExtreme(boolean best) {
    if (cubeSession == null || solveType.hasSteps() || cubeSession.getTimes().size() < 3) {
      return false;
    }
    boolean blind = solveType.isBlind();
    return (best ? cubeSession.getBestTimeInd(blind) : cubeSession.getWorstTimeInd(blind)) == 0;
  }

  /** How the solve sat against the Ao12, which is the line the bars below draw. */
  private String againstAverage() {
    Long average = (solveAverages == null) ? null : solveAverages.getAvgOf12();
    if (average == null || average <= 0 || lastSolveTime == null || lastSolveTime.getTime() <= 0) {
      return "";
    }
    long delta = lastSolveTime.getTime() - average;
    String amount = FormatterService.INSTANCE.formatSolveTime(Math.abs(delta));
    return " · " + getString(
        delta < 0 ? R.string.verdict_under_average : R.string.verdict_over_average, amount);
  }

  private void showVerdictChip(CharSequence text, int colorRes) {
    int color = getResources().getColor(colorRes);
    tvVerdictChip.setText(text);
    tvVerdictChip.setTextColor(color);
    tvVerdictChip.setBackgroundTintList(ColorStateList.valueOf(washOf(color)));
    tvVerdictChip.setVisibility(View.VISIBLE);
    EnterAnimation.stagger(tvVerdictChip); // it arrives with the screen rather than appearing in it
  }

  private void highlightRecordCell(int priority) {
    if (priority < 0 || priority >= RECORD_TILE_BY_PRIORITY.length) {
      return;
    }
    View cell = findViewById(RECORD_TILE_BY_PRIORITY[priority]);
    if (cell != null) {
      cell.setBackgroundResource(R.drawable.stat_tile_record);
    }
  }

  private void clearRecordCells() {
    for (int cellId : RECORD_TILE_BY_PRIORITY) {
      View cell = findViewById(cellId);
      if (cell != null) {
        cell.setBackgroundResource(0); // back to the card's own surface
      }
    }
  }

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
        surfacePressed = true;
        applyGround();
      } else {
        return false; // to avoid receiving the ACTION_UP
      }
    } else if (parMotionEventAction == MotionEvent.ACTION_CANCEL) {
      surfacePressed = false; // taken away rather than lifted, and no stop follows it
      applyGround();
    } else if (parMotionEventAction == MotionEvent.ACTION_UP) {
      surfacePressed = false;
      applyGround();
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
    } else if (!solveType.hasInspection()) {
      if (parMotionEventAction == MotionEvent.ACTION_UP) {
        // the solve type inspects before nothing: a release starts the solve straight away
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
              setDefaultBannerText(); // still inspecting, and the ring is what says so
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
          // Read before the discard below, which sets the flag again for the refresh its own delete triggers.
          boolean showNotifications = !skipRecordPanel;
          skipRecordPanel = false;
          if (data.getSolveTime() != null) { // a plain averages refresh carries no solve; keep the one we have
            setLastSolveTime(data.getSolveTime());
            if (discardWhenSaved) { // the solve the user already chose to discard has now been saved
              discardWhenSaved = false;
              deleteLastSolve();
            }
          }
          refreshAvgFields(showNotifications, data.getSolveTime() != null);
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
