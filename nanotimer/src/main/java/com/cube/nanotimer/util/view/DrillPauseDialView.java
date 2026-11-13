package com.cube.nanotimer.util.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.cube.nanotimer.R;
import com.cube.nanotimer.util.FormatterService;

import java.util.List;
import java.util.Locale;

/**
 * The dial the finished rep sits in during the pause between drill cases. The inner ring is the
 * rep's split going round from the top, each part its share of the circle in the colours of the rep
 * line's bar; the thin outer ring is the pause itself, running out clockwise. Under the time, which
 * is the rep line's own and is placed by {@link DrillRepPause}, each part's name and figure.
 *
 * <p>Drawing only: the pause decides where the dial stands and animates {@link #setSweep},
 * {@link #setRemaining} and {@link #setFiguresAlpha}.
 */
public class DrillPauseDialView extends View {

  private static final float RING_DP = 7;
  private static final float DRAIN_DP = 2.5f;
  private static final float DRAIN_GAP_DP = 13;
  /** Between two parts of the ring, in degrees, on top of what the round caps take. */
  private static final float PART_GAP_DEG = 3.5f;
  private static final int DISC_ALPHA = 90;
  private static final float LABEL_SP = 9.5f;
  private static final float FIGURE_SP = 18;
  private static final float LABEL_GAP_DP = 3;
  private static final float ROW_GAP_DP = 10;
  private static final float COLUMN_GAP_DP = 22;
  private static final float SIDE_PAD_DP = 14;

  private final float density;
  private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint drain = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint figure = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF oval = new RectF();
  private final int track;
  private final int drainColor;

  private long[] times = new long[0];
  private int[] colors = new int[0];
  private String[] labels = new String[0];
  private String[] figures = new String[0];
  private int[] figureColors = new int[0];
  private float column;
  private float centreY;
  private float radius;
  private float figuresTop;
  private float sweep = 1f;
  private float remaining = 1f;
  private float figuresAlpha = 1f;

  public DrillPauseDialView(Context context) {
    this(context, null);
  }

  public DrillPauseDialView(Context context, AttributeSet attributes) {
    super(context, attributes);
    density = getResources().getDisplayMetrics().density;
    disc.setColor(0xFF000000);
    ring.setStyle(Paint.Style.STROKE);
    ring.setStrokeCap(Paint.Cap.ROUND);
    ring.setStrokeWidth(RING_DP * density);
    drain.setStyle(Paint.Style.STROKE);
    drain.setStrokeCap(Paint.Cap.ROUND);
    drain.setStrokeWidth(DRAIN_DP * density);
    track = ContextCompat.getColor(context, R.color.white_wash_10);
    drainColor = ContextCompat.getColor(context, R.color.white);
    float sp = getResources().getDisplayMetrics().scaledDensity;
    label.setTextAlign(Paint.Align.CENTER);
    label.setTextSize(LABEL_SP * sp);
    label.setLetterSpacing(0.16f);
    label.setColor(ContextCompat.getColor(context, R.color.secondary_text));
    figure.setTextAlign(Paint.Align.CENTER);
    figure.setTextSize(FIGURE_SP * sp);
    figure.setFontFeatureSettings("tnum");
    if (!isInEditMode()) {
      label.setTypeface(ResourcesCompat.getFont(context, R.font.roboto_medium));
      figure.setTypeface(ResourcesCompat.getFont(context, R.font.roboto_bold));
    }
  }

  /** The rep's parts, or none for a rep with nothing to split (a skipped one): a bare ring then. */
  public void setParts(List<DrillRepParts.Part> parts) {
    int n = parts == null ? 0 : parts.size();
    times = new long[n];
    colors = new int[n];
    labels = new String[n];
    figures = new String[n];
    figureColors = new int[n];
    for (int i = 0; i < n; i++) {
      DrillRepParts.Part part = parts.get(i);
      times[i] = part.getMs();
      colors[i] = part.getColor();
      labels[i] = part.getName(getContext()).toUpperCase(Locale.getDefault());
      figures[i] = FormatterService.INSTANCE.formatSolveTime(part.getMs());
      figureColors[i] = part.getFigureColor();
    }
    column = 0;
    for (int i = 0; i < n; i++) {
      column = Math.max(column, Math.max(label.measureText(labels[i]),
          figure.measureText(figures[i])));
    }
    invalidate();
  }

  /** Where the dial stands, in this view's coordinates, and where the figures under the time go. */
  public void place(float centreY, float radius, float figuresTop) {
    this.centreY = centreY;
    this.radius = radius;
    this.figuresTop = figuresTop;
    invalidate();
  }

  /** How far round the split ring is drawn, 0 to 1: it sweeps in as the pause opens. */
  public void setSweep(float sweep) {
    this.sweep = sweep;
    invalidate();
  }

