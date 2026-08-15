package com.cube.nanotimer.services.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.cube.nanotimer.session.CaseKnowledge;
import com.cube.nanotimer.session.MethodStatistics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The materialized answer to "can the solver execute this case in one algorithm on their own", one
 * row per case, kept beside the evidence it is read from.
 *
 * <p>It is a cache and nothing more: {@link #rebuild} throws every row away and reads them back out
 * of the solves and drill reps, so the table can never be the only copy of anything. Rows are
 * refreshed a case at a time as evidence lands, because a whole rebuild after every solve would
 * read the entire history to change one row.
 *
 * <p>The evidence, in the vocabulary the breakdown already stores:
 *
 * <ul>
 *   <li>A last layer step naming a case, with fewer than two parts, went in one algorithm. Parts are
 *       only recorded where a step took more than one, and that holds for the codes an OLL was
 *       recorded under before it was split by algorithm as much as for the ones it is now.
 *   <li>A drill rep says the same thing, with {@code revealed} as the plainest negative evidence
 *       there is: the solver asked to be shown the algorithm.
 *   <li>The step a solve stopped inside is left out. It holds only the parts that were finished, so
 *       counting them would read an abandoned step as one clean algorithm.
 * </ul>
 */
public final class CaseKnowledgeStore {

  /** The case a step is under when it was already solved on arrival: no case was executed. */
  private static final String SKIPPED = MethodStatistics.SKIP;

  private CaseKnowledgeStore() {
  }

  /** Every case with a status, most recently seen first. */
  public static List<CaseKnowledge> read(SQLiteDatabase db) {
    List<CaseKnowledge> known = new ArrayList<CaseKnowledge>();
    Cursor cursor = db.rawQuery("SELECT " + DB.COL_CASE_KNOWLEDGE_SET
        + "     , " + DB.COL_CASE_KNOWLEDGE_CASE
        + "     , " + DB.COL_CASE_KNOWLEDGE_STATUS
        + "     , " + DB.COL_CASE_KNOWLEDGE_EVIDENCE
        + "     , " + DB.COL_CASE_KNOWLEDGE_LAST_SEEN
        + "  FROM " + DB.TABLE_CASE_KNOWLEDGE
        + " ORDER BY " + DB.COL_CASE_KNOWLEDGE_LAST_SEEN + " DESC", null);
    if (cursor != null) {
      for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
        CaseKnowledge.Status status = CaseKnowledge.Status.forCode(cursor.getString(2));
        if (status != null) { // a status this version does not know is no status
          known.add(new CaseKnowledge(cursor.getString(0), cursor.getString(1), status,
              cursor.getInt(3), cursor.getLong(4)));
        }
      }
      cursor.close();
    }
    return known;
  }

  /** Reads every case's status again from scratch, which is what makes the table safe to be wrong. */
  public static void rebuild(SQLiteDatabase db) {
    db.delete(DB.TABLE_CASE_KNOWLEDGE, null, null);
    update(db, casesWithEvidence(db));
  }

  /** Re-reads only the named cases, for evidence that has just landed. */
  public static void update(SQLiteDatabase db, Collection<String> codes) {
    for (String code : codes) {
      String caseName = code == null ? null : MethodStatistics.caseOf(code);
      if (caseName == null || SKIPPED.equals(caseName)) {
        continue; // a step with no case to name, or one that was already solved on arrival
      }
      write(db, MethodStatistics.familyOf(code), caseName, occurrences(db, code));
    }
  }

  private static void write(SQLiteDatabase db, String caseSet, String caseName,
      List<Occurrence> occurrences) {
    List<Boolean> unaided = new ArrayList<Boolean>();
    for (Occurrence occurrence : occurrences) {
      unaided.add(Boolean.valueOf(occurrence.unaided));
    }
    CaseKnowledge.Status status = CaseKnowledge.read(unaided);
    String where = DB.COL_CASE_KNOWLEDGE_SET + " = ? AND " + DB.COL_CASE_KNOWLEDGE_CASE + " = ?";
    String[] args = new String[] { caseSet, caseName };
    if (status == null) {
      db.delete(DB.TABLE_CASE_KNOWLEDGE, where, args); // back under the floor: no status, not a bad one
      return;
    }
    ContentValues values = new ContentValues();
    values.put(DB.COL_CASE_KNOWLEDGE_SET, caseSet);
    values.put(DB.COL_CASE_KNOWLEDGE_CASE, caseName);
    values.put(DB.COL_CASE_KNOWLEDGE_STATUS, status.code());
    values.put(DB.COL_CASE_KNOWLEDGE_EVIDENCE, occurrences.size());
    values.put(DB.COL_CASE_KNOWLEDGE_LAST_SEEN, occurrences.get(occurrences.size() - 1).atMs);
    values.put(DB.COL_CASE_KNOWLEDGE_UPDATED, System.currentTimeMillis());
    if (db.update(DB.TABLE_CASE_KNOWLEDGE, values, where, args) == 0) {
      db.insert(DB.TABLE_CASE_KNOWLEDGE, null, values);
    }
  }

  /** Every case the history holds any evidence of, solved or drilled. */
  private static Set<String> casesWithEvidence(SQLiteDatabase db) {
    Set<String> codes = new LinkedHashSet<String>();
    Cursor cursor = db.rawQuery("SELECT DISTINCT " + DB.COL_SMARTCUBE_SOLVESTEP_NAME
        + "  FROM " + DB.TABLE_SMARTCUBE_SOLVESTEP
        + " WHERE " + DB.COL_SMARTCUBE_SOLVESTEP_SUB_INDEX + " IS NULL"
        + " UNION SELECT DISTINCT " + DB.COL_DRILL_REP_CASE + " FROM " + DB.TABLE_DRILL_REP, null);
    if (cursor != null) {
      for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
        codes.add(cursor.getString(0));
      }
      cursor.close();
    }
    return codes;
  }

  /**
   * One case's last occurrences, oldest last of the newest {@link CaseKnowledge#WINDOW}, then turned
   * back the way round the rule reads them.
   *
   * <p>A rep that was abandoned or restarted is left out rather than counted against the case: it
   * says the rep was fumbled, and what is being answered here is whether the algorithm is known, not
   * how fluently it runs. A rep the solver asked to be shown counts however it ended, since asking
   * is the answer.
   */
  private static List<Occurrence> occurrences(SQLiteDatabase db, String code) {
    String parts = "(SELECT COUNT(*) FROM " + DB.TABLE_SMARTCUBE_SOLVESTEP + " p"
        + " WHERE p." + DB.COL_SMARTCUBE_SOLVESTEP_TIMEHISTORY_ID
        + "     = s." + DB.COL_SMARTCUBE_SOLVESTEP_TIMEHISTORY_ID
        + "   AND p." + DB.COL_SMARTCUBE_SOLVESTEP_STEP_INDEX
        + "     = s." + DB.COL_SMARTCUBE_SOLVESTEP_STEP_INDEX
        + "   AND p." + DB.COL_SMARTCUBE_SOLVESTEP_SUB_INDEX + " IS NOT NULL)";

    String solved = "SELECT " + parts + " < 2 AS unaided"
        + "     , h." + DB.COL_TIMEHISTORY_TIMESTAMP + " AS seen_at"
        + "     , 0 AS ordinal"
        + "  FROM " + DB.TABLE_SMARTCUBE_SOLVESTEP + " s"
        + "  JOIN " + DB.TABLE_TIMEHISTORY + " h ON h." + DB.COL_ID
        + "     = s." + DB.COL_SMARTCUBE_SOLVESTEP_TIMEHISTORY_ID
        + " WHERE s." + DB.COL_SMARTCUBE_SOLVESTEP_SUB_INDEX + " IS NULL"
        + "   AND s." + DB.COL_SMARTCUBE_SOLVESTEP_NAME + " = ?"
        + "   AND h." + DB.COL_TIMEHISTORY_TIME + " > 0"
        + "   AND (h." + DB.COL_TIMEHISTORY_SMARTCUBE_STOPPED_STEP + " IS NULL"
        + "     OR h." + DB.COL_TIMEHISTORY_SMARTCUBE_STOPPED_STEP
        + "    <> s." + DB.COL_SMARTCUBE_SOLVESTEP_STEP_INDEX + ")";

    String drilled = "SELECT r." + DB.COL_DRILL_REP_REVEALED + " = 0 AS unaided"
        + "     , d." + DB.COL_DRILL_TIMESTAMP + " AS seen_at"
        + "     , r." + DB.COL_DRILL_REP_POSITION + " AS ordinal"
        + "  FROM " + DB.TABLE_DRILL_REP + " r"
        + "  JOIN " + DB.TABLE_DRILL + " d ON d." + DB.COL_ID + " = r." + DB.COL_DRILL_REP_DRILL_ID
        + " WHERE r." + DB.COL_DRILL_REP_CASE + " = ?"
        + "   AND r." + DB.COL_DRILL_REP_DELETED + " = 0"
        + "   AND (r." + DB.COL_DRILL_REP_REVEALED + " = 1"
        + "     OR (r." + DB.COL_DRILL_REP_ABANDONED + " = 0"
        + "    AND r." + DB.COL_DRILL_REP_RESET_COUNT + " = 0))";

    List<Occurrence> occurrences = new ArrayList<Occurrence>();
    Cursor cursor = db.rawQuery(solved + " UNION ALL " + drilled
        + " ORDER BY seen_at DESC, ordinal DESC LIMIT " + CaseKnowledge.WINDOW,
        new String[] { code, code });
    if (cursor != null) {
      for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
        occurrences.add(0, new Occurrence(cursor.getInt(0) == 1, cursor.getLong(1)));
      }
      cursor.close();
    }
    return occurrences;
  }

  /** One time the case came up, and whether it went in one algorithm with nothing shown. */
  private static final class Occurrence {
    private final boolean unaided;
    private final long atMs;

    Occurrence(boolean unaided, long atMs) {
      this.unaided = unaided;
      this.atMs = atMs;
    }
  }
}
