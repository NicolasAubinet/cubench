package com.cube.nanotimer.util.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ImageSpan;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;

/** A button label behind the light blue smart cube mark: the action waits on a cube connecting. */
public final class SmartCubeMarkLabel {

  private SmartCubeMarkLabel() {
  }

  public static CharSequence of(Context context, CharSequence text, int sizeDp) {
    Drawable mark = ContextCompat.getDrawable(context, R.drawable.ic_smart_cube).mutate();
    mark.setColorFilter(ContextCompat.getColor(context, R.color.lightblue),
        PorterDuff.Mode.SRC_IN);
    int size = Math.round(sizeDp * context.getResources().getDisplayMetrics().density);
    mark.setBounds(0, 0, size, size);
    SpannableString label = new SpannableString("  " + text);
    label.setSpan(new MarkSpan(mark), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    return label;
  }

  /** The mark centred on the text's middle, without making the line any taller. */
  private static class MarkSpan extends ImageSpan {

    MarkSpan(Drawable drawable) {
      super(drawable);
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end,
        Paint.FontMetricsInt fm) {
      return getDrawable().getBounds().right;
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y,
        int bottom, Paint paint) {
      Drawable drawable = getDrawable();
      Paint.FontMetricsInt metrics = paint.getFontMetricsInt();
      float middle = y + (metrics.ascent + metrics.descent) / 2f;
      canvas.save();
      canvas.translate(x, middle - drawable.getBounds().height() / 2f);
      drawable.draw(canvas);
      canvas.restore();
    }
  }
}
