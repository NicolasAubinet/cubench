package com.cube.nanotimer.util.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.provider.Settings;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;

/**
 * The beat a finished rep gets before the next one goes up: a wash of colour over the cube and the
 * rep's figure arriving on top of it.
 *
 * <p><b>There are three of them, because a drill has more than one kind of finish.</b> The green
 * beat is for a rep that could not have gone better, the quiet one for a rep that ended without
 * being that, and a rep that produced nothing gets none: the beat is the fastest thing on the
 * screen to read, so the three finishes have to be told apart by it rather than only by the words
 * underneath. The celebration alone holds long enough to be a moment of its own and carries a badge,
 * since it is the one finish worth interrupting the user's own looking for.
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
  /** The same wash for a rep that finished without being the best it could be: audible, not loud. */
  private static final float QUIET_WASH_ALPHA = 0.3f;
  private static final long TIME_MS = 220;
  private static final float TIME_FROM_SCALE = 0.8f;

  /**
   * How long {@link #play}'s beat lasts, for a screen that has to wait it out before moving on.
   * <b>Not the celebration's</b>, which is several times that: a screen that moves on by itself
   * after this would cut one off before its badge had arrived.
   */
  public static final long PLAY_BEAT_MS = WASH_IN_MS + WASH_OUT_MS;

  /**
   * And the celebration, which is the same wash held. Long enough to read a badge over the cube and
   * no longer: what it covers is the thing the user has just built and came to look at.
   */
  private static final long CHEER_IN_MS = 140;
  private static final long CHEER_HOLD_MS = 560;
  private static final long CHEER_OUT_MS = 400;
  private static final long CHEER_POP_MS = 280;
  private static final float CHEER_FROM_SCALE = 0.6f;

  /**
   * What the badge holds for, which is not what the wash holds for: it takes longer to arrive, and
   * the two have to start fading together or the badge is left floating over a bare cube.
   */
  private static final long BADGE_HOLD_MS = CHEER_HOLD_MS - (CHEER_POP_MS - CHEER_IN_MS);

  private DrillRepFlourish() {
  }

  /**
   * The beat for a rep that could not have gone better.
   *
   * @param wash a coloured cover over the cube, invisible between reps
   * @param time the view the rep's figure has just been put into
   */
  public static void play(final View wash, View time) {
    if (!enabled(wash.getContext())) {
      return;
    }
    wash(wash, R.color.green_soft, WASH_ALPHA, WASH_IN_MS, 0, WASH_OUT_MS);
    pop(time, TIME_FROM_SCALE, TIME_MS, new DecelerateInterpolator());
  }

  /**
   * The beat for a rep that finished, but not as well as it could have. The app's own accent rather
   * than the green: a finish that is not a success must not look like one at a glance.
   */
  public static void playQuietly(final View wash, View time) {
    if (!enabled(wash.getContext())) {
      return;
    }
    wash(wash, R.color.color_accent, QUIET_WASH_ALPHA, WASH_IN_MS, 0, WASH_OUT_MS);
    pop(time, TIME_FROM_SCALE, TIME_MS, new DecelerateInterpolator());
  }

  /**
   * The loud one, for the finish a drill exists to produce. The wash holds while a badge lands on
   * it, and the rep's figure arrives with an overshoot rather than settling into place.
   *
   * @param badge what is being celebrated, said in a word over the cube; hidden again after
   */
  public static void celebrate(final View wash, View time, final View badge) {
    if (!enabled(wash.getContext())) {
      return;
    }
    wash(wash, R.color.green_soft, WASH_ALPHA, CHEER_IN_MS, CHEER_HOLD_MS, CHEER_OUT_MS);
    pop(time, CHEER_FROM_SCALE, CHEER_POP_MS, new OvershootInterpolator());

    badge.animate().cancel();
    badge.setAlpha(0f);
    badge.setScaleX(CHEER_FROM_SCALE);
    badge.setScaleY(CHEER_FROM_SCALE);
    badge.setVisibility(View.VISIBLE);
    badge.animate()
      .alpha(1f)
      .scaleX(1f)
      .scaleY(1f)
      .setStartDelay(0)
      .setDuration(CHEER_POP_MS)
      .setInterpolator(new OvershootInterpolator())
      .withEndAction(new Runnable() {
        @Override
        public void run() {
          badge.animate()
            .alpha(0f)
            .setStartDelay(BADGE_HOLD_MS)
            .setDuration(CHEER_OUT_MS)
            .setInterpolator(new DecelerateInterpolator())
            .withEndAction(new Runnable() {
              @Override
              public void run() {
                badge.setVisibility(View.GONE);
              }
            })
            .start();
        }
      })
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

  /** The same, for a screen whose celebration puts a badge up too. */
  public static void cancel(View wash, View time, View badge) {
    cancel(wash, time);
    badge.animate().cancel();
    badge.setVisibility(View.GONE);
    badge.setAlpha(1f);
    badge.setScaleX(1f);
    badge.setScaleY(1f);
  }

  /**
   * Colour in and out over the cube, with an optional hold between the two.
   *
   * <p>Every step of the animator is set here rather than left to the last caller's values: one
   * {@code ViewPropertyAnimator} serves a view for its lifetime, so a start delay or interpolator
   * from a previous beat would otherwise still be on it.
   */
  private static void wash(final View wash, int colorRes, float alpha, long inMs, final long holdMs,
      final long outMs) {
    wash.animate().cancel();
    wash.setBackgroundTintList(ColorStateList.valueOf(
      ContextCompat.getColor(wash.getContext(), colorRes)));
    wash.setAlpha(0f);
    wash.setVisibility(View.VISIBLE);
    wash.animate()
      .alpha(alpha)
      .setStartDelay(0)
      .setDuration(inMs)
      .setInterpolator(new DecelerateInterpolator())
      .withEndAction(new Runnable() {
        @Override
        public void run() {
          wash.animate()
            .alpha(0f)
            .setStartDelay(holdMs)
            .setDuration(outMs)
            .setInterpolator(new DecelerateInterpolator())
            .withEndAction(new Runnable() {
              @Override
              public void run() {
                wash.setVisibility(View.GONE);
              }
            })
            .start();
        }
      })
      .start();
  }

  /** A view arriving at its own size rather than being there already. */
  private static void pop(View view, float fromScale, long durationMs, Interpolator interpolator) {
    view.animate().cancel();
    view.setScaleX(fromScale);
    view.setScaleY(fromScale);
    view.animate()
      .scaleX(1f)
      .scaleY(1f)
      .setStartDelay(0)
      .setDuration(durationMs)
      .setInterpolator(interpolator)
      .start();
  }

  private static boolean enabled(Context context) {
    return Settings.Global.getFloat(context.getContentResolver(),
      Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
  }
}
