package com.cube.nanotimer.vo.drill;

import com.cube.nanotimer.vo.Deviation;

/**
 * What one case has cost over a stretch of drills: how often it came up, what it averaged, how far
 * it swung either side of that, and the two ends it reached.
 *
 * <p>Kept apart from {@link com.cube.nanotimer.vo.StepStats}, which is the same tally for a coach
 * reading drills and solves against each other, because a drill rep and a step of a solve are not
 * the same measurement and merging the two would let one window answer for the other.
 *
 * <p>Sums rather than means, so the figures of two windows could be added without a mean of means
 * weighting a case seen twice as heavily as one seen forty times.
 *
 * <p>Only reps that measured the case are in here: one given up on was never finished, and one
 * where the algorithm was looked up was finished with the answer in front of the user.
 */
public class DrillCaseStats {

  private final String caseCode;
  private final int count;
  private final long totalMs;
  private final long recognitionMs;
  private final long bestMs;
  private final long worstMs;
  private final double sumOfSquares; // of the whole reps, for the spread

  /**
   * @param caseCode the case as a solve records it, {@code oll_21} or {@code pll_ga}
   * @param totalMs recognition and execution together, over every rep
   * @param bestMs the quickest rep, and {@code worstMs} the slowest, both of them whole reps
   * @param sumOfSquares each rep multiplied by itself, added up
   */
  public DrillCaseStats(String caseCode, int count, long totalMs, long recognitionMs, long bestMs,
      long worstMs, double sumOfSquares) {
    this.caseCode = caseCode;
    this.count = count;
    this.totalMs = totalMs;
    this.recognitionMs = recognitionMs;
    this.bestMs = bestMs;
    this.worstMs = worstMs;
    this.sumOfSquares = sumOfSquares;
  }

  public String getCaseCode() {
    return caseCode;
  }

  /** How many reps are behind the figures. Every mean here is only worth as much as this number. */
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

  public long getWorstMs() {
    return worstMs;
  }

  public long getTotalMs() {
    return totalMs;
  }

  public long getRecognitionMs() {
    return recognitionMs;
  }

  /** How far the case swings around its mean: an uneven case is a different complaint from a slow one. */
  public long getStdDevMs() {
    return Deviation.of(count, totalMs, sumOfSquares);
  }

  /** How much of the case is spent finding the answer rather than turning, from 0 to 1. */
  public double getRecognitionShare() {
    return totalMs == 0 ? 0 : (double) recognitionMs / totalMs;
  }
}
