package com.cube.nanotimer.smartcube;

import com.cube.nanotimer.smartcube.model.CubeBatteryListener;
import com.cube.nanotimer.smartcube.model.CubeConnection;
import com.cube.nanotimer.smartcube.model.CubeConnectionListener;
import com.cube.nanotimer.smartcube.model.CubeMoveListener;
import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.CubeStateListener;
import com.cube.nanotimer.smartcube.model.DiscoveredCube;

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

  /** Pull a fresh full state from the cube to re-anchor after packet loss. */
  void requestState();

  /** Prompt the cube to report a fresh battery level. */
  void requestBattery();

  /** Tell the tracker the cube is now in {@code state} (e.g. solved by hand). */
  void syncState(CubeState state);

  /** Re-zero the gyroscope orientation reference (where supported). */
  void resetGyro();

  void disconnect();
}
