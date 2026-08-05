package com.cube.nanotimer.util.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Shader;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;

import java.util.ArrayList;
import java.util.List;

/**
 * The shape of the last solves, drawn small: the only place in the app that shows a trend without
 * opening the graph screen. Time is the axis, so a faster solve sits lower, the way it does on the
 * graph this is a preview of. DNFs are skipped rather than drawn as a gap, and the best solve in
 * the window carries a record-coloured dot at the bottom of the line, which is usually not the
 * lifetime best.
 *
 * <p>The line carries a faded caption naming the window it covers, since a normalized shape says
 * nothing about its own scale. It sits in whichever top corner the line leaves free.
 *
 * <p>Feed it {@link #setTimes} with the times newest first, as the service returns them.
 */
public class SparklineView extends View {

  /** Below this there is no trend to read, and the caller is expected to hide the view. */
  public static final int MIN_TIMES = 5;

  private static final int MAX_POINTS = 50;
  private static final float DRAW_MS = 520f;

  /** Quiet enough to be read only when looked for. */
  private static final int CAPTION_ALPHA = 140;

  private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint recordPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint captionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  private final Path linePath = new Path();
  private final Path fillPath = new Path();
  private final Path drawnPath = new Path();
  private final PathMeasure pathMeasure = new PathMeasure();

  private final List<Long> values = new ArrayList<>();
  private float[] pointsX = new float[0];
  private float[] pointsY = new float[0];
  private int bestIndex = -1;

  private String caption;
  private float captionX;
  private float captionY;

  private float progress = 1f;
  private ValueAnimator animator;

  private final int accentColor;
  private final float dotRadius;
  private final float recordRadius;
  private final float verticalPadding;

  public SparklineView(Context context) {
    this(context, null);
  }

  public SparklineView(Context context, AttributeSet attrs) {
    super(context, attrs);
    float density = getResources().getDisplayMetrics().density;
    dotRadius = 2.5f * density;
    recordRadius = 3.5f * density;
    verticalPadding = 5f * density;

    accentColor = ContextCompat.getColor(context, R.color.lightblue);
    linePaint.setStyle(Paint.Style.STROKE);
    linePaint.setStrokeWidth(2f * density);
    linePaint.setStrokeCap(Paint.Cap.ROUND);
    linePaint.setStrokeJoin(Paint.Join.ROUND);
    linePaint.setColor(accentColor);

    fillPaint.setStyle(Paint.Style.FILL);

    dotPaint.setStyle(Paint.Style.FILL);
    dotPaint.setColor(accentColor);

    recordPaint.setStyle(Paint.Style.FILL);
    recordPaint.setColor(ContextCompat.getColor(context, R.color.new_record));

    captionPaint.setColor(ContextCompat.getColor(context, R.color.secondary_text));
    captionPaint.setTextSize(10f * getResources().getDisplayMetrics().scaledDensity);
  }

  /**
   * @param times solve times newest first; DNFs (negative) are dropped, and only the most recent
   *              {@link #MAX_POINTS} are drawn
   * @param animate replay the draw, for a switch the user just made
   */
  public void setTimes(List<Long> times, boolean animate) {
    values.clear();
    int window = times == null ? 0 : Math.min(times.size(), MAX_POINTS);
    for (int i = window - 1; i >= 0; i--) { // oldest first
      Long time = times.get(i);
      if (time != null && time > 0) {
        values.add(time);
      }
    }
    bestIndex = -1;
    for (int i = 0; i < values.size(); i++) {
      if (bestIndex < 0 || values.get(i) < values.get(bestIndex)) {
        bestIndex = i;
      }
    }
    // The window the line covers, not the points left in it: a DNF is still one of your last 50.
    caption = hasEnoughTimes() ? getResources().getString(R.string.window_last_n, window) : null;
    setContentDescription(caption);
    buildPaths();
    if (animate && hasEnoughTimes() && animationsEnabled()) {
      startDrawAnimation();
    } else {
      cancelAnimation();
      progress = 1f;
      invalidate();
    }
  }

  /** True when there are enough solves left, after DNFs, for the line to mean anything. */
  public boolean hasEnoughTimes() {
    return values.size() >= MIN_TIMES;
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    // The fill fades out downward rather than sitting as a slab under the line.
    fillPaint.setShader(new LinearGradient(0, 0, 0, h,
      (0x40 << 24) | (accentColor & 0xFFFFFF), accentColor & 0xFFFFFF, Shader.TileMode.CLAMP));
    buildPaths();
  }

