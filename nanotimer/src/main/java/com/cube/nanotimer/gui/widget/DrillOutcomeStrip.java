package com.cube.nanotimer.gui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;

/**
 * A drill's reps in the order dealt, one segment each, coloured by how the rep went. A cross drill
 * scores a finish (the shortest cross, a longer one, none at all); a timed drill scores a rep against
 * the drill's own mean. The reps still to come stay as faint segments, and the rep in hand is ringed.
 */
public class DrillOutcomeStrip extends View {

  public static final int OPTIMAL = 0;
  public static final int LONGER = 1;
  public static final int MISSED = 2;
  /** Finished with nothing to score it against, or skipped. */
  public static final int UNSCORED = 3;
  /** A timed rep at or under the drill's mean so far. */
  public static final int FASTER = 4;
  /** A timed rep over the drill's mean so far. */
  public static final int SLOWER = 5;
  /** Not dealt yet. */
  public static final int TO_COME = 6;

  private static final float GAP_DP = 3;
  private static final float RING_GAP_DP = 2;
  private static final float RING_DP = 1;

  private final float density;
  private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF segment = new RectF();
  private final int[] colors;
  private int[] outcomes = new int[0];
  private int current = -1;

  public DrillOutcomeStrip(Context context) {
    this(context, null);
  }

  public DrillOutcomeStrip(Context context, AttributeSet attributes) {
    super(context, attributes);
    density = getResources().getDisplayMetrics().density;
    colors = new int[] {color(R.color.green_soft), color(R.color.warning),
        color(R.color.danger_text), color(R.color.white_wash_20), color(R.color.green_soft),
        color(R.color.danger_text), color(R.color.white_wash_10)};
    ring.setStyle(Paint.Style.STROKE);
    ring.setStrokeWidth(RING_DP * density);
  }

  /** @param outcomes one outcome per rep, every one of them dealt */
  public void setOutcomes(int[] outcomes) {
    setReps(outcomes, outcomes.length, -1);
  }

  /**
   * The drill so far.
   *
   * @param done one outcome per rep finished, in the order dealt
   * @param total how many reps the drill runs; the rest are drawn as still to come
   * @param current the rep to ring, or -1 for none
   */
  public void setReps(int[] done, int total, int current) {
    outcomes = new int[Math.max(total, done.length)];
    for (int i = 0; i < outcomes.length; i++) {
      outcomes[i] = i < done.length ? done[i] : TO_COME;
    }
    this.current = current;
    invalidate();
  }

  /** The colour an outcome is drawn in, for the key under the strip. */
  public int colorOf(int outcome) {
    return colors[outcome];
  }

  @Override
  protected void onDraw(Canvas canvas) {
    int count = outcomes.length;
    if (count == 0) {
      return;
    }
    // The ring sits outside its segment, so every segment is inset by the room it needs.
    float inset = current >= 0 ? (RING_GAP_DP + RING_DP) * density : 0;
    float gap = GAP_DP * density;
    float width = (getWidth() - 2 * inset - gap * (count - 1)) / count;
    float top = inset;
    float bottom = getHeight() - inset;
    float corner = (bottom - top) / 2;
    for (int i = 0; i < count; i++) {
      float left = inset + i * (width + gap);
      int color = colors[outcomes[i]];
      segment.set(left, top, left + width, bottom);
      fill.setColor(color);
      canvas.drawRoundRect(segment, corner, corner, fill);
      if (i == current) {
        float out = RING_GAP_DP * density + ring.getStrokeWidth() / 2;
        segment.inset(-out, -out);
        ring.setColor(outcomes[i] == TO_COME ? color(R.color.lightblue) : color);
        canvas.drawRoundRect(segment, corner + out, corner + out, ring);
      }
    }
  }

  private int color(int res) {
    return ContextCompat.getColor(getContext(), res);
  }
}
