package com.cube.nanotimer.smartcube.scanner;

import com.cube.nanotimer.smartcube.SmartCube;
import com.cube.nanotimer.smartcube.model.DiscoveredCube;
import java.util.function.Consumer;

/**
 * Scans for smart cubes and connects to one, auto-detecting the brand from the advertised
 * name / service UUID. The single entry point for consumers.
 */
public interface CubeScanner {

  /** Start scanning; delivers cubes as they are discovered. */
  void scan(Consumer<DiscoveredCube> onDiscovered);

  /**
   * Connect to a discovered cube. {@code macAddress} is required only when
   * {@link DiscoveredCube#needsMac()} is true (colon-separated, e.g. "CF:30:16:00:AB:CD").
   */
  SmartCube connect(DiscoveredCube cube, String macAddress);

  default SmartCube connect(DiscoveredCube cube) {
    return connect(cube, null);
  }

  void stopScan();
}
