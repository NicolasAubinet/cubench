package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.CubeStateListener;

/**
 * Isolated cube-to-timer bridge. While armed (the timer is running), it watches the
 * connected cube's state and fires {@link Listener#onCubeSolveCompleted()} the first time
 * the cube reaches solved after having been unsolved. Keeping this here lets the timer
 * screen stay almost untouched: it just arms/disarms and reacts to the one callback.
 */
public class SmartCubeTimerController implements CubeStateListener {

  public interface Listener {
    /** The connected cube reached solved during a running solve. Fires on the main thread. */
    void onCubeSolveCompleted();
  }

  private final Listener listener;
  private boolean armed;
  private boolean sawUnsolved;

  public SmartCubeTimerController(Listener listener) {
    this.listener = listener;
  }

  public void start() {
    SmartCubeManager.INSTANCE.addStateListener(this);
  }

  public void stop() {
    SmartCubeManager.INSTANCE.removeStateListener(this);
  }

  /** Begin watching for a solve completion (call when the timer starts). */
  public void arm() {
    armed = true;
    sawUnsolved = false;
  }

  /** Stop watching (call when the timer stops). */
  public void disarm() {
    armed = false;
  }

  @Override
  public void onState(CubeState state) {
    if (!armed) {
      return;
    }
    if (!state.isSolved()) {
      sawUnsolved = true;
    } else if (sawUnsolved) {
      armed = false;
      listener.onCubeSolveCompleted();
    }
  }
}
