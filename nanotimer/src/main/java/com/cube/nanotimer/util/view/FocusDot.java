package com.cube.nanotimer.util.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.View;

/**
 * The dot that stands in for the digits while a solve is timed without them: it breathes slowly, so
 * the screen says the timer is running without saying how long for, which is the whole point of the
 * setting that hides the time.
 *
 * <p>Purely visual. It is told that a solve started and that one ended, and is asked nothing.
 */
public class FocusDot {

  private static final long BREATH_MS = 1500;
  private static final float BREATH_MIN = 0.4f;

  private final View dot;
  private final GradientDrawable shape = new GradientDrawable();

  private ValueAnimator breath;

  public FocusDot(View dot) {
    this.dot = dot;
    shape.setShape(GradientDrawable.OVAL);
    dot.setBackground(shape);
    dot.setVisibility(View.GONE);
  }

  /** The hue the solve wears, from the puzzle or the solve type: never picked here. */
  public void setColor(int color) {
    shape.setColor(color);
  }

  public void show() {
    dot.setVisibility(View.VISIBLE);
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

  public void hide() {
    if (breath != null) {
      breath.cancel();
      breath = null;
    }
    dot.setAlpha(1f);
    dot.setScaleX(1f);
    dot.setScaleY(1f);
    dot.setVisibility(View.GONE);
  }

  private static boolean animationsEnabled(Context context) {
    return Settings.Global.getFloat(context.getContentResolver(),
      Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
  }

}
