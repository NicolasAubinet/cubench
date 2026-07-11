package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.model.DiscoveredCube;

/** Notified on the main thread as cubes are discovered during a scan. */
public interface SmartCubeScanListener {
  void onCubeDiscovered(DiscoveredCube cube);
}
