package com.cube.nanotimer.coach;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * What a case costs in a solve against what it costs in a drill, which is the one figure neither
 * history gives on its own: a case that is quick when drilled and slow in a solve is known and not
 * recognised under pressure, and the other way round says the drill is not asking what a solve asks.
 *
 * <p>Built only when both sides clear their floor and the gap clears the noise of the two samples,
 * so one good drill and two bad solves cannot produce a diagnosis. That test lives in
 * {@link CoachPayloadBuilder} rather than being asked of whoever reads this.
 */
public class CaseComparison {

  private final String code;
  private final int solveCount;
  private final long solveMeanMs;
  private final int drillCount;
  private final long drillMeanMs;

  public CaseComparison(String code, int solveCount, long solveMeanMs, int drillCount,
      long drillMeanMs) {
    this.code = code;
    this.solveCount = solveCount;
    this.solveMeanMs = solveMeanMs;
    this.drillCount = drillCount;
    this.drillMeanMs = drillMeanMs;
  }

  public String getCode() {
    return code;
  }

  public int getSolveCount() {
    return solveCount;
  }

  public long getSolveMeanMs() {
    return solveMeanMs;
  }

  public int getDrillCount() {
    return drillCount;
  }

  public long getDrillMeanMs() {
    return drillMeanMs;
  }

  /** How much longer the case takes in a solve than in a drill. Negative when the drill is slower. */
  public long getGapMs() {
    return solveMeanMs - drillMeanMs;
  }

  JSONObject toJson() throws JSONException {
    JSONObject json = new JSONObject();
    json.put("code", code);
    json.put("solve_count", solveCount);
    json.put("solve_mean_ms", solveMeanMs);
    json.put("drill_count", drillCount);
    json.put("drill_mean_ms", drillMeanMs);
    json.put("gap_ms", getGapMs());
    return json;
  }

  static CaseComparison fromJson(JSONObject json) throws JSONException {
    return new CaseComparison(json.getString("code"), json.getInt("solve_count"),
        json.getLong("solve_mean_ms"), json.getInt("drill_count"), json.getLong("drill_mean_ms"));
  }
}
