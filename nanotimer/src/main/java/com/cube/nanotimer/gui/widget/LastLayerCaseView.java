package com.cube.nanotimer.gui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.cube.nanotimer.R;
import com.cube.nanotimer.smartcube.step.LastLayerDiagram;
import com.cube.nanotimer.util.helper.Utils;

/**
 * A last-layer case drawn the way every chart draws one: the layer face square on, the twelve
 * stickers that face sideways as tabs around it, and on a permutation an arrow from each piece to
 * where it belongs.
 *
 * <p>The layer is yellow and the sides are the standard scheme whatever colour the user's own last
 * layer is. This is a chart rather than a picture of their cube — the drill screen's 3D cube is the
 * one that follows them — and a chart that changed colour with a setting would stop matching every
 * other chart they have ever read a case off.
 *
 * <p><b>The chart brings its own ground.</b> Stickers this grey on a card of about the same grey
 * are a picture with nothing to be a picture on, so the view paints a lit well behind them and the
 * card that used to surround it is gone. Turned down, it drops the well for an edge and draws the
 * stickers themselves quieter: fading the whole tile pulls value, chroma and contrast down together
 * and leaves the yellow olive.
 */
public class LastLayerCaseView extends View {

  /** Of the whole square: how much a side tab takes, and the gap between stickers. */
  private static final float TAB = 0.11f;
  private static final float GAP = 0.022f;

  private static final float CORNER_RADIUS = 0.035f;
  private static final float ARROW_HEAD = 0.075f;

  /** Of the whole square: the margin the chart keeps inside its well, and the well's own radius. */
  private static final float STAGE_PAD = 0.055f;
  private static final float STAGE_RADIUS = 0.10f;

  /** How much of a sticker is left on a case that is turned down, and how much of a blank one. */
  private static final float DIM = 0.42f;
  private static final float DIM_BLANK = 0.55f;

  private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint arrowLine = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint arrowHead = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF sticker = new RectF();
  private final RectF ground = new RectF();
  private final Path head = new Path();

  private GradientDrawable stage;
  private int layerColor;
  private int blankColor;
  private int arrowColor;
  private int groundColor;

  private LastLayerDiagram diagram;
  private boolean dimmed;

  public LastLayerCaseView(Context context) {
    super(context);
    init();
  }

  public LastLayerCaseView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  private void init() {
    layerColor = color(R.color.case_layer);
    blankColor = color(R.color.case_blank);
    arrowColor = color(R.color.case_arrow);
    groundColor = color(R.color.dialog_surface);
    fill.setStyle(Paint.Style.FILL);
    arrowLine.setStyle(Paint.Style.STROKE);
    arrowLine.setStrokeCap(Paint.Cap.ROUND);
    arrowHead.setStyle(Paint.Style.FILL);
    edge.setStyle(Paint.Style.STROKE);
    edge.setColor(color(R.color.case_off_edge));
    // Mutated: the radius follows the chart's size, and the constant state is shared with every
    // other chart on the screen.
    stage = (GradientDrawable) ContextCompat.getDrawable(getContext(), R.drawable.case_stage)
        .mutate();
  }

  public void setDiagram(LastLayerDiagram diagram) {
    this.diagram = diagram;
    invalidate();
  }

  /** A case the drill will not deal: the well goes, an edge stays, and the stickers go quiet. */
  public void setDimmed(boolean dimmed) {
    this.dimmed = dimmed;
    invalidate();
  }

  /** Square, and off the width unless the height was pinned: a chart in a list has no height of
   * its own to be square with, and taking the one it is offered leaves it drawn as nothing. */
  @Override
  protected void onMeasure(int widthSpec, int heightSpec) {
    int width = MeasureSpec.getSize(widthSpec);
    int size = MeasureSpec.getMode(heightSpec) == MeasureSpec.EXACTLY
        ? Math.min(width, MeasureSpec.getSize(heightSpec)) : width;
    setMeasuredDimension(size, size);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    float full = Math.min(getWidth(), getHeight());
    drawGround(canvas, full);
    if (diagram == null) {
      return;
    }
    float pad = full * STAGE_PAD;
    canvas.save();
    canvas.translate(pad, pad);
    float size = full - 2 * pad;
    float tab = size * TAB;
    float gap = size * GAP;
    float cell = (size - 2 * (tab + gap) - 2 * gap) / 3;
    float origin = tab + gap;
    float radius = size * CORNER_RADIUS;

    for (int index = 0; index < 9; index++) {
      float left = origin + (index % 3) * (cell + gap);
      float top = origin + (index / 3) * (cell + gap);
      sticker.set(left, top, left + cell, top + cell);
      fill.setColor(ink(diagram.isOriented(index) ? layerColor : blankColor));
      canvas.drawRoundRect(sticker, radius, radius, fill);
    }

    for (int index = 0; index < 12; index++) {
      int along = index % 3;
      float at = origin + along * (cell + gap);
      switch (index / 3) {
        case 0: sticker.set(at, 0, at + cell, tab); break;
        case 1: sticker.set(size - tab, at, size, at + cell); break;
        case 2: sticker.set(at, size - tab, at + cell, size); break;
        default: sticker.set(0, at, tab, at + cell); break;
      }
      fill.setColor(ink(sideColor(diagram.sideFace(index))));
      canvas.drawRoundRect(sticker, radius / 2, radius / 2, fill);
    }

    if (diagram.isPermutation()) {
      drawArrows(canvas, origin, cell, gap, size);
    }
    canvas.restore();
  }

