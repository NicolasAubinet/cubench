package com.cube.nanotimer.util.coach;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/**
 * The coach's free beta as this install last heard of it, and the rating its last plan owes.
 *
 * <p>The service decides where the beta stands: the app keeps the last answer to
 * {@code GET /v1/beta} and asks again when it is stale, so ending the beta reaches every install
 * on its next check, updated or not.
 *
 * <p>A member's next plan waits until the last one has its stars. The app keeps the id of the last
 * plan and of the last one rated, so it knows a rating is owed without asking, and keeps each plan's
 * answers as a draft until they are sent.
 *
 * <p>Kept in a file of its own, outside what a backup carries, like {@link CoachAccess}: membership
 * belongs to this install's Coach ID, which a restore does not bring.
 */
public final class CoachBeta {

  public enum Phase {
    /** Taking newcomers while seats are left. */
    OPEN,
    /** Taking nobody new; members keep the coach. */
    CLOSED,
    /** Nobody is let in by the beta. Members are still known, for the thank-you offer. */
    ENDED;

    String code() {
      return name().toLowerCase(Locale.ROOT);
    }

    /** Null for a code this app does not know. */
    static Phase of(String code) {
      for (Phase phase : values()) {
        if (phase.code().equals(code)) {
          return phase;
        }
      }
      return null;
    }
  }

  /** How long a status is trusted before the app asks again. */
  static final long STALE_MS = 6L * 60 * 60 * 1000;

  private static final String FILE = "coach_beta";
  private static final String PHASE_KEY = "phase";
  private static final String MEMBER_KEY = "member";
  private static final String SEATS_LEFT_KEY = "seats_left";
  private static final String CHECKED_AT_KEY = "checked_at";
  private static final String PLAN_ID_KEY = "plan_id";
  private static final String PLAN_SOLVE_TYPE_KEY = "plan_solve_type";
  private static final String PLAN_WRITTEN_AT_KEY = "plan_written_at";
  private static final String PREVIOUS_PLAN_ID_KEY = "previous_plan_id";
  private static final String RATED_PLAN_ID_KEY = "rated_plan_id";
  private static final String OWED_SAID_KEY = "owed_said";
  private static final String DRAFT_KEY_PREFIX = "draft_";

  private static CoachBeta instance;

  private final SharedPreferences preferences;

  private CoachBeta(SharedPreferences preferences) {
    this.preferences = preferences;
  }

