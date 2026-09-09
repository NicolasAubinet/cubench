package com.cube.nanotimer.gui.widget.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.widget.FrameLayout;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.gui.widget.BlindBufferPicker;

/**
 * Asks, once, which pieces the solver shoots from blindfolded.
 *
 * <p>Put at the first blind solve with a cube connected, because that is the moment the answer
 * starts to matter: it is what a blind reconstruction's targets are named through, and before a
 * cube nothing names any.
 *
 * <p>It has a default rather than a blank, and can be dismissed: a 3-styler's {@code UF}/{@code UFR}
 * is right for most solvers and is what the app assumes anyway, so the question is a chance to
 * correct it and not a gate. The settings row says the same thing again for anyone who taps past it.
 */
public final class BlindBuffersPrompt {

  private BlindBuffersPrompt() {
  }

  public static void askOnce(Activity activity) {
    if (activity.isFinishing() || Options.INSTANCE.areBlindBuffersAsked()) {
      return;
    }
    Options.INSTANCE.setBlindBuffersAsked(true); // asked, whatever comes of it
    View content = activity.getLayoutInflater().inflate(R.layout.blind_buffers_dialog, null);
    FrameLayout host = content.findViewById(R.id.bufferPicker);
    final BlindBufferPicker picker = new BlindBufferPicker(activity, host,
        Options.INSTANCE.getBlindEdgeBuffer(), Options.INSTANCE.getBlindCornerBuffer());
    host.addView(picker.getView());
    new AlertDialog.Builder(activity, R.style.NanoTimerDialogTheme)
        .setView(content)
        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            picker.save();
          }
        })
        .show();
  }
}
