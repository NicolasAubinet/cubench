package com.cube.nanotimer.gui.widget;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import androidx.gridlayout.widget.GridLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import com.cube.nanotimer.App;
import com.cube.nanotimer.R;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.session.TimesStatistics;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.helper.GUIUtils;
import com.cube.nanotimer.util.helper.TimeColorScale;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.SessionDetails;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveType;

import java.util.ArrayList;
import java.util.List;

public class SessionDetailDialog extends NanoTimerDialogFragment {

  private static final int TIMES_PER_LINE = 4;
  private static final String ARG_SOLVETYPE = "solvetype";
  private static final String ARG_SOLVETIME = "solvetime";
  private static final String ARG_AVERAGE_SIZE = "averagesize";

  private LayoutInflater inflater;
  private Spinner spSessionsList;
  private ArrayAdapter<String> spinnerAdapter;
  private GridLayout sessionTimesLayout;
  private List<Long> sessionStarts;
  private boolean sessionStartsInitialized;
  private List<Long> sessionTimes;
  private SolveType solveType;
  private int averageSize; // 0 when showing a session
  private SolveTime averageSolve;
  private String copyText; // what is on show, as plain text

  public static SessionDetailDialog newInstance(SolveType solveType) {
    SessionDetailDialog sessionDetailDialog = new SessionDetailDialog();
    Bundle bundle = new Bundle();
    bundle.putSerializable(ARG_SOLVETYPE, solveType);
    sessionDetailDialog.setArguments(bundle);
    return sessionDetailDialog;
  }

