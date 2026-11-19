package com.cube.nanotimer.util.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;

/** A share as a ring filled that far round from the top, beside the figure that says it. */
public class ShareRingView extends View {

  /** Of the view's size: how thick the ring is drawn. */
  private static final float STROKE_RATIO = 0.15f;

  private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF oval = new RectF();
  private float fraction;

  public ShareRingView(Context context) {
    this(context, null);
  }

  public ShareRingView(Context context, AttributeSet attributes) {
    super(context, attributes);
    track.setStyle(Paint.Style.STROKE);
    track.setColor(ContextCompat.getColor(context, R.color.case_alg_line));
    arc.setStyle(Paint.Style.STROKE);
    arc.setStrokeCap(Paint.Cap.ROUND);
  }

  /** @param percent the share, 0 to 100 */
  public void setShare(float percent, int color) {
    fraction = Math.max(0, Math.min(1, percent / 100f));
    arc.setColor(color);
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    float size = Math.min(getWidth(), getHeight());
    float stroke = size * STROKE_RATIO;
    track.setStrokeWidth(stroke);
    arc.setStrokeWidth(stroke);
    oval.set(stroke / 2, stroke / 2, size - stroke / 2, size - stroke / 2);
    canvas.drawOval(oval, track);
    if (fraction > 0) {
      // At least a dot, so a share under one percent still shows on the ring.
      canvas.drawArc(oval, -90, Math.max(1, fraction * 360), false, arc);
    }
  }
}
