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
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.App;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.cube.CaseExecutions;
import com.cube.nanotimer.cube.CaseFlags;
import com.cube.nanotimer.cube.CubePatternFormat;
import com.cube.nanotimer.cube.CubeStickering;
import com.cube.nanotimer.cube.VirtualCube;
import com.cube.nanotimer.gui.widget.LastLayerCaseView;
import com.cube.nanotimer.gui.widget.NanoTimerDialogFragment;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import com.cube.nanotimer.smartcube.step.AlgorithmExecution;
import com.cube.nanotimer.smartcube.step.CaseAlgorithms;
import com.cube.nanotimer.smartcube.step.CaseAlgorithms.Listed;
import com.cube.nanotimer.smartcube.step.F2LCaseAlgorithms;
import com.cube.nanotimer.smartcube.step.LastLayerCaseNames;
import com.cube.nanotimer.smartcube.step.LastLayerDiagram;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.helper.GUIUtils;
import com.cube.nanotimer.vo.SolveTime;

import java.util.ArrayList;
import java.util.List;

/**
 * One case at a size worth looking at, with the algorithms it is solved with, and the one this user
 * solves it with marked: a last layer case, or the case an F2L pair was handed.
 *
 * <p><b>An F2L pair's case is never named.</b> Its numbering is one website's rather than an
 * official one, so the dialog is titled for the pair and the case is shown on the 3D cube instead,
 * held cross down with only the cross and the pair in colour.
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
 *
 * <p><b>Opened from one solve, it shows that solve's execution instead</b>, marked "this solve":
 * the question asked there is about the moves on the screen, not about a habit.
 */
public class CaseAlgorithmsDialog extends NanoTimerDialogFragment {

  /** Sent to the fragment manager whenever what is pointed out for a case may have changed. */
  public static final String FLAGS_CHANGED = "case_flags_changed";

  private static final String ARG_CASE = "case";
  private static final String ARG_USED = "used";

  /** The cube's corner view, the drills' own: the top face and two sides, front a little ahead. */
  private static final double VIEW_LATITUDE = 26;
  private static final double VIEW_LONGITUDE = 30;
  /** The scramble dialog's distance, measured for a well of about this size. */
  private static final double CAMERA_DISTANCE = 5.2;

  /**
   * Where the case is drawn from: the cross on U, the pair going into up-front-left, which a z2
   * turns into the cross down and the slot in front right. In the cube's own slot numbering.
   */
  private static final int[] CROSS_UP_EDGES = {0, 1, 2, 3};
  private static final int CROSS_UP_PAIR_CORNER = 1;
  private static final int CROSS_UP_PAIR_EDGE = 9;

  /**
   * How many of the case's own solves are read for the moves. Each one is replayed, so this is the
   * cost of opening the dialog, and it is deep enough to see which execution is the usual one.
   */
  private static final int SOLVES_READ = 10;

  private String caseCode;
  /** The moves turned for the case in the solve the dialog was opened from, or null. */
  private String used;
  private String chosen;
  private String own;
  /** Everything they turn for the case, most turned first, empty until the solves have been read. */
  private List<Turned> turned = new ArrayList<Turned>();
  private LinearLayout rows;
  private LinearLayout yours;
  private View yoursLabel;
  private VirtualCube cube;

  public static CaseAlgorithmsDialog newInstance(String caseCode) {
    return newInstance(caseCode, null);
  }

