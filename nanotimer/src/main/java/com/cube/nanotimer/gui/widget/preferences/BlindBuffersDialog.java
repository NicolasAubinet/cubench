package com.cube.nanotimer.gui.widget.preferences;

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.gui.widget.BlindBufferPicker;

/** The settings row for the pieces a blind solver shoots from: one row, both types. */
public class BlindBuffersDialog extends DialogPreference {

  private BlindBufferPicker picker;

  public BlindBuffersDialog(Context context, AttributeSet attrs) {
    super(context, attrs);
    showValues();
  }

  @Override
  protected View onCreateDialogView() {
    picker = new BlindBufferPicker(getContext(), null,
        Options.INSTANCE.getBlindEdgeBuffer(), Options.INSTANCE.getBlindCornerBuffer());
    // The picker carries no padding of its own, the prompt that shares it having its own frame.
    View view = picker.getView();
    int side = view.getResources().getDimensionPixelSize(R.dimen.blind_buffers_dialog_padding);
    view.setPadding(side, view.getResources().getDimensionPixelSize(R.dimen.space_m), side, 0);
    return view;
  }

  @Override
  protected void onDialogClosed(boolean positiveResult) {
    super.onDialogClosed(positiveResult);
    if (positiveResult && picker != null) {
      picker.save();
      // Answered here, so the timer screen has nothing left to ask.
      Options.INSTANCE.setBlindBuffersAsked(true);
      showValues();
    }
  }

  private void showValues() {
    setSummary(getContext().getString(R.string.blind_buffers_summary,
        Options.INSTANCE.getBlindEdgeBuffer(), Options.INSTANCE.getBlindCornerBuffer()));
  }
}
