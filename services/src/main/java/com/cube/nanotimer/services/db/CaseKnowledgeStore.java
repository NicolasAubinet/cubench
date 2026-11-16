package com.cube.nanotimer.services.db;

import com.cube.nanotimer.step.F2lPairCases;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import com.cube.nanotimer.coach.CaseRecall;
import com.cube.nanotimer.drill.DrillSpec;
import com.cube.nanotimer.session.CaseKnowledge;
import com.cube.nanotimer.session.MethodStatistics;
import com.cube.nanotimer.vo.ScrambleType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The materialized answer to "can the solver execute this case in one algorithm on their own", one
 * row per case, kept beside the evidence it is read from.
 *
 * <p>It is a cache and nothing more: {@link #rebuild} throws every row away and reads them back out
 * of the solves, drill reps and learning-drill clears, so the table can never be the only copy of
 * anything. Each row also keeps what first made the case learned, and when. Rows are
 * refreshed a case at a time as evidence lands, because a whole rebuild after every solve would
 * read the entire history to change one row.
 *
 * <p>The evidence, in the vocabulary the breakdown already stores:
 *
 * <ul>
 *   <li>A last layer step naming a case, with fewer than two parts, went in one algorithm. Parts are
 *       only recorded where a step took more than one, and that holds for the codes an OLL was
 *       recorded under before it was split by algorithm as much as for the ones it is now.
 *   <li>A clean drill rep counts towards a run, by the size of the pool its drill dealt from, and a
 *       {@code revealed} one starts the run again: the algorithm was on screen.
 *   <li>A row of {@code case_cleared} is a learning drill clearing the case, read as a whole run.
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

  /**
   * Reads every case's status again from scratch, which is what makes the table safe to be wrong.
   *
   * <p>Throwing the rows away and reading them back is one transaction: it runs on a background
   * thread against the database a screen reads on its own, and the two statements apart leave a
   * window in which the table is empty and a reader takes that for "nothing is known".
   *
   * <p><b>The reading happens before the transaction is opened, and that is the whole point of the
   * shape.</b> A transaction here is {@code BEGIN EXCLUSIVE} on a single non-WAL connection, so
   * everything else in the app queues behind it, and the reads are the entire cost: a whole CFOP
   * history is around eighty cases, each an aggregate over every solve and drill rep of it. Held
   * across the reads, a refresh dispatched at startup blocks the main screen's own queries for as
   * long as it takes. The writes it is actually protecting are eighty small upserts.
   */
  public static void rebuild(SQLiteDatabase db) {
    Map<String, List<Occurrence>> history = new LinkedHashMap<String, List<Occurrence>>();
    Map<Long, CaseKnowledge.Evidence> cleanKinds = new HashMap<Long, CaseKnowledge.Evidence>();
    for (String code : casesWithEvidence(db)) {
      if (namesACase(code)) {
        history.put(code, occurrences(db, code, cleanKinds));
      }
    }
    db.beginTransaction();
    try {
      db.delete(DB.TABLE_CASE_KNOWLEDGE, null, null);
      for (Map.Entry<String, List<Occurrence>> one : history.entrySet()) {
        write(db, MethodStatistics.familyOf(one.getKey()), MethodStatistics.caseOf(one.getKey()),
            one.getValue());
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  /**
   * Re-reads only the named cases, for evidence that has just landed.
   *
   * <p>One case is one aggregate over the whole history of it, so this is for a solve or a rep that
   * has just been recorded and not for a batch: rewriting a history calls {@link #rebuild} once
   * instead, which is both cheaper and the only way to end up with statuses read from a settled
   * history rather than from a half-rewritten one.
   *
   * <p>Read and write in one transaction: a rep and a clear of the same case land on two threads,
   * and a read taken before the other's insert must not be written after its update.
   */
  public static void update(SQLiteDatabase db, Collection<String> codes) {
    Map<Long, CaseKnowledge.Evidence> cleanKinds = new HashMap<Long, CaseKnowledge.Evidence>();
    db.beginTransaction();
    try {
      for (String code : codes) {
        if (namesACase(code)) {
          write(db, MethodStatistics.familyOf(code), MethodStatistics.caseOf(code),
              occurrences(db, code, cleanKinds));
        }
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  /** Each named case's occurrences, oldest first, dated, for {@link CaseRecall}. */
  public static Map<String, List<CaseRecall.Occurrence>> history(SQLiteDatabase db,
      Collection<String> codes) {
    Map<String, List<CaseRecall.Occurrence>> history =
        new HashMap<String, List<CaseRecall.Occurrence>>();
    Map<Long, CaseKnowledge.Evidence> cleanKinds = new HashMap<Long, CaseKnowledge.Evidence>();
    for (String code : codes) {
      if (!namesACase(code)) {
        continue;
      }
      List<CaseRecall.Occurrence> dated = new ArrayList<CaseRecall.Occurrence>();
      for (Occurrence occurrence : occurrences(db, code, cleanKinds)) {
        dated.add(new CaseRecall.Occurrence(occurrence.says, occurrence.atMs));
      }
      history.put(code, dated);
    }
    return history;
  }

  /** A learning drill has cleared the case: stored as evidence, and its status read again. */
  public static void recordCleared(SQLiteDatabase db, String code, long atMs) {
    db.beginTransaction();
    try {
      insertCleared(db, code, atMs);
      update(db, Collections.singletonList(code));
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  /**
   * Stores a clear for each case that has none, for cases cleared before clears were stored. Dated
   * at the case's last recorded drill, or 0 where none is left. The caller rebuilds afterwards.
   */
  public static void backfillCleared(SQLiteDatabase db, Collection<String> codes) {
    for (String code : codes) {
      if (!namesACase(code) || DatabaseUtils.queryNumEntries(db, DB.TABLE_CASE_CLEARED,
          DB.COL_CASE_CLEARED_CASE + " = ?", new String[] { code }) > 0) {
        continue;
      }
      long atMs = DatabaseUtils.longForQuery(db, "SELECT COALESCE(MAX(d." + DB.COL_DRILL_TIMESTAMP
          + "), 0) FROM " + DB.TABLE_DRILL_REP + " r JOIN " + DB.TABLE_DRILL + " d ON d." + DB.COL_ID
          + " = r." + DB.COL_DRILL_REP_DRILL_ID + " WHERE r." + DB.COL_DRILL_REP_CASE + " = ?",
          new String[] { code });
      insertCleared(db, code, atMs);
    }
  }

  private static void insertCleared(SQLiteDatabase db, String code, long atMs) {
    ContentValues values = new ContentValues();
    values.put(DB.COL_CASE_CLEARED_CASE, code);
    values.put(DB.COL_CASE_CLEARED_AT, atMs);
    db.insert(DB.TABLE_CASE_CLEARED, null, values);
  }

  /**
   * A step naming no case, or one already solved on arrival, is evidence about nothing; nor is a
   * look-ahead rep, whose code reads like an F2L case and is two pairs.
   */
  private static boolean namesACase(String code) {
    String caseName = code == null ? null : MethodStatistics.caseOf(code);
    return caseName != null && !SKIPPED.equals(caseName)
        && !DB.DRILL_REP_LOOKAHEAD_CASE.equals(code) && F2lPairCases.slotOf(code) == null;
  }

  private static void write(SQLiteDatabase db, String caseSet, String caseName,
      List<Occurrence> occurrences) {
    List<CaseKnowledge.Evidence> said = new ArrayList<CaseKnowledge.Evidence>();
    for (Occurrence occurrence : occurrences) {
      said.add(occurrence.says);
    }
    CaseKnowledge.Status status = CaseKnowledge.read(said);
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
    values.put(DB.COL_CASE_KNOWLEDGE_EVIDENCE, CaseKnowledge.evidenceCount(said));
    values.put(DB.COL_CASE_KNOWLEDGE_LAST_SEEN, occurrences.get(occurrences.size() - 1).atMs);
    values.put(DB.COL_CASE_KNOWLEDGE_UPDATED, System.currentTimeMillis());
    int learned = CaseKnowledge.firstLearned(said);
    values.put(DB.COL_CASE_KNOWLEDGE_LEARNED_SOURCE,
        learned < 0 ? null : CaseKnowledge.sourceOf(said.get(learned)).code());
    long learnedAt = learned < 0 ? 0 : occurrences.get(learned).atMs;
    values.put(DB.COL_CASE_KNOWLEDGE_LEARNED_AT, learnedAt > 0 ? Long.valueOf(learnedAt) : null);
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
        + " UNION SELECT DISTINCT " + DB.COL_DRILL_REP_CASE + " FROM " + DB.TABLE_DRILL_REP
        + " UNION SELECT DISTINCT " + DB.COL_CASE_CLEARED_CASE + " FROM " + DB.TABLE_CASE_CLEARED,
        null);
    if (cursor != null) {
      for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
        codes.add(cursor.getString(0));
      }
      cursor.close();
    }
    return codes;
  }

  /**
   * One case's occurrences, oldest first, which is the way round the rule reads them.
   *
   * <p><b>All of them, with no window.</b> The rule only looks at the newest one that says
   * anything, and a case can go a hundred solves between occurrences, so cutting the list short
   * would lose the last thing the solver actually did with the case.
   *
   * <p>Solves count only from a full scramble: a special scramble's cases are dealt out of their
   * usual flow, which says less about recognising them and times them off a different start.
   *
   * <p>A rep that was abandoned or restarted is left out rather than counted against the case: it
   * says the rep was fumbled, and what is being answered here is whether the algorithm is known, not
   * how fluently it runs. A rep turned with the algorithm on screen counts however it ended, since
   * it starts the run again.
   *
   * @param cleanKinds what a clean rep of each drill says, filled as drills are met
   */
  private static List<Occurrence> occurrences(SQLiteDatabase db, String code,
      Map<Long, CaseKnowledge.Evidence> cleanKinds) {
    String parts = "(SELECT COUNT(*) FROM " + DB.TABLE_SMARTCUBE_SOLVESTEP + " p"
        + " WHERE p." + DB.COL_SMARTCUBE_SOLVESTEP_TIMEHISTORY_ID
        + "     = s." + DB.COL_SMARTCUBE_SOLVESTEP_TIMEHISTORY_ID
        + "   AND p." + DB.COL_SMARTCUBE_SOLVESTEP_STEP_INDEX
        + "     = s." + DB.COL_SMARTCUBE_SOLVESTEP_STEP_INDEX
        + "   AND p." + DB.COL_SMARTCUBE_SOLVESTEP_SUB_INDEX + " IS NOT NULL)";

    String solved = "SELECT " + parts + " < 2 AS unaided"
        + "     , 1 AS from_solve"
        + "     , h." + DB.COL_TIMEHISTORY_TIMESTAMP + " AS seen_at"
        + "     , 0 AS ordinal"
        + "     , 0 AS drill"
        + "  FROM " + DB.TABLE_SMARTCUBE_SOLVESTEP + " s"
        + "  JOIN " + DB.TABLE_TIMEHISTORY + " h ON h." + DB.COL_ID
        + "     = s." + DB.COL_SMARTCUBE_SOLVESTEP_TIMEHISTORY_ID
        + "  JOIN " + DB.TABLE_SOLVETYPE + " t ON t." + DB.COL_ID
        + "     = h." + DB.COL_TIMEHISTORY_SOLVETYPE_ID
        + " WHERE s." + DB.COL_SMARTCUBE_SOLVESTEP_SUB_INDEX + " IS NULL"
        + "   AND COALESCE(t." + DB.COL_SOLVETYPE_SCRAMBLE_TYPE + ", '') IN ('', '"
        + ScrambleType.DEFAULT_NAME + "')"
        + "   AND s." + DB.COL_SMARTCUBE_SOLVESTEP_NAME + " = ?"
        + "   AND h." + DB.COL_TIMEHISTORY_TIME + " > 0"
        + "   AND (h." + DB.COL_TIMEHISTORY_SMARTCUBE_STOPPED_STEP + " IS NULL"
        + "     OR h." + DB.COL_TIMEHISTORY_SMARTCUBE_STOPPED_STEP
        + "    <> s." + DB.COL_SMARTCUBE_SOLVESTEP_STEP_INDEX + ")";

    // A restarted rep breaks a run as a lookup does. A learning drill reads its own, in case_cleared.
    String drilled = "SELECT (r." + DB.COL_DRILL_REP_REVEALED + " = 0"
        + "     AND r." + DB.COL_DRILL_REP_RESET_COUNT + " = 0) AS unaided"
        + "     , 0 AS from_solve"
        + "     , d." + DB.COL_DRILL_TIMESTAMP + " AS seen_at"
        + "     , r." + DB.COL_DRILL_REP_POSITION + " AS ordinal"
        + "     , d." + DB.COL_ID + " AS drill"
        + "  FROM " + DB.TABLE_DRILL_REP + " r"
        + "  JOIN " + DB.TABLE_DRILL + " d ON d." + DB.COL_ID + " = r." + DB.COL_DRILL_REP_DRILL_ID
        + " WHERE r." + DB.COL_DRILL_REP_CASE + " = ?"
        + "   AND r." + DB.COL_DRILL_REP_DELETED + " = 0"
        + "   AND r." + DB.COL_DRILL_REP_PRACTICE + " = 0"
        + "   AND (r." + DB.COL_DRILL_REP_REVEALED + " = 1"
        + "     OR r." + DB.COL_DRILL_REP_RESET_COUNT + " > 0"
        + "     OR r." + DB.COL_DRILL_REP_ABANDONED + " = 0)";

    // After any rep stamped the same moment: a backfilled clear carries its drill's start.
    String cleared = "SELECT 1 AS unaided"
        + "     , 2 AS from_solve"
        + "     , " + DB.COL_CASE_CLEARED_AT + " AS seen_at"
        + "     , " + Integer.MAX_VALUE + " AS ordinal"
        + "     , 0 AS drill"
        + "  FROM " + DB.TABLE_CASE_CLEARED
        + " WHERE " + DB.COL_CASE_CLEARED_CASE + " = ?";

    List<Occurrence> occurrences = new ArrayList<Occurrence>();
    Cursor cursor = db.rawQuery(solved + " UNION ALL " + drilled + " UNION ALL " + cleared
        + " ORDER BY seen_at DESC, ordinal DESC", new String[] { code, code, code });
    if (cursor != null) {
      for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
        // unaided, from_solve, seen_at, ordinal, drill: the order every part of the union selects in.
        boolean unaided = cursor.getInt(0) == 1;
        CaseKnowledge.Evidence says;
        if (cursor.getInt(1) == 2) {
          says = CaseKnowledge.Evidence.CLEARED;
        } else if (cursor.getInt(1) == 1) {
          says = unaided ? CaseKnowledge.Evidence.UNAIDED : CaseKnowledge.Evidence.HELPED;
        } else if (!unaided) {
          says = CaseKnowledge.Evidence.LOOKED_UP;
        } else {
          says = cleanKindOf(db, cursor.getLong(4), cleanKinds);
        }
        occurrences.add(0, new Occurrence(says, cursor.getLong(2)));
      }
      cursor.close();
    }
    return occurrences;
  }

  /** What a clean rep of this drill says: from the daily drill, a large pool, or a small one. */
  private static CaseKnowledge.Evidence cleanKindOf(SQLiteDatabase db, long drillId,
      Map<Long, CaseKnowledge.Evidence> cleanKinds) {
    CaseKnowledge.Evidence kind = cleanKinds.get(drillId);
    if (kind != null) {
      return kind;
    }
    kind = CaseKnowledge.Evidence.CLEAN_REP;
    Cursor cursor = db.rawQuery("SELECT " + DB.COL_DRILL_SPEC + " FROM " + DB.TABLE_DRILL
        + " WHERE " + DB.COL_ID + " = ?", new String[] { String.valueOf(drillId) });
    if (cursor != null) {
      if (cursor.moveToFirst() && !cursor.isNull(0)) {
        kind = cleanKindOf(cursor.getString(0));
      }
      cursor.close();
    }
    cleanKinds.put(drillId, kind);
    return kind;
  }

  private static CaseKnowledge.Evidence cleanKindOf(String spec) {
    DrillSpec drill;
    try {
      drill = DrillSpec.fromJson(spec);
    } catch (IllegalArgumentException e) {
      return CaseKnowledge.Evidence.CLEAN_REP;
    }
    if (drill.isDaily()) {
      return CaseKnowledge.Evidence.CLEAN_REP_DAILY;
    }
    return poolOf(drill) >= CaseKnowledge.LARGE_POOL
        ? CaseKnowledge.Evidence.CLEAN_REP_LARGE_POOL : CaseKnowledge.Evidence.CLEAN_REP;
  }

  /** The cases a drill deals from at once: its working set where it has one. */
  private static int poolOf(DrillSpec drill) {
    int pool = drill.getCases().size();
    return drill.getWorkingSet() > 0 ? Math.min(pool, drill.getWorkingSet()) : pool;
  }

  /** One time the case came up, and what it said about whether the algorithm is known. */
  private static final class Occurrence {
    private final CaseKnowledge.Evidence says;
    private final long atMs;

    Occurrence(CaseKnowledge.Evidence says, long atMs) {
      this.says = says;
      this.atMs = atMs;
    }
  }
}
