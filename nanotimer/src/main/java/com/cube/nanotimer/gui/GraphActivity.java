package com.cube.nanotimer.gui;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
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
import com.cube.nanotimer.util.view.ViewSegments;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.FrequencyData;
import com.cube.nanotimer.vo.SolveHistory;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveType;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.Legend.LegendForm;
import com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment;
import com.github.mikephil.charting.components.Legend.LegendVerticalAlignment;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GraphActivity extends NanoTimerActivity {

  private static final long DAY_MS = 24 * 60 * 60 * 1000L;
  private static final long HALF_YEAR_MS = 182 * DAY_MS;
  /** What an X label says, from the finest window it is worth saying it on to the coarsest. */
  private static final String CLOCK_PATTERN = "HH:mm";
  private static final String DAY_AND_CLOCK_PATTERN = "MMM d \u00b7 HH:mm";
  private static final String DAY_PATTERN = "MMM d";
  private static final String MONTH_PATTERN = "MMM, yyyy";
  /** Past this many points a circle apiece is a band rather than a set of solves. */
  private static final int MAX_POINTS_WITH_CIRCLES = 80;

  private CubeType cubeType;
  private SolveType solveType;
  private List<ChartLineData> chartData = new ArrayList<>();
  private List<Long> pointTimestamps = new ArrayList<>();
  private List<String> bucketLabels = new ArrayList<>();

  private LineChart chart;
  private BarChart barChart;
  private Spinner spPeriod;
  private List<TextView> graphTypeCells = new ArrayList<>();
  private int graphTypePos;
  private TextView buSmooth;
  private boolean smooth;
  private SharedPreferences prefs;
  private final SimpleDateFormat axisDateFormat = new SimpleDateFormat(CLOCK_PATTERN, Locale.ENGLISH);

  private int defaultColor = R.color.graph_series;

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
      public String formatAxisValue(float value, float axisRange) {
        return formatAxisTime(Math.round(value), axisRange);
      }
    },
    /** Solves counted, one day at a time. */
    FREQUENCY {
      @Override
      public String formatAxisValue(float value, float axisRange) {
        return String.valueOf(Math.round(value));
      }
    },
    /** Its values are counts of solves, not times. */
    DISTRIBUTION {
      @Override
      public String formatAxisValue(float value, float axisRange) {
        return String.valueOf(Math.round(value));
      }
    };

    public abstract String formatAxisValue(float value, float axisRange);

    /**
     * A time as a gridline is labelled: to the coarsest precision the axis still separates. A solve
     * is read to the millisecond, but a gridline is not.
     */
    private static String formatAxisTime(long ms, float axisRange) {
      if (ms >= 60000) {
        long seconds = Math.round(ms / 1000d);
        return seconds / 60 + ":" + String.format(Locale.US, "%02d", seconds % 60);
      }
      return String.format(Locale.US, axisRange >= 5000 ? "%.1f" : "%.2f", ms / 1000d);
    }
  }

  /** Gridlines only: no value is drawn on a point, so this rules the Y axis and nothing else. */
  ValueFormatter yValueFormatter = new ValueFormatter() {
    @Override
    public String getFormattedValue(float value) {
      YAxis axis = chart.getAxisLeft();
      return getSelectedGraphType().formatAxisValue(value, axis.getAxisMaximum() - axis.getAxisMinimum());
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

    buSmooth = (TextView) findViewById(R.id.buSmooth);
    smooth = prefs.getBoolean("smooth", false);
    ViewSegments.style(buSmooth, smooth);
    buSmooth.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        smooth = !smooth;
        Editor editor = prefs.edit();
        editor.putBoolean("smooth", smooth);
        editor.apply();
        ViewSegments.style(buSmooth, smooth);
        getData();
      }
    });

    chart = (LineChart) findViewById(R.id.chart);
    chart.getDescription().setEnabled(false);
    chart.setBackgroundColor(getResourceColor(R.color.hero_card)); // blend into the surface card
    chart.setDrawGridBackground(true); // the plot, recessed a shade into that card
    chart.setGridBackgroundColor(getResourceColor(R.color.graph_plot));
    chart.setNoDataText("");
    chart.setExtraBottomOffset(4f); // keep the X labels clear of the card's rounded edge
    chart.setExtraRightOffset(6f); // and the last of them off its corner

    // Muted, slightly larger "no data" text instead of the bare default.
    chart.setNoDataTextColor(getResourceColor(R.color.secondary_text));
    Paint noDataPaint = chart.getPaint(Chart.PAINT_INFO);
    if (noDataPaint != null) {
      noDataPaint.setTextSize(TypedValue.applyDimension(
         TypedValue.COMPLEX_UNIT_SP, 16, getResources().getDisplayMetrics()));
    }

    ValueFormatter xValueFormatter = new ValueFormatter() {
      @Override
      public String getFormattedValue(float value) {
        int i = Math.round(value);
        return (i >= 0 && i < pointTimestamps.size()) ? formatAxisDate(pointTimestamps.get(i)) : "";
      }
    };

    // Soften axis presentation: no hard axis lines, only a faint Y grid over the plot.
    XAxis xAxis = chart.getXAxis();
    xAxis.setPosition(XAxis.XAxisPosition.BOTTOM); // time runs along the foot of the plot, as on the bars
    xAxis.setSpaceMin(1);
    xAxis.setTextColor(getResourceColor(R.color.secondary_text));
    xAxis.setTextSize(11);
    xAxis.setDrawAxisLine(false);
    xAxis.setDrawGridLines(false);
    xAxis.setAvoidFirstLastClipping(true);
    xAxis.setValueFormatter(xValueFormatter);

    YAxis yAxis = chart.getAxisLeft();
    yAxis.setTextColor(getResourceColor(R.color.secondary_text));
    yAxis.setTextSize(11);
    yAxis.setDrawAxisLine(false);
    yAxis.setGridColor(getResourceColor(R.color.graph_grid));
    yAxis.setGridLineWidth(0.5f);
    yAxis.setValueFormatter(yValueFormatter);

    chart.getAxisRight().setEnabled(false);

    setUpLegend(chart.getLegend());

    barChart = (BarChart) findViewById(R.id.barChart);
    setupBarChart();

    findViewById(R.id.buGraphHelp).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        DialogUtils.showFragment(GraphActivity.this, new GraphHelpDialog());
      }
    });

    setUpGraphTypes();

    // Last: picking a period loads the data, which needs both charts and the graph type to be set.
    spPeriod = (Spinner) findViewById(R.id.spPeriod);
    configureSpinner(spPeriod, R.array.graph_periods, "period");
    // Opened on a period of its own (the history screen's trend leads here), rather than on the
    // one last picked. It is then remembered like any other choice.
    Period requested = (Period) getIntent().getSerializableExtra("period");
    if (requested != null) {
      spPeriod.setSelection(requested.ordinal());
    }
  }

  /** The three graphs as a segmented control: the choice the screen is mostly about. */
  private void setUpGraphTypes() {
    graphTypeCells.add((TextView) findViewById(R.id.buGraphProgression));
    graphTypeCells.add((TextView) findViewById(R.id.buGraphFrequency));
    graphTypeCells.add((TextView) findViewById(R.id.buGraphDistribution));
    for (int i = 0; i < graphTypeCells.size(); i++) {
      final int index = i;
      graphTypeCells.get(i).setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          if (graphTypePos != index) {
            setGraphType(index);
            rememberGraphType();
            getData();
          }
        }
      });
    }
    setGraphType(prefs.getInt("graph_type", 0));
  }

  private void setGraphType(int pos) {
    graphTypePos = pos;
    for (int i = 0; i < graphTypeCells.size(); i++) {
      ViewSegments.style(graphTypeCells.get(i), i == pos);
    }
  }

  private void rememberGraphType() {
    Editor editor = prefs.edit();
    editor.putInt("graph_type", graphTypePos);
    editor.apply();
  }

  /** Names sit beside the plot rather than in it, and only where there is more than one line. */
  private void setUpLegend(Legend legend) {
    legend.setTextColor(getResourceColor(R.color.secondary_text));
    legend.setTextSize(11f);
    legend.setForm(LegendForm.LINE);
    legend.setFormSize(11f);
    legend.setFormLineWidth(2.5f);
    legend.setXEntrySpace(14f);
    legend.setVerticalAlignment(LegendVerticalAlignment.TOP);
    legend.setHorizontalAlignment(LegendHorizontalAlignment.RIGHT);
    legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
    legend.setDrawInside(false);
  }

  private String formatAxisDate(long timestamp) {
    axisDateFormat.applyPattern(axisDatePattern());
    return axisDateFormat.format(new Date(timestamp));
  }

  /**
   * What the labels say, taken from the stretch of time on screen: the clock within a day, the day
   * up to half a year, the month past that. The axis runs on solves rather than on time, so two
   * evenly spaced labels can land in the same day and say so twice; that is a day the user spent a
   * lot of solves in, and naming it twice is honest. Naming the month six times was not, and the
   * ladder cannot do it: a month label only appears where the labels are a month or more apart.
   */
  private String axisDatePattern() {
    long span = visibleSpanMs();
    if (span < DAY_MS / 2) {
      return CLOCK_PATTERN; // inside one day, where the date on every label says nothing
    } else if (span < 2 * DAY_MS) {
      return DAY_AND_CLOCK_PATTERN; // a day is crossed, and the clock still separates the labels
    } else if (span < HALF_YEAR_MS) {
      return DAY_PATTERN;
    }
    return MONTH_PATTERN;
  }

  /** The stretch of time the chart is showing, zoom included. */
  private long visibleSpanMs() {
    if (pointTimestamps.size() < 2) {
      return 0;
    }
    int first = clampToPoints(chart.getLowestVisibleX());
    int last = clampToPoints(chart.getHighestVisibleX());
    return Math.abs(pointTimestamps.get(last) - pointTimestamps.get(first));
  }

  private int clampToPoints(float x) {
    return Math.max(0, Math.min(pointTimestamps.size() - 1, Math.round(x)));
  }

  /** The distribution graph's chart, styled to match the line one. */
  private void setupBarChart() {
    barChart.getDescription().setEnabled(false);
    barChart.getLegend().setEnabled(false); // a single series, already named by the graph type
    barChart.setBackgroundColor(getResourceColor(R.color.hero_card));
    barChart.setDrawGridBackground(true);
    barChart.setGridBackgroundColor(getResourceColor(R.color.graph_plot));
    barChart.setNoDataText("");
    barChart.setNoDataTextColor(getResourceColor(R.color.secondary_text));
    barChart.setExtraTopOffset(5f); // room for the count written over the tallest bar
    barChart.setExtraBottomOffset(4f);
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
    xAxis.setTextColor(getResourceColor(R.color.secondary_text));
    xAxis.setTextSize(11);
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
    yAxis.setTextColor(getResourceColor(R.color.secondary_text));
    yAxis.setTextSize(11);
    yAxis.setDrawAxisLine(false);
    yAxis.setGridColor(getResourceColor(R.color.graph_grid));
    yAxis.setGridLineWidth(0.5f);
    yAxis.setAxisMinimum(0f);
    yAxis.setGranularity(1f); // counts are whole solves
    yAxis.setValueFormatter(yValueFormatter);

    barChart.getAxisRight().setEnabled(false);
  }

  private Spinner configureSpinner(final Spinner spinner, int dataArray, final String prefsKey) {
    // The list a pill drops marks the value the pill is showing: without it, the one you are on is
    // the only row the list does not tell you about.
    ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(this,
        R.layout.pill_spinner_item, getResources().getTextArray(dataArray)) {
      @Override
      public View getDropDownView(int position, View convertView, ViewGroup parent) {
        TextView row = (TextView) super.getDropDownView(position, convertView, parent);
        boolean current = (position == spinner.getSelectedItemPosition());
        row.setTextColor(getResourceColor(current ? R.color.lightblue : R.color.secondary_text));
        return row;
      }
    };
    adapter.setDropDownViewResource(R.layout.pill_spinner_dropdown_item);
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
    buSmooth.setEnabled(!bars); // there is nothing to smooth in a histogram
    buSmooth.setAlpha(bars ? 0.4f : 1f);

    // Solves counted are whole and start at none; times are neither.
    YAxis yAxis = chart.getAxisLeft();
    boolean counts = (selectedGraphType == GraphType.FREQUENCY);
    yAxis.setGranularity(1f);
    yAxis.setGranularityEnabled(counts);
    if (counts) {
      yAxis.setAxisMinimum(0f);
    } else {
      yAxis.resetAxisMinimum();
    }

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
            chartLineData = new ChartLineData(averageLineData, getString(R.string.ao5), R.color.graph_ao5);
            chartLineData.setxOffset(average - 1);
            chartLineData.setLineWidth(1f);
            chartLineData.setCircleSize(2f);
            chartData.add(chartLineData);

            average = 12;
            averageLineData = getAverageOf(timesLineData, average);
            chartLineData = new ChartLineData(averageLineData, getString(R.string.ao12), R.color.graph_ao12);
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
    if (smooth) {
      data = ChartUtils.getSmoothedChartTimes(chartData);
    } else {
      data = chartData;
    }
    return data;
  }

  private void refreshData() {
    chart.setNoDataText(getString(R.string.no_data_found)); // done here to avoid displaying that message when data is loading
    chart.clear();
    pointTimestamps.clear();

    ArrayList<ILineDataSet> dataSets = new ArrayList<ILineDataSet>();
    for (ChartLineData chartLineData : chartData) {
      List<ChartData> data = chartLineData.getData();
      if (data.isEmpty()) { // an average of fewer solves than it averages has no line
        continue;
      }

      ArrayList<Entry> times = new ArrayList<Entry>();
      for (ChartData solveTime : data) {
        times.add(new Entry(times.size() + chartLineData.getxOffset(), solveTime.getData()));
      }
      if (pointTimestamps.isEmpty()) {
        // The X axis is indexed by the first series; the averages hang off its points, offset.
        for (ChartData solveTime : data) {
          pointTimestamps.add(solveTime.getTimestamp());
        }
      }

      LineDataSet dataSet = new LineDataSet(times, chartLineData.getLabel());
      dataSet.setColor(getResourceColor(chartLineData.getColor()));
      dataSet.setLineWidth(chartLineData.getLineWidth());
      dataSet.setCircleColor(getResourceColor(chartLineData.getColor()));
      dataSet.setCircleRadius(chartLineData.getCircleSize());
      dataSet.setDrawCircles(times.size() <= MAX_POINTS_WITH_CIRCLES);
      dataSet.setHighlightEnabled(false);
      dataSet.setValueFormatter(yValueFormatter);
      dataSet.setDrawValues(false);

      dataSets.add(dataSet);
    }

    // A period with no solves still carries its three empty series, so it is the points that say
    // whether there is anything to draw, never the count of series.
    if (dataSets.isEmpty()) {
      return;
    }
    chart.getLegend().setEnabled(dataSets.size() > 1); // a lone line is already named by its picker
    chart.setData(new LineData(dataSets));
    chart.invalidate();
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
    GraphType[] graphTypes = GraphType.values();
    if (graphTypePos >= 0 && graphTypePos < graphTypes.length) {
      return graphTypes[graphTypePos];
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
