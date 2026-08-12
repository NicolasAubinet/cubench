package com.cube.nanotimer.util.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;
import androidx.annotation.NonNull;

/**
 * A move that was undone, drawn with a hairline through it.
 *
 * <p>{@link android.text.style.StrikethroughSpan} draws as thick as the stem of the letter it
 * crosses, and it draws it flat, which at breakdown text size turned an "L" into a turnstile: the
 * bar landed exactly where the letter would have had one. This one leans, so it crosses at an
 * angle no letter has and reads as a mark laid over the token rather than part of it.
 */
public class CancelledMoveSpan extends ReplacementSpan {

  /** Of the text size. Skia's own strikethrough is about a twelfth of it. */
  private static final float THICKNESS_RATIO = 0.055f;

  /** Where the slash crosses the token: the middle of the x-height, where a strike reads. */
  private static final float HEIGHT_RATIO = 0.28f;

  /** How far it leans. Flat enough to read as a strike, steep enough not to read as a letter. */
  private static final float SLOPE = 0.45f;

  private static final int LINE_ALPHA = 0xB4;

  private final int color;

  public CancelledMoveSpan(int color) {
    this.color = color;
  }

  @Override
  public int getSize(@NonNull Paint paint, CharSequence text, int start, int end,
      Paint.FontMetricsInt fm) {
    if (fm != null) {
      paint.getFontMetricsInt(fm);
    }
    return Math.round(paint.measureText(text, start, end));
  }

  @Override
  public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top,
      int y, int bottom, @NonNull Paint paint) {
    int previousColor = paint.getColor();
    Paint.Style previousStyle = paint.getStyle();
    float previousWidth = paint.getStrokeWidth();

    paint.setColor(color);
    canvas.drawText(text, start, end, x, y, paint);

    float width = paint.measureText(text, start, end);
    float middle = y - paint.getTextSize() * HEIGHT_RATIO;
    float rise = SLOPE * width / 2;
    paint.setColor((color & 0x00FFFFFF) | (LINE_ALPHA << 24));
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(Math.max(1f, paint.getTextSize() * THICKNESS_RATIO));
    canvas.drawLine(x, middle + rise, x + width, middle - rise, paint);

    paint.setColor(previousColor);
    paint.setStyle(previousStyle);
    paint.setStrokeWidth(previousWidth);
  }
}
