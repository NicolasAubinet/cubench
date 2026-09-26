package com.cube.nanotimer.services.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.cube.nanotimer.session.AverageRecords;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maintains {@link DB#COL_TIMEHISTORY_AVG_PB}, like the pb column is maintained for single times:
 * it is written when solves change, so reading the history needs no extra work. Bit {@code i} is
 * set when the average in {@link #COLUMNS}{@code [i]} is better than every earlier average of its
 * size (see {@link AverageRecords}). Blind types only get the Mo3 bit: their other columns are
 * plain averages (DNF as soon as there are two DNFs), not the success averages the screen shows.
 *
 * <p>A new solve computes its own bits when it is saved. An edit or a delete can change any later
 * flag, so {@link #update} recomputes the flags from the changed solve onwards. The solves before
 * it are unaffected (an average only depends on earlier solves), so a single aggregate query over
 * them gives the starting point.
 */
public final class AverageRecordsStore {

  public static final String[] COLUMNS = {
      DB.COL_TIMEHISTORY_AVG5, DB.COL_TIMEHISTORY_AVG12, DB.COL_TIMEHISTORY_AVG50, DB.COL_TIMEHISTORY_AVG100 };

  private AverageRecordsStore() {
  }

  /** Bitmask of the given indexes in {@link #COLUMNS}, keeping only the Mo3 bit for a blind type. */
  public static int mask(List<Integer> positions, boolean blind) {
    int mask = 0;
    for (int i : positions) {
      mask |= 1 << i;
    }
    return blind ? mask & 1 : mask;
  }

  /** Average sizes in a stored bitmask; the first column is the Mo3 for a blind type. */
  public static List<Integer> sizes(int mask, boolean blind) {
    if (mask == 0) {
      return Collections.emptyList();
    }
    int[] sizes = { blind ? 3 : 5, 12, 50, 100 };
    List<Integer> named = new ArrayList<>();
    for (int i = 0; i < sizes.length; i++) {
      if ((mask & (1 << i)) != 0) {
        named.add(sizes[i]);
      }
    }
    return named;
  }

  /** An {@link AverageRecords} initialized with the solves of the type older than {@code timestamp}. */
  public static AverageRecords recordsBefore(SQLiteDatabase db, int solveTypeId, long timestamp) {
    long[] best = new long[COLUMNS.length];
    int[] seen = new int[COLUMNS.length];
    StringBuilder q = new StringBuilder("SELECT ");
    for (int i = 0; i < COLUMNS.length; i++) {
      q.append(i == 0 ? "" : ", ").append("MIN(CASE WHEN ").append(COLUMNS[i]).append(" > 0 THEN ").append(COLUMNS[i]).append(" END)");
      q.append(", SUM(").append(COLUMNS[i]).append(" > 0)");
    }
    q.append(" FROM ").append(DB.TABLE_TIMEHISTORY);
    q.append(" WHERE ").append(DB.COL_TIMEHISTORY_SOLVETYPE_ID).append(" = ?");
    q.append("   AND ").append(DB.COL_TIMEHISTORY_TIMESTAMP).append(" < ?");
    Cursor cursor = db.rawQuery(q.toString(), new String[] { String.valueOf(solveTypeId), String.valueOf(timestamp) });
    if (cursor != null) {
      if (cursor.moveToFirst()) {
        for (int i = 0; i < COLUMNS.length; i++) {
          best[i] = cursor.getLong(2 * i); // 0 if none; ignored since seen is then 0
          seen[i] = cursor.getInt(2 * i + 1);
        }
      }
      cursor.close();
    }
    return new AverageRecords(best, seen);
  }

  /** Recomputes the flags of the solves from {@code timestamp} onwards and saves the ones that changed. */
  public static void update(SQLiteDatabase db, int solveTypeId, long timestamp) {
    StringBuilder q = new StringBuilder();
    q.append("SELECT ").append(DB.COL_ID);
    for (String column : COLUMNS) {
      q.append(", ").append(column);
    }
    q.append(", ").append(DB.COL_TIMEHISTORY_AVG_PB);
    q.append(" FROM ").append(DB.TABLE_TIMEHISTORY);
    q.append(" WHERE ").append(DB.COL_TIMEHISTORY_SOLVETYPE_ID).append(" = ?");
    q.append("   AND ").append(DB.COL_TIMEHISTORY_TIMESTAMP).append(" >= ?");
    q.append(" ORDER BY ").append(DB.COL_TIMEHISTORY_TIMESTAMP);
    boolean blind = isBlind(db, solveTypeId);
    AverageRecords records = recordsBefore(db, solveTypeId, timestamp);
    Map<Integer, Integer> changed = new LinkedHashMap<>();
    Cursor cursor = db.rawQuery(q.toString(), new String[] { String.valueOf(solveTypeId), String.valueOf(timestamp) });
    if (cursor != null) {
      Long[] averages = new Long[COLUMNS.length];
      for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
        for (int i = 0; i < averages.length; i++) {
          averages[i] = cursor.isNull(i + 1) ? null : cursor.getLong(i + 1);
        }
        int mask = mask(records.next(averages), blind);
        if (mask != cursor.getInt(COLUMNS.length + 1)) {
          changed.put(cursor.getInt(0), mask);
        }
      }
      cursor.close();
    }
    for (Map.Entry<Integer, Integer> e : changed.entrySet()) {
      ContentValues values = new ContentValues();
      values.put(DB.COL_TIMEHISTORY_AVG_PB, e.getValue());
      db.update(DB.TABLE_TIMEHISTORY, values, DB.COL_ID + " = ?", new String[] { String.valueOf(e.getKey()) });
    }
  }

  private static boolean isBlind(SQLiteDatabase db, int solveTypeId) {
    boolean blind = false;
    Cursor cursor = db.rawQuery("SELECT " + DB.COL_SOLVETYPE_BLIND + " FROM " + DB.TABLE_SOLVETYPE
        + " WHERE " + DB.COL_ID + " = ?", new String[] { String.valueOf(solveTypeId) });
    if (cursor != null) {
      blind = cursor.moveToFirst() && cursor.getInt(0) == 1;
      cursor.close();
    }
    return blind;
  }

  /** Computes the flags of every solve type from scratch, for the DB upgrade that adds the column. */
  public static void updateAll(SQLiteDatabase db) {
    List<Integer> ids = new ArrayList<>();
    Cursor cursor = db.rawQuery("SELECT " + DB.COL_ID + " FROM " + DB.TABLE_SOLVETYPE, new String[] { });
    if (cursor != null) {
      for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
        ids.add(cursor.getInt(0));
      }
      cursor.close();
    }
    for (int id : ids) {
      update(db, id, Long.MIN_VALUE);
    }
  }
}
