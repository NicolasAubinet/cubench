package com.cube.nanotimer.util.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.style.ReplacementSpan;
import androidx.annotation.NonNull;

/**
 * A run of whole-cube rotations, drawn as one small grey mark on a faint plate: a change of grip
 * between the turns, which the turns around it should not have to compete with.
 */
public class GripSpan extends ReplacementSpan {

  private static final float SIZE_RATIO = 0.85f;
  private static final int PLATE_COLOR = 0x12FFFFFF;

  private final int color;
  private final float padding;
  private final float radius;
  private final RectF plate = new RectF();

  public GripSpan(int color, float density) {
    this.color = color;
    this.padding = 4 * density;
    this.radius = 4 * density;
  }

  @Override
  public int getSize(@NonNull Paint paint, CharSequence text, int start, int end,
      Paint.FontMetricsInt fm) {
    if (fm != null) {
      paint.getFontMetricsInt(fm);
    }
    float size = paint.getTextSize();
    paint.setTextSize(size * SIZE_RATIO);
    float width = paint.measureText(text, start, end);
    paint.setTextSize(size);
    return Math.round(width + 2 * padding);
  }

  @Override
  public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top,
      int y, int bottom, @NonNull Paint paint) {
    int previousColor = paint.getColor();
    float size = paint.getTextSize();
    paint.setTextSize(size * SIZE_RATIO);
    float width = paint.measureText(text, start, end);
    Paint.FontMetrics metrics = paint.getFontMetrics();
    plate.set(x, y + metrics.ascent, x + width + 2 * padding, y + metrics.descent);
    paint.setColor(PLATE_COLOR);
    canvas.drawRoundRect(plate, radius, radius, paint);
    paint.setColor(color);
    canvas.drawText(text, start, end, x + padding, y, paint);
    paint.setTextSize(size);
    paint.setColor(previousColor);
  }
}
