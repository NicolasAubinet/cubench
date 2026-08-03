package com.cube.nanotimer.util.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.provider.Settings;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * The end of an inspection as one movement: the ring collapses back into the point it was drawn
 * around and the digits rise out of that same point, so the count reads as becoming the time rather
 * than being cut away the frame before it appears.
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

  private final InspectionRingView ring;

  private ValueAnimator ringOut;

  public FocusTransition(InspectionRingView ring) {
    this.ring = ring;
  }

  /** Inspection is starting: whatever the ring was doing, it is here and whole. */
  public void ringIn() {
    cancelRingOut();
    ring.setExit(0f);
  }

  /**
   * Inspection is over. The ring collapses back into its own centre rather than being cut, and
   * keeps that centre until it has finished. A second call while that runs joins the collapse
   * already going: the end of an inspection reaches this twice, once as the inspection stopping
   * and once as the solve starting.
   */
  public void ringOut() {
    if (ringOut != null) {
      return;
    }
    if (!ring.isUp() || !enabled(ring.getContext())) {
      ringGone(); // there was no ring to take away, or nothing here is allowed to take time
      return;
    }
    ringOut = ValueAnimator.ofFloat(0f, 1f);
    ringOut.setDuration(RING_OUT_MS);
    // It gives the centre up at once and then finishes gently, so the digits arrive over a ring
    // that is already a faint dot rather than over a ring still at its full size.
    ringOut.setInterpolator(new DecelerateInterpolator());
    ringOut.addUpdateListener(a -> ring.setExit((float) a.getAnimatedValue()));
    ringOut.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd(Animator animation) {
        if (ringOut == animation) { // a cancel has already put the ring where it belongs
          ringGone();
        }
      }
    });
    ringOut.start();
  }

  /** The ring is away and gives its centre back: the end of a collapse, or instead of one. */
  public void ringGone() {
    cancelRingOut();
    ring.setCenterY(null);
    ring.stop();
  }

  private void cancelRingOut() {
    if (ringOut != null) {
      ValueAnimator running = ringOut;
      ringOut = null; // cleared first, so the end it fires is not read as the fade finishing
      running.cancel();
    }
  }

  /**
   * The digits take the centre a ring is leaving, after the beat it needs to get out of them.
   * With no ring there is nothing to hand over, and they are simply there: a solve that starts
   * without an inspection is the plain change into this view, which is still a cut.
   */
  public void digitsIn(View digits) {
    if (ringOut == null || !enabled(digits.getContext())) {
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
