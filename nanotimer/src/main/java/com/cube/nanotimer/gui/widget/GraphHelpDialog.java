package com.cube.nanotimer.gui.widget;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import com.cube.nanotimer.R;

/**
 * What each graph draws, a row apiece. The graph screen's picker names them and nothing else says
 * what they are, least of all the distribution: bars of solve counts read as times until told
 * otherwise. Reached from the help button beside the picker, the way the smart cube's is.
 */
public class GraphHelpDialog extends NanoTimerBottomSheetFragment {

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    View v = inflater.inflate(R.layout.graph_help_dialog, container, false);
    v.findViewById(R.id.buGraphHelpDone).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        dismiss();
      }
    });
    return v;
  }
}
