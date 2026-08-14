package com.cube.nanotimer.gui;

import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.core.content.ContextCompat;

import com.cube.nanotimer.App;
import com.cube.nanotimer.R;
import com.cube.nanotimer.cube.SmartCubeChip;
import com.cube.nanotimer.cube.SmartCubeManager;
import com.cube.nanotimer.cube.VirtualCube;
import com.cube.nanotimer.gui.widget.SmartCubeConnectDialog;
import com.cube.nanotimer.smartcube.drill.DrillSpec;
import com.cube.nanotimer.smartcube.model.CubeConnection;
import com.cube.nanotimer.smartcube.model.CubeConnectionListener;
import com.cube.nanotimer.smartcube.model.CubeMoveListener;
import com.cube.nanotimer.util.DrillRecorder;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.vo.drill.DrillEnd;

/**
 * What every drill screen is made of, whichever question it asks: a cube drawn from the connected
 * one's turns, the chip that reaches that cube, and the handful of rules a drill has to obey however
 * it is scored.
 *
 * <p>Those rules are the reason this is shared rather than copied. A drill waits for a cube rather
 * than refusing for want of one; a cube that goes away ends the drill instead of losing it; back
 * stops it rather than throwing it away, because a drill stopped at rep 6 of 20 is a result; a cube
 * with no gyroscope to follow is stood at a corner rather than square on; and there is no control
 * that starts or stops a rep. Two screens keeping five rules in step by hand would not.
 *
 * <p>A subclass sets its own content view and must name the shared pieces the same way, since a
 * drill screen is the same screen twice over: {@code drillRunning}, {@code drillSummary},
 * {@code tvDrillUnavailable}, {@code wvDrillCube} and {@code pbDrillCube}.
 *
 * <p>The line for the rep that has just ended is shared as well: three slots, a name, a figure
 * and what the figure is made of, which every drill fills with whatever it scores. The same slots
 * mean the two screens cannot drift apart on the one rule that matters here, which is that the line
 * is about the rep that is over and never about the case on screen.
 *
 * <p>The summary sheet is shared too, down to its three cells: what a drill leaves behind is a
 * handful of figures whatever it asked, so the slots live here and each drill says what goes in
 * them.
 */
