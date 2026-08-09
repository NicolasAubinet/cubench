package com.cube.nanotimer.util.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.provider.Settings;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * The timer screen standing down for a solve and coming back around the result, as one movement
 * each way rather than a set of things that all change in the same frame.
 *
 * <p>Going in, the screen clears and the time slides to the middle. Coming back, the time returns
 * to where the layout keeps it and the screen assembles behind it, a beat later. At the end of an
 * inspection the ring collapses into the point it was drawn around and the digits rise out of that
 * same point, so the count reads as becoming the time.
 *
 * <p>Purely visual, and never in the timer's way: a solve has its start stamp before any of this
 * runs, the digits are live throughout, and with animations turned off every method here lands on
 * the same screen at once.
 */
public final class FocusTransition {

  private static final long RING_OUT_MS = 140;
  private static final long DIGITS_WAIT_MS = 55; // the beat the ring has the centre to itself
  private static final long DIGITS_IN_MS = 150;
  private static final float DIGITS_FROM = 0.9f; // they settle into place rather than appear
  private static final long PANELS_OUT_MS = 120; // the screen clears; it does not linger
  private static final long PANELS_LEAD_MS = 120; // most of the time's travel, so it lands first
  private static final long DIGITS_MOVE_MS = 240;
  private static final long RING_IN_MS = 160;
  private static final long LIGHTS_MS = 200; // the room dims a shade slower than the panels clear

  private final InspectionRingView ring;

  private ValueAnimator ringMove;
  private boolean ringLeaving; // the move under way is the collapse, not the ring opening
  private boolean focused; // the screen has stood down, so coming back out of it is a movement

  public FocusTransition(InspectionRingView ring) {
    this.ring = ring;
  }

  /**
   * Inspection has started: the ring opens out of the point it will collapse back into, as the
   * screen behind it clears, rather than standing there at full size over a screen still lit.
   * Call it once the ring has been told what it is counting, which resets what this drives.
   */
  public void ringIn() {
    cancelRingMove();
    if (!enabled(ring.getContext())) {
      ring.setExit(0f);
      return;
    }
    move(1f, 0f, RING_IN_MS, false); // the collapse run backwards
  }

  /**
   * Inspection is over. The ring collapses back into its own centre rather than being cut, and
   * keeps that centre until it has finished. A second call while that runs joins the collapse
   * already going: the end of an inspection reaches this twice, once as the inspection stopping
   * and once as the solve starting.
   */
  public void ringOut() {
    if (ringLeaving) {
      return;
    }
    if (!ring.isUp() || !enabled(ring.getContext())) {
      ringGone(); // there was no ring to take away, or nothing here is allowed to take time
      return;
    }
    // An inspection ended before the ring finished opening carries on from where it got to.
    float from = (ringMove == null) ? 0f : (float) ringMove.getAnimatedValue();
    cancelRingMove();
    move(from, 1f, RING_OUT_MS, true);
  }

  /** The ring is away and gives its centre back: the end of a collapse, or instead of one. */
  public void ringGone() {
    cancelRingMove();
    ring.setCenterY(null);
    ring.stop();
  }

