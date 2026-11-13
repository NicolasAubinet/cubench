package com.cube.nanotimer;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import com.cube.nanotimer.util.DrillCasePreset;
import com.cube.nanotimer.util.view.HeroStat;
import com.cube.nanotimer.util.view.TimerFont;
import com.cube.nanotimer.vo.BlindMethod;
import com.cube.nanotimer.vo.CubeMethod;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public enum Options {
  INSTANCE;

  public enum InspectionMode { HOLD_AND_RELEASE, AUTOMATIC, OFFICIAL }
  public enum InspectionSoundsType { CLASSIC, OFFICIAL }
  public enum BigCubesNotation { RUF, RWUWFW }
  public enum ClockNotation { UUdU_x_x, UUdd_ux_dx, URx_DRx_DLx }
  public enum RecordNotificationMode { ANY, PB_ONLY, NEVER }
  public enum CrossNeutrality { SPECIFIC, DUAL, FULL }
  public enum SessionTimesDisplay { BARS, TIMES }
  public enum TimerFontSize { SMALL, MEDIUM, LARGE }
  /** What the timer screen draws under the scramble: the cube, the flat net, or nothing. */
  public enum StatePreview { CUBE, NET, NONE }

  /** What a 3-styler shoots from, and so what to assume of a solver who has not said. */
  public static final String DEFAULT_EDGE_BUFFER = "UF";
  public static final String DEFAULT_CORNER_BUFFER = "UFR";
  /** OP corners with M2 edges, the usual pairing: an OP edge solver moves the edge to UR. */
  public static final String DEFAULT_OP_EDGE_BUFFER = "DF";
  public static final String DEFAULT_OP_CORNER_BUFFER = "UBL";
  // The cube's own labels, so white up and green front: the orientation a scramble is followed in.
  public static final String DEFAULT_UP_FACE = "U";
  public static final String DEFAULT_FRONT_FACE = "F";

  private Context context;
  private SharedPreferences sharedPreferences;

  public static final String INSPECTION_MODE_KEY = "inspection_mode";
  public static final String INSPECTION_TIME_KEY = "inspection_time";
  public static final String INSPECTION_SOUNDS_KEY = "inspection_sounds";
  public static final String INSPECTION_SOUNDS_TYPE_KEY = "inspection_sounds_type";
  public static final String SHOW_TIME_WHEN_RUNNING = "show_time_when_running";
  public static final String TIMER_FONT_KEY = "timer_font";
  public static final String TIMER_FONT_SIZE_KEY = "timer_font_size";
  public static final String KEEP_TIMER_SCREEN_ON_KEY = "keep_timer_screen_on";
  public static final String HIGH_PRECISION_TIMER_KEY = "high_precision_timer";
  public static final String COLOR_SAMPLE_SIZE_KEY = "color_sample_size";
  public static final String SESSION_TIMES_DISPLAY_KEY = "session_times_display";
  public static final String STATE_PREVIEW_KEY = "state_preview";
  public static final String BIG_CUBES_NOTATION_KEY = "big_cubes_notation";
  public static final String CLOCK_NOTATION_SYSTEM_KEY = "clock_notation";
  public static final String SOLVE_TYPES_SHORTCUT_KEY = "solve_types_shortcut";
  public static final String RECORD_NOTIFICATION_MODE_KEY = "record_notification_mode";
  public static final String CROSS_NEUTRALITY_KEY = "cross_neutrality";
  public static final String CROSS_FACE_KEY = "cross_face";
  public static final String LAST_LAYER_FACE_KEY = "last_layer_face";
  public static final String BREAKDOWN_SHOW_MOVES_KEY = "breakdown_show_moves";
  public static final String HERO_STAT_KEY_PREFIX = "hero_stat_";
  public static final String REPLAY_SHOW_GYRO_KEY = "replay_show_gyro";
  public static final String DRILL_FOLLOW_GYRO_KEY = "drill_follow_gyro";
  public static final String SCRAMBLE_VIEW_3D_KEY = "scramble_view_3d";
  public static final String SMART_CUBE_INTRO_SEEN_KEY = "smart_cube_intro_seen";
  public static final String LAST_SOLVE_HINT_SEEN_KEY = "last_solve_hint_seen";
  public static final String LAST_SOLVE_BUTTON_SET_KEY = "last_solve_button_set";
  public static final String SMART_CUBE_AUTO_CONNECT_KEY = "smart_cube_auto_connect";
  public static final String SMART_CUBE_LAST_ID_KEY = "smart_cube_last_id";
  public static final String SMART_CUBE_METHOD_KEY = "smart_cube_method";
  public static final String SMART_CUBE_METHOD_ASKED_KEY = "smart_cube_method_asked";
  public static final String SMART_CUBE_EDGE_BUFFER_KEY = "smart_cube_edge_buffer";
  public static final String SMART_CUBE_CORNER_BUFFER_KEY = "smart_cube_corner_buffer";
  public static final String SMART_CUBE_OP_EDGE_BUFFER_KEY = "smart_cube_op_edge_buffer";
  public static final String SMART_CUBE_OP_CORNER_BUFFER_KEY = "smart_cube_op_corner_buffer";
  public static final String SMART_CUBE_BLIND_METHOD_KEY = "smart_cube_blind_method";
  public static final String SMART_CUBE_UP_FACE_KEY = "smart_cube_blind_up_face";
  public static final String SMART_CUBE_FRONT_FACE_KEY = "smart_cube_blind_front_face";
  public static final String SMART_CUBE_BLIND_ASKED_KEY = "smart_cube_blind_asked";
  public static final String SMART_CUBE_AUTO_STOP_KEY = "smart_cube_auto_stop";
  public static final String SMART_CUBE_AUTO_PENALTY_KEY = "smart_cube_auto_penalty";
  public static final String DRILL_PAUSE_KEY = "drill_pause";
  public static final String SMART_CUBE_OFFSET_KEY_PREFIX = "smart_cube_offset_";
  public static final String DRILL_CHOICE_KEY_PREFIX = "drill_choice_";
  private static final String ANALYSIS_WINDOW_KEY = "analysisWindow";
  private static final String COACH_PENDING_KEY_PREFIX = "coach_pending_";
  private static final String COACH_PENDING_AT_KEY_PREFIX = "coach_pending_at_";
  private static final String COACH_PRACTICE_KEY_PREFIX = "coach_practice_";
  private static final String COACH_PRACTICE_AT_KEY_PREFIX = "coach_practice_at_";
  private static final String COACH_PRACTICE_WITH_KEY_PREFIX = "coach_practice_with_";
  private static final String COACH_DAILY_KEY_PREFIX = "coach_daily_";
  private static final String COACH_DAILY_AT_KEY_PREFIX = "coach_daily_at_";
  private static final String COACH_DAILY_PROGRESS_KEY_PREFIX = "coach_daily_progress_";
  private static final String LEARN_NEXT_COUNT_KEY = "learn_next_count";
  private static final int DEFAULT_LEARN_NEXT_COUNT = 3;
  private static final String COACH_ID_KEY = "coach_id";
  private static final String COACH_PLANS_ON_KEY = "coach_plans_on";
  private static final String COACH_PLANS_SOLVE_TYPE_KEY = "coach_plans_solve_type";
  private static final String COACH_LAST_PLAN_KEY = "coach_last_plan_at";
  private static final String COACH_ASK_AGAIN_KEY = "coach_ask_again_at";
  private static final String COACH_NUDGE_ON_KEY = "coach_nudge_on";
  private static final String COACH_LAST_NUDGE_KEY = "coach_last_nudge_at";
  public static final String COACH_NOTIFICATIONS_ON_KEY = "coach_notifications_on";
  public static final String COACH_PLAN_READY_ON_KEY = "coach_plan_ready_on";
  public static final String COACH_RECAP_ON_KEY = "coach_recap_on";
  public static final String COACH_RECAP_TIME_KEY = "coach_recap_time";
  public static final int COACH_RECAP_DEFAULT_MINUTES = 19 * 60;
  private static final String COACH_LAST_RECAP_KEY = "coach_last_recap_day";
  private static final String COACH_FEEDBACK_CHOICE_KEY = "coach_feedback_choice";
  private static final String COACH_PLAN_LANGUAGE_KEY = "coach_plan_language";
  public static final String CASE_KNOWLEDGE_RULE_KEY = "case_knowledge_rule";
  public static final String CFOP_READING_KEY = "cfop_reading_version";
  public static final String DRILL_CASES_KEY_PREFIX = "drill_cases_";
  private static final String LEARN_PICK_KEY_PREFIX = "learn_pick_";
  private static final String LEARN_PICK_CLEANED_KEY = "learn_pick_cleaned";
  public static final String CASE_ALGORITHM_KEY_PREFIX = "case_alg_";
  private static final String LEARNED_CASES_KEY = "learned_cases";
  private static final String LEARNED_PAIR_CASES_KEY = "learned_pair_cases";
  private static final String KEPT_ALGORITHM_CASES_KEY = "kept_algorithm_cases";
  private static final String CELEBRATED_GROUPS_KEY = "celebrated_groups";
  public static final String DRILL_PRESETS_KEY_PREFIX = "drill_case_presets_";
  public static final String CASE_OWN_ALGORITHM_KEY_PREFIX = "case_own_alg_";
  public static final String CASE_ALGORITHM_TIMES_KEY_PREFIX = "case_alg_times_";
  public static final String CASE_MUTED_ALGORITHMS_KEY_PREFIX = "case_muted_algs_";

  private static final int MAX_STEPS_COUNT = 8;

  public void setContext(Context context) {
    this.context = context;
    this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
  }

  public int getMaxStepsCount() {
    return MAX_STEPS_COUNT;
  }

  public InspectionMode getInspectionMode() {
    int mode = Integer.parseInt(sharedPreferences.getString(INSPECTION_MODE_KEY, "-1"));
    switch (mode) {
      case 1:
        return InspectionMode.HOLD_AND_RELEASE;
      case 2:
        return InspectionMode.AUTOMATIC;
      case 3:
        return InspectionMode.OFFICIAL;
      default:
        // nothing stored: a new install, older ones were pinned to hold and release on upgrade
        return InspectionMode.AUTOMATIC;
    }
  }

  public int getInspectionTime() {
    Integer defaultValue = context.getResources().getInteger(R.integer.inspection_time);
    return sharedPreferences.getInt(INSPECTION_TIME_KEY, defaultValue);
  }

  public boolean isInspectionSoundsEnabled() {
    Boolean defaultValue = context.getResources().getBoolean(R.bool.inspection_sounds);
    return sharedPreferences.getBoolean(INSPECTION_SOUNDS_KEY, defaultValue);
  }

  public InspectionSoundsType getInspectionSoundsType() {
    int type = Integer.parseInt(sharedPreferences.getString(INSPECTION_SOUNDS_TYPE_KEY, "-1"));
    switch (type) {
      case 1:
        return InspectionSoundsType.CLASSIC;
      case 2:
        return InspectionSoundsType.OFFICIAL;
      default:
        return InspectionSoundsType.CLASSIC;
    }
  }

  public boolean isShowTimeWhenRunning() {
      Boolean defaultValue = context.getResources().getBoolean(R.bool.show_time_when_running);
      return sharedPreferences.getBoolean(SHOW_TIME_WHEN_RUNNING, defaultValue);
  }

  public TimerFont getTimerFont() {
    if (sharedPreferences == null) {
      return TimerFont.getDefault();
    }
    String value = sharedPreferences.getString(TIMER_FONT_KEY, String.valueOf(TimerFont.getDefault().getId()));
    try {
      return TimerFont.fromId(Integer.parseInt(value));
    } catch (NumberFormatException e) {
      return TimerFont.getDefault();
    }
  }

  public TimerFontSize getTimerFontSize() {
    if (sharedPreferences == null) {
      return TimerFontSize.MEDIUM;
    }
    int size = Integer.parseInt(sharedPreferences.getString(TIMER_FONT_SIZE_KEY, "-1"));
    switch (size) {
      case 1:
        return TimerFontSize.SMALL;
      case 2:
        return TimerFontSize.MEDIUM;
      case 3:
        return TimerFontSize.LARGE;
      default:
        return TimerFontSize.MEDIUM;
    }
  }

  public float getTimerFontSizeScale() {
    switch (getTimerFontSize()) {
      case SMALL:
        return 0.9f;
      case LARGE:
        return 1.3f;
      default:
        return 1.1f;
    }
  }

  public boolean isKeepTimerScreenOnWhenTimerOff() {
    Boolean defaultValue = context.getResources().getBoolean(R.bool.keep_timer_screen_on);
    return sharedPreferences.getBoolean(KEEP_TIMER_SCREEN_ON_KEY, defaultValue);
  }

  public boolean isUsingHighPrecisionTimer() {
    Boolean defaultValue = context.getResources().getBoolean(R.bool.high_precision_timer);
    return sharedPreferences.getBoolean(HIGH_PRECISION_TIMER_KEY, defaultValue);
  }

  public int getColorSampleSize() {
    Integer defaultValue = context.getResources().getInteger(R.integer.color_sample_size);
    return sharedPreferences.getInt(COLOR_SAMPLE_SIZE_KEY, defaultValue);
  }

  public SessionTimesDisplay getSessionTimesDisplay() {
    int mode = Integer.parseInt(sharedPreferences.getString(SESSION_TIMES_DISPLAY_KEY, "-1"));
    return mode == 2 ? SessionTimesDisplay.TIMES : SessionTimesDisplay.BARS;
  }

  public StatePreview getStatePreview() {
    switch (Integer.parseInt(sharedPreferences.getString(STATE_PREVIEW_KEY, "-1"))) {
      case 2:
        return StatePreview.NET;
      case 3:
        return StatePreview.NONE;
      default:
        return StatePreview.CUBE;
    }
  }

  public BigCubesNotation getBigCubesNotation() {
    int notation = Integer.parseInt(sharedPreferences.getString(BIG_CUBES_NOTATION_KEY, "-1"));
    switch (notation) {
      case 1:
        return BigCubesNotation.RUF;
      case 2:
        return BigCubesNotation.RWUWFW;
      default:
        return BigCubesNotation.RUF;
    }
  }

  public RecordNotificationMode getRecordNotificationMode() {
    int mode = Integer.parseInt(sharedPreferences.getString(RECORD_NOTIFICATION_MODE_KEY, "-1"));
    switch (mode) {
      case 1:
        return RecordNotificationMode.ANY;
      case 2:
        return RecordNotificationMode.PB_ONLY;
      case 3:
        return RecordNotificationMode.NEVER;
      default:
        return RecordNotificationMode.PB_ONLY;
    }
  }

  public boolean isSolveTypesShortcutEnabled() {
    Boolean defaultValue = context.getResources().getBoolean(R.bool.solve_types_shortcut);
    return sharedPreferences.getBoolean(SOLVE_TYPES_SHORTCUT_KEY, defaultValue);
  }

  public ClockNotation getClockNotation() {
    int notation = Integer.parseInt(sharedPreferences.getString(CLOCK_NOTATION_SYSTEM_KEY, "-1"));
    switch (notation) {
      case 1:
        return ClockNotation.UUdU_x_x;
      case 2:
        return ClockNotation.UUdd_ux_dx;
      case 3:
        return ClockNotation.URx_DRx_DLx;
      default:
        // Modern WCA notation: the standard, and the only one the scramble diagram
        // can render (see ScrambleViewNotation). Matches @integer/clock_notation.
        return ClockNotation.URx_DRx_DLx;
    }
  }

  public CrossNeutrality getCrossNeutrality() {
    int ordinal = sharedPreferences.getInt(CROSS_NEUTRALITY_KEY, CrossNeutrality.SPECIFIC.ordinal());
    CrossNeutrality[] values = CrossNeutrality.values();
    return (ordinal >= 0 && ordinal < values.length) ? values[ordinal] : CrossNeutrality.SPECIFIC;
  }

  public void setCrossNeutrality(CrossNeutrality neutrality) {
    sharedPreferences.edit().putInt(CROSS_NEUTRALITY_KEY, neutrality.ordinal()).apply();
  }

  // The cross face is stored as an index into the cross face enum (default D), kept here as an int
  // to avoid coupling Options to the scrambler package.
  public int getCrossFaceIndex(int defaultIndex) {
    return sharedPreferences.getInt(CROSS_FACE_KEY, defaultIndex);
  }

  public void setCrossFaceIndex(int faceIndex) {
    sharedPreferences.edit().putInt(CROSS_FACE_KEY, faceIndex).apply();
  }

  /** The face whose colour the solver finishes on, as its letter: U (white) unless they chose. */
  public String getLastLayerFace() {
    return sharedPreferences.getString(LAST_LAYER_FACE_KEY, "U");
  }

  public void setLastLayerFace(String face) {
    sharedPreferences.edit().putString(LAST_LAYER_FACE_KEY, face).apply();
  }

  /**
   * What the drill selection screen was left set to. Loose values rather than named settings: they
   * are one screen's memory of the last drill picked, and nothing else reads them.
   */
  public int getDrillChoice(String key, int defaultValue) {
    return sharedPreferences.getInt(DRILL_CHOICE_KEY_PREFIX + key, defaultValue);
  }

  public void setDrillChoice(String key, int value) {
    sharedPreferences.edit().putInt(DRILL_CHOICE_KEY_PREFIX + key, value).apply();
  }

  /** Which window the Analysis hub was last read in, by ordinal. Its own memory, like the above. */
  public int getAnalysisWindow(int defaultValue) {
    return sharedPreferences.getInt(ANALYSIS_WINDOW_KEY, defaultValue);
  }

  public void setAnalysisWindow(int ordinal) {
    sharedPreferences.edit().putInt(ANALYSIS_WINDOW_KEY, ordinal).apply();
  }

  /** The plan request a solve type sent and never got a plan back for, or null. */
  public String getCoachPendingRequest(int solveTypeId) {
    return sharedPreferences.getString(COACH_PENDING_KEY_PREFIX + solveTypeId, null);
  }

  /** When that request was kept, 0 for none or for one kept before its time was. */
  public long getCoachPendingRequestAt(int solveTypeId) {
    return sharedPreferences.getLong(COACH_PENDING_AT_KEY_PREFIX + solveTypeId, 0);
  }

  /** Written with commit, not apply: it has to be on disk before the request leaves the device. */
  public void setCoachPendingRequest(int solveTypeId, String requestBody) {
    SharedPreferences.Editor editor = sharedPreferences.edit();
    if (requestBody == null) {
      editor.remove(COACH_PENDING_KEY_PREFIX + solveTypeId);
      editor.remove(COACH_PENDING_AT_KEY_PREFIX + solveTypeId);
    } else {
      editor.putString(COACH_PENDING_KEY_PREFIX + solveTypeId, requestBody);
      editor.putLong(COACH_PENDING_AT_KEY_PREFIX + solveTypeId, System.currentTimeMillis());
    }
    editor.commit();
  }

  /** The practice sets the coach last answered with for a solve type, as it sent them, or null. */
  public String getCoachPractice(int solveTypeId) {
    return sharedPreferences.getString(COACH_PRACTICE_KEY_PREFIX + solveTypeId, null);
  }

  /** When the kept practice sets were asked for, or 0 for never. */
  public long getCoachPracticeAt(int solveTypeId) {
    return sharedPreferences.getLong(COACH_PRACTICE_AT_KEY_PREFIX + solveTypeId, 0);
  }

  /** What the kept practice sets were asked with: the cleared and drilled cases, or null. */
  public String getCoachPracticeWith(int solveTypeId) {
    return sharedPreferences.getString(COACH_PRACTICE_WITH_KEY_PREFIX + solveTypeId, null);
  }

  public void setCoachPractice(int solveTypeId, String reply, long askedAt, String askedWith) {
    sharedPreferences.edit()
        .putString(COACH_PRACTICE_KEY_PREFIX + solveTypeId, reply)
        .putLong(COACH_PRACTICE_AT_KEY_PREFIX + solveTypeId, askedAt)
        .putString(COACH_PRACTICE_WITH_KEY_PREFIX + solveTypeId, askedWith)
        .apply();
  }

  /** Today's daily drill as the coach last answered it for a solve type, or null. */
  public String getCoachDaily(int solveTypeId) {
    return sharedPreferences.getString(COACH_DAILY_KEY_PREFIX + solveTypeId, null);
  }

  /** When the kept daily drill was asked for, or 0 for never. */
  public long getCoachDailyAt(int solveTypeId) {
    return sharedPreferences.getLong(COACH_DAILY_AT_KEY_PREFIX + solveTypeId, 0);
  }

  public void setCoachDaily(int solveTypeId, String reply, long askedAt) {
    sharedPreferences.edit()
        .putString(COACH_DAILY_KEY_PREFIX + solveTypeId, reply)
        .putLong(COACH_DAILY_AT_KEY_PREFIX + solveTypeId, askedAt)
        .remove(COACH_DAILY_PROGRESS_KEY_PREFIX + solveTypeId)
        .apply();
  }

  /** How far the kept daily drill has been run, as DailyRun wrote it; null before its first rep. */
  public String getCoachDailyProgress(int solveTypeId) {
    return sharedPreferences.getString(COACH_DAILY_PROGRESS_KEY_PREFIX + solveTypeId, null);
  }

  public void setCoachDailyProgress(int solveTypeId, String progress) {
    sharedPreferences.edit().putString(COACH_DAILY_PROGRESS_KEY_PREFIX + solveTypeId, progress)
        .apply();
  }

  /** How many OLLs the "learn next" practice set shows, as the solver picked it. */
  public int getLearnNextCount() {
    return sharedPreferences.getInt(LEARN_NEXT_COUNT_KEY, DEFAULT_LEARN_NEXT_COUNT);
  }

  public void setLearnNextCount(int count) {
    sharedPreferences.edit().putInt(LEARN_NEXT_COUNT_KEY, count).apply();
  }

  /** The id the coach service knows this install by, made the first time it is read. */
  public synchronized String getCoachId() {
    String id = sharedPreferences.getString(COACH_ID_KEY, null);
    if (id == null) {
      id = UUID.randomUUID().toString();
      sharedPreferences.edit().putString(COACH_ID_KEY, id).commit();
    }
    return id;
  }

  /** Whether the solver agreed to weekly plans, which are asked for in the background. */
  public boolean isCoachPlansOn() {
    return sharedPreferences.getBoolean(COACH_PLANS_ON_KEY, false);
  }

  public void setCoachPlansOn(boolean on) {
    sharedPreferences.edit().putBoolean(COACH_PLANS_ON_KEY, on).apply();
  }

  /** The id of the one solve type the weekly plans are written for, or -1 when there is none. */
  public int getCoachPlansSolveType() {
    return sharedPreferences.getInt(COACH_PLANS_SOLVE_TYPE_KEY, -1);
  }

  public void setCoachPlansSolveType(int solveTypeId) {
    sharedPreferences.edit().putInt(COACH_PLANS_SOLVE_TYPE_KEY, solveTypeId).apply();
  }

  /**
   * When the last plan was written, 0 when none was. Kept apart from the plan itself so that
   * moving the plans to another solve type, or deleting the one they were for, keeps the week.
   */
  public long getCoachLastPlanAt() {
    return sharedPreferences.getLong(COACH_LAST_PLAN_KEY, 0);
  }

  /** Written with commit, not apply: a lost write would ask for a second plan in the same week. */
  public void setCoachLastPlanAt(long writtenAt) {
    sharedPreferences.edit().putLong(COACH_LAST_PLAN_KEY, writtenAt).commit();
  }

  /** Until when the weekly job leaves the coach alone after a refusal, 0 when it was not refused. */
  public long getCoachAskAgainAt() {
    return sharedPreferences.getLong(COACH_ASK_AGAIN_KEY, 0);
  }

  public void setCoachAskAgainAt(long askAgainAt) {
    sharedPreferences.edit().putLong(COACH_ASK_AGAIN_KEY, askAgainAt).apply();
  }

  /** Whether to remind the solver, some days after a plan, of the solves the next one needs. */
  public boolean isCoachNudgeOn() {
    return isCoachNotificationsOn() && sharedPreferences.getBoolean(COACH_NUDGE_ON_KEY, true);
  }

  /** The switch over every coach notification. */
  public boolean isCoachNotificationsOn() {
    return sharedPreferences.getBoolean(COACH_NOTIFICATIONS_ON_KEY, true);
  }

  /** Whether to say a new plan is ready. */
  public boolean isCoachPlanReadyOn() {
    return isCoachNotificationsOn()
        && sharedPreferences.getBoolean(COACH_PLAN_READY_ON_KEY, true);
  }

  /** Whether to send the evening recap of a day with solves. */
  public boolean isCoachRecapOn() {
    return isCoachNotificationsOn() && sharedPreferences.getBoolean(COACH_RECAP_ON_KEY, true);
  }

  /** When the recap is sent, in minutes after local midnight. */
  public int getCoachRecapMinutes() {
    return sharedPreferences.getInt(COACH_RECAP_TIME_KEY, COACH_RECAP_DEFAULT_MINUTES);
  }

  public void setCoachRecapMinutes(int minutes) {
    sharedPreferences.edit().putInt(COACH_RECAP_TIME_KEY, minutes).apply();
  }

  /** The local day of the last recap sent, as yyyymmdd, 0 when never. */
  public int getCoachLastRecapDay() {
    return sharedPreferences.getInt(COACH_LAST_RECAP_KEY, 0);
  }

  public void setCoachLastRecapDay(int day) {
    sharedPreferences.edit().putInt(COACH_LAST_RECAP_KEY, day).apply();
  }

  /** When the solver was last reminded, 0 when never. */
  public long getCoachLastNudgeAt() {
    return sharedPreferences.getLong(COACH_LAST_NUDGE_KEY, 0);
  }

  public void setCoachLastNudgeAt(long nudgedAt) {
    sharedPreferences.edit().putLong(COACH_LAST_NUDGE_KEY, nudgedAt).apply();
  }

  /** Whether the solver chose to see the plan feedback, null when they never chose. */
  public Boolean getCoachFeedbackChoice() {
    return sharedPreferences.contains(COACH_FEEDBACK_CHOICE_KEY)
        ? sharedPreferences.getBoolean(COACH_FEEDBACK_CHOICE_KEY, false) : null;
  }

  public void setCoachFeedbackChoice(boolean shown) {
    sharedPreferences.edit().putBoolean(COACH_FEEDBACK_CHOICE_KEY, shown).apply();
  }

  /** The language plans are written in, null for the app's own. */
  public String getCoachPlanLanguage() {
    return sharedPreferences.getString(COACH_PLAN_LANGUAGE_KEY, null);
  }

  public void setCoachPlanLanguage(String code) {
    sharedPreferences.edit().putString(COACH_PLAN_LANGUAGE_KEY, code).apply();
  }

  /**
   * Which cases of a family a drill runs, or null for every one of them. Null and "all of them"
   * are deliberately the same thing: a user who has never opened the picker gets the whole set, and
   * one who ticks every box is not then pinned to the 57 OLLs that existed the day they ticked it.
   *
   * @param family the case code prefix, {@code "oll_"} or {@code "pll_"}
   */
  public Set<String> getDrillCases(String family) {
    return getCodes(DRILL_CASES_KEY_PREFIX + family);
  }

  /** @param cases the picked cases, or null to go back to every case in the family */
  public void setDrillCases(String family, Set<String> cases) {
    setCodes(DRILL_CASES_KEY_PREFIX + family, cases);
  }

  /**
   * The cases the user chose to learn next in place of the coach's suggestion, or null while they
   * follow the suggestion. A case leaves the pick when a learning drill clears it
   * ({@link #dropFromLearnPick}), so a known case picked on purpose stays until it is cleared again.
   *
   * @param family the case code prefix, {@code "oll_"} or {@code "pll_"}
   */
  public Set<String> getLearnPick(String family) {
    if (!sharedPreferences.getBoolean(LEARN_PICK_CLEANED_KEY, false)) {
      // Picks stored before cleared cases left them still hold those cases: dropped once.
      for (String each : new String[] { "oll_", "pll_" }) {
        setCodes(LEARN_PICK_KEY_PREFIX + each,
            withoutLearned(getCodes(LEARN_PICK_KEY_PREFIX + each), getLearnedCases()));
      }
      sharedPreferences.edit().putBoolean(LEARN_PICK_CLEANED_KEY, true).apply();
    }
    return getCodes(LEARN_PICK_KEY_PREFIX + family);
  }

  /** Takes a case just cleared in a learning drill out of its family's own pick. */
  public void dropFromLearnPick(String caseCode) {
    String family = caseCode.substring(0, caseCode.indexOf('_') + 1);
    Set<String> pick = getLearnPick(family);
    if (pick != null && pick.contains(caseCode)) {
      setLearnPick(family, withoutLearned(pick, Collections.singleton(caseCode)));
    }
  }

  /** The pick without the learned cases, or null when nothing is left of it. */
  static Set<String> withoutLearned(Set<String> pick, Set<String> learned) {
    if (pick == null) {
      return null;
    }
    Set<String> left = new LinkedHashSet<String>(pick);
    left.removeAll(learned);
    return left.isEmpty() ? null : left;
  }

  /** @param cases the chosen cases, or null to follow the suggestion again */
  public void setLearnPick(String family, Set<String> cases) {
    setCodes(LEARN_PICK_KEY_PREFIX + family, cases);
  }

  private Set<String> getCodes(String key) {
    String stored = sharedPreferences.getString(key, null);
    if (stored == null) {
      return null;
    }
    Set<String> cases = new LinkedHashSet<String>();
    for (String code : stored.split(",")) {
      if (!code.isEmpty()) {
        cases.add(code);
      }
    }
    return cases;
  }

  private void setCodes(String key, Set<String> cases) {
    if (cases == null) {
      sharedPreferences.edit().remove(key).apply();
      return;
    }
    StringBuilder stored = new StringBuilder();
    for (String code : cases) {
      if (stored.length() > 0) {
        stored.append(',');
      }
      stored.append(code);
    }
    sharedPreferences.edit().putString(key, stored.toString()).apply();
  }

  /**
   * The picks of a family the user has given a name to, in the order they saved them. A preset is
   * only ever a copy of a pick: it is applied by ticking what it holds, so nothing downstream of the
   * picker has to know that presets exist at all.
   *
   * @param family the case code prefix, {@code "oll_"} or {@code "pll_"}
   */
  public List<DrillCasePreset> getDrillCasePresets(String family) {
    return DrillCasePreset.fromJson(
        sharedPreferences.getString(DRILL_PRESETS_KEY_PREFIX + family, null));
  }

  public void setDrillCasePresets(String family, List<DrillCasePreset> presets) {
    String key = DRILL_PRESETS_KEY_PREFIX + family;
    if (presets.isEmpty()) {
      sharedPreferences.edit().remove(key).apply();
      return;
    }
    sharedPreferences.edit().putString(key, DrillCasePreset.toJson(presets)).apply();
  }

  /**
   * The algorithm the user solves a case with, or null while they have not said. Kept whether it
   * came off the list or was typed in, since which of those it was stops mattering the moment it is
   * theirs: what the app owes them afterwards is the one they use, not the one most people use.
   *
   * @param caseCode the case as a solve records it, {@code "oll_21"} or {@code "pll_ga"}
   */
  public String getCaseAlgorithm(String caseCode) {
    return sharedPreferences.getString(CASE_ALGORITHM_KEY_PREFIX + caseCode, null);
  }

  /** The cases cleared in a learning drill, which count as learned for a shape group's completion. */
  public Set<String> getLearnedCases() {
    return new LinkedHashSet<String>(
        sharedPreferences.getStringSet(LEARNED_CASES_KEY, new LinkedHashSet<String>()));
  }

  public void addLearnedCase(String caseCode) {
    Set<String> learned = getLearnedCases();
    if (learned.add(caseCode)) {
      sharedPreferences.edit().putStringSet(LEARNED_CASES_KEY, learned).apply();
    }
  }

  /**
   * The F2L pairs, back-slot ones included, cleared in a learning drill. Kept apart from
   * {@link #getLearnedCases}, which the coach's learn order reads and which holds no pair.
   */
  public Set<String> getLearnedPairCases() {
    return new LinkedHashSet<String>(
        sharedPreferences.getStringSet(LEARNED_PAIR_CASES_KEY, new LinkedHashSet<String>()));
  }

  public void addLearnedPairCase(String caseCode) {
    Set<String> learned = getLearnedPairCases();
    if (learned.add(caseCode)) {
      sharedPreferences.edit().putStringSet(LEARNED_PAIR_CASES_KEY, learned).apply();
    }
  }

  /**
   * Whether the case's algorithm was kept after trying it on the cube, with "Use this one". A star
   * set in the case popup alone does not count, so a learning drill still asks for that case.
   */
  public boolean isAlgorithmKept(String caseCode) {
    return sharedPreferences.getStringSet(KEPT_ALGORITHM_CASES_KEY, new LinkedHashSet<String>())
        .contains(caseCode);
  }

  public void addAlgorithmKept(String caseCode) {
    Set<String> kept = new LinkedHashSet<String>(
        sharedPreferences.getStringSet(KEPT_ALGORITHM_CASES_KEY, new LinkedHashSet<String>()));
    if (kept.add(caseCode)) {
      sharedPreferences.edit().putStringSet(KEPT_ALGORITHM_CASES_KEY, kept).apply();
    }
  }

  /** Whether a shape group's completion has been celebrated, so it is celebrated once. */
  public boolean isGroupCelebrated(String group) {
    return sharedPreferences.getStringSet(CELEBRATED_GROUPS_KEY, new LinkedHashSet<String>())
        .contains(group);
  }

  public void setGroupCelebrated(String group) {
    Set<String> groups = new LinkedHashSet<String>(
        sharedPreferences.getStringSet(CELEBRATED_GROUPS_KEY, new LinkedHashSet<String>()));
    groups.add(group);
    sharedPreferences.edit().putStringSet(CELEBRATED_GROUPS_KEY, groups).apply();
  }

  /** @param algorithm the algorithm they use, or null to go back to having no preference */
  public void setCaseAlgorithm(String caseCode, String algorithm) {
    String key = CASE_ALGORITHM_KEY_PREFIX + caseCode;
    if (algorithm == null) {
      sharedPreferences.edit().remove(key).apply();
    } else {
      sharedPreferences.edit().putString(key, algorithm).apply();
    }
  }

  /**
   * An algorithm the user typed in for a case, kept whether or not it is the one they are currently
   * using. Separate from the choice on purpose: typing an algorithm in is work, and trying one of
   * the listed ones for a week should not be a way to lose it.
   */
  public String getOwnCaseAlgorithm(String caseCode) {
    return sharedPreferences.getString(CASE_OWN_ALGORITHM_KEY_PREFIX + caseCode, null);
  }

  public void setOwnCaseAlgorithm(String caseCode, String algorithm) {
    String key = CASE_OWN_ALGORITHM_KEY_PREFIX + caseCode;
    if (algorithm == null) {
      sharedPreferences.edit().remove(key).apply();
    } else {
      sharedPreferences.edit().putString(key, algorithm).apply();
    }
  }

  /** Each algorithm's last execution times for a case, as {@code AlgorithmTimes} writes them. */
  public String getCaseAlgorithmTimes(String caseCode) {
    return sharedPreferences.getString(CASE_ALGORITHM_TIMES_KEY_PREFIX + caseCode, null);
  }

  public void setCaseAlgorithmTimes(String caseCode, String times) {
    sharedPreferences.edit().putString(CASE_ALGORITHM_TIMES_KEY_PREFIX + caseCode, times).apply();
  }

  /**
   * The algorithms of a case the user asked not to be flagged for turning, as they turned them,
   * oldest first. A list rather than one, since a case can have more than one answer.
   */
  public List<String> getMutedCaseAlgorithms(String caseCode) {
    String stored = sharedPreferences.getString(CASE_MUTED_ALGORITHMS_KEY_PREFIX + caseCode, null);
    return stored == null || stored.isEmpty() ? new ArrayList<String>()
        : new ArrayList<String>(Arrays.asList(stored.split("\n")));
  }

  public void setMutedCaseAlgorithms(String caseCode, List<String> algorithms) {
    String key = CASE_MUTED_ALGORITHMS_KEY_PREFIX + caseCode;
    if (algorithms.isEmpty()) {
      sharedPreferences.edit().remove(key).apply();
    } else {
      sharedPreferences.edit().putString(key, TextUtils.join("\n", algorithms)).apply();
    }
  }

  /**
   * Which statistic one of the history card's three cells shows. Set by tapping the cell rather
   * than from the preference screen, and kept per kind of solve type: a blind card wants its
   * success rate where a sighted one wants an average, and neither should follow the other.
   *
   * @param cell 0, 1 or 2, left to right
   */
  public HeroStat getHeroStat(int cell, boolean blind) {
    String stored = sharedPreferences.getString(heroStatKey(cell, blind), null);
    if (stored != null) {
      try {
        return HeroStat.valueOf(stored);
      } catch (IllegalArgumentException e) {
        // A statistic that no longer exists: fall through to the default rather than crash.
      }
    }
    return HeroStat.defaultFor(cell, blind);
  }

  public void setHeroStat(int cell, boolean blind, HeroStat stat) {
    sharedPreferences.edit().putString(heroStatKey(cell, blind), stat.name()).apply();
  }

  private String heroStatKey(int cell, boolean blind) {
    return HERO_STAT_KEY_PREFIX + (blind ? "blind_" : "") + cell;
  }

  // Whether the solve breakdown shows the moves of each step. Not a preference screen entry: it is
  // set by the switch on the breakdown itself, and kept so the dialog reopens the way it was left.
  public boolean isBreakdownShowMoves() {
    return sharedPreferences.getBoolean(BREAKDOWN_SHOW_MOVES_KEY, true);
  }

  public void setBreakdownShowMoves(boolean showMoves) {
    sharedPreferences.edit().putBoolean(BREAKDOWN_SHOW_MOVES_KEY, showMoves).apply();
  }

  /**
   * Whether the scramble dialog opens on the 3D cube rather than the flat net. Kept because the
   * choice is a habit rather than a per-solve decision: a blind solver who reads scrambles on a
   * cube they can turn to their own front face wants that view every time, and picking it again
   * on every solve is the friction this is here to remove. Off by default — the net shows all six
   * faces at once, which is what somebody checking they scrambled right is usually after.
   */
  public boolean isScrambleView3d() {
    return sharedPreferences.getBoolean(SCRAMBLE_VIEW_3D_KEY, false);
  }

  public void setScrambleView3d(boolean threeD) {
    sharedPreferences.edit().putBoolean(SCRAMBLE_VIEW_3D_KEY, threeD).apply();
  }

  /**
   * Whether a replay shows how the cube was physically held. Off by default: the square cube is
   * what the stored rotations say, and a solve without a gyro track can only be shown that way.
   */
  public boolean isReplayShowGyro() {
    return sharedPreferences.getBoolean(REPLAY_SHOW_GYRO_KEY, false);
  }

  public void setReplayShowGyro(boolean showGyro) {
    sharedPreferences.edit().putBoolean(REPLAY_SHOW_GYRO_KEY, showGyro).apply();
  }

  /**
   * Whether a drill's drawn cube follows the grip. On by default, which is what a drill screen is:
   * a mirror of the cube in the user's hands. Off, it stands at its corner to be turned by hand,
   * the way a cube with no gyroscope to follow is drilled.
   */
  public boolean isDrillFollowGyro() {
    return sharedPreferences.getBoolean(DRILL_FOLLOW_GYRO_KEY, true);
  }

  public void setDrillFollowGyro(boolean follow) {
    sharedPreferences.edit().putBoolean(DRILL_FOLLOW_GYRO_KEY, follow).apply();
  }

  // Whether the smart-cube sheet has already explained itself. It leads with the explanation until
  // it has been read once; the help button on the sheet brings it back afterwards.
  public boolean isSmartCubeIntroSeen() {
    return sharedPreferences.getBoolean(SMART_CUBE_INTRO_SEEN_KEY, false);
  }

  public void setSmartCubeIntroSeen(boolean seen) {
    sharedPreferences.edit().putBoolean(SMART_CUBE_INTRO_SEEN_KEY, seen).apply();
  }

  // Whether the timer has pointed at "Last solve" after a cube-timed solve. Shown once.
  public boolean isLastSolveHintSeen() {
    return sharedPreferences.getBoolean(LAST_SOLVE_HINT_SEEN_KEY, false);
  }

  public void setLastSolveHintSeen(boolean seen) {
    sharedPreferences.edit().putBoolean(LAST_SOLVE_HINT_SEEN_KEY, seen).apply();
  }

  // Solve types whose bar button was set to Last solve at their first cube-timed solve. Once each,
  // so a user who sets it back keeps their choice.
  public boolean isLastSolveButtonSet(int solveTypeId) {
    return sharedPreferences.getStringSet(LAST_SOLVE_BUTTON_SET_KEY, new LinkedHashSet<String>())
        .contains(String.valueOf(solveTypeId));
  }

  public void setLastSolveButtonSet(int solveTypeId) {
    Set<String> ids = new LinkedHashSet<>(
        sharedPreferences.getStringSet(LAST_SOLVE_BUTTON_SET_KEY, new LinkedHashSet<String>()));
    ids.add(String.valueOf(solveTypeId));
    sharedPreferences.edit().putStringSet(LAST_SOLVE_BUTTON_SET_KEY, ids).apply();
  }

  public boolean isSmartCubeAutoConnect() {
    return sharedPreferences.getBoolean(SMART_CUBE_AUTO_CONNECT_KEY, false);
  }

  public void setSmartCubeAutoConnect(boolean autoConnect) {
    sharedPreferences.edit().putBoolean(SMART_CUBE_AUTO_CONNECT_KEY, autoConnect).apply();
  }

  // The platform id of the last cube connected, which auto-connect looks for. Null before any.
  public String getSmartCubeLastId() {
    return sharedPreferences.getString(SMART_CUBE_LAST_ID_KEY, null);
  }

  public void setSmartCubeLastId(String id) {
    sharedPreferences.edit().putString(SMART_CUBE_LAST_ID_KEY, id).apply();
  }

  // The method new solve types are created with, stored as its code. Defaults to the most common
  // one rather than to nothing: reading the method off the solve is not reliable enough to offer.
  public CubeMethod getPreferredMethod() {
    CubeMethod method = CubeMethod.fromCode(sharedPreferences.getString(SMART_CUBE_METHOD_KEY, ""));
    return method == null ? CubeMethod.CFOP : method;
  }

  public void setPreferredMethod(CubeMethod method) {
    sharedPreferences.edit().putString(SMART_CUBE_METHOD_KEY, method.getCode()).apply();
  }

  // Tracked apart from the value itself, which has a default and so cannot say whether it was set.
  public boolean isPreferredMethodAsked() {
    return sharedPreferences.getBoolean(SMART_CUBE_METHOD_ASKED_KEY, false);
  }

  public void setPreferredMethodAsked(boolean asked) {
    sharedPreferences.edit().putBoolean(SMART_CUBE_METHOD_ASKED_KEY, asked).apply();
  }

  /**
   * How blind solve types shoot their targets unless one says otherwise. 3-style until asked, which
   * is what every blind solve was read as before there was a choice.
   */
  public BlindMethod getPreferredBlindMethod() {
    BlindMethod method = sharedPreferences == null ? null
        : BlindMethod.fromCode(sharedPreferences.getString(SMART_CUBE_BLIND_METHOD_KEY, ""));
    return method == null ? BlindMethod.THREE_STYLE : method;
  }

  public void setPreferredBlindMethod(BlindMethod method) {
    sharedPreferences.edit().putString(SMART_CUBE_BLIND_METHOD_KEY, method.getCode()).apply();
  }

  /**
   * The pieces the solver shoots from blindfolded, which is what a blind reconstruction is spelled
   * through: the frame is the one that puts the piece every algorithm shot from at these. Kept per
   * blind method, since each shoots from its own, and asked for once at the first blind solve.
   *
   * <p>Answered without preferences behind it, which is what the reconstruction tests read it
   * through: they exercise the reading and not what the solver was asked.
   */
  public String getBlindEdgeBuffer(BlindMethod method) {
    return getBlindBuffer(method, true);
  }

  public String getBlindCornerBuffer(BlindMethod method) {
    return getBlindBuffer(method, false);
  }

  private String getBlindBuffer(BlindMethod method, boolean edge) {
    String fallback = method == BlindMethod.OP_M2
        ? (edge ? DEFAULT_OP_EDGE_BUFFER : DEFAULT_OP_CORNER_BUFFER)
        : (edge ? DEFAULT_EDGE_BUFFER : DEFAULT_CORNER_BUFFER);
    return sharedPreferences == null ? fallback
        : sharedPreferences.getString(blindBufferKey(method, edge), fallback);
  }

  public void setBlindBuffers(BlindMethod method, String edge, String corner) {
    sharedPreferences.edit()
        .putString(blindBufferKey(method, true), edge)
        .putString(blindBufferKey(method, false), corner)
        .apply();
  }

  private static String blindBufferKey(BlindMethod method, boolean edge) {
    return method == BlindMethod.OP_M2
        ? (edge ? SMART_CUBE_OP_EDGE_BUFFER_KEY : SMART_CUBE_OP_CORNER_BUFFER_KEY)
        : (edge ? SMART_CUBE_EDGE_BUFFER_KEY : SMART_CUBE_CORNER_BUFFER_KEY);
  }

  /**
   * The faces the solver holds up and in front blindfolded, named as the cube reports them rather
   * than by colour: a cube writes its state against its own centres, which no face turn moves, so
   * those labels are a fixed colour and white up red front is {@code U} and {@code R}.
   *
   * <p>Declared rather than measured. It is the whole frame a blind solve's names are spelled in
   * wherever the pieces it shot from cannot settle one themselves, which a solve that stops before
   * its second piece type cannot: see {@code BlindFrame}. Defaults to the orientation the scramble
   * was followed in, and is asked for once at the first blind solve.
   *
   * <p>Answered without preferences behind it, as the buffers are, so the reconstruction tests read
   * the reading and not what the solver was asked.
   */
  public String getBlindUpFace() {
    return face(SMART_CUBE_UP_FACE_KEY, DEFAULT_UP_FACE);
  }

  public String getBlindFrontFace() {
    return face(SMART_CUBE_FRONT_FACE_KEY, DEFAULT_FRONT_FACE);
  }

  // Never empty, so that reading the face off it needs no check of its own.
  private String face(String key, String fallback) {
    String stored = sharedPreferences == null ? null : sharedPreferences.getString(key, fallback);
    return stored == null || stored.isEmpty() ? fallback : stored;
  }

  public void setBlindOrientation(String upFace, String frontFace) {
    sharedPreferences.edit().putString(SMART_CUBE_UP_FACE_KEY, upFace)
        .putString(SMART_CUBE_FRONT_FACE_KEY, frontFace).apply();
  }

  // Tracked apart from the values, which have defaults and so cannot say whether they were set. Its
  // own key rather than the buffers' older one, so a solver who answered that is asked the pair.
  public boolean isBlindSetupAsked() {
    return sharedPreferences.getBoolean(SMART_CUBE_BLIND_ASKED_KEY, false);
  }

  public void setBlindSetupAsked(boolean asked) {
    sharedPreferences.edit().putBoolean(SMART_CUBE_BLIND_ASKED_KEY, asked).apply();
  }

  // Whether the cube reading solved stops the timer by itself. Off leaves the solve to be stopped
  // by a tap, the way a Stackmat is, since the automatic stop lands a shade earlier than a hand.
  public boolean isSmartCubeAutoStop() {
    Boolean defaultValue = context.getResources().getBoolean(R.bool.smart_cube_auto_stop);
    return sharedPreferences.getBoolean(SMART_CUBE_AUTO_STOP_KEY, defaultValue);
  }

  // Whether the state the cube was left in adds the +2 or the DNF that state earns.
  public boolean isSmartCubeAutoPenalty() {
    Boolean defaultValue = context.getResources().getBoolean(R.bool.smart_cube_auto_penalty);
    return sharedPreferences.getBoolean(SMART_CUBE_AUTO_PENALTY_KEY, defaultValue);
  }

  // The pause after a drill rep before the next case, in ms; 0 deals it after the rep's beat.
  public long getDrillPauseMs() {
    try {
      return Long.parseLong(sharedPreferences.getString(DRILL_PAUSE_KEY, "2000"));
    } catch (NumberFormatException e) {
      return 2000;
    }
  }

  // How far a cube's own idea of its state has drifted from the real one, as the facelets of the
  // correction itself. Per cube, and kept across connections because the drift is: it lives in the
  // cube, which reports it again every time it is connected.
  public String getSmartCubeStateOffset(String macAddress) {
    return sharedPreferences.getString(SMART_CUBE_OFFSET_KEY_PREFIX + macAddress, null);
  }

  /** @param offsetFacelets null to drop the correction, for a cube that no longer needs one */
  public void setSmartCubeStateOffset(String macAddress, String offsetFacelets) {
    String key = SMART_CUBE_OFFSET_KEY_PREFIX + macAddress;
    if (offsetFacelets == null) {
      sharedPreferences.edit().remove(key).apply();
    } else {
      sharedPreferences.edit().putString(key, offsetFacelets).apply();
    }
  }

  /** Which reading of the occurrences the stored case statuses were last worked out under. */
  public int getCaseKnowledgeRuleVersion() {
    return sharedPreferences.getInt(CASE_KNOWLEDGE_RULE_KEY, 0);
  }

  public void setCaseKnowledgeRuleVersion(int version) {
    sharedPreferences.edit().putInt(CASE_KNOWLEDGE_RULE_KEY, version).apply();
  }

  /** Which reading of CFOP solves the stored breakdowns were last written under. */
  public int getCfopReadingVersion() {
    return sharedPreferences.getInt(CFOP_READING_KEY, 0);
  }

  public void setCfopReadingVersion(int version) {
    sharedPreferences.edit().putInt(CFOP_READING_KEY, version).apply();
  }

}
