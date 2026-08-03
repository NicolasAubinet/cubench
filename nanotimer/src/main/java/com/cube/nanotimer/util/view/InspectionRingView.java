package com.cube.nanotimer.util.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;

/**
 * Inspection, drawn as a ring that drains. The seconds sit inside it, so the screen says how long
 * you have left without a number to compare against: at the one setting where 8 and 12 seconds mean
 * a penalty, the ring turns amber and then red as they pass, and at every other setting it stays
 * the one colour, because there the thresholds mean nothing.
 *
 * <p>A full-screen overlay that draws nothing until it is told a solve is being inspected, and
 * never takes a touch: the whole timer screen stays one tap target.
 */
public class InspectionRingView extends View {

  private static final int AMBER_AT_SECONDS = 8;
  private static final int RED_AT_SECONDS = 12;
  /** The only inspection time where those two seconds are a penalty rather than a number. */
  private static final int OFFICIAL_INSPECTION_SECONDS = 15;

  private static final float RADIUS_FRACTION = 0.15f; // of the shorter side
  private static final float STROKE_FRACTION = 0.13f; // of the radius

  private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF bounds = new RectF();

  private final int accentColor;
  private final int amberColor;
  private final int redColor;

  private boolean inspecting;
  private int totalSeconds;
  private boolean marksPenalties;
  private long elapsedMs;
  private CharSequence label; // overrides the count, for the official mode's "+2"

  public InspectionRingView(Context context) {
    this(context, null);
  }

  public InspectionRingView(Context context, AttributeSet attributes) {
    super(context, attributes);
    setClickable(false);
    setFocusable(false);

    accentColor = ContextCompat.getColor(context, R.color.lightblue);
    amberColor = ContextCompat.getColor(context, R.color.warning);
    redColor = ContextCompat.getColor(context, R.color.danger_text);

    trackPaint.setStyle(Paint.Style.STROKE);
    trackPaint.setColor(ContextCompat.getColor(context, R.color.white_wash_10));
    arcPaint.setStyle(Paint.Style.STROKE);
    arcPaint.setStrokeCap(Paint.Cap.ROUND);
    labelPaint.setTextAlign(Paint.Align.CENTER);
    labelPaint.setFakeBoldText(true);
  }

  /**
   * @param totalSeconds the inspection time the ring drains over, or 0 when there is no limit
   */
  public void start(int totalSeconds) {
    this.totalSeconds = totalSeconds;
    this.marksPenalties = (totalSeconds == OFFICIAL_INSPECTION_SECONDS);
    this.elapsedMs = 0;
    this.label = null;
    this.inspecting = true;
    invalidate();
  }

  public void setElapsed(long elapsedMs) {
    this.elapsedMs = elapsedMs;
    invalidate();
  }

  /** Says something other than the count, or null to go back to it. */
  public void setLabel(CharSequence label) {
    this.label = label;
    invalidate();
  }

  public void stop() {
    inspecting = false;
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (!inspecting || getWidth() <= 0) {
      return;
    }
    float radius = Math.min(getWidth(), getHeight()) * RADIUS_FRACTION;
    float stroke = radius * STROKE_FRACTION;
    float centerX = getWidth() / 2f;
    float centerY = getHeight() / 2f;
    trackPaint.setStrokeWidth(stroke);
    arcPaint.setStrokeWidth(stroke);
    arcPaint.setColor(currentColor());
    labelPaint.setColor(currentColor());
    labelPaint.setTextSize(radius * 0.95f);

    canvas.drawCircle(centerX, centerY, radius, trackPaint);
    if (totalSeconds > 0) {
      float left = Math.max(0f, 1f - elapsedMs / (totalSeconds * 1000f));
      bounds.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
      canvas.drawArc(bounds, -90f, 360f * left, false, arcPaint);
    }

    CharSequence text = (label != null) ? label : String.valueOf(elapsedMs / 1000);
    float baseline = centerY - (labelPaint.descent() + labelPaint.ascent()) / 2f;
    canvas.drawText(text.toString(), centerX, baseline, labelPaint);
  }

  private int currentColor() {
    if (!marksPenalties) {
      return accentColor;
    }
    int seconds = (int) (elapsedMs / 1000);
    if (seconds >= RED_AT_SECONDS) {
      return redColor;
    }
    return seconds >= AMBER_AT_SECONDS ? amberColor : accentColor;
  }

}
