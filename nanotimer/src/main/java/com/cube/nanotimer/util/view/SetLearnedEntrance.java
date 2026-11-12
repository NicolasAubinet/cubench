package com.cube.nanotimer.util.view;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

import java.util.List;

/**
 * How the whole-set celebration arrives: the card swells in, the rays fade up and keep turning, the
 * trophy pops, confetti goes off three times from three places, and every case of the set lands in
 * a quick wave.
 */
public final class SetLearnedEntrance {

  private static final long SCRIM_MS = 300;
  private static final long CARD_MS = 520;
  private static final float CARD_FROM = 0.8f;
  private static final long RAYS_FADE_MS = 900;
  private static final long RAYS_TURN_MS = 24000;
  private static final long BADGE_DELAY_MS = 220;
  private static final long BADGE_MS = 620;
  private static final long CASES_DELAY_MS = 520;
  private static final long CASE_STAGGER_MS = 22;
  private static final long CASE_MS = 300;
  private static final long[] BURST_AT_MS = {280, 900, 1500};
  private static final float[][] BURST_FROM = {{0.5f, 0.72f}, {0.2f, 0.8f}, {0.8f, 0.8f}};
  private static final long LEAVE_MS = 220;

  private SetLearnedEntrance() {
  }

  /** @return the rays' turning, to cancel on leaving */
  public static ValueAnimator play(View panel, View card, View rays, View badge, List<View> cases,
      final ParticleView confetti) {
    panel.setAlpha(0f);
    panel.animate().alpha(1f).setDuration(SCRIM_MS);

    card.setScaleX(CARD_FROM);
    card.setScaleY(CARD_FROM);
    card.setAlpha(0f);
    card.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(CARD_MS)
        .setInterpolator(new OvershootInterpolator(1.2f));

    rays.setAlpha(0f);
    rays.animate().alpha(1f).setStartDelay(BADGE_DELAY_MS).setDuration(RAYS_FADE_MS);
    ObjectAnimator turn = ObjectAnimator.ofFloat(rays, "rotation", 0f, 360f);
    turn.setDuration(RAYS_TURN_MS);
    turn.setRepeatCount(ValueAnimator.INFINITE);
    turn.setInterpolator(new LinearInterpolator());
    turn.start();

    badge.setScaleX(0f);
    badge.setScaleY(0f);
    badge.animate().scaleX(1f).scaleY(1f).setStartDelay(BADGE_DELAY_MS).setDuration(BADGE_MS)
        .setInterpolator(new OvershootInterpolator(3.2f));

    for (int i = 0; i < cases.size(); i++) {
      View chart = cases.get(i);
      chart.setScaleX(0f);
      chart.setScaleY(0f);
      chart.setAlpha(0f);
      chart.animate().scaleX(1f).scaleY(1f).alpha(1f)
          .setStartDelay(CASES_DELAY_MS + i * CASE_STAGGER_MS).setDuration(CASE_MS)
          .setInterpolator(new OvershootInterpolator(2.4f));
    }

    for (int i = 0; i < BURST_AT_MS.length; i++) {
      final float[] from = BURST_FROM[i];
      confetti.postDelayed(new Runnable() {
        @Override
        public void run() {
          confetti.burstAt(from[0], from[1]);
        }
      }, BURST_AT_MS[i]);
    }
    return turn;
  }

  public static void leave(View panel, ValueAnimator turn, Runnable then) {
    turn.cancel();
    panel.animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).setDuration(LEAVE_MS)
        .setInterpolator(new AccelerateInterpolator()).withEndAction(then);
  }
}
