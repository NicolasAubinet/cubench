package com.cube.nanotimer.util.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * One rep drawn as a bar: where its time went, each part its share of the whole.
 *
 * <p>The three figures beside it say how long a rep was; this says where the time went, which is the
 * one thing a reader had to work out for themselves. It is the shape of the rep rather than its
 * size, so the bar always fills its width and two reps of different lengths are still comparable.
 *
 * <p>A case rep is two parts, the looking as a wash of the family's own colour and the turning at
 * full strength, which is the two colours the figures either side of the bar are written in, so the
 * bar needs no legend of its own. A look-ahead rep is four; {@link DrillRepParts} says what each is.
 */
public class DrillSplitBarView extends View {

  /** Of the bar's height: the radius its ends are rounded to. */
  private static final float CORNER_RATIO = 0.5f;

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF bounds = new RectF();
  private final Path rounded = new Path();

  private long[] times = new long[0];
  private int[] colors = new int[0];

  public DrillSplitBarView(Context context) {
    this(context, null);
  }

  public DrillSplitBarView(Context context, AttributeSet attributes) {
    super(context, attributes);
  }

  /**
   * A case rep's two halves, in the colour of the family they belong to. A rep with nothing in
   * either draws nothing at all.
   *
   * @param color the family's own colour, which the looking takes a wash of
   */
  public void setSplit(long recognitionMs, long executionMs, int color) {
    DrillRepParts.draw(this, DrillRepParts.split(recognitionMs, executionMs, color));
  }

  /** The parts in the order they were spent, each in its own colour. */
  public void setSegments(long[] times, int[] colors) {
    this.times = times.clone();
    this.colors = colors.clone();
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    long total = 0;
    for (long time : times) {
      total += Math.max(0, time);
    }
    if (total == 0 || getWidth() == 0 || getHeight() == 0) {
      return;
    }
    float height = getHeight();
    float radius = height * CORNER_RATIO;
    bounds.set(0, 0, getWidth(), height);
    rounded.reset();
    rounded.addRoundRect(bounds, radius, radius, Path.Direction.CW);
    // Clipped rather than drawn as rounded pieces: rounding each would round the joins as well,
    // and the parts are one bar rather than a row of chips.
    canvas.save();
    canvas.clipPath(rounded);
    float left = 0;
    long spent = 0;
    for (int i = 0; i < times.length; i++) {
      spent += Math.max(0, times[i]);
      float right = getWidth() * (spent / (float) total);
      paint.setColor(colors[i]);
      canvas.drawRect(left, 0, right, height, paint);
      left = right;
    }
    canvas.restore();
  }
}
