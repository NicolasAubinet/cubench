package com.cube.nanotimer.gui.widget.dialog;

import android.app.Activity;
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

import com.cube.nanotimer.App;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.cube.CaseExecutions;
import com.cube.nanotimer.gui.widget.LastLayerCaseView;
import com.cube.nanotimer.gui.widget.NanoTimerDialogFragment;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.smartcube.step.LastLayerCaseAlgorithms;
import com.cube.nanotimer.smartcube.step.LastLayerCaseAlgorithms.Algorithm;
import com.cube.nanotimer.smartcube.step.LastLayerCaseAlgorithms.Execution;
import com.cube.nanotimer.smartcube.step.LastLayerCaseNames;
import com.cube.nanotimer.smartcube.step.LastLayerDiagram;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.helper.GUIUtils;
import com.cube.nanotimer.vo.SolveTime;

import java.util.ArrayList;
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
 * order either — it sits under the list, where it can be theirs without displacing anything. Under
 * and not over: the row carrying "the one you use" is the answer to the question the dialog was
 * opened with, and nothing of theirs may stand above it.
 *
 * <p><b>One row per algorithm, not per spelling.</b> A sixth of the table writes one algorithm twice,
 * so the rows are folded before they are drawn — otherwise a case offers a choice between an
 * algorithm and itself, and its vote arrives split, which can put the wrong row in front. What that
 * costs is that a pick is kept as the text it was while the row it belongs to may now be spelled
 * some other way, so the star is hung on the turning rather than on the string.
 *
 * <p>An algorithm they typed in is kept whether or not it is the one they are using, so trying a
 * listed one is not a way to lose the work of entering theirs.
 *
 * <p><b>What they turn is read out of their own solves, and it is what carries the words.</b> The
 * tap and the execution are two separate facts, so they get two separate marks: a <b>star</b> on the
 * one they picked, which is a thing they said, and <b>"the one you use"</b> on the one their solves
 * show, which is a thing they did. Only the second claims to know what they use, because only the
 * second is evidence.
 *
 * <p>That split does the ageing for free. A solver who changes algorithm sees the words move to the
 * new one as soon as their recent solves outvote the old, while the star stays where they put it
 * until they move it — so a stale pick shows as a stale pick rather than being silently corrected.
 * <b>Nothing here is ever written to preferences from an execution</b>: a deliberate tap is the one
 * thing this dialog exists to keep. An execution that is none of the listed algorithms goes above
 * the list as theirs, marked the same way and still stored nowhere.
 *
 * <p><b>A case can honestly have two answers</b>, picked by the angle it came up at or by which hand
 * is free, so every execution they have turned more than once is shown and each says how many of
 * their answers it was. "The one you use" goes to the one they turn most; the rest are still theirs
 * and still marked. The counts appear only where there is more than one, since "5 of your last 5" is
 * a fact about nothing.
 *
 * <p><b>Once is not an answer.</b> A case misread and then put right leaves the moves that put it
 * right recorded against the case, and they solve it, so nothing in the moves says they were a
 * scramble and a rebuild. What says so is that they happened once while the real answer happened
 * more often. So a lone execution waits for its second before it is called theirs.
 */
public class CaseAlgorithmsDialog extends NanoTimerDialogFragment {

  private static final String ARG_CASE = "case";

  /**
   * How many of the case's own solves are read for the moves. Each one is replayed, so this is the
   * cost of opening the dialog, and it is deep enough to see which execution is the usual one.
   */
  private static final int SOLVES_READ = 10;

  private String caseCode;
  private String chosen;
  private String own;
  /** Everything they turn for the case, most turned first, empty until the solves have been read. */
  private List<Turned> turned = new ArrayList<Turned>();
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
    readExecution();

