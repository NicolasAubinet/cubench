package com.cube.nanotimer.util.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.cube.nanotimer.util.helper.Utils;
import java.util.Map;
import java.util.Set;

/** Reads the preferences a backup carries off the device, and writes them back. */
public class BackupPreferences {

  private BackupPreferences() {
  }

  public static PreferencesSnapshot capture(Context context) {
    PreferencesSnapshot snapshot = new PreferencesSnapshot();
    for (String file : BackupScope.FILES) {
      snapshot.put(file, open(context, file).getAll());
    }
    return snapshot;
  }

  /**
   * Replaces the device's preferences with the backup's, committed rather than applied.
   *
   * <p>A restore restarts the process right after this, and one of the upgrade scripts reads
   * preferences while migrating. Both need the values on disk before the next open, which
   * {@code apply()} does not promise.
   */
  public static void apply(Context context, PreferencesSnapshot snapshot) {
    for (String file : BackupScope.FILES) {
      // A file the backup did not carry is left alone. One it carried empty is cleared.
      if (!snapshot.getFiles().contains(file)) {
        continue;
      }
      Map<String, Object> values = snapshot.get(file);
      SharedPreferences.Editor editor = open(context, file).edit().clear();
      for (Map.Entry<String, Object> entry : values.entrySet()) {
        write(editor, entry.getKey(), entry.getValue());
      }
      editor.commit();
    }
  }

  @SuppressWarnings("unchecked")
  private static void write(SharedPreferences.Editor editor, String key, Object value) {
    if (value instanceof Boolean) {
      editor.putBoolean(key, (Boolean) value);
    } else if (value instanceof Integer) {
      editor.putInt(key, (Integer) value);
    } else if (value instanceof Long) {
      editor.putLong(key, (Long) value);
    } else if (value instanceof Float) {
      editor.putFloat(key, (Float) value);
    } else if (value instanceof String) {
      editor.putString(key, (String) value);
    } else if (value instanceof Set) {
      editor.putStringSet(key, (Set<String>) value);
    }
  }

  private static SharedPreferences open(Context context, String file) {
    if (BackupScope.DEFAULT.equals(file)) {
      return PreferenceManager.getDefaultSharedPreferences(context);
    }
    if (BackupScope.LANGUAGE.equals(file)) {
      return context.getSharedPreferences(Utils.LANGUAGE_PREFS_NAME, 0);
    }
    return context.getSharedPreferences(file, 0);
  }

}
