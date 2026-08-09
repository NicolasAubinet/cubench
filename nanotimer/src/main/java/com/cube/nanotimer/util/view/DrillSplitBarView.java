package com.cube.nanotimer.util.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;

/**
 * One rep drawn as a bar: what the looking took against what the turning took, each segment its
 * share of the whole.
 *
 * <p>The three figures beside it say how long a rep was; this says where the time went, which is the
 * one thing a reader had to work out for themselves. It is the shape of the rep rather than its
 * size, so the bar always fills its width and two reps of different lengths are still comparable.
 *
 * <p>The two colours are the ones the mean cell writes its own halves in, so the bar needs no legend
 * of its own.
 */
public class DrillSplitBarView extends View {

  /** Of the bar's height: the radius its ends are rounded to. */
  private static final float CORNER_RATIO = 0.5f;

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF bounds = new RectF();
  private final Path rounded = new Path();

  private final int recognitionColor;
  private final int executionColor;

  private long recognitionMs;
  private long executionMs;

  public DrillSplitBarView(Context context) {
    this(context, null);
  }

  public DrillSplitBarView(Context context, AttributeSet attributes) {
    super(context, attributes);
    recognitionColor = ContextCompat.getColor(context, R.color.drill_bar_recognition);
    executionColor = ContextCompat.getColor(context, R.color.drill_bar_execution);
  }

  /** The rep's two halves. A rep with nothing in either draws nothing at all. */
  public void setSplit(long recognitionMs, long executionMs) {
    this.recognitionMs = Math.max(0, recognitionMs);
    this.executionMs = Math.max(0, executionMs);
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    long total = recognitionMs + executionMs;
    if (total == 0 || getWidth() == 0 || getHeight() == 0) {
      return;
    }
    float height = getHeight();
    float radius = height * CORNER_RATIO;
    bounds.set(0, 0, getWidth(), height);
    rounded.reset();
    rounded.addRoundRect(bounds, radius, radius, Path.Direction.CW);
    // Clipped rather than drawn as two rounded pieces: rounding each would round the join as well,
    // and the two halves are one bar rather than two chips.
    canvas.save();
    canvas.clipPath(rounded);
    float split = getWidth() * (recognitionMs / (float) total);
    paint.setColor(recognitionColor);
    canvas.drawRect(0, 0, split, height, paint);
    paint.setColor(executionColor);
    canvas.drawRect(split, 0, getWidth(), height, paint);
    canvas.restore();
  }
}
