package com.cube.nanotimer.gui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.cube.nanotimer.R;

/**
 * An F2L case as a small 3D cube, cross down, seen from the front right: the first two layers in
 * their colours, the last layer grey. Drawn on a canvas rather than by the 3D cube, since a grid
 * holds dozens of them. A stuck piece is pictured in front right, so the pair always shows.
 *
 * <p>Takes the picture {@code CaseAlgorithms.pairPicture} writes: URFDLB facelets, a face letter
 * on the pair, lower case on the rest of the first two layers (drawn the same), '.' on the last
 * layer.
 */
public class PairCaseView extends View {

  /** The shade of the top, the left and the right face, as the light falls. */
  private static final float[] SHADE = {1f, 0.84f, 0.68f};
  /** Half a sticker, of a cubie's width. */
  private static final float STICKER = 0.43f;
  /** How much of a sticker is left on a case that is turned down. */
  private static final float TURNED_DOWN = 0.42f;
  /** The drawing's extent in cube units, across and down (the cube projects 5.2 by 6). */
  private static final float EXTENT = 6.2f;

  /** The view's turn off the front face; 45 would show front and right alike. */
  private static final double YAW = Math.toRadians(32);
  private static final float YAW_COS = (float) Math.cos(YAW);
  private static final float YAW_SIN = (float) Math.sin(YAW);

  private static final float STAGE_RADIUS = 0.10f;

  private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path path = new Path();
  private final RectF ground = new RectF();
  private GradientDrawable stage;
  private int blankColor;
  private int bodyColor;

  private String picture;
  private boolean dimmed;
  private boolean staged;

  public PairCaseView(Context context) {
    super(context);
    init();
  }

  public PairCaseView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  private void init() {
    blankColor = ContextCompat.getColor(getContext(), R.color.case_blank);
    bodyColor = 0xFF0C0E10;
    fill.setStyle(Paint.Style.FILL_AND_STROKE);
    fill.setStrokeJoin(Paint.Join.ROUND);
    edge.setStyle(Paint.Style.STROKE);
    edge.setColor(ContextCompat.getColor(getContext(), R.color.case_off_edge));
    stage = (GradientDrawable) ContextCompat.getDrawable(getContext(), R.drawable.case_stage)
        .mutate();
  }

  public void setPicture(String picture) {
    this.picture = picture;
    invalidate();
  }

  /** A case the drill will not deal, as {@link LastLayerCaseView#setDimmed} turns one down. */
  public void setDimmed(boolean dimmed) {
    this.dimmed = dimmed;
    invalidate();
  }

  /** Whether the cube stands in the tiles' well, rather than straight on what is behind the view. */
  public void setStaged(boolean staged) {
    this.staged = staged;
    invalidate();
  }

