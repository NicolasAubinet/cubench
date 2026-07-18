package com.cube.nanotimer.gui.widget;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import androidx.gridlayout.widget.GridLayout;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import com.cube.nanotimer.App;
import com.cube.nanotimer.R;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.session.TimesStatistics;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.helper.GUIUtils;
import com.cube.nanotimer.vo.SessionDetails;
import com.cube.nanotimer.vo.SolveType;

import java.util.ArrayList;
import java.util.List;

public class SessionDetailDialog extends NanoTimerDialogFragment {

  private static final int TIMES_PER_LINE = 4;
  private static final String ARG_SOLVETYPE = "solvetype";

  private LayoutInflater inflater;
  private TextView tvSessionStart;
  private Spinner spSessionsList;
  private ArrayAdapter<String> spinnerAdapter;
  private GridLayout sessionTimesLayout;
  private List<Long> sessionStarts;
  private boolean sessionStartsInitialized;
  private List<Long> sessionTimes;
  private SolveType solveType;

  public static SessionDetailDialog newInstance(SolveType solveType) {
    SessionDetailDialog sessionDetailDialog = new SessionDetailDialog();
    Bundle bundle = new Bundle();
    bundle.putSerializable(ARG_SOLVETYPE, solveType);
    sessionDetailDialog.setArguments(bundle);
    return sessionDetailDialog;
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    inflater = getActivity().getLayoutInflater();
    final View v = inflater.inflate(R.layout.sessiondetail_dialog, null);
    solveType = (SolveType) getArguments().getSerializable(ARG_SOLVETYPE);
    App.INSTANCE.getService().getSessionDetails(solveType, new DataCallback<SessionDetails>() {
      @Override
      public void onData(final SessionDetails data) {
        getActivity().runOnUiThread(new Runnable() {
          @Override
          public void run() {
            displaySessionDetails(v, data);
          }
        });
      }
    });
    View titleView = getTitleView();
    initSessionsList(v);

    final AlertDialog dialog = new AlertDialog.Builder(getActivity(), R.style.NanoTimerDialogTheme).setView(v).create();
    dialog.setCustomTitle(titleView);
    dialog.setCanceledOnTouchOutside(true);
    return dialog;
  }

