package com.cube.nanotimer.util.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * Lays its children out in a row, wrapping onto the next line when one no longer fits. Used for
 * rows of pills, whose number and width are the user's names and so cannot be laid out in advance.
 *
 * <p>Material's {@code ChipGroup} would do this, but its chips require a MaterialComponents theme
 * and the app is themed on AppCompat. Children are spaced by the gap below rather than by their
 * own margins, which this layout ignores.
 */
public class FlowLayout extends ViewGroup {

  private static final int GAP_DP = 6;

  private final int gap;

  public FlowLayout(Context context) {
    this(context, null);
  }

  public FlowLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
    gap = (int) (GAP_DP * getResources().getDisplayMetrics().density);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int available = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
    int childSpec = MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST);
    int x = 0;
    int lineHeight = 0;
    int height = 0;

    for (int i = 0; i < getChildCount(); i++) {
      View child = getChildAt(i);
      if (child.getVisibility() == GONE) {
        continue;
      }
      child.measure(childSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
      if (x > 0 && x + child.getMeasuredWidth() > available) {
        height += lineHeight + gap;
        x = 0;
        lineHeight = 0;
      }
      x += child.getMeasuredWidth() + gap;
      lineHeight = Math.max(lineHeight, child.getMeasuredHeight());
    }
    height += lineHeight;

    setMeasuredDimension(resolveSize(available + getPaddingLeft() + getPaddingRight(), widthMeasureSpec),
      resolveSize(height + getPaddingTop() + getPaddingBottom(), heightMeasureSpec));
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    int available = r - l - getPaddingLeft() - getPaddingRight();
    int x = getPaddingLeft();
    int y = getPaddingTop();
    int lineHeight = 0;

    for (int i = 0; i < getChildCount(); i++) {
      View child = getChildAt(i);
      if (child.getVisibility() == GONE) {
        continue;
      }
      if (x > getPaddingLeft() && x + child.getMeasuredWidth() > getPaddingLeft() + available) {
        y += lineHeight + gap;
        x = getPaddingLeft();
        lineHeight = 0;
      }
      child.layout(x, y, x + child.getMeasuredWidth(), y + child.getMeasuredHeight());
      x += child.getMeasuredWidth() + gap;
      lineHeight = Math.max(lineHeight, child.getMeasuredHeight());
    }
  }
}
