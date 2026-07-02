package com.cube.nanotimer.util.view;

import android.content.Context;
import androidx.appcompat.widget.AppCompatTextView;
import android.util.AttributeSet;
import android.util.TypedValue;
import com.cube.nanotimer.Options;

public class DigitalTextView extends AppCompatTextView {

  private float baseTextSizePx = -1f;

  public DigitalTextView(Context context) {
    super(context);
    setFont();
  }

  public DigitalTextView(Context context, AttributeSet attrs) {
    super(context, attrs);
    setFont();
  }

  public DigitalTextView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    setFont();
  }

  public void setFont() {
    if (!isInEditMode()) {
      if (baseTextSizePx < 0f) {
        baseTextSizePx = getTextSize();
      }
      TimerFont font = Options.INSTANCE.getTimerFont();
      font.applyTo(this);
      setTextSize(TypedValue.COMPLEX_UNIT_PX, baseTextSizePx * font.getSizeScale());
    }
  }

  /**
   * Shrinks the unscaled base size (e.g. so very long times fit on smaller cubes). The active
   * font's size scale is re-applied on top, so this composes with {@link #setFont()}.
   */
  public void reduceBaseTextSize(float px) {
    if (baseTextSizePx < 0f) {
      baseTextSizePx = getTextSize();
    }
    baseTextSizePx = Math.max(0f, baseTextSizePx - px);
    setFont();
  }

}