  @Override
  protected void onDetachedFromWindow() {
    cancelAnimation();
    super.onDetachedFromWindow();
  }

  private void buildPaths() {
    linePath.reset();
    fillPath.reset();
    pointsX = new float[0];
    pointsY = new float[0];
    int width = getWidth();
    int height = getHeight();
    if (width <= 0 || height <= 0 || values.size() < 2) {
      return;
    }

    long min = values.get(0);
    long max = values.get(0);
    for (Long value : values) {
      min = Math.min(min, value);
      max = Math.max(max, value);
    }
    float span = Math.max(1, max - min);
    float usable = height - 2 * verticalPadding;
    float step = (float) (width - 2 * recordRadius) / (values.size() - 1);

    pointsX = new float[values.size()];
    pointsY = new float[values.size()];
    for (int i = 0; i < values.size(); i++) {
      pointsX[i] = recordRadius + i * step;
      pointsY[i] = verticalPadding + (max - values.get(i)) / span * usable;
      if (i == 0) {
        linePath.moveTo(pointsX[i], pointsY[i]);
        fillPath.moveTo(pointsX[i], height);
        fillPath.lineTo(pointsX[i], pointsY[i]);
      } else {
        linePath.lineTo(pointsX[i], pointsY[i]);
        fillPath.lineTo(pointsX[i], pointsY[i]);
      }
    }
    fillPath.lineTo(pointsX[values.size() - 1], height);
    fillPath.close();
    placeCaption(width);
  }

  /**
   * Some point always touches the top, since the line is normalized, so a fixed corner would sit on
   * it half the time. The caption takes the corner the line stays further below.
   */
  private void placeCaption(int width) {
    if (caption == null) {
      return;
    }
    float textWidth = captionPaint.measureText(caption);
    float left = recordRadius;
    float right = width - recordRadius - textWidth;
    captionX = headroomIn(right, width) >= headroomIn(0, left + textWidth) ? right : left;
    captionY = verticalPadding - captionPaint.getFontMetrics().top;
  }

  /** How far the line stays below the top of a horizontal band. Larger is more room. */
  private float headroomIn(float fromX, float toX) {
    float headroom = Float.MAX_VALUE;
    for (int i = 0; i < pointsX.length; i++) {
      if (pointsX[i] >= fromX && pointsX[i] <= toX) {
        headroom = Math.min(headroom, pointsY[i]);
      }
    }
    return headroom;
  }

  private void startDrawAnimation() {
    cancelAnimation();
    animator = ValueAnimator.ofFloat(0f, 1f);
    animator.setDuration((long) DRAW_MS);
    animator.setInterpolator(new DecelerateInterpolator());
    animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
      @Override
      public void onAnimationUpdate(ValueAnimator animation) {
        progress = (float) animation.getAnimatedValue();
        invalidate();
      }
    });
    progress = 0f;
    animator.start();
  }

  private void cancelAnimation() {
    if (animator != null) {
      animator.cancel();
      animator = null;
    }
  }

  private boolean animationsEnabled() {
    return Settings.Global.getFloat(getContext().getContentResolver(),
      Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (values.size() < 2 || linePath.isEmpty()) {
      return;
    }

    fillPaint.setAlpha((int) (255 * progress));
    canvas.drawPath(fillPath, fillPaint);

    if (progress >= 1f) {
      canvas.drawPath(linePath, linePaint);
    } else {
      pathMeasure.setPath(linePath, false);
      drawnPath.reset();
      pathMeasure.getSegment(0f, pathMeasure.getLength() * progress, drawnPath, true);
      canvas.drawPath(drawnPath, linePaint);
    }

    // The dots land once the line has reached them, so nothing floats ahead of the draw.
    drawDotAt(canvas, values.size() - 1, dotPaint, dotRadius);
    if (bestIndex >= 0) {
      drawDotAt(canvas, bestIndex, recordPaint, recordRadius);
    }

    if (caption != null) {
      captionPaint.setAlpha((int) (CAPTION_ALPHA * progress));
      canvas.drawText(caption, captionX, captionY, captionPaint);
    }
  }

  private void drawDotAt(Canvas canvas, int index, Paint paint, float radius) {
    if (index >= pointsX.length || (float) index / (values.size() - 1) > progress) {
      return;
    }
    canvas.drawCircle(pointsX[index], pointsY[index], radius, paint);
  }

}
