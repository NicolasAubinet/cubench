package com.cube.nanotimer.coach;

import com.cube.nanotimer.drill.DrillSpec;
import com.cube.nanotimer.step.LastLayerAlgorithms;

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
 *
 * <p>The numbers are only half of it. A card also names what it is about, and {@link #unknownCodes}
 * throws out one naming a case the payload never mentioned, which is the invention the figures
 * cannot catch: every number a card cites can resolve while the case it prescribes was made up.
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

  /**
   * The cases and steps this plan is about that the payload never mentioned. Separate from
   * {@link #uncited} rather than folded into it because it is a different kind of invention and has
   * nothing to return as evidence: a made-up case has no path and no value, only a name.
   *
   * <p>A code counted here is one no figure and no case set holds, so this stays as method-agnostic
   * as the payload is: whatever the app recorded is what a plan may speak of.
   */
  public List<String> unknownCodes(CoachPayload payload) {
    List<String> unknown = new ArrayList<String>();
    for (FocusArea area : focus) {
      for (String code : area.getCodes()) {
        if (!payload.holds(code)) {
          unknown.add(code);
        }
      }
    }
    return unknown;
  }

  /**
   * The drills this plan prescribes that cannot be dealt, which is the third way a card goes wrong
   * and the one the other two cannot see.
   *
   * <p>{@link #uncited} reads the numbers a card cites and {@link #unknownCodes} reads the codes it
   * names. <b>Neither reads the case list inside the card's drill spec</b>, so a card can cite only
   * real figures, name only a real case, and still hand back a drill for something no scrambler has
   * heard of. That is a button that opens a drill with nothing in it, and it arrives by a route the
   * other two checks do not cover.
   *
   * <p>Two conditions, and a case has to meet both. It has to be one
   * {@link LastLayerAlgorithms#dealsCase} holds, because a scramble is a row of that table undone,
   * and it has to be one this payload sent, because a coach may only speak of what it was told. The
   * first is what {@code oll} fails, being a family the app deals cases of and not itself a case;
   * the second is what a case invented wholesale fails, and also what a real case belonging to
   * somebody else's history fails.
   *
   * <p>It lives here rather than in whoever writes the plan for the same reason {@code uncited}
   * does: two copies of a validation rule drift, and the app runs this over a plan read back out of
   * its own database, which no writer is present for.
   *
   * @return the case codes that cannot be dealt, or the reason code of an area whose drill will not
   *     parse at all, that one naming no cases to return; empty when every button has something
   *     behind it
   */
  public List<String> undealableDrills(CoachPayload payload) {
    List<String> undealable = new ArrayList<String>();
    for (FocusArea area : focus) {
      if (area.getDrill() == null) {
        continue;
      }
      DrillSpec spec;
      try {
        spec = DrillSpec.fromJson(area.getDrill());
      } catch (RuntimeException e) {
        undealable.add(area.getReasonCode());
        continue;
      }
      for (String code : spec.getCases()) {
        if (!LastLayerAlgorithms.dealsCase(code) || !payload.holds(code)) {
          undealable.add(code);
        }
      }
    }
    return undealable;
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
