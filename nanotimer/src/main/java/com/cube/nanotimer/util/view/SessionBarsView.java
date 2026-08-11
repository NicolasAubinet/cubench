package com.cube.nanotimer.util.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.helper.GUIUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * The last solves of the session as bars, oldest to newest, with a hairline at the average they
 * are being measured against: beating your recent times is beating the line, without reading a
 * number. A DNF is hollow rather than filled, and is drawn at the height of the time it replaced,
 * so a DNF that was on for a good solve says so. One with nothing to restore — every DNF recorded
 * before that was kept — has no duration to draw and takes the full height instead, which reads as
 * no solve rather than as a very fast one. The newest solve is underlined in the accent colour
 * rather than recoloured, since a bar's own colour is already saying something.
 *
 * <p>Two of the bars are named: the fastest in green and the slowest in red, so the strip carries a
 * scale and not only a shape. A name sits on the head of the bar it belongs to, with a short lead
 * down to it, because the one thing it has to say beyond the time itself is which bar it is the
 * time of. It cannot be made to fit inside one bar's width: ten minutes is eight characters over a
 * bar a fifth as wide. So it is carried on a plate of the card's own colour, which clears the
 * neighbours it crosses instead of printing over them. Room for the tallest bar's plate is kept
 * above the bars whether or not anything is said in it, so the strip does not change shape when
 * the second solve lands.
 *
 * <p>The two sit at their own bars' heights, which normally holds them well clear of each other.
 * They only come level when the average has pulled the range wide enough to squash every bar to
 * much the same height, which is what happens for the first solves after a session is reset.
 *
 * <p>The colours are the ones the session coloring option already decides, handed in alongside the
 * times; nothing here picks one.
 */
public class SessionBarsView extends View {

  private static final float FLOOR = 0.20f; // the fastest solve still gets a bar to see
  private static final float CEILING = 0.90f; // leaves the DNF's full height bar its own reading
  private static final float BAR_GAP_FRACTION = 0.28f; // of a bar's slot
  private static final float RULE_STROKE_DP = 2f; // the rule under the newest solve
  private static final float RULE_GAP_DP = 1.5f; // between the newest bar's foot and that rule

  /** The window the strip is, matching what the session hands it: a short session part fills it. */
  private static final int WINDOW = 12;

  // A name is read against the bars rather than instead of them, so it is set well below the size
  // the session grid gives a time. The rest are of the name's own size, so they follow it.
  private static final float NAME_TEXT_FRACTION = 0.115f; // of the strip's height
  private static final float NAME_PAD_X_FRACTION = 0.34f; // the plate around the name
  private static final float NAME_PAD_Y_FRACTION = 0.30f;
  private static final float NAME_LEAD_FRACTION = 0.36f; // the plate's foot to its bar's head
  private static final float NAME_SPACING_FRACTION = 0.5f; // between two plates sharing a line

  private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint hollowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint rulePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint namePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint platePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint leadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Rect digitBounds = new Rect();

  private final List<Long> times = new ArrayList<>(); // oldest first, as drawn
  private final List<Long> dnfTimes = new ArrayList<>(); // aligned with times, mostly nulls
  private int[] colors = new int[0];
  private Long average;

  // The two named bars, found and written out once a load: formatting reads the precision option.
  private int best = -1;
  private int worst = -1;
  private String bestName;
  private String worstName;

  private final float cornerRadius;
  private final float ruleStroke;
  private final float footLift;
  private final int bestColor;
  private final int worstColor;

  // A plate's shape, which the whole draw shares: measured once a draw, before the bars need it.
  private float plateHeight;
  private float plateLead;
  private float digitHeight;

  public SessionBarsView(Context context) {
    this(context, null);
  }

  public SessionBarsView(Context context, AttributeSet attrs) {
    super(context, attrs);
    float density = getResources().getDisplayMetrics().density;
    cornerRadius = 2f * density;
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
    namePaint.setStyle(Paint.Style.FILL);
    namePaint.setTypeface(Typeface.create(GUIUtils.appFont(context), Typeface.BOLD));
    namePaint.setFontFeatureSettings("tnum"); // tabular figures: the two names keep one rhythm
    platePaint.setStyle(Paint.Style.FILL);
    // The card's own colour: on the ground the plate is not there at all, and over a bar it reads
    // as a gap in the strip rather than as a box laid on top of it.
    platePaint.setColor(ContextCompat.getColor(context, R.color.timercardbg));
    leadPaint.setStyle(Paint.Style.STROKE);
    leadPaint.setStrokeWidth(1.5f * density);
    leadPaint.setStrokeCap(Paint.Cap.ROUND);
    bestColor = ContextCompat.getColor(context, R.color.session_best);
    worstColor = ContextCompat.getColor(context, R.color.session_worst);
  }

