package com.cube.nanotimer.gui;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.core.content.ContextCompat;

import com.cube.nanotimer.App;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.cube.GyroReferenceListener;
import com.cube.nanotimer.cube.SmartCubeChip;
import com.cube.nanotimer.cube.SmartCubeManager;
import com.cube.nanotimer.cube.VirtualCube;
import com.cube.nanotimer.gui.widget.DrillOutcomeStrip;
import com.cube.nanotimer.gui.widget.LastLayerCaseView;
import com.cube.nanotimer.gui.widget.SmartCubeConnectDialog;
import com.cube.nanotimer.gui.widget.StepCardBackground;
import com.cube.nanotimer.drill.DrillSpec;
import com.cube.nanotimer.smartcube.model.CubeConnection;
import com.cube.nanotimer.smartcube.model.CubeConnectionListener;
import com.cube.nanotimer.smartcube.model.CubeMoveListener;
import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.CubeStateListener;
import com.cube.nanotimer.smartcube.step.LastLayerDiagram;
import com.cube.nanotimer.util.DrillRecorder;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.util.view.DrillRepParts;
import com.cube.nanotimer.util.view.DrillSplitBarView;
import com.cube.nanotimer.vo.drill.DrillEnd;

import java.util.List;

/**
 * What every drill screen is made of, whichever question it asks: a cube drawn from the connected
 * one's turns, the chip that reaches that cube, and the handful of rules a drill has to obey however
 * it is scored.
 *
 * <p>Those rules are the reason this is shared rather than copied. A drill waits for a cube rather
 * than refusing for want of one; a cube that goes away ends the drill instead of losing it; back
 * stops it rather than throwing it away, because a drill stopped at rep 6 of 20 is a result; a cube
 * with no gyroscope to follow is stood at a corner rather than square on, and the chip on the well
 * stands a cube that has one there too; and there is no control that starts or stops a rep. Two screens keeping five rules in step by hand would not.
 *
 * <p>A subclass sets its own content view and must name the shared pieces the same way, since a
 * drill screen is the same screen twice over: {@code drillRunning}, {@code drillSummary},
 * {@code tvDrillUnavailable}, {@code wvDrillCube} and {@code pbDrillCube}, and it includes
 * {@code drill_gyro_chip} on the cube's well.
 *
 * <p>The line for the rep that has just ended is shared as well: three slots, a name, a figure
 * and what the figure is made of, which every drill fills with whatever it scores. The same slots
 * mean the two screens cannot drift apart on the one rule that matters here, which is that the line
 * is about the rep that is over and never about the case on screen.
 *
 * <p>The summary's card is shared too: a hero figure, a bar or a strip under it, and two cells, lit
 * in the step colour of what was drilled as the setup screen's card is. What a drill leaves behind
 * is a handful of figures whatever it asked, so the slots live here and each drill fills them.
 */
