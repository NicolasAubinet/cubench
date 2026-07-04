package com.cube.nanotimer.services;

import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.ExportResult;
import com.cube.nanotimer.vo.FrequencyData;
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

public interface Service {
  void getCubeTypes(boolean getEmpty, DataCallback<List<CubeType>> callback);
  void getSolveTypes(CubeType cubeType, DataCallback<List<SolveType>> callback);
  void saveTime(SolveTime solveTime, DataCallback<SolveAverages> callback);
  void deleteTime(SolveTime solveTime, DataCallback<SolveAverages> callback);
  void getSolveAverages(SolveType solveType, DataCallback<SolveAverages> callback);
  void getPagedHistory(SolveType solveType, TimesSort timesSort, DataCallback<SolveHistory> callback);
  void getPagedHistory(SolveType solveType, long from, TimesSort timesSort, DataCallback<SolveHistory> callback);
  void getHistory(SolveType solveType, long from, DataCallback<SolveHistory> callback);
  void deleteHistory(DataCallback<Void> callback);
  void deleteHistory(SolveType solveType, DataCallback<Void> callback);
  void getSessionTimes(SolveType solveType, DataCallback<List<Long>> callback);
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
  void getFrequencyData(SolveType solveType, long from, DataCallback<List<FrequencyData>> callback);
  void getAllUsedScrambleTypes(DataCallback<Map<CubeType, List<ScrambleType>>> callback);

  void addSolveType(SolveType solveType, DataCallback<Integer> callback);
  void addSolveTypeSteps(SolveType solveType, DataCallback<Void> callback);
  void updateSolveType(SolveType solveType, boolean recalculateAverages, DataCallback<Void> callback);
  void deleteSolveType(SolveType solveType, DataCallback<Void> callback);

  ServiceProvider getProviderAccess();
}
