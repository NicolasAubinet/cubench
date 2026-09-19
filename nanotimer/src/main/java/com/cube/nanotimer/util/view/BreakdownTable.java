package com.cube.nanotimer.util.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TableLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * The solve sheet's breakdown table, with a thin rule in each step's colour hanging from the dot
 * before its name, down the rows that belong to it: its parts and the moves tapes under them.
 *
 * <p>The rule is drawn by the table rather than by the rows, since the rows are separate views with
 * gaps between them and a rule made of their backgrounds would break at every gap.
 */
public class BreakdownTable extends TableLayout {

  private static final int RULE_ALPHA = 0x73;

  private final List<Rule> rules = new ArrayList<Rule>();
  private final Paint paint = new Paint();
  private final Rect bounds = new Rect();

  public BreakdownTable(Context context) {
    super(context);
  }

  public BreakdownTable(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  /**
   * Hangs a rule from a step's dot down to the last of its rows that is showing.
   *
   * @param dot the dot before the step's name, which the rule is centred under
   * @param rows the step's own rows under it, in order, any of which may be null or gone
   */
  public void addRule(View dot, int color, List<View> rows) {
    rules.add(new Rule(dot, color, rows));
    invalidate();
  }

  @Override
  public void removeAllViews() {
    rules.clear();
    super.removeAllViews();
  }

  @Override
  protected void dispatchDraw(Canvas canvas) {
    super.dispatchDraw(canvas);
    float width = 2 * getResources().getDisplayMetrics().density;
    for (Rule rule : rules) {
      View last = null;
      for (View row : rule.rows) {
        if (row != null && row.getVisibility() == VISIBLE) {
          last = row;
        }
      }
      if (last == null || rule.dot.getVisibility() != VISIBLE) {
        continue;
      }
      rule.dot.getDrawingRect(bounds);
      offsetDescendantRectToMyCoords(rule.dot, bounds);
      float x = bounds.exactCenterX();
      float top = bounds.bottom + width;
      last.getDrawingRect(bounds);
      offsetDescendantRectToMyCoords(last, bounds);
      paint.setColor((rule.color & 0x00FFFFFF) | (RULE_ALPHA << 24));
      canvas.drawRect(x - width / 2, top, x + width / 2, bounds.bottom, paint);
    }
  }

  private static final class Rule {
    private final View dot;
    private final int color;
    private final List<View> rows;

    private Rule(View dot, int color, List<View> rows) {
      this.dot = dot;
      this.color = color;
      this.rows = rows;
    }
  }
}
