package com.cube.nanotimer.gui.widget;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.res.TypedArray;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import android.util.TypedValue;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import com.cube.nanotimer.App;
import com.cube.nanotimer.R;
import com.cube.nanotimer.cube.SolveBreakdown;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HistoryDetailDialog extends NanoTimerDialogFragment {

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
    buildSolution(v, solution);

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

    final AlertDialog dialog = new AlertDialog.Builder(getActivity(), R.style.NanoTimerDialogTheme).setView(v).create();
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
   * of their own, folded away until the step is tapped.
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

      List<TableRow> partRows = new ArrayList<TableRow>();
      List<SolveStep> parts = step.getSubSteps();
      for (int j = 0; j < parts.size(); j++) {
        TableRow partRow = subStepRow(parts.get(j), j, partMoveCountOf(solution, i, j));
        partRow.setVisibility(View.GONE);
        partRows.add(partRow);
        table.addView(partRow);
      }
      if (!partRows.isEmpty()) {
        makeExpandable(row, name, partRows);
      }
    }
    v.findViewById(R.id.breakdownSection).setVisibility(View.VISIBLE);
  }

  private String moveCountOf(SolveSolution solution, int stepIndex) {
    return stepIndex < solution.getSteps().size()
        ? String.valueOf(solution.getSteps().get(stepIndex).getMoveCount()) : "";
  }

  private String partMoveCountOf(SolveSolution solution, int stepIndex, int part) {
    return stepIndex < solution.getSteps().size()
        ? String.valueOf(solution.getSteps().get(stepIndex).getPartMoveCount(part)) : "";
  }

  /**
   * The moves themselves, a block per step: what the breakdown's times were spent on. Folded away
   * behind its own label, because it is a transcript to read rather than a table to scan.
   */
  private void buildSolution(View v, SolveSolution solution) {
    if (solution.isEmpty()) {
      return;
    }
    int[] colors = getStepColors();
    LinearLayout container = (LinearLayout) v.findViewById(R.id.solutionSteps);
    for (SolveSolution.Step step : solution.getSteps()) {
      if (step.getMoveCount() == 0) { // a skipped step turned nothing: it has no line to show
        continue;
      }
      TextView name = cell(R.style.SolutionStepName,
          Utils.toSmartCubeStepLocalizedName(getActivity(), step.getName(), step.getIndex()));
      name.setTextColor(Utils.isUnfinishedTail(step.getName())
          ? ContextCompat.getColor(getActivity(), R.color.gray600)
          : colors[step.getIndex() % colors.length]);

      LinearLayout heading = new LinearLayout(getActivity());
      // Set in code, not in the style: layout_* attributes are ignored for a view built this way.
      heading.addView(name,
          new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
      heading.addView(cell(R.style.SolutionStepCount, String.valueOf(step.getMoveCount())));
      container.addView(heading);
      container.addView(cell(R.style.SolutionMoves, step.getMoves()));
    }

    final View card = v.findViewById(R.id.solutionCard);
    final TextView label = (TextView) v.findViewById(R.id.solutionLabel);
    label.setText(getString(R.string.solution) + " · "
        + getString(R.string.solution_moves, solution.getMoveCount()) + " · "
        + getString(R.string.solution_tps, FormatterService.INSTANCE.formatTps(solution.getTps())));
    setChevron(label, false);
    label.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View view) {
        boolean expand = card.getVisibility() != View.VISIBLE;
        card.setVisibility(expand ? View.VISIBLE : View.GONE);
        setChevron(label, expand);
      }
    });
    v.findViewById(R.id.solutionSection).setVisibility(View.VISIBLE);
  }

  private void makeExpandable(TableRow row, final TextView name, final List<TableRow> partRows) {
    setChevron(name, false);
    TypedValue background = new TypedValue();
    getActivity().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, background, true);
    row.setBackgroundResource(background.resourceId);
    row.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        boolean expand = partRows.get(0).getVisibility() != View.VISIBLE;
        for (TableRow partRow : partRows) {
          partRow.setVisibility(expand ? View.VISIBLE : View.GONE);
        }
        setChevron(name, expand);
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
