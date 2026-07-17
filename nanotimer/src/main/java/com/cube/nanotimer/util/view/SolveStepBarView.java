package com.cube.nanotimer.util.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
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

  private List<SolveStep> steps = Collections.emptyList();
  private int[] colors = new int[0];

  public SolveStepBarView(Context context) {
    super(context);
  }

  public SolveStepBarView(Context context, AttributeSet attributes) {
    super(context, attributes);
  }

  /** @param colors one per step, in step order */
  public void setSteps(List<SolveStep> steps, int[] colors) {
    this.steps = new ArrayList<>(steps);
    this.colors = colors;
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    long totalMs = 0;
    int drawnSteps = 0;
    for (SolveStep step : steps) {
      totalMs += step.getTotalMs();
      if (step.getTotalMs() > 0) { // a skipped step takes no width, and no gap either
        drawnSteps++;
      }
    }
    if (totalMs <= 0) {
      return;
    }

    float height = getHeight();
    float stepGap = height * STEP_GAP_RATIO;
    float partGap = Math.max(1f, height * PART_GAP_RATIO);
    float corner = height * CORNER_RATIO;
    float width = getWidth() - stepGap * Math.max(0, drawnSteps - 1);

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

  private int colorOf(int step) {
    return colors.length == 0 ? Color.WHITE : colors[step % colors.length];
  }
}
