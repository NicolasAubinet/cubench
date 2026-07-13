package com.cube.nanotimer.cube;

import android.util.Log;
import com.cube.nanotimer.smartcube.model.CubeConnection;
import com.cube.nanotimer.smartcube.model.CubeConnectionListener;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeMoveListener;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.CubeStateListener;
import com.cube.nanotimer.smartcube.step.CFOPStepDetector;
import com.cube.nanotimer.smartcube.step.SolveAnalyzer;
import com.cube.nanotimer.smartcube.step.StepTime;
import java.util.List;
import java.util.Locale;

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

  private static final String LOG_TAG = "SmartCube";

  private final Listener listener;
  private final CubeConnectionListener connectionListener = connection -> reevaluate();
  private final SolveAnalyzer analyzer = new SolveAnalyzer(new CFOPStepDetector());

  private String[] scramble;
  private boolean cubeDriven; // auto-stop applies (3x3 + connected)
  private boolean followable; // scramble-follow + auto-start apply (3x3 default scramble)
  private ScrambleFollower follower;
  private Phase phase = Phase.INACTIVE;
  private boolean sawUnsolved;
  private boolean analyzing;

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
    if (analyzing) {
      logStepTimes();
      analyzing = false;
    }
    phase = Phase.INACTIVE; // the next setScramble (after a new scramble) re-activates follow
  }

  /** The CFOP breakdown of the solve just finished. Empty unless the cube drove it. */
  public List<StepTime> getStepTimes() {
    return analyzer.getStepTimes();
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

  private void reevaluate() {
    if (phase != Phase.RUNNING && phase != Phase.ARMED) {
      applyScramble();
    }
  }

  private void applyScramble() {
    follower = null;
    analyzing = false;
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
          if (follower.isComplete()) {
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
        beginAnalysis(move); // the cube is still scrambled here: the move has not been applied yet
        listener.onCubeAutoStart(); // scramble is done; any move starts the solve
        break;
      case RUNNING:
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

  private void beginAnalysis(CubeMove move) {
    CubeState state = SmartCubeManager.INSTANCE.getCurrentState();
    if (state == null) {
      return;
    }
    analyzer.start(state, move.getCubeTimestampMs());
    analyzing = true;
    analyzer.onMove(move);
  }

  private void logStepTimes() {
    StringBuilder sb = new StringBuilder("Solve breakdown:");
    for (StepTime step : getStepTimes()) {
      appendStepTime(sb, step, "\n  ");
      for (StepTime subStep : step.getSubSteps()) {
        appendStepTime(sb, subStep, "\n      ");
      }
    }
    Log.i(LOG_TAG, sb.toString());
  }

  private static void appendStepTime(StringBuilder sb, StepTime step, String indent) {
    sb.append(indent).append(String.format(Locale.US, "%-8s %5.2f  (recognition %.2f, execution %.2f)",
        step.getStepName(), step.getTotalMs() / 1000f,
        step.getRecognitionMs() / 1000f, step.getExecutionMs() / 1000f));
  }

  private void notifyChanged() {
    listener.onScrambleFollowChanged();
  }
}
