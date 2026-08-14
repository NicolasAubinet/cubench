package com.cube.nanotimer.coach;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * What to work on this week: a few focus areas, the numbers behind each, and a drill for the ones
 * that have something to practise.
 *
 * <p>One shape whoever wrote it. A plan comes from the coaching service; it is read, rendered and
 * stored here without knowing how it was arrived at, which is what lets two plans off the same
 * payload be put side by side.
 *
 * <p>Every claim carries the payload figure it rests on, so {@link #uncited} can throw out anything
 * that cites a number nobody sent. A plan whose evidence does not resolve is not a worse plan, it is
 * not a plan.
 */
public class CoachPlan {

  /** The newest plan format this app can read back. */
  public static final int VERSION = 1;

  /** Rates are rounded on the way out, so a cited rate is allowed to be off by that much. */
  private static final double TOLERANCE = 0.005;

  /** Who wrote it, which is the whole point of keeping both. */
  public enum Source {
    /** Read from the payload alone, with no model behind it. */
    HEURISTIC,
    /** The coaching service. */
    COACH;

    public String code() {
      return name().toLowerCase(Locale.ROOT);
    }

    static Source of(String code) {
      for (Source source : values()) {
        if (source.code().equals(code)) {
          return source;
        }
      }
      throw new IllegalArgumentException("Unknown plan source: " + code);
    }
  }

  private final int planVersion;
  private final Source source;
  private final String headline;
  private final List<FocusArea> focus;

  /** @param headline the diagnosis in a sentence, null for a plan written without a model */
  public CoachPlan(int planVersion, Source source, String headline, List<FocusArea> focus) {
    this.planVersion = planVersion;
    this.source = source;
    this.headline = headline;
    this.focus = Collections.unmodifiableList(new ArrayList<FocusArea>(focus));
  }

  public int getPlanVersion() {
    return planVersion;
  }

  public Source getSource() {
    return source;
  }

  public String getHeadline() {
    return headline;
  }

  /** What to work on, worst first. */
  public List<FocusArea> getFocus() {
    return focus;
  }

  /**
   * The evidence this plan cites that the payload does not bear out: a path nothing sent, or a value
   * that is not the figure at it. Empty for a plan that only says what it was told.
   */
  public List<Evidence> uncited(CoachPayload payload) {
    List<Evidence> invented = new ArrayList<Evidence>();
    for (FocusArea area : focus) {
      for (Evidence cited : area.getEvidence()) {
        Double actual = payload.value(cited.getPath());
        if (actual == null || Math.abs(actual.doubleValue() - cited.getValue()) > TOLERANCE) {
          invented.add(cited);
        }
      }
    }
    return invented;
  }

  public String toJson() {
    try {
      JSONObject json = new JSONObject();
      json.put("plan_version", planVersion);
      json.put("source", source.code());
      if (headline != null) {
        json.put("headline", headline);
      }
      JSONArray areas = new JSONArray();
      for (FocusArea area : focus) {
        areas.put(area.toJson());
      }
      json.put("focus", areas);
      return json.toString();
    } catch (JSONException e) {
      throw new IllegalStateException("Cannot write coach plan", e);
    }
  }

  /**
   * @throws IllegalArgumentException if the text is not a plan, or is one this version is too old to
   *     read
   */
  public static CoachPlan parse(String text) {
    try {
      JSONObject json = new JSONObject(text);
      int version = json.getInt("plan_version");
      if (version > VERSION) {
        throw new IllegalArgumentException(
            "Coach plan version " + version + ", this reader knows " + VERSION);
      }
      List<FocusArea> focus = new ArrayList<FocusArea>();
      JSONArray areas = json.optJSONArray("focus");
      for (int i = 0; areas != null && i < areas.length(); i++) {
        focus.add(FocusArea.fromJson(areas.getJSONObject(i)));
      }
      return new CoachPlan(version, Source.of(json.getString("source")),
          json.has("headline") ? json.getString("headline") : null, focus);
    } catch (JSONException e) {
      throw new IllegalArgumentException("Not a coach plan: " + e.getMessage(), e);
    }
  }
}
