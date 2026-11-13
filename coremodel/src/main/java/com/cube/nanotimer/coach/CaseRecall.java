package com.cube.nanotimer.coach;

import com.cube.nanotimer.session.CaseKnowledge;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

/**
 * How well a learned case is holding, for the daily drill to pick from: when it was last seen,
 * its slips of the last week, how many days it has gone in clean since its last slip, and its
 * latest recognitions against its usual one.
 *
 * <p>Dated in local days ago rather than timestamps: spaced repetition needs to know when a case
 * was last reviewed, and a count of days says that with nothing finer. A slip is a solve that took
 * two looks at the case or a drill rep turned with the algorithm looked up or restarted. A clean
 * day is a local day the case went in clean at least once, in a solve or a drill, so a drill of
 * five reps is one review and not five.
 */
public final class CaseRecall {

  /** The window recent slips are counted over, in days. */
  public static final int SLIP_DAYS = 7;

  /** How many of a case's newest solves its recent recognition is the median of. */
  public static final int RECENT_RECOGNITIONS = 3;

  /** What a slip was, as the payload names it. */
  public static final String TWO_LOOKS = "two_looks";
  public static final String LOOKED_UP = "looked_up";

  private static final long DAY_MS = 24L * 60 * 60 * 1000;

  /** One occurrence of a case, as {@link CaseKnowledge} reads it, and when. */
  public static final class Occurrence {
    private final CaseKnowledge.Evidence says;
    private final long atMs;

    public Occurrence(CaseKnowledge.Evidence says, long atMs) {
      this.says = says;
      this.atMs = atMs;
    }
  }

  private final String code;
  private final String status;
  private final int seenDaysAgo;
  private final int slips;
  private final Integer slipDaysAgo;
  private final String slipKind;
  private final int cleanDays;
  private final Integer cleanDaysAgo;
  private final Long recentRecognitionMs;

  CaseRecall(String code, String status, int seenDaysAgo, int slips, Integer slipDaysAgo,
      String slipKind, int cleanDays, Integer cleanDaysAgo, Long recentRecognitionMs) {
    this.code = code;
    this.status = status;
    this.seenDaysAgo = seenDaysAgo;
    this.slips = slips;
    this.slipDaysAgo = slipDaysAgo;
    this.slipKind = slipKind;
    this.cleanDays = cleanDays;
    this.cleanDaysAgo = cleanDaysAgo;
    this.recentRecognitionMs = recentRecognitionMs;
  }

  /**
   * One case's recall, or null where it has no occurrence.
   *
   * @param occurrences oldest first
   * @param recognitionsMs the recognitions of its newest solves, newest first; may be empty
   */
  static CaseRecall of(String code, CaseKnowledge.Status status, List<Occurrence> occurrences,
      List<Long> recognitionsMs, long nowMs, TimeZone zone) {
    if (occurrences == null || occurrences.isEmpty()) {
      return null;
    }
    long today = dayOf(nowMs, zone);
    int slips = 0;
    Integer slipDaysAgo = null;
    String slipKind = null;
    Set<Long> cleanDays = new HashSet<Long>();
    Integer cleanDaysAgo = null;
    for (int i = occurrences.size() - 1; i >= 0; i--) {
      Occurrence occurrence = occurrences.get(i);
      int daysAgo = (int) Math.max(0, today - dayOf(occurrence.atMs, zone));
      String kind = slipKind(occurrence.says);
      if (kind != null) {
        if (slipDaysAgo == null) {
          slipDaysAgo = Integer.valueOf(daysAgo);
          slipKind = kind;
        }
        if (daysAgo < SLIP_DAYS) {
          slips++;
        }
      } else if (slipDaysAgo == null) {
        cleanDays.add(Long.valueOf(daysAgo));
        if (cleanDaysAgo == null) {
          cleanDaysAgo = Integer.valueOf(daysAgo);
        }
      }
    }
    int seen = (int) Math.max(0, today - dayOf(occurrences.get(occurrences.size() - 1).atMs, zone));
    return new CaseRecall(code, status == null ? null : status.code(), seen, slips, slipDaysAgo,
        slipKind, cleanDays.size(), cleanDaysAgo, median(recognitionsMs));
  }

