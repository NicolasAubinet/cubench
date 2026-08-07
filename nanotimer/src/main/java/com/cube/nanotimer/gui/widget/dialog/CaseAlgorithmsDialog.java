package com.cube.nanotimer.gui.widget.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.gui.widget.LastLayerCaseView;
import com.cube.nanotimer.gui.widget.NanoTimerDialogFragment;
import com.cube.nanotimer.smartcube.step.LastLayerCaseAlgorithms;
import com.cube.nanotimer.smartcube.step.LastLayerCaseAlgorithms.Algorithm;
import com.cube.nanotimer.smartcube.step.LastLayerCaseNames;
import com.cube.nanotimer.smartcube.step.LastLayerDiagram;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.helper.GUIUtils;

import java.util.List;

/**
 * One last-layer case at a size worth looking at, with the algorithms it is solved with, and the
 * one this user solves it with marked.
 *
 * <p>The list is in most-used order and says so once, at the bottom. Only a case whose top algorithm
 * is clearly ahead is given a recommendation: on a case the world is split down the middle, calling
 * one of them recommended would be reading a winner out of a rounding difference. No percentages —
 * the order is the whole of what they were there to say, and a column of numbers invites comparing
 * figures that are votes on a website rather than measurements.
 *
 * <p><b>Tapping one keeps it, and nothing moves.</b> The list stays in most-used order whatever the
 * user picks: marking their choice by lifting it to the front made the order a lie and took the
 * recommendation off the row that had earned it. An algorithm of their own is not squeezed into that
 * order either — it sits above the list, where it can be theirs without displacing anything.
 *
 * <p>An algorithm they typed in is kept whether or not it is the one they are using, so trying a
 * listed one is not a way to lose the work of entering theirs.
 */
public class CaseAlgorithmsDialog extends NanoTimerDialogFragment {

  private static final String ARG_CASE = "case";

  private String caseCode;
  private String chosen;
  private String own;
  private LinearLayout rows;
  private LinearLayout yours;
  private View yoursLabel;

