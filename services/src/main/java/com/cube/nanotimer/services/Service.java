package com.cube.nanotimer.services;

import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.coach.CoachPayload;
import com.cube.nanotimer.coach.CoachPlan;
import com.cube.nanotimer.coach.StoredCoachPlan;
import com.cube.nanotimer.session.MethodStatistics;
import com.cube.nanotimer.vo.BackupCounts;
import com.cube.nanotimer.vo.CaseHistory;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.ExportResult;
import com.cube.nanotimer.vo.FrequencyData;
import com.cube.nanotimer.vo.ScrambleType;
import com.cube.nanotimer.vo.SessionDetails;
import com.cube.nanotimer.vo.SessionTimes;
import com.cube.nanotimer.vo.SolveAverages;
import com.cube.nanotimer.vo.SolveHistory;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveTimeAverages;
import com.cube.nanotimer.vo.SolveType;
import com.cube.nanotimer.vo.StepStats;
import com.cube.nanotimer.vo.TimesSort;
import com.cube.nanotimer.vo.drill.DrillCaseAttempt;
import com.cube.nanotimer.vo.drill.DrillCaseRep;
import com.cube.nanotimer.vo.drill.DrillCaseStats;
import com.cube.nanotimer.vo.drill.DrillCrossRep;
import com.cube.nanotimer.vo.drill.DrillEnd;
import com.cube.nanotimer.vo.drill.DrillRecord;

import java.util.List;
import java.util.Map;

public interface Service {
  void getCubeTypes(boolean getEmpty, DataCallback<List<CubeType>> callback);
  void getSolveTypes(CubeType cubeType, DataCallback<List<SolveType>> callback);
  /** Lifetime solve count per cube type id, for the puzzle picker. Absent means none. */
  void getSolvesCountPerCubeType(DataCallback<Map<Integer, Integer>> callback);
  /** Lifetime solve count per solve type id of one cube type, for the solve type picker. */
  void getSolvesCountPerSolveType(CubeType cubeType, DataCallback<Map<Integer, Integer>> callback);
  /** The same, for every solve type at once, for screens that show them all. */
  void getSolvesCountPerSolveType(DataCallback<Map<Integer, Integer>> callback);
  void saveTime(SolveTime solveTime, DataCallback<SolveAverages> callback);
  void deleteTime(SolveTime solveTime, DataCallback<SolveAverages> callback);
  void getSolveAverages(SolveType solveType, DataCallback<SolveAverages> callback);
  void getPagedHistory(SolveType solveType, TimesSort timesSort, DataCallback<SolveHistory> callback);
  void getPagedHistory(SolveType solveType, long from, TimesSort timesSort, DataCallback<SolveHistory> callback);
  void getHistory(SolveType solveType, long from, DataCallback<SolveHistory> callback);
  void getLastSolves(SolveType solveType, int count, DataCallback<SolveHistory> callback);
  void deleteHistory(DataCallback<Void> callback);
  void deleteHistory(SolveType solveType, DataCallback<Void> callback);
  void getSessionTimes(SolveType solveType, DataCallback<SessionTimes> callback);
  void getLastSolveTimes(SolveType solveType, int count, DataCallback<List<Long>> callback);
  void startNewSession(SolveType solveType, long startTs, DataCallback<Void> callback);
  void getSessionStart(SolveType solveType, DataCallback<Long> callback);
  void saveSolveTypesOrder(List<SolveType> solveTypes, DataCallback<Void> callback);
  void getSolveTimeAverages(SolveTime solveTime, DataCallback<SolveTimeAverages> callback);
  void getSessionDetails(SolveType solveType, DataCallback<SessionDetails> callback);
  void getSessionDetails(SolveType solveType, long from, long to, DataCallback<SessionDetails> callback);
  void getSessionStarts(SolveType solveType, DataCallback<List<Long>> callback);
  void getSolvesCount(SolveType solveType, DataCallback<Integer> callback);
  void getExportFile(List<Integer> solveTypeIds, int limit, DataCallback<List<ExportResult>> callback);
  void getSolveTime(int solveTimeId, DataCallback<SolveTime> callback);
  void getGyroTrack(int solveTimeId, DataCallback<String> callback);
  void getFrequencyData(SolveType solveType, long from, DataCallback<List<FrequencyData>> callback);
  /** What each step and case of a solve type's method has cost over its last {@code lastSolves} solves. */
  void getMethodStatistics(SolveType solveType, CubeMethod method, int lastSolves,
      DataCallback<MethodStatistics> callback);

  /** How many of a solve type's solves a smart cube drove, and so could be read again. */
  void getSmartcubeSolvesCount(SolveType solveType, DataCallback<Integer> callback);

  /**
   * Whether a smart cube has ever driven a solve, of any solve type. Asked by a screen deciding
   * whether the reader has figures of their own anywhere, which is not the same question as whether
   * the one it is showing has any.
   */
  void hasAnySmartcubeSolve(DataCallback<Boolean> callback);

  /**
   * A solve type's cube-driven solves, carrying what it takes to read them again: the scramble and
   * the move stream. Their stored breakdowns are left out, being the very thing a re-reading
   * replaces.
   */
  void getSmartcubeSolves(SolveType solveType, DataCallback<List<SolveTime>> callback);

  /** The same, across every solve type, for the solves stored as read under the given method. */
  void getSmartcubeSolves(CubeMethod method, DataCallback<List<SolveTime>> callback);

