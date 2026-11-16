package com.cube.nanotimer.services.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.cube.nanotimer.vo.ScrambleType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The F2L pairs each solve put in and the moves it put them in with, which case knowledge reads a
 * pair's solves from. Only the app can replay a solve to get them, so it hands them over here; a
 * solve whose rows are gone is simply read again.
 */
public final class PairOutcomeStore {

  private PairOutcomeStore() {
  }

  /** One pair a solve put in, or one moment it did. */
  public static final class Execution {
    final String moves;
    final long atMs;

    Execution(String moves, long atMs) {
      this.moves = moves;
      this.atMs = atMs;
    }
  }

  /**
   * Keeps each solve's pairs in place of anything stored for it before.
   *
   * @param outcomes per solve id, each pair as {case, moves}; an empty list for a solve read with no
   *     pair, which keeps one row with no case so it is not read again
   * @return every case the solves held, before and now, for their knowledge to be read again
   */
  public static Set<String> write(SQLiteDatabase db, Map<Long, List<String[]>> outcomes) {
    Set<String> cases = new LinkedHashSet<String>();
    db.beginTransaction();
    try {
      for (Map.Entry<Long, List<String[]>> solve : outcomes.entrySet()) {
        long solveId = solve.getKey().longValue();
        cases.addAll(casesOf(db, solveId));
        forget(db, solveId);
        if (solve.getValue().isEmpty()) {
          insert(db, solveId, null, null);
        }
        for (String[] pair : solve.getValue()) {
          insert(db, solveId, pair[0], pair[1]);
          cases.add(pair[0]);
        }
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
    return cases;
  }

  private static void insert(SQLiteDatabase db, long solveId, String caseCode, String moves) {
    ContentValues values = new ContentValues();
    values.put(DB.COL_PAIR_OUTCOME_SOLVE_ID, solveId);
    values.put(DB.COL_PAIR_OUTCOME_CASE, caseCode);
    values.put(DB.COL_PAIR_OUTCOME_MOVES, moves);
    db.insert(DB.TABLE_PAIR_OUTCOME, null, values);
  }

  /** The cases one solve put a pair in for. */
  public static List<String> casesOf(SQLiteDatabase db, long solveId) {
    List<String> cases = new ArrayList<String>();
    Cursor cursor = db.rawQuery("SELECT DISTINCT " + DB.COL_PAIR_OUTCOME_CASE
        + " FROM " + DB.TABLE_PAIR_OUTCOME + " WHERE " + DB.COL_PAIR_OUTCOME_SOLVE_ID + " = ?"
        + " AND " + DB.COL_PAIR_OUTCOME_CASE + " IS NOT NULL",
        new String[] { String.valueOf(solveId) });
    if (cursor != null) {
      for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
        cases.add(cursor.getString(0));
      }
      cursor.close();
    }
    return cases;
  }

  /** Every case any solve still kept put a pair in for. */
  public static Set<String> cases(SQLiteDatabase db) {
    Set<String> cases = new LinkedHashSet<String>();
    Cursor cursor = db.rawQuery("SELECT DISTINCT p." + DB.COL_PAIR_OUTCOME_CASE
        + "  FROM " + DB.TABLE_PAIR_OUTCOME + " p"
        + "  JOIN " + DB.TABLE_TIMEHISTORY + " h ON h." + DB.COL_ID
        + "     = p." + DB.COL_PAIR_OUTCOME_SOLVE_ID
        + " WHERE p." + DB.COL_PAIR_OUTCOME_CASE + " IS NOT NULL", null);
    if (cursor != null) {
      for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
        cases.add(cursor.getString(0));
      }
      cursor.close();
    }
    return cases;
  }

  /**
   * Every execution of one case that counts as evidence, oldest first: from a full scramble, as the
   * last layer's solves are, and not a DNF.
   */
  public static List<Execution> executions(SQLiteDatabase db, String caseCode) {
    List<Execution> executions = new ArrayList<Execution>();
    Cursor cursor = db.rawQuery("SELECT p." + DB.COL_PAIR_OUTCOME_MOVES
        + "     , h." + DB.COL_TIMEHISTORY_TIMESTAMP
        + "  FROM " + DB.TABLE_PAIR_OUTCOME + " p"
        + "  JOIN " + DB.TABLE_TIMEHISTORY + " h ON h." + DB.COL_ID
        + "     = p." + DB.COL_PAIR_OUTCOME_SOLVE_ID
        + "  JOIN " + DB.TABLE_SOLVETYPE + " t ON t." + DB.COL_ID
        + "     = h." + DB.COL_TIMEHISTORY_SOLVETYPE_ID
        + " WHERE p." + DB.COL_PAIR_OUTCOME_CASE + " = ?"
        + "   AND h." + DB.COL_TIMEHISTORY_TIME + " > 0"
        + "   AND COALESCE(t." + DB.COL_SOLVETYPE_SCRAMBLE_TYPE + ", '') IN ('', '"
        + ScrambleType.DEFAULT_NAME + "')"
        + " ORDER BY h." + DB.COL_TIMEHISTORY_TIMESTAMP, new String[] { caseCode });
    if (cursor != null) {
      for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
        executions.add(new Execution(cursor.getString(0), cursor.getLong(1)));
      }
      cursor.close();
    }
    return executions;
  }

  /** Throws one solve's rows away, for a solve going or about to be read again. */
  public static void forget(SQLiteDatabase db, long solveId) {
    db.delete(DB.TABLE_PAIR_OUTCOME, DB.COL_PAIR_OUTCOME_SOLVE_ID + " = ?",
        new String[] { String.valueOf(solveId) });
  }

  /** Throws away the rows of every solve of one solve type, which is about to lose them. */
  public static void forgetSolveType(SQLiteDatabase db, int solveTypeId) {
    db.delete(DB.TABLE_PAIR_OUTCOME, DB.COL_PAIR_OUTCOME_SOLVE_ID + " IN (SELECT " + DB.COL_ID
        + " FROM " + DB.TABLE_TIMEHISTORY + " WHERE " + DB.COL_TIMEHISTORY_SOLVETYPE_ID + " = ?)",
        new String[] { String.valueOf(solveTypeId) });
  }

  /** Throws every row away. */
  public static void forgetAll(SQLiteDatabase db) {
    db.delete(DB.TABLE_PAIR_OUTCOME, null, null);
  }
}