    return new AlertDialog.Builder(getActivity(), R.style.NanoTimerDialogTheme)
        .setTitle(getString(caseCode.startsWith("oll_") ? R.string.case_title_oll
            : R.string.case_title_pll, LastLayerCaseNames.shortName(caseCode)))
        .setView(view)
        .setPositiveButton(R.string.close, null)
        .create();
  }

  /**
   * Reads the case out of the solves it came up in, which is a handful rather than the whole
   * history. The dialog stands and draws without it: it opens on a table that is already in memory,
   * and the marks arrive when the solves have been cut up.
   */
  private void readExecution() {
    App.INSTANCE.getService().getCaseSolves(CaseExecutions.codesFor(caseCode), SOLVES_READ,
        new DataCallback<List<SolveTime>>() {
          @Override
          public void onData(List<SolveTime> solves) {
            final List<Turned> read = read(CaseExecutions.spreadFrom(solves).get(caseCode));
            Activity activity = getActivity();
            if (activity == null) {
              return;
            }
            activity.runOnUiThread(new Runnable() {
              @Override
              public void run() {
                if (!isAdded()) {
                  return;
                }
                turned = read;
                refresh();
              }
            });
          }
        });
  }

  /**
   * Each execution named the way it will be shown, and read against the algorithms people use. Done
   * once, off the main thread, since matching an execution walks the case's rows against 24 grips.
   */
  private List<Turned> read(CaseExecutions.Spread spread) {
    List<Turned> read = new ArrayList<Turned>();
    for (CaseExecutions.Shown one : CaseExecutions.shownFrom(caseCode, spread)) {
      read.add(new Turned(one.getMoves(),
          LastLayerCaseAlgorithms.read(caseCode, one.getExecuted()), one.getTimes(), one.getOf()));
    }
    return read;
  }

  /**
   * The listed algorithms in their own order, and under them what is theirs and not on the list:
   * an algorithm they typed in, and every execution of theirs that is none of the listed ones.
   * Nothing here depends on what is chosen or turned: those mark a row, they do not rearrange them.
   */
  private void refresh() {
    rows.removeAllViews();
    yours.removeAllViews();
    List<Algorithm> listed = LastLayerCaseAlgorithms.foldedForCase(caseCode);
    List<String> shown = new ArrayList<String>();
    for (int i = 0; i < listed.size(); i++) {
      Algorithm algorithm = listed.get(i);
      rows.addView(row(algorithm.getMoves(), algorithm.isRecommended(), i == 0));
      shown.add(algorithm.getMoves());
    }
    if (own != null && !listedAlready(shown, own)) {
      shown.add(own);
      yours.addView(row(own, false, false));
    }
    for (Turned one : turned) {
      if (!listedAlready(shown, one.moves)) {
        shown.add(one.moves);
        yours.addView(row(one.moves, false, false));
      }
    }
    boolean any = yours.getChildCount() > 0;
    yoursLabel.setVisibility(any ? View.VISIBLE : View.GONE);
    yours.setVisibility(any ? View.VISIBLE : View.GONE);
  }

  /**
   * Whether a row is already up, asked of the turning rather than of the text: a list that folds two
   * spellings into one shows the folded one, and an algorithm of their own spelled the other way is
   * that row rather than a second copy of it.
   */
  private boolean listedAlready(List<String> shown, String moves) {
    for (String already : shown) {
      if (LastLayerCaseAlgorithms.sameTurning(caseCode, already, moves)) {
        return true;
      }
    }
    return false;
  }

  /**
   * What the solver turns for this row, or null where this is not one of their executions.
   *
   * <p>By the turning, for the same reason the list is folded by it. An execution is written as
   * the row of the table it matched, and the row the list draws is the one its fold is kept
   * under, which need not be the same spelling — so comparing the two as text would leave the row
   * unmarked while {@code listedAlready} had already counted the execution as being that row, and
   * it would drop off the dialog altogether.
   */
  private Turned turnedFor(String moves) {
    for (Turned one : turned) {
      if (LastLayerCaseAlgorithms.sameTurning(caseCode, one.moves, moves)) {
        return one;
      }
    }
    return null;
  }

  /**
   * One algorithm. The first is set a size larger and heavier: it is first because it is the one
   * most people use, and a column of identical rows says nothing about an order that is the whole
   * content of the list.
   */
  private View row(final String moves, boolean recommended, boolean top) {
    boolean declared = LastLayerCaseAlgorithms.sameTurning(caseCode, moves, chosen);
    Turned one = turnedFor(moves);
    boolean mine = declared || one != null; // theirs either way, said or done

    LinearLayout row = new LinearLayout(getActivity());
    row.setOrientation(LinearLayout.VERTICAL);
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

    LinearLayout line = new LinearLayout(getActivity());
    line.setOrientation(LinearLayout.HORIZONTAL);
    line.setLayoutParams(new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    row.addView(line);

    TextView notation = GUIUtils.newTextView(getActivity());
    notation.setText(moves);
    notation.setTextSize(top ? 16 : 15);
    notation.setTextColor(ContextCompat.getColor(getActivity(), R.color.white));
    notation.setLayoutParams(new LinearLayout.LayoutParams(0,
        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    if (top) {
      GUIUtils.setWeight(notation, Typeface.BOLD);
    }
    line.addView(notation);

    // Marks rather than labels, and all of them when they all apply: a choice does not stop an
    // algorithm being the recommended one, and watching that word disappear on being tapped reads
    // as having broken something.
    LinearLayout marks = new LinearLayout(getActivity());
    marks.setOrientation(LinearLayout.VERTICAL);
    marks.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
    marks.setPadding(dp(7), 0, 0, 0);
    LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    markParams.gravity = Gravity.CENTER_VERTICAL;
    marks.setLayoutParams(markParams);
    // Only the execution says this, and only the one they turn most: a tap says which one they mean
    // to use, not which one they do.
    if (one != null && one == turned.get(0)) {
      marks.addView(chip(R.string.case_algorithm_mine, true));
    }
    if (one != null && one.execution.isUnusual()) {
      marks.addView(chip(R.string.case_algorithm_unusual, false));
    }
    if (recommended) {
      marks.addView(chip(R.string.case_algorithm_recommended, false));
    }
    line.addView(marks);

    if (declared) {
      TextView star = GUIUtils.newTextView(getActivity());
      star.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_case_mine, 0, 0, 0);
      star.setGravity(Gravity.CENTER_VERTICAL);
      LinearLayout.LayoutParams starParams = new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      starParams.gravity = Gravity.CENTER_VERTICAL;
      starParams.leftMargin = dp(6);
      star.setLayoutParams(starParams);
      line.addView(star);
    }
    // How often, only where there is another answer to be compared with.
    if (one != null && turned.size() > 1) {
      row.addView(note(getString(R.string.case_algorithm_of_last, one.times, one.of)));
    }
    if (one != null && one.execution.isUnusual() && one.execution.isLonger()) {
      row.addView(note(getString(R.string.case_algorithm_longer, one.execution.getMoves(),
          one.execution.getUsualMoves())));
    }
    return row;
  }

  /** A line under an algorithm, for what will not fit in a chip. */
  private TextView note(String text) {
    TextView note = GUIUtils.newTextView(getActivity());
    note.setText(text);
    note.setTextSize(12);
    note.setTextColor(ContextCompat.getColor(getActivity(), R.color.secondary_text));
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    params.topMargin = dp(4);
    note.setLayoutParams(params);
    return note;
  }

  /** One execution of the case, named the way it is shown and read against the vote table. */
  private static final class Turned {

    private final String moves;
    private final Execution execution;
    private int times;
    private final int of;

    Turned(String moves, Execution execution, int times, int of) {
      this.moves = moves;
      this.execution = execution;
      this.times = times;
      this.of = of;
    }
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
    chosen = LastLayerCaseAlgorithms.sameTurning(caseCode, moves, chosen) ? null : moves;
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
    for (Algorithm algorithm : LastLayerCaseAlgorithms.foldedForCase(caseCode)) {
      if (LastLayerCaseAlgorithms.sameTurning(caseCode, algorithm.getMoves(), typed)) {
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
