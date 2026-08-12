package com.cube.nanotimer.gui.widget;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import com.cube.nanotimer.R;

/**
 * What a solve type is and what the screen's three gestures do. The tap and the drag used to stand
 * as a grey caption above the first row, which is the device the graph and drill screens already
 * replaced with a help button.
 */
public class SolveTypesHelpDialog extends NanoTimerBottomSheetFragment {

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    View v = inflater.inflate(R.layout.solvetypes_help_dialog, container, false);
    v.findViewById(R.id.buSolveTypesHelpDone).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        dismiss();
      }
    });
    return v;
  }
}
