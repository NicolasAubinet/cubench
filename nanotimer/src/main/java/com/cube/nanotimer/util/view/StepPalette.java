package com.cube.nanotimer.util.view;

import android.content.Context;
import android.graphics.Color;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;
import com.cube.nanotimer.session.MethodStatistics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Which colour a step family is drawn in, and the wash its recognition half takes.
 *
 * <p>A family's colour is its position in the method's own step order, read out of the same palette
 * {@link SolveStepBars#stepColors} hands the bars, so a family is the same colour wherever it
 * appears: as a segment of the solve, as a dot beside a step, and on the meter under a case.
 *
 * <p>Recognition and execution are two parts of one quantity rather than two quantities, so they are
 * <b>sequential</b>: one hue in two steps, a wash of the family colour for the looking and the full
 * colour for the turning. A fixed pair would have put an orange meaning "PLL" and an orange meaning
 * "execution" on the same row.
 */
public final class StepPalette {

  /** Of the family colour: what the looking is drawn at, against the turning's full strength. */
  private static final int WASH_ALPHA = 87; // 34%
  /** The same recession for a figure rather than a fill: text needs to stay read, not recede. */
  private static final int TEXT_ALPHA = 168; // 66%

  /** CFOP's steps in solving order, which is the world the drill screens live in. */
  private static final List<String> CFOP =
      Collections.unmodifiableList(Arrays.asList("cross", "f2l", "oll", "pll"));

  private final int[] colors;
  private final List<String> families;
  private final int unknown;

  private StepPalette(Context context, List<String> families) {
    this.colors = SolveStepBars.stepColors(context);
    this.families = new ArrayList<String>(families);
    this.unknown = ContextCompat.getColor(context, R.color.gray600);
  }

  /** @param families the method's own steps, in the order they are solved in */
  public static StepPalette of(Context context, List<String> families) {
    return new StepPalette(context, families);
  }

  /** The palette a screen that only ever shows CFOP's steps reads from. */
  public static StepPalette cfop(Context context) {
    return new StepPalette(context, CFOP);
  }

  /**
   * The colour of a family or of a case in one, so a caller holding either can ask without cutting
   * the code up first. A family this palette does not know is drawn in the neutral the bars give
   * time that belongs to no step, rather than borrowing another step's colour.
   */
  public int colorFor(String code) {
    if (code == null) {
      return unknown;
    }
    int at = families.indexOf(MethodStatistics.familyOf(code));
    return at < 0 || colors.length == 0 ? unknown : colors[at % colors.length];
  }

  /** The same hue, receded: the fill standing for the looking rather than the turning. */
  public static int wash(int color) {
    return alpha(color, WASH_ALPHA);
  }

  /** The looking's figure, which recedes less than its fill does because it has to stay legible. */
  public static int dim(int color) {
    return alpha(color, TEXT_ALPHA);
  }

  private static int alpha(int color, int alpha) {
    return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
  }
}