  /**
   * @param sessionTimes the session's last solves, newest first as the session holds them
   * @param barColors one colour per time, in the same order
   * @param sessionDnfTimes the time each DNF replaced, in the same order, or null for none known
   */
  public void setTimes(List<Long> sessionTimes, int[] barColors, List<Long> sessionDnfTimes) {
    times.clear();
    dnfTimes.clear();
    colors = new int[sessionTimes == null ? 0 : sessionTimes.size()];
    if (sessionTimes != null) {
      for (int i = sessionTimes.size() - 1; i >= 0; i--) { // oldest first, as the sparkline reads
        times.add(sessionTimes.get(i));
        dnfTimes.add((sessionDnfTimes != null && i < sessionDnfTimes.size())
          ? sessionDnfTimes.get(i) : null);
        colors[times.size() - 1] = barColors[i];
      }
    }
    findNamed();
    invalidate();
  }

  /**
   * The fastest and the slowest of the loaded times, and their names. A single solve, or a window
   * that is all the same time, has no two ends to tell apart and is left unnamed.
   */
  private void findNamed() {
    best = -1;
    worst = -1;
    bestName = null;
    worstName = null;
    for (int i = 0; i < times.size(); i++) {
      Long time = times.get(i);
      if (time == null || time <= 0) { // a DNF has no duration of its own to be an end of
        continue;
      }
      if (best < 0 || time < times.get(best)) {
        best = i;
      }
      if (worst < 0 || time > times.get(worst)) {
        worst = i;
      }
    }
    if (best >= 0 && times.get(best).longValue() != times.get(worst).longValue()) {
      bestName = FormatterService.INSTANCE.formatSolveTime(times.get(best));
      worstName = FormatterService.INSTANCE.formatSolveTime(times.get(worst));
    }
  }

  /**
   * The average the bars are measured against. Null until there are twelve solves for one, and the
   * window's own mean stands in until then, so the line is there from the second solve on.
   */
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
    measurePlate();
    float head = plateHeight + plateLead; // what the tallest bar's own plate needs above it
    float floor = getHeight(); // the baseline the bars sit on, and where the rule is drawn
    if (floor - head <= footLift) {
      return;
    }
    float slot = getWidth() / (float) Math.max(count, WINDOW);
    float barWidth = slot * (1f - BAR_GAP_FRACTION);

    Long line = (average != null) ? average : meanOfWindow();

    long low = Long.MAX_VALUE;
    long high = Long.MIN_VALUE;
    if (best >= 0) {
      low = times.get(best);
      high = times.get(worst);
    }
    if (line != null) { // the line is part of the range, so it always lands inside the bars
      low = Math.min(low, line);
      high = Math.max(high, line);
    }
    boolean scaled = low < high;

    // Behind the bars, so a bar that beats the average is one that stands above the line.
    if (line != null && scaled) {
      float y = head + (floor - head) * (1f - fraction(line, low, high, true));
      canvas.drawLine(0, y, getWidth(), y, linePaint);
    }

    float bestTop = 0f;
    float worstTop = 0f;
    for (int i = 0; i < count; i++) {
      Long time = times.get(i);
      boolean newest = (i == count - 1);
      float left = i * slot + (slot - barWidth) / 2f;
      float right = left + barWidth;
      boolean dnf = (time == null || time <= 0);
      // A DNF is drawn at the time it replaced; with none to draw it takes the whole height. The
      // range above is the successes' own, so a DNF slower than all of them is clamped to the top
      // rather than flattening every real solve to fit it in.
      Long height = dnf ? dnfTimes.get(i) : time;
      // Only the newest bar's foot lifts, to sit clear of its own rule; the rest keep the floor.
      float foot = newest ? floor - footLift : floor;
      float span = foot - head;
      float top = head + span * (1f - (height == null ? 1f : fraction(height, low, high, scaled)));
      if (i == best) {
        bestTop = top;
      }
      if (i == worst) {
        worstTop = top;
      }
      if (dnf) {
        float inset = hollowPaint.getStrokeWidth() / 2f;
        canvas.drawRoundRect(left + inset, top + inset, right - inset, foot - inset,
          cornerRadius, cornerRadius, hollowPaint);
      } else {
        barPaint.setColor(colors[i]);
        canvas.drawRoundRect(left, top, right, foot, cornerRadius, cornerRadius, barPaint);
      }
      if (newest) { // marked rather than recoloured: a bar's colour is information
        canvas.drawRoundRect(left, floor - ruleStroke, right, floor,
          ruleStroke / 2f, ruleStroke / 2f, rulePaint);
      }
    }

