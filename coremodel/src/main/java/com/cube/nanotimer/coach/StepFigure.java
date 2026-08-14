package com.cube.nanotimer.coach;

import com.cube.nanotimer.vo.StepStats;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * One line of the payload: what a step, a case or a solve has been costing, and how much the figure
 * is worth.
 *
 * <p>Every figure carries its count, because a mean over five reps and a mean over forty are not the
 * same claim, and a figure whose count is under its floor is never built at all
 * ({@link CoachPayloadBuilder}) rather than sent with a caveat. Anything that does not apply is
 * absent rather than zero: a step timed from its own first move has no recognition to report, and
 * sending 0 there reads as "instant" to anything that did not write it.
 */
public class StepFigure {

  private final String code;
  private final int count;
  private final long meanMs;
  private final Long recognitionMs;
  private final long stdDevMs;
  private final long bestMs;
  private final double rejectionRate;
  private final Long timeLostMs;
  private final Long familyMeanMs;
  private final Double skipRate;

  private StepFigure(String code, int count, long meanMs, Long recognitionMs, long stdDevMs,
      long bestMs, double rejectionRate, Long timeLostMs, Long familyMeanMs, Double skipRate) {
    this.code = code;
    this.count = count;
    this.meanMs = meanMs;
    this.recognitionMs = recognitionMs;
    this.stdDevMs = stdDevMs;
    this.bestMs = bestMs;
    this.rejectionRate = rejectionRate;
    this.timeLostMs = timeLostMs;
    this.familyMeanMs = familyMeanMs;
    this.skipRate = skipRate;
  }

  /** A step of the method, or one of the parts a step was built from, with how often it was skipped. */
  public static StepFigure family(StepStats stats, boolean measuresRecognition,
      double rejectionRate, Double skipRate) {
    return new StepFigure(stats.getCode(), stats.getCount(), stats.getMeanMs(),
        recognitionOf(stats, measuresRecognition), stats.getStdDevMs(), stats.getBestMs(),
        rejectionRate, null, null, skipRate);
  }

  /**
   * One case of a family, with what running it above the family's mean has cost over the window and
   * the mean it was weighed against, since that is what the cost is arithmetic on and neither figure
   * is checkable without the other.
   */
  public static StepFigure stepCase(StepStats stats, boolean measuresRecognition,
      double rejectionRate, long timeLostMs, long familyMeanMs) {
    return new StepFigure(stats.getCode(), stats.getCount(), stats.getMeanMs(),
        recognitionOf(stats, measuresRecognition), stats.getStdDevMs(), stats.getBestMs(),
        rejectionRate, Long.valueOf(timeLostMs), Long.valueOf(familyMeanMs), null);
  }

  /** A figure with neither a family behind it nor cases under it: a whole solve, or a drilled case. */
  public static StepFigure plain(StepStats stats, boolean measuresRecognition,
      double rejectionRate) {
    return new StepFigure(stats.getCode(), stats.getCount(), stats.getMeanMs(),
        recognitionOf(stats, measuresRecognition), stats.getStdDevMs(), stats.getBestMs(),
        rejectionRate, null, null, null);
  }

  /** A step timed from its own first move has nowhere to put recognition, and 0 there reads as instant. */
  private static Long recognitionOf(StepStats stats, boolean measuresRecognition) {
    long recognition = stats.getMeanRecognitionMs();
    return !measuresRecognition || recognition == 0 ? null : Long.valueOf(recognition);
  }

  public String getCode() {
    return code;
  }

  public int getCount() {
    return count;
  }

  public long getMeanMs() {
    return meanMs;
  }

  /** Time spent finding the answer rather than turning, or null when the step does not measure it. */
  public Long getRecognitionMs() {
    return recognitionMs;
  }

  public long getStdDevMs() {
    return stdDevMs;
  }

  public long getBestMs() {
    return bestMs;
  }

  /** How often an occurrence was dropped as junk, from 0 to 1. */
  public double getRejectionRate() {
    return rejectionRate;
  }

  /** What the case cost its family over the window, or null for anything that is not a case. */
  public Long getTimeLostMs() {
    return timeLostMs;
  }

  /** What its family averaged over the same window, or null for anything that is not a case. */
  public Long getFamilyMeanMs() {
    return familyMeanMs;
  }

  /** How often the step was already solved on arrival, or null for anything that cannot be skipped. */
  public Double getSkipRate() {
    return skipRate;
  }

  JSONObject toJson() throws JSONException {
    JSONObject json = new JSONObject();
    json.put("code", code);
    json.put("count", count);
    json.put("mean_ms", meanMs);
    json.put("std_dev_ms", stdDevMs);
    json.put("best_ms", bestMs);
    json.put("rejection_rate", round(rejectionRate));
    if (recognitionMs != null) {
      json.put("recognition_ms", recognitionMs.longValue());
    }
    if (timeLostMs != null) {
      json.put("time_lost_ms", timeLostMs.longValue());
    }
    if (familyMeanMs != null) {
      json.put("family_mean_ms", familyMeanMs.longValue());
    }
    if (skipRate != null) {
      json.put("skip_rate", round(skipRate.doubleValue()));
    }
    return json;
  }

  static StepFigure fromJson(JSONObject json) throws JSONException {
    return new StepFigure(json.getString("code"), json.getInt("count"), json.getLong("mean_ms"),
        json.has("recognition_ms") ? Long.valueOf(json.getLong("recognition_ms")) : null,
        json.getLong("std_dev_ms"), json.getLong("best_ms"), json.getDouble("rejection_rate"),
        json.has("time_lost_ms") ? Long.valueOf(json.getLong("time_lost_ms")) : null,
        json.has("family_mean_ms") ? Long.valueOf(json.getLong("family_mean_ms")) : null,
        json.has("skip_rate") ? Double.valueOf(json.getDouble("skip_rate")) : null);
  }

  /** Rates go out at two decimals: the third is noise the model would read as precision. */
  private static double round(double rate) {
    return Math.round(rate * 100) / 100.0;
  }
}