  public static synchronized CoachBeta of(Context context) {
    if (instance == null) {
      instance = new CoachBeta(
          context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE));
    }
    return instance;
  }

  /** Where the beta stood at the last check, null when this install never heard. */
  public synchronized Phase getPhase() {
    return Phase.of(preferences.getString(PHASE_KEY, null));
  }

  /** Whether this install joined, even once the beta has ended. */
  public synchronized boolean isMember() {
    return preferences.getBoolean(MEMBER_KEY, false);
  }

  public synchronized int getSeatsLeft() {
    return preferences.getInt(SEATS_LEFT_KEY, 0);
  }

  /** Whether the beta is running, open or closed: the coach is shown as "Beta", not "Premium". */
  public synchronized boolean isRunning() {
    return running(getPhase());
  }

  /** Whether the beta lets this install in, as far as the app can tell. */
  public synchronized boolean letsIn() {
    return isMember() && running(getPhase());
  }

  /** Whether this install may join now. */
  public synchronized boolean offersJoin() {
    return getPhase() == Phase.OPEN && !isMember() && getSeatsLeft() > 0;
  }

  /** Whether the beta is open but has no seat left for this install. */
  public synchronized boolean isFull() {
    return getPhase() == Phase.OPEN && !isMember() && getSeatsLeft() == 0;
  }

  static boolean running(Phase phase) {
    return phase == Phase.OPEN || phase == Phase.CLOSED;
  }

  /** Whether the last status is old enough to ask again. A time ahead of the clock is old. */
  public synchronized boolean isStale(long now) {
    return stale(preferences.getLong(CHECKED_AT_KEY, 0), now);
  }

  static boolean stale(long checkedAt, long now) {
    return checkedAt == 0 || checkedAt > now || now - checkedAt >= STALE_MS;
  }

  /** Keeps a status the service sent. Anything else is no news, and the last status stands. */
  synchronized void noted(CoachBetaReply reply, long now) {
    if (reply.getKind() != CoachBetaReply.Kind.STATUS) {
      return;
    }
    preferences.edit()
        .putString(PHASE_KEY, reply.getPhase().code())
        .putBoolean(MEMBER_KEY, reply.isMember())
        .putInt(SEATS_LEFT_KEY, reply.getSeatsLeft())
        .putLong(CHECKED_AT_KEY, now)
        .commit();
  }

  /** Something said the status is out of date, so it is asked again at the next check. */
  synchronized void statusDoubted() {
    preferences.edit().remove(CHECKED_AT_KEY).commit();
  }

  /**
   * A plan arrived. The same plan sent again (the service's answer to a repeated question) keeps
   * its rating and its draft.
   *
   * @param planId null from a service that sent none, which no rating can then be sent for
   */
  synchronized void planReceived(String planId, int solveTypeId, long writtenAt) {
    String last = preferences.getString(PLAN_ID_KEY, null);
    SharedPreferences.Editor editor = preferences.edit()
        .putInt(PLAN_SOLVE_TYPE_KEY, solveTypeId)
        .putLong(PLAN_WRITTEN_AT_KEY, writtenAt);
    if (planId == null || !planId.equals(last)) {
      if (last != null) {
        editor.putString(PREVIOUS_PLAN_ID_KEY, last);
        // The one before the last is no longer rateable from the app.
        String previous = preferences.getString(PREVIOUS_PLAN_ID_KEY, null);
        if (previous != null) {
          editor.remove(DRAFT_KEY_PREFIX + previous);
        }
      }
      editor.putString(PLAN_ID_KEY, planId).remove(OWED_SAID_KEY);
    }
    editor.commit();
  }

  /** The service's id for the last plan, null when there is none or it came without one. */
  public synchronized String getPlanId() {
    return preferences.getString(PLAN_ID_KEY, null);
  }

  /** The plan before the last, whose "did you work on it" is asked on the last one. */
  public synchronized String getPreviousPlanId() {
    return preferences.getString(PREVIOUS_PLAN_ID_KEY, null);
  }

  /**
   * Whether a stored plan is the last one the service sent, so the one a rating goes to.
   *
   * @param writtenAt the stored plan's time, which the app gave it when it arrived
   */
  public synchronized boolean isLastPlan(int solveTypeId, long writtenAt) {
    return getPlanId() != null
        && preferences.getInt(PLAN_SOLVE_TYPE_KEY, -1) == solveTypeId
        && preferences.getLong(PLAN_WRITTEN_AT_KEY, -1) == writtenAt;
  }

  /** Whether the next plan waits on the last one's stars. */
  public synchronized boolean owesRating() {
    return owes(letsIn(), getPlanId(), preferences.getString(RATED_PLAN_ID_KEY, null),
        preferences.getBoolean(OWED_SAID_KEY, false));
  }

  /**
   * @param said whether the service refused a plan for a missing rating since the last one
   *     arrived, which is believed over the app's own record: a rating may have failed to arrive
   */
  static boolean owes(boolean letsIn, String planId, String ratedPlanId, boolean said) {
    if (!letsIn || planId == null) {
      return false;
    }
    return said || !planId.equals(ratedPlanId);
  }

  /** The service refused a plan because the last one has no stars. */
  synchronized void ratingOwed() {
    preferences.edit().putBoolean(OWED_SAID_KEY, true).commit();
  }

  /** The service kept the stars of this plan. */
  synchronized void rated(String planId) {
    SharedPreferences.Editor editor = preferences.edit().putString(RATED_PLAN_ID_KEY, planId);
    if (planId.equals(getPlanId())) {
      editor.remove(OWED_SAID_KEY);
    }
    editor.commit();
  }

  /** The answers given on a plan so far, a fresh draft when there are none. */
  public synchronized CoachPlanFeedback getFeedback(String planId) {
    return CoachPlanFeedback.fromDraft(preferences.getString(DRAFT_KEY_PREFIX + planId, null));
  }

  public synchronized void saveFeedback(String planId, CoachPlanFeedback feedback) {
    preferences.edit().putString(DRAFT_KEY_PREFIX + planId, feedback.toDraft()).commit();
  }
}
