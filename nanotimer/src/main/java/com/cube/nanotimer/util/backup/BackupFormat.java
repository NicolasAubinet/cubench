package com.cube.nanotimer.util.backup;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** The names inside a backup zip, and how the app identifies itself in one. */
public class BackupFormat {

  public static final String MANIFEST_ENTRY = "manifest.json";
  public static final String PREFERENCES_ENTRY = "preferences.json";
  public static final String DB_ENTRY_DIR = "db/";

  public static final String MIME_TYPE = "application/zip";

  // The debug build installs under its own application id so it can sit next to the release one.
  // That must not make its backups a different app's: a backup has to move between the two.
  private static final String DEBUG_SUFFIX = ".debug";

  private BackupFormat() {
  }

  public static String dbEntry(String dbName) {
    return DB_ENTRY_DIR + dbName;
  }

  /** The application id with the debug build's suffix taken off. */
  public static String appPackage(Context context) {
    String pkg = context.getPackageName();
    return pkg.endsWith(DEBUG_SUFFIX) ? pkg.substring(0, pkg.length() - DEBUG_SUFFIX.length()) : pkg;
  }

  public static int versionCode(Context context) {
    try {
      return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
    } catch (NameNotFoundException e) {
      return 0;
    }
  }

  public static String fileName(Date date) {
    return "cubench-backup-" + new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(date) + ".zip";
  }

}
