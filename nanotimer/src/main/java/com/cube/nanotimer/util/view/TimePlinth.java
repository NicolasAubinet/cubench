package com.cube.nanotimer.util.view;

import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.util.ScaleUtils;
import com.cube.nanotimer.vo.CubeType;

/**
 * The short rule under the time on the timer screen, in the puzzle's own hue.
 *
 * <p>The one piece of colour the upper screen carries. It stands the digits on something without
 * putting a surface behind them, which is the whole grammar of that screen: a bare mat, and nothing
 * on it with an edge a press could outline.
 *
 * <p><b>Placed off the baseline, never off the box.</b> The digits sit in a box with their font's
 * descent inside it — some two thirds of a rule's own height of empty space at this size — and the
 * timer font is a user setting, so every face puts its baseline somewhere different. Measured off
 * the glyphs there is only one number to hold, {@link #UNDER_GLYPHS_PX}; measured off the box there
 * would be one per font, and the setting would quietly move the rule.
 *
 * <p>It hangs below its own parent by design (the parent must not clip), so it costs the layout
 * nothing: the block below the digits sits exactly where it did before there was a rule here.
 */
public final class TimePlinth {

  /** How far under the glyphs the rule sits, in the px the timer layouts are authored in. */
  private static final int UNDER_GLYPHS_PX = 18;

  private final View plinth;
  private final TextView digits;

  public TimePlinth(View plinth, TextView digits) {
    this.plinth = plinth;
    this.digits = digits;
    if (plinth != null && digits != null) {
      digits.addOnLayoutChangeListener(follow);
      place();
    }
  }

  /** The hue, which only {@link PuzzleIcons} knows. */
  public void setCubeType(CubeType cubeType) {
    if (plinth == null || plinth.getBackground() == null) {
      return;
    }
    // Mutated: the shape is a resource, and its constant state is shared with anything else
    // drawn from it.
    plinth.getBackground().mutate().setTint(
        ContextCompat.getColor(plinth.getContext(), PuzzleIcons.colorForCubeType(cubeType)));
  }

  /** Whether the screen is resting. A solve strips the screen down, and this goes with the rest. */
  public void setShown(boolean shown) {
    if (plinth != null) {
      plinth.setVisibility(shown ? View.VISIBLE : View.INVISIBLE);
    }
  }

  /** The digits changed size: a new font, a new size setting, or a 7x7 asking for smaller ones. */
  private void place() {
    if (!(plinth.getLayoutParams() instanceof FrameLayout.LayoutParams) || digits.getBaseline() < 0) {
      return;
    }
    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) plinth.getLayoutParams();
    int top = digits.getTop() + digits.getBaseline()
        + Math.round(UNDER_GLYPHS_PX * ScaleUtils.getScale(plinth.getContext()));
    if (params.topMargin != top) {
      params.topMargin = top;
      plinth.setLayoutParams(params);
    }
  }

  private final View.OnLayoutChangeListener follow = new View.OnLayoutChangeListener() {
    @Override
    public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
      // After the pass, not inside it: this asks for another one.
      v.post(new Runnable() {
        @Override
        public void run() {
          place();
        }
      });
    }
  };
}
