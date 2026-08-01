package com.cube.nanotimer.gui.widget;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.TypedArray;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import android.util.TypedValue;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import com.cube.nanotimer.App;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.cube.SolveBreakdown;
import com.cube.nanotimer.cube.SolveMovesFormat;
import com.cube.nanotimer.cube.SolveSolution;
import com.cube.nanotimer.cube.SolveTypeMethod;
import com.cube.nanotimer.cube.StoredSolveReplay;
import com.cube.nanotimer.gui.widget.dialog.CommentSolveDialog;
import com.cube.nanotimer.gui.widget.dialog.CrossSolverDialog;
import com.cube.nanotimer.gui.widget.dialog.ScrambleViewDialog;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.ScrambleFormatterService;
import com.cube.nanotimer.util.ScrambleViewNotation;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.view.FontFitTextView;
import com.cube.nanotimer.util.view.SolveStepBarView;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.ScrambleType;
import com.cube.nanotimer.vo.SolveAverages;
import com.cube.nanotimer.vo.SolveStep;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveTimeAverages;
import com.cube.nanotimer.vo.SolveTypeStep;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HistoryDetailDialog extends NanoTimerBottomSheetFragment {

  private static final String ARG_SOLVETIME = "solvetime";
  private static final String ARG_CUBETYPE = "cubetype";

  private TimeChangedHandler handler;
  public static HistoryDetailDialog newInstance(SolveTime solveTime, CubeType cubeType, TimeChangedHandler handler) {
    HistoryDetailDialog hd = new HistoryDetailDialog();
    hd.handler = handler;

    Bundle bundle = new Bundle();
    bundle.putSerializable(ARG_SOLVETIME, solveTime);
    bundle.putSerializable(ARG_CUBETYPE, cubeType);
    hd.setArguments(bundle);
    return hd;
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    final View v = getActivity().getLayoutInflater().inflate(R.layout.historydetail_dialog, null);

    Bundle args = getArguments();
    final SolveTime solveTime = (SolveTime) args.getSerializable(ARG_SOLVETIME);
    final CubeType cubeType = (CubeType) args.getSerializable(ARG_CUBETYPE);

    if (solveTime.hasSteps()) {
      v.findViewById(R.id.averagesTable).setVisibility(View.GONE);
      v.findViewById(R.id.trSteps).setVisibility(View.VISIBLE);
      ((TextView) v.findViewById(R.id.tvSteps)).setText(
      FormatterService.INSTANCE.formatStepsTimes(Arrays.asList(solveTime.getStepsTimes())));
    } else if (solveTime.getSolveType().isBlind()) {
      v.findViewById(R.id.averagesTable).setVisibility(View.GONE);
      v.findViewById(R.id.trMeanOfThree).setVisibility(View.VISIBLE);
      App.INSTANCE.getService().getSolveTimeAverages(solveTime, new DataCallback<SolveTimeAverages>() {
        @Override
        public void onData(final SolveTimeAverages data) {
          Activity activity = getActivity();
          if (activity != null) {
            activity.runOnUiThread(new Runnable() {
              @Override
              public void run() {
                ((TextView) v.findViewById(R.id.tvMeanOfThree)).setText(FormatterService.INSTANCE.formatSolveTime(data.getAvgOf5())); // avg5 contains mean of 3 for blind type (same DB column)
              }
            });
          }
        }
      });
    } else {
      App.INSTANCE.getService().getSolveTimeAverages(solveTime, new DataCallback<SolveTimeAverages>() {
        @Override
        public void onData(final SolveTimeAverages data) {
          Activity activity = getActivity();
          if (activity != null) {
            activity.runOnUiThread(new Runnable() {
              @Override
              public void run() {
                if (data != null) {
                  ((TextView) v.findViewById(R.id.tvAvgOfFive)).setText(FormatterService.INSTANCE.formatSolveTime(data.getAvgOf5(), "-"));
                  ((TextView) v.findViewById(R.id.tvAvgOfTwelve)).setText(FormatterService.INSTANCE.formatSolveTime(data.getAvgOf12(), "-"));
                  ((TextView) v.findViewById(R.id.tvAvgOfFifty)).setText(FormatterService.INSTANCE.formatSolveTime(data.getAvgOf50(), "-"));
                  ((TextView) v.findViewById(R.id.tvAvgOfHundred)).setText(FormatterService.INSTANCE.formatSolveTime(data.getAvgOf100(), "-"));
                }
              }
            });
          }
        }
      });
    }

    // The sheet shows one breakdown, measured against the turning time rather than the recorded one,
    // so a DNF still has a breakdown and a turn rate. On a solve type with its own steps the user's
    // split is the one it is read through; the method's steps are still recorded either way.
    long durationMs = SolveBreakdown.solvingDurationMs(solveTime);
    if (!buildManualSteps(v, solveTime, durationMs)) {
      // Read the breakdown off the scramble and the moves rather than off what was stored beside
      // them, so a solve type whose method changed shows its whole history under the method it now
      // reads as. Falls back to what was recorded whenever the solve cannot be read again.
      StoredSolveReplay.Result reread = StoredSolveReplay.reinterpret(solveTime.getScramble(),
          solveTime.getSmartcubeMoves(), SolveTypeMethod.of(solveTime.getSolveType()));
      CubeMethod method = reread == null ? solveTime.getSmartcubeMethod() : reread.getMethod();
      List<SolveStep> read = reread == null ? solveTime.getSmartcubeSteps() : reread.getSteps();
      Integer stoppedStep =
          reread == null ? solveTime.getSmartcubeStoppedStep() : reread.getStoppedStep();
      // The tail is derived rather than stored, so it is added back here, before anything reads the
      // breakdown: the solution splits its moves by the same step windows the bar draws.
      List<SolveStep> steps = SolveBreakdown.withTail(read, stoppedStep, durationMs,
          solveTime.getSmartcubeMoves(), method);
      buildBreakdown(v, steps, SolveSolution.from(solveTime.getSmartcubeMoves(), steps),
          getString(R.string.breakdown), null, method);
    }

    final TextView tvDate = (TextView) v.findViewById(R.id.tvDate);
    final TextView tvTime = (TextView) v.findViewById(R.id.tvTime);
    FontFitTextView tvScramble = (FontFitTextView) v.findViewById(R.id.tvScramble);
    Button buPlusTwo = (Button) v.findViewById(R.id.buPlusTwo);
    Button buDNF = (Button) v.findViewById(R.id.buDNF);
    Button buDelete = (Button) v.findViewById(R.id.buDelete);
    ImageButton buShareTime = (ImageButton) v.findViewById(R.id.buShareTime);
    ImageButton buComment = (ImageButton) v.findViewById(R.id.buComment);
    ImageView imgPb = (ImageView) v.findViewById(R.id.imgPb);

    if (solveTime.isDNF()) {
      buPlusTwo.setEnabled(false);
    }
    buDNF.setText(solveTime.canUndoDNF() ? R.string.undo_dnf : R.string.DNF);
    if (solveTime.isPb()) {
      imgPb.setVisibility(View.VISIBLE);
    } else {
      imgPb.setVisibility(View.GONE);
    }

    final View scrambleCard = v.findViewById(R.id.scrambleCard);
    if (solveTime.getScramble() != null) {
      tvScramble.setText(ScrambleFormatterService.INSTANCE.formatToColoredScramble(solveTime.getScramble(), cubeType));
      scrambleCard.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View view) {
          String scramble = ScrambleFormatterService.INSTANCE.formatScrambleForExport(solveTime.getScramble(), cubeType);
          DialogUtils.copyScrambleToClipboard(getActivity(), scramble);
        }
      });
    } else {
      tvScramble.setText(R.string.no_scramble);
      scrambleCard.setClickable(false);
      scrambleCard.setForeground(null);
    }
    setUpScrambleTools(v, solveTime, cubeType);
    tvDate.setText(FormatterService.INSTANCE.formatDateTime(solveTime.getTimestamp()));
    tvTime.setText(FormatterService.INSTANCE.formatSolveTime(solveTime.getTime()));
    if (solveTime.isDNF()) {
      tvTime.setTextColor(getResources().getColor(R.color.dnf_time));
    }

    final BottomSheetDialog dialog = new BottomSheetDialog(getActivity(), getTheme());
    dialog.setContentView(v);
    dialog.setCanceledOnTouchOutside(true);

    buPlusTwo.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View view) {
        if (!solveTime.isDNF()) {
          solveTime.setPlusTwo(!solveTime.isPlusTwo(), true);
          saveTime(solveTime);
        }
        dialog.dismiss();
      }
    });

    buDNF.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View view) {
        if (solveTime.canUndoDNF()) {
          solveTime.undoDNF();
          saveTime(solveTime);
        } else if (!solveTime.isDNF()) {
          solveTime.setDNF();
          saveTime(solveTime);
        }
        dialog.dismiss();
      }
    });

    buDelete.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View view) {
        App.INSTANCE.getService().deleteTime(solveTime, new DataCallback<SolveAverages>() {
          public void onData(SolveAverages data) {
            handler.onTimeDeleted(solveTime); // once deleted, so a handler may safely re-read the averages
          }
        });
        dialog.dismiss();
      }
    });

    buShareTime.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        DialogUtils.shareTime(getActivity(), solveTime, cubeType);
      }
    });

    buComment.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        DialogUtils.showFragment(getActivity(), CommentSolveDialog.newInstance(solveTime, handler));
      }
    });

    return dialog;
  }

  /**
   * The scramble-derived study tools shown in the scramble header: a visual scramble diagram, and
   * (3x3 full scrambles only) the optimal-cross solver. Each button is hidden when it doesn't apply,
   * and the whole header goes with the card when there is no scramble to work from.
   */
  private void setUpScrambleTools(View v, final SolveTime solveTime, final CubeType cubeType) {
    ImageButton buScrambleView = (ImageButton) v.findViewById(R.id.buScrambleView);
    ImageButton buCrossSolver = (ImageButton) v.findViewById(R.id.buCrossSolver);

    if (solveTime.getScramble() == null) {
      v.findViewById(R.id.scrambleHeader).setVisibility(View.GONE);
      return;
    }
    // The scramble is stored as one string; the tools below want it split back into moves.
    final String[] scramble = ScrambleFormatterService.INSTANCE
        .parseStringScrambleToArray(solveTime.getScramble(), cubeType);

    boolean scrambleViewAvailable = ScrambleViewNotation.getRenderKey(cubeType) != null;
    buScrambleView.setVisibility(scrambleViewAvailable ? View.VISIBLE : View.GONE);
    if (scrambleViewAvailable) {
      buScrambleView.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View view) {
          openScrambleView(cubeType, scramble);
        }
      });
    }

    ScrambleType scrambleType = solveTime.getSolveType().getScrambleType();
    boolean crossSolverAvailable = cubeType == CubeType.THREE_BY_THREE
        && (scrambleType == null || scrambleType.isDefault()); // null scramble type means the default full scramble
    buCrossSolver.setVisibility(crossSolverAvailable ? View.VISIBLE : View.GONE);
    if (crossSolverAvailable) {
      buCrossSolver.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View view) {
          String single = ScrambleFormatterService.INSTANCE.formatScrambleAsSingleLine(scramble, cubeType);
          DialogUtils.showFragment(getActivity(), CrossSolverDialog.newInstance(single));
        }
      });
    }
  }

  private void openScrambleView(CubeType cubeType, String[] scramble) {
    String key = ScrambleViewNotation.getRenderKey(cubeType);
    String moves = ScrambleViewNotation.toCubingNotation(scramble, cubeType);
    String readable = ScrambleFormatterService.INSTANCE.formatScrambleAsSingleLine(scramble, cubeType);
    // When the diagram can't be drawn (a Clock pin notation), show the text and nudge toward the
    // notation that does render — mirrors TimerActivity.openScrambleView.
    String fallback = (moves == null && cubeType == CubeType.CLOCK)
        ? getString(R.string.scramble_view_clock_notation_hint) + "\n\n" + readable
        : readable;
    DialogUtils.showFragment(getActivity(), ScrambleViewDialog.newInstance(key, moves, fallback));
  }

  /**
   * The breakdown drawn from the user's own steps, with the recorded moves split at the taps that
   * ended them. Built only when a cube recorded the solve: without moves the table would say no more
   * than the times line above.
   *
   * <p>The split is approximate where the method breakdown is exact — a tap lands a moment after the
   * move it follows, and is timed on the phone's clock rather than the cube's. That is also why these
   * steps have no thinking/turning split: a tap says when a step ended, nothing more.
   *
   * @return true when these steps take the section over, so the method's breakdown is not drawn
   */
  private boolean buildManualSteps(View v, SolveTime solveTime, long durationMs) {
    String moves = solveTime.getSmartcubeMoves();
    if (!solveTime.hasSteps() || moves == null || moves.isEmpty()) {
      return false;
    }
    List<SolveStep> steps = SolveBreakdown.fromStepTimes(solveTime.getStepsTimes());
    SolveSolution solution = SolveSolution.from(moves, steps);
    if (solution.isEmpty()) {
      return false;
    }
    v.findViewById(R.id.trSteps).setVisibility(View.GONE); // the table tells it, and the moves with it
    SolveTypeStep[] names = solveTime.getSolveType().hasSteps()
        ? solveTime.getSolveType().getSteps() : new SolveTypeStep[0];
    buildBreakdown(v, steps, solution, getString(R.string.steps), names, null);
    return true;
  }

  /**
   * The step bar the timer showed, with the numbers behind it: a row per step, and its parts on rows
   * of their own, folded away until the step is tapped. The moves each row was spent on sit under it,
   * shown or hidden as a whole by the switch in the section header.
   *
   * @param userSteps the user's step names when their own steps are shown, null for the method's own
   *                  — the user's carry no thinking/turning split, so those columns are left out
   */
  private void buildBreakdown(View v, List<SolveStep> steps, SolveSolution solution,
      String label, SolveTypeStep[] userSteps, CubeMethod method) {
    if (steps == null || steps.isEmpty()) {
      return;
    }
    boolean split = userSteps == null;
    int[] colors = getStepColors();
    ((SolveStepBarView) v.findViewById(R.id.breakdownBar)).setSteps(steps, colors);

    TableLayout table = (TableLayout) v.findViewById(R.id.breakdownTable);
    table.addView(headerRow(split));
    for (int i = 0; i < steps.size(); i++) {
      SolveStep step = steps.get(i);
      TextView name = cell(R.style.BreakdownStepName, stepName(step, i, userSteps));
      name.setTextColor(Utils.isTailSegment(step.getName())
          ? ContextCompat.getColor(getActivity(), R.color.gray600)
          : colors[i % colors.length]);
      TableRow row = stepRow(step, name, moveCountOf(solution, i), split);
      table.addView(row);

      StepRows stepRows = new StepRows(name);
      stepRows.moves = movesRow(table, R.style.BreakdownMoves, movesOf(solution, i));
      List<SolveStep> parts = step.getSubSteps();
      for (int j = 0; j < parts.size(); j++) {
        TableRow partRow = subStepRow(parts.get(j), j, partMoveCountOf(solution, i, j));
        table.addView(partRow);
        stepRows.partRows.add(partRow);
        stepRows.partMoves.add(movesRow(table, R.style.BreakdownSubMoves, partMovesOf(solution, i, j)));
      }
      breakdownRows.add(stepRows);
      if (!stepRows.partRows.isEmpty()) {
        makeExpandable(row, stepRows);
      }
    }
    buildMovesSwitch(v, solution, label, method);
    applyRowVisibility();
    v.findViewById(R.id.breakdownSection).setVisibility(View.VISIBLE);
  }

  /** A user step goes by the name it was given, or by its position when the steps changed since. */
  private CharSequence stepName(SolveStep step, int index, SolveTypeStep[] userSteps) {
    if (userSteps == null) {
      return withCaseColor(step, index);
    }
    if (index < userSteps.length && userSteps[index].getName() != null) {
      return userSteps[index].getName();
    }
    return getString(R.string.breakdown_step) + " " + (index + 1);
  }

  /**
   * The rows of one step, so a change of switch or of fold can be applied to all of them at once.
   * A step's own moves stand in for its parts' while it is folded, and step it aside when it opens.
   */
  private static final class StepRows {
    private final TextView name;
    private final List<TableRow> partRows = new ArrayList<TableRow>();
    private final List<TextView> partMoves = new ArrayList<TextView>();
    private TextView moves;
    private boolean expanded = true; // folding is for skimming; a solve opens fully told

    private StepRows(TextView name) {
      this.name = name;
    }
  }

  private final List<StepRows> breakdownRows = new ArrayList<StepRows>();
  private boolean showMoves;

  /** Shows the solve's move count and turn rate, and turns every moves row on or off at once. */
  private void buildMovesSwitch(View v, SolveSolution solution, String label, CubeMethod method) {
    ((TextView) v.findViewById(R.id.breakdownLabel)).setText(label);
    TextView movesLabel = (TextView) v.findViewById(R.id.movesSwitchLabel);
    SwitchCompat sw = (SwitchCompat) v.findViewById(R.id.swMoves);
    if (solution.isEmpty()) { // nothing was recorded to show: the switch would toggle empty rows
      movesLabel.setVisibility(View.GONE);
      sw.setVisibility(View.GONE);
      return;
    }
    TextView totals = (TextView) v.findViewById(R.id.breakdownTotals);
    StringBuilder text = new StringBuilder()
        .append(getString(R.string.breakdown_moves_count, solution.getMoveCount())).append(" · ")
        .append(getString(R.string.breakdown_tps,
            FormatterService.INSTANCE.formatTps(solution.getTps())));
    // A blind solve is read by how many algorithms it took; a sighted method's parts are fixed.
    if (method == CubeMethod.BLIND && solution.getPartCount() > 0) {
      text.append(" · ").append(getString(R.string.breakdown_algs, solution.getPartCount()));
    }
    totals.setText(text);
    totals.setVisibility(View.VISIBLE);
    showMoves = Options.INSTANCE.isBreakdownShowMoves();
    sw.setChecked(showMoves);
    sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton button, boolean checked) {
        showMoves = checked;
        Options.INSTANCE.setBreakdownShowMoves(checked);
        applyRowVisibility();
      }
    });
  }

  /**
   * A part's rows follow its step's fold; the moves rows follow the switch on top of that. A folded
   * step shows the moves of the whole step, an open one leaves them to its parts.
   */
  private void applyRowVisibility() {
    for (StepRows step : breakdownRows) {
      boolean hasParts = !step.partRows.isEmpty();
      setVisible(step.moves, showMoves && !(hasParts && step.expanded));
      for (int i = 0; i < step.partRows.size(); i++) {
        setVisible(step.partRows.get(i), step.expanded);
        setVisible(step.partMoves.get(i), step.expanded && showMoves);
      }
    }
  }

  private void setVisible(View view, boolean visible) {
    if (view != null) {
      view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
  }

  /**
   * The moves go straight into the table rather than into a row of it, so they run its whole width
   * instead of being squeezed into the name column. A step that turned nothing has no row at all.
   */
  private TextView movesRow(TableLayout table, int style, String moves) {
    if (moves == null || moves.isEmpty()) {
      return null;
    }
    TextView view = cell(style, dimRotations(moves));
    table.addView(view);
    return view;
  }

  /**
   * Greys the whole-cube rotations so the turns stand out from them. They are not moves and are
   * not counted, and setting them apart also makes a habit visible at a glance — more than one
   * rotation inside a single F2L pair, say.
   */
  private CharSequence dimRotations(String moves) {
    SpannableString text = new SpannableString(moves);
    int color = ContextCompat.getColor(getActivity(), R.color.gray600);
    for (int start = 0; start < moves.length(); ) {
      int end = moves.indexOf(' ', start);
      if (end < 0) {
        end = moves.length();
      }
      if (SolveMovesFormat.isRotation(moves.substring(start, end))) {
        text.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
      start = end + 1;
    }
    return text;
  }

  private String movesOf(SolveSolution solution, int stepIndex) {
    return stepIndex < solution.getSteps().size()
        ? solution.getSteps().get(stepIndex).getMoves() : "";
  }

  private String partMovesOf(SolveSolution solution, int stepIndex, int part) {
    return stepIndex < solution.getSteps().size()
        ? solution.getSteps().get(stepIndex).getPartMoves(part) : "";
  }

  private String moveCountOf(SolveSolution solution, int stepIndex) {
    return stepIndex < solution.getSteps().size()
        ? String.valueOf(solution.getSteps().get(stepIndex).getMoveCount()) : "";
  }

  private String partMoveCountOf(SolveSolution solution, int stepIndex, int part) {
    return stepIndex < solution.getSteps().size()
        ? String.valueOf(solution.getSteps().get(stepIndex).getPartMoveCount(part)) : "";
  }

  private void makeExpandable(TableRow row, final StepRows stepRows) {
    setChevron(stepRows.name, stepRows.expanded);
    TypedValue background = new TypedValue();
    getActivity().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, background, true);
    row.setBackgroundResource(background.resourceId);
    row.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        stepRows.expanded = !stepRows.expanded;
        setChevron(stepRows.name, stepRows.expanded);
        applyRowVisibility();
      }
    });
  }

  private void setChevron(TextView name, boolean expanded) {
    name.setCompoundDrawablesWithIntrinsicBounds(0, 0,
        expanded ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down, 0);
  }

  private int[] getStepColors() {
    TypedArray stepColors = getResources().obtainTypedArray(R.array.solve_step_colors);
    int[] colors = new int[stepColors.length()];
    for (int i = 0; i < colors.length; i++) {
      colors[i] = stepColors.getColor(i, 0);
    }
    stepColors.recycle();
    return colors;
  }

  private TableRow headerRow(boolean split) {
    TableRow row = new TableRow(getActivity());
    row.addView(cell(R.style.BreakdownHeaderName, getString(R.string.breakdown_step)));
    if (split) {
      row.addView(cell(R.style.BreakdownHeaderCell, getString(R.string.breakdown_recognition)));
      row.addView(cell(R.style.BreakdownHeaderCell, getString(R.string.breakdown_execution)));
    }
    row.addView(cell(R.style.BreakdownHeaderCell, getString(R.string.breakdown_total)));
    row.addView(cell(R.style.BreakdownHeaderCell, getString(R.string.breakdown_moves)));
    return row;
  }

  private TableRow stepRow(SolveStep step, TextView name, String moveCount, boolean split) {
    TableRow row = new TableRow(getActivity());
    row.addView(name);
    if (split) {
      row.addView(cell(R.style.BreakdownRecognitionCell, formatTime(step.getRecognitionMs())));
      row.addView(cell(R.style.BreakdownCell, formatTime(step.getExecutionMs())));
    }
    row.addView(cell(R.style.BreakdownCell, formatTime(step.getTotalMs())));
    row.addView(cell(R.style.BreakdownRecognitionCell, moveCount));
    return row;
  }

  private TableRow subStepRow(SolveStep part, int position, String moveCount) {
    TableRow row = new TableRow(getActivity());
    row.addView(cell(R.style.BreakdownSubName, withPairColors(part.getName(),
        Utils.toSmartCubeStepLocalizedName(getActivity(), part.getName(), position))));
    row.addView(cell(R.style.BreakdownSubCell, formatTime(part.getRecognitionMs())));
    row.addView(cell(R.style.BreakdownSubCell, formatTime(part.getExecutionMs())));
    row.addView(cell(R.style.BreakdownSubCell, formatTime(part.getTotalMs())));
    row.addView(cell(R.style.BreakdownSubCell, moveCount));
    return row;
  }

  /**
   * A last layer step is shown with the case it was left with, in the colour the table gives its
   * lesser figures rather than the step's own: which case it was is a detail of the step, and the
   * name is what the eye is running down the column for.
   */
  private CharSequence withCaseColor(SolveStep step, int index) {
    String name = Utils.toSmartCubeStepDisplayName(getActivity(), step, index);
    String caseLabel = Utils.toSmartCubeCaseLabel(getActivity(), step.getName());
    int at = caseLabel == null ? -1 : name.indexOf(caseLabel);
    if (at < 0) {
      return name;
    }
    SpannableStringBuilder text = new SpannableStringBuilder(name);
    text.setSpan(new ForegroundColorSpan(color(R.color.secondary_text)), at,
        at + caseLabel.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    return text;
  }

  /**
   * An F2L pair is labelled by the order it was built, so the two colours of its slot are what says
   * <em>which</em> pair it was. Solves recorded before the slot was stored simply keep the label.
   */
  private CharSequence withPairColors(String code, String label) {
    char[] faces = Utils.getSmartCubePairFaces(code);
    if (faces == null) {
      return label;
    }
    SpannableStringBuilder text = new SpannableStringBuilder(" " + label);
    text.setSpan(new ImageSpan(pairSwatch(faces), ImageSpan.ALIGN_BASELINE),
        0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    return text;
  }

  /** The slot's two colours as one rectangle split down the middle, with the gap to the label built
   * into the drawable so it stays a fixed size rather than a space character's. */
  private Drawable pairSwatch(char[] faces) {
    int height = dp(9);
    int gap = dp(7);
    LayerDrawable swatch = new LayerDrawable(new Drawable[] {
        new ColorDrawable(color(Utils.getFaceColorRes(faces[0]))),
        new ColorDrawable(color(Utils.getFaceColorRes(faces[1]))),
    });
    swatch.setLayerInset(0, 0, 0, height + gap, 0);
    swatch.setLayerInset(1, height, 0, gap, 0);
    swatch.setBounds(0, 0, height * 2 + gap, height);
    return swatch;
  }

  private int dp(int value) {
    return (int) (value * getResources().getDisplayMetrics().density);
  }

  private int color(int colorResId) {
    return ContextCompat.getColor(getActivity(), colorResId);
  }

  private TextView cell(int style, CharSequence text) {
    TextView cell = new TextView(getActivity(), null, 0, style);
    cell.setText(text);
    return cell;
  }

  private String formatTime(long timeMs) {
    return FormatterService.INSTANCE.formatSolveTime(timeMs);
  }

  private void saveTime(final SolveTime solveTime) {
    App.INSTANCE.getService().saveTime(solveTime, new DataCallback<SolveAverages>() {
      @Override
      public void onData(SolveAverages data) {
        handler.onTimeChanged(solveTime);
      }
    });
  }

  @Override
  public void show(FragmentManager manager, String tag) {
    if (manager.findFragmentByTag(tag) == null) {
      super.show(manager, tag);
    }
  }

}
