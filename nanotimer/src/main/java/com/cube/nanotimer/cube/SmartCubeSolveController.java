package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.model.CubeConnection;
import com.cube.nanotimer.smartcube.model.CubeConnectionListener;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeMoveListener;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.CubeStateListener;
import com.cube.nanotimer.smartcube.step.CFOPStepDetector;
import com.cube.nanotimer.smartcube.step.SolveAnalyzer;
import com.cube.nanotimer.smartcube.step.StepTime;
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

  private final Listener listener;
  private final CubeConnectionListener connectionListener = this::onConnection;
  private final SolveAnalyzer analyzer = new SolveAnalyzer(new CFOPStepDetector());
  private final RotationTracker rotationTracker = new RotationTracker();

  private CubeConnection connection;
  private List<StepTime> stepTimes = Collections.emptyList();
  private Integer stoppedStep;
  private String solveMoves = "";
  private String[] scramble;
  private boolean cubeDriven; // auto-stop applies (3x3 + connected)
  private boolean followable; // scramble-follow + auto-start apply (3x3 default scramble)
  private ScrambleFollower follower;
  private Phase phase = Phase.INACTIVE;
  private boolean sawUnsolved;
  private boolean analyzing;
  private long scrambleStartWallMs; // 0 until the first followed move of the current scramble
  private long lastFollowMoveWallMs;

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
   */
  public void setScramble(String[] scramble, boolean cubeDriven, boolean followable) {
    this.scramble = scramble;
    this.cubeDriven = cubeDriven;
    this.followable = followable;
    applyScramble();
  }

  public void onTimerStarted() {
    if (cubeDriven && SmartCubeManager.INSTANCE.isConnected()) {
      phase = Phase.RUNNING;
      sawUnsolved = false;
      notifyChanged(); // swap the scramble for the "solving" state
    } else {
      phase = Phase.INACTIVE;
    }
  }

  public void onTimerStopped() {
    // A solve the cube drove has a breakdown as far as its milestones went, whether or not it
    // reached solved — a botched PLL is exactly the solve worth looking at. What still earns none is
    // a method the milestones never fitted, or a prefix too short to tell the methods apart.
    boolean matched = analyzing && analyzer.matchesMethod();
    stepTimes = matched ? analyzer.getStepTimes() : Collections.<StepTime>emptyList();
    stoppedStep = matched ? analyzer.getStoppedStep() : null;
    // The moves need no method: an unrecognised solve still has a solution worth keeping.
    solveMoves = analyzing
        ? SolveMovesFormat.format(analyzer.getMoves(), rotationTracker.getRotations(),
            analyzer.getSolveStartMs())
        : "";
    analyzing = false;
    phase = Phase.INACTIVE; // the next setScramble (after a new scramble) re-activates follow
  }

  /** The CFOP breakdown of the solve just finished, as far as it got. Empty unless the cube drove
   * it and its milestones matched the method. */
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
    scrambleStartWallMs = 0;
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
      analyzer.onState(state);
    }
    switch (phase) {
      case RUNNING:
        if (!state.isSolved()) {
          sawUnsolved = true;
        } else if (sawUnsolved) {
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
            rotationTracker.scrambleComplete(System.currentTimeMillis());
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
        // Scrambling is when the cube is known to be held in the standard position, so its still
        // windows are fed as candidates for the standard grip. Only windows begun after this
        // scramble started count: the cube resting on the mat minutes earlier was also "still",
        // in whatever grip the previous solve left it.
        long followMoveWallMs = System.currentTimeMillis();
        if (scrambleStartWallMs == 0) {
          scrambleStartWallMs = followMoveWallMs;
        } else if (followMoveWallMs - lastFollowMoveWallMs > FOLLOW_RESUME_GAP_MS) {
          // A long pause mid-follow means the cube was set down: grips from before it say
          // nothing about how it is held now, and a real solve once anchored on the grip the
          // cube had rested in for hours. The break's own still window began before the new
          // cut-off, so it can never become the anchor either.
          scrambleStartWallMs = followMoveWallMs;
          rotationTracker.restartAnchor();
        }
        lastFollowMoveWallMs = followMoveWallMs;
        rotationTracker.anchor(SmartCubeManager.INSTANCE.getStillWindow(scrambleStartWallMs));
        boolean changed = follower.onMove(move);
        if (follower.isComplete()) {
          phase = Phase.ARMED;
          rotationTracker.scrambleComplete(System.currentTimeMillis());
          changed = true;
        }
        if (changed) {
          notifyChanged();
        }
        break;
      case ARMED:
        // Stays ARMED if the timer refused to start, so later moves must not re-anchor the analyzer.
        trackRotation(move);
        if (analyzing) {
          analyzer.onMove(move);
        } else {
          beginAnalysis(move); // the cube is still scrambled here: the move has not been applied yet
        }
        listener.onCubeAutoStart(); // scramble is done; any move starts the solve
        break;
      case RUNNING:
        trackRotation(move);
        if (analyzing) {
          analyzer.onMove(move);
        } else {
          beginAnalysis(move); // tap-started solve: the first move opens the breakdown
        }
        break;
      default:
        break;
    }
  }

  private void trackRotation(CubeMove move) {
    rotationTracker.onMove(SmartCubeManager.INSTANCE.getStillWindow(scrambleStartWallMs),
        SmartCubeManager.INSTANCE.getOrientation(), move.getCubeTimestampMs());
  }

  private void beginAnalysis(CubeMove move) {
    CubeState state = SmartCubeManager.INSTANCE.getCurrentState();
    if (state == null) {
      return;
    }
    analyzer.start(state, move.getCubeTimestampMs());
    analyzing = true;
    analyzer.onMove(move);
  }


  private void notifyChanged() {
    listener.onScrambleFollowChanged();
  }
}