public abstract class DrillScreenActivity extends NanoTimerActivity
    implements CubeMoveListener, CubeStateListener, CubeConnectionListener, GyroReferenceListener,
    VirtualCube.ReadyListener {

  /**
   * Whether the reps are meant to count. Carried apart from the spec, which is what a coach sends
   * and has no business knowing what the user decided about their own history.
   */
  public static final String EXTRA_RECORDING = "drillRecording";

  private static final int[] CELL_KEYS = {R.id.tvDrillCellKeyOne, R.id.tvDrillCellKeyTwo};
  private static final int[] CELL_VALUES = {R.id.tvDrillCellValueOne, R.id.tvDrillCellValueTwo};
  private static final int[] CELL_SUBS = {R.id.tvDrillCellSubOne, R.id.tvDrillCellSubTwo};

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
  /**
   * And off the corner, so two side faces show rather than one. A cube nobody is holding for the
   * screen has to say more about itself than the single face it would be pointed at. Off rather
   * than on: at the corner's own 45 the two show equally and neither reads as the front, which on
   * a drill is the face the user is being asked about.
   */
  private static final double VIEW_LONGITUDE = 30;

  /** A rep with no time to show is stood down from the layout's headline size, to at most this. */
  private static final float REP_VALUE_QUIET_RATIO = 0.7f;
  private static final float REP_VALUE_QUIET_MAX_SP = 22;

  protected VirtualCube cube;

  protected View runningLayout;
  protected View summaryLayout;
  private TextView tvUnavailable;
  private ProgressBar pbCube;
  private WebView webView;
  private SmartCubeChip smartCubeChip;
  private TextView gyroChip;
  private MenuItem straightenItem;

  /** Whether the drawn cube follows the grip. The user's standing answer, kept between drills. */
  private boolean followGyro;

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

  /** The step colour the summary is lit in, which its bar takes too. */
  private int summaryHue;
  /** Whether the summary counts finishes on the strip rather than dividing time on the bar. */
  private boolean stripShown;

  /** Deals the first case. Called once, when there is a cube to run the drill with. */
  protected abstract void startDrill();

  /** The case is on screen, which is where a rep's looking runs from. */
  protected abstract void onCaseVisible();

  /** The drill is to end where it stands, keeping the reps it has. */
  protected abstract void showSummary();

  /** Whether there is a drill still to interrupt. */
  protected abstract boolean isDrillRunning();

  /** Whether a rep has been solved; a drill stopped before one has nothing to sum up. */
  protected abstract boolean hasSolvedRep();

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

    followGyro = Options.INSTANCE.isDrillFollowGyro();
    gyroChip = findViewById(R.id.tvDrillGyro);
    gyroChip.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        followGyro = !followGyro;
        Options.INSTANCE.setDrillFollowGyro(followGyro);
        refreshGyroChip();
      }
    });
    refreshGyroChip();

    // Shown because it cannot be changed from here: a set drilled loosely must not be claimable as
    // a result at the end, so the user has to be able to see which mode they locked in.
    ((TextView) findViewById(R.id.tvDrillMode)).setText(modeName());

    // Hands stay on the cube for a whole drill, so nothing here ever touches the screen.
    runningLayout.setKeepScreenOn(true);

    // Back stops the drill rather than throwing it away: a drill ended at rep 6 of 20 is a result,
    // and the reps up to there are what the user came for. A second press then leaves. With nothing
    // solved there is no result, so it just leaves.
    getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
      @Override
      public void handleOnBackPressed() {
        if (!started || !isDrillRunning() || finished) {
          setEnabled(false);
          DrillScreenActivity.this.getOnBackPressedDispatcher().onBackPressed();
        } else if (!hasSolvedRep()) {
          finished = true;
          if (recorder != null) {
            recorder.end(DrillEnd.STOPPED);
          }
          finish();
        } else {
          endReason = DrillEnd.STOPPED;
          showSummary();
        }
      }
    });
  }

  /** The bar's arrow is a back press, so it steps out of practice and ends on the summary too. */
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == R.id.itStraighten) {
      straighten();
      return true;
    }
    if (item.getItemId() == android.R.id.home) {
      getOnBackPressedDispatcher().onBackPressed();
      return true;
    }
    return super.onOptionsItemSelected(item);
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
    cube.setGyroFollowing(followGyro);
    // No squaring: it snapped real peeks and regrips away, and the gyro drifts little enough now.
    // How it stands when it is let go of, which each drill answers for itself.
    cube.setHold(restingRotation());
    if (standsOnMat()) {
      // The timer's own camera, which is what the page's pool of shadow is drawn for.
      cube.setFloor(true);
    } else {
      // The well here is most of the screen, and at the page's own distance the cube filled about
      // a third of it. The same value the scramble dialog measured for its own box.
      cube.setCameraDistance(CUBE_CAMERA_DISTANCE);
      cube.setNudge(CUBE_NUDGE);
    }
    // A drill can be the first thing a session does, and the cube on screen only follows the
    // physical one once there is a grip to measure from. Fills an empty one; never re-takes.
    SmartCubeManager.INSTANCE.anchorGyroIfUnset();
    // Where it stands while that grip never comes, which is every cube with no gyroscope: at a
    // corner, with two side faces in view, and the page's own drag to reach the rest. A cube that
    // is followed is still drawn square on; the page holds this until that grip goes.
    cube.setView(VIEW_LATITUDE, VIEW_LONGITUDE);
    return true;
  }

  /**
   * Whether the cube stands on the screen's bare ground in a pool of shadow, as the timer's does,
   * rather than in a lit well of its own.
   */
  protected boolean standsOnMat() {
    return false;
  }

  /**
   * The rotation the cube stands at once it is no longer following the grip. Square by default; a
   * drill that writes its moves in a rotated frame stands its cube in that same frame, or the two
   * spell one turn two ways.
   */
  protected CubeOrientation restingRotation() {
    return null;
  }

  /**
   * Points the cube at the grip or lets it go, and says which on the chip.
   *
   * <p>The chip is hidden where there is no grip to let go of, which is every cube with no
   * gyroscope: that cube is already stood at its corner and turned by hand, so the toggle would
   * offer the state it is in. The drill's own scoring never reads the grip, so this changes nothing
   * but what the user is looking at.
   */
  private void refreshGyroChip() {
    if (cube != null) {
      cube.setGyroFollowing(followGyro);
    }
    gyroChip.setSelected(followGyro);
    boolean gripped = SmartCubeManager.INSTANCE.getGyroReference().isSet();
    gyroChip.setVisibility(gripped ? View.VISIBLE : View.GONE);
    if (straightenItem != null) {
      straightenItem.setVisible(gripped && followGyro && straightenUpFace() != 0);
    }
  }

  /**
   * The face the user holds on top when straightening the drawn cube, or 0 for a drill that offers
   * no straightening: the cross drill is turned every which way, so no hold can be declared.
   */
  protected char straightenUpFace() {
    return 0;
  }

  /**
   * Takes the grip again from a declared hold, for a gyro that has drifted: nothing in the cube's
   * data says which way it faces, so the user holds it a known way, green in front.
   */
  private void straighten() {
    char up = straightenUpFace();
    char front = up == 'F' || up == 'B' ? 'U' : 'F';
    CubeRotation grip = CubeRotation.holding(up, front);
    if (grip == null) {
      return;
    }
    final String upName = getString(Utils.getFaceColourNameRes(up));
    DialogUtils.showInfoMessage(this, getString(R.string.drill_straighten_hold, upName,
        getString(Utils.getFaceColourNameRes(front))));
    SmartCubeManager.INSTANCE.straightenTo(grip, held -> {
      if (!held && !isFinishing()) {
        DialogUtils.showInfoMessage(this, getString(R.string.drill_straighten_wrong, upName));
      }
    });
  }

  /** A grip taken or lost is what decides whether the chip has anything to offer. */
  @Override
  public void onGyroReferenceChanged() {
    runOnUiThread(this::refreshGyroChip);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.drill_menu, menu);
    straightenItem = menu.findItem(R.id.itStraighten);
    refreshGyroChip();
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
    SmartCubeManager.INSTANCE.addStateListener(this);
    SmartCubeManager.INSTANCE.addGyroReferenceListener(this);
    SmartCubeManager.INSTANCE.addConnectionListener(this); // replays the connection at once
    // The grip can have been taken or lost while the screen was away, and nothing told this then.
    refreshGyroChip();
  }

  @Override
  protected void onPause() {
    super.onPause();
    smartCubeChip.stop();
    SmartCubeManager.INSTANCE.removeMoveListener(this);
    SmartCubeManager.INSTANCE.removeStateListener(this);
    SmartCubeManager.INSTANCE.removeGyroReferenceListener(this);
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

  /**
   * The state the cube says it is really in, which is the truth wherever its turns disagree. A
   * drill that draws from the turns alone is one dropped notification away from a cube nobody can
   * solve, and a QiYi drops one whenever a slice is turned fast. Left to each drill, since what
   * has to be redrawn is the drill's own business.
   */
  @Override
  public void onState(CubeState state) {
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

  /** The layout's size for the rep's figure, read once before anything changes it. */
  private float repValuePx = -1;

  /** What the line about the rep before the one being worked is worth: readable, not current. */
  private static final float PAST_REP_ALPHA = 0.45f;
  private static final long PAST_REP_FADE_MS = 200;

  /**
   * The rep that has just ended, with the figure in whichever ink the drill scores it in.
   *
   * @param name what it was, or null where the drill deals nothing with a name
   * @param nameColorRes the family colour that name carries everywhere else, or the verdict's
   * @param value the figure it is scored on, set as the thing the eye lands on
   * @param valueColorRes the ink for that figure
   * @param quiet true for a rep with no figure, which is said rather than announced
   * @param sub what the figure is made of, or null
   */
  protected void setLastRep(CharSequence name, int nameColorRes, CharSequence value,
      int valueColorRes, boolean quiet, CharSequence sub) {
    findViewById(R.id.llDrillLastRep).setAlpha(1f);
    TextView tvName = findViewById(R.id.tvDrillLastRepName);
    tvName.setText(name);
    tvName.setVisibility(name == null ? View.GONE : View.VISIBLE);
    if (name != null) {
      int colour = ContextCompat.getColor(this, nameColorRes);
      tvName.setTextColor(colour);
      // For a drill that puts a pill behind the name; on one whose name is bare text this tints
      // nothing, which is how the case drill's own families keep looking like plain labels.
      tvName.setBackgroundTintList(ColorStateList.valueOf(colour));
    }
    TextView tvValue = findViewById(R.id.tvDrillLastRep);
    tvValue.setText(value);
    if (repValuePx < 0) {
      repValuePx = tvValue.getTextSize(); // each screen sets its headline size in its layout
    }
    float quietPx = Math.min(repValuePx * REP_VALUE_QUIET_RATIO, TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, REP_VALUE_QUIET_MAX_SP, getResources().getDisplayMetrics()));
    tvValue.setTextSize(TypedValue.COMPLEX_UNIT_PX, quiet ? quietPx : repValuePx);
    tvValue.setTextColor(ContextCompat.getColor(this, valueColorRes));
    TextView tvSub = findViewById(R.id.tvDrillLastRepSplit);
    tvSub.setText(sub);
    tvSub.setVisibility(sub == null ? View.GONE : View.VISIBLE);
  }

  /** The same, for a drill that scores every rep in the one ink. */
  protected void setLastRep(CharSequence name, int nameColorRes, CharSequence value, boolean quiet,
      CharSequence sub) {
    setLastRep(name, nameColorRes, value, quiet ? R.color.secondary_text : R.color.white, quiet,
        sub);
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

  /** One of the card's two cells: what the figure is, the figure, and what it is of. */
  protected void setSummaryCell(int index, CharSequence key, CharSequence value, CharSequence sub) {
    findViewById(R.id.llDrillSummaryCells).setVisibility(View.VISIBLE);
    ((TextView) findViewById(CELL_KEYS[index])).setText(key);
    TextView tvValue = findViewById(CELL_VALUES[index]);
    tvValue.setText(value);
    tvValue.setTextColor(ContextCompat.getColor(this, R.color.white));
    TextView tvSub = findViewById(CELL_SUBS[index]);
    tvSub.setText(sub);
    tvSub.setVisibility(sub == null || sub.length() == 0 ? View.GONE : View.VISIBLE);
  }

  /** A cell's figure in a colour of its own, for the one the table below colours the same way. */
  protected void setSummaryCellColor(int index, int color) {
    ((TextView) findViewById(CELL_VALUES[index])).setTextColor(color);
  }

  /**
   * Which case the second cell's figure was: its chart and short name, or only a caption.
   *
   * @param chartCode the case to draw, or null for a figure with no picture
   */
  protected void setSummaryCellCase(String chartCode, CharSequence caption) {
    findViewById(R.id.llDrillCellCaseTwo).setVisibility(View.VISIBLE);
    LastLayerCaseView chart = findViewById(R.id.vDrillCellChartTwo);
    if (chartCode == null) {
      chart.setVisibility(View.GONE);
    } else {
      chart.setVisibility(View.VISIBLE);
      chart.setDiagram(LastLayerDiagram.forCase(chartCode));
    }
    ((TextView) findViewById(R.id.tvDrillCellCaseTwo)).setText(caption);
  }

  /** A summary with no cells, whose figures all sit in the hero and the bar. */
  protected void hideSummaryCells() {
    findViewById(R.id.llDrillSummaryCells).setVisibility(View.GONE);
  }

  /**
   * The one figure the drill is read by.
   *
   * @param of what a count is out of, or null for a figure that is not a count
   * @param unit what the figure is
   */
  protected void setSummaryHero(CharSequence value, CharSequence of, CharSequence unit) {
    ((TextView) findViewById(R.id.tvDrillSummaryHero)).setText(value);
    TextView tvOf = findViewById(R.id.tvDrillSummaryHeroOf);
    tvOf.setText(of);
    tvOf.setVisibility(of == null ? View.GONE : View.VISIBLE);
    ((TextView) findViewById(R.id.tvDrillSummaryHeroUnit)).setText(unit);
  }

  /** What a coach drill's ending has to say in words, or null for nothing. */
  protected void setSummaryNote(CharSequence note) {
    TextView tvNote = findViewById(R.id.tvDrillSummaryNote);
    tvNote.setText(note);
    tvNote.setVisibility(note == null || note.length() == 0 ? View.GONE : View.VISIBLE);
  }

  /** The line over the hero, which by default only says the drill is over. */
  protected void setSummaryEyebrow(CharSequence text) {
    ((TextView) findViewById(R.id.tvDrillSummaryEyebrow)).setText(text);
  }

  /**
   * Where the drill's time went, drawn as the drill screen draws a rep, with every part named. Two
   * parts are named at the bar's two ends; more are named in one line under it.
   *
   * @param parts the mean rep's parts, from {@link DrillRepParts}
   * @param meanMs the whole mean, for a summary whose hero is not already it, or -1
   */
  protected void setSummarySplit(List<DrillRepParts.Part> parts, long meanMs) {
    DrillRepParts.draw((DrillSplitBarView) findViewById(R.id.vDrillSummarySplit), parts);
    boolean ends = parts.size() == 2;
    TextView first = findViewById(R.id.tvDrillSummaryRecognition);
    first.setText(DrillRepParts.words(this, ends ? parts.subList(0, 1) : parts));
    first.setGravity(ends ? Gravity.START : Gravity.CENTER_HORIZONTAL);
    TextView last = findViewById(R.id.tvDrillSummaryExecution);
    last.setVisibility(ends ? View.VISIBLE : View.GONE);
    if (ends) {
      last.setText(DrillRepParts.words(this, parts.subList(1, 2)));
    }
    TextView mean = findViewById(R.id.tvDrillSummaryMean);
    mean.setVisibility(meanMs < 0 ? View.GONE : View.VISIBLE);
    if (meanMs >= 0) {
      mean.setText(legend(getString(R.string.drill_summary_cell_average), meanMs));
    }
  }

  /**
   * The cross drill's reps by how each finished, in place of the bar, with a key naming the
   * finishes that occur.
   *
   * @param outcomes one {@link DrillOutcomeStrip} outcome per rep, in the order dealt
   * @param names what each outcome is called, indexed by outcome; null for one with no key
   */
  protected void setSummaryStrip(int[] outcomes, CharSequence[] names) {
    stripShown = true;
    findViewById(R.id.llDrillSummarySplit).setVisibility(View.GONE);
    findViewById(R.id.llDrillSummaryStrip).setVisibility(View.VISIBLE);
    DrillOutcomeStrip strip = findViewById(R.id.vDrillSummaryStrip);
    strip.setOutcomes(outcomes);
    LinearLayout key = findViewById(R.id.llDrillSummaryStripKey);
    key.removeAllViews();
    boolean[] seen = new boolean[names.length];
    for (int outcome : outcomes) {
      seen[outcome] = true;
    }
    LayoutInflater inflater = LayoutInflater.from(this);
    for (int outcome = 0; outcome < names.length; outcome++) {
      if (!seen[outcome] || names[outcome] == null) {
        continue;
      }
      TextView item = (TextView) inflater.inflate(R.layout.drill_summary_key, key, false);
      item.setText(names[outcome]);
      item.setCompoundDrawablesRelativeWithIntrinsicBounds(
          keySwatch(strip.colorOf(outcome), outcome == DrillOutcomeStrip.MISSED), null, null, null);
      key.addView(item);
    }
  }

  /**
   * Puts the figures back, for a summary whose reps changed after it was drawn. A rep thrown out
   * can be put back, so nothing {@link #showSummaryEmpty} does may be one way only.
   */
  protected void showSummaryFigures() {
    findViewById(R.id.llDrillSummaryHero).setVisibility(View.VISIBLE);
    findViewById(R.id.llDrillSummarySplit).setVisibility(stripShown ? View.GONE : View.VISIBLE);
    findViewById(R.id.llDrillSummaryStrip).setVisibility(stripShown ? View.VISIBLE : View.GONE);
    findViewById(R.id.llDrillCellTwo).setVisibility(View.VISIBLE);
    findViewById(R.id.tvDrillSummaryEmpty).setVisibility(View.GONE);
  }

  /**
   * A drill with nothing to average. The first cell stays: how far it got is still the result,
   * and it is the one figure a drill that timed nothing still has.
   */
  protected void showSummaryEmpty(int messageId) {
    findViewById(R.id.llDrillSummaryHero).setVisibility(View.GONE);
    findViewById(R.id.llDrillSummarySplit).setVisibility(View.GONE);
    findViewById(R.id.llDrillSummaryStrip).setVisibility(View.GONE);
    findViewById(R.id.llDrillCellTwo).setVisibility(View.INVISIBLE);
    TextView empty = findViewById(R.id.tvDrillSummaryEmpty);
    empty.setVisibility(View.VISIBLE);
    empty.setText(messageId);
  }

  /**
   * Turns the screen over to the summary, lit in the drill's step colour, and closes the recording,
   * this being the one point every ending passes through. A drill that never reaches it was killed
   * rather than ended, and is stored with no ending at all.
   *
   * @param hue the step colour of what was drilled
   */
  protected void showSummaryFor(int hue) {
    if (recorder != null) {
      recorder.end(endReason);
    }
    summaryHue = hue;
    runningLayout.setVisibility(View.GONE);
    summaryLayout.setVisibility(View.VISIBLE);
    StepCardBackground.paint(findViewById(R.id.llDrillSummaryCard), hue);
    TextView eyebrow = findViewById(R.id.tvDrillSummaryEyebrow);
    eyebrow.setTextColor(hue);
    eyebrow.setText(R.string.drill_finished);
    ((TextView) findViewById(R.id.tvDrillSummaryMode)).setText(modeName());
  }

  /** "Drill finished" and how many reps it ran, for a summary with no cell to count them in. */
  protected CharSequence finishedAfter(int reps) {
    return getString(R.string.drill_summary_subtitle, getString(R.string.drill_finished),
        getResources().getQuantityString(R.plurals.drill_summary_reps, reps, reps));
  }

  /** A half of the mean as the legend says it: its name, then the figure in white. */
  private CharSequence legend(String name, long ms) {
    SpannableStringBuilder text = new SpannableStringBuilder(name).append(' ');
    int start = text.length();
    text.append(FormatterService.INSTANCE.formatSolveTime(ms));
    text.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.white)), start,
        text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    return text;
  }

  /** The key's mark for one finish: a filled chip, or a dashed one for a cross never built. */
  private GradientDrawable keySwatch(int color, boolean dashed) {
    float density = getResources().getDisplayMetrics().density;
    GradientDrawable swatch = new GradientDrawable();
    swatch.setCornerRadius(2 * density);
    swatch.setSize(Math.round(10 * density), Math.round(6 * density));
    if (dashed) {
      swatch.setStroke(Math.round(density), color, 2 * density, 2 * density);
    } else {
      swatch.setColor(color);
    }
    return swatch;
  }

  private int modeName() {
    return isRecording() ? R.string.drill_summary_recorded : R.string.drill_summary_not_recorded;
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
