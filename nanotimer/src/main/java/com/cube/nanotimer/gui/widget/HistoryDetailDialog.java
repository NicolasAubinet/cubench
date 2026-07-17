package com.cube.nanotimer.gui.widget;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.res.TypedArray;
import android.os.Bundle;
import androidx.fragment.app.FragmentManager;
import android.util.TypedValue;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import com.cube.nanotimer.App;
import com.cube.nanotimer.R;
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

    buildBreakdown(v, solveTime.getSmartcubeSteps());

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
  private void buildBreakdown(View v, List<SolveStep> steps) {
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
          Utils.toSmartCubeStepLocalizedName(getActivity(), step.getName(), i));
      name.setTextColor(colors[i % colors.length]);
      TableRow row = stepRow(step, name);
      table.addView(row);

      List<TableRow> partRows = new ArrayList<TableRow>();
      List<SolveStep> parts = step.getSubSteps();
      for (int j = 0; j < parts.size(); j++) {
        TableRow partRow = subStepRow(parts.get(j), j);
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
    return row;
  }

  private TableRow stepRow(SolveStep step, TextView name) {
    TableRow row = new TableRow(getActivity());
    row.addView(name);
    row.addView(cell(R.style.BreakdownRecognitionCell, formatTime(step.getRecognitionMs())));
    row.addView(cell(R.style.BreakdownCell, formatTime(step.getExecutionMs())));
    row.addView(cell(R.style.BreakdownCell, formatTime(step.getTotalMs())));
    return row;
  }

  private TableRow subStepRow(SolveStep part, int position) {
    TableRow row = new TableRow(getActivity());
    row.addView(cell(R.style.BreakdownSubName,
        Utils.toSmartCubeStepLocalizedName(getActivity(), part.getName(), position)));
    row.addView(cell(R.style.BreakdownSubCell, formatTime(part.getRecognitionMs())));
    row.addView(cell(R.style.BreakdownSubCell, formatTime(part.getExecutionMs())));
    row.addView(cell(R.style.BreakdownSubCell, formatTime(part.getTotalMs())));
    return row;
  }

  private TextView cell(int style, String text) {
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
