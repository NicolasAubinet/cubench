package com.cube.nanotimer.cube;

import android.os.Handler;
import android.os.Looper;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.smartcube.cube.StopPenalty;
import com.cube.nanotimer.smartcube.cube.StopSettle;
import com.cube.nanotimer.smartcube.model.CubeConnection;
import com.cube.nanotimer.smartcube.model.CubeConnectionListener;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeMoveListener;
import com.cube.nanotimer.smartcube.model.CubeRotation;
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

  /** How far past the last move the moves wait for the gyro: a settled reading, plus a sample period
   * for it to arrive. A solve ending on a slice has none yet when the cube reports it solved. */
  private static final long GYRO_CATCHUP_MS = SliceSpinDetector.SETTLE_MS + 50;

  private final Listener listener;
  private final CubeConnectionListener connectionListener = this::onConnection;
  private MethodAnalyzers analyzers = new MethodAnalyzers(CubeMethod.CFOP); // replaced by the solve type's own at the first setScramble
  // Taken once per gyro session and owned by the manager, so a solve does not re-take it.
  private final GyroReference gyroReference = SmartCubeManager.INSTANCE.getGyroReference();
  private final RotationTracker rotationTracker = new RotationTracker(gyroReference);
  private final SliceSpinDetector sliceSpins = new SliceSpinDetector();
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  // The window the cube has after a tap to finish the solve it was stopped in.
  private final StopSettle settle = new StopSettle();
  private final Runnable handover = this::recordMoves;

  private CubeConnection connection;
  private List<StepTime> stepTimes = Collections.emptyList();
  private CubeMethod method; // which one the solve just finished fitted, null for none
  private CubeMethod expectedMethod; // what the solve type says its solves are, and the only one read
  private Integer stoppedStep;
  private String solveMoves = "";
  private String gyroTrack; // the solve's small physical rotations, null without a gyro to read them
  private String[] scramble;
  private boolean cubeDriven; // auto-stop applies (3x3 + connected)
  private boolean followable; // scramble-follow + auto-start apply (3x3 default scramble)
  private boolean blind; // the solve type memorises first: neither end of the solve is the cube's
  private boolean endsSolved; // the solve is meant to finish solved, so its stop is worth judging
  private ScrambleFollower follower;
  private Phase phase = Phase.INACTIVE;
  private boolean sawUnsolved;
  private boolean analyzing;
  private CubeRotation pickup; // the grip the running solve was picked up in, once it is known
  private boolean pickupRead; // whether the reading before its first move has been asked for
  private CubeRotation usedPickup; // the grip the analysis actually ran on, settled reading or fallback
  private long timerStartMs; // when the tap started the solve, on the cube's (host-fitted) clock
  private long lastSolveMoveHostMs; // host clock at the solve's latest move, 0 before the first
  private Runnable pendingRecord; // set while the stopped solve is still waiting on the cube

  public SmartCubeSolveController(Listener listener) {
    this.listener = listener;
  }

  public void start() {
    SmartCubeManager.INSTANCE.addStateListener(this);
    SmartCubeManager.INSTANCE.addMoveListener(this);
    SmartCubeManager.INSTANCE.addConnectionListener(connectionListener);
  }

  public void stop() {
    recordMoves(); // the screen is going away: hand the solve over now rather than lose it
    SmartCubeManager.INSTANCE.removeStateListener(this);
    SmartCubeManager.INSTANCE.removeMoveListener(this);
    SmartCubeManager.INSTANCE.removeConnectionListener(connectionListener);
  }

  /**
   * @param cubeDriven true when the cube may auto-stop this solve (3x3, connected)
   * @param followable true when the scramble can be followed + auto-started (3x3 full scramble)
   * @param blind true when the solve type is a blindfolded one, which turns both automatic ends of
   *     the solve off (see {@link #onTimerStarted()})
   * @param endsSolved true when the solve is meant to finish on a solved cube, which is what makes
   *     an unsolved stop worth judging. A practice state (a cross, an F2L case) ends unsolved by
   *     design, so its stop earns nothing and waits for nothing; its late moves are still the
   *     solve's, which is the other half of {@link StopSettle}.
   * @param expectedMethod the method the solve type is read as, already resolved against the
   *     preferred one. The solve is read as that method or as none: it is what the type says its
   *     solves are, not a guess to be overruled by whatever else the moves happen to fit.
   */
  public void setScramble(String[] scramble, boolean cubeDriven, boolean followable, boolean blind,
      boolean endsSolved, CubeMethod expectedMethod) {
    this.scramble = scramble;
    this.cubeDriven = cubeDriven;
    this.followable = followable;
    this.blind = blind;
    this.endsSolved = endsSolved;
    if (expectedMethod != this.expectedMethod) {
      this.expectedMethod = expectedMethod;
      analyzers = new MethodAnalyzers(expectedMethod); // another method is read by another detector
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
    usedPickup = null; // whatever the last solve was read through is not this one's
    recordMoves(); // this solve's moves would land in the trackers the previous one still reads
    if (cubeDriven && SmartCubeManager.INSTANCE.isConnected()) {
      phase = Phase.RUNNING;
      sawUnsolved = false;
      pickup = null; // this solve reads its own grip, at its own first move
      pickupRead = false;
      if (blind) {
        beginAnalysis(timerStartMs); // the memo starts here, with the cube untouched
      }
      notifyChanged(); // swap the scramble for the "solving" state
    } else {
      phase = Phase.INACTIVE;
    }
  }

  /**
   * Ends the solve and hands it over through {@code onRecorded}, which runs a moment after this
   * returns: the solve waits on the gyro (see {@link #GYRO_CATCHUP_MS}) and on whatever the cube
   * still has to say about its own end (see {@link StopSettle}). It always runs, and always on the
   * main thread.
   */
  public void onTimerStopped(Runnable onRecorded) {
    // A solve the cube drove has a breakdown as far as its milestones went, whether or not it
    // reached solved — a botched PLL is exactly the solve worth looking at. What still earns none is
    // a method the milestones never fitted, or a prefix too short to tell the methods apart.
    boolean cubeDrove = analyzing;
    // Judged from the state at the tap rather than the one at the handover, which is late enough
    // for the cube to have been put down and picked up again. A solve is judged on the state it was
    // stopped in, and the window only ever takes that verdict back down (see StopSettle).
    //
    // A cube that dropped mid-solve leaves its last state behind it, and that state is not the one
    // the solve ended in: a solve nothing was watching the end of is judged by nothing.
    boolean readable = cubeDrove && SmartCubeManager.INSTANCE.isConnected();
    // The reading is taken whether or not this solve is judged on it: an unjudged practice state
    // still has a last move that can be late, and the window is what waits for it.
    settle.onStop(readable ? StopPenalty.of(SmartCubeManager.INSTANCE.getCurrentState())
        : StopPenalty.none(), endsSolved, System.currentTimeMillis());
    phase = Phase.INACTIVE; // the next setScramble (after a new scramble) re-activates follow
    if (!cubeDrove) {
      method = null;
      stepTimes = Collections.emptyList();
      stoppedStep = null;
      solveMoves = "";
      gyroTrack = null;
      onRecorded.run();
      return;
    }
    pendingRecord = onRecorded;
    scheduleHandover();
  }

  /**
   * Arms the handover for the latest of what the stopped solve is still owed, and re-arms it
   * whenever that changes: a move landing after the tap brings its own gyro catch-up with it, and a
   * cube reporting itself solved takes the rest of the wait away.
   */
  private void scheduleHandover() {
    if (pendingRecord == null) {
      return;
    }
    long readyAtMs =
        Math.max(lastSolveMoveHostMs + GYRO_CATCHUP_MS, settle.getVerdictOpenUntilMs());
    mainHandler.removeCallbacks(handover);
    mainHandler.postDelayed(handover, Math.max(0, readyAtMs - System.currentTimeMillis()));
  }

  /** Reads the solve's moves off the trackers and hands it over. Runs once per stop, whoever calls. */
  private void recordMoves() {
    if (pendingRecord == null) {
      return;
    }
    Runnable onRecorded = pendingRecord;
    pendingRecord = null;
    mainHandler.removeCallbacks(handover);
    settle.close();
    analyzing = false; // the solve is over: no later state may still reach the analyzers
    // Resolved here rather than at the tap, so the moves that landed after it are read too. Taken
    // at the tap, a solve whose last algorithm was still in flight was called abandoned.
    method = analyzers.resolve();
    SolveAnalyzer analyzer = method == null ? null : analyzers.get(method);
    stepTimes = analyzer == null ? Collections.<StepTime>emptyList() : analyzer.getStepTimes();
    stoppedStep = analyzer == null ? null : analyzer.getStoppedStep();
    long solveStartMs = analyzers.moves().getSolveStartMs();
    // The moves need no method: an unrecognised solve still has a solution worth keeping.
    solveMoves = SolveMovesFormat.format(analyzers.moves().getMoves(),
        rotationTracker.getRotations(
            sliceSpins.coreSpins(SmartCubeManager.INSTANCE::getOrientationAt)),
        solveStartMs, gripToStore());
    // The same window the moves cover, read off the buffer the gyro has been filling all along —
    // no sampling of our own during the solve, so recording costs nothing until it is over.
    gyroTrack = GyroTrackFormat.format(
        SmartCubeManager.INSTANCE.getOrientationsBetween(
            solveStartMs, lastSolveMoveHostMs + GYRO_CATCHUP_MS),
        gyroReference.get(), solveStartMs);
    onRecorded.run();
  }

  /**
   * The grip the stored stream carries: the one the analysis actually named the solve through, so
   * reading it back gives the same names. A blind solve settles its own from the pieces it shot
   * from, which is the answer wherever it has one; anything else keeps the gyro's.
   */
  private String gripToStore() {
    CubeRotation settled = analyzers.getPickupRotation();
    if (settled != null) {
      return settled.getNotation();
    }
    return usedPickup == null ? null : usedPickup.getNotation();
  }

  /**
   * What the state the solve just finished was stopped in earned it. Never null, and always none
   * for a solve that is not judged on it: the reading is taken for the window's sake either way, and
   * a caller should not have to know which solves it means anything for.
   */
  public StopPenalty getStopPenalty() {
    return endsSolved ? settle.getPenalty() : StopPenalty.none();
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

  /**
   * The gyro track of the solve just finished, stored form, or null where there is none — a cube
   * with no gyro (every GAN Gen3, and not every other GAN has one), or a solve the cube did not
   * drive. The moves stand without it: it is what the discrete rotation tokens leave out.
   */
  public String getGyroTrack() {
    return gyroTrack;
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

  /**
   * Whether the cube is carrying an attempt: from the <b>first scramble move applied to it</b> until
   * the solve ends. Through that window what the cube shows <em>is</em> the attempt — the scramble
   * being built, then the solve coming apart — which is why a blind solve type hides the live mirror
   * on it. Deliberately not the whole of {@code FOLLOWING}: that begins with the cube still solved,
   * and there is nothing to give away yet.
   */
  public boolean isAttemptUnderway() {
    switch (phase) {
      case FOLLOWING:
        return follower != null && follower.getDoneCount() > 0;
      case ARMED:
      case RUNNING:
        return true;
      default:
        return false;
    }
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
    recordMoves(); // the resets below are what a solve still waiting on the gyro reads
    follower = null;
    analyzing = false;
    rotationTracker.reset();
    // ⚠️ The reference is deliberately NOT restarted here. A new scramble used to re-anchor at its
    // own first move, which swung the cube on screen by however differently it was being held from
    // the grip the last solve ended in. Uprighting settles the up face from gravity anyway, so a
    // fresh reading only re-picks the yaw — and yaw holds for a whole session.
    sliceSpins.reset();
    pickup = null;
    pickupRead = false;
    lastSolveMoveHostMs = 0;
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
    long nowMs = System.currentTimeMillis();
    // Past the window the cube is being handled, and a state from there would let the detectors
    // mark milestones the solve itself never reached.
    if (analyzing && (pendingRecord == null || settle.acceptsReading(nowMs))) {
      analyzers.onState(state);
    }
    if (pendingRecord != null && settle.onState(StopPenalty.of(state), nowMs)) {
      scheduleHandover(); // a softer verdict shortens what is left to wait for
    }
    switch (phase) {
      case RUNNING:
        if (!state.isSolved()) {
          sawUnsolved = true;
        } else if (sawUnsolved && stopsItself()) {
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

  // Asked here and not at the timer: a solve nothing stops stays RUNNING, so its later moves still
  // reach the analyzers. A blind one is stopped by its solver; the setting says so for the rest.
  private boolean stopsItself() {
    return !blind && Options.INSTANCE.isSmartCubeAutoStop();
  }

  @Override
  public void onMove(CubeMove move) {
    if (!gyroReference.isSet()) {
      // The connection takes the reference, but a cube's gyro stream can start later than the two
      // seconds it waits. Turning one proves there is a cube in a hand, so ask again.
      SmartCubeManager.INSTANCE.anchorGyroIfUnset();
    }
    // The moves that finish a solve can reach us after the tap that ended it. Inside the settle
    // window they are still that solve's: dropping them left the stored stream a few moves short of
    // the end, which the breakdown then read as a solve given up on.
    if (pendingRecord != null && settle.acceptsReading(System.currentTimeMillis())) {
      trackOrientation(move);
      readPickup(); // the same refresh a running solve does: the tracker has just moved on
      analyzers.onMove(move);
      scheduleHandover(); // the move brings its own gyro catch-up with it
      return;
    }
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
        readPickup();
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

  /**
   * The grip the solve was picked up in, which is what a blind solver's targets are named by, read
   * across everything <b>before</b> its first move and then left alone.
   *
   * <p>Every reading at a move has the solve's own slices in it — a slice carries the core, and the
   * gyro and the face labels with it — while the names are spelled off states carried back to the
   * frame the solve started in. So a solve opening on a slice spelled every target a quarter turn
   * out, and re-asking each move moved the spelling mid-solve. Before the first move nothing has
   * turned, so the core has not spun and the whole stretch is of one grip.
   *
   * <p>Read from all of it rather than from one reading in it: a blind memorisation is half a minute
   * of holding the cube and peeking at it, and the instant before the first move is the worst of it,
   * with the hands settling back onto the cube. One reading there named a whole solve through the
   * scramble's grip. Falls back on the frame at the first move where the gyro said nothing earlier.
   */
  private void readPickup() {
    if (pickup != null) {
      return;
    }
    if (!pickupRead) {
      pickupRead = true;
      // Both bounds on the host clock, the one the readings are filed under. The move's own is the
      // cube's, fitted to host time only within a couple of seconds, so asking the history with it
      // silently answers from the wrong moment rather than not at all.
      pickup = gyroReference.frameOver(
          SmartCubeManager.INSTANCE.getOrientationsBetween(timerStartMs, lastSolveMoveHostMs),
          lastSolveMoveHostMs);
    }
    // Kept as the grip the analysis actually ran on, fallback included, since that is the one the
    // stored moves have to be read back through for the names to come out the same.
    usedPickup = pickup != null ? pickup
        : rotationTracker.getPickupRotation(sliceSpins.possiblePairs());
    analyzers.setPickupRotation(usedPickup);
  }

  /** Both readers of the gyro: the frame the solve is turned in, and the slices it is turned with. */
  private void trackOrientation(CubeMove move) {
    rotationTracker.onMove(SmartCubeManager.INSTANCE.getOrientation(), move.getCubeTimestampMs());
    sliceSpins.onMove(move);
    // Host time, not the move's own: the gyro samples are filed under the host clock, and the cube's
    // is only fitted to it, re-anchoring at a 2 s error — too coarse to time a 250 ms wait on.
    lastSolveMoveHostMs = System.currentTimeMillis();
  }

  /** Anchor the breakdown on the state the cube is in now, dated at the given moment. */
  private void beginAnalysis(long startTimestampMs) {
    CubeState state = SmartCubeManager.INSTANCE.getCurrentState();
    if (state == null) {
      return;
    }
    // Asked here, at every solve, rather than once with the scramble: a solver who has only just
    // been asked what they shoot from, or who has changed it in the settings, is answered on the
    // very next solve and not on the one after it.
    analyzers.setBlindBuffers(
        Options.INSTANCE.getBlindEdgeBuffer(), Options.INSTANCE.getBlindCornerBuffer());
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
