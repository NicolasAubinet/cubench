package com.cube.nanotimer.gui.widget.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.gui.widget.BlindSetupPicker;

/**
 * Asks, once, the two things a blindfolded reconstruction is named through: how the solver holds the
 * cube, and which pieces they shoot from.
 *
 * <p>Put at the first blind solve with a cube connected, because that is the moment the answers
 * start to matter: before a cube nothing names any target.
 *
 * <p>Both have a default rather than a blank, and the dialog can be dismissed: a 3-styler's
 * {@code UF}/{@code UFR} held the way the scramble was followed is right for many solvers and is
 * what the app assumes anyway, so the question is a chance to correct it and not a gate. The
 * settings row opens this very dialog again for anyone who taps past it.
 */
public final class BlindSetupPrompt {

  private BlindSetupPrompt() {
  }

  public static void askOnce(Activity activity) {
    if (activity.isFinishing() || Options.INSTANCE.isBlindSetupAsked()) {
      return;
    }
    Options.INSTANCE.setBlindSetupAsked(true); // asked, whatever comes of it
    final BlindSetupPicker picker = new BlindSetupPicker(activity, true);
    new AlertDialog.Builder(activity, R.style.NanoTimerDialogTheme)
        .setView(picker.getView())
        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            picker.save();
          }
        })
        .show();
  }
}
