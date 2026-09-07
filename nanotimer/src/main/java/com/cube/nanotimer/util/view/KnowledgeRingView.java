package com.cube.nanotimer.util.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;

/**
 * How far through a set of cases the solver is: known, seen but not yet known, and never seen, as
 * arcs of one ring.
 *
 * <p>A ring rather than a bar, alone in an app whose proportions are all bars, because this is not
 * the composition of a duration but progress through a fixed set, and it is the only figure in the
 * hub with a natural "complete".
 *
 * <p><b>One hue in three steps, not three hues.</b> The three states are degrees of the same thing,
 * so they are sequential; giving them their own colours would spend the step palette on a magnitude
 * and put a fourth meaning on colours that already carry three on this screen.
 */
public class KnowledgeRingView extends View {

  /** Of the view's width: how thick the ring is drawn. */
  private static final float STROKE_RATIO = 0.12f;

  /** What the seen-but-not-known arc is worth against the known one. Shared with the legend, which
   * has to draw the same three steps of the hue beside the ring rather than its own. */
  public static final int SEEN_ALPHA = 107; // 42%

  private static final float FULL_TURN = 360f;
  private static final float TWELVE_O_CLOCK = -90f;

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF bounds = new RectF();
  private final int trackColor;

  private int known;
  private int seen;
  private int total;
  private int color;

  public KnowledgeRingView(Context context) {
    this(context, null);
  }

  public KnowledgeRingView(Context context, AttributeSet attributes) {
    super(context, attributes);
    trackColor = ContextCompat.getColor(context, R.color.hero_inset);
    color = ContextCompat.getColor(context, R.color.lightblue);
    paint.setStyle(Paint.Style.STROKE);
  }

  /**
   * @param known cases that go in unaided
   * @param seen cases that have come up but do not yet go in unaided
   * @param total how many cases the set holds, which the two above are part of
   */
  public void setCounts(int known, int seen, int total, int color) {
    this.known = known;
    this.seen = seen;
    this.total = total;
    this.color = color;
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (total <= 0) {
      return;
    }
    float stroke = getWidth() * STROKE_RATIO;
    paint.setStrokeWidth(stroke);
    float inset = stroke / 2;
    bounds.set(inset, inset, getWidth() - inset, getHeight() - inset);

    // The whole set first, so what is left to learn is the ring's own ground rather than an arc.
    paint.setColor(trackColor);
    paint.setAlpha(255);
    canvas.drawArc(bounds, TWELVE_O_CLOCK, FULL_TURN, false, paint);

    float knownSweep = FULL_TURN * known / total;
    float seenSweep = FULL_TURN * seen / total;
    paint.setColor(color);
    paint.setAlpha(SEEN_ALPHA);
    canvas.drawArc(bounds, TWELVE_O_CLOCK + knownSweep, seenSweep, false, paint);
    paint.setAlpha(Color.alpha(color));
    canvas.drawArc(bounds, TWELVE_O_CLOCK, knownSweep, false, paint);
  }
}
