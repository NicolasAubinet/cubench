package com.cube.nanotimer.services;

import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.ExportResult;
import com.cube.nanotimer.vo.FrequencyData;
import com.cube.nanotimer.vo.ProgressListener;
import com.cube.nanotimer.vo.ScrambleType;
import com.cube.nanotimer.vo.SessionDetails;
import com.cube.nanotimer.vo.SolveAverages;
import com.cube.nanotimer.vo.SolveHistory;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveTimeAverages;
import com.cube.nanotimer.vo.SolveType;
import com.cube.nanotimer.vo.TimesSort;

import java.util.List;
import java.util.Map;

public interface ServiceProvider {
  List<CubeType> getCubeTypes(boolean getEmpty);
  List<SolveType> getSolveTypes(CubeType cubeType);
  SolveAverages saveTime(SolveTime solveTime);
  SolveAverages saveTimes(List<SolveTime> solveTimes, ProgressListener progressListener);
  SolveAverages getSolveAverages(SolveType solveType);
  SolveAverages deleteTime(SolveTime solveTime);
  SolveHistory getPagedHistory(SolveType solveType, TimesSort timesSort);
  SolveHistory getPagedHistory(SolveType solveType, Long from, TimesSort timesSort);
  SolveHistory getHistory(SolveType solveType, Long from);
  void deleteHistory();
  void deleteHistory(SolveType solveType);
  List<Long> getSessionTimes(SolveType solveType);
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
  List<FrequencyData> getFrequencyData(SolveType solveType, Long from);
  Map<CubeType, List<ScrambleType>> getAllUsedScrambleTypes();

  int addSolveType(SolveType solveType);
  void addSolveTypeSteps(SolveType solveType);
  void updateSolveType(SolveType solveType, boolean recalculateAverages);
  void deleteSolveType(SolveType solveType);
}
