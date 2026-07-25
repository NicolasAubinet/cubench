package com.cube.nanotimer.smartcube.drivers;

import com.cube.nanotimer.smartcube.model.CubeState;
import java.util.List;

/**
 * One GAN protocol generation. Implementations are PURE — bytes in, events out — so they unit-test
 * from fixtures without hardware.
 */
public interface GanProtocol {

  /** The encrypted packet for {@code request}, or null if this generation has no such command. */
  int[] encodeRequest(GanRequest request);

  /**
   * The encrypted packet asking for {@code count} moves ending at {@code serial}, or null on
   * generations that never ask (Gen2).
   */
  int[] encodeMoveHistory(int serial, int count);

  List<GanEvent> parse(int[] raw, long hostTimeMs);

  CubeState getCurrentState();

  /** True while the model is untrusted: moves are dropped until facelets re-anchor it. */
  boolean needsAnchor();

  /** Last known battery percentage, or null if none has arrived yet. */
  Integer getBatteryLevel();

  /** Realign the tracked model without a physical resync. */
  void setState(CubeState state);
}
