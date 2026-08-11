package com.cube.nanotimer.services;

import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.session.MethodStatistics;
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

  void addSolveType(SolveType solveType, DataCallback<Integer> callback);
  void addSolveTypeSteps(SolveType solveType, DataCallback<Void> callback);
  void updateSolveType(SolveType solveType, boolean recalculateAverages, DataCallback<Void> callback);
  void deleteSolveType(SolveType solveType, DataCallback<Void> callback);

  ServiceProvider getProviderAccess();
}
