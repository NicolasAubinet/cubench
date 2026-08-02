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
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.vo.SolveStep;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A solve drawn as a bar: one segment per step, its width its share of the solve. Each segment is
 * split into the parts the step was built in (the F2L slots, the two looks of an OLL), and each part
 * into a pale stretch of thinking and a solid one of turning — so the solve reads as think, turn,
 * think, turn.
 *
 * <p>Sizes are taken from the measured height so the view scales with the screen like the rest of
 * the timer layout, which {@link ScalingLinearLayout} cannot do for what a view paints itself.
 */
public class SolveStepBarView extends View {

  private static final int RECOGNITION_ALPHA = 77; // 30%: thinking recedes, turning reads solid

  private static final float STEP_GAP_RATIO = 0.25f;
  private static final float PART_GAP_RATIO = 0.1f;
  private static final float CORNER_RATIO = 0.2f;

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF bounds = new RectF();
  private final Path corners = new Path();

  private final int tailColor;

  private static final float PLAYHEAD_WIDTH_RATIO = 0.12f;

  private List<SolveStep> steps = Collections.emptyList();
  private int[] colors = new int[0];
  private float progress = 1f; // left-to-right reveal fraction; 1 = fully drawn
  private float playhead = -1f; // where a replay has got to, or < 0 for a bar nothing is playing
  private OnSeekListener seekListener;

  public SolveStepBarView(Context context) {
    super(context);
    tailColor = ContextCompat.getColor(context, R.color.gray600);
  }

  public SolveStepBarView(Context context, AttributeSet attributes) {
    super(context, attributes);
    tailColor = ContextCompat.getColor(context, R.color.gray600);
  }

  /** @param colors one per step, in step order */
  public void setSteps(List<SolveStep> steps, int[] colors) {
    this.steps = new ArrayList<>(steps);
    this.colors = colors;
    invalidate();
  }

  /** Told where a replay of this solve has got to, so the bar can say so. */
  public interface OnSeekListener {
    /** @param fraction where along the solve the bar was touched, 0 to 1 */
    void onSeek(float fraction);
  }

  /**
   * Makes the bar seekable. Only a bar given a listener takes touches at all, so the ones that
   * merely describe a solve keep behaving as they did.
   */
  public void setOnSeekListener(OnSeekListener listener) {
    this.seekListener = listener;
    setClickable(listener != null);
  }

  /** Where a replay has got to, 0 to 1, or negative to show no marker. */
  public void setPlayhead(float fraction) {
    this.playhead = fraction;
    invalidate();
  }

  @Override
  public boolean onTouchEvent(android.view.MotionEvent event) {
    if (seekListener == null) {
      return super.onTouchEvent(event);
    }
    int action = event.getActionMasked();
    if (action == android.view.MotionEvent.ACTION_DOWN
        || action == android.view.MotionEvent.ACTION_MOVE) {
      // The bar lives in a scrolling sheet, which would otherwise steal the drag off it.
      getParent().requestDisallowInterceptTouchEvent(true);
      seekListener.onSeek(fractionAt(event.getX()));
      return true;
    }
    if (action == android.view.MotionEvent.ACTION_UP
        || action == android.view.MotionEvent.ACTION_CANCEL) {
      getParent().requestDisallowInterceptTouchEvent(false);
      if (action == android.view.MotionEvent.ACTION_UP) {
        performClick();
      }
      return true;
    }
    return super.onTouchEvent(event);
  }

  @Override
  public boolean performClick() {
    return super.performClick();
  }

  /** The reveal fraction, 0 (nothing) to 1 (whole bar); drives the sweep-in animation. */
  public void setProgress(float progress) {
    this.progress = Math.max(0f, Math.min(1f, progress));
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    long totalMs = totalMs(steps);
    if (totalMs <= 0) {
      return;
    }

    canvas.save();
    if (progress < 1f) { // clip to a growing left-to-right window for the reveal
      canvas.clipRect(0, 0, getWidth() * progress, getHeight());
    }

    float height = getHeight();
    float stepGap = height * STEP_GAP_RATIO;
    float partGap = Math.max(1f, height * PART_GAP_RATIO);
    float corner = height * CORNER_RATIO;
    float width = getWidth() - stepGap * Math.max(0, drawnSteps(steps) - 1);

    // Kept in step with xAt/fractionAt, which lay the same segments out for the playhead and a seek.
    float left = 0;
    for (int i = 0; i < steps.size(); i++) {
      SolveStep step = steps.get(i);
      if (step.getTotalMs() <= 0) {
        continue;
      }
      float stepWidth = width * step.getTotalMs() / totalMs;
      drawStep(canvas, step, colorOf(i), left, stepWidth, height, corner, partGap);
      left += stepWidth + stepGap;
    }
    canvas.restore(); // the playhead marks a moment in the solve, it is not part of the reveal
    drawPlayhead(canvas, height);
  }

