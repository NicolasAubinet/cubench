package com.cube.nanotimer.util.view;

import android.animation.ValueAnimator;
import android.text.Spannable;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.cube.nanotimer.R;
import com.cube.nanotimer.util.ScrambleFormatterService;
import com.cube.nanotimer.util.helper.GUIUtils;
import java.util.List;

/**
 * Gives the smart-cube scramble-follow a subtle life: the move you are on sits a touch larger, and
 * the moment you turn it, it shrinks back and fades to grey as the next move grows up to take its
 * place. Purely cosmetic — it owns all of the letter animation so the timer never has to.
 *
 * <p>The caller hands over the already-coloured progress text (executed grey, current yellow) and
 * the number of moves done; this decorates the executed and current moves with {@link ScaledMoveSpan}
 * and, when exactly one move was just completed, tweens the hand-off. Scaled moves keep their normal
 * width, so nothing reflows.
 */
public class ScrambleFollowAnimator {

  private static final float GROWN_SCALE = 1.18f;
  private static final float DONE_SCALE = 0.9f;
  private static final long TRANSITION_MS = 180;

  private final TextView textView;
  private final int currentColor;
  private final int doneColor;

  private int lastDoneCount = -1;
  private ValueAnimator animator;
  private ScaledMoveSpan currentSpan;  // the move now expected (yellow, grown)
  private ScaledMoveSpan executedSpan; // the move just turned (fading grey, shrinking)

  public ScrambleFollowAnimator(TextView textView) {
    this.textView = textView;
    this.currentColor = ContextCompat.getColor(textView.getContext(), R.color.cube_yellow);
    this.doneColor = ContextCompat.getColor(textView.getContext(), R.color.gray600);
  }

  /** Forget the progress so the next {@link #show} starts fresh, without a hand-off tween. */
  public void reset() {
    cancel();
    lastDoneCount = -1;
  }

  /**
   * @param text the coloured progress text; decorated in place
   * @param doneCount how many scramble moves are done — move {@code doneCount} is the current one
   */
  public void show(Spannable text, int doneCount) {
    cancel();
    List<int[]> tokens = ScrambleFormatterService.INSTANCE.moveTokenRanges(text);
    boolean handOff = lastDoneCount >= 0 && doneCount == lastDoneCount + 1;
    currentSpan = null;
    executedSpan = null;

    for (int i = 0; i < tokens.size(); i++) {
      if (i < doneCount) {
        boolean justExecuted = handOff && i == doneCount - 1;
        ScaledMoveSpan span = attach(text, tokens.get(i),
            justExecuted ? GROWN_SCALE : DONE_SCALE, justExecuted ? currentColor : doneColor);
        if (justExecuted) {
          executedSpan = span;
        }
      } else if (i == doneCount) {
        currentSpan = attach(text, tokens.get(i), handOff ? 1f : GROWN_SCALE, currentColor);
      }
      // pending moves keep the base column colour and normal size
    }

    textView.setText(text);
    lastDoneCount = doneCount;
    if (handOff) {
      animateHandOff();
    } else {
      textView.invalidate();
    }
  }

  private ScaledMoveSpan attach(Spannable text, int[] token, float scale, int color) {
    ScaledMoveSpan span = new ScaledMoveSpan(scale, color, true);
    text.setSpan(span, token[0], token[1], Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
    return span;
  }

  private void animateHandOff() {
    animator = ValueAnimator.ofFloat(0f, 1f);
    animator.setDuration(TRANSITION_MS);
    animator.setInterpolator(new DecelerateInterpolator());
    animator.addUpdateListener(a -> {
      float p = (float) a.getAnimatedValue();
      if (currentSpan != null) {
        currentSpan.setScale(lerp(1f, GROWN_SCALE, p));
      }
      if (executedSpan != null) {
        executedSpan.setScale(lerp(GROWN_SCALE, DONE_SCALE, p));
        executedSpan.setColor(GUIUtils.getColorCodeBetween(currentColor, doneColor, p));
      }
      textView.invalidate();
    });
    animator.start();
  }

  private void cancel() {
    if (animator != null) {
      animator.cancel();
      animator = null;
    }
  }

  private static float lerp(float from, float to, float t) {
    return from + (to - from) * t;
  }
}
