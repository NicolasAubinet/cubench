package com.cube.nanotimer.util.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;

/**
 * One step's share against what that step usually takes, as a bar growing either way from a rule
 * down the middle.
 *
 * <p>Signed length about a zero is the one form that says "over" and "under" at a glance, which is
 * why the comparison is drawn here rather than as notches on the composition bar: those are
 * cumulative, so a single over-long first step would shift every notch after it and three steps out
 * of four would read as wrong.
 *
 * <p>Coloured by the step rather than by the direction. Which side of the rule the bar is on already
 * says the direction, and a green-and-red pair here would be the third meaning the same two colours
 * carry on this screen.
 */
public class DeltaBarView extends View {

  /** Of the bar's height: the radius its ends are rounded to. */
  private static final float CORNER_RATIO = 0.25f;

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final int ruleColor;

  private float fraction; // -1 to 1 of the half width, negative being under
  private int color;

  public DeltaBarView(Context context) {
    this(context, null);
  }

  public DeltaBarView(Context context, AttributeSet attributes) {
    super(context, attributes);
    ruleColor = ContextCompat.getColor(context, R.color.white_wash_25);
  }

  /**
   * @param fraction how far out the bar reaches, -1 to 1, negative for a step taking less of the
   *     solve than the reference says
   * @param color the step's own colour
   */
  public void setDelta(float fraction, int color) {
    this.fraction = Math.max(-1f, Math.min(1f, fraction));
    this.color = color;
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    float height = getHeight();
    float middle = getWidth() / 2f;
    paint.setColor(ruleColor);
    canvas.drawRect(middle - 0.5f, 0, middle + 0.5f, height, paint);
    if (fraction == 0) {
      return;
    }
    float end = middle + fraction * middle;
    float corner = height * CORNER_RATIO;
    paint.setColor(color);
    canvas.drawRoundRect(Math.min(middle, end), 0, Math.max(middle, end), height,
        corner, corner, paint);
  }
}
