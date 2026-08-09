package com.cube.nanotimer.util.view;

import android.content.Context;
import android.provider.Settings;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * The beat a solved case gets before the next one goes up: a wash of colour over the cube and the
 * rep's time arriving on top of it.
 *
 * <p>Kept out of the drill screen, in the same spirit as {@link EnterAnimation}: it says nothing
 * about what a rep means, and taking it away would change nothing but the look. <b>It does not
 * decide when the next case appears</b> either. The screen holds that, because the hold is not
 * decoration: without one, a case replaced the instant it was solved and read as having gone wrong.
 */
public final class DrillRepFlourish {

  private static final long WASH_IN_MS = 90;
  private static final long WASH_OUT_MS = 260;
  private static final float WASH_ALPHA = 0.55f;
  private static final long TIME_MS = 220;
  private static final float TIME_FROM_SCALE = 0.8f;

  /** How long the whole beat lasts, for a screen that has to wait it out before moving on. */
  public static final long BEAT_MS = WASH_IN_MS + WASH_OUT_MS;

  private DrillRepFlourish() {
  }

  /**
   * @param wash a coloured cover over the cube, invisible between reps
   * @param time the view the rep's time has just been put into
   */
  public static void play(final View wash, View time) {
    if (!enabled(wash.getContext())) {
      return;
    }
    wash.setAlpha(0f);
    wash.setVisibility(View.VISIBLE);
    wash.animate()
      .alpha(WASH_ALPHA)
      .setDuration(WASH_IN_MS)
      .setInterpolator(new DecelerateInterpolator())
      .withEndAction(new Runnable() {
        @Override
        public void run() {
          wash.animate().alpha(0f).setDuration(WASH_OUT_MS).withEndAction(new Runnable() {
            @Override
            public void run() {
              wash.setVisibility(View.GONE);
            }
          }).start();
        }
      })
      .start();

    time.setScaleX(TIME_FROM_SCALE);
    time.setScaleY(TIME_FROM_SCALE);
    time.animate()
      .scaleX(1f)
      .scaleY(1f)
      .setDuration(TIME_MS)
      .setInterpolator(new DecelerateInterpolator())
      .start();
  }

  /** Leaves the views as they stand, which is the state the animation would have ended in. */
  public static void cancel(View wash, View time) {
    wash.animate().cancel();
    wash.setVisibility(View.GONE);
    time.animate().cancel();
    time.setScaleX(1f);
    time.setScaleY(1f);
  }

  private static boolean enabled(Context context) {
    return Settings.Global.getFloat(context.getContentResolver(),
      Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
  }
}
