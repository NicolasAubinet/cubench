package com.cube.nanotimer.gui;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Spinner;
import android.widget.TextView;
import com.cube.nanotimer.App;
import com.cube.nanotimer.R;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.session.TimesStatistics;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.chart.ChartData;
import com.cube.nanotimer.util.chart.ChartLineData;
import com.cube.nanotimer.util.chart.ChartUtils;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.FrequencyData;
import com.cube.nanotimer.vo.SolveHistory;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveType;
import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class GraphActivity extends NanoTimerActivity {

  private CubeType cubeType;
  private SolveType solveType;
  private List<ChartLineData> chartData = new ArrayList<>();
  private List<Long> pointTimestamps = new ArrayList<>();

  private LineChart chart;
  private Spinner spPeriod;
  private Spinner spGraphType;
  private CheckBox cbSmooth;
  private SharedPreferences prefs;

  private int defaultColor = R.color.iceblue;

  /**
   * How much of the history the graph draws. A stretch of time, or — for the one that mirrors the
   * history screen's trend line — a count of solves.
   *
   * <p>Order matters: these line up with {@code @array/graph_periods}, and the choice is
   * remembered by position. New entries go on the end, or every user's saved period shifts.
   */
  enum Period {
    DAY(1),
    WEEK(7),
    MONTH(31),
    YEAR(365),
    ALL(0),
    /**
     * The window the history screen draws its trend over, with the detail a graph can add. The
     * count is named in {@code @string/graph_period_last_50}, so the two move together.
     */
    LAST_SOLVES(0, MainScreenActivity.TREND_SIZE);

    private final int days;
    private final int solves; // 0 for a period measured in days

    Period(int days) {
      this(days, 0);
    }

    Period(int days, int solves) {
      this.days = days;
      this.solves = solves;
    }

    /** Whether this period counts solves rather than days. */
    private boolean isBySolves() {
      return solves > 0;
    }

    private int getSolves() {
      return solves;
    }

    private long getPeriodStart() {
      if (days == 0) {
        return 0;
      } else if (this == DAY) {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DATE);
        calendar.set(year, month, day, 0, 0, 0);
        return calendar.getTimeInMillis();
      }
      return System.currentTimeMillis() - (((long) days) * 24 * 60 * 60 * 1000);
    }
  }

  enum GraphType {
    PROGRESSION {
      @Override
      public String formatValue(float value) {
        return FormatterService.INSTANCE.formatSolveTime(Math.round((double) value));
      }
    },
    FREQUENCY {
      @Override
      public String formatValue(float value) {
        return FormatterService.INSTANCE.formatFloat(value, 2);
      }
//    DEVIATION {
//      @Override
//      public String formatValue(float value) {
//        return FormatterService.INSTANCE.formatSolveTime(Math.round((double) value));
//      }
    };

    public abstract String formatValue(float value);
  }

  ValueFormatter yValueFormatter = new ValueFormatter() {
    @Override
    public String getFormattedValue(float value) {
      return getSelectedGraphType().formatValue(value);
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.graph_screen);

    cubeType = (CubeType) getIntent().getSerializableExtra("cubeType");
    solveType = (SolveType) getIntent().getSerializableExtra("solveType");

    ((TextView) findViewById(R.id.tvCubeType)).setText(cubeType.getName());
    String solveTypeName = Utils.toSolveTypeLocalizedName(this, solveType.getName());
    ((TextView) findViewById(R.id.tvSolveType)).setText(solveTypeName);

    prefs = getSharedPreferences("graph", 0);

    cbSmooth = (CheckBox) findViewById(R.id.cbSmooth);
    cbSmooth.setChecked(prefs.getBoolean("smooth", false));
    cbSmooth.setOnCheckedChangeListener(new OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        Editor editor = prefs.edit();
        editor.putBoolean("smooth", b);
        editor.apply();
        if (chartData != null) {
          getData();
        }
      }
    });

    spPeriod = (Spinner) findViewById(R.id.spPeriod);
    configureSpinner(spPeriod, R.array.graph_periods, "period");
    // Opened on a period of its own (the history screen's trend leads here), rather than on the
    // one last picked. It is then remembered like any other choice.
    Period requested = (Period) getIntent().getSerializableExtra("period");
    if (requested != null) {
      spPeriod.setSelection(requested.ordinal());
    }
    spGraphType = (Spinner) findViewById(R.id.spGraphType);
    configureSpinner(spGraphType, R.array.graph_types, "graph_type");

    chart = (LineChart) findViewById(R.id.chart);
    chart.getDescription().setEnabled(false);
    chart.getLegend().setEnabled(true);
    chart.setBackgroundColor(getResourceColor(R.color.mainscreen_top_card)); // blend into the surface card (G3)
    chart.setDrawGridBackground(false);
    chart.setNoDataText("");
    chart.setExtraTopOffset(5f); // fix X-labels being cut at the top

    // G4: styled empty state — muted, slightly larger "no data" text instead of bare default.
    chart.setNoDataTextColor(getResourceColor(R.color.secondary_text));
    Paint noDataPaint = chart.getPaint(Chart.PAINT_INFO);
    if (noDataPaint != null) {
      noDataPaint.setTextSize(TypedValue.applyDimension(
         TypedValue.COMPLEX_UNIT_SP, 16, getResources().getDisplayMetrics()));
    }

    ValueFormatter xValueFormatter = new ValueFormatter() {
      @Override
      public String getFormattedValue(float value) {
        int i = (int) value;
        if (i >= 0 && i < pointTimestamps.size()) {
          long timestamp = pointTimestamps.get(i);
          if (chart.getVisibleXRange() < 20) { // when zoomed in, show more details
            return FormatterService.INSTANCE.formatDate(timestamp);
          } else {
            return FormatterService.INSTANCE.formatMonthYear(timestamp);
          }
        }
        return "";
      }
    };

    // G3: soften axis presentation — drop hard axis lines, keep only a faint Y grid.
    XAxis xAxis = chart.getXAxis();
    xAxis.setPosition(XAxis.XAxisPosition.TOP);
    xAxis.setSpaceMin(1);
    xAxis.setTextColor(getResourceColor(R.color.white));
    xAxis.setTextSize(12);
    xAxis.setDrawAxisLine(false);
    xAxis.setDrawGridLines(false);
    xAxis.setValueFormatter(xValueFormatter);

    YAxis yAxis = chart.getAxisLeft();
    yAxis.setTextColor(getResourceColor(R.color.white));
    yAxis.setTextSize(12);
    yAxis.setDrawAxisLine(false);
    yAxis.setGridColor(getResourceColor(R.color.gray600));
    yAxis.setGridLineWidth(0.5f);
    yAxis.setValueFormatter(yValueFormatter);

    chart.getAxisRight().setEnabled(false);
  }

  private Spinner configureSpinner(Spinner spinner, int dataArray, final String prefsKey) {
    ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, dataArray, R.layout.spinner_item);
    adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
    spinner.setAdapter(adapter);
    spinner.setSelection(prefs.getInt(prefsKey, 0));
    spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        Editor editor = prefs.edit();
        editor.putInt(prefsKey, pos);
        editor.apply();
        getData();
      }

      @Override
      public void onNothingSelected(AdapterView<?> adapterView) {
      }
    });
    return spinner;
  }

  private void getData() {
    GraphType selectedGraphType = getSelectedGraphType();
    if (selectedGraphType == GraphType.PROGRESSION) {
      getProgressionData();
    } else if (selectedGraphType == GraphType.FREQUENCY) {
      getFrequencyData();
    }
  }

  private void getProgressionData() {
    Period period = getSelectedPeriod();
    DataCallback<SolveHistory> callback = new DataCallback<SolveHistory>() {
      @Override
      public void onData(final SolveHistory data) {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            chartData.clear();

            List<ChartData> timesLineData = parseData(getChartTimesFromSolveHistory(data));
            ChartLineData chartLineData = new ChartLineData(timesLineData, getString(R.string.times), defaultColor);
            chartLineData.setxOffset(0);
            chartLineData.setLineWidth(2.5f);
            chartLineData.setCircleSize(4f);
            chartData.add(chartLineData);

            int average = 5;
            List<ChartData> averageLineData = getAverageOf(timesLineData, average);
            chartLineData = new ChartLineData(averageLineData, getString(R.string.ao5), R.color.green);
            chartLineData.setxOffset(average - 1);
            chartLineData.setLineWidth(1f);
            chartLineData.setCircleSize(2f);
            chartData.add(chartLineData);

            average = 12;
            averageLineData = getAverageOf(timesLineData, average);
            chartLineData = new ChartLineData(averageLineData, getString(R.string.ao12), R.color.darkred);
            chartLineData.setxOffset(average - 1);
            chartLineData.setLineWidth(1f);
            chartLineData.setCircleSize(2f);
            chartData.add(chartLineData);

            refreshData();
          }
        });
      }
    };
    if (period.isBySolves()) {
      App.INSTANCE.getService().getLastSolves(solveType, period.getSolves(), callback);
    } else {
      App.INSTANCE.getService().getHistory(solveType, period.getPeriodStart(), callback);
    }
  }

  // A count of solves says nothing about how many were done on a day, so the frequency graph reads
  // a by-solves period as the whole history, which is what its zero period start already means.
  private void getFrequencyData() {
    App.INSTANCE.getService().getFrequencyData(solveType, getSelectedPeriod().getPeriodStart(), new DataCallback<List<FrequencyData>>() {
      @Override
      public void onData(final List<FrequencyData> frequencyData) {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            chartData.clear();
            chartData.add(new ChartLineData(parseData(getChartDataFromFrequency(frequencyData)), getString(R.string.chart_type_frequency), defaultColor));
            refreshData();
          }
        });
      }
    });
  }

  private List<ChartData> parseData(List<ChartData> chartData) {
    List<ChartData> data;
    if (cbSmooth.isChecked()) {
      data = ChartUtils.getSmoothedChartTimes(chartData);
    } else {
      data = chartData;
    }
    return data;
  }

  private void refreshData() {
    chart.setNoDataText(getString(R.string.no_data_found)); // done here to avoid displaying that message when data is loading
    chart.clear();

    if (chartData.isEmpty()) {
      return;
    }

    ArrayList<ILineDataSet> dataSets = new ArrayList<ILineDataSet>();
    pointTimestamps.clear();

    for (ChartLineData chartLineData : chartData) {
      List<ChartData> data = chartLineData.getData();

      ArrayList<Entry> times = new ArrayList<Entry>();
      for (ChartData solveTime : data) {
        pointTimestamps.add(solveTime.getTimestamp());
        times.add(new Entry(times.size() + chartLineData.getxOffset(), solveTime.getData()));
      }

      LineDataSet dataSet = new LineDataSet(times, chartLineData.getLabel());
      dataSet.setColor(getResourceColor(chartLineData.getColor()));
      dataSet.setLineWidth(chartLineData.getLineWidth());
      dataSet.setCircleColor(getResourceColor(chartLineData.getColor()));
      dataSet.setCircleRadius(chartLineData.getCircleSize());
      dataSet.setHighlightEnabled(false);
      dataSet.setValueFormatter(yValueFormatter);
      dataSet.setDrawValues(false);

      dataSets.add(dataSet);
    }

    LineData chartData = new LineData(dataSets);
    chart.setData(chartData);
    chart.invalidate();

    chart.getLegend().setTextColor(Color.WHITE);
  }

  private int getResourceColor(int colorRes) {
    return ContextCompat.getColor(this, colorRes);
  }

  private Period getSelectedPeriod() {
    int pos = spPeriod.getSelectedItemPosition();
    Period[] periods = Period.values();
    if (pos >= 0 && pos < periods.length) {
      return periods[pos];
    } else {
      return Period.DAY;
    }
  }

  private GraphType getSelectedGraphType() {
    int pos = spGraphType.getSelectedItemPosition();
    GraphType[] graphTypes = GraphType.values();
    if (pos >= 0 && pos < graphTypes.length) {
      return graphTypes[pos];
    } else {
      return GraphType.PROGRESSION;
    }
  }

  private List<ChartData> getChartTimesFromSolveHistory(SolveHistory solveHistory) {
    List<ChartData> chartTimes = new ArrayList<ChartData>();
    for (int i = solveHistory.getSolveTimes().size() - 1; i >= 0; i--) {
      SolveTime solveTime = solveHistory.getSolveTimes().get(i);
      if (solveTime.getTime() > 0) {
        chartTimes.add(new ChartData(solveTime.getTime(), solveTime.getTimestamp()));
      }
    }
    return chartTimes;
  }

  private List<ChartData> getAverageOf(List<ChartData> chartDataList, int n) {
    List<ChartData> averages = new ArrayList<ChartData>();
    List<Long> averageTimes = new ArrayList<>();
    for (int i = 0; i < chartDataList.size(); i++) {
      if (averageTimes.size() >= n) {
        averageTimes.remove(0);
      }

      ChartData chartData = chartDataList.get(i);
      averageTimes.add((long) chartData.getData());

      if (averageTimes.size() >= n) {
        TimesStatistics timesStatistics = new TimesStatistics(averageTimes);
        long avg = timesStatistics.getAverageOf(n);
        if (avg > 0) {
          averages.add(new ChartData(avg, chartData.getTimestamp()));
        }
      }
    }
    return averages;
  }

  private List<ChartData> getChartDataFromFrequency(List<FrequencyData> frequencyData) {
    List<ChartData> chartData = new ArrayList<ChartData>();
    for (FrequencyData curFrequencyData : frequencyData) {
      chartData.add(new ChartData(curFrequencyData.getSolvesCount(), curFrequencyData.getDay()));
    }
    return chartData;
  }

}
