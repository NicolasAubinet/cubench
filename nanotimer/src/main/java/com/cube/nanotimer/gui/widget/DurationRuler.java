package com.cube.nanotimer.gui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;

/**
 * A graduated line for picking a short duration: a tick every step, a longer one every half
 * second, a labelled one every second. Dragged or tapped anywhere along it, it snaps to the nearest
 * step and ticks under the finger as it passes each one.
 */
public class DurationRuler extends View {

  /** Takes the duration the ruler stands on, as it moves. */
  public interface Listener {
    void onDuration(int ms);
  }

  private static final int HALF_MS = 500;
  private static final int SECOND_MS = 1000;

  private final float density;
  private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint filled = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint thumbRing = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final int faint;
  private final int strong;
  private final int accent;
  private int maxMs = 5000;
  private int stepMs = 100;
  private int valueMs;
  private CharSequence zeroLabel = "0";
  private Listener listener;

  public DurationRuler(Context context) {
    this(context, null);
  }

  public DurationRuler(Context context, AttributeSet attributes) {
    super(context, attributes);
    density = getResources().getDisplayMetrics().density;
    faint = ContextCompat.getColor(context, R.color.white_wash_20);
    strong = ContextCompat.getColor(context, R.color.secondary_text);
    accent = ContextCompat.getColor(context, R.color.color_accent);
    track.setColor(faint);
    track.setStrokeWidth(4 * density);
    track.setStrokeCap(Paint.Cap.ROUND);
    filled.setColor(accent);
    filled.setStrokeWidth(4 * density);
    filled.setStrokeCap(Paint.Cap.ROUND);
    tick.setStrokeCap(Paint.Cap.ROUND);
    label.setTextAlign(Paint.Align.CENTER);
    label.setTextSize(12 * getResources().getDisplayMetrics().scaledDensity);
    label.setColor(strong);
    thumb.setColor(accent);
    thumbRing.setStyle(Paint.Style.STROKE);
    thumbRing.setStrokeWidth(3 * density);
    thumbRing.setColor(ContextCompat.getColor(context, R.color.graybg));
  }

  /** @param zero what the start of the line is called, such as "Off" */
  public void setRange(int maxMs, int stepMs, CharSequence zero) {
    this.maxMs = maxMs;
    this.stepMs = stepMs;
    this.zeroLabel = zero;
    invalidate();
  }

  public void setListener(Listener listener) {
    this.listener = listener;
  }

  public int getDuration() {
    return valueMs;
  }

  public void setDuration(int ms) {
    valueMs = snap(ms);
    invalidate();
  }

  @Override
  protected void onMeasure(int widthSpec, int heightSpec) {
    int height = Math.round(76 * density);
    setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthSpec),
        resolveSize(height, heightSpec));
  }

  private float left() {
    return getPaddingLeft() + 16 * density;
  }

  private float right() {
    return getWidth() - getPaddingRight() - 16 * density;
  }

  private float lineY() {
    return 24 * density;
  }

  private float xOf(int ms) {
    return left() + (right() - left()) * ms / maxMs;
  }

  private int snap(int ms) {
    int snapped = Math.round(ms / (float) stepMs) * stepMs;
    return Math.max(0, Math.min(maxMs, snapped));
  }

  @Override
  protected void onDraw(Canvas canvas) {
    float y = lineY();
    float x = xOf(valueMs);
    canvas.drawLine(left(), y, right(), y, track);
    if (valueMs > 0) {
      canvas.drawLine(left(), y, x, y, filled);
    }
    float tickTop = y + 10 * density;
    for (int ms = 0; ms <= maxMs; ms += stepMs) {
      boolean second = ms % SECOND_MS == 0;
      boolean half = ms % HALF_MS == 0;
      float length = (second ? 12 : half ? 8 : 4) * density;
      tick.setStrokeWidth((second ? 2 : 1.5f) * density);
      tick.setColor(ms <= valueMs && valueMs > 0 ? accent : second ? strong : faint);
      float tx = xOf(ms);
      canvas.drawLine(tx, tickTop, tx, tickTop + length, tick);
      if (second) {
        String text = ms == 0 ? zeroLabel.toString() : String.valueOf(ms / SECOND_MS);
        label.setColor(ms == valueMs ? accent : strong);
        // Kept inside the view: a long word for "off" would run past its left edge
        float lx = Math.max(tx, getPaddingLeft() + label.measureText(text) / 2);
        canvas.drawText(text, lx, tickTop + length + 16 * density, label);
      }
    }
    canvas.drawCircle(x, y, 11 * density, thumb);
    canvas.drawCircle(x, y, 11 * density, thumbRing);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        getParent().requestDisallowInterceptTouchEvent(true);
        // fall through
      case MotionEvent.ACTION_MOVE:
        float fraction = (event.getX() - left()) / (right() - left());
        int ms = snap(Math.round(fraction * maxMs));
        if (ms != valueMs) {
          valueMs = ms;
          performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
          invalidate();
          if (listener != null) {
            listener.onDuration(ms);
          }
        }
        return true;
      case MotionEvent.ACTION_UP:
        performClick();
        return true;
      default:
        return super.onTouchEvent(event);
    }
  }

  @Override
  public boolean performClick() {
    return super.performClick();
  }
}
