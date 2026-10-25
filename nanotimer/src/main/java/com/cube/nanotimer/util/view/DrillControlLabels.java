package com.cube.nanotimer.util.view;

import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;

/**
 * The running drill's three controls, each a word with an accent icon before it, all at one size.
 *
 * <p>Fitted by hand rather than autosized: autosizing does nothing on a single-line button. The
 * largest size from {@link #MAX_SP} down to {@link #MIN_SP} at which all three fit is used for all
 * three; if none fits with icons, all three go without, since one bare word among icons reads as a
 * different kind of control.
 */
public final class DrillControlLabels {

  private static final float MAX_SP = 14;
  private static final float MIN_SP = 12;
  private static final float BARE_MIN_SP = 11;
  /** The icon's size against the text's. */
  private static final float ICON_RATIO = 1.25f;
  private static final String GAP = " ";

  private DrillControlLabels() {
  }

  /** Labels the buttons once they have a width; icons and words are given per button. */
  public static void apply(final Button[] buttons, final int[] icons, final int[] words) {
    buttons[0].post(new Runnable() {
      @Override
      public void run() {
        fit(buttons, icons, words);
      }
    });
  }

  private static void fit(Button[] buttons, int[] icons, int[] words) {
    float scaled = buttons[0].getResources().getDisplayMetrics().scaledDensity;
    for (float sp = MAX_SP; sp >= MIN_SP; sp -= 0.5f) {
      if (fits(buttons, words, sp * scaled, true)) {
        set(buttons, icons, words, sp, true);
        return;
      }
    }
    for (float sp = MAX_SP; sp > BARE_MIN_SP; sp -= 0.5f) {
      if (fits(buttons, words, sp * scaled, false)) {
        set(buttons, icons, words, sp, false);
        return;
      }
    }
    set(buttons, icons, words, BARE_MIN_SP, false); // ellipsized, as a last resort
  }

  private static boolean fits(Button[] buttons, int[] words, float px, boolean withIcon) {
    for (int i = 0; i < buttons.length; i++) {
      Button button = buttons[i];
      if (button.getVisibility() == View.GONE) {
        continue; // a control the drill does not offer has no width to fit
      }
      Paint paint = new Paint(button.getPaint());
      paint.setTextSize(px);
      String word = button.getContext().getString(words[i]);
      float width = paint.measureText(word)
          + (withIcon ? px * ICON_RATIO + paint.measureText(GAP) : 0);
      int room = button.getWidth() - button.getPaddingLeft() - button.getPaddingRight();
      if (button.getWidth() == 0 || width > room) {
        return false;
      }
    }
    return true;
  }

  private static void set(Button[] buttons, int[] icons, int[] words, float sp, boolean withIcon) {
    for (int i = 0; i < buttons.length; i++) {
      Button button = buttons[i];
      button.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
      String word = button.getContext().getString(words[i]);
      if (!withIcon) {
        button.setText(word);
        continue;
      }
      Drawable icon = ContextCompat.getDrawable(button.getContext(), icons[i]).mutate();
      icon.setTint(ContextCompat.getColor(button.getContext(), R.color.color_accent));
      int size = Math.round(button.getTextSize() * ICON_RATIO);
      icon.setBounds(0, 0, size, size);
      SpannableStringBuilder label = new SpannableStringBuilder(" " + GAP);
      label.setSpan(new CenteredImageSpan(icon), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      button.setText(label.append(word));
    }
  }
}
