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
 * Inspection, drawn as a ring that drains from the top: the arc keeps its head at twelve and its
 * tail unwinds back towards it. The seconds sit inside it, so the screen says how long is left
 * without a number to compare against, and the ring turns amber and then red as the end comes up.
 *
 * <p>The thresholds are counted back from the end rather than forward from the start, so they mean
 * the same thing at any inspection time: at 15 seconds they land on 8 and 12, which is where the
 * official penalties are. Below {@link #MIN_MARKED_SECONDS} there is too little inspection for a
 * warning to arrive before the end, so the ring keeps its one colour.
 *
 * <p>It also washes the whole screen in its own colour, faintly at first and clearly by the end:
 * a ground that only drops once says nothing after the first instant.
 *
 * <p>A full-screen overlay that draws nothing until it is told a solve is being inspected, and
 * never takes a touch: the whole timer screen stays one tap target.
 */
public class InspectionRingView extends View {

  private static final int AMBER_SECONDS_LEFT = 7; // 8 seconds in, at the official 15
  private static final int RED_SECONDS_LEFT = 3; // and 12 seconds in
  /** Under this there is no room for a warning to arrive before the end. */
  private static final int MIN_MARKED_SECONDS = 10;

  private static final float RADIUS_FRACTION = 0.15f; // of the shorter side
  private static final float STROKE_FRACTION = 0.13f; // of the radius
  private static final int WASH_ALPHA_START = 4; // of 255, over the dropped ground
  private static final int WASH_ALPHA_END = 20;
  private static final float EXIT_SHRINK = 0.8f; // of the radius, by the time it has gone

  private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint washPaint = new Paint();
  private final RectF bounds = new RectF();

  private final int accentColor;
  private final int amberColor;
  private final int redColor;
  private final int trackAlpha;

  private boolean inspecting;
  private float exit; // how far through leaving the ring is: 0 while it is up, 1 once it is gone
  private int totalSeconds;
  private boolean marksPenalties;
  private long elapsedMs;
  private CharSequence label; // overrides the count, for the official mode's "+2"
  private Float centerY; // set by the screen, so the ring and the digits share a centre

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
    trackAlpha = trackPaint.getAlpha(); // the track is already faint: it fades from there, not 255
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
    this.marksPenalties = (totalSeconds >= MIN_MARKED_SECONDS);
    this.elapsedMs = 0;
    this.label = null;
    this.exit = 0f;
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

  /** Where the ring sits, in this view's own coordinates. Null centres it on the view. */
  public void setCenterY(Float centerY) {
    this.centerY = centerY;
    invalidate();
  }

  /**
   * How far the ring is through leaving, 0 while it is up and 1 once it has gone: it shrinks back
   * towards its own centre and fades, and its wash over the screen goes with it. The shrinking is
   * what keeps it out of the digits arriving at the same point, which a plain fade would sit under.
   */
  public void setExit(float exit) {
    this.exit = Math.max(0f, Math.min(1f, exit));
    invalidate();
  }

  /** True while there is a ring on screen, and so something to take away. */
  public boolean isUp() {
    return inspecting;
  }

  public void stop() {
    inspecting = false;
    exit = 0f;
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (!inspecting || getWidth() <= 0) {
      return;
    }
    float here = 1f - exit; // how much of the ring is still there
    washPaint.setColor(currentColor());
    washPaint.setAlpha(Math.round(here * (WASH_ALPHA_START
      + Math.round((WASH_ALPHA_END - WASH_ALPHA_START) * (1f - fractionLeft())))));
    canvas.drawPaint(washPaint);

    float radius = Math.min(getWidth(), getHeight()) * RADIUS_FRACTION * (1f - EXIT_SHRINK * exit);
    float stroke = radius * STROKE_FRACTION;
    float centerX = getWidth() / 2f;
    float centerY = (this.centerY != null) ? this.centerY : getHeight() / 2f;
    trackPaint.setStrokeWidth(stroke);
    arcPaint.setStrokeWidth(stroke);
    arcPaint.setColor(currentColor());
    labelPaint.setColor(currentColor());
    labelPaint.setTextSize(radius * 0.95f);
    trackPaint.setAlpha(Math.round(trackAlpha * here)); // after the colours, which carry their own
    arcPaint.setAlpha(Math.round(255 * here));
    labelPaint.setAlpha(Math.round(255 * here));

    canvas.drawCircle(centerX, centerY, radius, trackPaint);
    if (totalSeconds > 0) {
      // The arc keeps its head at twelve and its tail retreats towards it, so what is left of the
      // inspection is what is left of the ring, read from the top.
      float left = fractionLeft();
      bounds.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
      canvas.drawArc(bounds, -90f, 360f * left, false, arcPaint);
    }

    CharSequence text = (label != null) ? label : String.valueOf(elapsedMs / 1000);
    float baseline = centerY - (labelPaint.descent() + labelPaint.ascent()) / 2f;
    canvas.drawText(text.toString(), centerX, baseline, labelPaint);
  }

  /** How much of the inspection is left, 1 at the start and 0 at the end (and after it). */
  private float fractionLeft() {
    if (totalSeconds <= 0) {
      return 1f;
    }
    return Math.max(0f, 1f - elapsedMs / (totalSeconds * 1000f));
  }

  private int currentColor() {
    if (!marksPenalties) {
      return accentColor;
    }
    int secondsLeft = totalSeconds - (int) (elapsedMs / 1000);
    if (secondsLeft <= RED_SECONDS_LEFT) {
      return redColor;
    }
    return secondsLeft <= AMBER_SECONDS_LEFT ? amberColor : accentColor;
  }

}
