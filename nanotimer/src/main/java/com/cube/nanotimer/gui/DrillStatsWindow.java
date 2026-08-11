package com.cube.nanotimer.gui;

import com.cube.nanotimer.R;

import java.util.Calendar;

/**
 * How far back a drill stats screen is looking. Shared by the screen that lists the cases and the
 * one that lists a case's attempts, so that opening a case cannot quietly widen the window its
 * figures were read in.
 *
 * <p>The windows are counted in whole days from the start of today rather than in hours back from
 * now. A rolling week would drop the beginning of a session done at this hour seven days ago and
 * keep the end of it, which is a stranger thing to report than either including or excluding the
 * day.
 */
public enum DrillStatsWindow {

  TODAY(R.string.drill_stats_window_today, R.string.drill_stats_window_today_long, 0),
  WEEK(R.string.drill_stats_window_week, R.string.drill_stats_window_week_long, 6),
  MONTH(R.string.drill_stats_window_month, R.string.drill_stats_window_month_long, 29),
  /** Everything ever recorded, which is where the screen opens: a new user's week is empty. */
  ALL(R.string.drill_stats_window_all, R.string.drill_stats_window_all_long, -1);

  private final int labelId;
  private final int longLabelId;
  private final int daysBack;

  DrillStatsWindow(int labelId, int longLabelId, int daysBack) {
    this.labelId = labelId;
    this.longLabelId = longLabelId;
    this.daysBack = daysBack;
  }

  /** What the segment says, which has to be a word. */
  public int getLabelId() {
    return labelId;
  }

  /** What the window is called where there is room to say it, on a screen that has no segments. */
  public int getLongLabelId() {
    return longLabelId;
  }

  /** The moment the window opens, or 0 for every drill ever recorded. */
  public long since() {
    if (daysBack < 0) {
      return 0;
    }
    Calendar day = Calendar.getInstance();
    day.set(Calendar.HOUR_OF_DAY, 0);
    day.set(Calendar.MINUTE, 0);
    day.set(Calendar.SECOND, 0);
    day.set(Calendar.MILLISECOND, 0);
    day.add(Calendar.DAY_OF_YEAR, -daysBack);
    return day.getTimeInMillis();
  }

  /** The window of that name, falling back to all of them for one this version does not know. */
  public static DrillStatsWindow of(String name) {
    for (DrillStatsWindow window : values()) {
      if (window.name().equals(name)) {
        return window;
      }
    }
    return ALL;
  }
}
