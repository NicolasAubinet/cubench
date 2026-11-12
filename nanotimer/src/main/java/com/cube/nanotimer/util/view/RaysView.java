package com.cube.nanotimer.util.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** Rays of light fanning out from the centre and fading at the edge, behind a reward's badge. */
public class RaysView extends View {

  private static final int RAYS = 14;
  /** Each ray's share of its slice of the circle. */
  private static final float RAY_FILL = 0.45f;

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path ray = new Path();
  private int color = Color.WHITE;

  public RaysView(Context context) {
    super(context);
  }

  public RaysView(Context context, AttributeSet attributes) {
    super(context, attributes);
  }

  public void setColor(int color) {
    this.color = color;
    rebuild(getWidth(), getHeight());
    invalidate();
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    rebuild(w, h);
  }

  private void rebuild(int w, int h) {
    float radius = Math.min(w, h) / 2f;
    if (radius <= 0) {
      return;
    }
    int clear = color & 0x00FFFFFF;
    int mid = (0x66 << 24) | clear;
    paint.setShader(new RadialGradient(w / 2f, h / 2f, radius,
        new int[] {mid, mid, clear}, new float[] {0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
    ray.reset();
    double slice = 2 * Math.PI / RAYS;
    for (int i = 0; i < RAYS; i++) {
      double from = i * slice;
      double to = from + slice * RAY_FILL;
      ray.moveTo(w / 2f, h / 2f);
      ray.lineTo(w / 2f + (float) Math.cos(from) * radius, h / 2f + (float) Math.sin(from) * radius);
      ray.lineTo(w / 2f + (float) Math.cos(to) * radius, h / 2f + (float) Math.sin(to) * radius);
      ray.close();
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    canvas.drawPath(ray, paint);
  }
}