  /**
   * Drives how much of the ring is drawn, either way. It gives the centre up at once and then
   * finishes gently, so the digits arrive over a ring that is already a faint dot.
   */
  private void move(float from, float to, long durationMs, final boolean leaving) {
    ringLeaving = leaving;
    final ValueAnimator moving = ValueAnimator.ofFloat(from, to);
    moving.setDuration(durationMs);
    moving.setInterpolator(new DecelerateInterpolator());
    moving.addUpdateListener(a -> ring.setExit((float) a.getAnimatedValue()));
    moving.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd(Animator animation) {
        if (ringMove != animation) {
          return; // a cancel has already put the ring where it belongs
        }
        ringMove = null;
        if (leaving) {
          ringGone();
        } else {
          ringLeaving = false;
        }
      }
    });
    ringMove = moving;
    moving.start();
  }

  private void cancelRingMove() {
    ringLeaving = false;
    if (ringMove != null) {
      ValueAnimator running = ringMove;
      ringMove = null; // cleared first, so the end it fires is not read as the move finishing
      running.cancel();
    }
  }

  /**
   * The digits take the centre a ring is leaving, after the beat it needs to get out of them.
   * With no ring there is nothing to hand over, and they are simply there: a solve that starts
   * without an inspection is the plain change into this view, which is still a cut.
   */
  public void digitsIn(View digits) {
    if (!ringLeaving || !enabled(digits.getContext())) {
      return;
    }
    digits.animate().cancel();
    digits.setAlpha(0f);
    digits.setScaleX(DIGITS_FROM);
    digits.setScaleY(DIGITS_FROM);
    digits.animate()
      .alpha(1f)
      .scaleX(1f)
      .scaleY(1f)
      .setStartDelay(DIGITS_WAIT_MS)
      .setDuration(DIGITS_IN_MS)
      .setInterpolator(new DecelerateInterpolator())
      .start();
  }

  /**
   * The screen stands down for a solve: everything but the time fades out and is gone. Each panel
   * is left at its own alpha once hidden, since the strip is shown again on its own to carry the
   * hold countdown.
   */
  public void panelsOut(View... panels) {
    focused = true;
    for (final View panel : panels) {
      panel.animate().cancel();
      panel.setTranslationY(0f);
      if (!enabled(panel.getContext())) {
        panel.setAlpha(1f);
        panel.setVisibility(View.INVISIBLE);
        continue;
      }
      panel.animate().alpha(0f).setStartDelay(0).setDuration(PANELS_OUT_MS)
        .withEndAction(new Runnable() {
          @Override
          public void run() {
            panel.setVisibility(View.INVISIBLE);
            panel.setAlpha(1f);
          }
        }).start();
    }
  }

  /**
   * The screen comes back around the time it was hiding. Only a screen that actually stood down
   * arrives: one being built or turned sideways is simply there, which is what it was doing before.
   */
  public void panelsIn(View... panels) {
    boolean returning = focused;
    focused = false;
    for (View panel : panels) {
      panel.animate().cancel();
      panel.setAlpha(1f);
      panel.setTranslationY(0f);
      panel.setVisibility(View.VISIBLE);
    }
    if (returning) {
      // The same arrival the rest of the app already uses, held until the time has landed: it
      // travels up through where the verdict and the scramble sit, and should not cross them lit.
      EnterAnimation.stagger(PANELS_LEAD_MS, panels);
    }
  }

  /** The time travels to where the screen it is on keeps it, as part of the same change. */
  public void digitsTo(View box, float x, float y) {
    if (ringLeaving || !enabled(box.getContext())) {
      digitsAt(box, x, y); // a ring is handing the centre over: the time belongs there already
      return;
    }
    box.animate().cancel();
    box.animate().translationX(x).translationY(y)
      .setStartDelay(0)
      .setDuration(DIGITS_MOVE_MS)
      .setInterpolator(new DecelerateInterpolator())
      .start();
  }

  /** The time belongs there now, with no movement: a screen being built or turned sideways. */
  public void digitsAt(View box, float x, float y) {
    box.animate().cancel();
    box.setTranslationX(x);
    box.setTranslationY(y);
  }

  /**
   * The pool of light the number stands in while the room is dark, coming up or going out. Slower
   * than the panels, because a light that snaps on reads as a flash rather than as the room
   * changing.
   */
  public void lightsDown(View pool, boolean on) {
    if (pool == null) {
      return;
    }
    pool.animate().cancel();
    if (!enabled(pool.getContext())) {
      pool.setAlpha(on ? 1f : 0f);
      return;
    }
    pool.animate().alpha(on ? 1f : 0f).setStartDelay(0).setDuration(LIGHTS_MS).start();
  }

  /** The digits are wanted as they are, now: a solve ending cannot wait on a fade. */
  public void digitsRest(View digits) {
    digits.animate().cancel();
    digits.animate().setStartDelay(0); // the delay outlives the animation it was set on
    digits.setAlpha(1f);
    digits.setScaleX(1f);
    digits.setScaleY(1f);
  }

  private static boolean enabled(Context context) {
    return Settings.Global.getFloat(context.getContentResolver(),
      Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
  }
}
