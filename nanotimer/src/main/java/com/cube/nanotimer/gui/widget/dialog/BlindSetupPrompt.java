package com.cube.nanotimer.gui.widget.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.gui.widget.BlindSetupPicker;
import com.cube.nanotimer.vo.SolveType;

/**
 * Asks, once, what a blindfolded reconstruction is named through: how the solver holds the cube, how
 * they shoot, and which pieces they shoot from.
 *
 * <p>Put at the first blind solve with a cube connected, because that is the moment the answers
 * start to matter: before a cube nothing names any target.
 *
 * <p>Each has a default rather than a blank, and the dialog can be dismissed: a 3-styler's
 * {@code UF}/{@code UFR} held the way the scramble was followed is right for many solvers and is
 * what the app assumes anyway, so the question is a chance to correct it and not a gate. The
 * settings row opens this very dialog again for anyone who taps past it.
 */
public final class BlindSetupPrompt {

  private BlindSetupPrompt() {
  }

  /** @param solveType the blind type being opened, whose method the question starts on */
  public static void askOnce(Activity activity, SolveType solveType) {
    if (activity.isFinishing() || Options.INSTANCE.isBlindSetupAsked()) {
      return;
    }
    Options.INSTANCE.setBlindSetupAsked(true); // asked, whatever comes of it
    show(activity, true, solveType, null, null);
  }

  /**
   * The same question again, whatever was answered before and whether or not it ever was. Put under
   * a blind reconstruction, where a solver looking at names they did not memorise is looking at the
   * one screen that says what this setting did.
   */
  public static void open(Activity activity, SolveType solveType, Runnable onSaved,
      Runnable onReport) {
    if (!activity.isFinishing()) {
      show(activity, false, solveType, onSaved, onReport);
    }
  }

  /** @param asked whether this is the question being put, which alone says where to answer again */
  private static void show(Activity activity, boolean asked, SolveType solveType,
      final Runnable onSaved, final Runnable onReport) {
    final BlindSetupPicker picker = new BlindSetupPicker(activity, asked, solveType);
    AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.NanoTimerDialogTheme)
        .setView(picker.getView())
        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            picker.save();
            if (onSaved != null) {
              onSaved.run();
            }
          }
        });
    if (!asked) {
      builder.setNegativeButton(R.string.cancel, null); // opened to look, not only to answer
    }
    final AlertDialog dialog = builder.show();
    picker.onReport(onReport == null ? null : new Runnable() {
      @Override
      public void run() {
        dialog.dismiss(); // the report leaves this screen, so it does not sit behind the mail
        onReport.run();
      }
    });
  }
}