  /** Square, off the width unless the height was pinned, as a last-layer chart is. */
  @Override
  protected void onMeasure(int widthSpec, int heightSpec) {
    int width = MeasureSpec.getSize(widthSpec);
    int size = MeasureSpec.getMode(heightSpec) == MeasureSpec.EXACTLY
        ? Math.min(width, MeasureSpec.getSize(heightSpec)) : width;
    setMeasuredDimension(size, size);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    float size = Math.min(getWidth(), getHeight());
    if (staged) {
      drawGround(canvas, size);
    }
    if (picture == null) {
      return;
    }
    float scale = size / EXTENT;
    canvas.save();
    canvas.translate(size / 2, size / 2);
    canvas.scale(scale, scale);
    fill.setStrokeWidth(0.16f);
    fill.setColor(ink(bodyColor));
    for (int face = 0; face < 3; face++) {
      drawBody(canvas, face);
    }
    fill.setStrokeWidth(0.07f);
    for (int facelet = 0; facelet < picture.length(); facelet++) {
      drawSticker(canvas, facelet);
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

  /** One of the three faces seen, as the dark body the stickers sit on. */
  private void drawBody(Canvas canvas, int seen) {
    float[][] corners = new float[4][];
    for (int i = 0; i < 4; i++) {
      float a = i == 0 || i == 3 ? -1.5f : 1.5f;
      float b = i < 2 ? -1.5f : 1.5f;
      corners[i] = seen == 0 ? new float[] {a, 1.5f, b} // top
          : seen == 1 ? new float[] {a, -b, 1.5f} // left of the two sides, facing +z
          : new float[] {1.5f, -b, -a}; // right, facing +x
    }
    polygon(canvas, corners);
  }

  private void drawSticker(Canvas canvas, int facelet) {
    float[] centre = centre(facelet);
    float[] normal = normal(facelet / 9);
    int seen = normal[1] > 0.5f ? 0 : normal[2] > 0.5f ? 1 : normal[0] > 0.5f ? 2 : -1;
    if (seen < 0) {
      return;
    }
    float[] at = centre;
    int[] across = new int[2];
    for (int axis = 0, n = 0; axis < 3; axis++) {
      if (Math.abs(normal[axis]) < 0.5f) {
        across[n++] = axis;
      }
    }
    float[][] corners = new float[4][];
    for (int i = 0; i < 4; i++) {
      float[] corner = at.clone();
      corner[across[0]] += (i == 0 || i == 3 ? -STICKER : STICKER);
      corner[across[1]] += (i < 2 ? -STICKER : STICKER);
      corners[i] = corner;
    }
    fill.setColor(ink(ColorUtils.blendARGB(colour(picture.charAt(facelet)), Color.BLACK,
        1 - SHADE[seen])));
    polygon(canvas, corners);
  }

  private void polygon(Canvas canvas, float[][] corners) {
    path.reset();
    for (int i = 0; i < corners.length; i++) {
      float x = (corners[i][0] * YAW_COS - corners[i][2] * YAW_SIN) * 1.2247f;
      float y = -corners[i][1] + (corners[i][0] * YAW_SIN + corners[i][2] * YAW_COS) * 0.7071f;
      if (i == 0) {
        path.moveTo(x, y);
      } else {
        path.lineTo(x, y);
      }
    }
    path.close();
    canvas.drawPath(path, fill);
  }

  /** A facelet's centre, the cube spanning -1.5 to 1.5 with y up and z towards the front. */
  private static float[] centre(int facelet) {
    int face = facelet / 9;
    int row = facelet % 9 / 3;
    int col = facelet % 3;
    switch (face) {
      case 0: return new float[] {col - 1, 1.5f, row - 1}; // U, back row first
      case 1: return new float[] {1.5f, 1 - row, 1 - col}; // R, front column first
      case 2: return new float[] {col - 1, 1 - row, 1.5f}; // F
      case 3: return new float[] {col - 1, -1.5f, 1 - row}; // D, front row first
      case 4: return new float[] {-1.5f, 1 - row, col - 1}; // L, back column first
      default: return new float[] {1 - col, 1 - row, -1.5f}; // B, right column first
    }
  }

  private static float[] normal(int face) {
    switch (face) {
      case 0: return new float[] {0, 1, 0};
      case 1: return new float[] {1, 0, 0};
      case 2: return new float[] {0, 0, 1};
      case 3: return new float[] {0, -1, 0};
      case 4: return new float[] {-1, 0, 0};
      default: return new float[] {0, 0, -1};
    }
  }

  /** The cross face (D) is white and the last layer's yellow, as the solver holds the cube. */
  private int colour(char sticker) {
    int res;
    switch (Character.toUpperCase(sticker)) {
      case 'U': res = R.color.cube_yellow; break;
      case 'D': res = R.color.cube_white; break;
      case 'R': res = R.color.cube_red; break;
      case 'L': res = R.color.cube_orange; break;
      case 'F': res = R.color.cube_green; break;
      case 'B': res = R.color.cube_blue; break;
      default: return blankColor;
    }
    return ContextCompat.getColor(getContext(), res);
  }

  private int ink(int colour) {
    return dimmed ? ColorUtils.blendARGB(Color.BLACK, colour, TURNED_DOWN) : colour;
  }
}
