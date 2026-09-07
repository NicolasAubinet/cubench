package com.cube.nanotimer.gui;

import com.cube.nanotimer.R;

/**
 * How many solves back the Analysis hub is reading.
 *
 * <p>A count of solves rather than a stretch of time, unlike the drill screens: a case turns up in
 * one solve in twenty, so "this week" is a different amount of evidence for one solver than for
 * another, and the figures here are only worth what their counts are.
 */
public enum AnalysisWindow {

  FIFTY(R.string.analysis_window_50, 50),
  /** Where the hub opens: enough of a window for the cases to have counts worth reading. */
  HUNDRED(R.string.analysis_window_100, 100),
  ALL(R.string.analysis_window_all, Integer.MAX_VALUE);

  private final int labelId;
  private final int solves;

  AnalysisWindow(int labelId, int solves) {
    this.labelId = labelId;
    this.solves = solves;
  }

  public int getLabelId() {
    return labelId;
  }

  /** How many of the most recent solves the figures are read from. */
  public int solves() {
    return solves;
  }

  /** The window at that ordinal, falling back to the default for one this version does not have. */
  public static AnalysisWindow of(int ordinal) {
    return ordinal < 0 || ordinal >= values().length ? HUNDRED : values()[ordinal];
  }
}
