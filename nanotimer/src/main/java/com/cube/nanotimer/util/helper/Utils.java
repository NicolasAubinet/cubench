package com.cube.nanotimer.util.helper;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.BatteryManager;
import android.preference.PreferenceManager;
import android.util.Log;
import com.cube.nanotimer.App;
import com.cube.nanotimer.R;
import com.cube.nanotimer.cube.SolveBreakdown;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.ScrambleType;
import com.cube.nanotimer.vo.SolveStep;
import com.cube.nanotimer.vo.SolveType;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class Utils {

  public static final char[] FORBIDDEN_NAME_CHARACTERS = new char[] { '"', ',', ';', '|', '=' };

  private static final String PAIR_CODE_PREFIX = "pair_";

  /** The parts that carry the slot they went into, which is shown as their order instead. */
  private static final String[] SLOT_CODE_PREFIXES = { PAIR_CODE_PREFIX, "corner_", "edge_" };

  /** The steps whose code carries the last-layer case they were left with ("pll_jb", "oll_21"). */
  private static final String[] CASE_CODE_PREFIXES = { "oll_", "pll_" };
  /** The parts of a last layer step: the algorithms that were run, each named by the case it solves
   * ("alg_jb" for a PLL, "ollalg_45" for an OLL). */
  private static final String PERMUTATION_ALGORITHM_PREFIX = "alg_";
  private static final String ORIENTATION_ALGORITHM_PREFIX = "ollalg_";
  private static final String SKIPPED_CASE = "skip";
  private static final String FLIP_CODE_PREFIX = "flip:", TWIST_CODE_PREFIX = "twist:";
  private static final String MEMO_CODE = "memo";

  public static final String LANGUAGE_PREFS_NAME = "language";
  public static final String LANGUAGE_PREF_KEY = "picked";

  public static String parseFloatToString(Float f) {
    return f == null ? null : String.valueOf(f);
  }

  public static String getAppVersion(Context c) {
    try {
      return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
    } catch (NameNotFoundException e) {
      e.printStackTrace();
    }
    return "";
  }

  public static Random getRandom() {
    return new SecureRandom();
  }

  public static CubeType getCurrentCubeType(Context c) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
    return CubeType.getCubeType(prefs.getInt("cubeTypeId", CubeType.THREE_BY_THREE.getId()));
  }

  public static int getCurrentSolveTypeId(Context c) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
    return prefs.getInt("solveTypeId", -1);
  }

  public static void setCurrentCubeType(Context c, CubeType cubeType) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
    int id = (cubeType == null) ? CubeType.THREE_BY_THREE.getId() : cubeType.getId();
    prefs.edit().putInt("cubeTypeId", id).apply();
  }

  public static void setCurrentSolveType(Context c, SolveType solveType) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
    int id = (solveType == null) ? -1 : solveType.getId();
    prefs.edit().putInt("solveTypeId", id).apply();
  }

  public static String[] invertMoves(String[] moves) {
    if (moves == null) {
      return null; // might happen if generation was cancelled
    }
    String[] inverted = new String[moves.length];
    for (int i = 0; i < moves.length; i++) {
      String m = moves[moves.length - 1 - i];
      if (m.endsWith("'")) {
        m = m.substring(0, m.length() - 1);
      } else if (!m.endsWith("2")) {
        m += "'";
      }
      inverted[i] = m;
    }
    return inverted;
  }

  public static long daysToMs(int days) {
    return ((long) days) * 24 * 60 * 60 * 1000;
  }

  public static boolean openPlayStorePage(Context context, String packageName) {
    Intent rateAppIntent;
    String storePackage = context.getPackageManager().getInstallerPackageName(context.getPackageName());
    if ("com.android.vending".equals(storePackage)) { // google
      rateAppIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName));
    } else if ("com.amazon.venezia".equals(storePackage)) { // amazon
      rateAppIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("amzn://apps/android?p=" + packageName));
    } else {
      rateAppIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName)); // try google market (play store)
    }

    if (context.getPackageManager().queryIntentActivities(rateAppIntent, 0).size() > 0) {
      try {
        context.startActivity(rateAppIntent);
        return true;
      } catch (ActivityNotFoundException e) {
        DialogUtils.showInfoMessage(context, R.string.could_not_launch_market);
      }
    } else {
      DialogUtils.showInfoMessage(context, R.string.could_not_find_market);
    }
    return false;
  }

  public static boolean isCurrentlyCharging() {
    IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    Intent batteryStatus = App.INSTANCE.getContext().registerReceiver(null, filter);
    int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
    return (status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL);
  }

  public static String getNewLine() {
    return System.getProperty("line.separator");
  }

  public static Character checkForForbiddenCharacters(String stepName) {
    Character forbiddenChar = null;
    for (char c : FORBIDDEN_NAME_CHARACTERS) {
      if (stepName.contains(String.valueOf(c))) {
        forbiddenChar = c;
        break;
      }
    }
    return forbiddenChar;
  }

  /** What a solving method is called, wherever one is named. */
  public static int getMethodLabel(CubeMethod method) {
    if (method == CubeMethod.ROUX) {
      return R.string.method_roux;
    }
    if (method == CubeMethod.LBL) {
      return R.string.method_lbl;
    }
    // Never offered as an override, but a blind solve type is read as it all the same.
    return method == CubeMethod.BLIND ? R.string.method_blind : R.string.method_cfop;
  }

  public static String toScrambleTypeLocalizedName(Context context, ScrambleType scrambleType) {
    int nameStringResourceId = Utils.getStringIdentifier(context, "scramble_type_" + scrambleType.getName());
    return context.getString(nameStringResourceId);
  }

  /** Localized name of a smart cube breakdown step code; position numbers repeated parts (the
   * F2L pairs), 1-based, and is ignored by the codes that do not carry a number. */
  public static String toSmartCubeStepLocalizedName(Context context, String code, int position) {
    String turn = toSmartCubeTurnedName(context, code);
    if (turn != null) {
      return turn;
    }
    if (code != null && code.startsWith(ORIENTATION_ALGORITHM_PREFIX)) {
      return context.getString(R.string.smartcube_step_oll_alg,
          code.substring(ORIENTATION_ALGORITHM_PREFIX.length()));
    }
    if (code != null && code.startsWith(PERMUTATION_ALGORITHM_PREFIX)) {
      return context.getString(R.string.smartcube_step_alg,
          capitalized(code.substring(PERMUTATION_ALGORITHM_PREFIX.length())));
    }
    int resId = getStringIdentifier(context, "smartcube_step_" + toSmartCubeStepBaseCode(code));
    return resId == 0 ? code : context.getString(resId, position + 1);
  }

  /**
   * A blind algorithm that turned its pieces where they stand rather than shooting them anywhere
   * ("flip:UF-UL"), said as the pieces plus what was done to them — otherwise null. The pieces are
   * the code's own and are never translated; only the word for the turn is.
   */
  private static String toSmartCubeTurnedName(Context context, String code) {
    if (code == null) {
      return null;
    }
    if (code.startsWith(FLIP_CODE_PREFIX)) {
      return context.getString(R.string.smartcube_step_flip,
          code.substring(FLIP_CODE_PREFIX.length()));
    }
    if (code.startsWith(TWIST_CODE_PREFIX)) {
      return context.getString(R.string.smartcube_step_twist,
          code.substring(TWIST_CODE_PREFIX.length()));
    }
    return null;
  }

  /**
   * An F2L pair is stored per slot ("pair_rf") and a last layer step per case ("pll_jb"); both go by
   * the name of the step itself, with what they carry shown beside it.
   */
  private static String toSmartCubeStepBaseCode(String code) {
    if (code == null) {
      return null;
    }
    for (String prefix : SLOT_CODE_PREFIXES) {
      if (code.startsWith(prefix)) {
        return prefix.substring(0, prefix.length() - 1);
      }
    }
    for (String prefix : CASE_CODE_PREFIXES) {
      if (code.startsWith(prefix)) {
        return prefix.substring(0, prefix.length() - 1);
      }
    }
    return code;
  }

  /**
   * The name a breakdown step is shown under. A last layer step is shown with the case it was left
   * with ("PLL (Jb)"), which is what says whether the algorithm that followed was the right one. A
   * step the solve stopped inside says so, since it holds only the parts that were finished and would
   * otherwise read as a step that was seen through.
   */
  public static String toSmartCubeStepDisplayName(Context context, SolveStep step, int position) {
    String name = toSmartCubeStepLocalizedName(context, step.getName(), position);
    String caseLabel = toSmartCubeCaseLabel(context, step.getName());
    if (caseLabel != null) {
      name = name + " " + caseLabel;
    }
    return step.isComplete() ? name : context.getString(R.string.smartcube_step_partial, name);
  }

  /**
   * How the last layer case a step's code carries is shown beside its name ("(case Ub)", "(case 8)"),
   * or null for a step that carries none — including every solve recorded before the cases were read.
   *
   * <p>Said as a case rather than as the bare name a speedcuber writes: "Ub" is plain enough to
   * someone who knows the case, and an OLL's number is a number on its own, which reads as anything.
   */
  public static String toSmartCubeCaseLabel(Context context, String code) {
    if (code == null) {
      return null;
    }
    for (String prefix : CASE_CODE_PREFIXES) {
      if (!code.startsWith(prefix)) {
        continue;
      }
      String name = code.substring(prefix.length());
      if (SKIPPED_CASE.equals(name)) {
        return context.getString(R.string.smartcube_case_skip);
      }
      return context.getString(R.string.smartcube_step_case, capitalized(name));
    }
    return null;
  }

  /** The algorithm a last layer step was answered with, rather than the case it was handed. */
  private static boolean isAlgorithmCode(String code) {
    return code.startsWith(PERMUTATION_ALGORITHM_PREFIX)
        || code.startsWith(ORIENTATION_ALGORITHM_PREFIX);
  }

  /** A case is written the way a speedcuber writes it: Ub, Ga, T. */
  private static String capitalized(String caseName) {
    return caseName.isEmpty() ? caseName
        : caseName.substring(0, 1).toUpperCase(Locale.US) + caseName.substring(1);
  }

  /**
   * A case as a headline, "PLL Ga" or "OLL 21", for a screen whose whole subject is the case.
   * {@link #toSmartCubeCaseLabel} is parenthetical because it hangs off a step name in a breakdown;
   * here there is no step name for it to hang off.
   */
  public static String toSmartCubeCaseHeadline(Context context, String code) {
    if (code == null) {
      return null;
    }
    if (isAlgorithmCode(code)) {
      return toSmartCubeStepLocalizedName(context, code, 0); // already names its own case
    }
    int split = code.indexOf('_');
    String name = split < 0 ? code : code.substring(split + 1);
    return context.getString(R.string.smartcube_case_headline,
        toSmartCubeStepLocalizedName(context, code, 0),
        name.substring(0, 1).toUpperCase(Locale.US) + name.substring(1));
  }

  /** A tail segment: the time after the last milestone, on a solve the cube never saw finish or on a
   * blind one nothing stopped. It belongs to no step, so it is drawn apart from them rather than in
   * the step colours. */
  public static boolean isTailSegment(String code) {
    return SolveBreakdown.UNFINISHED_STEP.equals(code) || SolveBreakdown.GAP_STEP.equals(code);
  }

  /** A blind solve's memorisation, which is time spent on the whole solve rather than on a step. */
  public static boolean isMemoStep(String code) {
    return MEMO_CODE.equals(code);
  }

  /**
   * The faces a part shows in its own colours, from its slot code: an F2L pair's two ("pair_rf"), a
   * first-layer corner's three ("corner_dfr"). Null for any other step, and for the pairs of solves
   * recorded before the slot was stored.
   */
  public static char[] getSmartCubeSlotFaces(String code) {
    if (code == null) {
      return null;
    }
    for (String prefix : SLOT_CODE_PREFIXES) {
      if (!code.startsWith(prefix)) {
        continue;
      }
      String faces = code.substring(prefix.length()).toUpperCase(Locale.US);
      return faces.length() >= 2 ? faces.toCharArray() : null;
    }
    return null;
  }

  /**
   * The number each part is shown under, by step and by part: its rank among the parts of its own
   * kind across the whole solve rather than within the step it fell in. Layer by layer breaks a
   * layer into as many steps as the solver came back to it, and keyhole's held-back corner — a step
   * of its own, after the second layer — is still the first layer's fourth corner.
   */
  public static int[][] getSmartCubeSubStepPositions(List<SolveStep> steps) {
    int[][] positions = new int[steps.size()][];
    Map<String, Integer> seen = new HashMap<String, Integer>();
    for (int step = 0; step < steps.size(); step++) {
      List<SolveStep> parts = steps.get(step).getSubSteps();
      positions[step] = new int[parts.size()];
      for (int part = 0; part < parts.size(); part++) {
        String kind = toSmartCubeStepBaseCode(parts.get(part).getName());
        Integer count = seen.get(kind);
        positions[step][part] = count == null ? 0 : count;
        seen.put(kind, positions[step][part] + 1);
      }
    }
    return positions;
  }

  /**
   * The pieces a blind algorithm's code names, in the order they are said — the mark saying what was
   * done to them taken off, and the rest split on everything a name joins pieces with. A code that
   * names none comes back as itself, and nothing marks one: it carries no marks either.
   */
  public static String[] getSmartCubeNamedPieces(String code) {
    if (code == null) {
      return new String[0];
    }
    return code.substring(code.indexOf(':') + 1).split("-| \\+ ");
  }

  /** Standard WCA face colors. Decorative: the app never relies on them to identify a face. */
  public static int getFaceColorRes(char face) {
    switch (face) {
      case 'U': return R.color.cube_white;
      case 'D': return R.color.cube_yellow;
      case 'R': return R.color.cube_red;
      case 'L': return R.color.cube_orange;
      case 'F': return R.color.cube_green;
      case 'B': return R.color.cube_blue;
      default: return R.color.gray400;
    }
  }

  public static String toSolveTypeLocalizedName(Context context, String solveTypeName) {
    String localizedName = solveTypeName;
    Integer locTranslationId = App.INSTANCE.getDynamicTranslations().getSolveTypeNameResourceId(localizedName);
    if (locTranslationId != null) {
      localizedName = context.getString(locTranslationId);
    }
    return localizedName;
  }

  public static boolean isDefaultSolveTypeName(String solveTypeName) {
    for (String defaultSolveTypeName : App.INSTANCE.getDynamicTranslations().getDefaultSolveTypeStrings()) {
      if (defaultSolveTypeName.equals(solveTypeName)) {
        return true;
      }
    }
    return false;
  }

  public static int getStringIdentifier(Context context, String name) {
    return context.getResources().getIdentifier(name, "string", context.getPackageName());
  }

  /**
   * Relaunches the app from scratch, tearing the process down with it.
   *
   * <p>Used where state held across the app has been replaced underneath it, by a language change
   * or by a restore. Killing the process is the point rather than a shortcut: it is the only way to
   * be sure no screen is still showing what was there before.
   */
  public static void restartApp(Context context) {
    Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
    if (launchIntent != null) {
      context.startActivity(Intent.makeRestartActivityTask(launchIntent.getComponent()));
    } else {
      // Nothing to come back to, but staying up is worse: the caller has already replaced what
      // the running app was built on. It closes, and the user opens it again.
      Log.w("[NanoTimer]", "No launch intent to restart with");
    }
    System.exit(0);
  }

  public static void updateContextWithPrefsLocale(Context context) {
    SharedPreferences prefs = context.getSharedPreferences(LANGUAGE_PREFS_NAME, 0);
    String localeString = prefs.getString(LANGUAGE_PREF_KEY, Locale.getDefault().getLanguage());

    Locale newLocale = new Locale(localeString);
    Locale.setDefault(newLocale);

    Resources res = context.getResources();
    Configuration config = new Configuration();
    config.locale = newLocale;
    res.updateConfiguration(config, res.getDisplayMetrics());

//    return Utils.wrapLocaleContext(context, newLocale);
  }

  /*private static ContextWrapper wrapLocaleContext(Context context, Locale newLocale) {
    Resources res = context.getResources();
    Configuration configuration = res.getConfiguration();

    if (VERSION.SDK_INT >= 24) {
      configuration.setLocale(newLocale);

      LocaleList localeList = new LocaleList(newLocale);
      LocaleList.setDefault(localeList);
      configuration.setLocales(localeList);

      context = context.createConfigurationContext(configuration);
    } else if (VERSION.SDK_INT >= 17) {
      configuration.setLocale(newLocale);
      context = context.createConfigurationContext(configuration);
    } else {
      configuration.locale = newLocale;
      res.updateConfiguration(configuration, res.getDisplayMetrics());
    }

    return new ContextWrapper(context);
  }*/

  public static byte[] toSingleDimensionByteArray(byte[][] data) {
    byte[] bytes = new byte[data.length * data[0].length];
    for (int i = 0; i < data.length; i++) {
      System.arraycopy(data[i], 0, bytes, i * data[0].length, data[0].length);
    }
    return bytes;
  }

  public static byte[][] toTwoDimensionalByteArray(byte[] data, int firstDimensionSize) {
    byte[][] bytes = new byte[firstDimensionSize][data.length / firstDimensionSize];
    for (int i = 0; i < bytes.length; i++) {
      for (int j = 0; j < bytes[i].length; j++) {
        bytes[i][j] = data[i*bytes[i].length+j];
      }
    }
    return bytes;
  }

}
