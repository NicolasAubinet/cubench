package com.cube.nanotimer.util.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.content.ContextCompat;
import com.cube.nanotimer.R;

/**
 * The halo drawn behind the smart-cube glyph on the connect sheet. While scanning it sends accent
 * rings outward, so the wait reads as the app listening rather than stalling; once a cube is linked
 * the rings settle into a battery arc around the same glyph. Purely decorative: it carries no state
 * of its own beyond what the sheet tells it to show.
 */
public class SmartCubeRadarView extends View {

  private enum Mode { IDLE, SEARCHING, LINKED }

  private static final long PULSE_PERIOD_MS = 2400;
  private static final long SETTLE_MS = 700;
  private static final int RINGS = 3;

  /** Below these levels the arc warns rather than reassures. */
  private static final int BATTERY_LOW = 15;
  private static final int BATTERY_MEDIUM = 40;

  private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF arcBounds = new RectF();

  private Mode mode = Mode.IDLE;
  private Integer batteryLevel;
  private long modeStartMs;

  private final int accentColor;

  public SmartCubeRadarView(Context context) {
    this(context, null);
  }

  public SmartCubeRadarView(Context context, AttributeSet attrs) {
    super(context, attrs);
    accentColor = ContextCompat.getColor(context, R.color.lightblue);

    ringPaint.setStyle(Paint.Style.STROKE);
    ringPaint.setColor(accentColor);
    ringPaint.setStrokeWidth(dp(1.5f));

    trackPaint.setStyle(Paint.Style.STROKE);
    trackPaint.setColor(ContextCompat.getColor(context, R.color.gray800));
    trackPaint.setStrokeWidth(dp(4));

    arcPaint.setStyle(Paint.Style.STROKE);
    arcPaint.setStrokeWidth(dp(4));
    arcPaint.setStrokeCap(Paint.Cap.ROUND);
  }

  /** Pulse outward: no cube linked yet, and the sheet is listening for one. */
  public void showSearching() {
    setMode(Mode.SEARCHING);
  }

  /** Settle into a battery arc. A null level draws a plain accent ring — linked, charge unknown. */
  public void showLinked(Integer battery) {
    boolean sameArc = mode == Mode.LINKED && equal(batteryLevel, battery);
    batteryLevel = battery;
    if (sameArc) {
      return; // already showing this arc; don't replay the sweep on every battery notification
    }
    setMode(Mode.LINKED);
  }

  /** Nothing to say — problem states, where the halo would only add noise. */
  public void showIdle() {
    setMode(Mode.IDLE);
  }

  private void setMode(Mode newMode) {
    if (mode == newMode && newMode != Mode.LINKED) {
      return;
    }
    mode = newMode;
    modeStartMs = SystemClock.uptimeMillis();
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float cx = getWidth() / 2f;
    float cy = getHeight() / 2f;
    float extent = Math.min(cx, cy);

    switch (mode) {
      case SEARCHING:
        drawPulses(canvas, cx, cy, extent);
        postInvalidateOnAnimation();
        break;
      case LINKED:
        drawBatteryArc(canvas, cx, cy, extent);
        break;
      default:
        break;
    }
  }

  /** Rings leave the glyph together and fade as they widen, evenly spaced around the cycle. */
  private void drawPulses(Canvas canvas, float cx, float cy, float extent) {
    float inner = extent * 0.42f;
    float outer = extent * 0.96f;
    long elapsed = SystemClock.uptimeMillis() - modeStartMs;

    for (int i = 0; i < RINGS; i++) {
      float phase = ((elapsed / (float) PULSE_PERIOD_MS) + i / (float) RINGS) % 1f;
      // Fast at first, then coasting outward: a ping rather than a steady expansion.
      float eased = 1f - (1f - phase) * (1f - phase);
      ringPaint.setAlpha((int) (200 * (1f - phase) * (1f - phase)));
      canvas.drawCircle(cx, cy, inner + (outer - inner) * eased, ringPaint);
    }
  }

  private void drawBatteryArc(Canvas canvas, float cx, float cy, float extent) {
    float radius = extent * 0.62f;
    arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius);
    canvas.drawCircle(cx, cy, radius, trackPaint);

    float fraction = batteryLevel == null ? 1f : Math.max(0, Math.min(100, batteryLevel)) / 100f;
    arcPaint.setColor(batteryLevel == null ? accentColor : batteryColor(batteryLevel));

    long elapsed = SystemClock.uptimeMillis() - modeStartMs;
    float settle = Math.min(1f, elapsed / (float) SETTLE_MS);
    float sweep = 360f * fraction * (1f - (1f - settle) * (1f - settle)); // decelerate into place
    canvas.drawArc(arcBounds, -90, sweep, false, arcPaint);
    if (settle < 1f) {
      postInvalidateOnAnimation();
    }
  }

  private int batteryColor(int level) {
    int color = R.color.cube_green;
    if (level <= BATTERY_LOW) {
      color = R.color.cube_red;
    } else if (level <= BATTERY_MEDIUM) {
      color = R.color.warning;
    }
    return ContextCompat.getColor(getContext(), color);
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    modeStartMs = SystemClock.uptimeMillis(); // the cycle restarts rather than jumping mid-flight
    invalidate();
  }

  private static boolean equal(Integer a, Integer b) {
    return a == null ? b == null : a.equals(b);
  }

  private float dp(float value) {
    return value * getResources().getDisplayMetrics().density;
  }
}
