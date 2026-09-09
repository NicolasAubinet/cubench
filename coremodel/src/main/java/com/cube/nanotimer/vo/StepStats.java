package com.cube.nanotimer.vo;

import java.io.Serializable;

/**
 * What one step code has been costing over a stretch of solves. The code is a family on its own
 * ("cross") or a family and its case ("pll_gb").
 *
 * <p>Sums rather than means, so two of these add up: a family is its cases put together, and a mean
 * of means would weigh a case seen twice as heavily as one seen forty times.
 */
public class StepStats implements Serializable {

  private final String code;
  private final int count;
  private final long totalMs;
  private final long recognitionMs;
  private final long bestMs;
  private final long worstMs;
  private final double sumOfSquares; // of the step times, for the spread

  public StepStats(String code, int count, long totalMs, long recognitionMs, long bestMs,
      long worstMs, double sumOfSquares) {
    this.code = code;
    this.count = count;
    this.totalMs = totalMs;
    this.recognitionMs = recognitionMs;
    this.bestMs = bestMs;
    this.worstMs = worstMs;
    this.sumOfSquares = sumOfSquares;
  }

  /** The two tallies as one, under the given code. */
  public static StepStats merge(String code, StepStats a, StepStats b) {
    if (a == null) {
      return b == null ? null : new StepStats(code, b.count, b.totalMs, b.recognitionMs, b.bestMs,
          b.worstMs, b.sumOfSquares);
    }
    if (b == null) {
      return new StepStats(code, a.count, a.totalMs, a.recognitionMs, a.bestMs, a.worstMs,
          a.sumOfSquares);
    }
    return new StepStats(code, a.count + b.count, a.totalMs + b.totalMs,
        a.recognitionMs + b.recognitionMs, Math.min(a.bestMs, b.bestMs),
        Math.max(a.worstMs, b.worstMs), a.sumOfSquares + b.sumOfSquares);
  }

  public String getCode() {
    return code;
  }

  /** How many times the step was timed. Every mean here is only worth as much as this number. */
  public int getCount() {
    return count;
  }

  public long getMeanMs() {
    return count == 0 ? 0 : totalMs / count;
  }

  public long getMeanRecognitionMs() {
    return count == 0 ? 0 : recognitionMs / count;
  }

  public long getMeanExecutionMs() {
    return count == 0 ? 0 : (totalMs - recognitionMs) / count;
  }

  public long getBestMs() {
    return bestMs;
  }

  /** The slowest the step ever went in the window, which is what a mean cannot say on its own. */
  public long getWorstMs() {
    return worstMs;
  }

  /**
   * How much of the step is spent finding the answer rather than turning, from 0 to 1. A step timed
   * from its own first move has nowhere to put recognition and reads 0, which means unmeasured
   * rather than instant: CFOP's cross is the case, since the solve starts when it does.
   */
  public double getRecognitionShare() {
    return totalMs == 0 ? 0 : (double) recognitionMs / totalMs;
  }

  /** How far the step swings around its mean: an uneven case is a different complaint from a slow one. */
  public long getStdDevMs() {
    return Deviation.of(count, totalMs, sumOfSquares);
  }
}
