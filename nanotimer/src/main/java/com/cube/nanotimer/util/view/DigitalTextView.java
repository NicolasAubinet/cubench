package com.cube.nanotimer.util.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import androidx.appcompat.widget.AppCompatTextView;
import android.util.AttributeSet;
import android.util.TypedValue;
import com.cube.nanotimer.Options;

/**
 * The timer's own digits.
 *
 * <p>Owns the timer font setting, the quieter fraction the running screen asks for (see
 * {@link #setQuietFraction}), and the size the digits are drawn at, which is the size the settings
 * ask for until a time comes along that will not fit across the screen: see {@link #fitToWidth}.
 * The figures stay tabular whatever the size, which is the font's job ({@code TimerFont} sets
 * {@code tnum} on every face that has it) and not this one's — without it the number jitters as
 * the hundredths turn over.
 */
public class DigitalTextView extends AppCompatTextView {

  /** How large the fraction is drawn against the seconds, and how much of the colour it keeps. */
  private static final float FRACTION_SIZE = 0.62f;
  private static final float FRACTION_ALPHA = 0.55f;

  /** How far the digits may be shrunk to fit. Below this they are a nuisance to read, and a time
   * that long is a stopwatch reading rather than a solve. */
  private static final float MIN_FIT = 0.5f;

  private float baseTextSizePx = -1f;
  /** The size this was last told to be. What is drawn is that or smaller, never larger. */
  private float wantedTextSizePx = -1f;
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
   * Every size this is set to passes through here, which is what {@link #fitToWidth} shrinks from:
   * the font setting is not the last word on it, since {@link ScalingLinearLayout} multiplies the
   * whole screen up from the px the layouts are authored in.
   */
  @Override
  public void setTextSize(int unit, float size) {
    super.setTextSize(unit, size);
    wantedTextSizePx = getTextSize();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    fitToWidth(MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight());
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }

  /**
   * Draws the digits smaller when the time is too long for the width, and only then.
   *
   * <p>An hour-long solve at high precision with a +2 beside it is fourteen figures where the
   * screen was drawn for five, and wrapped it lands on top of the scramble. Everything that fits
   * keeps the size the settings ask for, which is every time anybody actually solves in.
   *
   * <p>Set on the paint rather than through {@code setTextSize}: this runs inside the measure pass,
   * whose whole job is to answer with a width, and asking for another layout from in there is how a
   * measure loop starts.
   */
  private void fitToWidth(int available) {
    if (wantedTextSizePx <= 0f || available <= 0 || getText().length() == 0) {
      return;
    }
    TextPaint paint = getPaint();
    paint.setTextSize(wantedTextSizePx);
    float width = Layout.getDesiredWidth(getText(), paint);
    if (width <= available) {
      return;
    }
    float floor = wantedTextSizePx * MIN_FIT;
    float size = Math.max(floor, wantedTextSizePx * available / width);
    paint.setTextSize(size);
    // A glyph's advance does not scale quite linearly, and the width is rounded up after this, so
    // the size the ratio gives can still be a pixel over. Step down until it is not.
    while (size > floor && Math.ceil(Layout.getDesiredWidth(getText(), paint)) > available) {
      size -= 1f;
      paint.setTextSize(size);
    }
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
