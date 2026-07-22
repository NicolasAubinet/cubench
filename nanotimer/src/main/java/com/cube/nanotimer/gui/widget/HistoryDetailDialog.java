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
import com.cube.nanotimer.gui.widget.dialog.CommentSolveDialog;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.ScrambleFormatterService;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.view.FontFitTextView;
import com.cube.nanotimer.util.view.SolveStepBarView;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.SolveAverages;
import com.cube.nanotimer.vo.SolveStep;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveTimeAverages;
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

    // The tail is derived rather than stored, so it is added back here, before anything reads the
    // breakdown: the solution splits its moves by the same step windows the bar draws. Both measure
    // against the turning time, not the recorded one, so a DNF still has a breakdown and a turn rate.
    long durationMs = SolveBreakdown.solvingDurationMs(solveTime);
    List<SolveStep> steps = SolveBreakdown.withUnfinishedTail(solveTime.getSmartcubeSteps(),
        solveTime.getSmartcubeStoppedStep(), durationMs, solveTime.getSmartcubeMoves());
    SolveSolution solution = SolveSolution.from(solveTime.getSmartcubeMoves(), steps, durationMs);
    buildBreakdown(v, steps, solution);

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
      buDNF.setEnabled(false);
      buPlusTwo.setEnabled(false);
    }
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
        if (!solveTime.isDNF()) {
          solveTime.setTime(-1);
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
   * The step bar the timer showed, with the numbers behind it: a row per step, and its parts on rows
   * of their own, folded away until the step is tapped. The moves each row was spent on sit under it,
   * shown or hidden as a whole by the switch in the section header.
   */
  private void buildBreakdown(View v, List<SolveStep> steps, SolveSolution solution) {
    if (steps == null || steps.isEmpty()) {
      return;
    }
    int[] colors = getStepColors();
    ((SolveStepBarView) v.findViewById(R.id.breakdownBar)).setSteps(steps, colors);

    TableLayout table = (TableLayout) v.findViewById(R.id.breakdownTable);
    table.addView(headerRow());
    for (int i = 0; i < steps.size(); i++) {
      SolveStep step = steps.get(i);
      TextView name = cell(R.style.BreakdownStepName,
          Utils.toSmartCubeStepDisplayName(getActivity(), step, i));
      name.setTextColor(Utils.isUnfinishedTail(step.getName())
          ? ContextCompat.getColor(getActivity(), R.color.gray600)
          : colors[i % colors.length]);
      TableRow row = stepRow(step, name, moveCountOf(solution, i));
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
    buildMovesSwitch(v, solution);
    applyRowVisibility();
    v.findViewById(R.id.breakdownSection).setVisibility(View.VISIBLE);
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
  private void buildMovesSwitch(View v, SolveSolution solution) {
    ((TextView) v.findViewById(R.id.breakdownLabel)).setText(getString(R.string.breakdown));
    TextView totals = (TextView) v.findViewById(R.id.breakdownTotals);
    TextView label = (TextView) v.findViewById(R.id.movesSwitchLabel);
    SwitchCompat sw = (SwitchCompat) v.findViewById(R.id.swMoves);
    if (solution.isEmpty()) { // nothing was recorded to show: the switch would toggle empty rows
      label.setVisibility(View.GONE);
      sw.setVisibility(View.GONE);
      return;
    }
    totals.setText(getString(R.string.breakdown_moves_count, solution.getMoveCount()) + " · "
        + getString(R.string.breakdown_tps, FormatterService.INSTANCE.formatTps(solution.getTps())));
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

  private TableRow headerRow() {
    TableRow row = new TableRow(getActivity());
    row.addView(cell(R.style.BreakdownHeaderName, getString(R.string.breakdown_step)));
    row.addView(cell(R.style.BreakdownHeaderCell, getString(R.string.breakdown_recognition)));
    row.addView(cell(R.style.BreakdownHeaderCell, getString(R.string.breakdown_execution)));
    row.addView(cell(R.style.BreakdownHeaderCell, getString(R.string.breakdown_total)));
    row.addView(cell(R.style.BreakdownHeaderCell, getString(R.string.breakdown_moves)));
    return row;
  }

  private TableRow stepRow(SolveStep step, TextView name, String moveCount) {
    TableRow row = new TableRow(getActivity());
    row.addView(name);
    row.addView(cell(R.style.BreakdownRecognitionCell, formatTime(step.getRecognitionMs())));
    row.addView(cell(R.style.BreakdownCell, formatTime(step.getExecutionMs())));
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