  /** What is left of the pause, 1 to 0. */
  public void setRemaining(float remaining) {
    this.remaining = remaining;
    invalidate();
  }

  public void setFiguresAlpha(float alpha) {
    figuresAlpha = alpha;
    invalidate();
  }

  /** The room the dial leaves outside its ring: the drain ring and its stroke. */
  public float outerReach() {
    return (DRAIN_GAP_DP + DRAIN_DP) * density;
  }

  /** How tall the figures under the time stand, for the pause to centre the whole. */
  public float figuresHeight() {
    int rows = rows();
    if (rows == 0) {
      return 0;
    }
    float row = label.getTextSize() + LABEL_GAP_DP * density + figure.getTextSize();
    return rows * row + (rows - 1) * ROW_GAP_DP * density;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (radius <= 0) {
      return;
    }
    float cx = getWidth() / 2f;
    disc.setAlpha(Math.round(DISC_ALPHA * Math.min(1f, sweep * 2)));
    canvas.drawCircle(cx, centreY, radius, disc);
    drawSplit(canvas, cx);
    drawDrain(canvas, cx);
    drawFigures(canvas, cx);
  }

  private void drawSplit(Canvas canvas, float cx) {
    oval.set(cx - radius, centreY - radius, cx + radius, centreY + radius);
    long total = 0;
    int shown = 0;
    for (long time : times) {
      total += time;
      shown += time > 0 ? 1 : 0;
    }
    float reach = 360f * sweep;
    if (total == 0) {
      ring.setColor(track);
      canvas.drawArc(oval, -90, reach, false, ring);
      return;
    }
    // A round cap reaches past the arc's end by half the stroke, so the gap is widened to match
    float caps = (float) Math.toDegrees(ring.getStrokeWidth() / radius);
    float gap = shown > 1 ? PART_GAP_DEG + caps : 0;
    float start = 0;
    for (int i = 0; i < times.length; i++) {
      if (times[i] <= 0) {
        continue;
      }
      float extent = 360f * times[i] / total;
      float from = start + gap / 2;
      float to = Math.min(start + extent - gap / 2, reach);
      start += extent;
      if (to <= from) {
        continue;
      }
      ring.setColor(colors[i]);
      canvas.drawArc(oval, -90 + from, to - from, false, ring);
    }
  }

  private void drawDrain(Canvas canvas, float cx) {
    float r = radius + DRAIN_GAP_DP * density;
    oval.set(cx - r, centreY - r, cx + r, centreY + r);
    drain.setColor(track);
    drain.setAlpha(Math.round(Color.alpha(track) * sweep));
    canvas.drawArc(oval, -90, 360, false, drain);
    if (remaining <= 0) {
      return;
    }
    drain.setColor(drainColor);
    drain.setAlpha(Math.round(150 * sweep));
    canvas.drawArc(oval, -90 + 360 * (1 - remaining), 360 * remaining, false, drain);
  }

  private void drawFigures(Canvas canvas, float cx) {
    int n = figures.length;
    if (n == 0 || figuresAlpha <= 0) {
      return;
    }
    int perRow = n <= 2 ? n : 2;
    float pitch = column + COLUMN_GAP_DP * density;
    float rowWidth = perRow * column + (perRow - 1) * COLUMN_GAP_DP * density;
    // Shrunk to the circle's width where the last row stands, for a large font
    float lowest = figuresTop + figuresHeight() - centreY;
    float chord = 2 * (float) Math.sqrt(Math.max(0, radius * radius - lowest * lowest))
        - 2 * SIDE_PAD_DP * density;
    float fit = rowWidth > chord && chord > 0 ? chord / rowWidth : 1f;
    canvas.save();
    canvas.scale(fit, fit, cx, figuresTop);
    float rowHeight = label.getTextSize() + LABEL_GAP_DP * density + figure.getTextSize();
    int alpha = Math.round(255 * figuresAlpha);
    for (int i = 0; i < n; i++) {
      int row = i / perRow;
      int inRow = Math.min(perRow, n - row * perRow);
      float x = cx + ((i % perRow) - (inRow - 1) / 2f) * pitch;
      float top = figuresTop + row * (rowHeight + ROW_GAP_DP * density);
      label.setAlpha(alpha);
      canvas.drawText(labels[i], x, top - label.ascent(), label);
      figure.setColor(figureColors[i]);
      figure.setAlpha(Math.round(Color.alpha(figureColors[i]) * figuresAlpha));
      canvas.drawText(figures[i], x,
          top + label.getTextSize() + LABEL_GAP_DP * density - figure.ascent(), figure);
    }
    canvas.restore();
  }

  private int rows() {
    return (figures.length + 1) / 2;
  }
}
