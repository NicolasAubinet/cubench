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
import com.cube.nanotimer.App;
import com.cube.nanotimer.R;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.ScrambleType;
import com.cube.nanotimer.vo.SolveType;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Random;

public class Utils {

  public static final char[] FORBIDDEN_NAME_CHARACTERS = new char[] { '"', ',', ';', '|', '=' };

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

  public static String toScrambleTypeLocalizedName(Context context, ScrambleType scrambleType) {
    int nameStringResourceId = Utils.getStringIdentifier(context, "scramble_type_" + scrambleType.getName());
    return context.getString(nameStringResourceId);
  }

  /** Localized name of a smart cube breakdown step code; position numbers repeated parts (the
   * F2L pairs), 1-based, and is ignored by the codes that do not carry a number. */
  public static String toSmartCubeStepLocalizedName(Context context, String code, int position) {
    int resId = getStringIdentifier(context, "smartcube_step_" + code);
    return resId == 0 ? code : context.getString(resId, position + 1);
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
