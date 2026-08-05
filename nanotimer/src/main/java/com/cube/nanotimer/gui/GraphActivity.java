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
import com.cube.nanotimer.gui.widget.GraphHelpDialog;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.session.TimesStatistics;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.chart.ChartData;
import com.cube.nanotimer.util.chart.ChartLineData;
import com.cube.nanotimer.util.chart.ChartUtils;
import com.cube.nanotimer.util.chart.TimeDistribution;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.FrequencyData;
import com.cube.nanotimer.vo.SolveHistory;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveType;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
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
  private List<String> bucketLabels = new ArrayList<>();

  private LineChart chart;
  private BarChart barChart;
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

  /**
   * What the graph draws. Like {@link Period}, these line up with {@code @array/graph_types} and the
   * choice is remembered by position, so new entries go on the end.
   */
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
    },
    /** Its values are counts of solves, not times. */
    DISTRIBUTION {
      @Override
      public String formatValue(float value) {
        return String.valueOf(Math.round(value));
      }
    };

    public abstract String formatValue(float value);
  }

  ValueFormatter yValueFormatter = new ValueFormatter() {
    @Override
    public String getFormattedValue(float value) {
      return getSelectedGraphType().formatValue(value);
    }
  };

  /** Counts written over the bars, empty buckets left bare rather than labelled with a zero. */
  ValueFormatter barValueFormatter = new ValueFormatter() {
    @Override
    public String getFormattedValue(float value) {
      return value <= 0 ? "" : String.valueOf(Math.round(value));
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

    barChart = (BarChart) findViewById(R.id.barChart);
    setupBarChart();

    findViewById(R.id.buGraphHelp).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        DialogUtils.showFragment(GraphActivity.this, new GraphHelpDialog());
      }
    });

    // Last: picking a spinner value loads the data, which needs both charts to already be there.
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
  }

  /** The distribution graph's chart, styled to match the line one. */
  private void setupBarChart() {
    barChart.getDescription().setEnabled(false);
    barChart.getLegend().setEnabled(false); // a single series, already named by the graph type
    barChart.setBackgroundColor(getResourceColor(R.color.mainscreen_top_card));
    barChart.setDrawGridBackground(false);
    barChart.setNoDataText("");
    barChart.setNoDataTextColor(getResourceColor(R.color.secondary_text));
    barChart.setExtraTopOffset(5f);
    barChart.setScaleEnabled(false); // every bucket already fits on screen
    barChart.setPinchZoom(false);
    barChart.setDoubleTapToZoomEnabled(false);

    Paint noDataPaint = barChart.getPaint(Chart.PAINT_INFO);
    if (noDataPaint != null) {
      noDataPaint.setTextSize(TypedValue.applyDimension(
         TypedValue.COMPLEX_UNIT_SP, 16, getResources().getDisplayMetrics()));
    }

    XAxis xAxis = barChart.getXAxis();
    xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
    xAxis.setTextColor(getResourceColor(R.color.white));
    xAxis.setTextSize(12);
    xAxis.setDrawAxisLine(false);
    xAxis.setDrawGridLines(false);
    xAxis.setGranularity(1f); // one label per bucket at most, never a repeated bound
    xAxis.setValueFormatter(new ValueFormatter() {
      @Override
      public String getFormattedValue(float value) {
        int i = Math.round(value);
        return (i >= 0 && i < bucketLabels.size()) ? bucketLabels.get(i) : "";
      }
    });

    YAxis yAxis = barChart.getAxisLeft();
    yAxis.setTextColor(getResourceColor(R.color.white));
    yAxis.setTextSize(12);
    yAxis.setDrawAxisLine(false);
    yAxis.setGridColor(getResourceColor(R.color.gray600));
    yAxis.setGridLineWidth(0.5f);
    yAxis.setAxisMinimum(0f);
    yAxis.setGranularity(1f); // counts are whole solves
    yAxis.setValueFormatter(yValueFormatter);

    barChart.getAxisRight().setEnabled(false);
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

    boolean bars = (selectedGraphType == GraphType.DISTRIBUTION);
    chart.setVisibility(bars ? View.GONE : View.VISIBLE);
    barChart.setVisibility(bars ? View.VISIBLE : View.GONE);
    cbSmooth.setEnabled(!bars); // there is nothing to smooth in a histogram
    cbSmooth.setAlpha(bars ? 0.4f : 1f);

    if (selectedGraphType == GraphType.PROGRESSION) {
      getProgressionData();
    } else if (selectedGraphType == GraphType.FREQUENCY) {
      getFrequencyData();
    } else if (selectedGraphType == GraphType.DISTRIBUTION) {
      getDistributionData();
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

  // Reads the same solves the progression graph does, a count of solves included: how many solves
  // landed in a bucket is a fair question of the last 50 as much as of a stretch of time.
  private void getDistributionData() {
    Period period = getSelectedPeriod();
    DataCallback<SolveHistory> callback = new DataCallback<SolveHistory>() {
      @Override
      public void onData(final SolveHistory data) {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            List<Long> times = new ArrayList<>();
            for (SolveTime solveTime : data.getSolveTimes()) {
              times.add(solveTime.getTime()); // DNFs carry -1 and are dropped by the bucketing
            }
            refreshDistributionData(TimeDistribution.of(times));
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

  private void refreshDistributionData(TimeDistribution distribution) {
    barChart.setNoDataText(getString(R.string.no_data_found));
    barChart.clear();
    bucketLabels.clear();

    if (distribution.getBuckets().isEmpty()) {
      return;
    }

    List<BarEntry> entries = new ArrayList<>();
    for (TimeDistribution.Bucket bucket : distribution.getBuckets()) {
      entries.add(new BarEntry(entries.size(), bucket.getCount()));
      bucketLabels.add(bucket.getLabel());
    }

    BarDataSet dataSet = new BarDataSet(entries, getString(R.string.solves));
    dataSet.setColor(getResourceColor(defaultColor));
    dataSet.setHighlightEnabled(false);
    dataSet.setValueTextColor(getResourceColor(R.color.white));
    dataSet.setValueTextSize(10f);
    dataSet.setValueFormatter(barValueFormatter);

    BarData barData = new BarData(dataSet);
    barData.setBarWidth(0.9f);
    barChart.setData(barData);
    barChart.setFitBars(true); // keep the first and last bars whole
    barChart.invalidate();
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
