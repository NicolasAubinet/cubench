package com.cube.nanotimer.util.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;

/**
 * Draws a scramble move scaled about its own centre, in a given colour, while reporting its normal
 * (unscaled) width — so the surrounding text never reflows as the move grows or shrinks. The scale
 * and colour are mutable and read at draw time, so an animator can tween them and just
 * {@code invalidate()} the view, with no relayout and no {@code setText}.
 */
public class ScaledMoveSpan extends ReplacementSpan {

  private float scale;
  private int color;
  private boolean bold;

  public ScaledMoveSpan(float scale, int color, boolean bold) {
    this.scale = scale;
    this.color = color;
    this.bold = bold;
  }

  public void setScale(float scale) {
    this.scale = scale;
  }

  public void setColor(int color) {
    this.color = color;
  }

  @Override
  public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
    if (fm != null) {
      Paint.FontMetricsInt base = paint.getFontMetricsInt();
      fm.top = base.top;
      fm.ascent = base.ascent;
      fm.descent = base.descent;
      fm.bottom = base.bottom;
    }
    return Math.round(paint.measureText(text, start, end));
  }

  @Override
  public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y,
      int bottom, Paint paint) {
    float width = paint.measureText(text, start, end);
    int prevColor = paint.getColor();
    boolean prevBold = paint.isFakeBoldText();
    canvas.save();
    canvas.scale(scale, scale, x + width / 2f, y); // grow/shrink in place around the baseline centre
    paint.setColor(color);
    paint.setFakeBoldText(bold);
    canvas.drawText(text, start, end, x, y, paint);
    canvas.restore();
    paint.setColor(prevColor);
    paint.setFakeBoldText(prevBold);
  }
}
