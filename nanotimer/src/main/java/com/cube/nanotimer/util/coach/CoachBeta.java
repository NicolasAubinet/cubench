package com.cube.nanotimer.util.coach;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Handler;
import android.os.Looper;


import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

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

  /**
   * Whether this install is asked about its plans, as the service last said. Whatever let the install
   * in decides: a member is required, a grant has its own, a purchase is hidden.
   */
  public enum FeedbackMode {
    /** No questions, no stars. */
    HIDDEN,
    /** The questions and the stars, nothing required and nothing held. */
    OPTIONAL,
    /** Every question answered before sending, and the next plan held until the stars are in. */
    REQUIRED;

    String code() {
      return name().toLowerCase(Locale.ROOT);
    }

    /** Null for a code this app does not know. */
    static FeedbackMode of(String code) {
      for (FeedbackMode mode : values()) {
        if (mode.code().equals(code)) {
          return mode;
        }
      }
      return null;
    }
  }

  /** Told when a new status is kept, on the main thread, so a screen can redraw its beta parts. */
  public interface Listener {
    void onBetaChanged();
  }

  /** How long a status is trusted before the app asks again. */
  static final long STALE_MS = 6L * 60 * 60 * 1000;
  /** For an install that never joined: the beta's seats move slowly, and every user asks this. */
  static final long ANONYMOUS_STALE_MS = 24L * 60 * 60 * 1000;

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
  private static final String MODE_KEY = "feedback_mode";
  private static final String END_SEEN_KEY = "end_seen";
  private static final String INVITATION_SHOWN_KEY = "invitation_shown";
  private static final String DEBUG_PHASE_KEY = "debug_phase";
  private static final String DEBUG_MEMBER_KEY = "debug_member";
  private static final String DEBUG_MODE_KEY = "debug_mode";
  private static final String DEBUG_SEATS_KEY = "debug_seats";

  private static final Set<Listener> LISTENERS = new CopyOnWriteArraySet<Listener>();

  private static CoachBeta instance;

  private final SharedPreferences preferences;
  /** Only a debug build reads the forced values. */
  private final boolean debuggable;

  private CoachBeta(SharedPreferences preferences, boolean debuggable) {
    this.preferences = preferences;
    this.debuggable = debuggable;
  }

  public static synchronized CoachBeta of(Context context) {
    if (instance == null) {
      instance = new CoachBeta(
          context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE),
          (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);
    }
    return instance;
  }

  /**
   * The phase drawn until the service first answers, so an install that has never been online
   * still shows the beta. Set it to ENDED in the first release after the beta ends.
   */
  static final Phase UNHEARD_PHASE = Phase.OPEN;

  /**
   * Where the beta stood at the last check, {@link #UNHEARD_PHASE} when this install never heard,
   * null for a phase this app does not know.
   */
  public synchronized Phase getPhase() {
    String forced = debug(DEBUG_PHASE_KEY);
    if (forced == null && !isHeard()) {
      return UNHEARD_PHASE;
    }
    return Phase.of(forced != null ? forced : preferences.getString(PHASE_KEY, null));
  }

  /** Whether the service ever answered this install about the beta. */
  public synchronized boolean isHeard() {
    return preferences.contains(PHASE_KEY);
  }

  /** Whether this install joined, even once the beta has ended. */
  public synchronized boolean isMember() {
    String forced = debug(DEBUG_MEMBER_KEY);
    return forced != null ? Boolean.parseBoolean(forced) : preferences.getBoolean(MEMBER_KEY, false);
  }

  public synchronized int getSeatsLeft() {
    String forced = debug(DEBUG_SEATS_KEY);
    if (forced == null && !isHeard()) {
      return 1; // assumed open until told: joining asks the service anyway
    }
    return forced != null ? Integer.parseInt(forced) : preferences.getInt(SEATS_LEFT_KEY, 0);
  }

  /** Whether the plans are asked about, and whether the next waits on it. Hidden until heard. */
  public synchronized FeedbackMode getFeedbackMode() {
    String forced = debug(DEBUG_MODE_KEY);
    FeedbackMode mode = FeedbackMode.of(
        forced != null ? forced : preferences.getString(MODE_KEY, null));
    return mode == null ? FeedbackMode.HIDDEN : mode;
  }

  /** A debug build's forced value, or null to read the service's. */
  private String debug(String key) {
    return debuggable ? preferences.getString(key, null) : null;
  }

  /**
   * Debug builds only: draws the beta as if the service said this, so both worlds can be checked on
   * one install. A null leaves that part to the service.
   */
  public synchronized void forceForDebug(Phase phase, Boolean member, FeedbackMode mode,
      Integer seatsLeft) {
    preferences.edit()
        .putString(DEBUG_PHASE_KEY, phase == null ? null : phase.code())
        .putString(DEBUG_MEMBER_KEY, member == null ? null : member.toString())
        .putString(DEBUG_MODE_KEY, mode == null ? null : mode.code())
        .putString(DEBUG_SEATS_KEY, seatsLeft == null ? null : seatsLeft.toString())
        .remove(END_SEEN_KEY)
        .remove(INVITATION_SHOWN_KEY)
        .commit();
    changed();
  }

  /** What a debug build forces, in words, or null when it forces nothing. */
  /** Whether this build may force the beta, which only a debug build may. */
  public boolean isDebuggable() {
    return debuggable;
  }

  public synchronized String describeDebug() {
    StringBuilder text = new StringBuilder();
    for (String key : new String[] {DEBUG_PHASE_KEY, DEBUG_MEMBER_KEY, DEBUG_MODE_KEY,
        DEBUG_SEATS_KEY}) {
      String value = debug(key);
      if (value != null) {
        text.append(text.length() == 0 ? "" : ", ").append(key.substring(6)).append(' ')
            .append(value);
      }
    }
    return text.length() == 0 ? null : text.toString();
  }

  public static void addListener(Listener listener) {
    LISTENERS.add(listener);
  }

  public static void removeListener(Listener listener) {
    LISTENERS.remove(listener);
  }

  private static void changed() {
    new Handler(Looper.getMainLooper()).post(new Runnable() {
      @Override
      public void run() {
        for (Listener listener : LISTENERS) {
          listener.onBetaChanged();
        }
      }
    });
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

  /**
   * Whether the last status is old enough to ask again. A time ahead of the clock is old.
   *
   * @param anonymous whether this install asks without saying who it is ({@code CoachClient})
   */
  public synchronized boolean isStale(long now, boolean anonymous) {
    return due(preferences.getLong(CHECKED_AT_KEY, 0), now, anonymous,
        preferences.contains(PHASE_KEY)
            && Phase.of(preferences.getString(PHASE_KEY, null)) == Phase.ENDED);
  }

  /**
   * @param heardEnded whether the service last said the beta has ended: a beta never comes back,
   *     so an install that never joined has nothing more to ask, ever
   */
  static boolean due(long checkedAt, long now, boolean anonymous, boolean heardEnded) {
    if (anonymous && heardEnded) {
      return false;
    }
    long maxAge = anonymous ? ANONYMOUS_STALE_MS : STALE_MS;
    return checkedAt == 0 || checkedAt > now || now - checkedAt >= maxAge;
  }

  static boolean stale(long checkedAt, long now) {
    return due(checkedAt, now, false, false);
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
        .putString(MODE_KEY, modeOf(reply).code())
        .putLong(CHECKED_AT_KEY, now)
        .commit();
    changed();
  }

  /**
   * The mode a status gives. A service from before modes sent none, and then only a member was
   * asked, and held.
   */
  static FeedbackMode modeOf(CoachBetaReply reply) {
    if (reply.getFeedbackMode() != null) {
      return reply.getFeedbackMode();
    }
    return reply.isMember() && running(reply.getPhase()) ? FeedbackMode.REQUIRED
        : FeedbackMode.HIDDEN;
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

  /** The solve type the last plan was written for, -1 when there is none. */
  public synchronized int getPlanSolveTypeId() {
    return preferences.getInt(PLAN_SOLVE_TYPE_KEY, -1);
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
    return owes(getFeedbackMode() == FeedbackMode.REQUIRED, getPlanId(),
        preferences.getString(RATED_PLAN_ID_KEY, null),
        preferences.getBoolean(OWED_SAID_KEY, false));
  }

  /**
   * @param held whether this install is in required mode, the only one the service holds
   * @param said whether the service refused a plan for a missing rating since the last one
   *     arrived, which is believed over the app's own record: a rating may have failed to arrive
   */
  static boolean owes(boolean held, String planId, String ratedPlanId, boolean said) {
    if (!held || planId == null) {
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
    changed();
  }

  /** Whether the thanks at the end of the beta was shown and closed. Only ever shown once. */
  public synchronized boolean isEndSeen() {
    return preferences.getBoolean(END_SEEN_KEY, false);
  }

  public synchronized void endSeen() {
    preferences.edit().putBoolean(END_SEEN_KEY, true).commit();
    changed();
  }

  /** Whether the beta's invitation opened by itself. Only ever once. */
  public synchronized boolean isInvitationShown() {
    return preferences.getBoolean(INVITATION_SHOWN_KEY, false);
  }

  public synchronized void invitationShown() {
    preferences.edit().putBoolean(INVITATION_SHOWN_KEY, true).commit();
  }

  /** Whether the stars of this plan reached the service. */
  public synchronized boolean isRated(String planId) {
    return planId != null && planId.equals(preferences.getString(RATED_PLAN_ID_KEY, null));
  }

  /** The answers given on a plan so far, a fresh draft when there are none. */
  public synchronized CoachPlanFeedback getFeedback(String planId) {
    return CoachPlanFeedback.fromDraft(preferences.getString(DRAFT_KEY_PREFIX + planId, null));
  }

  public synchronized void saveFeedback(String planId, CoachPlanFeedback feedback) {
    preferences.edit().putString(DRAFT_KEY_PREFIX + planId, feedback.toDraft()).commit();
  }
}