  private void drawGround(Canvas canvas, float size) {
    float radius = size * STAGE_RADIUS;
    if (dimmed) {
      float width = getResources().getDisplayMetrics().density;
      edge.setStrokeWidth(width);
      ground.set(width / 2, width / 2, size - width / 2, size - width / 2);
      canvas.drawRoundRect(ground, radius, radius, edge);
    } else {
      stage.setCornerRadius(radius);
      stage.setBounds(0, 0, (int) size, (int) size);
      stage.draw(canvas);
    }
  }

  /**
   * A colour as it is drawn here: itself, or what is left of it once the case is turned down. A
   * sticker goes down towards black, which keeps its hue; mixing it into the tile instead takes the
   * chroma with the value and lands the yellow on olive, which is the fade this replaces. A blank
   * one has no hue to keep and goes the other way, or it would sink into the tile and take the
   * shape of the case with it.
   */
  private int ink(int color) {
    if (!dimmed) {
      return color;
    }
    return color == blankColor ? ColorUtils.blendARGB(groundColor, color, DIM_BLANK)
        : ColorUtils.blendARGB(Color.BLACK, color, DIM);
  }

  /**
   * A side sticker is worth its real colour only once the layer is oriented, and then it is the
   * whole of what the case looks like: headlights and blocks are read off these and nothing else.
   * While the layer is still being oriented they say nothing, since the algorithm that follows will
   * scatter them anyway, so there they are drawn as blank or as the layer's colour turned sideways.
   */
  private int sideColor(char face) {
    if (diagram.isPermutation()) {
      return ContextCompat.getColor(getContext(), Utils.getFaceColorRes(face));
    }
    return face == 'U' ? layerColor : blankColor;
  }

  /**
   * One line per journey, with a head at each end when two pieces trade places: a swap drawn as two
   * arrows is the same line drawn twice, which reads as thicker rather than as mutual.
   */
  private void drawArrows(Canvas canvas, float origin, float cell, float gap, float size) {
    arrowLine.setStrokeWidth(size * 0.025f);
    arrowLine.setColor(ink(arrowColor));
    arrowHead.setColor(arrowLine.getColor());
    for (int from = 0; from < 9; from++) {
      int to = diagram.arrow(from);
      if (to == from) {
        continue;
      }
      boolean swap = diagram.arrow(to) == from;
      if (swap && to < from) {
        continue;
      }
      float fromX = centre(origin, cell, gap, from % 3);
      float fromY = centre(origin, cell, gap, from / 3);
      float toX = centre(origin, cell, gap, to % 3);
      float toY = centre(origin, cell, gap, to / 3);
      float headSize = size * ARROW_HEAD;
      // Stop the line short of the head so the two do not build up a blunt tip.
      float length = (float) Math.hypot(toX - fromX, toY - fromY);
      float unitX = (toX - fromX) / length;
      float unitY = (toY - fromY) / length;
      float startX = swap ? fromX + unitX * headSize * 0.6f : fromX;
      float startY = swap ? fromY + unitY * headSize * 0.6f : fromY;
      canvas.drawLine(startX, startY, toX - unitX * headSize * 0.6f,
          toY - unitY * headSize * 0.6f, arrowLine);
      drawHead(canvas, toX, toY, unitX, unitY, headSize);
      if (swap) {
        drawHead(canvas, fromX, fromY, -unitX, -unitY, headSize);
      }
    }
  }

  private void drawHead(Canvas canvas, float tipX, float tipY, float unitX, float unitY,
      float headSize) {
    float baseX = tipX - unitX * headSize;
    float baseY = tipY - unitY * headSize;
    float wingX = -unitY * headSize * 0.42f;
    float wingY = unitX * headSize * 0.42f;
    head.reset();
    head.moveTo(tipX, tipY);
    head.lineTo(baseX + wingX, baseY + wingY);
    head.lineTo(baseX - wingX, baseY - wingY);
    head.close();
    canvas.drawPath(head, arrowHead);
  }

  private static float centre(float origin, float cell, float gap, int index) {
    return origin + index * (cell + gap) + cell / 2;
  }

  private int color(int colorResId) {
    return ContextCompat.getColor(getContext(), colorResId);
  }
}
