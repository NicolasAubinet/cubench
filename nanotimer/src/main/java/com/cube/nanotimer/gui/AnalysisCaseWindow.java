package com.cube.nanotimer.gui;

import com.cube.nanotimer.R;

/**
 * How many of each case's own last one-algorithm solves the Analysis hub's Cases tab reads.
 *
 * <p>Per case rather than per solve: a case dealt once in seventy would hold no spread, or fall
 * out of the table, in any window the common cases read well over. Capped at each case's own count,
 * so a case met three times is read over those three under every choice.
 */
public enum AnalysisCaseWindow {

  TEN(R.string.analysis_window_10, 10),
  FIFTY(R.string.analysis_window_50, 50),
  HUNDRED(R.string.analysis_window_100, 100);

  private final int labelId;
  private final int perCase;

  AnalysisCaseWindow(int labelId, int perCase) {
    this.labelId = labelId;
    this.perCase = perCase;
  }

  public int getLabelId() {
    return labelId;
  }

  /** How many of each case's most recent one-algorithm solves are read. */
  public int perCase() {
    return perCase;
  }

  /** The window at that ordinal, falling back to the default for one this version does not have. */
  public static AnalysisCaseWindow of(int ordinal) {
    return ordinal < 0 || ordinal >= values().length ? FIFTY : values()[ordinal];
  }
}
