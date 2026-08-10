package com.cube.nanotimer.util.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Paint;
import android.graphics.Typeface;
import androidx.appcompat.widget.AppCompatTextView;
import android.text.method.TransformationMethod;
import android.util.AttributeSet;
import android.util.TypedValue;
import com.cube.nanotimer.R;

public class FontFitTextView extends AppCompatTextView {

  private Paint mTestPaint;
  private float initialTextSize;
  private int textSizeUnit = TypedValue.COMPLEX_UNIT_PX;
  private int extraPaddingTop;
  private int extraPaddingBottom;

  /**
   * The face a scramble is set in. It has to be monospaced: {@code formatScramble} pads every move
   * out to the longest one to build the columns, and a proportional font throws that alignment
   * away.
   */
  enum Font {
    SCRAMBLE(0, "fonts/JetBrainsMono-Medium.ttf");

    private int id;
    private String fontName;
    Font(int id, String fontName) {
      this.id = id;
      this.fontName = fontName;
    }
    public static Font getFont(int id) {
      for (Font f : values()) {
        if (f.id == id) {
          return f;
        }
      }
      return null;
    }
  }

  public FontFitTextView(Context context) {
    super(context);
    initialTextSize = getTextSize();
    init();
  }

  public FontFitTextView(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialTextSize = getTextSize();
    init(context, attrs);
  }

  private void init() {
    mTestPaint = new Paint();
    mTestPaint.set(getPaint());
    // max size defaults to the initially specified text size unless it is too small
  }

  private void init(Context context, AttributeSet attrs) {
    init();

    TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FontFitTextView);
    if (a.hasValue(R.styleable.FontFitTextView_myfont)) {
      Font font = Font.getFont(a.getInt(R.styleable.FontFitTextView_myfont, -1));
      if (font != null) {
        setFont(font.fontName);
      }
    }
  }

  /*
   * Re size the font so the specified text fits in the text box
   * assuming the text box is the specified width.
   */
  private void refitText(String text, int textWidth) {
    if (textWidth <= 0) {
      return;
    }
    int targetWidth = textWidth - getPaddingLeft() - getPaddingRight();
    float hi = initialTextSize;
    float lo = 2;
    final float threshold = 0.5f; // How close we have to be

    String longestLine = "";
    for (String line : text.split("\n")) {
      if (line.length() > longestLine.length()) {
        longestLine = line;
      }
    }
    mTestPaint.set(getPaint());

    mTestPaint.setTextSize(initialTextSize);
    if (mTestPaint.measureText(longestLine) < targetWidth) {
      // Set the initial size
      super.setTextSize(textSizeUnit, initialTextSize);
      return;
    }

    while ((hi - lo) > threshold) {
      float size = (hi+lo) / 2;
      mTestPaint.setTextSize(size);
      if (mTestPaint.measureText(longestLine) >= targetWidth) {
        hi = size; // too big
      } else {
        lo = size; // too small
      }
    }
    // Use lo so that we undershoot rather than overshoot
    super.setTextSize(textSizeUnit, lo);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int widthMode = MeasureSpec.getMode(widthMeasureSpec);
    int widthSize = MeasureSpec.getSize(widthMeasureSpec);
    if (widthMode != MeasureSpec.UNSPECIFIED && widthSize > 0) {
      refitText(displayedText(), widthSize);
    }
    centreShrunkLine();
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    // Shrinking the text must not shrink the box holding it. The cell around this view is sized to
    // its tallest child, so a long value that scaled itself down was pulling the tile down with it
    // and leaving it shorter than the one beside it.
    if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
      setMeasuredDimension(getMeasuredWidth(),
          Math.max(getMeasuredHeight(), unshrunkHeight()));
    }
  }

  /** What is actually drawn: a view set in caps is wider than the string it was handed. */
  private String displayedText() {
    CharSequence text = getText();
    TransformationMethod transformation = getTransformationMethod();
    if (transformation != null) {
      CharSequence transformed = transformation.getTransformation(text, this);
      if (transformed != null) {
        text = transformed;
      }
    }
    return text.toString();
  }

  /** The height this would measure with its text at full size, however far it has been scaled. */
  private int unshrunkHeight() {
    mTestPaint.set(getPaint());
    mTestPaint.setTextSize(initialTextSize);
    Paint.FontMetricsInt metrics = mTestPaint.getFontMetricsInt();
    int lines = Math.max(1, getLineCount());
    // The leading counts: a view told to set its lines closer together must be allowed to be
    // shorter for it, or this floor hands the saved pixels straight back.
    float lineHeight = (metrics.bottom - metrics.top) * getLineSpacingMultiplier()
        + getLineSpacingExtra();
    return basePaddingTop() + basePaddingBottom() + (int) (lines * lineHeight);
  }

  /**
   * Pads a shrunk line back out to the height it had at full size, half above and half below, so it
   * sits where the full-size line sat. Leaving the box to do it is not enough: a shrunk value ended
   * up higher than the label beside it, which had not shrunk, and the two read as misaligned.
   * The vertical padding is this view's own: whatever it is given is taken as the base to add to.
   */
  private void centreShrunkLine() {
    int top = 0;
    int bottom = 0;
    if (getMaxLines() == 1 && getTextSize() < initialTextSize) {
      mTestPaint.set(getPaint());
      Paint.FontMetricsInt shrunk = mTestPaint.getFontMetricsInt();
      mTestPaint.setTextSize(initialTextSize);
      Paint.FontMetricsInt full = mTestPaint.getFontMetricsInt();
      int missing = (full.bottom - full.top) - (shrunk.bottom - shrunk.top);
      top = missing / 2;
      bottom = missing - top;
    }
    if (top != extraPaddingTop || bottom != extraPaddingBottom) {
      int baseTop = basePaddingTop();
      int baseBottom = basePaddingBottom();
      extraPaddingTop = top;
      extraPaddingBottom = bottom;
      setPadding(getPaddingLeft(), baseTop + top, getPaddingRight(), baseBottom + bottom);
    }
  }

  private int basePaddingTop() {
    return getPaddingTop() - extraPaddingTop;
  }

  private int basePaddingBottom() {
    return getPaddingBottom() - extraPaddingBottom;
  }

  @Override
  protected void onTextChanged(final CharSequence text, final int start, final int before, final int after) {
    requestLayout();
  }

  @Override
  public void setTextSize(float size) {
    initialTextSize = size;
    super.setTextSize(size);
  }

  @Override
  public void setTextSize(int unit, float size) {
    initialTextSize = size;
    textSizeUnit = unit;
    super.setTextSize(unit, size);
  }

  public void setFont(String fontString) {
    if (!isInEditMode()) {
      Typeface font = Typeface.createFromAsset(getContext().getAssets(), fontString);
      setTypeface(font);
    }
  }

}
