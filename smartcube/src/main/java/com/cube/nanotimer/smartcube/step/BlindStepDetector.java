package com.cube.nanotimer.smartcube.step;

import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Splits a blindfolded solve into memorisation and execution. The solver taps to start, memorises
 * without touching the cube, and the first turn is the moment the two meet — so memo is the one step
 * that is read off a move rather than off the state.
 *
 * <p>Two things separate this from the sighted detectors.
 *
 * <p><b>The solve is anchored at the tap, not at the first move.</b> Everywhere else the first move
 * both starts the clock and opens the breakdown, which would make memo zero by construction. Here
 * {@link #reset} is called with the time the timer started, and the wait that follows is the step.
 *
 * <p><b>Finished means the cube reached solved at some point, not that it is solved now.</b> Nothing
 * stops the timer for a blind solver — they cannot see that they are done, and may keep turning past
 * the end thinking an orientation is still out. So execution is dated at the first solved state and
 * whatever follows belongs to no step.
 */
public final class BlindStepDetector implements StepDetector {

  public static final int MEMO = 0;
  public static final int EXECUTION = 1;

  private static final String[] STEP_NAMES = {"memo", "execution"};

  private final Long[] times = new Long[STEP_NAMES.length];
  private final Long[] reported = new Long[STEP_NAMES.length];

  private long solveStartMs;
  private long lastTimestampMs;

  @Override
  public void reset(CubeState startState, long startTimestampMs) {
    Arrays.fill(times, null);
    Arrays.fill(reported, null);
    solveStartMs = startTimestampMs;
    lastTimestampMs = startTimestampMs;
  }

  @Override
  public List<StepBoundaryEvent> onState(CubeState state, CubeMove lastMove) {
    if (lastMove != null) {
      lastTimestampMs = lastMove.getCubeTimestampMs();
      if (times[MEMO] == null) {
        times[MEMO] = lastTimestampMs; // memorising ends the instant the cube is first turned
      }
    }
    // A solved state before the first move is the cube waiting to be scrambled, not a solve that is
    // already over, so execution can only be dated once memo has closed.
    if (times[MEMO] != null && times[EXECUTION] == null && state.isSolved()) {
      times[EXECUTION] = lastTimestampMs;
    }

    List<StepBoundaryEvent> events = new ArrayList<>();
    for (int step = 0; step < STEP_NAMES.length; step++) {
      if (times[step] != null && !times[step].equals(reported[step])) {
        events.add(new StepBoundaryEvent(step, times[step]));
      }
      reported[step] = times[step];
    }
    return events;
  }

  @Override
  public int stepCount() {
    return STEP_NAMES.length;
  }

  @Override
  public String stepName(int index) {
    return STEP_NAMES[index];
  }

  @Override
  public Long getStepTimestampMs(int index) {
    return times[index];
  }

  /** Memo has no moves to align, and a blind solver never sees a case to square up. */
  @Override
  public boolean isAlignmentMove(int step, CubeMove move) {
    return false;
  }

  @Override
  public int subStepCount(int step) {
    return 0;
  }

  @Override
  public String subStepName(int step, int subStep) {
    throw new IndexOutOfBoundsException("Blind steps have no parts yet");
  }

  @Override
  public Long getSubStepTimestampMs(int step, int subStep) {
    return null;
  }

  @Override
  public boolean isComplete() {
    return times[EXECUTION] != null;
  }

  /**
   * The solve was memorised before it was turned — which is all the state can say so far, and it is
   * true of any blind method. It deliberately puts no floor on how long memo lasted: a threshold
   * that would reject a sighted solve done on a blind solve type would be a guess at where memo
   * becomes "real", and every constant in this package is a measurement rather than a guess. The
   * discriminator that does not need one is telling the piece types apart, which comes with them.
   */
  @Override
  public boolean matchesMethod() {
    return times[MEMO] != null && times[MEMO] >= solveStartMs;
  }
}
