package com.cube.nanotimer.smartcube;

import com.cube.nanotimer.smartcube.model.CubeBatteryListener;
import com.cube.nanotimer.smartcube.model.CubeConnection;
import com.cube.nanotimer.smartcube.model.CubeConnectionListener;
import com.cube.nanotimer.smartcube.model.CubeMoveListener;
import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.CubeStateListener;
import com.cube.nanotimer.smartcube.model.DiscoveredCube;
import com.cube.nanotimer.smartcube.model.OrientationHistory;
import java.util.List;

/**
 * A connected smart cube. Brand-agnostic: consumers register listeners and never touch
 * BLE or per-brand protocol.
 */
public interface SmartCube {

  DiscoveredCube getDevice();

  /** Quarter-turn events, in order, with fitted timestamps. */
  void addMoveListener(CubeMoveListener listener);

  /** Full-state snapshots — emitted on connect and after every applied move. */
  void addStateListener(CubeStateListener listener);

  void addConnectionListener(CubeConnectionListener listener);

  void addBatteryListener(CubeBatteryListener listener);

  CubeConnection getConnection();

  /** The last known cube state (integrated from the move stream). */
  CubeState getCurrentState();

  /** Last known battery percentage 0–100, or null if not yet received. */
  Integer getBatteryLevel();

  /**
   * The cube's last reported physical orientation, or null if it has no gyro or none has
   * arrived yet. Polled rather than pushed: the stream runs at ~20 Hz, far faster than any
   * consumer needs, so callers sample it at the moments that matter (e.g. when a move lands).
   */
  CubeOrientation getOrientation();

  /**
   * The orientation the cube was in at {@code timestampMs} (wall clock), or null if no reading was
   * taken near it. Reading backwards is what tells a slice from a two-handed pair of the same two
   * faces: the slice rocks the core, and the proof of it is the step between the readings either
   * side of the turn.
   */
  CubeOrientation getOrientationAt(long timestampMs);

  /**
   * Every reading taken between two moments (wall clock), oldest first. Empty where the cube has no
   * gyro, and where the window has already fallen out of the buffer — which holds about two and a
   * half minutes, longer than the solve it is ever read within.
   */
  List<OrientationHistory.Sample> getOrientationsBetween(long fromMs, long toMs);

  /** Pull a fresh full state from the cube to re-anchor after packet loss. */
  void requestState();

  /** Prompt the cube to report a fresh battery level. */
  void requestBattery();

  /** Tell the tracker the cube is now in {@code state} (e.g. solved by hand). */
  void syncState(CubeState state);

  /**
   * Whether {@link #syncState} reaches the cube itself rather than only this app's model of it. A
   * cube that cannot be told keeps its own idea of where it is and hands it back on every connect,
   * so a drift there has to be corrected on this side instead.
   */
  boolean supportsStateReset();

  /** Re-zero the gyroscope orientation reference (where supported). */
  void resetGyro();

  void disconnect();
}
