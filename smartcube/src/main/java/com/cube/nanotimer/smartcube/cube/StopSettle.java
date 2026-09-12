package com.cube.nanotimer.smartcube.cube;

/**
 * The window after a tap in which the cube may still be reporting the solve it was stopped in.
 *
 * <p>A solve's last moves can reach the app after the tap that ended it. The radio has a latency of
 * its own, a dropped notification is held back until the cube's move history fills the gap, and
 * every notification is delivered on the main looper, so a packet that arrived before the tap can
 * still be queued behind it. Judged on what had arrived at that instant, a finished solve reads as
 * one stopped a move or two short: a +2 or a DNF nobody earned, and a move stream too short for the
 * breakdown to see the solve through to its end.
 *
 * <p>The window has two halves, because they are owed to different things and cost differently.
 *
 * <p><b>Readings</b> (moves and states) landing inside it are still the stopped solve's, and every
 * stopped solve opens that half: a practice state earns no verdict but its last moves are as late
 * as anyone's, and a cube that already reads solved can still have that move's callbacks queued
 * behind the tap, since the state is written as the packet lands while the callbacks are posted
 * behind it. It costs nothing on a solve where nothing more arrives.
 *
 * <p><b>The verdict</b> is held open only for a solve that is judged and was stopped short, and
 * only that half makes the handover wait. It closes early on the cube reporting the state the solve
 * was headed for, which is the answer and leaves nothing to wait for. Only a <em>milder</em> verdict
 * is ever taken, and only from a state the cube itself reports, so nothing done after a tap softens
 * one by accident: it would have to land the cube exactly where the solve was already going.
 *
 * <p><b>The one thing it cannot do is tell an in-flight move from a turn made after the tap.</b> The
 * cube's clock is fitted to host time only within a couple of seconds, so neither can be dated out
 * of the way, and a reading is therefore taken as the solve's on timing alone. Two things keep that
 * honest: while the cube is short of where the solve was going, a late move is far likelier than a
 * deliberate turn, and the moment it reports itself there, the window shuts rather than running on.
 * A cube with nothing left to report is given only {@link #DRAIN_MS} instead.
 *
 * <p>{@link #WINDOW_MS} is deliberately generous: a window too long costs a verdict that lands a
 * moment later, while one too short costs the solve.
 */
public final class StopSettle {

  /** How long past the tap a cube stopped short of solved may still be finishing. */
  public static final long WINDOW_MS = 600;

  /**
   * How long past the tap a cube with nothing left to report may still be delivering it: long
   * enough to drain callbacks the radio had already handed over, short enough that the next turn is
   * read as what it is, the cube being handled.
   */
  public static final long DRAIN_MS = 150;

  private StopPenalty penalty = StopPenalty.none();
  private long readingsUntilMs; // the latest a move or a state may still be the stopped solve's
  private long verdictOpenUntilMs; // while the verdict may still soften, 0 once nothing is owed

  /**
   * Opens the window on the state the tap found the cube in.
   *
   * @param atTap what that state earns the solve
   * @param judged whether the verdict is this solve's to carry. Only a judged solve holds one open,
   *     and holding one open is the half that makes the handover wait.
   */
  public void onStop(StopPenalty atTap, boolean judged, long nowMs) {
    penalty = atTap;
    readingsUntilMs = nowMs + (atTap.isNone() ? DRAIN_MS : WINDOW_MS);
    verdictOpenUntilMs = judged && !atTap.isNone() ? nowMs + WINDOW_MS : 0;
  }

  /**
   * Takes a state the cube reported while the verdict is open.
   *
   * @return true when the verdict changed, which is the caller's cue that what it is still waiting
   *     for has changed too
   */
  public boolean onState(StopPenalty reading, long nowMs) {
    if (verdictOpenUntilMs == 0 || nowMs > verdictOpenUntilMs
        || !reading.isMilderThan(penalty)) {
      return false;
    }
    penalty = reading;
    if (penalty.isNone()) {
      // The cube is where the solve was going: nothing after this is the solve's, and nothing is
      // owed to it either.
      verdictOpenUntilMs = 0;
      readingsUntilMs = 0;
    }
    return true;
  }

  /** Whether a move or a state landing now is still the stopped solve's. */
  public boolean acceptsReading(long nowMs) {
    return readingsUntilMs > 0 && nowMs <= readingsUntilMs;
  }

  /** Wall clock: the moment the verdict stops being open, or 0 with nothing owed to it. */
  public long getVerdictOpenUntilMs() {
    return verdictOpenUntilMs;
  }

  /** What the state the solve was stopped in earned it, as far as the window got. Never null. */
  public StopPenalty getPenalty() {
    return penalty;
  }

  /** Shuts the window, keeping the verdict it settled on for the solve being handed over. */
  public void close() {
    readingsUntilMs = 0;
    verdictOpenUntilMs = 0;
  }
}
