package com.cube.nanotimer.util.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

/** Draws short centered text with a crisp stroke outline behind the fill, for legibility over icons. */
public class OutlineTextView extends View {

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private String text = "";
  private final float textSizePx;
  private final float strokeWidthPx;
  private final int fillColor = Color.WHITE;
  private final int strokeColor = Color.BLACK;

  public OutlineTextView(Context context, AttributeSet attrs) {
    super(context, attrs);
    textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11, getResources().getDisplayMetrics());
    strokeWidthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3, getResources().getDisplayMetrics());
    paint.setTextAlign(Paint.Align.CENTER);
    paint.setFakeBoldText(true);
    paint.setTextSize(textSizePx);
  }

  public void setText(String text) {
    this.text = (text == null) ? "" : text;
    requestLayout();
    invalidate();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    float width = paint.measureText(text) + strokeWidthPx * 2;
    Paint.FontMetrics fm = paint.getFontMetrics();
    float height = (fm.descent - fm.ascent) + strokeWidthPx * 2;
    setMeasuredDimension(
        resolveSize((int) Math.ceil(width), widthMeasureSpec),
        resolveSize((int) Math.ceil(height), heightMeasureSpec));
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (text.isEmpty()) {
      return;
    }
    float x = getWidth() / 2f;
    float y = getHeight() / 2f - (paint.descent() + paint.ascent()) / 2f;

    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(strokeWidthPx);
    paint.setColor(strokeColor);
    canvas.drawText(text, x, y, paint);

    paint.setStyle(Paint.Style.FILL);
    paint.setColor(fillColor);
    canvas.drawText(text, x, y, paint);
  }
}
