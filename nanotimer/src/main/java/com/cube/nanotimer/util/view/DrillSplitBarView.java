package com.cube.nanotimer.util.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * One rep drawn as a bar: what the looking took against what the turning took, each segment its
 * share of the whole.
 *
 * <p>The three figures beside it say how long a rep was; this says where the time went, which is the
 * one thing a reader had to work out for themselves. It is the shape of the rep rather than its
 * size, so the bar always fills its width and two reps of different lengths are still comparable.
 *
 * <p>Both halves are drawn in the family's own colour, the looking as a wash of it and the turning
 * at full strength, which is the two colours the figures either side of the bar are written in, so
 * the bar needs no legend of its own. A rep with no turning in it is drawn whole at full strength:
 * there is no second half to set the wash against, and on its own the wash reads as disabled.
 */
public class DrillSplitBarView extends View {

  /** Of the bar's height: the radius its ends are rounded to. */
  private static final float CORNER_RATIO = 0.5f;

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF bounds = new RectF();
  private final Path rounded = new Path();

  private int color = Color.TRANSPARENT;
  private long recognitionMs;
  private long executionMs;

  public DrillSplitBarView(Context context) {
    this(context, null);
  }

  public DrillSplitBarView(Context context, AttributeSet attributes) {
    super(context, attributes);
  }

  /**
   * The rep's two halves, in the colour of the family they belong to. A rep with nothing in either
   * draws nothing at all.
   *
   * @param color the family's own colour, which the looking takes a wash of
   */
  public void setSplit(long recognitionMs, long executionMs, int color) {
    this.recognitionMs = Math.max(0, recognitionMs);
    this.executionMs = Math.max(0, executionMs);
    this.color = color;
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
    // Nothing turned means the bar has no second half to mark, and a lone wash reads as disabled.
    float split = executionMs > 0 ? getWidth() * (recognitionMs / (float) total) : 0;
    paint.setColor(StepPalette.wash(color));
    canvas.drawRect(0, 0, split, height, paint);
    paint.setColor(color);
    canvas.drawRect(split, 0, getWidth(), height, paint);
    canvas.restore();
  }
}
