package com.cube.nanotimer.services.db;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;
import android.util.Log;
import com.cube.nanotimer.session.TimesStatistics;
import com.cube.nanotimer.vo.SolveTime;

import java.util.ArrayList;
import java.util.List;

public class DBUpgradeScripts {

  public static void calculateAndUpdateAvg50(SQLiteDatabase db) {
    List<Integer> solveTypeIds = getAllSolveTypesIds(db);
//    Log.i("[Avg50]", solveTypeIds.size() + " solve types.");
    for (int solveTypeId : solveTypeIds) {
//      Log.i("[Avg50]", "\n      --> Processing solve type " + solveTypeId + "...\n");
      List<SolveTime> solveTimes = new ArrayList<SolveTime>();
      StringBuilder q = new StringBuilder();
      q.append("SELECT ").append(DB.COL_ID).append(", ").append(DB.COL_TIMEHISTORY_TIME);
      q.append(" FROM ").append(DB.TABLE_TIMEHISTORY);
      q.append(" WHERE ").append(DB.COL_TIMEHISTORY_SOLVETYPE_ID).append(" = ?");
      q.append("   AND ").append(DB.COL_TIMEHISTORY_TIME).append(" > 0");
      q.append(" ORDER BY ").append(DB.COL_TIMEHISTORY_TIMESTAMP);
      Cursor cursor = db.rawQuery(q.toString(), new String[] { String.valueOf(solveTypeId) });
      if (cursor != null) {
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
          SolveTime st = new SolveTime();
          st.setId(cursor.getInt(0));
          st.setTime(cursor.getInt(1));
          solveTimes.add(st);
        }
        cursor.close();
      }
//      Log.i("[Avg50]", "Found " + solveTimes.size() + " times. Start updating...");
      long total = 0;
      for (int i = 0; i < solveTimes.size(); i++) {
        SolveTime st = solveTimes.get(i);
        total += st.getTime();
//        Log.i("[Avg50]", "  Time: " + st.getTime() + ". New total: " + total);
        if (i >= 50 - 1) {
          if (i >= 50) {
            total -= solveTimes.get(i - 50).getTime();
//            Log.i("[Avg50]", "   Removed " + solveTimes.get(i - 50).getTime() + ". New total: " + total);
          }
          ContentValues values = new ContentValues();
          values.put(DB.COL_TIMEHISTORY_AVG50, (total / 50));
//          Log.i("[Avg50]", "  Update " + st.getId() + " with value " + (total / 50));
          db.update(DB.TABLE_TIMEHISTORY, values, DB.COL_ID + " = ?", new String[] { String.valueOf(st.getId()) });
        }
      }
    }
  }

  /**
   * Writes "hold and release" as the inspection mode of an install that never chose one, so that
   * an upgrade does not move it onto the automatic mode new installs now start on.
   * The key is spelled out rather than read from Options, which this module cannot reach.
   */
  public static void keepInspectionModeOfExistingInstall(Context context) {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    if (!preferences.contains("inspection_mode")) {
      preferences.edit().putString("inspection_mode", "1").commit();
    }
  }

  public static void updateMeansToAverages(SQLiteDatabase db) {
    List<Integer> solveTypeIds = getAllSolveTypesIds(db);
//    Log.i("[MeansToAvg]", solveTypeIds.size() + " solve types.");
    for (int solveTypeId : solveTypeIds) {
//      Log.i("[MeansToAvg]", "\n      --> Processing solve type " + solveTypeId + "...\n");
      List<Integer> liIds = new ArrayList<Integer>();
      List<Long> liTimes = new ArrayList<Long>();
      StringBuilder q = new StringBuilder();
      q.append("SELECT ").append(DB.COL_ID).append(", ").append(DB.COL_TIMEHISTORY_TIME);
      q.append(" FROM ").append(DB.TABLE_TIMEHISTORY);
      q.append(" WHERE ").append(DB.COL_TIMEHISTORY_SOLVETYPE_ID).append(" = ?");
      q.append(" ORDER BY ").append(DB.COL_TIMEHISTORY_TIMESTAMP).append(" DESC"); // most recent time first (for session/RA calculations below)
      Cursor cursor = db.rawQuery(q.toString(), new String[] { String.valueOf(solveTypeId) });
      if (cursor != null) {
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
          liIds.add(cursor.getInt(0));
          liTimes.add(cursor.getLong(1));
        }
        cursor.close();
      }
//      Log.i("[MeansToAvg]", "Found " + liTimes.size() + " times");

      boolean blindMode = false;
      q = new StringBuilder();
      q.append("SELECT ").append(DB.COL_SOLVETYPE_BLIND);
      q.append(" FROM ").append(DB.TABLE_SOLVETYPE);
      q.append(" WHERE ").append(DB.COL_ID).append(" = ?");
      cursor = db.rawQuery(q.toString(), new String[] { String.valueOf(solveTypeId) });
      if (cursor != null) {
        if (cursor.moveToFirst()) {
          blindMode = (cursor.getInt(0) == 1);
        }
      }
//      Log.i("[MeansToAvg]", "Blind mode? " + blindMode);
//      Log.i("[MeansToAvg]", "Start updating...");

      for (int i = liIds.size() - 1; i >= 0; i--) { // reverse because most recent time is in position 0
        TimesStatistics session = new TimesStatistics(liTimes.subList(i, Math.min(i + 100, liIds.size())));
        ContentValues values = new ContentValues();
        if (blindMode) {
          values.put(DB.COL_TIMEHISTORY_AVG5, session.getMeanOf(3));
        } else {
          values.put(DB.COL_TIMEHISTORY_AVG5, session.getAverageOf(5));
        }
        values.put(DB.COL_TIMEHISTORY_AVG12, session.getAverageOf(12));
        values.put(DB.COL_TIMEHISTORY_AVG50, session.getAverageOf(50));
        values.put(DB.COL_TIMEHISTORY_AVG100, session.getAverageOf(100));

//        Log.i("[MeansToAvg]", "  Update " + liIds.get(i) + " (time: " + liTimes.get(i) + ") with averages " +
//            values.getAsLong(DB.COL_TIMEHISTORY_AVG5) + " | " +
//            values.getAsLong(DB.COL_TIMEHISTORY_AVG12) + " | " +
//            values.getAsLong(DB.COL_TIMEHISTORY_AVG50) + " | " +
//            values.getAsLong(DB.COL_TIMEHISTORY_AVG100));
        db.update(DB.TABLE_TIMEHISTORY, values, DB.COL_ID + " = ?", new String[] { String.valueOf(liIds.get(i)) });
      }
    }
  }

  public static void updateSolveTypesToBlindType(SQLiteDatabase db) {
    String q;
    q = "UPDATE " + DB.TABLE_SOLVETYPE +
        " SET " + DB.COL_SOLVETYPE_BLIND + " = 1" +
        " WHERE (LOWER(" + DB.COL_SOLVETYPE_NAME + ") LIKE '%blind%'" +
        "     OR LOWER(" + DB.COL_SOLVETYPE_NAME + ") LIKE '%bld%')" +
        "    AND 0 = (SELECT COUNT(*) FROM " + DB.TABLE_SOLVETYPESTEP +
        "             WHERE " + DB.COL_SOLVETYPESTEP_SOLVETYPE_ID + " = " + DB.TABLE_SOLVETYPE + "." + DB.COL_ID + ")";
    db.execSQL(q);
  }

  public static void updatePersonalBestFlag(SQLiteDatabase db) {
    final int timesCountBeforeBest = 12; // only set pb flag if >= 12 times for that solve type
    for (int solveTypeId : getAllSolveTypesIds(db)) {
      long bestTime = Integer.MAX_VALUE;
      List<SolveTime> solveTimes = new ArrayList<SolveTime>();
      StringBuilder q = new StringBuilder();
      q.append("SELECT ").append(DB.COL_ID).append(", ").append(DB.COL_TIMEHISTORY_TIME);
      q.append(" FROM ").append(DB.TABLE_TIMEHISTORY);
      q.append(" WHERE ").append(DB.COL_TIMEHISTORY_SOLVETYPE_ID).append(" = ?");
      q.append("   AND ").append(DB.COL_TIMEHISTORY_TIME).append(" > 0");
      q.append(" ORDER BY ").append(DB.COL_TIMEHISTORY_TIMESTAMP);
      Cursor cursor = db.rawQuery(q.toString(), new String[] { String.valueOf(solveTypeId) });
      if (cursor != null) {
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
          SolveTime st = new SolveTime();
          st.setId(cursor.getInt(0));
          st.setTime(cursor.getInt(1));
          solveTimes.add(st);
        }
        cursor.close();
      }

      int timesCount = 1;
      for (SolveTime st : solveTimes) {
        if (st.getTime() < bestTime) {
          if (timesCount >= timesCountBeforeBest) {
            ContentValues values = new ContentValues();
            values.put(DB.COL_TIMEHISTORY_PB, 1);
            db.update(DB.TABLE_TIMEHISTORY, values, DB.COL_ID + " = ?", new String[] { String.valueOf(st.getId()) });
          }
          bestTime = st.getTime();
        }
        timesCount++;
      }
    }
  }

  public static void updateSessionStarts(SQLiteDatabase db) {
    StringBuilder q = new StringBuilder();
    q.append("SELECT ").append(DB.COL_ID).append(", sessionstart");
    q.append(" FROM ").append(DB.TABLE_SOLVETYPE);
    q.append(" WHERE sessionstart IS NOT NULL");
    q.append("   AND sessionstart > 0");
    Cursor cursor = db.rawQuery(q.toString(), new String[] { });
    if (cursor != null) {
      for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
        int solveTypeId = cursor.getInt(0);
        long sessionStart = cursor.getLong(1);
        Log.i("[NanoTimer]", "Insert start " + sessionStart + " for solve type id " + solveTypeId);
        ContentValues values = new ContentValues();
        values.put(DB.COL_SESSION_START, sessionStart);
        values.put(DB.COL_SESSION_SOLVETYPE_ID, solveTypeId);
        db.insert(DB.TABLE_SESSION, null, values);
      }
      cursor.close();
    }
  }

  private static List<Integer> getAllSolveTypesIds(SQLiteDatabase db) {
    List<Integer> solveTypeIds = new ArrayList<Integer>();
    StringBuilder q = new StringBuilder();
    q.append("SELECT ").append(DB.COL_ID);
    q.append(" FROM ").append(DB.TABLE_SOLVETYPE);
    Cursor cursor = db.rawQuery(q.toString(), new String[] { });
    if (cursor != null) {
      for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
        solveTypeIds.add(cursor.getInt(0));
      }
      cursor.close();
    }
    return solveTypeIds;
  }

}