    if (bestName != null) {
      drawNames(canvas, slot, bestTop, worstTop);
    }
  }

  /** The shape a name is carried on, from the size the strip's own height gives its text. */
  private void measurePlate() {
    float size = getHeight() * NAME_TEXT_FRACTION;
    namePaint.setTextSize(size);
    namePaint.getTextBounds("0", 0, 1, digitBounds); // a figure's own ink: times have no descender
    digitHeight = digitBounds.height();
    plateHeight = digitHeight + 2f * size * NAME_PAD_Y_FRACTION;
    plateLead = size * NAME_LEAD_FRACTION;
  }

  // A pair that has come level is opened out around its own middle; the lead keeps it on its bar.
  private void drawNames(Canvas canvas, float slot, float bestTop, float worstTop) {
    float padding = 2f * namePaint.getTextSize() * NAME_PAD_X_FRACTION;
    float bestWidth = namePaint.measureText(bestName) + padding;
    float worstWidth = namePaint.measureText(worstName) + padding;
    float bestPoint = centreOf(best, slot);
    float worstPoint = centreOf(worst, slot);
    float bestX = bestPoint - bestWidth / 2f;
    float worstX = worstPoint - worstWidth / 2f;

    if (Math.abs(bestTop - worstTop) < plateHeight + plateLead) { // the two share a line
      float spacing = namePaint.getTextSize() * NAME_SPACING_FRACTION;
      boolean bestFirst = (bestX <= worstX);
      float leftX = bestFirst ? bestX : worstX;
      float rightX = bestFirst ? worstX : bestX;
      float leftWidth = bestFirst ? bestWidth : worstWidth;
      float rightWidth = bestFirst ? worstWidth : bestWidth;
      float overlap = leftX + leftWidth + spacing - rightX;
      if (overlap > 0) { // each gives way as much as the other, so neither is sent off on its own
        leftX -= overlap / 2f;
        rightX += overlap / 2f;
      }
      leftX = Math.max(0, leftX); // held inside the strip as a pair, so the gap survives the edges
      rightX = Math.max(rightX, leftX + leftWidth + spacing);
      if (rightX + rightWidth > getWidth()) {
        rightX = getWidth() - rightWidth;
        leftX = Math.max(0, Math.min(leftX, rightX - spacing - leftWidth));
      }
      bestX = bestFirst ? leftX : rightX;
      worstX = bestFirst ? rightX : leftX;
    } else {
      bestX = clamp(bestX, 0, Math.max(0, getWidth() - bestWidth));
      worstX = clamp(worstX, 0, Math.max(0, getWidth() - worstWidth));
    }

    // Both plates before either name: a plate laid afterwards would cut the end off its neighbour.
    drawPlate(canvas, bestX, bestWidth, bestTop);
    drawPlate(canvas, worstX, worstWidth, worstTop);
    drawName(canvas, bestName, bestX, bestWidth, bestPoint, bestTop, bestColor);
    drawName(canvas, worstName, worstX, worstWidth, worstPoint, worstTop, worstColor);
  }

  private void drawPlate(Canvas canvas, float x, float width, float barTop) {
    float bottom = barTop - plateLead;
    canvas.drawRoundRect(x, bottom - plateHeight, x + width, bottom,
      plateHeight / 2f, plateHeight / 2f, platePaint);
  }

  /** @param point the middle of the bar the name belongs to, which its lead is drawn down to */
  private void drawName(Canvas canvas, String name, float x, float width, float point, float barTop,
      int color) {
    float bottom = barTop - plateLead;
    leadPaint.setColor(color);
    // The lead leaves from under the plate however far the plate has been pushed off its bar.
    canvas.drawLine(clamp(point, x + plateHeight / 2f, x + width - plateHeight / 2f), bottom,
      point, barTop, leadPaint);
    namePaint.setColor(color);
    canvas.drawText(name, x + (width - namePaint.measureText(name)) / 2f,
      bottom - (plateHeight - digitHeight) / 2f, namePaint);
  }

  private static float centreOf(int index, float slot) {
    return index * slot + slot / 2f;
  }

  /**
   * What the bars are measured against before there is an average of 12 to measure them against:
   * the mean of the solves in the window. A plain mean, not a trimmed one, since it stands in for
   * an average rather than claiming to be it.
   */
  private Long meanOfWindow() {
    long total = 0;
    int counted = 0;
    for (Long time : times) {
      if (time != null && time > 0) {
        total += time;
        counted++;
      }
    }
    return counted < 2 ? null : total / counted; // one solve has nothing to be measured against
  }

  private static float clamp(float value, float min, float max) {
    return Math.max(min, Math.min(max, value));
  }

  private static float fraction(long time, long low, long high, boolean scaled) {
    if (!scaled) {
      return (FLOOR + CEILING) / 2f;
    }
    return clamp(FLOOR + (CEILING - FLOOR) * (time - low) / (float) (high - low), FLOOR, 1f);
  }

}