  /** Shows the solves an average recorded on a solve was taken over, rather than a session. */
  public static SessionDetailDialog newInstance(SolveTime solveTime, int averageSize) {
    SessionDetailDialog sessionDetailDialog = newInstance(solveTime.getSolveType());
    sessionDetailDialog.getArguments().putSerializable(ARG_SOLVETIME, solveTime);
    sessionDetailDialog.getArguments().putInt(ARG_AVERAGE_SIZE, averageSize);
    return sessionDetailDialog;
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    inflater = getActivity().getLayoutInflater();
    final View v = inflater.inflate(R.layout.sessiondetail_dialog, null);
    solveType = (SolveType) getArguments().getSerializable(ARG_SOLVETYPE);
    averageSize = getArguments().getInt(ARG_AVERAGE_SIZE);
    DataCallback<SessionDetails> displayCallback = new DataCallback<SessionDetails>() {
      @Override
      public void onData(final SessionDetails data) {
        getActivity().runOnUiThread(new Runnable() {
          @Override
          public void run() {
            displaySessionDetails(v, data, data.getSessionStart());
          }
        });
      }
    };
    spSessionsList = (Spinner) v.findViewById(R.id.spSessionsList);
    if (averageSize > 0) {
      averageSolve = (SolveTime) getArguments().getSerializable(ARG_SOLVETIME);
      ((TextView) v.findViewById(R.id.tvSessionTitle)).setText(averageLabel(averageSize));
      spSessionsList.setVisibility(View.GONE);
      App.INSTANCE.getService().getAverageDetails(averageSolve, averageSize, displayCallback);
    } else {
      App.INSTANCE.getService().getSessionDetails(solveType, displayCallback);
      initSessionsList(v);
    }

    v.findViewById(R.id.buSessionDetailHelp).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        DialogUtils.showFragment(getActivity(), SessionDetailHelpDialog.newInstance(solveType.isBlind()));
      }
    });
    v.findViewById(R.id.buSessionDetailCopy).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (copyText != null) {
          DialogUtils.copyToClipboard(getActivity(), "session", copyText, R.string.details_copied);
        }
      }
    });

    final AlertDialog dialog = new AlertDialog.Builder(getActivity(), R.style.NanoTimerDialogTheme).setView(v).create();
    dialog.setCanceledOnTouchOutside(true);
    return dialog;
  }

  private void displaySessionDetails(View v, SessionDetails sessionDetails, long sessionStart) {
    sessionTimes = sessionDetails.getSessionTimes();
    TimesStatistics session = new TimesStatistics(sessionTimes);
    int count = sessionTimes.size();
    FormatterService formatter = FormatterService.INSTANCE;
    List<String> stats = new ArrayList<String>();

    int averageLabel = solveType.isBlind() ? R.string.session_success_average : R.string.session_avg;
    long average = solveType.isBlind() ? session.getSuccessAverageOf(count, true) : session.getAverageOf(count);
    ((TextView) v.findViewById(R.id.tvLabelAverage)).setText(averageLabel);
    showStat(v, R.id.tvAverage, averageLabel, formatter.formatSolveTime(average), stats);
    showStat(v, R.id.tvSolves, R.string.session_solves, String.valueOf(sessionDetails.getSessionSolvesCount()), stats);
    showStat(v, R.id.tvBest, R.string.session_best, formatter.formatSolveTime(session.getBestTime(count)), stats);
    showStat(v, R.id.tvDeviation, R.string.session_deviation, formatter.formatSolveTime(session.getDeviation(count)), stats);
    if (solveType.isBlind()) {
      v.findViewById(R.id.bestAveragesLayout).setVisibility(View.GONE);
      v.findViewById(R.id.blindStatsLayout).setVisibility(View.VISIBLE);
      showStat(v, R.id.tvBestMeanOfThree, R.string.session_best_mean_of_three, formatter.formatSolveTime(getBestMeanOf(sessionTimes, 3)), stats);
      showStat(v, R.id.tvAccuracy, R.string.session_accuracy, formatter.formatPercentage(session.getAccuracy(count)), stats);
    } else {
      setupBestAverages(v, sessionTimes, stats);
    }
    sessionTimesLayout = (GridLayout) v.findViewById(R.id.sessionTimesLayout);
    sessionTimesLayout.removeAllViews();

    // Ranked by colour against the session itself, as the timer's own grid and its bars are.
    TimeColorScale scale = new TimeColorScale(getActivity());
    scale.setTimes(sessionTimes, false);
    int sessionTimesCount = sessionTimes.size();

    if (sessionTimesCount == 0) {
      for (int i = 0; i < TIMES_PER_LINE; i++) {
        addNewSolveTimeTextView(sessionTimesLayout);
      }
    } else {
      for (int i = 0; i < sessionTimesCount; i++) {
        TextView tv = addNewSolveTimeTextView(sessionTimesLayout);
        long time = sessionTimes.get(i);
        GUIUtils.setSessionTimeCellColor(tv, time, scale.colorFor(time, time < 0));
      }
      // add remaining cells to have the same cells count than the above lines
      if (sessionTimesCount > TIMES_PER_LINE && sessionTimesCount % TIMES_PER_LINE != 0) {
        for (int i = 0; i < TIMES_PER_LINE - (sessionTimesCount % TIMES_PER_LINE); i++) {
          addNewSolveTimeTextView(sessionTimesLayout);
        }
      }
    }
    copyText = buildCopyText(sessionDetails.getSolves(), sessionStart, stats);
  }

  private void showStat(View v, int valueId, int labelId, String value, List<String> copyLines) {
    ((TextView) v.findViewById(valueId)).setText(value);
    copyLines.add(getString(labelId) + ": " + value);
  }

  /** The stats as listed, then every solve oldest first with its scramble, the way timers share one. */
  private String buildCopyText(List<SolveTime> solves, long sessionStart, List<String> stats) {
    FormatterService formatter = FormatterService.INSTANCE;
    StringBuilder sb = new StringBuilder();
    sb.append(CubeType.getCubeType(solveType.getCubeTypeId()).getName()).append(" · ").append(solveType.getName()).append('\n');
    if (averageSize > 0) {
      sb.append(getString(averageLabel(averageSize))).append(" · ").append(formatter.formatDateTimeToMinute(averageSolve.getTimestamp()));
    } else {
      sb.append(getString(R.string.session_title)).append(" · ").append(formatter.formatSessionStart(sessionStart));
    }
    sb.append("\n\n");
    for (String stat : stats) {
      sb.append(stat).append('\n');
    }
    sb.append('\n').append(getString(R.string.times)).append(":\n");
    for (int i = solves.size() - 1; i >= 0; i--) {
      SolveTime solve = solves.get(i);
      sb.append(solves.size() - i).append(". ").append(formatter.formatSolveTime(solve));
      if (solve.getScramble() != null) {
        sb.append(" (").append(solve.getScramble().replaceAll("\\s+", " ").trim()).append(')');
      }
      sb.append('\n');
    }
    return sb.toString().trim();
  }

  private int averageLabel(int size) {
    switch (size) {
      case 3: return R.string.mo3_label;
      case 5: return R.string.ao5_label;
      case 12: return R.string.ao12_label;
      case 50: return R.string.ao50_label;
      default: return R.string.ao100_label;
    }
  }

  private long getBestMeanOf(List<Long> times, int n) {
    long best = Long.MAX_VALUE;
    for (int i = 0; i <= times.size() - n; i++) {
      TimesStatistics session = new TimesStatistics(times.subList(i, Math.min(i + n, times.size())));
      long mean = session.getMeanOf(n);
      if (mean > 0 && mean < best) {
        best = mean;
      }
    }
    return best == Long.MAX_VALUE ? -2 : best;
  }

  private long getBestAverageOf(List<Long> times, int n) {
    if (averageSize > 0 && n >= averageSize) {
      return -2; // inside an average, its own size would only repeat the headline
    }
    long best = Long.MAX_VALUE;
    for (int i = 0; i <= times.size() - n; i++) {
      TimesStatistics session = new TimesStatistics(times.subList(i, Math.min(i + n, times.size())));
      long avg = session.getAverageOf(n);
      if (avg > 0 && avg < best) {
        best = avg;
      }
    }
    return best == Long.MAX_VALUE ? -2 : best;
  }

  private void setupBestAverages(View v, List<Long> times, List<String> copyLines) {
    long avg5 = getBestAverageOf(times, 5);
    long avg12 = getBestAverageOf(times, 12);
    long avg50 = getBestAverageOf(times, 50);
    long avg100 = getBestAverageOf(times, 100);

    if (avg5 < 0 && avg12 < 0 && avg50 < 0 && avg100 < 0) {
      v.findViewById(R.id.bestAveragesLayout).setVisibility(View.GONE);
      return;
    }
    // shown again explicitly: the same views serve every session the picker switches to
    v.findViewById(R.id.bestAveragesLayout).setVisibility(View.VISIBLE);

    List<String> shown = new ArrayList<String>();
    setBestAverage(v, R.id.avgTileFive, R.id.tvAvgOfFive, avg5, R.string.ao5_label, shown);
    setBestAverage(v, R.id.avgTileTwelve, R.id.tvAvgOfTwelve, avg12, R.string.ao12_label, shown);
    setBestAverage(v, R.id.avgTileFifty, R.id.tvAvgOfFifty, avg50, R.string.ao50_label, shown);
    setBestAverage(v, R.id.avgTileHundred, R.id.tvAvgOfHundred, avg100, R.string.ao100_label, shown);
    copyLines.add(getString(R.string.best_averages) + ": " + TextUtils.join(", ", shown));
  }

  private void setBestAverage(View v, int tileId, int valueId, long average, int labelId, List<String> shown) {
    if (average < 0) {
      v.findViewById(tileId).setVisibility(View.GONE);
      return;
    }
    v.findViewById(tileId).setVisibility(View.VISIBLE);
    String value = FormatterService.INSTANCE.formatSolveTime(average);
    ((TextView) v.findViewById(valueId)).setText(value);
    shown.add(getString(labelId) + " " + value);
  }

  private TextView getNewSolveTimeTextView() {
    return (TextView) inflater.inflate(R.layout.session_textview, null);
  }

  private TextView addNewSolveTimeTextView(GridLayout gridLayout) {
    TextView textView = getNewSolveTimeTextView();

    int backgroundColorIndex = gridLayout.getChildCount();
    if ((backgroundColorIndex / TIMES_PER_LINE) % 2 == 1) {
      backgroundColorIndex += 1; // offset for odd lines
    }

    int resource;
    if (backgroundColorIndex % 2 == 0) {
      resource = R.drawable.session_chip_1;
    } else {
      resource = R.drawable.session_chip_2;
    }
    textView.setBackgroundResource(resource);

    GridLayout.LayoutParams param = new GridLayout.LayoutParams(
      GridLayout.spec(GridLayout.UNDEFINED, 1f),
      GridLayout.spec(GridLayout.UNDEFINED, 1f)
    );
    param.width = 0; // equal, fixed columns (the column weight defines width, not the content)
    int gap = (int) (2 * getResources().getDisplayMetrics().density); // gap between chips
    param.setMargins(gap, gap, gap, gap);
    textView.setLayoutParams(param);

    gridLayout.addView(textView);
    return textView;
  }

  private void initSessionsList(final View v) {
    App.INSTANCE.getService().getSessionStarts(solveType, new DataCallback<List<Long>>() {
      @Override
      public void onData(List<Long> data) {
        sessionStarts = data;
        getActivity().runOnUiThread(new Runnable() {
          @Override
          public void run() {
            List<String> sessionStartsTexts = new ArrayList<String>();
            for (long sessionStart : sessionStarts) {
              sessionStartsTexts.add(FormatterService.INSTANCE.formatSessionStart(sessionStart));
            }
            spinnerAdapter = new ArrayAdapter<String>(getActivity(), R.layout.pill_spinner_item, sessionStartsTexts);
            spinnerAdapter.setDropDownViewResource(R.layout.pill_spinner_dropdown_item);
            spSessionsList.setAdapter(spinnerAdapter);
          }
        });
      }
    });

    spSessionsList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        if (!sessionStartsInitialized) { // used to avoid calling the service twice when dialog is opened
          sessionStartsInitialized = true;
          return;
        }
        long from = sessionStarts.get(i);
        long to = (i-1 >= 0) ? sessionStarts.get(i-1) : System.currentTimeMillis();
        App.INSTANCE.getService().getSessionDetails(solveType, from, to, new DataCallback<SessionDetails>() {
          @Override
          public void onData(final SessionDetails data) {
            getActivity().runOnUiThread(new Runnable() {
              @Override
              public void run() {
                displaySessionDetails(v, data, from);
              }
            });
          }
        });
      }

      @Override
      public void onNothingSelected(AdapterView<?> adapterView) {
      }
    });
  }

}
