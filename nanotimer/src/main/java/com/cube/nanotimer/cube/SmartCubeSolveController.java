package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.model.CubeConnection;
import com.cube.nanotimer.smartcube.model.CubeConnectionListener;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeMoveListener;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.CubeStateListener;
import com.cube.nanotimer.smartcube.step.SolveAnalyzer;
import com.cube.nanotimer.smartcube.step.StepTime;
import com.cube.nanotimer.vo.CubeMethod;
import java.util.Collections;
import java.util.List;

/**
 * Owns the whole cube-driven solve lifecycle, isolated from the timer screen:
 *
 * <pre>
 *   INACTIVE / NEEDS_SOLVE / FOLLOWING --(scramble complete)--> ARMED
 *   ARMED --(first solve move)--> onCubeAutoStart() --> RUNNING
 *   RUNNING --(cube solved)--> onCubeAutoStop()
 * </pre>
 *
 * The timer screen only sets the scramble, forwards timer start/stop, and re-renders the
 * scramble from the getters whenever {@link Listener#onScrambleFollowChanged()} fires. Tap
 * start/stop keeps working at every point.
 */
public class SmartCubeSolveController implements CubeStateListener, CubeMoveListener {

  /** What the timer screen should show for the scramble. */
  public enum FollowMode { INACTIVE, NEEDS_SOLVE, FOLLOWING, SOLVING }

  public interface Listener {
    void onCubeAutoStart();

    void onCubeAutoStop();

    void onScrambleFollowChanged();
  }

  private enum Phase { INACTIVE, NEEDS_SOLVE, FOLLOWING, ARMED, RUNNING }

  /** A follow pause longer than this means the cube was set down, not a slow scramble. */
  private static final long FOLLOW_RESUME_GAP_MS = 60_000;

  /** What the solve was expected to be: nothing, until solve types carry a method of their own. */
  private static final CubeMethod EXPECTED_METHOD = null;

  private final Listener listener;
  private final CubeConnectionListener connectionListener = this::onConnection;
  private MethodAnalyzers analyzers = new MethodAnalyzers(false);
  private final RotationTracker rotationTracker = new RotationTracker();
  private final SliceSpinDetector sliceSpins = new SliceSpinDetector();

  private CubeConnection connection;
  private List<StepTime> stepTimes = Collections.emptyList();
  private CubeMethod method; // which one the solve just finished fitted, null for none
  private Integer stoppedStep;
  private String solveMoves = "";
  private String[] scramble;
  private boolean cubeDriven; // auto-stop applies (3x3 + connected)
  private boolean followable; // scramble-follow + auto-start apply (3x3 default scramble)
  private boolean blind; // the solve type memorises first: neither end of the solve is the cube's
  private ScrambleFollower follower;
  private Phase phase = Phase.INACTIVE;
  private boolean sawUnsolved;
  private boolean analyzing;
  private long lastFollowMoveWallMs; // 0 until the first followed move of the current scramble
  private long timerStartMs; // when the tap started the solve, on the cube's (host-fitted) clock

  public SmartCubeSolveController(Listener listener) {
    this.listener = listener;
  }

  public void start() {
    SmartCubeManager.INSTANCE.addStateListener(this);
    SmartCubeManager.INSTANCE.addMoveListener(this);
    SmartCubeManager.INSTANCE.addConnectionListener(connectionListener);
  }

  public void stop() {
    SmartCubeManager.INSTANCE.removeStateListener(this);
    SmartCubeManager.INSTANCE.removeMoveListener(this);
    SmartCubeManager.INSTANCE.removeConnectionListener(connectionListener);
  }

  /**
   * @param cubeDriven true when the cube may auto-stop this solve (3x3, connected)
   * @param followable true when the scramble can be followed + auto-started (3x3 full scramble)
   * @param blind true when the solve type is a blindfolded one, which turns both automatic ends of
   *     the solve off (see {@link #onTimerStarted()})
   */
  public void setScramble(String[] scramble, boolean cubeDriven, boolean followable, boolean blind) {
    this.scramble = scramble;
    this.cubeDriven = cubeDriven;
    this.followable = followable;
    if (blind != this.blind) {
      this.blind = blind;
      analyzers = new MethodAnalyzers(blind); // a different solve type is read by different detectors
    }
    applyScramble();
  }

