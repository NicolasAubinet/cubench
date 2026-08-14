package com.cube.nanotimer.gui.widget;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import com.cube.nanotimer.R;

/**
 * What leaves the app when times are exported, and which of the two buttons does what. The screen
 * shows a running total and two actions but never says what the file is, and "where did it go" is
 * the question an export screen always gets.
 *
 * <p>The import side has no panel of its own because it has no screen of its own: above API 21 the
 * import is the system file picker, and {@code ImportActivity} is only launched below that.
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