  /**
   * @param used the moves one solve turned for the case: shown and marked in place of what the
   *     solver's recent solves say they usually turn
   */
  public static CaseAlgorithmsDialog newInstance(String caseCode, String used) {
    CaseAlgorithmsDialog frag = new CaseAlgorithmsDialog();
    Bundle args = new Bundle();
    args.putString(ARG_CASE, caseCode);
    args.putString(ARG_USED, used);
    frag.setArguments(args);
    return frag;
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    caseCode = getArguments().getString(ARG_CASE);
    used = getArguments().getString(ARG_USED);
    chosen = Options.INSTANCE.getCaseAlgorithm(caseCode);
    own = Options.INSTANCE.getOwnCaseAlgorithm(caseCode);
    View view = LayoutInflater.from(getActivity()).inflate(R.layout.case_algorithms_dialog, null);
    boolean pair = CaseAlgorithms.isPair(caseCode);

    TextView shape = view.findViewById(R.id.tvCaseShape);
    if (pair) {
      view.findViewById(R.id.vCaseChart).setVisibility(View.GONE);
      shape.setVisibility(View.GONE);
      view.findViewById(R.id.flCaseCube).setVisibility(View.VISIBLE);
      showPairCase((WebView) view.findViewById(R.id.wvCaseCube));
      ((TextView) view.findViewById(R.id.tvCaseSource)).setText(R.string.case_algorithms_order);
    } else {
      ((LastLayerCaseView) view.findViewById(R.id.vCaseChart))
          .setDiagram(LastLayerDiagram.forCase(caseCode));
      String shapeName = LastLayerCaseNames.shape(caseCode);
      shape.setText(shapeName == null ? "" : shapeName);
      shape.setVisibility(shapeName == null ? View.GONE : View.VISIBLE);
    }

    rows = view.findViewById(R.id.llCaseAlgorithms);
    yours = view.findViewById(R.id.llCaseYours);
    yoursLabel = view.findViewById(R.id.tvCaseYoursLabel);
    view.findViewById(R.id.tvCaseAddAlgorithm).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        askForAlgorithm();
      }
    });
    if (used != null) {
      turned.add(new Turned(CaseAlgorithms.asAlgorithm(caseCode, used),
          CaseAlgorithms.read(caseCode, used), 1, 1));
      refresh();
    } else {
      refresh();
      readExecution();
    }

    return new AlertDialog.Builder(getActivity(), R.style.NanoTimerDialogTheme)
        .setTitle(pair ? getString(R.string.case_title_f2l)
            : getString(caseCode.startsWith("oll_") ? R.string.case_title_oll
                : R.string.case_title_pll, LastLayerCaseNames.shortName(caseCode)))
        .setView(view)
        .setPositiveButton(R.string.close, null)
        .create();
  }

  /**
   * The case on the 3D cube, standing still: a picture of the case, not a mirror of the cube in the
   * user's hands. The player's own drag still turns it round.
   */
  private void showPairCase(WebView webView) {
    String facelets = F2LCaseAlgorithms.crossUpFacelets(CaseAlgorithms.pairCase(caseCode));
    if (facelets == null) {
      return;
    }
    cube = new VirtualCube(webView, null, () -> { });
    cube.setGyroFollowing(false);
    cube.setHold(CubeRotation.byNotation("z2").quaternion());
    cube.setView(VIEW_LATITUDE, VIEW_LONGITUDE);
    cube.setCameraDistance(CAMERA_DISTANCE);
    cube.setNudge(0);
    cube.setState(CubePatternFormat.format(facelets));
    cube.setStickering(CubeStickering.crossAndPair(CROSS_UP_EDGES, CROSS_UP_PAIR_CORNER,
        CROSS_UP_PAIR_EDGE));
  }

  @Override
  public void onResume() {
    super.onResume();
    if (cube != null) {
      cube.onResume();
    }
  }

  @Override
  public void onPause() {
    if (cube != null) {
      cube.onPause();
    }
    super.onPause();
  }

  @Override
  public void onDestroyView() {
    if (cube != null) {
      cube.destroy(); // a WebGL context outlives the dialog unless it is told not to
      cube = null;
    }
    super.onDestroyView();
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
            final List<Turned> read = read(CaseExecutions.spreadFrom(solves, caseCode));
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
          CaseAlgorithms.read(caseCode, one.getExecuted()), one.getTimes(), one.getOf()));
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
    List<Listed> listed = CaseAlgorithms.shown(caseCode);
    List<String> shown = new ArrayList<String>();
    for (int i = 0; i < listed.size(); i++) {
      Listed algorithm = listed.get(i);
      rows.addView(row(algorithm.getMoves(), algorithm.isRecommended(), i == 0,
          algorithm.getEmptySlot()));
      shown.add(algorithm.getMoves());
    }
    if (own != null && !listedAlready(shown, own)) {
      shown.add(own);
      yours.addView(row(own, false, false, CaseAlgorithms.emptySlotOf(caseCode, own)));
    }
    for (Turned one : turned) {
      if (!listedAlready(shown, one.moves)) {
        shown.add(one.moves);
        yours.addView(
            row(one.moves, false, false, CaseAlgorithms.emptySlotOf(caseCode, one.moves)));
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
      if (CaseAlgorithms.sameTurning(caseCode, already, moves)) {
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
      if (CaseAlgorithms.sameTurning(caseCode, one.moves, moves)) {
        return one;
      }
    }
    return null;
  }

  /**
   * One algorithm. The first is set a size larger and heavier: it is first because it is the one
   * most people use, and a column of identical rows says nothing about an order that is the whole
   * content of the list.
   *
   * @param emptySlot the slot the algorithm needs empty ("fl", "br"), or null
   */
  private View row(final String moves, boolean recommended, boolean top, String emptySlot) {
    boolean declared = CaseAlgorithms.sameTurning(caseCode, moves, chosen);
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
      marks.addView(chip(used != null ? R.string.case_algorithm_used : R.string.case_algorithm_mine,
          true));
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
    if (one != null && one.execution.isUnusual()) {
      row.addView(muteToggle(one.moves));
    }
    if (emptySlot != null) {
      row.addView(note(getString("fl".equals(emptySlot) ? R.string.case_algorithm_empty_fl
          : R.string.case_algorithm_empty_br)));
    }
    return row;
  }

  /**
   * Stops an algorithm off the list being pointed out, or starts it again: for a solver who turns it
   * on purpose. The algorithm alone, so another way of turning the case is still pointed out.
   */
  private TextView muteToggle(final String moves) {
    final boolean muted = CaseFlags.isMuted(caseCode, moves);
    TextView toggle = note(getString(muted ? R.string.case_algorithm_unmute
        : R.string.case_algorithm_mute));
    toggle.setTextColor(ContextCompat.getColor(getActivity(), R.color.color_accent));
    toggle.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        CaseFlags.setMuted(caseCode, moves, !muted);
        flagsChanged();
        refresh();
      }
    });
    return toggle;
  }

  private void flagsChanged() {
    getParentFragmentManager().setFragmentResult(FLAGS_CHANGED, new Bundle());
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
    private final AlgorithmExecution execution;
    private int times;
    private final int of;

    Turned(String moves, AlgorithmExecution execution, int times, int of) {
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
    chosen = CaseAlgorithms.sameTurning(caseCode, moves, chosen) ? null : moves;
    Options.INSTANCE.setCaseAlgorithm(caseCode, chosen);
    flagsChanged();
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
    field.setHint(CaseAlgorithms.isPair(caseCode) ? R.string.case_algorithm_hint_f2l
        : R.string.case_algorithm_hint);
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
        if (!CaseAlgorithms.solves(caseCode, typed)) {
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
    flagsChanged();
    for (Listed algorithm : CaseAlgorithms.shown(caseCode)) {
      if (CaseAlgorithms.sameTurning(caseCode, algorithm.getMoves(), typed)) {
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