  /**
   * A blind solve is timed by the taps at both ends and by nothing else.
   *
   * <p><b>Not auto-started</b>, because the tap opens the memorisation and the first move is the
   * end of it, not the beginning of the solve — starting there would swallow the memo whole.
   *
   * <p><b>Not auto-stopped</b>, because the solver cannot see that the cube came out solved. They
   * may well turn on past it, thinking an orientation is still out; and the time that counts is the
   * one they stop, after the blindfold is off. Stopping at the solved state would quietly record a
   * time nobody achieved.
   */
  public void onTimerStarted() {
    timerStartMs = System.currentTimeMillis(); // the cube's stamps are fitted to this same clock
    if (cubeDriven && SmartCubeManager.INSTANCE.isConnected()) {
      phase = Phase.RUNNING;
      sawUnsolved = false;
      if (blind) {
        beginAnalysis(timerStartMs); // the memo starts here, with the cube untouched
      }
      notifyChanged(); // swap the scramble for the "solving" state
    } else {
      phase = Phase.INACTIVE;
    }
  }

  public void onTimerStopped() {
    // A solve the cube drove has a breakdown as far as its milestones went, whether or not it
    // reached solved — a botched PLL is exactly the solve worth looking at. What still earns none is
    // a method the milestones never fitted, or a prefix too short to tell the methods apart.
    method = analyzing ? analyzers.resolve(EXPECTED_METHOD) : null;
    SolveAnalyzer analyzer = method == null ? null : analyzers.get(method);
    stepTimes = analyzer == null ? Collections.<StepTime>emptyList() : analyzer.getStepTimes();
    stoppedStep = analyzer == null ? null : analyzer.getStoppedStep();
    // The moves need no method: an unrecognised solve still has a solution worth keeping.
    solveMoves = analyzing
        ? SolveMovesFormat.format(analyzers.moves().getMoves(),
            rotationTracker.getRotations(
                sliceSpins.coreSpins(SmartCubeManager.INSTANCE::getOrientationAt)),
            analyzers.moves().getSolveStartMs())
        : "";
    analyzing = false;
    phase = Phase.INACTIVE; // the next setScramble (after a new scramble) re-activates follow
  }

  /** The method the solve just finished was solved with, null when its milestones fitted none. */
  public CubeMethod getMethod() {
    return method;
  }

  /** The breakdown of the solve just finished, as far as it got. Empty unless the cube drove it and
   * its milestones fitted a method. */
  public List<StepTime> getStepTimes() {
    return stepTimes;
  }

  /** The step the solve just finished stopped in, null when the cube saw it through to solved. */
  public Integer getStoppedStep() {
    return stoppedStep;
  }

  /** The moves of the solve just finished, stored form. Empty unless the cube drove it. */
  public String getSolveMoves() {
    return solveMoves;
  }

  /** True when a solve now would be broken down into steps: a 3x3 with a cube connected. */
  public boolean isCubeDriven() {
    return cubeDriven && SmartCubeManager.INSTANCE.isConnected();
  }

  public FollowMode getFollowMode() {
    switch (phase) {
      case NEEDS_SOLVE:
        return FollowMode.NEEDS_SOLVE;
      case FOLLOWING:
      case ARMED:
        return FollowMode.FOLLOWING;
      case RUNNING:
        return FollowMode.SOLVING;
      default:
        return FollowMode.INACTIVE;
    }
  }

  public int getDoneCount() {
    if (follower == null) {
      return 0;
    }
    return phase == Phase.ARMED ? follower.getMoveCount() : follower.getDoneCount();
  }

  public boolean isWrong() {
    return follower != null && follower.isWrong();
  }

  /** True once the whole scramble has been followed and the timer is armed. */
  public boolean isReadyToSolve() {
    return phase == Phase.ARMED;
  }

  /** Notation to execute to undo the wrong moves, e.g. "U' R2". Empty when on track. */
  public String getReverseMoves() {
    return follower == null ? "" : follower.getReverseMoves();
  }

  /**
   * Only a real change restarts the follow. Re-subscribing (the screen coming back from the
   * background) replays the connection we already had, and rebuilding on that would drop a scramble
   * the user is halfway through; the state replayed on re-subscribe reconciles the progress instead.
   */
  private void onConnection(CubeConnection connection) {
    if (connection != this.connection) {
      this.connection = connection;
      reevaluate();
    }
  }

  private void reevaluate() {
    if (phase != Phase.RUNNING && phase != Phase.ARMED) {
      applyScramble();
    }
  }

