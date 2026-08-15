package com.cube.nanotimer.gui.widget;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import com.cube.nanotimer.R;

/**
 * What leaves the app, and which of the two buttons does what. The screen writes two files that a
 * segmented control switches between, and "where did it go" is the question an export screen always
 * gets.
 *
 * <p>Import has no panel of its own because it has no screen to hang one on: it is a file picker,
 * and what it does with what you picked is asked in a dialog at the moment it matters.
 */
public class ExportHelpDialog extends NanoTimerBottomSheetFragment {

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    View v = inflater.inflate(R.layout.export_help_dialog, container, false);
    v.findViewById(R.id.buExportHelpDone).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        dismiss();
      }
    });
    return v;
  }
}
