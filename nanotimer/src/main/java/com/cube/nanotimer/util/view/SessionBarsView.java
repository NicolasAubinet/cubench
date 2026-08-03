package com.cube.nanotimer.util.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;

import java.util.ArrayList;
import java.util.List;

/**
 * The last solves of the session as bars, oldest to newest, with a hairline at the average they
 * are being measured against: beating your recent times is beating the line, without reading a
 * number. A DNF has no duration to draw, so it takes a hollow full height bar rather than a gap
 * (which would read as no solve) or a stub (which would read as very fast).
 *
 * <p>The colours are the ones the session coloring option already decides, handed in alongside the
 * times; nothing here picks one.
 */
public class SessionBarsView extends View {

  private static final float FLOOR = 0.20f; // the fastest solve still gets a bar to see
  private static final float CEILING = 0.90f; // leaves the DNF's full height bar its own reading
  private static final float BAR_GAP_FRACTION = 0.42f; // of a bar's slot
  private static final float TOP_ROOM_FRACTION = 0.13f; // of the height, for the newest solve's dot

  /** The window the strip is, matching what the session hands it: a short session part fills it. */
  private static final int WINDOW = 12;

  private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint hollowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  private final List<Long> times = new ArrayList<>(); // oldest first, as drawn
  private int[] colors = new int[0];
  private Long average;

  private final float cornerRadius;

  public SessionBarsView(Context context) {
    this(context, null);
  }

  public SessionBarsView(Context context, AttributeSet attrs) {
    super(context, attrs);
    float density = getResources().getDisplayMetrics().density;
    cornerRadius = 1.5f * density;

    barPaint.setStyle(Paint.Style.FILL);
    hollowPaint.setStyle(Paint.Style.STROKE);
    hollowPaint.setStrokeWidth(1.2f * density);
    hollowPaint.setColor(ContextCompat.getColor(context, R.color.dnf_time));
    linePaint.setStyle(Paint.Style.STROKE);
    linePaint.setStrokeWidth(1f * density);
    linePaint.setColor(ContextCompat.getColor(context, R.color.secondary_text));
    dotPaint.setStyle(Paint.Style.FILL);
    dotPaint.setColor(ContextCompat.getColor(context, R.color.lightblue));
  }

  /**
   * @param sessionTimes the session's last solves, newest first as the session holds them
   * @param barColors one colour per time, in the same order
   */
  public void setTimes(List<Long> sessionTimes, int[] barColors) {
    times.clear();
    colors = new int[sessionTimes == null ? 0 : sessionTimes.size()];
    if (sessionTimes != null) {
      for (int i = sessionTimes.size() - 1; i >= 0; i--) { // oldest first, as the sparkline reads
        times.add(sessionTimes.get(i));
        colors[times.size() - 1] = barColors[i];
      }
    }
    invalidate();
  }

  /** The average the bars are measured against, or null while there is not one yet. */
  public void setAverage(Long average) {
    this.average = (average != null && average > 0) ? average : null;
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    int count = times.size();
    if (count == 0 || getWidth() <= 0) {
      return;
    }
    float floor = getHeight();
    // The dot above the newest bar needs room the bars cannot also use.
    float usableHeight = floor * (1f - TOP_ROOM_FRACTION);
    float slot = getWidth() / (float) Math.max(count, WINDOW);
    float barWidth = slot * (1f - BAR_GAP_FRACTION);
    float dotRadius = floor * 0.06f;

    long low = Long.MAX_VALUE;
    long high = Long.MIN_VALUE;
    for (Long time : times) {
      if (time != null && time > 0) {
        low = Math.min(low, time);
        high = Math.max(high, time);
      }
    }
    if (average != null) { // the line is part of the range, so it always lands inside the bars
      low = Math.min(low, average);
      high = Math.max(high, average);
    }
    boolean scaled = low < high;

    // Behind the bars, so a bar that beats the average is one that stands above the line.
    if (average != null && scaled) {
      float y = floor - usableHeight * fraction(average, low, high, true);
      canvas.drawLine(0, y, getWidth(), y, linePaint);
    }

    for (int i = 0; i < count; i++) {
      Long time = times.get(i);
      float left = i * slot + (slot - barWidth) / 2f;
      float right = left + barWidth;
      boolean dnf = (time == null || time <= 0);
      float top = floor - usableHeight * (dnf ? 1f : fraction(time, low, high, scaled));
      if (dnf) {
        float inset = hollowPaint.getStrokeWidth() / 2f;
        canvas.drawRoundRect(left + inset, top + inset, right - inset, floor - inset,
          cornerRadius, cornerRadius, hollowPaint);
      } else {
        barPaint.setColor(colors[i]);
        canvas.drawRoundRect(left, top, right, floor, cornerRadius, cornerRadius, barPaint);
      }
      if (i == count - 1) { // the newest is marked rather than recoloured: its colour is information
        canvas.drawCircle((left + right) / 2f, Math.max(dotRadius, top - dotRadius * 1.6f),
          dotRadius, dotPaint);
      }
    }
  }

  private static float fraction(long time, long low, long high, boolean scaled) {
    if (!scaled) {
      return (FLOOR + CEILING) / 2f;
    }
    return FLOOR + (CEILING - FLOOR) * (time - low) / (float) (high - low);
  }

}
