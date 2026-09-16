package com.cube.nanotimer.gui.widget.preferences;

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
 */
public class LastLayerColourDialog extends DialogPreference {

  public LastLayerColourDialog(Context context, AttributeSet attrs) {
    super(context, attrs);
    setWidgetLayoutResource(R.layout.preference_colour_chip);
  }

  @Override
  protected View onCreateDialogView() {
    LinearLayout row = new LinearLayout(getContext());
    row.setOrientation(LinearLayout.HORIZONTAL);
    int side = getContext().getResources().getDimensionPixelSize(R.dimen.blind_buffers_dialog_padding);
    row.setPadding(side, getContext().getResources().getDimensionPixelSize(R.dimen.space_m), side, 0);
    CrossFaceSwatches swatches = new CrossFaceSwatches(getContext(), row,
        new CrossFaceSwatches.Listener() {
          @Override
          public void onFacePicked(CrossFace face) {
            Options.INSTANCE.setLastLayerFace(face.name());
            notifyChanged(); // redraws the chip beside the summary
            getDialog().dismiss();
          }
        });
    swatches.setSelection(current(), null);
    return row;
  }

  @Override
  protected void onBindView(View view) {
    super.onBindView(view);
    View chip = view.findViewById(R.id.vColourChip);
    if (chip == null) {
      return;
    }
    Context context = getContext();
    float density = context.getResources().getDisplayMetrics().density;
    GradientDrawable fill = new GradientDrawable();
    fill.setCornerRadius(6 * density);
    fill.setColor(ContextCompat.getColor(context,
        Utils.getFaceColorRes(current().name().charAt(0))));
    fill.setStroke(Math.round(density), ContextCompat.getColor(context, R.color.gray700));
    chip.setBackground(fill);
  }

  private static CrossFace current() {
    return CrossFace.valueOf(Options.INSTANCE.getLastLayerFace());
  }
}
