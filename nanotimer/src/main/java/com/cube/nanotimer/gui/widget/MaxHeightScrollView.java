package com.cube.nanotimer.gui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.ScrollView;

import com.cube.nanotimer.R;

/** A scroll view as tall as its content, up to {@code android:maxHeight}; past that it scrolls. */
public class MaxHeightScrollView extends ScrollView {

  private final int maxHeight;

  public MaxHeightScrollView(Context context, AttributeSet attrs) {
    super(context, attrs);
    TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MaxHeightScrollView);
    maxHeight = a.getDimensionPixelSize(R.styleable.MaxHeightScrollView_android_maxHeight, 0);
    a.recycle();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    if (maxHeight > 0) {
      int size = MeasureSpec.getSize(heightMeasureSpec);
      int capped = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED
          ? maxHeight : Math.min(size, maxHeight);
      heightMeasureSpec = MeasureSpec.makeMeasureSpec(capped, MeasureSpec.AT_MOST);
    }
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }
}
