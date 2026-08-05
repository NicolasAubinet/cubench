package com.cube.nanotimer.util.view;

import android.content.Context;
import com.cube.nanotimer.R;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.vo.SolveAverages;
import com.cube.nanotimer.vo.SolveType;
import java.util.ArrayList;
import java.util.List;

/**
 * A statistic one of the history card's three cells can be set to. Each entry says how it is
 * labelled, how it is read out of a {@link SolveAverages} and which solve types it means anything
 * for — so a cell is only ever "which of these", and offering a new one is an entry here.
 *
 * <p>The order they are declared in is the order the picker lists them: shortest window first, the
 * lifetime figures after them, and the blind-only success rates last.
 */
public enum HeroStat {

  MO3(R.string.mo3_label),
  AO5(R.string.ao5_label),
  AO12(R.string.ao12_label),
  AO50(R.string.ao50_label),
  AO100(R.string.ao100_label),
  MEAN(R.string.mean_label),
  PB(R.string.record_label_lifetime),
  ACC12(R.string.acc12_label),
  ACC50(R.string.acc50_label),
  ACC100(R.string.acc100_label),
  ACC_ALL(R.string.acc_label);

  /**
   * What marks an average a blind solve type takes over its successes only. A sighted average is
   * over the last N solves whatever they were, so the two are not the same statistic and must not
   * wear the same label.
   */
  public static final String SUCCESSES_MARK = "*";

  private final int labelRes;

  HeroStat(int labelRes) {
    this.labelRes = labelRes;
  }

  /** The statistics worth offering for this solve type, in the order the picker lists them. */
  public static List<HeroStat> optionsFor(SolveType solveType) {
    List<HeroStat> options = new ArrayList<HeroStat>();
    for (HeroStat stat : values()) {
      if (stat.appliesTo(solveType)) {
        options.add(stat);
      }
    }
    return options;
  }

  /** What a cell shows until the user picks something else. */
  public static HeroStat defaultFor(int cell, boolean blind) {
    switch (cell) {
      case 0:
        return blind ? AO12 : AO5;
      case 1:
        return blind ? ACC50 : AO12;
      default:
        return PB;
    }
  }

  /**
   * A success rate is only a statistic where missing is part of the game, and a blind attempt is
   * counted in threes rather than fives — so neither side is offered the other's window.
   */
  public boolean appliesTo(SolveType solveType) {
    boolean blind = solveType.isBlind();
    switch (this) {
      case AO5:
        return !blind;
      case ACC12:
      case ACC50:
      case ACC100:
      case ACC_ALL:
        return blind;
      default:
        return true;
    }
  }

  /** The cell's key, marked where the average is over successes only. */
  public String label(Context context, SolveType solveType) {
    String label = context.getString(labelRes);
    return solveType.isBlind() && overSuccessesOnly() ? label + SUCCESSES_MARK : label;
  }

  /** The cell's value, or N/A where there are not enough solves for it yet. */
  public String value(Context context, SolveAverages averages) {
    String na = context.getString(R.string.NA);
    if (averages == null) {
      return na;
    }
    switch (this) {
      case ACC12:
        return FormatterService.INSTANCE.formatPercentage(averages.getAccuracyOf12(), na);
      case ACC50:
        return FormatterService.INSTANCE.formatPercentage(averages.getAccuracyOf50(), na);
      case ACC100:
        return FormatterService.INSTANCE.formatPercentage(averages.getAccuracyOf100(), na);
      case ACC_ALL:
        return FormatterService.INSTANCE.formatPercentage(averages.getLifetimeAccuracy(), na);
      default:
        return FormatterService.INSTANCE.formatSolveTime(time(averages), na);
    }
  }

  private Long time(SolveAverages averages) {
    switch (this) {
      case MO3:
        return averages.getMeanOf3();
      case AO5:
        return averages.getAvgOf5();
      case AO12:
        return averages.getAvgOf12();
      case AO50:
        return averages.getAvgOf50();
      case AO100:
        return averages.getAvgOf100();
      case MEAN:
        return averages.getAvgOfLifetime();
      default:
        return averages.getBestOfLifetime();
    }
  }

  // The Mo3 is not one of these: a single DNF makes it a DNF, the way a blind mean is meant to work.
  private boolean overSuccessesOnly() {
    return this == AO12 || this == AO50 || this == AO100;
  }
}
