package com.cube.nanotimer.gui.widget.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;
import com.cube.nanotimer.gui.widget.NanoTimerDialogFragment;
import com.cube.nanotimer.util.helper.GUIUtils;

import java.util.ArrayList;

/**
 * The shortest ways a cross could have been built, shown after the rep that did not find one.
 *
 * <p>Handed the solutions rather than a scramble to search: the drill already ran the search when it
 * drew the case, so by the time this opens the answer has been sitting ready. Nobody is made to
 * watch a spinner having just done the work themselves.
 */
public class CrossSolutionsDialog extends NanoTimerDialogFragment {

  private static final String ARG_SOLUTIONS = "solutions";
  private static final String ARG_LENGTH = "length";

  /**
   * How many to list. A cross can have dozens of ways in the same number of moves, and past a
   * handful they stop being things to learn from and become a wall to scroll; the count above them
   * still says how many there really were.
   */
  private static final int MAX_SHOWN = 12;

  /** @param solutions each already written the way it should be read, cross on the bottom */
  public static CrossSolutionsDialog newInstance(ArrayList<String> solutions, int length) {
    CrossSolutionsDialog frag = new CrossSolutionsDialog();
    Bundle args = new Bundle();
    args.putStringArrayList(ARG_SOLUTIONS, solutions);
    args.putInt(ARG_LENGTH, length);
    frag.setArguments(args);
    return frag;
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    ArrayList<String> solutions = getArguments().getStringArrayList(ARG_SOLUTIONS);
    int length = getArguments().getInt(ARG_LENGTH);

    LinearLayout body = new LinearLayout(getActivity());
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(20), dp(8), dp(20), dp(4));

    TextView count = GUIUtils.newTextView(getActivity());
    count.setText(getString(R.string.drill_cross_solutions_count, solutions.size(), length));
    count.setTextColor(color(R.color.secondary_text));
    count.setTextSize(13);
    count.setPadding(0, 0, 0, dp(6));
    body.addView(count);

    for (int i = 0; i < solutions.size() && i < MAX_SHOWN; i++) {
      String solution = solutions.get(i);
      TextView line = new TextView(getActivity());
      line.setText(solution);
      line.setTextColor(color(R.color.gray200));
      line.setTextSize(15);
      line.setTypeface(Typeface.MONOSPACE);
      line.setPadding(0, dp(3), 0, dp(3));
      body.addView(line);
    }

    ScrollView scroll = new ScrollView(getActivity());
    scroll.addView(body, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

    return new AlertDialog.Builder(getActivity(), R.style.NanoTimerDialogTheme)
        .setTitle(R.string.drill_cross_solutions)
        .setView(scroll)
        .setPositiveButton(R.string.close, null)
        .create();
  }

  private int dp(int value) {
    return (int) (value * getResources().getDisplayMetrics().density);
  }

  private int color(int colorResId) {
    return ContextCompat.getColor(getActivity(), colorResId);
  }
}
