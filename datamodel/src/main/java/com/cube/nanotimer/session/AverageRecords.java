package com.cube.nanotimer.session;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the solves whose average is better than every earlier average of the same size. Call
 * {@link #next} once per solve, oldest first, with the solve's averages always in the same order.
 *
 * <p>An average only counts as a record after {@link #WARM_UP} earlier averages of its size, like
 * the single PB waits for 12 solves: otherwise almost every one of the first averages would be
 * flagged. A DNF or missing average (null, zero or negative) is ignored and doesn't count toward
 * the warm-up.
 */
public final class AverageRecords {

  public static final int WARM_UP = 12;

  private final long[] best;
  private final int[] seen;

  public AverageRecords(int sizes) {
    best = new long[sizes];
    seen = new int[sizes];
  }

  /** Starts from earlier solves: for each size, their best average and how many valid averages they had. */
  public AverageRecords(long[] best, int[] seen) {
    this.best = best.clone();
    this.seen = seen.clone();
  }

  /** Returns the indexes in {@code averages} that are new bests, and updates the bests. */
  public List<Integer> next(Long... averages) {
    List<Integer> records = new ArrayList<>();
    for (int i = 0; i < best.length && i < averages.length; i++) {
      Long average = averages[i];
      if (average == null || average <= 0) {
        continue;
      }
      if (seen[i] == 0 || average < best[i]) {
        if (seen[i] >= WARM_UP) {
          records.add(i);
        }
        best[i] = average;
      }
      seen[i]++;
    }
    return records;
  }
}
