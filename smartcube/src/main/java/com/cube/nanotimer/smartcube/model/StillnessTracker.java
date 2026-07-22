package com.cube.nanotimer.smartcube.model;

/**
 * Watches the ~20 Hz orientation stream for windows where the cube is held still.
 *
 * <p>Exists because a sample taken <em>at a move</em> is mid-turn: wrist wobble puts it tens of
 * degrees from the grip the solver is actually holding, which is exactly how the solve anchor got
 * yaw-locked a quarter turn off. Between moves the grip is rock steady (noise ~6°), so a run of
 * consecutive samples agreeing within {@link #STILL_TOLERANCE_DEGREES} is the grip itself.
 */
public final class StillnessTracker {

  /** Comfortably above the ~6° sensor noise floor, far below the ~25°+ of mid-turn wobble. */
  private static final double STILL_TOLERANCE_DEGREES = 8;
  /** Three to four samples at 20 Hz — long enough that a turn cannot masquerade as a pause. */
  private static final long MIN_STILL_MS = 150;

  private CubeOrientation runFirst;
  private long runStartMs;
  private CubeOrientation stillOrientation;
  private long stillWindowStartMs;
  private long stillWindowEndMs;

  /** Feed every orientation sample, with the wall-clock time it arrived. */
  public synchronized void onSample(CubeOrientation orientation, long timestampMs) {
    if (orientation == null) {
      return;
    }
    if (runFirst == null || runFirst.angleToDegrees(orientation) > STILL_TOLERANCE_DEGREES) {
      runFirst = orientation;
      runStartMs = timestampMs;
      return;
    }
    if (timestampMs - runStartMs >= MIN_STILL_MS) {
      stillOrientation = orientation; // the freshest sample of the window: drift-free by definition
      stillWindowStartMs = runStartMs;
      stillWindowEndMs = timestampMs;
    }
  }

  /**
   * The latest still window that began after {@code heldAfterMs}, or null if the cube has not
   * held still since then. The cut-off matters: the cube resting on the table minutes earlier
   * is also "still", but in whatever orientation the previous solve left it.
   */
  public synchronized Window getStillWindow(long heldAfterMs) {
    return stillOrientation != null && stillWindowStartMs > heldAfterMs
        ? new Window(stillOrientation, stillWindowStartMs, stillWindowEndMs)
        : null;
  }

  /** One window the cube was held still in: a grip, with the span it was held for. */
  public static final class Window {

    private final CubeOrientation orientation;
    private final long startMs;
    private final long endMs;

    public Window(CubeOrientation orientation, long startMs, long endMs) {
      this.orientation = orientation;
      this.startMs = startMs;
      this.endMs = endMs;
    }

    /** The freshest sample of the window: drift-free by definition. */
    public CubeOrientation getOrientation() {
      return orientation;
    }

    public long getStartMs() {
      return startMs;
    }

    public long getDurationMs() {
      return endMs - startMs;
    }
  }
}
