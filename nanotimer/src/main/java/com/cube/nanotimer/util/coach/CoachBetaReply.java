package com.cube.nanotimer.util.coach;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * What the coach service answered about its free beta, to a status check or a join. Read from the
 * status and body alone, like {@link CoachReply}.
 */
public final class CoachBetaReply {

  public enum Kind {
    /** Where the beta stands, in {@link #getPhase}, {@link #isMember} and {@link #getSeatsLeft}. */
    STATUS,
    /** The service declined, and said why in {@link #getRefusal}. */
    REFUSED,
    UNREACHABLE,
    UNREADABLE
  }

  private final Kind kind;
  private final CoachBeta.Phase phase;
  private final boolean member;
  private final int seatsLeft;
  private final CoachRefusal refusal;
  private final CoachBeta.FeedbackMode feedbackMode;

  private CoachBetaReply(Kind kind, CoachBeta.Phase phase, boolean member, int seatsLeft,
      CoachBeta.FeedbackMode feedbackMode, CoachRefusal refusal) {
    this.kind = kind;
    this.phase = phase;
    this.member = member;
    this.seatsLeft = seatsLeft;
    this.refusal = refusal;
    this.feedbackMode = feedbackMode;
  }

  static CoachBetaReply unreachable() {
    return new CoachBetaReply(Kind.UNREACHABLE, null, false, 0, null, null);
  }

  static CoachBetaReply unreadable() {
    return new CoachBetaReply(Kind.UNREADABLE, null, false, 0, null, null);
  }

  static CoachBetaReply parse(int httpStatus, String body) {
    try {
      JSONObject json = new JSONObject(body);
      JSONObject error = json.optJSONObject("error");
      if (error != null) {
        return new CoachBetaReply(Kind.REFUSED, null, false, 0, null,
            CoachRefusal.of(error.optString("code")));
      }
      CoachBeta.Phase phase = CoachBeta.Phase.of(json.optString("phase"));
      if (httpStatus != 200 || phase == null || !json.has("member")) {
        return unreadable();
      }
      return new CoachBetaReply(Kind.STATUS, phase, json.getBoolean("member"),
          Math.max(0, json.optInt("seats_left", 0)),
          CoachBeta.FeedbackMode.of(json.optString("feedback_mode", null)), null);
    } catch (JSONException e) {
      return unreadable();
    }
  }

  public Kind getKind() {
    return kind;
  }

  /** Only for {@link Kind#STATUS}. */
  public CoachBeta.Phase getPhase() {
    return phase;
  }

  /** Whether this install joined. Still true once the beta has ended, for the thank-you offer. */
  public boolean isMember() {
    return member;
  }

  /** 0 unless the beta is open. */
  public int getSeatsLeft() {
    return seatsLeft;
  }

  /**
   * The mode the service gave this install, or null from a service older than modes, or one this
   * app does not know.
   */
  public CoachBeta.FeedbackMode getFeedbackMode() {
    return feedbackMode;
  }

  /** Why the service declined, only for {@link Kind#REFUSED}. */
  public CoachRefusal getRefusal() {
    return refusal;
  }
}
