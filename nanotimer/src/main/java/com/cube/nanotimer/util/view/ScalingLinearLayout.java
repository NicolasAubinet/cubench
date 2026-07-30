package com.cube.nanotimer.util.view;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.cube.nanotimer.R;
import com.cube.nanotimer.util.ScaleUtils;

public class ScalingLinearLayout extends LinearLayout {

  private Integer screenWidth;
  private Integer screenHeight;
  private int previousWidth;
  private int previousHeight;

  public ScalingLinearLayout(Context context) {
    super(context);
  }

  public ScalingLinearLayout(Context context, AttributeSet attributes) {
    super(context, attributes);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    refreshScreenScale();
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }

  private void refreshScreenScale() {
    if (screenWidth == null || screenHeight == null) {
      screenWidth = ScaleUtils.getScreenWidth(getContext());
      screenHeight = ScaleUtils.getScreenHeight(getContext());
    }

    if (screenWidth != previousWidth || screenHeight != previousHeight) {
      // the root is left alone, to avoid scaling manual padding (from status bar and navigation bar)
      scaleChildren(this, ScaleUtils.getScale(getContext()));

      previousWidth = screenWidth;
      previousHeight = screenHeight;
    }
  }

  /**
   * Scales a subtree inflated after the pass above has already run. That pass runs once per layout,
   * so a view added later keeps the raw px sizes the layouts are authored in and draws at a fraction
   * of its size. Calling this before the pass is equally fine: a view is only ever scaled once.
   */
  public static void scaleLateSubtree(View root, float scale) {
    scaleViewAndChildren(root, scale);
  }

  // Scale the given view, its contents, and all of its children by the given factor.
  private static void scaleViewAndChildren(View root, float scale) {
    if (Boolean.TRUE.equals(root.getTag(R.id.tag_scaled))) { // scaling twice would compound
      return;
    }
    root.setTag(R.id.tag_scaled, Boolean.TRUE);

    // Retrieve the view's layout information
    ViewGroup.LayoutParams layoutParams = root.getLayoutParams();
    if (layoutParams != null) {
      // Scale the View itself
      if (layoutParams.width != ViewGroup.LayoutParams.MATCH_PARENT && layoutParams.width != ViewGroup.LayoutParams.WRAP_CONTENT) {
        layoutParams.width *= scale;
      }
      if (layoutParams.height != ViewGroup.LayoutParams.MATCH_PARENT && layoutParams.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
        layoutParams.height *= scale;
      }

      // If the View has margins, scale those too
      if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
        ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) layoutParams;
        marginParams.leftMargin *= scale;
        marginParams.topMargin *= scale;
        marginParams.rightMargin *= scale;
        marginParams.bottomMargin *= scale;
      }
      root.setLayoutParams(layoutParams);
    }

    // Same treatment for padding
    root.setPadding(
      (int) (root.getPaddingLeft() * scale),
      (int) (root.getPaddingTop() * scale),
      (int) (root.getPaddingRight() * scale),
      (int) (root.getPaddingBottom() * scale)
    );

    // If it's a TextView, scale the font size
    if (root instanceof TextView) {
      TextView tv = (TextView) root;
      tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, tv.getTextSize() * scale);
    }

    scaleChildren(root, scale);
  }

  // If it's a ViewGroup, recurse!
  private static void scaleChildren(View root, float scale) {
    if (root instanceof ViewGroup) {
      ViewGroup vg = (ViewGroup) root;
      for (int i = 0; i < vg.getChildCount(); i++) {
        scaleViewAndChildren(vg.getChildAt(i), scale);
      }
    }
  }

}
