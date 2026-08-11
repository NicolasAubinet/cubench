package com.cube.nanotimer.util.helper;

import android.content.Context;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;
import com.cube.nanotimer.vo.SolveTime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Maps a solve time to a color on the green→grey→red gradient (fast→median→slow).
 * The grey pivot is the median (interpolated on even counts so it never lands on an end);
 * the fast/slow ends are the 5th/95th percentiles so outliers can't stretch the scale, or
 * the raw min/max when {@code trimOutliers} is off. Rebuild via {@link #setTimes} per data
 * load; {@link #colorFor} is O(1) per row.
 *
 * <p>The three anchors sit at roughly one brightness, so what separates a time from the median is
 * its colour and not its lightness. A ramp through white did the opposite: the loudest mark on a
 * strip belonged to the times nearest average, and the best and the worst were the quietest.</p>
 */
public class TimeColorScale {

  private static final float P_FAST = 0.05f; // green anchor: 5th percentile (fast end)
  private static final float P_SLOW = 0.95f; // red anchor: 95th percentile (slow end)

  private final int colorFast;    // green
  private final int colorSlow;    // red
  private final int colorNeutral; // grey (around the median)
  private final int colorDnf;     // dnf_time (darker grey)

  // Gradient anchors over the loaded window; valid only when hasRange is true.
  private boolean hasRange;
  private long fast;   // fast (green) end
  private long median; // neutral (grey) pivot
  private long slow;   // slow (red) end

  public TimeColorScale(Context context) {
    colorFast = ContextCompat.getColor(context, R.color.time_good);
    colorSlow = ContextCompat.getColor(context, R.color.time_bad);
    colorNeutral = ContextCompat.getColor(context, R.color.time_neutral);
    colorDnf = ContextCompat.getColor(context, R.color.dnf_time);
  }

  /** Recomputes the anchors from the given times (DNFs ignored), trimming outliers via percentiles. */
  public void setTimes(List<Long> times) {
    setTimes(times, true);
  }

  /**
   * Recomputes the anchors (DNFs ignored); clears them if unusable. {@code trimOutliers} pulls
   * the ends to the 5th/95th percentiles; when false the raw min/max anchor the gradient.
   */
  public void setTimes(List<Long> times, boolean trimOutliers) {
    hasRange = false;
    if (times == null) {
      return;
    }
    List<Long> sorted = new ArrayList<>(times.size());
    for (Long time : times) {
      if (time != null && time >= 0) { // skip DNFs
        sorted.add(time);
      }
    }
    if (sorted.size() < 2) {
      return;
    }
    Collections.sort(sorted);
    if (trimOutliers) {
      fast = percentile(sorted, P_FAST);
      slow = percentile(sorted, P_SLOW);
    } else {
      fast = sorted.get(0);
      slow = sorted.get(sorted.size() - 1);
    }
    median = median(sorted);
    // Keep the neutral pivot strictly inside the gradient so neither end maps to it.
    median = Math.max(fast, Math.min(slow, median));
    hasRange = fast < slow;
  }

  /** Color for a solve: green near the fast end, grey around the median, red near the slow end. */
  public int colorFor(SolveTime st) {
    return colorFor(st.getTime(), st.isDNF());
  }

  /** Color for a raw time; DNFs (dnf == true, or a negative time) render gray. */
  public int colorFor(long time, boolean dnf) {
    if (dnf || time < 0) {
      return colorDnf;
    }
    if (!hasRange) {
      return colorNeutral;
    }
    if (time <= median) {
      float p = (median == fast) ? 1f : (float) (time - fast) / (median - fast);
      return GUIUtils.getColorCodeBetween(colorFast, colorNeutral, clamp01(p));
    }
    float p = (slow == median) ? 1f : (float) (time - median) / (slow - median);
    return GUIUtils.getColorCodeBetween(colorNeutral, colorSlow, clamp01(p));
  }

  private static long percentile(List<Long> sorted, float p) {
    return sorted.get(Math.round(p * (sorted.size() - 1)));
  }

  // Median of a sorted list, averaging the two middle values on even counts.
  private static long median(List<Long> sorted) {
    int n = sorted.size();
    int mid = n / 2;
    if (n % 2 == 1) {
      return sorted.get(mid);
    }
    return (sorted.get(mid - 1) + sorted.get(mid)) / 2;
  }

  private static float clamp01(float v) {
    return Math.max(0f, Math.min(1f, v));
  }
}
