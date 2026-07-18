package com.cube.nanotimer.gui.widget.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import com.cube.nanotimer.R;
import com.cube.nanotimer.gui.widget.NanoTimerDialogFragment;

public abstract class ConfirmDialog extends NanoTimerDialogFragment {

  protected Dialog dialog;
  protected View view;

  // Subclasses can override to opt out of the modern rounded frame by returning 0.
  protected int getDialogTheme() {
    return R.style.NanoTimerDialogTheme;
  }

  protected Dialog getDialog(int confirmText) {
    view = getCustomView();
    final AlertDialog d;

    d = new AlertDialog.Builder(getActivity(), getDialogTheme())
        .setView(view)
        .setPositiveButton(confirmText, new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int whichButton) {
          }
        })
        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int whichButton) {
          }
        })
        .create();
    showSoftKeyboard(d);
    return d;
  }

  protected abstract void onConfirm();

  public void showSoftKeyboard(Dialog d) {
    d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
  }

  @Override
  public void onStart() {
    super.onStart();
    // redefining to avoid closing the dialog if we don't want to
    AlertDialog d = (AlertDialog) getDialog();
    if (d != null) {
      Button positiveButton = d.getButton(Dialog.BUTTON_POSITIVE);
      if (positiveButton != null) {
        positiveButton.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            onConfirm();
          }
        });
      }
    }
  }

  protected View getCustomView() {
    LayoutInflater factory = LayoutInflater.from(getActivity());
    return factory.inflate(R.layout.edittext_field, null);
  }

}
