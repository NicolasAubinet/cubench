package com.cube.nanotimer.util.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
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
 * (which would read as no solve) or a stub (which would read as very fast). The newest solve is
 * underlined in the accent colour rather than recoloured, since a bar's own colour is already
 * saying something.
 *
 * <p>The colours are the ones the session coloring option already decides, handed in alongside the
 * times; nothing here picks one.
 */
public class SessionBarsView extends View {

  private static final float FLOOR = 0.20f; // the fastest solve still gets a bar to see
  private static final float CEILING = 0.90f; // leaves the DNF's full height bar its own reading
  private static final float BAR_GAP_FRACTION = 0.42f; // of a bar's slot
  private static final float RULE_STROKE_DP = 2f; // the rule under the newest solve
  private static final float RULE_GAP_DP = 1.5f; // between the newest bar's foot and that rule

  /** The window the strip is, matching what the session hands it: a short session part fills it. */
  private static final int WINDOW = 12;

  // The band a filled bar reads best in: enough colour that a near-white mid tone is still a hue,
  // little enough that the ends stay pastel rather than turning into signal green and signal red.
  private static final float FILL_MIN_SATURATION = 0.30f;
  private static final float FILL_MAX_SATURATION = 0.55f;
  private static final float FILL_MIN_VALUE = 0.86f;
  private static final float FILL_MAX_VALUE = 0.97f;
  /** Below this a colour has no hue to keep, and is meant to be neutral. */
  private static final float NEUTRAL_SATURATION = 0.05f;

  private static final float[] hsv = new float[3];

  private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint hollowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint rulePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  private final List<Long> times = new ArrayList<>(); // oldest first, as drawn
  private int[] colors = new int[0];
  private Long average;

  private final float cornerRadius;
  private final float ruleStroke;
  private final float footLift;

  public SessionBarsView(Context context) {
    this(context, null);
  }

  public SessionBarsView(Context context, AttributeSet attrs) {
    super(context, attrs);
    float density = getResources().getDisplayMetrics().density;
    cornerRadius = 1.5f * density;
    ruleStroke = RULE_STROKE_DP * density;
    footLift = ruleStroke + RULE_GAP_DP * density;

    barPaint.setStyle(Paint.Style.FILL);
    hollowPaint.setStyle(Paint.Style.STROKE);
    hollowPaint.setStrokeWidth(1.2f * density);
    hollowPaint.setColor(ContextCompat.getColor(context, R.color.dnf_time));
    linePaint.setStyle(Paint.Style.STROKE);
    linePaint.setStrokeWidth(1f * density);
    linePaint.setColor(ContextCompat.getColor(context, R.color.secondary_text));
    rulePaint.setStyle(Paint.Style.FILL);
    rulePaint.setColor(ContextCompat.getColor(context, R.color.lightblue));
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
    if (count == 0 || getWidth() <= 0 || getHeight() <= footLift) {
      return;
    }
    float floor = getHeight(); // the baseline the bars sit on, and where the rule is drawn
    float slot = getWidth() / (float) Math.max(count, WINDOW);
    float barWidth = slot * (1f - BAR_GAP_FRACTION);

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
      float y = floor * (1f - fraction(average, low, high, true));
      canvas.drawLine(0, y, getWidth(), y, linePaint);
    }

    for (int i = 0; i < count; i++) {
      Long time = times.get(i);
      boolean newest = (i == count - 1);
      float left = i * slot + (slot - barWidth) / 2f;
      float right = left + barWidth;
      boolean dnf = (time == null || time <= 0);
      // Only the newest bar's foot lifts, to sit clear of its own rule; the rest keep the floor.
      float foot = newest ? floor - footLift : floor;
      float top = foot * (1f - (dnf ? 1f : fraction(time, low, high, scaled)));
      if (dnf) {
        float inset = hollowPaint.getStrokeWidth() / 2f;
        canvas.drawRoundRect(left + inset, top + inset, right - inset, foot - inset,
          cornerRadius, cornerRadius, hollowPaint);
      } else {
        barPaint.setColor(asFill(colors[i]));
        canvas.drawRoundRect(left, top, right, foot, cornerRadius, cornerRadius, barPaint);
      }
      if (newest) { // marked rather than recoloured: a bar's colour is information
        canvas.drawRoundRect(left, floor - ruleStroke, right, floor,
          ruleStroke / 2f, ruleStroke / 2f, rulePaint);
      }
    }
  }

  /**
   * The coloring option's colours are picked for text on a dark ground: at the middle of the scale
   * they wash out to near white, and at its ends they are the flat green and red that read on small
   * type. Filled at this size both are wrong, so a bar keeps the hue and takes the pastel band.
   * A colour with no hue to keep is left alone: it is meant to be neutral.
   */
  private static int asFill(int color) {
    Color.colorToHSV(color, hsv);
    if (hsv[1] < NEUTRAL_SATURATION) {
      return color;
    }
    hsv[1] = clamp(hsv[1], FILL_MIN_SATURATION, FILL_MAX_SATURATION);
    hsv[2] = clamp(hsv[2], FILL_MIN_VALUE, FILL_MAX_VALUE);
    return Color.HSVToColor(Color.alpha(color), hsv);
  }

  private static float clamp(float value, float min, float max) {
    return Math.max(min, Math.min(max, value));
  }

  private static float fraction(long time, long low, long high, boolean scaled) {
    if (!scaled) {
      return (FLOOR + CEILING) / 2f;
    }
    return FLOOR + (CEILING - FLOOR) * (time - low) / (float) (high - low);
  }

}
