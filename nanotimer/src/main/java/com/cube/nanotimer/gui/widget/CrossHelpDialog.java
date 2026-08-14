package com.cube.nanotimer.gui.widget;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import com.cube.nanotimer.R;

/**
 * What the cross solver lists, and what its three neutrality modes mean. The mode names are the
 * sport's jargon and the screen defines none of them, so the segmented control reads as three
 * unlabelled choices to anyone who has not met the term.
 */
public class CrossHelpDialog extends NanoTimerBottomSheetFragment {

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    View v = inflater.inflate(R.layout.cross_help_dialog, container, false);
    v.findViewById(R.id.buCrossHelpDone).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        dismiss();
      }
    });
    return v;
  }
}
