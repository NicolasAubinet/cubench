package com.cube.nanotimer.gui.widget;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import com.cube.nanotimer.R;

/**
 * What the Analysis hub's figures are, for a screen made entirely of them: a composition bar, a set
 * of signed shares and two ranked tables, none of which names its own unit.
 *
 * <p>Takes the tab it was opened from and explains only that one, the reader having no use for the
 * vocabulary of a tab they are not on. Plan gets a section of its own rather than no button: hiding
 * the ? there slid the window chip sideways every time the tabs were crossed.
 */
public class AnalysisHelpDialog extends NanoTimerBottomSheetFragment {

  private static final String ARG_TAB = "tab";

  // The hub's own tab ordinals, which is how AnalysisActivity.EXTRA_TAB already names them.
  private static final int TAB_SOLVE = 0;
  private static final int TAB_CASES = 1;
  private static final int TAB_PLAN = 2;

  /** @param tab the ordinal of the tab asking, which is the section the sheet opens on */
  public static AnalysisHelpDialog newInstance(int tab) {
    AnalysisHelpDialog dialog = new AnalysisHelpDialog();
    Bundle bundle = new Bundle();
    bundle.putInt(ARG_TAB, tab);
    dialog.setArguments(bundle);
    return dialog;
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    View v = inflater.inflate(R.layout.analysis_help_dialog, container, false);

    int tab = getArguments() == null ? TAB_SOLVE : getArguments().getInt(ARG_TAB);
    v.findViewById(R.id.llAnalysisHelpSolve).setVisibility(section(tab, TAB_SOLVE));
    v.findViewById(R.id.llAnalysisHelpCases).setVisibility(section(tab, TAB_CASES));
    v.findViewById(R.id.llAnalysisHelpPlan).setVisibility(section(tab, TAB_PLAN));

    v.findViewById(R.id.buAnalysisHelpDone).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        dismiss();
      }
    });
    return v;
  }

  private static int section(int tab, int of) {
    return tab == of ? View.VISIBLE : View.GONE;
  }
}
