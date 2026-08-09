package com.cube.nanotimer.gui.widget;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import com.cube.nanotimer.R;

/**
 * What a drill settles before it deals its first case: why the mode cannot be changed later, and
 * what the planning limit is for. Both used to sit as grey paragraphs between the last control and
 * Start, which is the device the graph screen already replaced with a help button.
 */
public class DrillHelpDialog extends NanoTimerBottomSheetFragment {

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    View v = inflater.inflate(R.layout.drill_help_dialog, container, false);
    v.findViewById(R.id.buDrillHelpDone).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        dismiss();
      }
    });
    return v;
  }
}
