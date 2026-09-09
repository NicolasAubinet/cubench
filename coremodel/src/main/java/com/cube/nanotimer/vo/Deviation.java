package com.cube.nanotimer.vo;

/**
 * How far a set of times swings around its own mean, from the three sums a tally already keeps.
 *
 * <p>Shared because two tables show this figure in the same column, one over solves and one over
 * drill reps, and a reader crossing between them is entitled to read the heading once.
 */
public final class Deviation {

  private Deviation() {
  }

  /**
   * The standard deviation over every time in the tally, in milliseconds.
   *
   * @param sumOfSquares each time multiplied by itself, added up
   */
  public static long of(int count, long totalMs, double sumOfSquares) {
    if (count == 0) {
      return 0;
    }
    double mean = (double) totalMs / count;
    double variance = sumOfSquares / count - mean * mean;
    return variance <= 0 ? 0 : (long) Math.sqrt(variance);
  }
}
