package com.cube.nanotimer.util.view;

import android.view.View;

import androidx.core.widget.NestedScrollView;

/**
 * A "more below" mark over the bottom of a scrolling sheet, shown while there is more to scroll to
 * and gone once the end is reached, so a sheet whose first screen looks complete still reads as one
 * that goes on.
 */
public final class ScrollHint {

  private static final long FADE_MS = 180;

  private ScrollHint() {
  }

  /** Shows {@code hint} whenever {@code scroll} can still go down, re-checked on scroll and resize. */
  public static void attach(NestedScrollView scroll, View hint) {
    Runnable update = () -> {
      boolean more = scroll.canScrollVertically(1);
      float target = more ? 1f : 0f;
      if (hint.getTag() == null || (float) hint.getTag() != target) {
        hint.setTag(target);
        hint.animate().cancel();
        hint.animate().alpha(target).setDuration(FADE_MS).start();
      }
    };
    hint.setAlpha(0f);
    // A hint is not a control: touches go through to the list under it.
    hint.setClickable(false);
    scroll.setOnScrollChangeListener(
        (NestedScrollView.OnScrollChangeListener) (v, x, y, oldX, oldY) -> update.run());
    View.OnLayoutChangeListener onLayout = (v, l, t, r, b, ol, ot, or, ob) -> update.run();
    scroll.addOnLayoutChangeListener(onLayout);
    if (scroll.getChildCount() > 0) {
      scroll.getChildAt(0).addOnLayoutChangeListener(onLayout);
    }
  }
}
