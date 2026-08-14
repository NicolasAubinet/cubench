package com.cube.nanotimer.smartcube.drivers;

/**
 * Carries a cube's own clock onto host time, monotonically.
 *
 * <p>A cube counts from its own power-on, which means nothing to anything reading its moves, so the
 * offset between the two clocks is fitted on the first one. It is <b>never re-fitted while the moves
 * keep coming</b>, however far the two have drifted: a host time is taken when Android hands the
 * notification to the app, on the main thread and after whatever the UI was doing, so it is by far
 * the noisier of the two, in bursts of seconds. Re-fitting on that noise moves the timeline
 * backwards mid-solve, and a step dated before the moves that built it hands those moves to the step
 * after it. That is how a 10-move cross was read as a 4-move one, its last edge landing in the first
 * F2L pair.
 *
 * <p>What the re-fit is actually for is a cube that slept and came back counting from zero, caught
 * here by its clock running backwards, and a clock of either side that jumped or ran slow. Both are
 * asked for only at a gap in the stream, which falls between solves rather than inside one, and only
 * when the two clocks really have parted. That leaves the alignment the slice detector reads the
 * gyro history by no looser than it was, while leaving the moves of a solve alone.
 */
final class CubeClock {

  /** How far the two clocks may part before a gap in the stream is taken as worth re-fitting on. */
  private static final long DRIFT_MS = 2000;

  /** A gap this long falls between solves, where a jump in the timeline crosses nothing. */
  private static final long IDLE_MS = 10000;

  private long offsetMs;
  private long lastCubeMs;
  private long lastHostMs;
  private long lastStampMs;
  private boolean fitted;

  /**
   * The host-time stamp for a move the cube dated {@code cubeMs}, delivered at {@code hostTimeMs}.
   * Strictly increasing across a stream, so the moves of a solve can never be read out of order.
   */
  long stamp(long cubeMs, long hostTimeMs) {
    if (!fitted || cubeMs < lastCubeMs || staleAtGap(cubeMs, hostTimeMs)) {
      offsetMs = hostTimeMs - cubeMs;
      fitted = true;
      lastStampMs = Long.MIN_VALUE; // a re-fit is the one place the timeline may move
    }
    lastCubeMs = cubeMs;
    lastHostMs = hostTimeMs;
    lastStampMs = Math.max(cubeMs + offsetMs, lastStampMs + 1);
    return lastStampMs;
  }

  /** The stamp of the last move, for one that arrived without a time of its own. */
  long lastStamp() {
    return fitted ? lastStampMs : 0;
  }

  private boolean staleAtGap(long cubeMs, long hostTimeMs) {
    return hostTimeMs - lastHostMs > IDLE_MS
        && Math.abs(hostTimeMs - (cubeMs + offsetMs)) > DRIFT_MS;
  }
}
