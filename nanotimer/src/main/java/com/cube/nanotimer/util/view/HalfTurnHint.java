package com.cube.nanotimer.util.view;

import android.os.SystemClock;
import android.view.View;

/**
 * Holds back the quarter-left hint of a half turn (R2 shown as R once R is turned) until the cube
 * has rested a quarter in for {@link #DELAY_MS}, so an R2 turned in one go never flickers. Call
 * {@link #ready} from each render; it schedules one more render when the delay runs out.
 */
public class HalfTurnHint {

  static final long DELAY_MS = 150;

  private final View view;
  private final Runnable rerender;
  private Object pending; // the half-turn state waited on, null when there is none
  private long since;

  public HalfTurnHint(View view, Runnable rerender) {
    this.view = view;
    this.rerender = rerender;
  }

  /**
   * @param state what identifies the half turn the cube is a quarter into (such as its index), or
   *     null when it is in none
   * @return whether the hint may show now
   */
  public boolean ready(Object state) {
    if (state == null) {
      clear();
      return false;
    }
    long now = SystemClock.uptimeMillis();
    if (!state.equals(pending)) {
      clear();
      pending = state;
      since = now;
      view.postDelayed(rerender, DELAY_MS);
      return false;
    }
    return now - since >= DELAY_MS;
  }

  public void clear() {
    pending = null;
    view.removeCallbacks(rerender);
  }
}
