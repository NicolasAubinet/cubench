package com.cube.nanotimer.util.backup;

import com.cube.nanotimer.vo.BackupCounts;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * What a backup says about itself: the container layout, when it was taken, by which build, at
 * which schema version, and how much it holds.
 *
 * <p>{@code format} versions the container and moves only when the zip's own layout changes, which
 * is a much slower clock than {@link #getDbVersion()}. The schema version is what decides whether a
 * file can be restored at all: an older one migrates through the ordinary upgrade chain, a newer
 * one cannot be opened by this build and is refused rather than attempted.
 */
public class BackupManifest {

  /** The container layout this build writes, and the newest it can read. */
  public static final int FORMAT = 1;

  private static final String KEY_FORMAT = "format";
  private static final String KEY_CREATED_AT = "createdAt";
  private static final String KEY_APP = "app";
  private static final String KEY_PACKAGE = "package";
  private static final String KEY_VERSION_CODE = "versionCode";
  private static final String KEY_VERSION_NAME = "versionName";
  private static final String KEY_DB_VERSION = "dbVersion";
  private static final String KEY_CONTENTS = "contents";
  private static final String KEY_SOLVES = "solves";
  private static final String KEY_SOLVE_TYPES = "solveTypes";
  private static final String KEY_DRILLS = "drills";
  private static final String KEY_DRILL_REPS = "drillReps";

  private final int format;
  private final long createdAt;
  private final String appPackage;
  private final int versionCode;
  private final String versionName;
  private final int dbVersion;
  private final BackupCounts counts;

  public BackupManifest(int format, long createdAt, String appPackage, int versionCode,
      String versionName, int dbVersion, BackupCounts counts) {
    this.format = format;
    this.createdAt = createdAt;
    this.appPackage = appPackage;
    this.versionCode = versionCode;
    this.versionName = versionName;
    this.dbVersion = dbVersion;
    this.counts = counts;
  }

  public String toJson() {
    try {
      JSONObject app = new JSONObject();
      app.put(KEY_PACKAGE, appPackage);
      app.put(KEY_VERSION_CODE, versionCode);
      app.put(KEY_VERSION_NAME, versionName);

      JSONObject contents = new JSONObject();
      contents.put(KEY_SOLVES, counts.getSolves());
      contents.put(KEY_SOLVE_TYPES, counts.getSolveTypes());
      contents.put(KEY_DRILLS, counts.getDrills());
      contents.put(KEY_DRILL_REPS, counts.getDrillReps());

      JSONObject json = new JSONObject();
      json.put(KEY_FORMAT, format);
      json.put(KEY_CREATED_AT, createdAt);
      json.put(KEY_APP, app);
      json.put(KEY_DB_VERSION, dbVersion);
      json.put(KEY_CONTENTS, contents);
      return json.toString(2);
    } catch (JSONException e) {
      throw new IllegalStateException("Could not write the backup manifest", e);
    }
  }

  /** Null for anything that does not parse, which the caller reports as a damaged file. */
  public static BackupManifest parse(String json) {
    if (json == null) {
      return null;
    }
    try {
      JSONObject o = new JSONObject(json);
      JSONObject app = o.optJSONObject(KEY_APP);
      JSONObject contents = o.optJSONObject(KEY_CONTENTS);
      if (app == null || contents == null) {
        return null;
      }
      return new BackupManifest(
        o.getInt(KEY_FORMAT),
        o.optLong(KEY_CREATED_AT, 0),
        app.getString(KEY_PACKAGE),
        app.optInt(KEY_VERSION_CODE, 0),
        app.optString(KEY_VERSION_NAME, ""),
        o.getInt(KEY_DB_VERSION),
        new BackupCounts(
          contents.optInt(KEY_SOLVES, 0),
          contents.optInt(KEY_SOLVE_TYPES, 0),
          contents.optInt(KEY_DRILLS, 0),
          contents.optInt(KEY_DRILL_REPS, 0)));
    } catch (JSONException e) {
      return null;
    }
  }

  /**
   * Why this file cannot be restored by a build at {@code dbVersion} running as {@code appPackage},
   * or null when it can be.
   */
  public BackupRejection reject(int currentDbVersion, String appPackage) {
    if (format > FORMAT) {
      return BackupRejection.NEWER_FORMAT;
    }
    if (!this.appPackage.equals(appPackage)) {
      return BackupRejection.WRONG_APP;
    }
    // An older schema is the ordinary case: it migrates on the first open, like any app update.
    if (dbVersion > currentDbVersion) {
      return BackupRejection.NEWER_DB_VERSION;
    }
    return null;
  }

  public int getFormat() {
    return format;
  }

  public long getCreatedAt() {
    return createdAt;
  }

  public String getAppPackage() {
    return appPackage;
  }

  public int getVersionCode() {
    return versionCode;
  }

  public String getVersionName() {
    return versionName;
  }

  public int getDbVersion() {
    return dbVersion;
  }

  public BackupCounts getCounts() {
    return counts;
  }

}
