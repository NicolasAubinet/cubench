package com.cube.nanotimer.util;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.ReplacementSpan;
import androidx.core.content.ContextCompat;
import com.cube.nanotimer.App;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.vo.SolveTime;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public enum FormatterService {
  INSTANCE;

  private static final String EXPORT_DATE_FORMAT = "MMM d yyyy - HH:mm:ss";

  private static final long MINUTE_MS = 60000;
  private static final long HOUR_MS = 60 * MINUTE_MS;

  /** How large a solve's mark is drawn against the time it qualifies. Quieter than the time, and
   * quieter still behind a DNF: the +2 changes what the time means, while the time behind a DNF is
   * only there to be looked up. */
  private static final float PLUS_TWO_SIZE = 0.4f;
  private static final float DNF_TIME_SIZE = 0.3f;

  /** The same mark in a list, where the time is small to begin with and half of it is illegible. */
  private static final float ROW_MARK_SIZE = 0.6f;

  /** Stands in for the space the counterweight holds, and is never drawn itself. */
  private static final String COUNTERWEIGHT = "\u200B";

  public String formatSolveTime(Long solveTime) {
    return formatSolveTime(solveTime, null);
  }

  /**
   * A solve as it should be read, carrying what was done to it: a +2 says so beside the time it is
   * already part of, and a DNF is followed by the time it replaced.
   *
   * <p>Marked wherever a whole solve is shown, because the app hands these out by itself when a
   * smart cube reads the stop, and a penalty nobody was told about is one nobody can take back. The
   * time behind a DNF is shown for the same reason it is kept: the solve happened, and its owner
   * gets to see what it was.
   */
  public String formatSolveTime(SolveTime solveTime) {
    return solveTimeText(solveTime) + solveTimeMark(solveTime);
  }

  /**
   * The same, with the mark drawn smaller: it is a note on the time rather than part of it, and the
   * time is what the screen is read for. For a time given a whole screen or a whole dialog, where
   * the mark hangs off the right of the time rather than moving it: the time is centred where it
   * would be with nothing after it, over the plinth and under the heading.
   */
  public CharSequence formatMarkedSolveTime(SolveTime solveTime) {
    String mark = solveTimeMark(solveTime);
    float size = solveTime != null && solveTime.isDNF() ? DNF_TIME_SIZE : PLUS_TWO_SIZE;
    return marked(solveTimeText(solveTime), mark, size, true);
  }

  /**
   * A solve in a list of them, marked only where the mark changes the time: a +2 is part of the
   * figure on the row and says why it reads high, while the time behind a DNF is another figure
   * again, and a column of times is no place to be told two of them. That one is for the solve's
   * own screen, where there is room to say which is which.
   */
  public CharSequence formatRowSolveTime(SolveTime solveTime) {
    String mark = solveTime != null && !solveTime.isDNF() && solveTime.isPlusTwo() ? plusTwoMark() : "";
    return marked(solveTimeText(solveTime), mark, ROW_MARK_SIZE, false);
  }

  /**
   * The time and its mark, the mark drawn small and quiet. Centred, the mark is balanced by empty
   * space of its own width before the time, so that what centres is the time.
   */
  private CharSequence marked(String text, String mark, float size, boolean centred) {
    if (mark.isEmpty()) {
      return text;
    }
    SpannableString out = new SpannableString((centred ? COUNTERWEIGHT : "") + text + mark);
    int markStart = out.length() - mark.length();
    out.setSpan(new RelativeSizeSpan(size), markStart, out.length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    out.setSpan(new ForegroundColorSpan(markColor()), markStart, out.length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    if (centred) {
      out.setSpan(new CounterweightSpan(mark, size), 0, COUNTERWEIGHT.length(),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    return out;
  }

  /** Empty space as wide as the mark it answers, drawing nothing. */
  private static class CounterweightSpan extends ReplacementSpan {
    private final String mark;
    private final float size;

    CounterweightSpan(String mark, float size) {
      this.mark = mark;
      this.size = size;
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
      if (fm != null) {
        paint.getFontMetricsInt(fm);
      }
      float full = paint.getTextSize();
      paint.setTextSize(full * size);
      int width = Math.round(paint.measureText(mark));
      paint.setTextSize(full);
      return width;
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y,
        int bottom, Paint paint) {
      // Space, and nothing in it.
    }
  }

  private int markColor() {
    return ContextCompat.getColor(App.INSTANCE.getContext(), R.color.solve_mark);
  }

  private String solveTimeText(SolveTime solveTime) {
    if (solveTime == null) {
      return formatSolveTime((Long) null);
    }
    return solveTime.isDNF() ? App.INSTANCE.getContext().getString(R.string.DNF)
        : formatSolveTime(solveTime.getTime());
  }

  /** What was done to the solve, said after it: the +2 it carries, or the time its DNF replaced. */
  private String solveTimeMark(SolveTime solveTime) {
    if (solveTime == null) {
      return "";
    }
    if (solveTime.isDNF()) {
      Long before = solveTime.getTimeBeforeDnf();
      return before == null ? "" : " (" + formatSolveTime(before) + ")";
    }
    return solveTime.isPlusTwo() ? plusTwoMark() : "";
  }

  private String plusTwoMark() {
    return " (" + App.INSTANCE.getContext().getString(R.string.plus_two) + ")";
  }

  /** Turns per second, to one decimal — the precision the figure is worth. */
  public String formatTps(double tps) {
    return String.format(Locale.getDefault(), "%.1f", tps);
  }

  public String formatSolveTime(Long solveTime, String defaultValue) {
    return formatSolveTime(solveTime, defaultValue, Options.INSTANCE.isUsingHighPrecisionTimer());
  }

  public String formatSolveTime(Long solveTime, String defaultValue, boolean parUseHighPrecision) {
    if (solveTime == null) {
      return defaultValue == null ? App.INSTANCE.getContext().getString(R.string.NA) : defaultValue;
    }
    if (solveTime == -1) {
      return App.INSTANCE.getContext().getString(R.string.DNF);
    }
    if (solveTime == -2) {
      return App.INSTANCE.getContext().getString(R.string.NA);
    }

    if (!parUseHighPrecision) {
      solveTime = (long) Math.round(solveTime / 10f) * 10;
    }

    StringBuilder sb = new StringBuilder();
    int minutes = (int) (solveTime / 60000);
    int seconds = (int) (solveTime / 1000) % 60;

    int millis;
    if (parUseHighPrecision) {
      millis = (int) (solveTime % 1000);
    } else {
      millis = (int) ((solveTime / 10) % 100);
    }

    if (minutes > 0) {
      sb.append(minutes).append(":");
      sb.append(String.format("%02d", seconds));
    } else {
      sb.append(seconds);
    }

    if (parUseHighPrecision) {
      sb.append(".").append(String.format("%03d", millis));
    } else {
      sb.append(".").append(String.format("%02d", millis));
    }

    return sb.toString();
  }

  // Formats a positive time improvement (in ms) as a signed delta, e.g. "-0.54".
  // Uses the same precision as formatSolveTime so it matches the displayed times.
  public String formatSolveTimeDifference(long diffMs) {
    return formatSolveTimeDifference(diffMs, Options.INSTANCE.isUsingHighPrecisionTimer());
  }

  public String formatSolveTimeDifference(long diffMs, boolean parUseHighPrecision) {
    return "-" + formatSolveTime(diffMs, null, parUseHighPrecision);
  }

  public Long unformatSolveTime(String solveTime) {
    if (solveTime == null) {
      return null;
    }
    Long time = parseSolveTime(solveTime);
    if (time != null) {
      return time;
    }
    // Only a non-numeric string can be one of the localized sentinels; checking them last keeps
    // the numeric path free of the Android context (which also makes imports unit-testable).
    if (solveTime.equals(App.INSTANCE.getContext().getString(R.string.DNF))) {
      return (long) -1;
    }
    if (solveTime.equals(App.INSTANCE.getContext().getString(R.string.NA))) {
      return (long) -2;
    }
    return null;
  }

  /**
   * Parses a plain solve time ("12.345", "1:05.120"), null when the string is not one. Unlike
   * {@link #unformatSolveTime} it knows no localized sentinel, and so needs no Android context.
   */
  public Long unformatPlainSolveTime(String solveTime) {
    return (solveTime == null) ? null : parseSolveTime(solveTime);
  }

  private Long parseSolveTime(String solveTime) {
    String[] split = solveTime.split(":");
    if (split.length > 2) {
      return null;
    }

    long ts = 0;

    try {
      int minutes = 0;
      if (split.length == 2) {
        minutes = Integer.parseInt(split[0]);
      }
      split = split[split.length - 1].split("\\.");
      int seconds = Integer.parseInt(split[0]);

      String decimalsStr = split[1];
      int decimals = Integer.parseInt(decimalsStr);

      if (decimalsStr.length() == 2) {
        ts += decimals * 10;
      } else if (decimalsStr.length() == 3) {
        ts += decimals;
      } else {
        return null;
      }

      ts += seconds * 1000;
      ts += minutes * 60000;
    } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
      return null; // a time with no decimal part ("5") used to escape as an uncaught exception
    }

    return ts;
  }

  public String formatPercentage(Integer pct) {
    return formatPercentage(pct, null);
  }

  public String formatPercentage(Integer pct, String defaultValue) {
    if (pct == null || pct < 0 || pct > 100) {
      return defaultValue == null ? App.INSTANCE.getContext().getString(R.string.NA) : defaultValue;
    }
    return pct + "%";
  }

  public String formatDateTime(Long ms) {
    if (ms == null) {
      return "";
    }
    SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy · HH:mm:ss", Locale.ENGLISH);
    return sdf.format(new Date(ms));
  }

  /** The same moment to the minute, for a row too narrow to finish the seconds. */
  public String formatDateTimeToMinute(Long ms) {
    if (ms == null) {
      return "";
    }
    return new SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.ENGLISH).format(new Date(ms));
  }

  /**
   * When a solve happened, as a history row says it: how long ago for one within the hour, which is
   * what you are reading a running session for, and the time of day for anything older. Past an
   * hour the relative figure stops telling one row from another, and a whole sitting would read
   * "2 h ago" all the way down. The solve's own screen always states the moment itself.
   */
  public String formatSolveMoment(Long timestamp, long now) {
    if (timestamp == null) {
      return "";
    }
    long elapsed = now - timestamp;
    if (elapsed < 0 || elapsed >= HOUR_MS) {
      return formatTimeOfDay(timestamp);
    }
    if (elapsed < MINUTE_MS) {
      return App.INSTANCE.getContext().getString(R.string.time_just_now);
    }
    return App.INSTANCE.getContext().getString(R.string.time_minutes_ago, elapsed / MINUTE_MS);
  }

  /** The time of day alone, for a row whose date is already carried by its day heading. */
  public String formatTimeOfDay(Long ms) {
    if (ms == null) {
      return "";
    }
    return new SimpleDateFormat("HH:mm:ss", Locale.ENGLISH).format(new Date(ms));
  }

  public String formatDate(Long ms) {
    if (ms == null) {
      return "";
    }
    SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH);
    return sdf.format(new Date(ms));
  }

  public String formatMonthYear(Long ms) {
    if (ms == null) {
      return "";
    }
    SimpleDateFormat sdf = new SimpleDateFormat("MMM, yyyy", Locale.ENGLISH);
    return sdf.format(new Date(ms));
  }

  /** A session start as its picker states it: the year only when it is not the current one. */
  public String formatSessionStart(Long ms) {
    if (ms == null) {
      return "";
    }
    Calendar start = Calendar.getInstance();
    start.setTimeInMillis(ms);
    boolean thisYear = start.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR);
    SimpleDateFormat sdf = new SimpleDateFormat(thisYear ? "MMM d · HH:mm" : "MMM d, yyyy · HH:mm", Locale.ENGLISH);
    return sdf.format(new Date(ms));
  }

  public String formatExportDateTime(Long ms) {
    if (ms == null) {
      return "";
    }
    SimpleDateFormat sdf = new SimpleDateFormat(EXPORT_DATE_FORMAT, Locale.ENGLISH);
    return sdf.format(new Date(ms));
  }

  public Long unformatExportDateTime(String date) {
    SimpleDateFormat sdf = new SimpleDateFormat(EXPORT_DATE_FORMAT, Locale.ENGLISH);
    Long ts;
    try {
      ts = sdf.parse(date).getTime();
    } catch (ParseException e) {
      ts = null;
    }
    return ts;
  }

  public String formatFloat(double value, int decimalsCount) {
    double multiplicator = Math.pow(10, decimalsCount);
    value = value * multiplicator;
    long rounded = Math.round(value); // to avoid things like "2.9999"
    return String.valueOf(((float) rounded) / multiplicator);
  }

}
