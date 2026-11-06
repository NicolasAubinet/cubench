package com.cube.nanotimer.util.coach;

import java.util.Locale;
import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A "Report this plan" sent to {@code POST /v1/plans/{planId}/report}, which mails it to the
 * developer with the plan. Play's policy on AI-generated content asks for a way to flag a plan from
 * inside the app; the stars and answers of {@link CoachPlanFeedback} are product feedback, not that.
 */
public final class CoachPlanReport {

  public static final int REPORT_VERSION = 1;
  public static final int MAX_COMMENT_CHARS = 2000;

  /** Why the plan is reported, as the service reads it. */
  public enum Reason {
    OFFENSIVE, HARMFUL, OTHER;

    String code() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  private CoachPlanReport() {
  }

  /** The request body, under a new report id so a resend after a lost answer is not mailed twice. */
  public static String body(Reason reason, String comment, String versionName) {
    return body(reason, comment, versionName, UUID.randomUUID().toString());
  }

  static String body(Reason reason, String comment, String versionName, String reportId) {
    String note = comment == null ? "" : comment.trim();
    if (note.length() > MAX_COMMENT_CHARS) {
      note = note.substring(0, MAX_COMMENT_CHARS);
    }
    try {
      return new JSONObject()
          .put("report_version", REPORT_VERSION)
          .put("report_id", reportId.toLowerCase(Locale.ROOT))
          .put("reason", reason.code())
          .put("comment", note)
          .put("app", new JSONObject().put("version_name", versionName == null ? "" : versionName))
          .toString();
    } catch (JSONException e) {
      throw new IllegalStateException(e); // only thrown for a non-finite number, and none is put
    }
  }
}
