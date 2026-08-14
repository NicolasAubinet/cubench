package com.cube.nanotimer.coach;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * One number a plan leans on, named by where it sits in the payload it came from.
 *
 * <p>This is what makes a plan checkable. A path resolves against the payload or it does not, and a
 * value either matches the figure at that path or it was invented, so a claim can be thrown out
 * without asking anything about the language it was written in
 * ({@link CoachPlan#uncited(CoachPayload)}).
 *
 * <p>Paths read like the payload: {@code solve.mean_ms}, {@code families.pll.recognition_ms},
 * {@code cases.pll_gb.time_lost_ms}, {@code drill_cases.pll_gb.mean_ms},
 * {@code comparisons.pll_gb.gap_ms}.
 */
public class Evidence {

  private final String path;
  private final double value;

  public Evidence(String path, double value) {
    this.path = path;
    this.value = value;
  }

  public String getPath() {
    return path;
  }

  public double getValue() {
    return value;
  }

  JSONObject toJson() throws JSONException {
    JSONObject json = new JSONObject();
    json.put("path", path);
    json.put("value", value);
    return json;
  }

  static Evidence fromJson(JSONObject json) throws JSONException {
    return new Evidence(json.getString("path"), json.getDouble("value"));
  }
}
