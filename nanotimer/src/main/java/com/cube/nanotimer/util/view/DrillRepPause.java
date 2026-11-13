package com.cube.nanotimer.util.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The pause between two drill cases: the solved cube dims, and the rep just finished rises from its
 * slot under the cube to the middle of the cube's room, larger, with a line draining under it. When
 * the pause ends the dimming lifts first and the next case is dealt onto a clear cube, while the
 * line settles back into its slot.
 *
 * <p>Kept out of the drill screen, as {@link DrillRepFlourish} is: it only moves views. The screen
 * decides when a pause starts and ends, and the next case is still timed from when it is dealt.
 */
public final class DrillRepPause {

  private static final float SCALE = 1.25f;
  private static final float SCRIM_ALPHA = 0.88f;
  private static final long IN_MS = 320;
  private static final long EXTRAS_DELAY_MS = 120;
  private static final long EXTRAS_IN_MS = 220;
  private static final long OUT_MS = 280;
  private static final long EXTRAS_OUT_MS = 120;
  /** How long the dimming takes to lift before the next case is dealt onto the cube. */
  private static final long CLEAR_MS = 160;
  private static final float GAP_DP = 20;

  private final View stage;
  private final View scrim;
  private final View line;
  private final TextView title;
  private final View foot;
  private final View drain;
  private final float gap;
  private AnimatorSet running;
  private ViewTreeObserver.OnPreDrawListener placing;
  private boolean up;
  private Runnable pending;
  private final Runnable runPending = new Runnable() {
    @Override
    public void run() {
      Runnable then = pending;
      pending = null;
      if (then != null) {
        then.run();
      }
    }
  };

  /**
   * @param scrim covers the cube, inside the cube's own frame, which is the room the rep rises into
   * @param line the rep's line, a sibling of the cube's frame, moved and scaled by the pause
   * @param title over the cube's frame, above the rep: shown for a titled pause only
   * @param foot over the cube's frame, below the rep: the drain and the hint
   * @param drain the line that runs out as the pause does
   */
  public DrillRepPause(View scrim, View line, TextView title, View foot, View drain) {
    this.stage = (View) scrim.getParent();
    this.scrim = scrim;
    this.line = line;
    this.title = title;
    this.foot = foot;
    this.drain = drain;
    gap = GAP_DP * scrim.getResources().getDisplayMetrics().density;
  }

  /** Whether a pause is up, or ending with its next case still to deal. */
  public boolean isActive() {
    return up || pending != null;
  }

  /**
   * Brings the finished rep forward for the pause.
   *
   * @param heading said above the rep, or null for a plain pause
   * @param durationMs how long the drain takes to run out
   */
  public void enter(CharSequence heading, final long durationMs) {
    cancel();
    up = true;
    title.setText(heading);
    title.setVisibility(heading == null ? View.GONE : View.VISIBLE);
    title.setAlpha(0f);
    foot.setAlpha(0f);
    foot.setVisibility(View.VISIBLE);
    scrim.setAlpha(0f);
    scrim.setVisibility(View.VISIBLE);
    drain.setPivotX(0f);
    drain.setScaleX(1f);
    // Placed once laid out: the title and foot have no height before that
    placing = new ViewTreeObserver.OnPreDrawListener() {
      @Override
      public boolean onPreDraw() {
        stopPlacing();
        if (up) {
          animateIn(durationMs);
        }
        return true;
      }
    };
    line.getViewTreeObserver().addOnPreDrawListener(placing);
    line.invalidate();
  }

  /**
   * Ends the pause: the dimming lifts, then {@code dealt} runs, unless {@code now} runs it at once
   * (a turn already made for the next case). The line goes back to its slot meanwhile.
   */
  public void leave(Runnable dealt, boolean now) {
    stopPlacing();
    stage.removeCallbacks(runPending);
    pending = null;
    if (up) {
      up = false;
      animateOut();
    }
    if (now) {
      dealt.run();
    } else {
      pending = dealt;
      stage.postDelayed(runPending, CLEAR_MS);
    }
  }

