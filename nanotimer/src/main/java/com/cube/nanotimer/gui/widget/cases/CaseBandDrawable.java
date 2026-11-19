package com.cube.nanotimer.gui.widget.cases;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * The case sheet's top washed in the step's own colour, fading into the sheet by the time the
 * figures end. Drawn over the sheet's surface, so it only carries the tint and clears to nothing.
 */
final class CaseBandDrawable extends Drawable {

  /** How much of the hue the band starts with, and still has at {@link #MID_AT} of its height. */
  private static final float TOP_ALPHA = 0.36f;
  private static final float MID_ALPHA = 0.16f;
  private static final float MID_AT = 0.55f;

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path clip = new Path();
  private final RectF rect = new RectF();
  private final float cornerRadius;
  private final int hue;
  private int bandHeight;

  CaseBandDrawable(int hue, float cornerRadius) {
    this.hue = hue;
    this.cornerRadius = cornerRadius;
  }

  /** Where the band has faded out, from the sheet's top. */
  void setBandHeight(int height) {
    if (height != bandHeight) {
      bandHeight = height;
      paint.setShader(new LinearGradient(0, 0, 0, bandHeight,
          new int[] {tint(TOP_ALPHA), tint(MID_ALPHA), tint(0)},
          new float[] {0, MID_AT, 1}, Shader.TileMode.CLAMP));
      invalidateSelf();
    }
  }

  @Override
  public void draw(Canvas canvas) {
    if (bandHeight <= 0) {
      return;
    }
    int width = getBounds().width();
    // The sheet is rounded at the top only: square the bottom corners off below the band.
    rect.set(0, 0, width, bandHeight + cornerRadius);
    clip.reset();
    clip.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW);
    canvas.save();
    canvas.clipRect(0, 0, width, bandHeight);
    canvas.drawPath(clip, paint);
    canvas.restore();
  }

  private int tint(float alpha) {
    return Color.argb(Math.round(alpha * 255), Color.red(hue), Color.green(hue), Color.blue(hue));
  }

  @Override
  public void setAlpha(int alpha) {
    paint.setAlpha(alpha);
  }

  @Override
  public void setColorFilter(ColorFilter filter) {
    paint.setColorFilter(filter);
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