  /**
   * Writes the breakdowns of solves read again, all of them or none of them. A solve carrying no
   * method has its breakdown removed rather than replaced, which is what a solve that no longer
   * fits its type's method deserves.
   */
  void saveSmartcubeBreakdowns(List<SolveTime> solveTimes, DataCallback<Void> callback);

  /**
   * How many solves the coach has to read on this solve type, which is what says whether it can say
   * anything at all. Asked before a payload is built, so a history too young to speak from is told
   * so rather than made to press for an empty plan.
   */
  void getCoachSolveCount(SolveType solveType, CubeMethod method, DataCallback<Integer> callback);
  /**
   * The solver's history as a coach may see it: aggregated figures, vocabulary codes, and nothing a
   * user ever typed. What is too thin to stand is left out here rather than caveated later.
   */
  void getCoachPayload(SolveType solveType, CubeMethod method, DataCallback<CoachPayload> callback);
  /**
   * Keeps the last plan written for a solve type, and the payload it was written from, replacing
   * whatever that writer left before. A plan is stored so it reads without the network and without
   * being paid for twice.
   */
  void saveCoachPlan(SolveType solveType, StoredCoachPlan plan, DataCallback<Void> callback);
  /** The last plan that writer left for the solve type, or null where it has never written one. */
  void getCoachPlan(SolveType solveType, CoachPlan.Source source,
      DataCallback<StoredCoachPlan> callback);
  void getAllUsedScrambleTypes(DataCallback<Map<CubeType, List<ScrambleType>>> callback);

  /**
   * Opens a recorded drill and hands back the id its reps are stored against. Drills are kept
   * wholly apart from solves: nothing recorded here reaches the solve history, the session averages
   * or {@link #getMethodStatistics}, whose figures are the baseline a drill is measured against.
   */
  void addDrill(DrillRecord drill, DataCallback<Long> callback);
  void addDrillCaseRep(long drillId, DrillCaseRep rep, DataCallback<Void> callback);
  void addDrillCrossRep(long drillId, DrillCrossRep rep, DataCallback<Void> callback);
  /** Fills in the shortest solution for a cross rep whose search landed after the rep had ended. */
  void setDrillCrossRepOptimalLength(long drillId, int position, int optimalLength,
      DataCallback<Void> callback);
  /**
   * Throws a case rep out of every figure, or puts it back. The row stays either way, so the rep
   * can be restored and so a coach can still see that it was pruned.
   */
  void setDrillCaseRepDeleted(long drillId, int position, boolean deleted,
      DataCallback<Void> callback);
  /** Says how a drill stopped, once it has. One left without an end was never ended. */
  void endDrill(long drillId, DrillEnd end, DataCallback<Void> callback);
  void getDrills(int limit, DataCallback<List<DrillRecord>> callback);
  void getDrillCaseReps(long drillId, DataCallback<List<DrillCaseRep>> callback);
  void getDrillCrossReps(long drillId, DataCallback<List<DrillCrossRep>> callback);
  /** What each case has cost over the last {@code lastDrills} recorded drills. */
  void getDrillCaseStatistics(int lastDrills, DataCallback<List<StepStats>> callback);
  /** What each case has cost over the drills done since {@code fromTimestamp}, 0 for all of them. */
  void getDrillCaseStats(long fromTimestamp, DataCallback<List<DrillCaseStats>> callback);
  /** Every rep of one case since {@code fromTimestamp}, latest first, the pruned ones left out. */
  void getDrillCaseAttempts(String caseCode, long fromTimestamp,
      DataCallback<List<DrillCaseAttempt>> callback);

  /** The four figures a backup is described by, counted over the whole database. */
  void getBackupCounts(DataCallback<BackupCounts> callback);

  /**
   * Which cases the solver does unaided, and the solves the moves they turn are read out of.
   *
   * @param solves how many of the most recent smart-cube solves to bring back. Reading them is what
   *     costs, since each is replayed, so a screen asks for as many as it needs and no more.
   */
  void getCaseHistory(int solves, DataCallback<CaseHistory> callback);

  /**
   * The newest solves one case came up in, for the moves it was answered with. Far cheaper than the
   * whole history: a case turns up once in sixty solves and every solve read is one replayed.
   *
   * @param codes what the case is recorded under: the step handed it, and the part naming the
   *     algorithm that answers it, since a two-look records the second under the part alone
   */
  void getCaseSolves(List<String> codes, int solves, DataCallback<List<SolveTime>> callback);

  /**
   * The solves {@link #getMethodStatistics} reads over the same window, newest first, with the moves
   * to read them again by.
   */
  void getMethodSolves(SolveType solveType, CubeMethod method, int lastSolves,
      DataCallback<List<SolveTime>> callback);

  /**
   * Works out every case's status again from the solves and drill reps behind it. The statuses are
   * a cache, so this only ever has to be asked for when the rule that reads them has changed.
   */
  void refreshCaseKnowledge(DataCallback<Void> callback);

  void addSolveType(SolveType solveType, DataCallback<Integer> callback);
  void addSolveTypeSteps(SolveType solveType, DataCallback<Void> callback);
  void updateSolveType(SolveType solveType, boolean recalculateAverages, DataCallback<Void> callback);
  void deleteSolveType(SolveType solveType, DataCallback<Void> callback);

  ServiceProvider getProviderAccess();
}
