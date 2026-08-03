package com.cube.nanotimer.util.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Paint;
import android.graphics.Typeface;
import androidx.appcompat.widget.AppCompatTextView;
import android.util.AttributeSet;
import android.util.TypedValue;
import com.cube.nanotimer.R;

public class FontFitTextView extends AppCompatTextView {

  private Paint mTestPaint;
  private float initialTextSize;
  private int textSizeUnit = TypedValue.COMPLEX_UNIT_PX;

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
      refitText(getText().toString(), widthSize);
    }
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    // Shrinking the text must not shrink the box holding it. The cell around this view is sized to
    // its tallest child, so a long value that scaled itself down was pulling the tile down with it
    // and leaving it shorter than the one beside it.
    if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
      setMeasuredDimension(getMeasuredWidth(),
          Math.max(getMeasuredHeight(), unshrunkHeight()));
    }
  }

  /** The height this would measure with its text at full size, however far it has been scaled. */
  private int unshrunkHeight() {
    mTestPaint.set(getPaint());
    mTestPaint.setTextSize(initialTextSize);
    Paint.FontMetricsInt metrics = mTestPaint.getFontMetricsInt();
    int lines = Math.max(1, getLineCount());
    return getPaddingTop() + getPaddingBottom() + lines * (metrics.bottom - metrics.top);
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
