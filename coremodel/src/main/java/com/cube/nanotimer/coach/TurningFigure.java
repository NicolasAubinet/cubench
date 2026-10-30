package com.cube.nanotimer.coach;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * How a step, or the whole solve, is turned: moves a solve, and how often the solver stops between
 * two of them once they know what to turn.
 *
 * <p><b>The two ride together because neither reads alone.</b> A low turning speed is a solver
 * turning smoothly and slowly or one turning fast with freezes between, and the advice is opposite;
 * moves and time with nothing else would have a coach tell a stalling solver to turn faster.
 * {@code stall_ms} is all the time between turns not spent turning, each turn timed at the solver's
 * own pace, and {@code stalls} the pauses in it over two turns long: a stop, not slow turning.
 * {@code stop_ms} is what those stops came to, which is what says a step is stalling.
 *
 * <p><b>No turns per second.</b> It is the moves over a time already sent, and a field of its own
 * could be cited without either half clearing its floor. Moves count what was turned, including
 * what was taken back, since a mistake and its undo are the inefficiency being measured.
 */
public class TurningFigure {

  private final String code;
  private final int count;
  private final double moves;
  private final double stalls;
  private final long stallMs;
  private final long stopMs;
  private final double rejectionRate;

  TurningFigure(String code, int count, double moves, double stalls, long stallMs, long stopMs,
      double rejectionRate) {
    this.code = code;
    this.count = count;
    this.moves = moves;
    this.stalls = stalls;
    this.stallMs = stallMs;
    this.stopMs = stopMs;
    this.rejectionRate = rejectionRate;
  }

  /** The step's family, {@code cross}, or {@link CoachPayloadBuilder#SOLVE} for the whole solve. */
  public String getCode() {
    return code;
  }

  /** How many solves it is read from. */
  public int getCount() {
    return count;
  }

  /** Moves a solve. */
  public double getMoves() {
    return moves;
  }

  /** Stops a solve. */
  public double getStalls() {
    return stalls;
  }

  /** Time not turning a solve, stops included. */
  public long getStallMs() {
    return stallMs;
  }

  /** What the stops came to a solve. */
  public long getStopMs() {
    return stopMs;
  }

  public double getRejectionRate() {
    return rejectionRate;
  }

  Double field(String name) {
    if ("count".equals(name)) {
      return Double.valueOf(count);
    }
    if ("moves".equals(name)) {
      return Double.valueOf(moves);
    }
    if ("stalls".equals(name)) {
      return Double.valueOf(stalls);
    }
    if ("stall_ms".equals(name)) {
      return Double.valueOf(stallMs);
    }
    if ("stop_ms".equals(name)) {
      return Double.valueOf(stopMs);
    }
    return "rejection_rate".equals(name) ? Double.valueOf(rejectionRate) : null;
  }

  JSONObject toJson() throws JSONException {
    JSONObject json = new JSONObject();
    json.put("code", code);
    json.put("count", count);
    json.put("moves", tenths(moves));
    json.put("stalls", tenths(stalls));
    json.put("stall_ms", stallMs);
    json.put("stop_ms", stopMs);
    json.put("rejection_rate", Math.round(rejectionRate * 100) / 100.0);
    return json;
  }

  /** An app older than stop_ms sent stall_ms as whole gaps over 400 ms, which is its stop time. */
  static TurningFigure fromJson(JSONObject json) throws JSONException {
    long stallMs = json.getLong("stall_ms");
    return new TurningFigure(json.getString("code"), json.getInt("count"),
        json.getDouble("moves"), json.getDouble("stalls"), stallMs,
        json.optLong("stop_ms", stallMs), json.getDouble("rejection_rate"));
  }

  /** A mean of whole counts is not finer than a tenth. */
  private static double tenths(double value) {
    return Math.round(value * 10) / 10.0;
  }
}
