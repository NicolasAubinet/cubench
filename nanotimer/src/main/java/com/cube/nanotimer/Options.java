package com.cube.nanotimer;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.cube.nanotimer.util.view.HeroStat;
import com.cube.nanotimer.util.view.TimerFont;
import com.cube.nanotimer.vo.CubeMethod;
import java.util.LinkedHashSet;
import java.util.Set;

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
  public static final String BREAKDOWN_SHOW_MOVES_KEY = "breakdown_show_moves";
  public static final String HERO_STAT_KEY_PREFIX = "hero_stat_";
  public static final String REPLAY_SHOW_GYRO_KEY = "replay_show_gyro";
  public static final String SCRAMBLE_VIEW_3D_KEY = "scramble_view_3d";
  public static final String SMART_CUBE_INTRO_SEEN_KEY = "smart_cube_intro_seen";
  public static final String SMART_CUBE_METHOD_KEY = "smart_cube_method";
  public static final String SMART_CUBE_METHOD_ASKED_KEY = "smart_cube_method_asked";
  public static final String SMART_CUBE_AUTO_STOP_KEY = "smart_cube_auto_stop";
  public static final String SMART_CUBE_OFFSET_KEY_PREFIX = "smart_cube_offset_";
  public static final String DRILL_CHOICE_KEY_PREFIX = "drill_choice_";
  public static final String DRILL_CASES_KEY_PREFIX = "drill_cases_";
  public static final String CASE_ALGORITHM_KEY_PREFIX = "case_alg_";
  public static final String CASE_OWN_ALGORITHM_KEY_PREFIX = "case_own_alg_";

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

  /**
   * Which cases of a family a drill runs, or null for every one of them. Null and "all of them"
   * are deliberately the same thing: a user who has never opened the picker gets the whole set, and
   * one who ticks every box is not then pinned to the 57 OLLs that existed the day they ticked it.
   *
   * @param family the case code prefix, {@code "oll_"} or {@code "pll_"}
   */
  public Set<String> getDrillCases(String family) {
    String stored = sharedPreferences.getString(DRILL_CASES_KEY_PREFIX + family, null);
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

  /** @param cases the picked cases, or null to go back to every case in the family */
  public void setDrillCases(String family, Set<String> cases) {
    String key = DRILL_CASES_KEY_PREFIX + family;
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
   * The algorithm the user solves a case with, or null while they have not said. Kept whether it
   * came off the list or was typed in, since which of those it was stops mattering the moment it is
   * theirs: what the app owes them afterwards is the one they use, not the one most people use.
   *
   * @param caseCode the case as a solve records it, {@code "oll_21"} or {@code "pll_ga"}
   */
  public String getCaseAlgorithm(String caseCode) {
    return sharedPreferences.getString(CASE_ALGORITHM_KEY_PREFIX + caseCode, null);
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

  // Whether the smart-cube sheet has already explained itself. It leads with the explanation until
  // it has been read once; the help button on the sheet brings it back afterwards.
  public boolean isSmartCubeIntroSeen() {
    return sharedPreferences.getBoolean(SMART_CUBE_INTRO_SEEN_KEY, false);
  }

  public void setSmartCubeIntroSeen(boolean seen) {
    sharedPreferences.edit().putBoolean(SMART_CUBE_INTRO_SEEN_KEY, seen).apply();
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

  // Whether the cube reading solved stops the timer by itself. Off leaves the solve to be stopped
  // by a tap, the way a Stackmat is, since the automatic stop lands a shade earlier than a hand.
  public boolean isSmartCubeAutoStop() {
    Boolean defaultValue = context.getResources().getBoolean(R.bool.smart_cube_auto_stop);
    return sharedPreferences.getBoolean(SMART_CUBE_AUTO_STOP_KEY, defaultValue);
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

}