  private void applyScramble() {
    follower = null;
    analyzing = false;
    rotationTracker.reset(); // a new scramble re-anchors at its own first move
    sliceSpins.reset();
    lastFollowMoveWallMs = 0;
    if (!followable || scramble == null || !SmartCubeManager.INSTANCE.isConnected()) {
      phase = Phase.INACTIVE;
      notifyChanged();
      return;
    }
    try {
      follower = new ScrambleFollower(scramble);
    } catch (RuntimeException e) {
      follower = null;
      phase = Phase.INACTIVE;
      notifyChanged();
      return;
    }
    CubeState state = SmartCubeManager.INSTANCE.getCurrentState();
    if (state != null && state.isSolved()) {
      phase = Phase.FOLLOWING;
      follower.reset();
    } else {
      phase = Phase.NEEDS_SOLVE;
    }
    notifyChanged();
  }

  @Override
  public void onState(CubeState state) {
    if (analyzing) {
      analyzers.onState(state);
    }
    switch (phase) {
      case RUNNING:
        if (!state.isSolved()) {
          sawUnsolved = true;
        } else if (sawUnsolved && !blind) { // a blind solve is stopped by its solver, never by us
          phase = Phase.INACTIVE;
          listener.onCubeAutoStop();
        }
        break;
      case NEEDS_SOLVE:
        if (state.isSolved()) {
          phase = Phase.FOLLOWING;
          follower.reset();
          notifyChanged();
        }
        break;
      case FOLLOWING:
        if (follower.onState(state)) { // reconcile only; moves drive the follow
          if (follower.isLost()) {
            phase = Phase.NEEDS_SOLVE; // the cube is somewhere we cannot place: ask for a solved one
          } else if (follower.isComplete()) {
            phase = Phase.ARMED;
          }
          notifyChanged();
        }
        break;
      default:
        break;
    }
  }

  @Override
  public void onMove(CubeMove move) {
    switch (phase) {
      case FOLLOWING:
        // The first followed move is the one moment the cube is known to be held the way the
        // scramble reads, so its reading is the reference every later frame is measured from.
        long followMoveWallMs = System.currentTimeMillis();
        if (lastFollowMoveWallMs != 0
            && followMoveWallMs - lastFollowMoveWallMs > FOLLOW_RESUME_GAP_MS) {
          // A long pause mid-follow means the cube was set down, and it can be picked back up any
          // way up: the reference restarts at the move that resumes the scramble.
          rotationTracker.restartAnchor();
        }
        lastFollowMoveWallMs = followMoveWallMs;
        rotationTracker.anchor(SmartCubeManager.INSTANCE.getOrientation());
        boolean changed = follower.onMove(move);
        if (follower.isComplete()) {
          phase = Phase.ARMED;
          changed = true;
        }
        if (changed) {
          notifyChanged();
        }
        break;
      case ARMED:
        if (blind) {
          break; // only the tap starts a blind solve: a turn before it opens nothing
        }
        // Stays ARMED if the timer refused to start, so later moves must not re-anchor the analyzer.
        trackOrientation(move);
        if (analyzing) {
          analyzers.onMove(move);
        } else {
          beginAnalysis(move); // the cube is still scrambled here: the move has not been applied yet
        }
        listener.onCubeAutoStart(); // scramble is done; any move starts the solve
        break;
      case RUNNING:
        trackOrientation(move);
        // The opening is where the pick-up shows: the scramble was turned green in front and this is
        // the grip the solver memorised in, which is what their targets are named by. Asked again at
        // every move, since a first move taken inside a slice pair is answered only by a later one.
        analyzers.setPickupRotation(rotationTracker.getPickupRotation(sliceSpins.possiblePairs()));
        if (analyzing) {
          analyzers.onMove(move);
        } else if (!blind) {
          beginAnalysis(move); // tap-started solve: the first move opens the breakdown
        }
        // A blind solve that could not anchor at the tap gets no breakdown rather than one anchored
        // at the first move, which would report a memo of zero it never had.
        break;
      default:
        break;
    }
  }

  /** Both readers of the gyro: the frame the solve is turned in, and the slices it is turned with. */
  private void trackOrientation(CubeMove move) {
    rotationTracker.onMove(SmartCubeManager.INSTANCE.getOrientation(), move.getCubeTimestampMs());
    sliceSpins.onMove(move);
  }

  /** Anchor the breakdown on the state the cube is in now, dated at the given moment. */
  private void beginAnalysis(long startTimestampMs) {
    CubeState state = SmartCubeManager.INSTANCE.getCurrentState();
    if (state == null) {
      return;
    }
    analyzers.start(state, startTimestampMs);
    analyzing = true;
  }

  private void beginAnalysis(CubeMove move) {
    beginAnalysis(move.getCubeTimestampMs());
    if (analyzing) {
      analyzers.onMove(move);
    }
  }


  private void notifyChanged() {
    listener.onScrambleFollowChanged();
  }
}
