package com.cube.nanotimer.util.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import androidx.appcompat.widget.AppCompatTextView;
import android.util.AttributeSet;
import android.util.TypedValue;
import com.cube.nanotimer.Options;

/**
 * The timer's own digits.
 *
 * <p>Owns the timer font setting, and the quieter fraction the running screen asks for: see
 * {@link #setQuietFraction}. The figures stay tabular either way, which is the font's job
 * ({@code TimerFont} sets {@code tnum} on every face that has it) and not this one's — without it
 * the number jitters as the hundredths turn over.
 */
public class DigitalTextView extends AppCompatTextView {

  /** How large the fraction is drawn against the seconds, and how much of the colour it keeps. */
  private static final float FRACTION_SIZE = 0.62f;
  private static final float FRACTION_ALPHA = 0.55f;

  private float baseTextSizePx = -1f;
  private boolean quietFraction;
  /**
   * What was last handed in, unstyled, so the styling can be put on or taken off at any time.
   *
   * <p>No initialiser: {@link #setText} runs from the superclass constructor, before this object's
   * own fields are assigned, so one here would overwrite the first text with an empty string.
   */
  private CharSequence plain;

  public DigitalTextView(Context context) {
    super(context);
    setFont();
  }

  public DigitalTextView(Context context, AttributeSet attrs) {
    super(context, attrs);
    setFont();
  }

  public DigitalTextView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    setFont();
  }

  public void setFont() {
    if (!isInEditMode()) {
      if (baseTextSizePx < 0f) {
        baseTextSizePx = getTextSize();
      }
      TimerFont font = Options.INSTANCE.getTimerFont();
      font.applyTo(this);
      float scale = font.getSizeScale() * Options.INSTANCE.getTimerFontSizeScale();
      setTextSize(TypedValue.COMPLEX_UNIT_PX, baseTextSizePx * scale);
    }
  }

  /**
   * Draws the hundredths smaller and dimmer than the seconds, or stops.
   *
   * <p>For the screen while a solve runs, where the last two digits are a blur nobody reads and at
   * full weight they are the loudest thing on it. The seconds keep their size and their colour, so
   * what the number says at a glance does not change.
   */
  public void setQuietFraction(boolean quiet) {
    if (quiet != quietFraction) {
      quietFraction = quiet;
      restyle();
    }
  }

  @Override
  public void setText(CharSequence text, BufferType type) {
    plain = (text == null) ? "" : text;
    super.setText(styled(plain), quietFraction ? BufferType.SPANNABLE : type);
  }

  // Both overloads: the fraction is drawn as a share of whatever colour the digits are wearing, so
  // a colour set after the text has to put the spans back.
  @Override
  public void setTextColor(int color) {
    super.setTextColor(color);
    if (quietFraction) {
      restyle();
    }
  }

  @Override
  public void setTextColor(ColorStateList colors) {
    super.setTextColor(colors);
    if (quietFraction) {
      restyle();
    }
  }

  private void restyle() {
    CharSequence text = (plain == null) ? "" : plain;
    super.setText(styled(text), BufferType.SPANNABLE);
  }

  /** The fraction is whatever follows the last separator; a DNF has none and is left alone. */
  private CharSequence styled(CharSequence text) {
    if (!quietFraction || text == null) {
      return text;
    }
    int dot = -1;
    for (int i = text.length() - 1; i >= 0; i--) {
      if (text.charAt(i) == '.' || text.charAt(i) == ',') {
        dot = i;
        break;
      }
    }
    if (dot < 0 || dot == text.length() - 1) {
      return text;
    }
    int color = getCurrentTextColor();
    SpannableString out = new SpannableString(text);
    out.setSpan(new RelativeSizeSpan(FRACTION_SIZE), dot + 1, text.length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    out.setSpan(new ForegroundColorSpan(Color.argb(
            Math.round(Color.alpha(color) * FRACTION_ALPHA),
            Color.red(color), Color.green(color), Color.blue(color))),
        dot + 1, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    return out;
  }

  /**
   * Shrinks the unscaled base size (e.g. so very long times fit on smaller cubes). The active
   * font's size scale is re-applied on top, so this composes with {@link #setFont()}.
   */
  public void reduceBaseTextSize(float px) {
    if (baseTextSizePx < 0f) {
      baseTextSizePx = getTextSize();
    }
    baseTextSizePx = Math.max(0f, baseTextSizePx - px);
    setFont();
  }

}
