package com.cube.nanotimer.util.backup;

import com.cube.nanotimer.AppLaunchStats;

/**
 * Which preferences a backup carries, and which it deliberately leaves behind.
 *
 * <p>The three names below are the keys the preferences travel under in the file, not the names
 * Android stores them under. They are frozen: the default file's real name carries the application
 * id, so it differs between the debug and release builds, and a backup has to move between them.
 * Renaming a preferences file on the device is a separate decision from renaming it in the format.
 *
 * <p>What is left out describes the install rather than the user. Carrying a launch count or the
 * last release-notes version to a new phone either re-triggers a prompt or permanently swallows
 * one, neither of which is what "restore my solves" asked for.
 */
public class BackupScope {

  /** Everything under {@code PreferenceManager.getDefaultSharedPreferences}: settings and content. */
  public static final String DEFAULT = "default";
  /** GraphActivity's last selection. */
  public static final String GRAPH = "graph";
  /** The in-app language override, a genuine user choice rather than a device one. */
  public static final String LANGUAGE = "language";

  public static final String[] FILES = { DEFAULT, GRAPH, LANGUAGE };

  /** ReleaseNotes' key. Restoring it can swallow the release notes on the new install. */
  static final String RELEASE_NOTES_VERSION_KEY = "app_version";

  private BackupScope() {
  }

  public static boolean isExcluded(String file, String key) {
    if (!DEFAULT.equals(file)) {
      return false;
    }
    return AppLaunchStats.LAUNCH_COUNT_KEY.equals(key)
        || AppLaunchStats.FIRST_LAUNCH_KEY.equals(key)
        || RELEASE_NOTES_VERSION_KEY.equals(key);
  }

}
