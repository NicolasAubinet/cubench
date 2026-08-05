package com.cube.nanotimer.util.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;
import com.cube.nanotimer.util.helper.Utils;

/**
 * The state a connected cube reports, unfolded flat: its 54 stickers, in the usual cross.
 *
 * <p>Drawn from facelets rather than from an alg, which is the whole reason it exists — the mirrored
 * 3D cube is fed a move stream and can only be pointed at a state somebody knows the moves to, so a
 * cube that was already scrambled when it connected, or one the mirror has lost, had nothing to show
 * but a sentence asking for a solved cube.
 *
 * <p>All six faces at once is the point and not a concession. The question this answers is whether
 * the app sees the cube that is in the hand — and on a drawn cube the three faces that matter are
 * the three facing away.
 */
public class CubeNetView extends View {

  /** Where each face of the facelet string (U, R, F, D, L, B) sits in the unfolded cross. */
  private static final int[] FACE_COL = {1, 2, 1, 1, 0, 3};
  private static final int[] FACE_ROW = {0, 1, 1, 2, 1, 1};

  private static final String FACES = "URFDLB";

  /** Everything below is in sticker widths, so the net scales as one whatever space it is given. */
  private static final float FACE_GAP = 0.4f;
  private static final float STICKER_INSET = 0.05f;
  private static final float STICKER_RADIUS = 0.16f;
  private static final float NET_COLS = 4 * 3 + 3 * FACE_GAP;
  private static final float NET_ROWS = 3 * 3 + 2 * FACE_GAP;

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF sticker = new RectF();
  private final int[] faceColors = new int[FACES.length()];
  private final int unknownColor;

  private String facelets;

  public CubeNetView(Context context) {
    this(context, null);
  }

  public CubeNetView(Context context, AttributeSet attrs) {
    super(context, attrs);
    for (int i = 0; i < faceColors.length; i++) {
      faceColors[i] = ContextCompat.getColor(context, Utils.getFaceColorRes(FACES.charAt(i)));
    }
    unknownColor = ContextCompat.getColor(context, R.color.gray400);
  }

  /**
   * @param facelets the 54 sticker colours, faces in URFDLB order. Null, or anything that is not 54
   *     long and therefore not a state, draws nothing.
   */
  public void setFacelets(String facelets) {
    if (facelets != null && facelets.length() != FACES.length() * 9) {
      facelets = null;
    }
    if (facelets == null ? this.facelets == null : facelets.equals(this.facelets)) {
      return;
    }
    this.facelets = facelets;
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (facelets == null) {
      return;
    }
    float size = Math.min(getWidth() / NET_COLS, getHeight() / NET_ROWS);
    if (size <= 0) {
      return;
    }
    float left = (getWidth() - size * NET_COLS) / 2;
    float top = (getHeight() - size * NET_ROWS) / 2;
    float inset = size * STICKER_INSET;
    float radius = size * STICKER_RADIUS;
    for (int face = 0; face < FACES.length(); face++) {
      float faceLeft = left + FACE_COL[face] * (3 + FACE_GAP) * size;
      float faceTop = top + FACE_ROW[face] * (3 + FACE_GAP) * size;
      for (int i = 0; i < 9; i++) {
        paint.setColor(colorOf(facelets.charAt(face * 9 + i)));
        float x = faceLeft + (i % 3) * size;
        float y = faceTop + (i / 3) * size;
        sticker.set(x + inset, y + inset, x + size - inset, y + size - inset);
        canvas.drawRoundRect(sticker, radius, radius, paint);
      }
    }
  }

  private int colorOf(char facelet) {
    int face = FACES.indexOf(facelet);
    return face < 0 ? unknownColor : faceColors[face];
  }
}
