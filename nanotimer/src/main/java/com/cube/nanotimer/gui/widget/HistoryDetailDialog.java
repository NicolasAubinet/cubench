package com.cube.nanotimer.gui.widget;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import androidx.fragment.app.FragmentManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import com.cube.nanotimer.App;
import com.cube.nanotimer.R;
import com.cube.nanotimer.gui.widget.dialog.CommentSolveDialog;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.ScrambleFormatterService;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.view.FontFitTextView;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.SolveAverages;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveTimeAverages;

import java.util.Arrays;

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
          }
        });
        handler.onTimeDeleted(solveTime);
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
