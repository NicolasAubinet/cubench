package com.cube.nanotimer.util.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;

/**
 * A step's case set as one bar: standard, not standard and not seen. The ring's one hue in three
 * steps ({@link KnowledgeRingView}), since these too are shares of a fixed set rather than kinds.
 */
public class AlgorithmFiguresBarView extends View {

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF bounds = new RectF();
  private final Path clip = new Path();
  private final int trackColor;
  private final int color;

  private int standard;
  private int notStandard;
  private int total;

  public AlgorithmFiguresBarView(Context context) {
    this(context, null);
  }

  public AlgorithmFiguresBarView(Context context, AttributeSet attributes) {
    super(context, attributes);
    trackColor = ContextCompat.getColor(context, R.color.hero_inset);
    color = ContextCompat.getColor(context, R.color.lightblue);
  }

  public void setCounts(int standard, int notStandard, int total) {
    this.standard = standard;
    this.notStandard = notStandard;
    this.total = total;
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (total <= 0) {
      return;
    }
    float radius = getHeight() / 2f;
    bounds.set(0, 0, getWidth(), getHeight());
    clip.reset();
    clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
    canvas.save();
    canvas.clipPath(clip);
    paint.setColor(trackColor);
    canvas.drawRect(bounds, paint);

    float standardEnd = getWidth() * (float) standard / total;
    float notStandardEnd = getWidth() * (float) (standard + notStandard) / total;
    paint.setColor(color);
    paint.setAlpha(KnowledgeRingView.SEEN_ALPHA);
    canvas.drawRect(standardEnd, 0, notStandardEnd, getHeight(), paint);
    paint.setAlpha(Color.alpha(color));
    canvas.drawRect(0, 0, standardEnd, getHeight(), paint);
    canvas.restore();
  }
}
