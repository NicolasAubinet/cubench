package com.cube.nanotimer.util.backup;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The preferences a backup carries, as values rather than as the XML Android keeps them in.
 *
 * <p>Grouped by file and then by type, because the type has to survive the trip: a value stored as
 * a {@code long} and read back as an {@code int} throws {@code ClassCastException} at the next
 * read, and JSON on its own cannot tell the two apart. Reading from {@code getAll()} rather than
 * copying the XML is what lets {@link BackupScope} drop keys on the way past.
 *
 * <p>Excluded keys are dropped both on the way in and on the way out, so a file written by a build
 * whose exclusion list was shorter still cannot put them back.
 */
public class PreferencesSnapshot {

  private static final String TYPE_BOOLEAN = "boolean";
  private static final String TYPE_INT = "int";
  private static final String TYPE_LONG = "long";
  private static final String TYPE_FLOAT = "float";
  private static final String TYPE_STRING = "string";
  private static final String TYPE_STRING_SET = "stringSet";

  private final Map<String, Map<String, Object>> files = new LinkedHashMap<String, Map<String, Object>>();

  /** Adds one file's values, minus the keys {@link BackupScope} leaves behind. */
  public void put(String file, Map<String, ?> values) {
    Map<String, Object> kept = new TreeMap<String, Object>();
    for (Map.Entry<String, ?> entry : values.entrySet()) {
      if (!BackupScope.isExcluded(file, entry.getKey()) && entry.getValue() != null) {
        kept.put(entry.getKey(), entry.getValue());
      }
    }
    files.put(file, kept);
  }

  /** One file's values, empty when the backup did not carry that file. */
  public Map<String, Object> get(String file) {
    Map<String, Object> values = files.get(file);
    return values == null ? Collections.<String, Object>emptyMap() : values;
  }

  public Set<String> getFiles() {
    return files.keySet();
  }

  public String toJson() {
    try {
      JSONObject json = new JSONObject();
      for (Map.Entry<String, Map<String, Object>> file : files.entrySet()) {
        JSONObject byType = new JSONObject();
        for (Map.Entry<String, Object> entry : file.getValue().entrySet()) {
          String type = typeOf(entry.getValue());
          if (type == null) {
            continue; // a type SharedPreferences cannot hold: not ours to guess at
          }
          JSONObject bucket = byType.optJSONObject(type);
          if (bucket == null) {
            bucket = new JSONObject();
            byType.put(type, bucket);
          }
          bucket.put(entry.getKey(), toJsonValue(entry.getValue()));
        }
        json.put(file.getKey(), byType);
      }
      return json.toString(2);
    } catch (JSONException e) {
      throw new IllegalStateException("Could not write the backup preferences", e);
    }
  }

  /** Null for anything that does not parse, which the caller reports as a damaged file. */
  public static PreferencesSnapshot parse(String json) {
    if (json == null) {
      return null;
    }
    try {
      JSONObject root = new JSONObject(json);
      PreferencesSnapshot snapshot = new PreferencesSnapshot();
      for (Iterator<String> fileNames = root.keys(); fileNames.hasNext(); ) {
        String file = fileNames.next();
        JSONObject byType = root.getJSONObject(file);
        Map<String, Object> values = new TreeMap<String, Object>();
        for (Iterator<String> types = byType.keys(); types.hasNext(); ) {
          String type = types.next();
          JSONObject bucket = byType.getJSONObject(type);
          for (Iterator<String> keys = bucket.keys(); keys.hasNext(); ) {
            String key = keys.next();
            if (BackupScope.isExcluded(file, key)) {
              continue;
            }
            Object value = fromJsonValue(type, bucket, key);
            if (value != null) {
              values.put(key, value);
            }
          }
        }
        snapshot.files.put(file, values);
      }
      return snapshot;
    } catch (JSONException e) {
      return null;
    }
  }

  private static String typeOf(Object value) {
    if (value instanceof Boolean) {
      return TYPE_BOOLEAN;
    } else if (value instanceof Integer) {
      return TYPE_INT;
    } else if (value instanceof Long) {
      return TYPE_LONG;
    } else if (value instanceof Float) {
      return TYPE_FLOAT;
    } else if (value instanceof String) {
      return TYPE_STRING;
    } else if (value instanceof Set) {
      return TYPE_STRING_SET;
    }
    return null;
  }

  private static Object toJsonValue(Object value) {
    if (value instanceof Set) {
      JSONArray array = new JSONArray();
      for (Object member : (Set<?>) value) {
        array.put(String.valueOf(member));
      }
      return array;
    }
    if (value instanceof Float) {
      return Double.valueOf(((Float) value).doubleValue());
    }
    return value;
  }

  private static Object fromJsonValue(String type, JSONObject bucket, String key)
      throws JSONException {
    if (TYPE_BOOLEAN.equals(type)) {
      return Boolean.valueOf(bucket.getBoolean(key));
    } else if (TYPE_INT.equals(type)) {
      return Integer.valueOf(bucket.getInt(key));
    } else if (TYPE_LONG.equals(type)) {
      return Long.valueOf(bucket.getLong(key));
    } else if (TYPE_FLOAT.equals(type)) {
      return Float.valueOf((float) bucket.getDouble(key));
    } else if (TYPE_STRING.equals(type)) {
      return bucket.getString(key);
    } else if (TYPE_STRING_SET.equals(type)) {
      JSONArray array = bucket.getJSONArray(key);
      Set<String> members = new HashSet<String>();
      for (int i = 0; i < array.length(); i++) {
        members.add(array.getString(i));
      }
      return members;
    }
    return null; // a type a later version writes: left alone rather than guessed at
  }

}
