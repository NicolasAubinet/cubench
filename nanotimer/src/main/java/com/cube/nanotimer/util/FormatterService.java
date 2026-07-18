package com.cube.nanotimer.util;

import com.cube.nanotimer.App;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public enum FormatterService {
  INSTANCE;

  private static final String EXPORT_DATE_FORMAT = "MMM d yyyy - HH:mm:ss";

  public String formatSolveTime(Long solveTime) {
    return formatSolveTime(solveTime, null);
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
    if (solveTime.equals(App.INSTANCE.getContext().getString(R.string.DNF))) {
      return (long) -1;
    }
    if (solveTime.equals(App.INSTANCE.getContext().getString(R.string.NA))) {
      return (long) -2;
    }
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
    } catch (NumberFormatException e) {
      return null;
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

  public String formatDateTimeWithoutSeconds(Long ms) {
    if (ms == null) {
      return "";
    }
    SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.ENGLISH);
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

  /**
   * Format the times of different steps to a String
   * @param times a list of times in ms
   * @return the formatted steps times
   */
  public String formatStepsTimes(List<Long> times) {
    StringBuilder sb = new StringBuilder();
    if (times != null) {
      for (int i = 0; i < times.size(); i++) {
        sb.append(formatSolveTime(times.get(i)));
        if (i < times.size() - 1) {
          sb.append(" / ");
        }
      }
    } else {
      sb.append("-");
    }
    return sb.toString();
  }

}
