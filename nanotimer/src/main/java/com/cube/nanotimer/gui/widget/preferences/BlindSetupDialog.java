package com.cube.nanotimer.gui.widget.preferences;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import com.cube.nanotimer.gui.widget.BlindSetupPicker;
import com.cube.nanotimer.util.helper.DialogUtils;

/**
 * The settings row for how a blind solver holds the cube, how they shoot and what from: one row, and
 * the same popup the timer screen asks it in.
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
    picker = new BlindSetupPicker(getContext(), false, null);
    return picker.getView();
  }

  @Override
  protected void showDialog(Bundle state) {
    super.showDialog(state);
    if (getDialog() instanceof AlertDialog && picker != null) {
      DialogUtils.fitToWindow((AlertDialog) getDialog(), picker.getView());
    }
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
