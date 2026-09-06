package com.cube.nanotimer.gui.widget;

import android.app.Activity;
import android.widget.TextView;

import com.cube.nanotimer.R;
import com.cube.nanotimer.util.view.StepPalette;

/**
 * The bits of the shared drill cells that are not simply text, which is only the two labels naming
 * the halves of the mean.
 *
 * <p>They are written in the same wash and full strength the meter under every case is drawn in, so
 * the cell teaches the meter without a legend. A drill is one family, so there is one hue to take.
 */
public final class DrillStatCells {

  private DrillStatCells() {
  }

  /** Colours the mean cell's two labels in the family the screen is reading. */
  public static void nameHalves(Activity activity, int hue) {
    ((TextView) activity.findViewById(R.id.tvDrillMeanRecognitionLabel))
        .setTextColor(StepPalette.dim(hue));
    ((TextView) activity.findViewById(R.id.tvDrillMeanExecutionLabel)).setTextColor(hue);
  }
}