  private static String slipKind(CaseKnowledge.Evidence says) {
    return says == CaseKnowledge.Evidence.HELPED ? TWO_LOOKS
        : says == CaseKnowledge.Evidence.LOOKED_UP ? LOOKED_UP : null;
  }

  private static Long median(List<Long> recognitionsMs) {
    List<Long> recent = new ArrayList<Long>();
    for (int i = 0; recognitionsMs != null && i < recognitionsMs.size()
        && recent.size() < RECENT_RECOGNITIONS; i++) {
      recent.add(recognitionsMs.get(i));
    }
    if (recent.size() < RECENT_RECOGNITIONS) {
      return null;
    }
    Collections.sort(recent);
    return recent.get(recent.size() / 2);
  }

  private static long dayOf(long atMs, TimeZone zone) {
    long local = atMs + zone.getOffset(atMs);
    long day = local / DAY_MS;
    return local < 0 && local % DAY_MS != 0 ? day - 1 : day; // Math.floorDiv needs API 24
  }

  /** The case, "pll_gb". */
  public String getCode() {
    return code;
  }

  /** Its {@link CaseKnowledge.Status} code, "needs_review", or null where it has none. */
  public String getStatus() {
    return status;
  }

  public boolean needsReview() {
    return CaseKnowledge.Status.NEEDS_REVIEW.code().equals(status);
  }

  /** Local days since it last came up, in a solve or a drill; 0 for today. */
  public int getSeenDaysAgo() {
    return seenDaysAgo;
  }

  /** Slips in the last {@link #SLIP_DAYS} days. */
  public int getSlips() {
    return slips;
  }

  /** Local days since its newest slip, or null for a case never slipped on. */
  public Integer getSlipDaysAgo() {
    return slipDaysAgo;
  }

  /** {@link #TWO_LOOKS} or {@link #LOOKED_UP} for its newest slip, or null. */
  public String getSlipKind() {
    return slipKind;
  }

  /** Local days it went in clean since its newest slip, or ever where it has none. */
  public int getCleanDays() {
    return cleanDays;
  }

  /** Local days since it last went in clean after its newest slip, or null where it has not. */
  public Integer getCleanDaysAgo() {
    return cleanDaysAgo;
  }

  /** Median recognition of its newest {@link #RECENT_RECOGNITIONS} solves, or null for fewer. */
  public Long getRecentRecognitionMs() {
    return recentRecognitionMs;
  }

  public JSONObject toJson() throws JSONException {
    JSONObject json = new JSONObject();
    json.put("code", code);
    if (status != null) {
      json.put("status", status);
    }
    json.put("seen_days_ago", seenDaysAgo);
    json.put("slips", slips);
    if (slipDaysAgo != null) {
      json.put("slip_days_ago", slipDaysAgo);
      json.put("slip_kind", slipKind);
    }
    json.put("clean_days", cleanDays);
    if (cleanDaysAgo != null) {
      json.put("clean_days_ago", cleanDaysAgo);
    }
    if (recentRecognitionMs != null) {
      json.put("recent_recognition_ms", recentRecognitionMs);
    }
    return json;
  }

  public static CaseRecall fromJson(JSONObject json) throws JSONException {
    return new CaseRecall(json.getString("code"), json.optString("status", null),
        json.getInt("seen_days_ago"), json.optInt("slips"),
        json.has("slip_days_ago") ? Integer.valueOf(json.getInt("slip_days_ago")) : null,
        json.optString("slip_kind", null), json.optInt("clean_days"),
        json.has("clean_days_ago") ? Integer.valueOf(json.getInt("clean_days_ago")) : null,
        json.has("recent_recognition_ms")
            ? Long.valueOf(json.getLong("recent_recognition_ms")) : null);
  }
}
