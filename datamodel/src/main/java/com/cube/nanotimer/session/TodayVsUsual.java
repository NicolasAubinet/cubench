package com.cube.nanotimer.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Today's solves against the usual ones, for the evening recap: the whole solve and each CFOP step,
 * keeping the one that was clearly faster today. Clear means at least {@link #MIN_TODAY} solves
 * today and {@link #MIN_USUAL} before, a gain of at least {@link #MIN_RATIO} of the usual mean and
 * {@link #MIN_GAIN_MS}, and two standard errors of the gap: a day that was merely lucky says
 * nothing.
 */
public final class TodayVsUsual {

  /** The code standing for the whole solve, beside the step codes. */
  public static final String SOLVE = "solve";
  /** How many solves before today make the usual. */
  public static final int USUAL_WINDOW = 100;
  static final int MIN_TODAY = 5;
  static final int MIN_USUAL = 20;
  static final double MIN_RATIO = 0.03;
  static final long MIN_GAIN_MS = 100;
  private static final double MIN_Z = 2;
  /** The share of each end left out, so one popped solve does not move the usual. */
  private static final double TRIM = 0.05;

  /** One figure that was faster today. */
  public static final class Gain {
    private final String code;
    private final long todayMs;
    private final long usualMs;

    Gain(String code, long todayMs, long usualMs) {
      this.code = code;
      this.todayMs = todayMs;
      this.usualMs = usualMs;
    }

    /** {@link #SOLVE} or a step code such as {@code cross}. */
    public String getCode() {
      return code;
    }

    public long getTodayMs() {
      return todayMs;
    }

    public long getUsualMs() {
      return usualMs;
    }

    public long getGainMs() {
      return usualMs - todayMs;
    }
  }

  private TodayVsUsual() {
  }

  /**
   * The figure most clearly faster today, by share of its usual, or null when none was.
   *
   * @param today each figure's times today, keyed by {@link #SOLVE} or step code
   * @param usual the same figures over the solves before today
   */
  public static Gain best(Map<String, List<Long>> today, Map<String, List<Long>> usual) {
    Gain best = null;
    for (Map.Entry<String, List<Long>> entry : today.entrySet()) {
      List<Long> before = usual.get(entry.getKey());
      Gain gain = clear(entry.getKey(), entry.getValue(),
          before == null ? Collections.<Long>emptyList() : before);
      if (gain != null && (best == null
          || gain.getGainMs() / (double) gain.getUsualMs()
              > best.getGainMs() / (double) best.getUsualMs())) {
        best = gain;
      }
    }
    return best;
  }

  static Gain clear(String code, List<Long> todayTimes, List<Long> usualTimes) {
    if (todayTimes.size() < MIN_TODAY || usualTimes.size() < MIN_USUAL) {
      return null;
    }
    List<Long> today = trimmed(todayTimes);
    List<Long> usual = trimmed(usualTimes);
    double todayMean = mean(today);
    double usualMean = mean(usual);
    double gap = usualMean - todayMean;
    if (gap < MIN_GAIN_MS || gap < usualMean * MIN_RATIO) {
      return null;
    }
    double error = Math.sqrt(variance(today, todayMean) / today.size()
        + variance(usual, usualMean) / usual.size());
    if (gap < MIN_Z * error) {
      return null;
    }
    return new Gain(code, Math.round(todayMean), Math.round(usualMean));
  }

  private static List<Long> trimmed(List<Long> times) {
    List<Long> sorted = new ArrayList<Long>(times);
    Collections.sort(sorted);
    int cut = (int) (sorted.size() * TRIM);
    return sorted.subList(cut, sorted.size() - cut);
  }

  private static double mean(List<Long> times) {
    double sum = 0;
    for (Long time : times) {
      sum += time;
    }
    return sum / times.size();
  }

  private static double variance(List<Long> times, double mean) {
    double sum = 0;
    for (Long time : times) {
      sum += (time - mean) * (time - mean);
    }
    return sum / (times.size() - 1);
  }
}