  private void displaySessionDetails(View v, SessionDetails sessionDetails) {
    sessionTimes = sessionDetails.getSessionTimes();
    TimesStatistics session = new TimesStatistics(sessionTimes);

    if (solveType.isBlind()) {
      v.findViewById(R.id.bestAveragesLayout).setVisibility(View.GONE);
      v.findViewById(R.id.trBestMeanOfThree).setVisibility(View.VISIBLE);
      v.findViewById(R.id.trAccuracy).setVisibility(View.VISIBLE);
      ((TextView) v.findViewById(R.id.tvBestMeanOfThree)).setText(FormatterService.INSTANCE.formatSolveTime(getBestMeanOf(sessionTimes, 3)));
      ((TextView) v.findViewById(R.id.tvAccuracy)).setText(FormatterService.INSTANCE.formatPercentage(session.getAccuracy(sessionTimes.size())));
      ((TextView) v.findViewById(R.id.tvLabelAverage)).setText(R.string.session_success_average);
      long successAverage = session.getSuccessAverageOf(sessionTimes.size(), true);
      ((TextView) v.findViewById(R.id.tvAverage)).setText(FormatterService.INSTANCE.formatSolveTime(successAverage));
    } else {
      setupBestAverages(v, sessionTimes);
      ((TextView) v.findViewById(R.id.tvAverage)).setText(FormatterService.INSTANCE.formatSolveTime(session.getAverageOf(sessionTimes.size())));
    }

    ((TextView) v.findViewById(R.id.tvSolves)).setText(String.valueOf(sessionDetails.getSessionSolvesCount()));
    ((TextView) v.findViewById(R.id.tvBest)).setText(FormatterService.INSTANCE.formatSolveTime(session.getBestTime(sessionTimes.size())));
    ((TextView) v.findViewById(R.id.tvDeviation)).setText(FormatterService.INSTANCE.formatSolveTime(session.getDeviation(sessionTimes.size())));
    sessionTimesLayout = (GridLayout) v.findViewById(R.id.sessionTimesLayout);
    sessionTimesLayout.removeAllViews();

    int bestInd = session.getBestTimeInd(solveType.isBlind());
    int worstInd = session.getWorstTimeInd(solveType.isBlind());
    int sessionTimesCount = sessionTimes.size();

    if (sessionTimesCount == 0) {
      for (int i = 0; i < TIMES_PER_LINE; i++) {
        addNewSolveTimeTextView(sessionTimesLayout);
      }
    } else {
      for (int i = 0; i < sessionTimesCount; i++) {
        TextView tv = addNewSolveTimeTextView(sessionTimesLayout);
        GUIUtils.setSessionTimeCellText(tv, sessionTimes.get(i), i, bestInd, worstInd);
      }
      // add remaining cells to have the same cells count than the above lines
      if (sessionTimesCount > TIMES_PER_LINE && sessionTimesCount % TIMES_PER_LINE != 0) {
        for (int i = 0; i < TIMES_PER_LINE - (sessionTimesCount % TIMES_PER_LINE); i++) {
          addNewSolveTimeTextView(sessionTimesLayout);
        }
      }
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

  private void setupBestAverages(View v, List<Long> times) {
    long avg5 = getBestAverageOf(times, 5);
    long avg12 = getBestAverageOf(times, 12);
    long avg50 = getBestAverageOf(times, 50);
    long avg100 = getBestAverageOf(times, 100);

    if (avg5 < 0 && avg12 < 0 && avg50 < 0 && avg100 < 0) {
      v.findViewById(R.id.bestAveragesLayout).setVisibility(View.GONE);
      return;
    }

    TableLayout averagesTableLayout = (TableLayout) v.findViewById(R.id.bestAveragesTableLayout);
    TableRow averagesTableRowHeader = (TableRow) averagesTableLayout.getChildAt(0);
    TableRow averagesTableRowContent = (TableRow) averagesTableLayout.getChildAt(1);

    setBestAverages(averagesTableRowHeader, averagesTableRowContent, 0, avg5);
    setBestAverages(averagesTableRowHeader, averagesTableRowContent, 1, avg12);
    setBestAverages(averagesTableRowHeader, averagesTableRowContent, 2, avg50);
    setBestAverages(averagesTableRowHeader, averagesTableRowContent, 3, avg100);
  }

  private void setBestAverages(TableRow tableRowHeader, TableRow tableRowContent, int index, long average) {
    int visibility;

    if (average < 0) {
      visibility = View.GONE;
    } else {
      visibility = View.VISIBLE;
      String averageStr = FormatterService.INSTANCE.formatSolveTime(average);
      ((TextView) tableRowContent.getChildAt(index)).setText(averageStr);
    }

    tableRowHeader.getChildAt(index).setVisibility(visibility);
    tableRowContent.getChildAt(index).setVisibility(visibility);
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
              sessionStartsTexts.add(FormatterService.INSTANCE.formatDateTimeWithoutSeconds(sessionStart));
            }
            spinnerAdapter = new ArrayAdapter<String>(getActivity(), R.layout.spinner_item, sessionStartsTexts);
            spinnerAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
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
                displaySessionDetails(v, data);
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

  private View getTitleView() {
    int fontSize = 20;
    LinearLayout view = new LinearLayout(getActivity());
    view.setOrientation(LinearLayout.VERTICAL);

    RelativeLayout titleLayout = new RelativeLayout(getActivity());
    titleLayout.setGravity(Gravity.CENTER_VERTICAL);
    titleLayout.setBackgroundColor(getResources().getColor(R.color.graybg));

    TextView tvStart = new TextView(getActivity());
    tvStart.setPadding(10, 0, 0, 0);
    tvStart.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);
    tvStart.setText(R.string.session_start);
    RelativeLayout.LayoutParams tvStartParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    tvStartParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
    tvStartParams.addRule(RelativeLayout.CENTER_VERTICAL);

    titleLayout.setPadding(10, 0, 0, 0);
    spSessionsList = new Spinner(getActivity());
    View sessionView = spSessionsList;

    RelativeLayout.LayoutParams sessionViewParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    sessionViewParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);

    titleLayout.addView(tvStart, tvStartParams);
    titleLayout.addView(sessionView, sessionViewParams);

    LinearLayout separator = new LinearLayout(getActivity());
    separator.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 3));
    separator.setBackgroundColor(getResources().getColor(R.color.iceblue));

    view.addView(titleLayout);
    view.addView(separator);

    return view;
  }

}
