package com.cube.nanotimer.gui.widget;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.cube.nanotimer.R;
import com.cube.nanotimer.scrambler.cross.CrossFace;
import com.cube.nanotimer.util.helper.GUIUtils;
import com.cube.nanotimer.util.helper.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * The row of six colour swatches a cross face is picked from: the standard scheme, with the face
 * letter left on each as the label that cannot be misread.
 *
 * <p>Shared rather than copied. Anything that asks the user which cross they are solving is asking
 * the same question, and two rows drawn from two tables would eventually disagree about which colour
 * L is.
 */
public class CrossFaceSwatches {

  /** How much of a face is left on one that is not the answer, and how much of its letter. */
  private static final float DIM = 0.58f;
  private static final float DIM_LETTER = 0.85f;

  public interface Listener {
    void onFacePicked(CrossFace face);
  }

  private final Context context;
  private final Listener listener;
  private final List<TextView> swatches = new ArrayList<TextView>();

  private CrossFace selected;
  private CrossFace paired;

  public CrossFaceSwatches(Context context, LinearLayout container, Listener listener) {
    this.context = context;
    this.listener = listener;
    build(container);
  }

  /**
   * @param selected the face being solved
   * @param paired its partner, for a dual neutral pick, or null
   */
  public void setSelection(CrossFace selected, CrossFace paired) {
    this.selected = selected;
    this.paired = paired;
    refresh();
  }

  private void build(LinearLayout container) {
    CrossFace[] faces = CrossFace.values();
    for (int i = 0; i < faces.length; i++) {
      final CrossFace face = faces[i];
      TextView swatch = new TextView(context);
      swatch.setText(face.name());
      swatch.setGravity(Gravity.CENTER);
      swatch.setTextSize(15);
      GUIUtils.setWeight(swatch, Typeface.BOLD);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
      if (i > 0) {
        lp.leftMargin = dp(6);
      }
      swatch.setLayoutParams(lp);
      swatch.setClickable(true);
      swatch.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          if (selected != face) {
            listener.onFacePicked(face);
          }
        }
      });
      swatches.add(swatch);
      container.addView(swatch);
    }
  }

  private void refresh() {
    CrossFace[] faces = CrossFace.values();
    for (int i = 0; i < faces.length; i++) {
      CrossFace face = faces[i];
      TextView swatch = swatches.get(i);
      int faceColor = color(Utils.getFaceColorRes(face.name().charAt(0)));
      boolean primary = (face == selected);
      boolean partner = (paired != null && face == paired);

      boolean answer = primary || partner;
      // Down towards black, not faded: alpha over the app's grey ground pulls value, chroma and
      // contrast down together and lands the yellow on olive, so the row stops naming colours.
      int shown = answer ? faceColor : ColorUtils.blendARGB(Color.BLACK, faceColor, DIM);

      GradientDrawable bg = new GradientDrawable();
      bg.setShape(GradientDrawable.RECTANGLE);
      bg.setCornerRadius(dp(8));
      bg.setColor(shown);
      if (primary) {
        bg.setStroke(dp(5), color(R.color.lightblue));
      } else if (partner) {
        bg.setStroke(dp(4), color(R.color.iceblue));
      } else {
        bg.setStroke(dp(1), color(R.color.gray700));
      }
      swatch.setBackground(bg);
      int letter = isLightColor(shown) ? 0xFF222222 : color(R.color.white);
      swatch.setTextColor(answer ? letter : ColorUtils.blendARGB(shown, letter, DIM_LETTER));
    }
  }

  private static boolean isLightColor(int c) {
    int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
    return (0.299 * r + 0.587 * g + 0.114 * b) > 150;
  }

  private int dp(int value) {
    return (int) (value * context.getResources().getDisplayMetrics().density);
  }

  private int color(int colorResId) {
    return ContextCompat.getColor(context, colorResId);
  }
}
