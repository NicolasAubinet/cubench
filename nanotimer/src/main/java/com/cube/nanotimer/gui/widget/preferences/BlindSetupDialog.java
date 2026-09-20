package com.cube.nanotimer.gui.widget.preferences;

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import com.cube.nanotimer.gui.widget.BlindSetupPicker;

/**
 * The settings row for how a blind solver holds the cube and what they shoot from: one row, and the
 * same popup the timer screen asks it in.
 */
public class BlindSetupDialog extends DialogPreference {

  private BlindSetupPicker picker;

  public BlindSetupDialog(Context context, AttributeSet attrs) {
    super(context, attrs);
    // The body carries its own heading, as the question does; a second one above it would say it
    // twice. Set to null explicitly, or the row's own title is taken for it.
    setDialogTitle(null);
    showValues();
  }

  @Override
  protected View onCreateDialogView() {
    picker = new BlindSetupPicker(getContext(), false);
    return picker.getView();
  }

  @Override
  protected void onDialogClosed(boolean positiveResult) {
    super.onDialogClosed(positiveResult);
    if (positiveResult && picker != null) {
      picker.save();
      showValues();
    }
  }

  private void showValues() {
    setSummary(BlindSetupPicker.inWords(getContext()));
  }
}
