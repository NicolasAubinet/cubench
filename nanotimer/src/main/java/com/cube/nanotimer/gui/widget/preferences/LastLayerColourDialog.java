package com.cube.nanotimer.gui.widget.preferences;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import androidx.core.content.ContextCompat;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.gui.widget.CrossFaceSwatches;
import com.cube.nanotimer.scrambler.cross.CrossFace;
import com.cube.nanotimer.util.helper.Utils;

/**
 * The settings row for the colour a solver finishes on, picked from the same chips a cross colour
 * is. A tap is the answer, so the dialog closes on it and only offers Cancel.
 *
 * <p>The drill setup screen offers the same picker through {@link #pick}, so the two never drift.
 */
public class LastLayerColourDialog extends DialogPreference {

  public LastLayerColourDialog(Context context, AttributeSet attrs) {
    super(context, attrs);
    setWidgetLayoutResource(R.layout.preference_colour_chip);
  }

  @Override
  protected View onCreateDialogView() {
    return swatchRow(getContext(), new Runnable() {
      @Override
      public void run() {
        notifyChanged(); // redraws the chip beside the summary
        getDialog().dismiss();
      }
    });
  }

  @Override
  protected void onBindView(View view) {
    super.onBindView(view);
    View chip = view.findViewById(R.id.vColourChip);
    if (chip != null) {
      paintChip(chip);
    }
  }

  /** Opens the picker outside the settings screen; {@code onPicked} runs once a colour is saved. */
  public static void pick(Context context, final Runnable onPicked) {
    final Dialog[] dialog = new Dialog[1];
    View row = swatchRow(context, new Runnable() {
      @Override
      public void run() {
        dialog[0].dismiss();
        onPicked.run();
      }
    });
    dialog[0] = new AlertDialog.Builder(context, R.style.NanoTimerDialogTheme)
        .setTitle(R.string.last_layer_colour)
        .setView(row)
        .setNegativeButton(R.string.cancel, null)
        .show();
  }

  /** Fills {@code chip} with the colour the setting holds. */
  public static void paintChip(View chip) {
    Context context = chip.getContext();
    float density = context.getResources().getDisplayMetrics().density;
    GradientDrawable fill = new GradientDrawable();
    fill.setCornerRadius(6 * density);
    fill.setColor(ContextCompat.getColor(context,
        Utils.getFaceColorRes(current().name().charAt(0))));
    fill.setStroke(Math.round(density), ContextCompat.getColor(context, R.color.gray700));
    chip.setBackground(fill);
  }

  private static View swatchRow(Context context, final Runnable onPicked) {
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    int side = context.getResources().getDimensionPixelSize(R.dimen.blind_buffers_dialog_padding);
    row.setPadding(side, context.getResources().getDimensionPixelSize(R.dimen.space_m), side, 0);
    CrossFaceSwatches swatches = new CrossFaceSwatches(context, row,
        new CrossFaceSwatches.Listener() {
          @Override
          public void onFacePicked(CrossFace face) {
            Options.INSTANCE.setLastLayerFace(face.name());
            onPicked.run();
          }
        });
    swatches.setSelection(current(), null);
    return row;
  }

  private static CrossFace current() {
    return CrossFace.valueOf(Options.INSTANCE.getLastLayerFace());
  }
}