  public static CaseAlgorithmsDialog newInstance(String caseCode) {
    CaseAlgorithmsDialog frag = new CaseAlgorithmsDialog();
    Bundle args = new Bundle();
    args.putString(ARG_CASE, caseCode);
    frag.setArguments(args);
    return frag;
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    caseCode = getArguments().getString(ARG_CASE);
    chosen = Options.INSTANCE.getCaseAlgorithm(caseCode);
    own = Options.INSTANCE.getOwnCaseAlgorithm(caseCode);
    View view = LayoutInflater.from(getActivity()).inflate(R.layout.case_algorithms_dialog, null);

    ((LastLayerCaseView) view.findViewById(R.id.vCaseChart))
        .setDiagram(LastLayerDiagram.forCase(caseCode));

    TextView shape = view.findViewById(R.id.tvCaseShape);
    String shapeName = LastLayerCaseNames.shape(caseCode);
    shape.setText(shapeName == null ? "" : shapeName);
    shape.setVisibility(shapeName == null ? View.GONE : View.VISIBLE);

    rows = view.findViewById(R.id.llCaseAlgorithms);
    yours = view.findViewById(R.id.llCaseYours);
    yoursLabel = view.findViewById(R.id.tvCaseYoursLabel);
    view.findViewById(R.id.tvCaseAddAlgorithm).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        askForAlgorithm();
      }
    });
    refresh();

    return new AlertDialog.Builder(getActivity(), R.style.NanoTimerDialogTheme)
        .setTitle(getString(caseCode.startsWith("oll_") ? R.string.case_title_oll
            : R.string.case_title_pll, LastLayerCaseNames.shortName(caseCode)))
        .setView(view)
        .setPositiveButton(R.string.close, null)
        .create();
  }

  /**
   * The listed algorithms in their own order, and above them the user's own if they have entered
   * one that is not already on the list. Nothing here depends on what is currently chosen: a choice
   * marks a row, it does not rearrange them.
   */
  private void refresh() {
    rows.removeAllViews();
    yours.removeAllViews();
    List<Algorithm> listed = LastLayerCaseAlgorithms.forCase(caseCode);
    boolean ownIsListed = false;
    for (int i = 0; i < listed.size(); i++) {
      Algorithm algorithm = listed.get(i);
      rows.addView(row(algorithm.getMoves(), algorithm.isRecommended(), i == 0));
      ownIsListed |= algorithm.getMoves().equals(own);
    }
    boolean hasOwn = own != null && !ownIsListed;
    if (hasOwn) {
      yours.addView(row(own, false, false));
    }
    yoursLabel.setVisibility(hasOwn ? View.VISIBLE : View.GONE);
    yours.setVisibility(hasOwn ? View.VISIBLE : View.GONE);
  }

  /**
   * One algorithm. The first is set a size larger and heavier: it is first because it is the one
   * most people use, and a column of identical rows says nothing about an order that is the whole
   * content of the list.
   */
  private View row(final String moves, boolean recommended, boolean top) {
    boolean mine = moves.equals(chosen);

    LinearLayout row = new LinearLayout(getActivity());
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setPadding(dp(10), dp(9), dp(10), dp(9));
    row.setBackgroundResource(mine ? R.drawable.case_alg_mine : R.drawable.case_alg);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    params.topMargin = dp(6);
    row.setLayoutParams(params);
    row.setClickable(true);
    row.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        choose(moves);
      }
    });

    TextView notation = GUIUtils.newTextView(getActivity());
    notation.setText(moves);
    notation.setTextSize(top ? 16 : 15);
    notation.setTextColor(ContextCompat.getColor(getActivity(), R.color.white));
    notation.setLayoutParams(new LinearLayout.LayoutParams(0,
        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    if (top) {
      GUIUtils.setWeight(notation, Typeface.BOLD);
    }
    row.addView(notation);

    // Marks rather than labels, and both when both apply: a choice does not stop an algorithm being
    // the recommended one, and watching that word disappear on being tapped reads as having broken
    // something.
    LinearLayout marks = new LinearLayout(getActivity());
    marks.setOrientation(LinearLayout.VERTICAL);
    marks.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
    marks.setPadding(dp(7), 0, 0, 0);
    LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    markParams.gravity = Gravity.CENTER_VERTICAL;
    marks.setLayoutParams(markParams);
    if (mine) {
      marks.addView(chip(R.string.case_algorithm_mine, true));
    }
    if (recommended) {
      marks.addView(chip(R.string.case_algorithm_recommended, false));
    }
    row.addView(marks);

    if (mine) {
      TextView star = GUIUtils.newTextView(getActivity());
      star.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_case_mine, 0, 0, 0);
      star.setGravity(Gravity.CENTER_VERTICAL);
      LinearLayout.LayoutParams starParams = new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      starParams.gravity = Gravity.CENTER_VERTICAL;
      starParams.leftMargin = dp(6);
      star.setLayoutParams(starParams);
      row.addView(star);
    }
    return row;
  }

  private TextView chip(int textResId, boolean accent) {
    TextView chip = new TextView(getActivity(), null, 0,
        accent ? R.style.RowChipAccent : R.style.RowChip);
    chip.setText(textResId);
    chip.setTextSize(10);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    params.topMargin = dp(2);
    chip.setLayoutParams(params);
    return chip;
  }

  /** Tapping the one already kept lets it go, so a wrong tap is undone the same way it was made. */
  private void choose(String moves) {
    chosen = moves.equals(chosen) ? null : moves;
    Options.INSTANCE.setCaseAlgorithm(caseCode, chosen);
    refresh();
  }

  /**
   * An algorithm of the user's own, checked before it is kept: it has to actually solve the case it
   * is being filed under. A typo stored here would come back weeks later as the algorithm they were
   * told they use, at the moment they had forgotten it and could not tell.
   */
  private void askForAlgorithm() {
    final EditText field = new EditText(getActivity());
    field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    field.setSingleLine();
    field.setHint(R.string.case_algorithm_hint);
    field.setText(own == null ? "" : own);
    field.setTextColor(ContextCompat.getColor(getActivity(), R.color.white));
    int pad = dp(20);
    field.setPadding(pad, dp(8), pad, dp(8));

    final AlertDialog dialog = new AlertDialog.Builder(getActivity(), R.style.NanoTimerDialogTheme)
        .setTitle(R.string.case_algorithm_add)
        .setView(field)
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(R.string.save, null)
        .create();
    dialog.show();
    // Bound after showing so that a rejected algorithm leaves the dialog standing with what was
    // typed still in it. Dismissing on a typo would mean typing the whole thing again to fix one
    // turn, and an algorithm is exactly long enough for that to be the moment someone gives up.
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        String typed = field.getText().toString().trim().replaceAll("\\s+", " ");
        if (!LastLayerCaseAlgorithms.solves(caseCode, typed)) {
          DialogUtils.showInfoMessage(getActivity(), R.string.case_algorithm_wrong);
          return;
        }
        keepOwn(typed);
        dialog.dismiss();
      }
    });
  }

  /**
   * Keeps what was typed and starts using it. Only kept as theirs when it is not already on the
   * list: an algorithm typed out in full that happens to be the one most people use is that row,
   * not a second copy of it above the list.
   */
  private void keepOwn(String typed) {
    chosen = typed;
    Options.INSTANCE.setCaseAlgorithm(caseCode, chosen);
    for (Algorithm algorithm : LastLayerCaseAlgorithms.forCase(caseCode)) {
      if (algorithm.getMoves().equals(typed)) {
        refresh();
        return;
      }
    }
    own = typed;
    Options.INSTANCE.setOwnCaseAlgorithm(caseCode, own);
    refresh();
  }

  private int dp(int value) {
    return (int) (value * getResources().getDisplayMetrics().density);
  }
}
