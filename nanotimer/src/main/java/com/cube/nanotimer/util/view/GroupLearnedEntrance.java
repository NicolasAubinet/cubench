package com.cube.nanotimer.util.view;

import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ProgressBar;

import com.cube.nanotimer.R;

import java.util.List;

/**
 * How the shape-group celebration arrives: the card swells in, the trophy pops, confetti bursts, the
 * group's cases land one after another, the OLL bar fills by the cases just learned, and the
 * group's pip lights in the row of groups.
 */
public final class GroupLearnedEntrance {

  private static final long SCRIM_MS = 220;
  private static final long CARD_MS = 420;
  private static final float CARD_FROM = 0.86f;
  private static final long BADGE_DELAY_MS = 180;
  private static final long BADGE_MS = 460;
  private static final long CONFETTI_DELAY_MS = 260;
  private static final long CASES_DELAY_MS = 380;
  private static final long CASE_STAGGER_MS = 90;
  private static final long CASE_MS = 340;
  private static final long BAR_MS = 900;
  private static final long PIP_MS = 520;
  private static final long LEAVE_MS = 180;

  private GroupLearnedEntrance() {
  }

  public static void play(View panel, View card, View badge, List<View> cases,
      final ProgressBar bar, final int barTo, final View newPip, final ParticleView confetti) {
    panel.setAlpha(0f);
    panel.animate().alpha(1f).setDuration(SCRIM_MS);

    card.setScaleX(CARD_FROM);
    card.setScaleY(CARD_FROM);
    card.setAlpha(0f);
    card.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(CARD_MS)
        .setInterpolator(new OvershootInterpolator(1.4f));

    badge.setScaleX(0f);
    badge.setScaleY(0f);
    badge.animate().scaleX(1f).scaleY(1f).setStartDelay(BADGE_DELAY_MS).setDuration(BADGE_MS)
        .setInterpolator(new OvershootInterpolator(3f));

    for (int i = 0; i < cases.size(); i++) {
      View chart = cases.get(i);
      chart.setScaleX(0f);
      chart.setScaleY(0f);
      chart.setAlpha(0f);
      chart.animate().scaleX(1f).scaleY(1f).alpha(1f)
          .setStartDelay(CASES_DELAY_MS + i * CASE_STAGGER_MS).setDuration(CASE_MS)
          .setInterpolator(new OvershootInterpolator(2.2f));
    }

    confetti.postDelayed(new Runnable() {
      @Override
      public void run() {
        confetti.burst();
      }
    }, CONFETTI_DELAY_MS);

    ObjectAnimator fill = ObjectAnimator.ofInt(bar, "progress", bar.getProgress(), barTo);
    fill.setStartDelay(CASES_DELAY_MS + cases.size() * CASE_STAGGER_MS + CASE_MS);
    fill.setDuration(BAR_MS);
    fill.setInterpolator(new DecelerateInterpolator());
    fill.start();

    if (newPip != null) {
      newPip.postDelayed(new Runnable() {
        @Override
        public void run() {
          newPip.setBackgroundResource(R.drawable.group_learned_pip_on);
          newPip.setScaleX(0f);
          newPip.setScaleY(0f);
          newPip.animate().scaleX(1f).scaleY(1f).setDuration(PIP_MS)
              .setInterpolator(new OvershootInterpolator(4f));
        }
      }, fill.getStartDelay() + BAR_MS / 2);
    }
  }

  public static void leave(View panel, Runnable then) {
    panel.animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).setDuration(LEAVE_MS)
        .setInterpolator(new AccelerateInterpolator()).withEndAction(then);
  }
}