public abstract class DrillScreenActivity extends NanoTimerActivity
    implements CubeMoveListener, CubeConnectionListener,
    VirtualCube.ReadyListener {

  /**
   * Whether the reps are meant to count. Carried apart from the spec, which is what a coach sends
   * and has no business knowing what the user decided about their own history.
   */
  public static final String EXTRA_RECORDING = "drillRecording";

  private static final int[] CELL_ROWS =
      {R.id.llDrillCellOne, R.id.llDrillCellTwo, R.id.llDrillCellThree};
  private static final int[] CELL_KEYS =
      {R.id.tvDrillCellKeyOne, R.id.tvDrillCellKeyTwo, R.id.tvDrillCellKeyThree};
  private static final int[] CELL_VALUES =
      {R.id.tvDrillCellValueOne, R.id.tvDrillCellValueTwo, R.id.tvDrillCellValueThree};
  private static final int[] CELL_SUBS =
      {R.id.tvDrillCellSubOne, R.id.tvDrillCellSubTwo, R.id.tvDrillCellSubThree};

  /** How far back the camera stands over a well that takes most of the screen. */
  private static final double CUBE_CAMERA_DISTANCE = 5.2;

  /**
   * And how far below the middle of that well it is drawn: not at all. The page's own default is a
   * percentage measured in the timer's box, and this well is more than twice as tall, which turned
   * a cube sitting a shade low into one sitting 30dp low with its foot almost on the bottom edge.
   */
  private static final double CUBE_NUDGE = 0;

  /** How far above the cube the camera stands where there is no grip to follow. The mirror's own
   * tilt: far enough down to see the top face, and no further. */
  private static final double VIEW_LATITUDE = 26;
  /** And at a corner, so two side faces show rather than one. A cube nobody is holding for the
   * screen has to say more about itself than the single face it would be pointed at. */
  private static final double VIEW_LONGITUDE = 45;

  /** The rep line at headline size, and stood down for a rep with no time to show. */
  private static final float REP_VALUE_SP = 24;
  private static final float REP_VALUE_QUIET_SP = 17;

  protected VirtualCube cube;

  protected View runningLayout;
  protected View summaryLayout;
  private TextView tvUnavailable;
  private ProgressBar pbCube;
  private WebView webView;
  private SmartCubeChip smartCubeChip;

  /** The page has drawn the case, which is when a turn can be counted against it. */
  protected boolean cubeReady;
  /** The drill is over, by its last rep or by the cube going away, and takes no more turns. */
  protected boolean finished;

  /** The first case has been dealt. Once only, however often a cube comes and goes after it. */
  private boolean started;
  /** Held at the door for want of a cube, which is not the same as having failed for good. */
  private boolean awaitingCube;

  /** Where a recording drill's reps go, and a drain for a casual one's. */
  protected DrillRecorder recorder;
  /**
   * How this drill is going to have stopped. Set where the drill is cut short, since by the time
   * the summary is drawn the three endings look alike, and a drill whose cube died at rep 6 is not
   * the same result as one the user walked out of at rep 6.
   */
  private DrillEnd endReason = DrillEnd.FINISHED;

  /** Deals the first case. Called once, when there is a cube to run the drill with. */
  protected abstract void startDrill();

  /** The case is on screen, which is where a rep's looking runs from. */
  protected abstract void onCaseVisible();

  /** The drill is to end where it stands, keeping the reps it has. */
  protected abstract void showSummary();

  /** Whether there is a drill still to interrupt. */
  protected abstract boolean isDrillRunning();

  /**
   * Starts keeping the reps, for a drill the user chose to record. Call once the spec has been
   * read: a spec that could not be is a drill that never ran, and records nothing.
   */
  protected void initRecording(DrillSpec spec) {
    recorder = new DrillRecorder(App.INSTANCE.getService(), spec, isRecording());
  }

  /** Whether the reps count. Fixed before the drill and not changeable from here. */
  protected boolean isRecording() {
    return getIntent().getBooleanExtra(EXTRA_RECORDING, true);
  }

  /** Wires the shared pieces. Call from {@code onCreate}, after {@code setContentView}. */
  protected void bindDrillScreen() {
    smartCubeChip = new SmartCubeChip(this, this::openSmartCubeConnect);

    runningLayout = findViewById(R.id.drillRunning);
    summaryLayout = findViewById(R.id.drillSummary);
    tvUnavailable = findViewById(R.id.tvDrillUnavailable);
    pbCube = findViewById(R.id.pbDrillCube);

    // Shown because it cannot be changed from here: a set drilled loosely must not be claimable as
    // a result at the end, so the user has to be able to see which mode they locked in.
    ((TextView) findViewById(R.id.tvDrillMode)).setText(modeName());

    // Hands stay on the cube for a whole drill, so nothing here ever touches the screen.
    runningLayout.setKeepScreenOn(true);

    // Back stops the drill rather than throwing it away: a drill ended at rep 6 of 20 is a result,
    // and the reps up to there are what the user came for. A second press then leaves.
    getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
      @Override
      public void handleOnBackPressed() {
        if (!started || !isDrillRunning() || finished) {
          setEnabled(false);
          DrillScreenActivity.this.getOnBackPressedDispatcher().onBackPressed();
        } else {
          endReason = DrillEnd.STOPPED;
          showSummary();
        }
      }
    });
  }

  /**
   * Starts the drill, or waits at the door and starts it the moment a cube connects.
   *
   * <p>Waiting rather than refusing, because the chip that connects a cube is in this screen's own
   * bar: connecting from here is the ordinary way in, and a one-shot check at the door made it the
   * one way that could not work. The drill was turned down, the cube arrived a second later, and
   * nothing on the screen noticed until it was left and opened again.
   */
  protected void startWhenCubeConnected() {
    if (started) {
      return;
    }
    if (!SmartCubeManager.INSTANCE.isConnected()) {
      awaitingCube = true;
      showUnavailable(getString(R.string.drill_needs_cube));
      return;
    }
    // A cube connected from here leaves the connect sheet standing over the drill, and the first
    // case would be dealt behind it and its recognition timed while nobody could see it. Only a
    // drill that was waiting has to check: one opened with a cube already there has no sheet, and
    // has no window focus yet either.
    if (awaitingCube && !hasWindowFocus()) {
      return;
    }
    awaitingCube = false;
    hideUnavailable();
    if (!createCube()) {
      return; // no cube to draw on, and back leaves rather than summing up a drill never dealt
    }
    started = true;
    startDrill();
  }

  /** The connect sheet is gone, so a drill that was waiting behind it can deal its first case. */
  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus && awaitingCube) {
      startWhenCubeConnected();
    }
  }

  /**
   * Builds the drawn cube and points it at the user's grip.
   *
   * @return false when this device cannot draw one, having already said so in place of the drill
   */
  private boolean createCube() {
    webView = findViewById(R.id.wvDrillCube);
    try {
      // Null touch listener: a press on the cube does nothing here. This is not the timer, where a
      // dead zone would leave a solve unstarted.
      cube = new VirtualCube(webView, null, this);
    } catch (Throwable t) {
      // e.g. no WebView on this device. A drill cannot go on without a cube to look at, since the
      // cube is the whole of what it shows.
      showUnavailable(getString(R.string.drill_no_cube_view));
      return false;
    }
    cube.setGyroFollowing(true);
    // The well here is most of the screen, and at the page's own distance the cube filled about a
    // third of it. The same value the scramble dialog measured for its own box.
    cube.setCameraDistance(CUBE_CAMERA_DISTANCE);
    cube.setNudge(CUBE_NUDGE);
    // A drill can be the first thing a session does, and the cube on screen only follows the
    // physical one once there is a grip to measure from. Fills an empty one; never re-takes.
    SmartCubeManager.INSTANCE.anchorGyroIfUnset();
    // Where it stands while that grip never comes, which is every cube with no gyroscope: at a
    // corner, with two side faces in view, and the page's own drag to reach the rest. A cube that
    // is followed is still drawn square on; the page holds this until that grip goes.
    cube.setView(VIEW_LATITUDE, VIEW_LONGITUDE);
    return true;
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.drill_menu, menu);
    MenuItem item = menu.findItem(R.id.itSmartCube);
    smartCubeChip.bind(item != null ? item.getActionView() : null);
    return super.onCreateOptionsMenu(menu);
  }

  private void openSmartCubeConnect() {
    DialogUtils.showFragment(this, new SmartCubeConnectDialog());
  }

  @Override
  protected void onResume() {
    super.onResume();
    smartCubeChip.start();
    if (cube != null) {
      cube.onResume();
    }
    // Subscribed whether or not there is a cube yet: a drill held at the door has no cube of its
    // own, and this is what tells it one has arrived.
    SmartCubeManager.INSTANCE.addMoveListener(this);
    SmartCubeManager.INSTANCE.addConnectionListener(this); // replays the connection at once
  }

  @Override
  protected void onPause() {
    super.onPause();
    smartCubeChip.stop();
    SmartCubeManager.INSTANCE.removeMoveListener(this);
    SmartCubeManager.INSTANCE.removeConnectionListener(this);
    if (cube != null) {
      cube.onPause();
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (cube != null) {
      cube.destroy();
    }
  }

  @Override
  public void onCubeDrawn() {
    pbCube.setVisibility(View.GONE);
    webView.setVisibility(View.VISIBLE);
    cubeReady = true;
    onCaseVisible();
  }

  /** A drill stopped at rep 6 of 20 is a result, so a cube that goes away ends it rather than
   * losing it. One that never started waits instead, since it has no reps to end with. */
  @Override
  public void onConnection(CubeConnection connection) {
    if (awaitingCube) {
      startWhenCubeConnected();
    } else if (started && isDrillRunning() && !SmartCubeManager.INSTANCE.isConnected()) {
      endReason = DrillEnd.CUBE_LOST;
      showSummary();
    }
  }

  /** What the line about the rep before the one being worked is worth: readable, not current. */
  private static final float PAST_REP_ALPHA = 0.45f;
  private static final long PAST_REP_FADE_MS = 200;

  /**
   * The rep that has just ended.
   *
   * @param name what it was, or null where the drill deals nothing with a name
   * @param nameColorRes the family colour that name carries everywhere else
   * @param value the figure it is scored on, set as the thing the eye lands on
   * @param quiet true for a rep with no figure, which is said rather than announced
   * @param sub what the figure is made of, or null
   */
  protected void setLastRep(CharSequence name, int nameColorRes, CharSequence value, boolean quiet,
      CharSequence sub) {
    findViewById(R.id.llDrillLastRep).setAlpha(1f);
    TextView tvName = findViewById(R.id.tvDrillLastRepName);
    tvName.setText(name);
    tvName.setVisibility(name == null ? View.GONE : View.VISIBLE);
    if (name != null) {
      tvName.setTextColor(ContextCompat.getColor(this, nameColorRes));
    }
    TextView tvValue = findViewById(R.id.tvDrillLastRep);
    tvValue.setText(value);
    tvValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, quiet ? REP_VALUE_QUIET_SP : REP_VALUE_SP);
    tvValue.setTextColor(ContextCompat.getColor(this,
        quiet ? R.color.secondary_text : R.color.white));
    TextView tvSub = findViewById(R.id.tvDrillLastRepSplit);
    tvSub.setText(sub);
    tvSub.setVisibility(sub == null ? View.GONE : View.VISIBLE);
  }

  /** Nothing to say about a rep that is over, because the one being worked is not it. */
  protected void clearLastRep() {
    setLastRep(null, 0, null, false, null);
  }

  /**
   * Leaves the finished rep's line up once its successor has been dealt, standing back.
   *
   * <p>It stays because a line that went with its own case was on screen for the length of the beat
   * and no longer, which is not long enough to read a name and two figures. It stands back so that
   * a name beside a time still reads as the rep that is over rather than as the name of the case
   * now in front of the user, which this screen never gives away.
   */
  protected void recedeLastRep() {
    findViewById(R.id.llDrillLastRep).animate().alpha(PAST_REP_ALPHA)
        .setDuration(PAST_REP_FADE_MS).start();
  }

  /** Where the drill has got to, in words and as the meter under them. */
  protected void setProgress(int rep, int total) {
    ((TextView) findViewById(R.id.tvDrillProgress))
        .setText(getString(R.string.drill_progress, rep, total));
    ProgressBar meter = findViewById(R.id.pbDrillProgress);
    meter.setMax(Math.max(1, total));
    meter.setProgress(rep);
  }

  /** One of the summary's three cells: what the figure is, the figure, and what it is of. */
  protected void setSummaryCell(int index, CharSequence key, CharSequence value, CharSequence sub) {
    ((TextView) findViewById(CELL_KEYS[index])).setText(key);
    ((TextView) findViewById(CELL_VALUES[index])).setText(value);
    ((TextView) findViewById(CELL_SUBS[index])).setText(sub);
  }

  /**
   * The figure with no cell of its own, under them. A screen whose figures all have a cell has no
   * such line, and does not have to grow an empty one to be a drill screen.
   */
  protected void setSummaryExtra(CharSequence text) {
    TextView extra = findViewById(R.id.tvDrillSummaryExtra);
    if (extra != null) {
      extra.setText(text);
    }
  }

  /**
   * Puts the figure cells back, for a summary whose reps changed after it was drawn. A rep thrown
   * out can be put back, so nothing {@link #showSummaryEmpty} does may be one way only.
   */
  protected void showSummaryFigures() {
    findViewById(CELL_ROWS[1]).setVisibility(View.VISIBLE);
    findViewById(CELL_ROWS[2]).setVisibility(View.VISIBLE);
    View extra = findViewById(R.id.tvDrillSummaryExtra);
    if (extra != null) {
      extra.setVisibility(View.VISIBLE);
    }
    findViewById(R.id.tvDrillSummaryEmpty).setVisibility(View.GONE);
  }

  /**
   * A drill with nothing to average. The reps cell stays: how far it got is still the result, and
   * it is the one figure a drill that timed nothing still has.
   */
  protected void showSummaryEmpty(int messageId) {
    findViewById(CELL_ROWS[1]).setVisibility(View.GONE);
    findViewById(CELL_ROWS[2]).setVisibility(View.GONE);
    View extra = findViewById(R.id.tvDrillSummaryExtra);
    if (extra != null) {
      extra.setVisibility(View.GONE);
    }
    TextView empty = findViewById(R.id.tvDrillSummaryEmpty);
    empty.setVisibility(View.VISIBLE);
    empty.setText(messageId);
  }

  /**
   * What was drilled and in which mode, since the drill is over and its own bar is gone. Also where
   * the recording is closed off, this being the one point every ending passes through. A drill that
   * never reaches it was killed rather than ended, and is stored with no ending at all.
   */
  protected void showSummaryFor(String label) {
    if (recorder != null) {
      recorder.end(endReason);
    }
    runningLayout.setVisibility(View.GONE);
    summaryLayout.setVisibility(View.VISIBLE);
    ((TextView) findViewById(R.id.tvDrillSummarySubtitle)).setText(
        getString(R.string.drill_summary_subtitle, label, getString(modeName())));
  }

  private int modeName() {
    return isRecording() ? R.string.drill_mode_recording : R.string.drill_mode_casual;
  }

  /** Nothing to drill with. Said in place of the drill rather than over it. */
  protected void showUnavailable(String message) {
    runningLayout.setVisibility(View.GONE);
    tvUnavailable.setVisibility(View.VISIBLE);
    tvUnavailable.setText(message);
  }

  private void hideUnavailable() {
    tvUnavailable.setVisibility(View.GONE);
    runningLayout.setVisibility(View.VISIBLE);
  }
}