  /** Over the segments rather than in them: it marks a moment, it is not part of the solve. */
  private void drawPlayhead(Canvas canvas, float height) {
    if (playhead < 0) {
      return;
    }
    float w = Math.max(2f, height * PLAYHEAD_WIDTH_RATIO);
    float x = xAt(Math.max(0f, Math.min(1f, playhead)));
    paint.setColor(Color.WHITE);
    paint.setAlpha(255);
    canvas.drawRect(Math.min(x, getWidth() - w), 0, Math.min(x + w, getWidth()), height, paint);
  }

  private float xAt(float fraction) {
    return xAt(steps, getWidth(), getHeight(), fraction);
  }

  private float fractionAt(float x) {
    return fractionAt(steps, getWidth(), getHeight(), x);
  }

  /**
   * Where a moment of the solve falls across the bar. The gaps between the steps come out of the
   * width before the segments are shared out and go back one step at a time, so a share of the
   * solve is not a share of the view: reading the playhead off {@code getWidth()} instead ran it a
   * step's worth of gap ahead of the segments inside a step and behind them in the next.
   *
   * <p>Static so a plain unit test can pin it, which one cannot do through a view.
   */
  static float xAt(List<SolveStep> steps, float viewWidth, float viewHeight, float fraction) {
    long totalMs = totalMs(steps);
    float stepGap = viewHeight * STEP_GAP_RATIO;
    float width = viewWidth - stepGap * Math.max(0, drawnSteps(steps) - 1);
    if (totalMs <= 0 || width <= 0) {
      return 0;
    }
    float wanted = fraction * totalMs;
    float left = 0;
    long at = 0;
    for (SolveStep step : steps) {
      if (step.getTotalMs() <= 0) {
        continue;
      }
      float stepWidth = width * step.getTotalMs() / totalMs;
      if (wanted <= at + step.getTotalMs()) {
        return left + stepWidth * Math.max(0, wanted - at) / step.getTotalMs();
      }
      left += stepWidth + stepGap;
      at += step.getTotalMs();
    }
    return viewWidth;
  }

  /** The inverse of {@link #xAt}; a touch landing in a gap belongs to the boundary it draws. */
  static float fractionAt(List<SolveStep> steps, float viewWidth, float viewHeight, float x) {
    long totalMs = totalMs(steps);
    float stepGap = viewHeight * STEP_GAP_RATIO;
    float width = viewWidth - stepGap * Math.max(0, drawnSteps(steps) - 1);
    if (totalMs <= 0 || width <= 0) {
      return 0;
    }
    float left = 0;
    long at = 0;
    for (SolveStep step : steps) {
      if (step.getTotalMs() <= 0) {
        continue;
      }
      float stepWidth = width * step.getTotalMs() / totalMs;
      if (x < left + stepWidth) {
        return Math.max(0f, at + step.getTotalMs() * (x - left) / stepWidth) / totalMs;
      }
      left += stepWidth + stepGap;
      at += step.getTotalMs();
      if (x < left) {
        return at / (float) totalMs;
      }
    }
    return 1f;
  }

  private static long totalMs(List<SolveStep> steps) {
    long totalMs = 0;
    for (SolveStep step : steps) {
      totalMs += step.getTotalMs();
    }
    return totalMs;
  }

  /** A skipped step takes no width, and no gap either. */
  private static int drawnSteps(List<SolveStep> steps) {
    int drawn = 0;
    for (SolveStep step : steps) {
      if (step.getTotalMs() > 0) {
        drawn++;
      }
    }
    return drawn;
  }

  private void drawStep(Canvas canvas, SolveStep step, int color, float left, float stepWidth,
      float height, float corner, float partGap) {
    canvas.save();
    corners.reset();
    bounds.set(left, 0, left + stepWidth, height);
    corners.addRoundRect(bounds, corner, corner, Path.Direction.CW);
    canvas.clipPath(corners);

    List<SolveStep> parts = step.getSubSteps().isEmpty()
        ? Collections.singletonList(step)
        : step.getSubSteps();

    float partLeft = left;
    for (int i = 0; i < parts.size(); i++) {
      SolveStep part = parts.get(i);
      float partWidth = stepWidth * part.getTotalMs() / step.getTotalMs();
      boolean last = i == parts.size() - 1;
      float partRight = partLeft + partWidth - (last ? 0 : partGap); // the gap separates the parts
      float split = partLeft + partWidth * part.getRecognitionMs() / Math.max(1, part.getTotalMs());

      fill(canvas, color, RECOGNITION_ALPHA, partLeft, Math.min(split, partRight), height);
      fill(canvas, color, Color.alpha(color), split, partRight, height);
      partLeft += partWidth;
    }
    canvas.restore();
  }

  private void fill(Canvas canvas, int color, int alpha, float left, float right, float height) {
    if (right <= left) {
      return;
    }
    paint.setColor(color);
    paint.setAlpha(alpha);
    canvas.drawRect(left, 0, right, height, paint);
  }

  /** The tail is deliberately outside the step palette: colouring it like a step would claim it
   * was one. */
  private int colorOf(int step) {
    if (Utils.isTailSegment(steps.get(step).getName())) {
      return tailColor;
    }
    return colors.length == 0 ? Color.WHITE : colors[step % colors.length];
  }
}
