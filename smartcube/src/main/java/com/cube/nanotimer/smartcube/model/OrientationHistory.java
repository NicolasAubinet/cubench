package com.cube.nanotimer.smartcube.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A bounded history of the ~20 Hz orientation stream, so a reading can be taken at a moment that
 * has already passed.
 *
 * <p>A slice's core rock can only be confirmed in hindsight: what marks it as a
 * slice is a pair of opposite faces in the move stream, and by the time that pair has been seen,
 * the readings either side of it are already history.
 */
public final class OrientationHistory {

  /** Two and a half minutes at 20 Hz — longer than a solve, which is all it is ever read within. */
  private static final int MAX_SAMPLES = 3000;

  /** Samples arrive every ~50 ms, so a lookup landing further out than this has found a gap. */
  private static final long MATCH_TOLERANCE_MS = 150;

  private final Deque<Sample> samples = new ArrayDeque<Sample>();

  /** Feed every orientation sample, with the wall-clock time it arrived. */
  public synchronized void onSample(CubeOrientation orientation, long timestampMs) {
    if (orientation == null) {
      return;
    }
    samples.addLast(new Sample(orientation, timestampMs));
    while (samples.size() > MAX_SAMPLES) {
      samples.removeFirst();
    }
  }

  /** The reading nearest {@code timestampMs}, or null if none was taken close enough to it. */
  public synchronized CubeOrientation at(long timestampMs) {
    Sample nearest = null;
    for (Sample sample : samples) {
      if (nearest == null || sample.distanceTo(timestampMs) < nearest.distanceTo(timestampMs)) {
        nearest = sample;
      }
    }
    return nearest != null && nearest.distanceTo(timestampMs) <= MATCH_TOLERANCE_MS
        ? nearest.orientation
        : null;
  }

  /**
   * Every reading taken between two moments, oldest first — the raw stream a solve's gyro track is
   * keyframed out of. Empty for a cube with no gyro, and for a window older than the buffer holds.
   */
  public synchronized List<Sample> between(long fromMs, long toMs) {
    List<Sample> window = new ArrayList<Sample>();
    for (Sample sample : samples) {
      if (sample.timestampMs >= fromMs && sample.timestampMs <= toMs) {
        window.add(sample);
      }
    }
    return window;
  }

  /** One reading, and the host moment it arrived. */
  public static final class Sample {

    private final CubeOrientation orientation;
    private final long timestampMs;

    private Sample(CubeOrientation orientation, long timestampMs) {
      this.orientation = orientation;
      this.timestampMs = timestampMs;
    }

    public CubeOrientation getOrientation() {
      return orientation;
    }

    public long getTimestampMs() {
      return timestampMs;
    }

    private long distanceTo(long otherMs) {
      return Math.abs(timestampMs - otherMs);
    }
  }
}
