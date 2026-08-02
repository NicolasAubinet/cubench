package com.cube.nanotimer.util.view;

import android.content.Context;
import android.provider.Settings;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * A short rise-and-fade for values that have just been replaced, so a switch reads as arriving
 * somewhere rather than as a redraw. Kept out of the screens themselves: it says nothing about
 * what the values mean, and removing it would change nothing but the look.
 */
public final class EnterAnimation {

  private static final long DURATION_MS = 260;
  private static final long STAGGER_MS = 45;
  private static final float RISE_DP = 5f;

  private EnterAnimation() {
  }

  /** Fades the views in in order, each one a beat after the last. */
  public static void stagger(View... views) {
    if (views.length == 0 || !enabled(views[0].getContext())) {
      return;
    }
    float rise = RISE_DP * views[0].getResources().getDisplayMetrics().density;
    for (int i = 0; i < views.length; i++) {
      View view = views[i];
      view.setAlpha(0f);
      view.setTranslationY(rise);
      view.animate()
        .alpha(1f)
        .translationY(0f)
        .setStartDelay(i * STAGGER_MS)
        .setDuration(DURATION_MS)
        .setInterpolator(new DecelerateInterpolator())
        .start();
    }
  }

  private static boolean enabled(Context context) {
    return Settings.Global.getFloat(context.getContentResolver(),
      Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
  }
}
