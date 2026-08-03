package com.cube.nanotimer.util.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.View;

/**
 * What the timer screen draws while it is not to be read: a pool of the solve's own colour behind
 * the digits, and a slowly breathing dot that stands in for them when the time is being kept back.
 *
 * <p>Purely visual. It is told that a solve started and that one ended, and is asked nothing.
 */
public class TimerGlow {

  private static final int POOL_ALPHA = 105; // out of 255, over the dropped ground
  private static final long FADE_MS = 260;
  private static final long BREATH_MS = 1500;
  private static final float BREATH_MIN = 0.4f;

  private final View pool; // the band the digits sit in; the glow is its background
  private final View dot;
  private final PoolDrawable poolShape = new PoolDrawable();
  private final GradientDrawable dotShape = new GradientDrawable();

  private ValueAnimator fade;
  private ValueAnimator breath;

  public TimerGlow(View pool, View dot) {
    this.pool = pool;
    this.dot = dot;
    poolShape.setAlpha(0);
    pool.setBackground(poolShape);
    dotShape.setShape(GradientDrawable.OVAL);
    dot.setBackground(dotShape);
    dot.setVisibility(View.GONE);
  }

  /** The hue the solve wears, from the puzzle or the solve type: never picked here. */
  public void setColor(int color) {
    poolShape.setColor(color);
    dotShape.setColor(color);
  }

  /** @param standIn draw the dot in place of the digits, for a solve timed without them */
  public void show(boolean standIn) {
    fadeTo(POOL_ALPHA);
    if (standIn) {
      dot.setVisibility(View.VISIBLE);
      startBreathing();
    }
  }

  public void hide() {
    fadeTo(0);
    stopBreathing();
    dot.setVisibility(View.GONE);
  }

  private void fadeTo(int alpha) {
    if (fade != null) {
      fade.cancel();
      fade = null;
    }
    if (!animationsEnabled(pool.getContext())) {
      poolShape.setAlpha(alpha);
      return;
    }
    fade = ValueAnimator.ofInt(poolShape.getAlpha(), alpha);
    fade.setDuration(FADE_MS);
    fade.addUpdateListener(a -> poolShape.setAlpha((int) a.getAnimatedValue()));
    fade.start();
  }

  private void startBreathing() {
    if (breath != null) {
      return;
    }
    if (!animationsEnabled(dot.getContext())) {
      dot.setAlpha(1f);
      return;
    }
    breath = ValueAnimator.ofFloat(1f, BREATH_MIN);
    breath.setDuration(BREATH_MS);
    breath.setRepeatMode(ValueAnimator.REVERSE);
    breath.setRepeatCount(ValueAnimator.INFINITE);
    breath.addUpdateListener(a -> {
      float breathed = (float) a.getAnimatedValue();
      dot.setAlpha(breathed);
      dot.setScaleX(0.85f + 0.15f * breathed);
      dot.setScaleY(0.85f + 0.15f * breathed);
    });
    breath.start();
  }

  private void stopBreathing() {
    if (breath != null) {
      breath.cancel();
      breath = null;
    }
    dot.setAlpha(1f);
    dot.setScaleX(1f);
    dot.setScaleY(1f);
  }

  private static boolean animationsEnabled(Context context) {
    return Settings.Global.getFloat(context.getContentResolver(),
      Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
  }

  /**
   * A soft ellipse of colour centred in its bounds. A background rather than a view, so the pool
   * costs no height and cannot move the digits it sits behind; it fades out well inside its own
   * edges, which is what keeps it from reading as a lit rectangle.
   */
  private static class PoolDrawable extends Drawable {

    private static final float WIDTH_FRACTION = 0.34f; // of the band, each way from the centre

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int color = Color.TRANSPARENT;
    private int alpha = 255;

    void setColor(int color) {
      this.color = color;
      buildShader();
      invalidateSelf();
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
      buildShader();
    }

    private void buildShader() {
      Rect bounds = getBounds();
      if (bounds.width() <= 0 || bounds.height() <= 0) {
        return;
      }
      float radius = bounds.height() / 2f;
      RadialGradient gradient = new RadialGradient(bounds.exactCenterX(), bounds.exactCenterY(),
        radius, color, color & 0x00FFFFFF, Shader.TileMode.CLAMP);
      Matrix stretch = new Matrix();
      stretch.setScale(bounds.width() * WIDTH_FRACTION / radius, 1f,
        bounds.exactCenterX(), bounds.exactCenterY());
      gradient.setLocalMatrix(stretch);
      paint.setShader(gradient);
    }

    @Override
    public void draw(Canvas canvas) {
      if (paint.getShader() != null && alpha > 0) {
        canvas.drawRect(getBounds(), paint);
      }
    }

    @Override
    public void setAlpha(int alpha) {
      this.alpha = alpha;
      paint.setAlpha(alpha);
      invalidateSelf();
    }

    @Override
    public int getAlpha() {
      return alpha;
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
      paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
      return PixelFormat.TRANSLUCENT;
    }
  }

}
