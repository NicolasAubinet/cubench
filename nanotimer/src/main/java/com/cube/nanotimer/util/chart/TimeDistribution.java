package com.cube.nanotimer.util.chart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Solve times counted into buckets, for a histogram of how the times are spread.
 *
 * <p>The bucket width is picked so the times fall into roughly {@link #TARGET_BUCKETS} bars, rounded
 * up to a width that reads well on an axis. A couple of disaster solves would otherwise stretch the
 * range until every real solve landed in the same bar, so the top of the range comes from a
 * percentile and everything above it is pooled into a final overflow bucket.
 */
public class TimeDistribution {

  private static final int TARGET_BUCKETS = 15;
  private static final int MAX_BUCKETS = 24;
  /** Under this many solves a percentile says little, so the whole range is drawn. */
  private static final int MIN_SOLVES_TO_CLIP = 20;
  private static final double CLIP_PERCENTILE = 0.95;

  /** Widths that read well on an axis, in ms. All divide a minute, so bounds land on round times. */
  private static final long[] NICE_WIDTHS =
     { 10, 20, 50, 100, 200, 250, 500, 1000, 2000, 5000, 10000, 15000, 30000, 60000, 120000, 300000, 600000 };

  private final List<Bucket> buckets;
  private final long bucketWidthMs;

  private TimeDistribution(List<Bucket> buckets, long bucketWidthMs) {
    this.buckets = buckets;
    this.bucketWidthMs = bucketWidthMs;
  }

  /**
   * Buckets the given times. Null and non-positive values (DNFs carry -1) are left out, so an
   * all-DNF period gives an empty distribution rather than a bar at zero.
   */
  public static TimeDistribution of(List<Long> times) {
    List<Long> sorted = new ArrayList<Long>();
    if (times != null) {
      for (Long time : times) {
        if (time != null && time > 0) {
          sorted.add(time);
        }
      }
    }
    if (sorted.isEmpty()) {
      return new TimeDistribution(new ArrayList<Bucket>(), 0);
    }
    Collections.sort(sorted);

    long min = sorted.get(0);
    long top = sorted.get(sorted.size() - 1);
    if (sorted.size() >= MIN_SOLVES_TO_CLIP) {
      int clipIndex = (int) Math.ceil(sorted.size() * CLIP_PERCENTILE) - 1;
      top = sorted.get(Math.max(0, Math.min(sorted.size() - 1, clipIndex)));
    }

    long width = niceWidth(top - min);
    long start = (min / width) * width;
    int bucketCount = (int) Math.min(MAX_BUCKETS, ((top - start) / width) + 1);

    List<Bucket> buckets = new ArrayList<Bucket>();
    for (int i = 0; i < bucketCount; i++) {
      buckets.add(new Bucket(start + i * width, width, false));
    }
    long overflowFrom = start + (long) bucketCount * width;
    Bucket overflow = null;
    for (Long time : sorted) {
      if (time >= overflowFrom) {
        if (overflow == null) {
          overflow = new Bucket(overflowFrom, width, true);
        }
        overflow.count++;
      } else {
        buckets.get((int) ((time - start) / width)).count++;
      }
    }
    if (overflow != null) {
      buckets.add(overflow);
    }
    return new TimeDistribution(buckets, width);
  }

  /** The buckets in ascending time order, the overflow one (if any) last. */
  public List<Bucket> getBuckets() {
    return buckets;
  }

  public long getBucketWidthMs() {
    return bucketWidthMs;
  }

  private static long niceWidth(long range) {
    long raw = Math.max(1, range / TARGET_BUCKETS);
    for (long width : NICE_WIDTHS) {
      if (width >= raw) {
        return width;
      }
    }
    long biggest = NICE_WIDTHS[NICE_WIDTHS.length - 1];
    return ((raw + biggest - 1) / biggest) * biggest;
  }

  /** One bar: how many solves landed between {@code lowerMs} and the next bucket's bound. */
  public static class Bucket {

    private final long lowerMs;
    private final long widthMs;
    private final boolean overflow;
    private int count;

    private Bucket(long lowerMs, long widthMs, boolean overflow) {
      this.lowerMs = lowerMs;
      this.widthMs = widthMs;
      this.overflow = overflow;
    }

    public long getLowerMs() {
      return lowerMs;
    }

    public int getCount() {
      return count;
    }

    /** Whether this bucket pools everything above the drawn range. */
    public boolean isOverflow() {
      return overflow;
    }

    /** The bucket's lower bound, at the precision the bucket width is worth (ex: "12", "12.5"). */
    public String getLabel() {
      String label = formatBound(lowerMs, widthMs);
      return overflow ? label + "+" : label;
    }

    private static String formatBound(long ms, long widthMs) {
      long minutes = ms / 60000;
      if (widthMs >= 1000) {
        long seconds = (ms % 60000) / 1000;
        return minutes > 0 ? minutes + ":" + String.format(Locale.US, "%02d", seconds) : String.valueOf(seconds);
      }
      double seconds = (ms % 60000) / 1000d;
      return minutes > 0 ? minutes + ":" + String.format(Locale.US, "%04.1f", seconds)
         : String.format(Locale.US, "%.1f", seconds);
    }

  }

}