  /** Drops the pause at once, nothing dealt: everything back at rest. */
  public void cancel() {
    stopPlacing();
    stage.removeCallbacks(runPending);
    pending = null;
    up = false;
    if (running != null) {
      running.cancel();
      running = null;
    }
    rest();
  }

  private void animateIn(long durationMs) {
    float height = line.getHeight();
    float titleRoom = title.getVisibility() == View.VISIBLE ? title.getHeight() + gap : 0;
    float block = titleRoom + height * SCALE + gap + foot.getHeight();
    float top = stage.getTop() + Math.max(0f, (stage.getHeight() - block) / 2);
    float lineCentre = top + titleRoom + height * SCALE / 2;
    title.setTranslationY(top - stage.getTop());
    foot.setTranslationY(top + titleRoom + height * SCALE + gap - stage.getTop());

    List<Animator> parts = new ArrayList<Animator>();
    parts.add(timed(ObjectAnimator.ofFloat(scrim, View.ALPHA, SCRIM_ALPHA), IN_MS, 0));
    parts.add(timed(ObjectAnimator.ofFloat(line, View.TRANSLATION_Y,
        lineCentre - (line.getTop() + height / 2)), IN_MS, 0));
    parts.add(timed(ObjectAnimator.ofFloat(line, View.SCALE_X, SCALE), IN_MS, 0));
    parts.add(timed(ObjectAnimator.ofFloat(line, View.SCALE_Y, SCALE), IN_MS, 0));
    parts.add(timed(ObjectAnimator.ofFloat(title, View.ALPHA, 1f), EXTRAS_IN_MS, EXTRAS_DELAY_MS));
    parts.add(timed(ObjectAnimator.ofFloat(foot, View.ALPHA, 1f), EXTRAS_IN_MS, EXTRAS_DELAY_MS));
    Animator drainOut = ObjectAnimator.ofFloat(drain, View.SCALE_X, 0f);
    drainOut.setDuration(durationMs);
    drainOut.setInterpolator(new LinearInterpolator());
    parts.add(drainOut);
    play(parts);
  }

  private void animateOut() {
    List<Animator> parts = new ArrayList<Animator>();
    parts.add(timed(ObjectAnimator.ofFloat(scrim, View.ALPHA, 0f), CLEAR_MS, 0));
    parts.add(timed(ObjectAnimator.ofFloat(title, View.ALPHA, 0f), EXTRAS_OUT_MS, 0));
    parts.add(timed(ObjectAnimator.ofFloat(foot, View.ALPHA, 0f), EXTRAS_OUT_MS, 0));
    parts.add(timed(ObjectAnimator.ofFloat(line, View.TRANSLATION_Y, 0f), OUT_MS, 0));
    parts.add(timed(ObjectAnimator.ofFloat(line, View.SCALE_X, 1f), OUT_MS, 0));
    parts.add(timed(ObjectAnimator.ofFloat(line, View.SCALE_Y, 1f), OUT_MS, 0));
    AnimatorSet set = play(parts);
    set.addListener(new AnimatorListenerAdapter() {
      private boolean cancelled;

      @Override
      public void onAnimationCancel(Animator animation) {
        cancelled = true;
      }

      @Override
      public void onAnimationEnd(Animator animation) {
        if (!cancelled && !up) {
          hideExtras();
        }
      }
    });
  }

  private AnimatorSet play(List<Animator> parts) {
    if (running != null) {
      running.cancel();
    }
    AnimatorSet set = new AnimatorSet();
    set.playTogether(parts);
    running = set;
    set.start();
    return set;
  }

  private static Animator timed(Animator animator, long durationMs, long delayMs) {
    animator.setDuration(durationMs);
    animator.setStartDelay(delayMs);
    animator.setInterpolator(new DecelerateInterpolator());
    return animator;
  }

  private void stopPlacing() {
    if (placing != null) {
      line.getViewTreeObserver().removeOnPreDrawListener(placing);
      placing = null;
    }
  }

  private void rest() {
    line.setTranslationY(0f);
    line.setScaleX(1f);
    line.setScaleY(1f);
    hideExtras();
  }

  private void hideExtras() {
    scrim.setVisibility(View.GONE);
    title.setVisibility(View.GONE);
    foot.setVisibility(View.GONE);
  }
}
