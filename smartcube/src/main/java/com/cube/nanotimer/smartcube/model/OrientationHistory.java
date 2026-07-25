package com.cube.nanotimer.smartcube.model;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A bounded history of the ~20 Hz orientation stream, so a reading can be taken at a moment that
 * has already passed.
 *
 * <p>{@link StillnessTracker} answers what grip the cube is in <em>now</em>; this answers what it
 * was in <em>then</em>. A slice's core rock can only be confirmed in hindsight: what marks it as a
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

  private static final class Sample {

    private final CubeOrientation orientation;
    private final long timestampMs;

    private Sample(CubeOrientation orientation, long timestampMs) {
      this.orientation = orientation;
      this.timestampMs = timestampMs;
    }

    private long distanceTo(long otherMs) {
      return Math.abs(timestampMs - otherMs);
    }
  }
}
