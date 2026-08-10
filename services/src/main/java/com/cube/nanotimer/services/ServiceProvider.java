package com.cube.nanotimer.services;

import com.cube.nanotimer.session.MethodStatistics;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.ExportResult;
import com.cube.nanotimer.vo.FrequencyData;
import com.cube.nanotimer.vo.ProgressListener;
import com.cube.nanotimer.vo.ScrambleType;
import com.cube.nanotimer.vo.SessionDetails;
import com.cube.nanotimer.vo.SessionTimes;
import com.cube.nanotimer.vo.SolveAverages;
import com.cube.nanotimer.vo.SolveHistory;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveTimeAverages;
import com.cube.nanotimer.vo.SolveType;
import com.cube.nanotimer.vo.StepStats;
import com.cube.nanotimer.vo.drill.DrillCaseRep;
import com.cube.nanotimer.vo.drill.DrillCrossRep;
import com.cube.nanotimer.vo.drill.DrillEnd;
import com.cube.nanotimer.vo.drill.DrillRecord;
import com.cube.nanotimer.vo.TimesSort;

import java.util.List;
import java.util.Map;

public interface ServiceProvider {
  List<CubeType> getCubeTypes(boolean getEmpty);
  List<SolveType> getSolveTypes(CubeType cubeType);
  Map<Integer, Integer> getSolvesCountPerCubeType();
  Map<Integer, Integer> getSolvesCountPerSolveType(CubeType cubeType);
  Map<Integer, Integer> getSolvesCountPerSolveType();
  SolveAverages saveTime(SolveTime solveTime);
  SolveAverages saveTimes(List<SolveTime> solveTimes, ProgressListener progressListener);
  SolveAverages getSolveAverages(SolveType solveType);
  SolveAverages deleteTime(SolveTime solveTime);
  SolveHistory getPagedHistory(SolveType solveType, TimesSort timesSort);
  SolveHistory getPagedHistory(SolveType solveType, Long from, TimesSort timesSort);
  SolveHistory getHistory(SolveType solveType, Long from);
  SolveHistory getLastSolves(SolveType solveType, int count);
  void deleteHistory();
  void deleteHistory(SolveType solveType);
  SessionTimes getSessionTimes(SolveType solveType);
  List<Long> getLastSolveTimes(SolveType solveType, int count);
  void startNewSession(SolveType solveType, long startTs);
  long getSessionStart(SolveType solveType);
  void saveSolveTypesOrder(List<SolveType> solveTypes);
  SolveTimeAverages getSolveTimeAverages(SolveTime solveTime);
  SessionDetails getSessionDetails(SolveType solveType, Long from, Long to);
  List<Long> getSessionStarts(SolveType solveType);
  int getSolvesCount(SolveType solveType);
  List<ExportResult> getExportResults(List<Integer> solveTypeIds, int limit);
  SolveTime getSolveTime(int solveTimeId);
  String getGyroTrack(int solveTimeId);
  List<FrequencyData> getFrequencyData(SolveType solveType, Long from);
  MethodStatistics getMethodStatistics(SolveType solveType, CubeMethod method, int lastSolves);
  Map<CubeType, List<ScrambleType>> getAllUsedScrambleTypes();

  /** Opens a recorded drill and hands back the id its reps are stored against. */
  long addDrill(DrillRecord drill);
  void addDrillCaseRep(long drillId, DrillCaseRep rep);
  void addDrillCrossRep(long drillId, DrillCrossRep rep);
  /** Fills in the shortest solution for a cross rep whose search landed after the rep had ended. */
  void setDrillCrossRepOptimalLength(long drillId, int position, int optimalLength);
  /** Says how a drill stopped, once it has. A drill left without one was never ended. */
  void endDrill(long drillId, DrillEnd end);
  List<DrillRecord> getDrills(int limit);
  List<DrillCaseRep> getDrillCaseReps(long drillId);
  List<DrillCrossRep> getDrillCrossReps(long drillId);
  /** What each case has cost over the last {@code lastDrills} recorded drills. */
  List<StepStats> getDrillCaseStatistics(int lastDrills);

  int addSolveType(SolveType solveType);
  void addSolveTypeSteps(SolveType solveType);
  void updateSolveType(SolveType solveType, boolean recalculateAverages);
  void deleteSolveType(SolveType solveType);
}
