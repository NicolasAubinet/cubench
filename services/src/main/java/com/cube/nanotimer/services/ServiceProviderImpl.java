package com.cube.nanotimer.services;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.cube.nanotimer.services.db.DB;
import com.cube.nanotimer.session.TimesStatistics;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.ExportResult;
import com.cube.nanotimer.vo.FrequencyData;
import com.cube.nanotimer.vo.ProgressListener;
import com.cube.nanotimer.vo.ScrambleType;
import com.cube.nanotimer.vo.SessionDetails;
import com.cube.nanotimer.vo.SolveAverages;
import com.cube.nanotimer.vo.SolveHistory;
import com.cube.nanotimer.vo.SolveStep;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveTimeAverages;
import com.cube.nanotimer.vo.SolveType;
import com.cube.nanotimer.vo.SolveTypeStep;
import com.cube.nanotimer.vo.TimesSort;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.TimeZone;

public class ServiceProviderImpl implements ServiceProvider {

  private SQLiteDatabase db;

  private SolveType currentSolveType;
  private List<CachedTime> cachedSolveTimes;
  private Map<Integer, Long> cachedBestAverages = new HashMap<Integer, Long>();
  private Long cachedLifetimeBest;

  private final Object cacheSyncHelper = new Object();

  private final int CACHE_MAX_SIZE = 1010;
  private final int CACHE_MIN_SIZE = 1000;
  private final int HISTORY_PAGE_SIZE = 20;
  private final int SESSION_TIMES_COUNT = 12;
  private final int MAX_AVERAGE_COUNT = 100;
  private final int MIN_TIMES_BEFORE_PB_FLAG = 12;

  public ServiceProviderImpl(SQLiteDatabase db) {
    this.db = db;
  }

  @Override
  public List<CubeType> getCubeTypes(boolean getEmpty) {
    List<CubeType> cubeTypes = new ArrayList<CubeType>();
    StringBuilder q = new StringBuilder();
    q.append("SELECT ").append(DB.COL_ID);
    q.append(" FROM ").append(DB.TABLE_CUBETYPE);
    if (!getEmpty) {
      q.append(" WHERE 0 < (");
      q.append("     SELECT COUNT(*) FROM ").append(DB.TABLE_SOLVETYPE);
      q.append("     WHERE ").append(DB.TABLE_SOLVETYPE).append(".").append(DB.COL_SOLVETYPE_CUBETYPE_ID);
      q.append("         = ").append(DB.TABLE_CUBETYPE).append(".").append(DB.COL_ID).append(")");
    }
    Cursor cursor = db.rawQuery(q.toString(), null);
    if (cursor != null) {
      for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
        cubeTypes.add(CubeType.getCubeType(cursor.getInt(0)));
      }
      cursor.close();
    }
    // Sort by enum declaration order so the puzzle list follows CubeType's order
    // rather than the DB id order (lets FTO appear after Megaminx despite its higher id).
    Collections.sort(cubeTypes, new Comparator<CubeType>() {
      @Override
      public int compare(CubeType c1, CubeType c2) {
        return Integer.compare(c1.ordinal(), c2.ordinal());
      }
    });
    return cubeTypes;
  }

  @Override
  public List<SolveType> getSolveTypes(CubeType cubeType) {
    List<SolveType> solveTypes = new ArrayList<SolveType>();
    StringBuilder q = new StringBuilder();
    q.append("SELECT ").append(DB.COL_ID);
    q.append(", ").append(DB.COL_SOLVETYPE_NAME);
    q.append(", ").append(DB.COL_SOLVETYPE_BLIND);
    q.append(", ").append(DB.COL_SOLVETYPE_SCRAMBLE_TYPE);
    q.append(", ").append(DB.COL_SOLVETYPE_CUBETYPE_ID);
    q.append(" FROM ").append(DB.TABLE_SOLVETYPE);
    q.append(" WHERE ").append(DB.COL_SOLVETYPE_CUBETYPE_ID).append(" = ?");
    q.append(" ORDER BY ").append(DB.COL_SOLVETYPE_POSITION);
    Cursor cursor = db.rawQuery(q.toString(), getStringArray(cubeType.getId()));
    if (cursor != null) {
      for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
        SolveType st = new SolveType(cursor.getInt(0), cursor.getString(1), (cursor.getInt(2) == 1), toScrambleType(cubeType, cursor.getString(3)), cursor.getInt(4));
        st.setSteps(getSolveTypeSteps(st.getId()).toArray(new SolveTypeStep[0]));
        solveTypes.add(st);
      }
      cursor.close();
    }
    return solveTypes;
  }

  private List<SolveTypeStep> getSolveTypeSteps(int solveTypeId) {
    List<SolveTypeStep> solveTypeSteps = new ArrayList<SolveTypeStep>();
    StringBuilder q = new StringBuilder();
    q.append("SELECT ").append(DB.COL_ID).append(", ").append(DB.COL_SOLVETYPESTEP_NAME);
    q.append(" FROM ").append(DB.TABLE_SOLVETYPESTEP);
    q.append(" WHERE ").append(DB.COL_SOLVETYPESTEP_SOLVETYPE_ID).append(" = ?");
    q.append(" ORDER BY ").append(DB.COL_SOLVETYPESTEP_POSITION);
    Cursor cursor = db.rawQuery(q.toString(), getStringArray(solveTypeId));
    if (cursor != null) {
      for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
        SolveTypeStep st = new SolveTypeStep();
        st.setId(cursor.getInt(0));
        st.setName(cursor.getString(1));
        solveTypeSteps.add(st);
      }
      cursor.close();
    }
    return solveTypeSteps;
  }

  @Override
  public SolveAverages saveTime(SolveTime solveTime) {
    return saveTime(solveTime, true);
  }

  public SolveAverages saveTime(SolveTime solveTime, boolean updateCachesFromDB) {
    syncCaches(solveTime.getSolveType());
    if (solveTime.getId() > 0) {
      return updateTime(solveTime);
    } else {
      return createTime(solveTime, updateCachesFromDB);
    }
  }

  @Override
  public SolveAverages saveTimes(List<SolveTime> solveTimes, ProgressListener progressListener) {
    if (solveTimes.size() == 0) {
      return null;
    }
    SolveType solveType = solveTimes.get(0).getSolveType();
    syncCaches(solveType);
    int i = 0;
    for (SolveTime solveTime : solveTimes) {
      createTime(solveTime, false);
      if (progressListener != null) {
        progressListener.onProgress(++i);
      }
    }
    return updateAveragesAndPBCaches(solveType);
  }

  private SolveAverages updateTime(SolveTime solveTime) {
    ContentValues values = new ContentValues();
    values.put(DB.COL_TIMEHISTORY_TIME, solveTime.getTime());
    values.put(DB.COL_TIMEHISTORY_PLUSTWO, solveTime.isPlusTwo() ? 1 : 0);
    values.put(DB.COL_TIMEHISTORY_COMMENT, (solveTime.getComment() != null) ? solveTime.getComment() : "");
    long previousBestTime = getBestTimeBefore(solveTime);
    boolean recheckPBs = false;
    if (isTimeBetter(previousBestTime, solveTime.getTime())) {
      if (!solveTime.isPb() && getTimesCountBefore(solveTime) >= MIN_TIMES_BEFORE_PB_FLAG) {
        solveTime.setPb(true);
      }
      recheckPBs = true;
    } else if (solveTime.isPb() && (solveTime.isDNF() || previousBestTime <= solveTime.getTime())) {
      // this isn't the best time anymore
      solveTime.setPb(false);
      recheckPBs = true;
    }
    if (!solveTime.isDNF()) {
      previousBestTime = Math.min(previousBestTime, solveTime.getTime());
    }
    values.put(DB.COL_TIMEHISTORY_PB, solveTime.isPb() ? 1 : 0);
    db.update(DB.TABLE_TIMEHISTORY, values, DB.COL_ID + " = ?", getStringArray(solveTime.getId()));
    synchronized (cacheSyncHelper) {
      for (CachedTime ct : cachedSolveTimes) {
        if (ct.getSolveId() == solveTime.getId()) {
          ct.setTime(solveTime.getTime());
          break;
        }
      }
    }

    recalculateAverages(solveTime.getTimestamp(), solveTime.getSolveType());

    loadBestAverages(solveTime.getSolveType().getId());
    loadLifetimeBest(solveTime.getSolveType().getId());

    if (recheckPBs) {
      checkNewerPBs(previousBestTime, solveTime);
    }

    SolveAverages solveAverages = getSolveAverages(solveTime.getSolveType());
    solveAverages.setSolveTime(solveTime);

    return solveAverages;
  }

  private void checkNewerPBs(long previousBestTime, SolveTime solveTime) {
    // Update PB flags of times newer than ts
    StringBuilder q = new StringBuilder();
    q.append("SELECT ").append(DB.COL_ID);
    q.append(" , ").append(DB.COL_TIMEHISTORY_TIME);
    q.append(" , ").append(DB.COL_TIMEHISTORY_TIMESTAMP);
    q.append(" , ").append(DB.COL_TIMEHISTORY_PB);
    q.append(" , ").append(DB.COL_TIMEHISTORY_SOLVETYPE_ID);
    q.append(" FROM ").append(DB.TABLE_TIMEHISTORY);
    q.append(" WHERE ").append(DB.COL_TIMEHISTORY_TIMESTAMP).append(" > ?");
    q.append("   AND ").append(DB.COL_TIMEHISTORY_SOLVETYPE_ID).append(" = ?");
    q.append(" ORDER BY ").append(DB.COL_TIMEHISTORY_TIMESTAMP);
    Cursor cursor = db.rawQuery(q.toString(), getStringArray(solveTime.getTimestamp(), solveTime.getSolveType().getId()));
    if (cursor != null) {
      long curBestTime = previousBestTime;
      boolean isMinTimesReached = false;
      for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
        int solveTimeId = cursor.getInt(0);
        long time = cursor.getInt(1);
        long timestamp = cursor.getLong(2);
        boolean pb = (cursor.getInt(3) == 1);
        int solveTypeId = cursor.getInt(4);
        boolean isBetter = isTimeBetter(curBestTime, time);
        if (isBetter && (isMinTimesReached || getTimesCountBefore(timestamp, solveTypeId) >= MIN_TIMES_BEFORE_PB_FLAG)) {
          if (!pb) {
            ContentValues values = new ContentValues();
            values.put(DB.COL_TIMEHISTORY_PB, 1);
            db.update(DB.TABLE_TIMEHISTORY, values, DB.COL_ID + " = ?", getStringArray(solveTimeId));
          }
          isMinTimesReached = true;
        } else if (pb) {
          ContentValues values = new ContentValues();
          values.put(DB.COL_TIMEHISTORY_PB, 0);
          db.update(DB.TABLE_TIMEHISTORY, values, DB.COL_ID + " = ?", getStringArray(solveTimeId));
        }
        if (isBetter) {
          curBestTime = time;
        }
      }
      cursor.close();
    }
  }

  private int getTimesCountBefore(SolveTime solveTime) {
    return getTimesCountBefore(solveTime.getTimestamp(), solveTime.getSolveType().getId());
  }

  private int getTimesCountBefore(long timestamp, int solveTypeId) {
    StringBuilder q = new StringBuilder();
    q.append("SELECT COUNT(*)");
    q.append(" FROM ").append(DB.TABLE_TIMEHISTORY);
    q.append(" WHERE ").append(DB.COL_TIMEHISTORY_TIMESTAMP).append(" < ?");
    q.append("   AND ").append(DB.COL_TIMEHISTORY_SOLVETYPE_ID).append(" = ?");
    Cursor cursor = db.rawQuery(q.toString(), getStringArray(timestamp, solveTypeId));
    int count = 0;
    if (cursor != null) {
      if (cursor.moveToFirst()) {
        count = cursor.getInt(0);
      }
      cursor.close();
    }
    return count;
  }

  private int getBestTimeBefore(SolveTime solveTime) {
    StringBuilder q = new StringBuilder();
    q.append("SELECT MIN(").append(DB.COL_TIMEHISTORY_TIME).append(")");
    q.append(" FROM ").append(DB.TABLE_TIMEHISTORY);
    q.append(" WHERE ").append(DB.COL_TIMEHISTORY_TIME).append(" > 0");
    q.append("   AND ").append(DB.COL_TIMEHISTORY_TIMESTAMP).append(" < ?");
    q.append("   AND ").append(DB.COL_TIMEHISTORY_SOLVETYPE_ID).append(" = ?");
    Cursor cursor = db.rawQuery(q.toString(), getStringArray(solveTime.getTimestamp(), solveTime.getSolveType().getId()));
    int time = -1;
    if (cursor != null) {
      if (cursor.moveToFirst()) {
        int val = cursor.getInt(0);
        if (val > 0) { // if something was found (min returns 0 otherwise)
          time = val;
        }
      }
      cursor.close();
    }
    return time;
  }

  private SolveAverages createTime(SolveTime solveTime, boolean updateCachesFromDB) {
    CachedTime cachedTime = new CachedTime(solveTime);
    synchronized (cacheSyncHelper) {
      int i;
      for (i = 0; i < cachedSolveTimes.size(); i++) {
        if (solveTime.getTimestamp() >= cachedSolveTimes.get(i).getTimestamp()) {
          break;
        }
      }
      cachedSolveTimes.add(i, cachedTime);
      if (cachedSolveTimes.size() > CACHE_MAX_SIZE) {
        cachedSolveTimes.remove(cachedSolveTimes.size() - 1);
      }
    }
    Long avg5;
    if (currentSolveType.isBlind()) {
      avg5 = getLastMean(3);
    } else {
      avg5 = getLastAvg(5);
    }
    Long avg12 = getLastAvg(12);
    Long avg50 = getLastAvg(50);
    Long avg100 = getLastAvg(100);

    syncBestAveragesWithCurrent(avg5, avg12, avg50, avg100);
    if (isTimeBetter(cachedLifetimeBest, solveTime.getTime()) && cachedSolveTimes.size() >= MIN_TIMES_BEFORE_PB_FLAG) {
      solveTime.setPb(true);
      cachedLifetimeBest = solveTime.getTime();
    }

    ContentValues values = new ContentValues();
    values.put(DB.COL_TIMEHISTORY_TIME, solveTime.getTime());
    values.put(DB.COL_TIMEHISTORY_SOLVETYPE_ID, solveTime.getSolveType().getId());
    values.put(DB.COL_TIMEHISTORY_SCRAMBLE, solveTime.getScramble());
    values.put(DB.COL_TIMEHISTORY_COMMENT, solveTime.getComment());
    values.put(DB.COL_TIMEHISTORY_TIMESTAMP, solveTime.getTimestamp());
    values.put(DB.COL_TIMEHISTORY_PLUSTWO, solveTime.isPlusTwo() ? 1 : 0);
    values.put(DB.COL_TIMEHISTORY_PB, solveTime.isPb() ? 1 : 0);
    values.put(DB.COL_TIMEHISTORY_AVG5, avg5);
    values.put(DB.COL_TIMEHISTORY_AVG12, avg12);
    values.put(DB.COL_TIMEHISTORY_AVG50, avg50);
    values.put(DB.COL_TIMEHISTORY_AVG100, avg100);
    if (solveTime.hasSmartcubeBreakdown()) {
      values.put(DB.COL_TIMEHISTORY_SMARTCUBE_METHOD, solveTime.getSmartcubeMethod().getCode());
    }
    if (solveTime.hasSmartcubeMoves()) { // kept even when no method matched: the breakdown is optional, the solution is not
      values.put(DB.COL_TIMEHISTORY_SMARTCUBE_MOVES, solveTime.getSmartcubeMoves());
    }
    // Only alongside a breakdown: on its own there are no steps for it to point into.
    if (solveTime.hasSmartcubeBreakdown() && solveTime.getSmartcubeStoppedStep() != null) {
      values.put(DB.COL_TIMEHISTORY_SMARTCUBE_STOPPED_STEP, solveTime.getSmartcubeStoppedStep());
    }
    long historyId = db.insert(DB.TABLE_TIMEHISTORY, null, values);
    if (solveTime.hasSteps()) {
      Iterator<SolveTypeStep> stsIt = getSolveTypeSteps(solveTime.getSolveType().getId()).iterator();
      for (Long stepTime : solveTime.getStepsTimes()) {
        values = new ContentValues();
        values.put(DB.COL_TIMEHISTORYSTEP_TIME, stepTime);
        values.put(DB.COL_TIMEHISTORYSTEP_SOLVETYPESTEP_ID, stsIt.next().getId());
        values.put(DB.COL_TIMEHISTORYSTEP_TIMEHISTORY_ID, historyId);
        db.insert(DB.TABLE_TIMEHISTORYSTEP, null, values);
      }
    }
    if (solveTime.hasSmartcubeBreakdown()) {
      insertSmartcubeSteps(historyId, solveTime.getSmartcubeSteps());
    }
    cachedTime.setSolveId((int) historyId);
    solveTime.setId((int) historyId);

    SolveAverages solveAverages = null;
    if (updateCachesFromDB) {
      solveAverages = updateAveragesAndPBCaches(solveTime.getSolveType());
      solveAverages.setSolveTime(solveTime);
    }
    return solveAverages;
  }

  private SolveAverages updateAveragesAndPBCaches(SolveType solveType) {
    if (solveType == null) {
      return null;
    }
    loadBestAverages(solveType.getId());
    loadLifetimeBest(solveType.getId());
    return getSolveAverages(solveType);
  }

  @Override
  public SolveAverages getSolveAverages(SolveType solveType) {
    syncCaches(solveType);
    SolveAverages solveAverages = new SolveAverages();
    if (solveType.hasSteps()) {
      setStepsAverages(solveAverages, solveType);
    } else if (solveType.isBlind()) {
      solveAverages.setMeanOf3(getLastMean(3));
      solveAverages.setBestOf3(cachedBestAverages.get(5)); // DB column avg5 contains the mean of 3 for blind
      Long[] averages = getSuccessAverages(new int[] { 12, 50, 100 });
      solveAverages.setAvgOf12(averages[0]);
      solveAverages.setAvgOf50(averages[1]);
      solveAverages.setAvgOf100(averages[2]);
      solveAverages.setAvgOfLifetime(getLastSuccessMean(1000, true));
      solveAverages.setBestOfLifetime(cachedLifetimeBest);
      solveAverages.setAccuracyOf12(getLastAccuracy(12, false));
      solveAverages.setAccuracyOf50(getLastAccuracy(50, false));
      solveAverages.setAccuracyOf100(getLastAccuracy(100, false));
      solveAverages.setLifetimeAccuracy(getLastAccuracy(1000, true));
    } else {
      solveAverages.setMeanOf3(getLastMean(3));
      solveAverages.setAvgOf5(getLastAvg(5));
      solveAverages.setAvgOf12(getLastAvg(12));
      solveAverages.setAvgOf50(getLastAvg(50));
      solveAverages.setAvgOf100(getLastAvg(100));
      solveAverages.setAvgOfLifetime(getLastSuccessMean(1000, true));
      solveAverages.setBestOf5(cachedBestAverages.get(5));
      solveAverages.setBestOf12(cachedBestAverages.get(12));
      solveAverages.setBestOf50(cachedBestAverages.get(50));
      solveAverages.setBestOf100(cachedBestAverages.get(100));
      solveAverages.setBestOfLifetime(cachedLifetimeBest);
    }
    return solveAverages;
  }

  void setStepsAverages(SolveAverages solveAverages, SolveType solveType) {
    if (!solveType.hasSteps()) {
      return;
    }
    List<List<Long>> stepsTimes = new ArrayList<List<Long>>();
    StringBuilder q = new StringBuilder();
    q.append("SELECT ").append(DB.TABLE_SOLVETYPESTEP).append(".").append(DB.COL_SOLVETYPESTEP_POSITION).append(",");
    q.append("       ").append(DB.TABLE_TIMEHISTORYSTEP).append(".").append(DB.COL_TIMEHISTORYSTEP_TIME);
    q.append(" FROM ").append(DB.TABLE_TIMEHISTORY);
    q.append(" JOIN ").append(DB.TABLE_TIMEHISTORYSTEP);
    q.append("   ON ").append(DB.TABLE_TIMEHISTORYSTEP).append(".").append(DB.COL_TIMEHISTORYSTEP_TIMEHISTORY_ID);
    q.append("    = ").append(DB.TABLE_TIMEHISTORY).append(".").append(DB.COL_ID);
    q.append(" JOIN ").append(DB.TABLE_SOLVETYPESTEP);
    q.append("   ON ").append(DB.TABLE_SOLVETYPESTEP).append(".").append(DB.COL_ID);
    q.append("    = ").append(DB.TABLE_TIMEHISTORYSTEP).append(".").append(DB.COL_TIMEHISTORYSTEP_SOLVETYPESTEP_ID);
    q.append(" WHERE ").append(DB.TABLE_TIMEHISTORY).append(".").append(DB.COL_TIMEHISTORY_SOLVETYPE_ID).append(" = ?");
    q.append("   AND ").append(DB.TABLE_TIMEHISTORY).append(".").append(DB.COL_TIMEHISTORY_TIME).append(" > 0");
    q.append(" ORDER BY ").append(DB.COL_SOLVETYPESTEP_POSITION).append(", ").append(DB.COL_TIMEHISTORY_TIMESTAMP).append(" DESC");
    Cursor cursor = db.rawQuery(q.toString(), getStringArray(solveType.getId()));
    if (cursor != null) {
      int curStep = -1;
      List<Long> times = new ArrayList<Long>();
      for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
        int step = cursor.getInt(0);
        if (step != curStep) {
          if (times.size() > 0) {
            stepsTimes.add(times);
          }
          times = new ArrayList<Long>();
          curStep = step;
        }
        times.add(cursor.getLong(1));
      }
      if (times.size() > 0) {
        stepsTimes.add(times);
      }
      cursor.close();
    }

    if (stepsTimes.size() > 0) {
      Map<Integer, List<Long>> averages = new HashMap<Integer, List<Long>>();
      long[] stepsSums = new long[stepsTimes.size()];
      int timesCount = stepsTimes.get(0).size();
      int[] avgsToGet = new int[] { 5, 12, 50, 100 };
      for (int i = 0; i < stepsTimes.size(); i++) {
        List<Long> st = stepsTimes.get(i);
        TimesStatistics session = new TimesStatistics(st);
        for (int a : avgsToGet) {
          if (averages.get(a) == null) {
            averages.put(a, new ArrayList<Long>());
          }
          averages.get(a).add(session.getAverageOf(a));
        }
        for (int j = 0; j < st.size(); j++) {
          stepsSums[i] += st.get(j);
        }
      }
      for (int a : avgsToGet) {
        List<Long> avgs = averages.get(a);
        if (avgs.get(0) == -2) {
          avgs = null;
        }
        solveAverages.setStepsAvgOf(a, avgs);
      }

      List<Long> lifeAvgs = new ArrayList<Long>();
      for (long l : stepsSums) {
        lifeAvgs.add(l / timesCount);
      }
      solveAverages.setStepsAvgOfLifetime(lifeAvgs);
    }
  }

  private void syncBestAveragesWithCurrent(Long avg5, Long avg12, Long avg50, Long avg100) {
    if (isTimeBetter(cachedBestAverages.get(5), avg5) || avg5 == null) {
      cachedBestAverages.put(5, avg5);
    }
    if (isTimeBetter(cachedBestAverages.get(12), avg12) || avg12 == null) {
      cachedBestAverages.put(12, avg12);
    }
    if (isTimeBetter(cachedBestAverages.get(50), avg50) || avg50 == null) {
      cachedBestAverages.put(50, avg50);
    }
    if (isTimeBetter(cachedBestAverages.get(100), avg100) || avg100 == null) {
      cachedBestAverages.put(100, avg100);
    }
  }

  private boolean isTimeBetter(Long oldVal, Long newVal) {
    if ((oldVal == null || oldVal == -1) && newVal != null && newVal > 0) {
      return true;
    } else if (oldVal != null && newVal != null && newVal < oldVal && newVal > 0) {
      return true;
    } else {
      return false;
    }
  }

  @Override
  public SolveAverages deleteTime(SolveTime solveTime) {
    int i = 0;
    syncCaches(solveTime.getSolveType());
    // remove from cache
    synchronized (cacheSyncHelper) {
      for (CachedTime ct : cachedSolveTimes) {
        if (ct.getSolveId() == solveTime.getId()) {
          cachedSolveTimes.remove(i);
          break;
        }
        i++;
      }
    }

    // remove from DB
    db.delete(DB.TABLE_TIMEHISTORYSTEP, DB.COL_TIMEHISTORYSTEP_TIMEHISTORY_ID + " = ?", getStringArray(solveTime.getId()));
    db.delete(DB.TABLE_SMARTCUBE_SOLVESTEP, DB.COL_SMARTCUBE_SOLVESTEP_TIMEHISTORY_ID + " = ?", getStringArray(solveTime.getId()));
    db.delete(DB.TABLE_TIMEHISTORY, DB.COL_ID + " = ?", getStringArray(solveTime.getId()));

    if (cachedSolveTimes.size() == CACHE_MIN_SIZE) { // re-read the cache if we reach the minimum size
      loadSolveTimes(solveTime.getSolveType().getId());
    }

    recalculateAverages(solveTime.getTimestamp(), solveTime.getSolveType());
    loadBestAverages(solveTime.getSolveType().getId());
    loadLifetimeBest(solveTime.getSolveType().getId());

    long previousBest = getBestTimeBefore(solveTime);
    if (isTimeBetter(previousBest, solveTime.getTime())) {
      checkNewerPBs(previousBest, solveTime);
    }

    return getSolveAverages(solveTime.getSolveType());
  }

  @Override
  public SolveHistory getPagedHistory(SolveType solveType, TimesSort timesSort) {
    return getPagedHistory(solveType, null, timesSort);
  }

  @Override
  public SolveHistory getPagedHistory(SolveType solveType, Long from, TimesSort timesSort) {
    SolveHistory solveHistory = new SolveHistory();
    solveHistory.setSolveTimes(getHistoryTimes(solveType, from, true, HISTORY_PAGE_SIZE, timesSort));
    solveHistory.setSolvesCount(getHistorySolvesCount(solveType));
    return solveHistory;
  }

  @Override
  public SolveHistory getHistory(SolveType solveType, Long from) {
    SolveHistory solveHistory = new SolveHistory();
    solveHistory.setSolveTimes(getHistoryTimes(solveType, from, false, null, TimesSort.TIMESTAMP));
    solveHistory.setSolvesCount(getHistorySolvesCount(solveType));
    return solveHistory;
  }

  public List<SolveTime> getHistoryTimes(SolveType solveType, Long from, boolean searchInPast, Integer pageSize, TimesSort timesSort) {
    List<SolveTime> history = new ArrayList<SolveTime>();
    StringBuilder q = new StringBuilder();
    q.append("SELECT ").append(DB.COL_ID);
    q.append("     , ").append(DB.COL_TIMEHISTORY_TIME);
    q.append("     , ").append(DB.COL_TIMEHISTORY_TIMESTAMP);
    q.append("     , ").append(DB.COL_TIMEHISTORY_SCRAMBLE);
    q.append("     , ").append(DB.COL_TIMEHISTORY_COMMENT);
    q.append("     , ").append(DB.COL_TIMEHISTORY_PLUSTWO);
    q.append("     , ").append(DB.COL_TIMEHISTORY_PB);
    q.append("     , ").append(DB.COL_TIMEHISTORY_SMARTCUBE_METHOD);
    q.append("     , ").append(DB.COL_TIMEHISTORY_SMARTCUBE_MOVES);
    q.append("     , ").append(DB.COL_TIMEHISTORY_SMARTCUBE_STOPPED_STEP);
    q.append(" FROM ").append(DB.TABLE_TIMEHISTORY);
    q.append(" WHERE ").append(DB.COL_TIMEHISTORY_SOLVETYPE_ID).append(" = ?");

    String sortColumn = DB.COL_TIMEHISTORY_TIMESTAMP;
    if (timesSort == TimesSort.TIME) {
      sortColumn = DB.COL_TIMEHISTORY_TIME;
    }
    if (from != null) {
      String fromSymbol = " < ";
      if (timesSort == TimesSort.TIMESTAMP && !searchInPast) {
        fromSymbol = " >= ";
      } else if (timesSort == TimesSort.TIME && searchInPast) {
        fromSymbol = " > ";
      }
      q.append("   AND ").append(sortColumn).append(" ").append(fromSymbol).append(" ?");
    }
    String sortOrder = " DESC";
    if (timesSort == TimesSort.TIME) {
      sortOrder = " ASC";
      q.append("   AND ").append(DB.COL_TIMEHISTORY_TIME).append(" > 0");
    }
    q.append(" ORDER BY ").append(sortColumn).append(sortOrder);
    if (pageSize != null) {
      q.append(" LIMIT ").append(HISTORY_PAGE_SIZE);
    }
    String[] params = (from == null) ? getStringArray(solveType.getId()) : getStringArray(solveType.getId(), from);
    Cursor cursor = db.rawQuery(q.toString(), params);
    if (cursor != null) {
      for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
        SolveTime st = new SolveTime();
        st.setId(cursor.getInt(0));
        st.setTime(cursor.getInt(1));
        st.setTimestamp(cursor.getLong(2));
        st.setScramble(cursor.getString(3));
        st.setComment(cursor.getString(4));
        st.setPlusTwo(cursor.getInt(5) == 1, false);
        st.setPb(cursor.getInt(6) == 1);
        st.setSolveType(solveType);
        if (solveType.hasSteps()) {
          List<Long> stepTimes = getSolveTimeSteps(st.getId());
          st.setStepsTimes(stepTimes.size() == 0 ? null : stepTimes.toArray(new Long[0]));
        }
        Integer stoppedStep = cursor.isNull(9) ? null : cursor.getInt(9);
        String method = cursor.getString(7);
        if (method != null) {
          st.setSmartcubeMethod(CubeMethod.fromCode(method));
          st.setSmartcubeSteps(getSmartcubeSteps(st.getId(), stoppedStep));
        }
        st.setSmartcubeMoves(cursor.getString(8));
        st.setSmartcubeStoppedStep(stoppedStep);
        history.add(st);
      }
      cursor.close();
    }
    return history;
  }

  private int getHistorySolvesCount(SolveType solveType) {
    int solvesCount = 0;
    StringBuilder q = new StringBuilder();
    q.append("SELECT COUNT(*)");
    q.append(" FROM ").append(DB.TABLE_TIMEHISTORY);
    q.append(" WHERE ").append(DB.COL_TIMEHISTORY_SOLVETYPE_ID).append(" = ?");
    Cursor cursor = db.rawQuery(q.toString(), getStringArray(solveType.getId()));
    if (cursor != null) {
      if (cursor.moveToFirst()) {
        solvesCount = cursor.getInt(0);
      }
      cursor.close();
    }
    return solvesCount;
  }

  private List<Long> getSolveTimeSteps(int solveTimeId) {
    List<Long> stepTimes = new ArrayList<Long>();
    StringBuilder q = new StringBuilder();
    q.append("SELECT ").append(DB.COL_TIMEHISTORYSTEP_TIME);
    q.append(" FROM ").append(DB.TABLE_TIMEHISTORYSTEP);
    q.append(" JOIN ").append(DB.TABLE_SOLVETYPESTEP);
    q.append("   ON ").append(DB.TABLE_SOLVETYPESTEP).append(".").append(DB.COL_ID);
    q.append("    = ").append(DB.TABLE_TIMEHISTORYSTEP).append(".").append(DB.COL_TIMEHISTORYSTEP_SOLVETYPESTEP_ID);
    q.append(" WHERE ").append(DB.COL_TIMEHISTORYSTEP_TIMEHISTORY_ID).append(" = ?");
    q.append(" ORDER BY ").append(DB.COL_SOLVETYPESTEP_POSITION);
    Cursor cursor = db.rawQuery(q.toString(), getStringArray(solveTimeId));
    if (cursor != null) {
      for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
        stepTimes.add(cursor.getLong(0));
      }
      cursor.close();
    }
    return stepTimes;
  }

  private void insertSmartcubeSteps(long historyId, List<SolveStep> steps) {
    for (SolveStep step : steps) {
      insertSmartcubeStep(historyId, step.getStepIndex(), null, step);
      List<SolveStep> subSteps = step.getSubSteps();
      for (int i = 0; i < subSteps.size(); i++) {
        insertSmartcubeStep(historyId, step.getStepIndex(), i, subSteps.get(i));
      }
    }
  }

  private void insertSmartcubeStep(long historyId, int stepIndex, Integer subIndex, SolveStep step) {
    ContentValues values = new ContentValues();
    values.put(DB.COL_SMARTCUBE_SOLVESTEP_STEP_INDEX, stepIndex);
    values.put(DB.COL_SMARTCUBE_SOLVESTEP_SUB_INDEX, subIndex); // null for the step itself
    values.put(DB.COL_SMARTCUBE_SOLVESTEP_NAME, step.getName());
    values.put(DB.COL_SMARTCUBE_SOLVESTEP_TIME, step.getTotalMs());
    values.put(DB.COL_SMARTCUBE_SOLVESTEP_RECOGNITION, step.getRecognitionMs());
    values.put(DB.COL_SMARTCUBE_SOLVESTEP_TIMEHISTORY_ID, historyId);
    db.insert(DB.TABLE_SMARTCUBE_SOLVESTEP, null, values);
  }

  /**
   * The method breakdown of one solve: a step per top-level row, its parts (the rows with a
   * sub_index) folded under it. NULL sub_index sorts first, so each step precedes its parts.
   *
   * @param stoppedStep the step the solve stopped in, null when it finished: that one step holds
   *     only the parts that were done, which is not visible in the rows themselves
   */
  private List<SolveStep> getSmartcubeSteps(int solveTimeId, Integer stoppedStep) {
    StringBuilder q = new StringBuilder();
    q.append("SELECT ").append(DB.COL_SMARTCUBE_SOLVESTEP_STEP_INDEX);
    q.append("     , ").append(DB.COL_SMARTCUBE_SOLVESTEP_SUB_INDEX);
    q.append("     , ").append(DB.COL_SMARTCUBE_SOLVESTEP_NAME);
    q.append("     , ").append(DB.COL_SMARTCUBE_SOLVESTEP_TIME);
    q.append("     , ").append(DB.COL_SMARTCUBE_SOLVESTEP_RECOGNITION);
    q.append("  FROM ").append(DB.TABLE_SMARTCUBE_SOLVESTEP);
    q.append(" WHERE ").append(DB.COL_SMARTCUBE_SOLVESTEP_TIMEHISTORY_ID).append(" = ?");
    q.append(" ORDER BY ").append(DB.COL_SMARTCUBE_SOLVESTEP_STEP_INDEX);
    q.append("     , ").append(DB.COL_SMARTCUBE_SOLVESTEP_SUB_INDEX);

    List<SolveStep> steps = new ArrayList<SolveStep>();
    List<SolveStep> subSteps = new ArrayList<SolveStep>();
    int stepIndex = -1;
    String name = null;
    long recognitionMs = 0;
    long timeMs = 0;
    boolean pending = false;
    Cursor cursor = db.rawQuery(q.toString(), getStringArray(solveTimeId));
    if (cursor != null) {
      for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
        if (cursor.isNull(1)) { // a step: close the previous one, then start this one
          if (pending) {
            steps.add(new SolveStep(stepIndex, name, recognitionMs, timeMs - recognitionMs, subSteps,
                stoppedStep == null || stoppedStep != stepIndex));
          }
          stepIndex = cursor.getInt(0);
          name = cursor.getString(2);
          timeMs = cursor.getLong(3);
          recognitionMs = cursor.getLong(4);
          subSteps = new ArrayList<SolveStep>();
          pending = true;
        } else { // a part of the step being read
          long partTime = cursor.getLong(3);
          long partRecognition = cursor.getLong(4);
          subSteps.add(new SolveStep(cursor.getInt(1), cursor.getString(2),
              partRecognition, partTime - partRecognition, new ArrayList<SolveStep>()));
        }
      }
      if (pending) {
        steps.add(new SolveStep(stepIndex, name, recognitionMs, timeMs - recognitionMs, subSteps,
            !Integer.valueOf(stepIndex).equals(stoppedStep)));
      }
      cursor.close();
    }
    return steps;
  }

  @Override
  public void deleteHistory() {
    db.delete(DB.TABLE_TIMEHISTORYSTEP, null, null);
    db.delete(DB.TABLE_SMARTCUBE_SOLVESTEP, null, null);
    db.delete(DB.TABLE_TIMEHISTORY, null, null);
    clearCaches();
  }

  @Override
  public void deleteHistory(SolveType solveType) {
    for (SolveTypeStep step : solveType.getSteps()) {
      db.delete(DB.TABLE_TIMEHISTORYSTEP, DB.COL_TIMEHISTORYSTEP_SOLVETYPESTEP_ID + " = ?", getStringArray(step.getId()));
    }
    db.delete(DB.TABLE_SMARTCUBE_SOLVESTEP,
        DB.COL_SMARTCUBE_SOLVESTEP_TIMEHISTORY_ID + " IN (SELECT " + DB.COL_ID + " FROM " + DB.TABLE_TIMEHISTORY
            + " WHERE " + DB.COL_TIMEHISTORY_SOLVETYPE_ID + " = ?)", getStringArray(solveType.getId()));
    db.delete(DB.TABLE_TIMEHISTORY, DB.COL_TIMEHISTORY_SOLVETYPE_ID + " = ?", getStringArray(solveType.getId()));
    if (solveType.equals(currentSolveType)) {
      clearCaches();
    }
  }

  /**
   * Returns the last session solve times.
   * This returns the last n times, searching from the last session start,
   * or from the beginning if no session was created.
   * The number of returned results is limited by SESSION_TIMES_COUNT.
   * @param solveType the solve type
   * @return the list of most recent solve times
   */
  @Override
  public List<Long> getSessionTimes(SolveType solveType) {
    return getSessionTimes(solveType, null, null, SESSION_TIMES_COUNT);
  }

  /**
   * Returns the last solve times for a solve type, across all sessions.
   * The most recent times come first; DNFs are kept (stored as negative values).
   * @param solveType the solve type
   * @param count the maximum number of times to return
   * @return the list of most recent solve times (newest first)
   */
  @Override
  public List<Long> getLastSolveTimes(SolveType solveType, int count) {
    return getSessionTimes(solveType, 0L, null, count);
  }

  public List<Long> getSessionTimes(SolveType solveType, Long from, Long to, Integer limit) {
    if (from == null) {
      from = getSessionStart(solveType);
    }
    List<Long> sessionTimes = new ArrayList<Long>();
    StringBuilder q = new StringBuilder();
    q.append("SELECT ").append(DB.COL_TIMEHISTORY_TIME);
    q.append(" FROM ").append(DB.TABLE_TIMEHISTORY);
    q.append(" WHERE ").append(DB.COL_TIMEHISTORY_SOLVETYPE_ID).append(" = ?");
    q.append("   AND ").append(DB.COL_TIMEHISTORY_TIMESTAMP).append(" >= ?");
    if (to != null) {
      q.append("   AND ").append(DB.COL_TIMEHISTORY_TIMESTAMP).append(" < ?");
    }
    q.append(" ORDER BY ").append(DB.COL_TIMEHISTORY_TIMESTAMP).append(" DESC");
    if (limit != null) {
      q.append(" LIMIT ").append(limit);
    }
    String[] params;
    if (to != null) {
      params = getStringArray(solveType.getId(), from, to);
    } else {
      params = getStringArray(solveType.getId(), from);
    }
    Cursor cursor = db.rawQuery(q.toString(), params);
    if (cursor != null) {
      for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
        sessionTimes.add(cursor.getLong(0));
      }
      cursor.close();
    }
    return sessionTimes;
  }

  @Override
  public void startNewSession(SolveType solveType, long startTs) {
    ContentValues values = new ContentValues();
    values.put(DB.COL_SESSION_START, startTs);
    values.put(DB.COL_SESSION_SOLVETYPE_ID, solveType.getId());
    db.insert(DB.TABLE_SESSION, null, values);
  }

  @Override
  public long getSessionStart(SolveType solveType) {
    long sessionStart = 0;
    StringBuilder q = new StringBuilder();
    q.append("SELECT MAX(").append(DB.COL_SESSION_START).append(")");
    q.append(" FROM ").append(DB.TABLE_SESSION);
    q.append(" WHERE ").append(DB.COL_SESSION_SOLVETYPE_ID).append(" = ?");
    Cursor cursor = db.rawQuery(q.toString(), getStringArray(solveType.getId()));
    if (cursor != null) {
      if (cursor.moveToFirst()) {
        sessionStart = cursor.getLong(0);
      }
      cursor.close();
    }
    return sessionStart;
  }

  @Override
  public int addSolveType(SolveType solveType) {
    int position = -1;
    // retrieve the position (add it as the last solve type)
    StringBuilder q = new StringBuilder();
    q.append("SELECT MAX(").append(DB.COL_SOLVETYPE_POSITION).append(")");
    q.append(" FROM ").append(DB.TABLE_SOLVETYPE);
    q.append(" WHERE ").append(DB.COL_SOLVETYPE_CUBETYPE_ID).append(" = ?");
    Cursor cursor = db.rawQuery(q.toString(), getStringArray(solveType.getCubeTypeId()));
    if (cursor != null) {
      if (cursor.moveToFirst()) {
        position = cursor.getInt(0);
        position++;
      }
      cursor.close();
    }
    if (position < 0) {
      position = 1;
    }

    ContentValues values = new ContentValues();
    values.put(DB.COL_SOLVETYPE_NAME, solveType.getName());
    values.put(DB.COL_SOLVETYPE_POSITION, position);
    values.put(DB.COL_SOLVETYPE_CUBETYPE_ID, solveType.getCubeTypeId());
    values.put(DB.COL_SOLVETYPE_BLIND, solveType.isBlind() ? 1 : 0);
    values.put(DB.COL_SOLVETYPE_SCRAMBLE_TYPE, (solveType.getScrambleType() != null ? solveType.getScrambleType().getName() : ""));
    int id = (int) db.insert(DB.TABLE_SOLVETYPE, null, values);
    solveType.setId(id);

    if (solveType.hasSteps()) {
      addSolveTypeSteps(solveType);
    }
    return id;
  }

  @Override
  public void addSolveTypeSteps(SolveType solveType) {
    // only add steps if it does not already have some (we can't edit the steps because it would mess up the times)
    if (solveType.getId() == 0 || getSolveTypeSteps(solveType.getId()).size() > 0) {
      return;
    }
    int i = 1;
    for (SolveTypeStep step : solveType.getSteps()) {
      ContentValues values = new ContentValues();
      values.put(DB.COL_SOLVETYPESTEP_NAME, step.getName());
      values.put(DB.COL_SOLVETYPESTEP_POSITION, i);
      values.put(DB.COL_SOLVETYPESTEP_SOLVETYPE_ID, solveType.getId());
      int stepId = (int) db.insert(DB.TABLE_SOLVETYPESTEP, null, values);
      step.setId(stepId);
      i++;
    }
  }

  @Override
  public void updateSolveType(SolveType solveType, boolean recalculateAverages) {
    ContentValues values = new ContentValues();
    values.put(DB.COL_SOLVETYPE_NAME, solveType.getName());
    values.put(DB.COL_SOLVETYPE_BLIND, solveType.isBlind() ? 1 : 0);
    values.put(DB.COL_SOLVETYPE_SCRAMBLE_TYPE, (solveType.getScrambleType() != null ? solveType.getScrambleType().getName() : ""));
    db.update(DB.TABLE_SOLVETYPE, values, DB.COL_ID + " = ?", getStringArray(solveType.getId()));
    if (recalculateAverages) {
      recalculateAverages(0, solveType);
      clearCaches();
    }
  }

  @Override
  public void deleteSolveType(SolveType solveType) {
    for (SolveTypeStep step : solveType.getSteps()) {
      db.delete(DB.TABLE_TIMEHISTORYSTEP, DB.COL_TIMEHISTORYSTEP_SOLVETYPESTEP_ID + " = ?", getStringArray(step.getId()));
    }
    db.delete(DB.TABLE_TIMEHISTORY, DB.COL_TIMEHISTORY_SOLVETYPE_ID + " = ?", getStringArray(solveType.getId()));
    db.delete(DB.TABLE_SOLVETYPESTEP, DB.COL_SOLVETYPESTEP_SOLVETYPE_ID + " = ?", getStringArray(solveType.getId()));
    db.delete(DB.TABLE_SOLVETYPE, DB.COL_ID + " = ?", getStringArray(solveType.getId()));
    if (solveType.equals(currentSolveType)) {
      clearCaches();
    }
  }

  @Override
  public void saveSolveTypesOrder(List<SolveType> solveTypes) {
    int i = 1;
    for (SolveType st : solveTypes) {
      ContentValues values = new ContentValues();
      values.put(DB.COL_SOLVETYPE_POSITION, i);
      db.update(DB.TABLE_SOLVETYPE, values, DB.COL_ID + " = ?", getStringArray(st.getId()));
      i++;
    }
  }

  @Override
  public SolveTimeAverages getSolveTimeAverages(SolveTime solveTime) {
    SolveTimeAverages sta = null;
    StringBuilder q = new StringBuilder();
    q.append("SELECT ").append(DB.COL_ID);
    q.append("     , ").append(DB.COL_TIMEHISTORY_TIME);
    q.append("     , ").append(DB.COL_TIMEHISTORY_TIMESTAMP);
    q.append("     , ").append(DB.COL_TIMEHISTORY_SCRAMBLE);
    q.append("     , ").append(DB.COL_TIMEHISTORY_COMMENT);
    q.append("     , ").append(DB.COL_TIMEHISTORY_PLUSTWO);
    q.append("     , ").append(DB.COL_TIMEHISTORY_PB);
    q.append("     , ").append(DB.COL_TIMEHISTORY_AVG5);
    q.append("     , ").append(DB.COL_TIMEHISTORY_AVG12);
    q.append("     , ").append(DB.COL_TIMEHISTORY_AVG50);
    q.append("     , ").append(DB.COL_TIMEHISTORY_AVG100);
    q.append(" FROM ").append(DB.TABLE_TIMEHISTORY);
    q.append(" WHERE ").append(DB.COL_ID).append(" = ?");
    Cursor cursor = db.rawQuery(q.toString(), getStringArray(solveTime.getId()));
    if (cursor != null) {
      cursor.moveToFirst();
      if (!cursor.isAfterLast()) {
        sta = new SolveTimeAverages();
        sta.setId(cursor.getInt(0));
        sta.setTime(cursor.getInt(1));
        sta.setTimestamp(cursor.getLong(2));
        sta.setScramble(cursor.getString(3));
        sta.setComment(cursor.getString(4));
        sta.setPlusTwo(cursor.getInt(5) == 1, false);
        sta.setPb(cursor.getInt(6) == 1);
        Long v = getCursorLong(cursor, 7);
        sta.setAvgOf5(v == null || v == -2 ? null : v);
        v = getCursorLong(cursor, 8);
        sta.setAvgOf12(v == null || v == -2 ? null : v);
        v = getCursorLong(cursor, 9);
        sta.setAvgOf50(v == null || v == -2 ? null : v);
        v = getCursorLong(cursor, 10);
        sta.setAvgOf100(v == null || v == -2 ? null : v);
        sta.setSolveType(solveTime.getSolveType());
      }
      cursor.close();
    }
    return sta;
  }

  /**
   * Returns the detailed list of session times, and the total solves count.
   * The list of times will only be set if a new session was created for this solve type.
   * It will be null if no session was ever created for this solve type.
   * @param solveType the solve type
   * @param from from when to get the session details (inclusive)
   * @param to to when to get the session details (exclusive)
   * @return the session details
   */
  @Override
  public SessionDetails getSessionDetails(SolveType solveType, Long from, Long to) {
    SessionDetails sessionDetails = new SessionDetails();
    sessionDetails.setTotalSolvesCount(getHistorySolvesCount(solveType));
    long sessionStart = getSessionStart(solveType);
    sessionDetails.setSessionStart(sessionStart);
    if (sessionStart > 0) { // if a new session was created
      sessionDetails.setSessionTimes(getSessionTimes(solveType, from, to, null));
    }
    return sessionDetails;
  }

  @Override
  public List<Long> getSessionStarts(SolveType solveType) {
    List<Long> sessionStarts = new ArrayList<Long>();
    StringBuilder q = new StringBuilder();
    q.append("SELECT ").append(DB.COL_SESSION_START);
    q.append(" FROM ").append(DB.TABLE_SESSION);
    q.append(" WHERE ").append(DB.COL_SESSION_SOLVETYPE_ID).append(" = ?");
    q.append(" ORDER BY ").append(DB.COL_SESSION_START).append(" DESC");
    Cursor cursor = db.rawQuery(q.toString(), getStringArray(solveType.getId()));
    if (cursor != null) {
      for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
        sessionStarts.add(cursor.getLong(0));
      }
      cursor.close();
    }
    return sessionStarts;
  }

  /**
   * Returns the solves count, starting from session start if a session was created, or from start if no session exists
   * @param solveType the solve type
   * @return the solves count
   */
  @Override
  public int getSolvesCount(SolveType solveType) {
    int solvesCount = 0;
    long sessionStart = getSessionStart(solveType);
    StringBuilder q = new StringBuilder();
    q.append("SELECT COUNT(*)");
    q.append(" FROM ").append(DB.TABLE_TIMEHISTORY);
    q.append(" WHERE ").append(DB.COL_TIMEHISTORY_SOLVETYPE_ID).append(" = ?");
    q.append("   AND ").append(DB.COL_TIMEHISTORY_TIMESTAMP).append(" >= ?");
    Cursor cursor = db.rawQuery(q.toString(), getStringArray(solveType.getId(), sessionStart));
    if (cursor != null) {
      if (cursor.moveToFirst()) {
        solvesCount = cursor.getInt(0);
      }
      cursor.close();
    }
    return solvesCount;
  }

  @Override
  public List<ExportResult> getExportResults(List<Integer> solveTypeIds, int limit) {
    List<ExportResult> results = new ArrayList<ExportResult>();
    for (Integer id : solveTypeIds) {
      StringBuilder q = new StringBuilder();
      q.append("SELECT ").append(DB.TABLE_TIMEHISTORY).append(".").append(DB.COL_ID);
      q.append("     , ").append(DB.TABLE_CUBETYPE).append(".").append(DB.COL_ID);
      q.append("     , ").append(DB.TABLE_CUBETYPE).append(".").append(DB.COL_CUBETYPE_NAME);
      q.append("     , ").append(DB.TABLE_SOLVETYPE).append(".").append(DB.COL_ID);
      q.append("     , ").append(DB.TABLE_SOLVETYPE).append(".").append(DB.COL_SOLVETYPE_NAME);
      q.append("     , ").append(DB.TABLE_TIMEHISTORY).append(".").append(DB.COL_TIMEHISTORY_TIME);
      q.append("     , ").append(DB.TABLE_TIMEHISTORY).append(".").append(DB.COL_TIMEHISTORY_TIMESTAMP);
      q.append("     , ").append(DB.TABLE_TIMEHISTORY).append(".").append(DB.COL_TIMEHISTORY_PLUSTWO);
      q.append("     , ").append(DB.TABLE_SOLVETYPE).append(".").append(DB.COL_SOLVETYPE_BLIND);
      q.append("     , ").append(DB.TABLE_SOLVETYPE).append(".").append(DB.COL_SOLVETYPE_SCRAMBLE_TYPE);
      q.append("     , ").append(DB.TABLE_TIMEHISTORY).append(".").append(DB.COL_TIMEHISTORY_SCRAMBLE);
      q.append("     , ").append(DB.TABLE_TIMEHISTORY).append(".").append(DB.COL_TIMEHISTORY_COMMENT);
      q.append("     , ").append(DB.TABLE_TIMEHISTORY).append(".").append(DB.COL_TIMEHISTORY_SMARTCUBE_METHOD);
      q.append("     , ").append(DB.TABLE_TIMEHISTORY).append(".").append(DB.COL_TIMEHISTORY_SMARTCUBE_MOVES);
      q.append("     , ").append(DB.TABLE_TIMEHISTORY).append(".").append(DB.COL_TIMEHISTORY_SMARTCUBE_STOPPED_STEP);
      q.append(" FROM ").append(DB.TABLE_TIMEHISTORY);
      q.append(" JOIN ").append(DB.TABLE_SOLVETYPE);
      q.append("   ON ").append(DB.COL_TIMEHISTORY_SOLVETYPE_ID);
      q.append("    = ").append(DB.TABLE_SOLVETYPE).append(".").append(DB.COL_ID);
      q.append(" JOIN ").append(DB.TABLE_CUBETYPE);
      q.append("   ON ").append(DB.COL_SOLVETYPE_CUBETYPE_ID);
      q.append("    = ").append(DB.TABLE_CUBETYPE).append(".").append(DB.COL_ID);
      q.append(" WHERE ").append(DB.COL_TIMEHISTORY_SOLVETYPE_ID).append(" = ?");
      q.append(" ORDER BY ").append(DB.COL_TIMEHISTORY_TIMESTAMP).append(" DESC");
      String[] params = getStringArray(id);
      if (limit > 0) {
        q.append(" LIMIT ?");
        params = getStringArray(id, limit);
      }
      List<ExportResult> curResults = new ArrayList<ExportResult>();
      Cursor cursor = db.rawQuery(q.toString(), params);
      if (cursor != null) {
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
          int cubeTypeId = cursor.getInt(1);
          ExportResult result = new ExportResult(cursor.getInt(0), cubeTypeId, cursor.getString(2), cursor.getInt(3),
              cursor.getString(4), cursor.getLong(5), cursor.getLong(6), (cursor.getInt(7) == 1), (cursor.getInt(8) == 1),
              cursor.getString(9), cursor.getString(10), cursor.getString(11));
          result.setSmartcubeMethod(CubeMethod.fromCode(cursor.getString(12)));
          result.setSmartcubeMoves(cursor.getString(13));
          result.setSmartcubeStoppedStep(cursor.isNull(14) ? null : cursor.getInt(14));
          curResults.add(result);
        }
        cursor.close();
      }
      List<SolveTypeStep> steps = getSolveTypeSteps(id);
      if (steps.size() > 0) { // if solve type has steps
        String[] stepsNames = new String[steps.size()];
        for (int i = 0; i < steps.size(); i++) {
          stepsNames[i] = steps.get(i).getName();
        }
        for (ExportResult r : curResults) {
          r.setStepsNames(stepsNames);
          r.setStepsTimes(getSolveTimeSteps(r.getSolveTimeId()).toArray(new Long[0]));
        }
      }
      for (ExportResult r : curResults) { // a method is only ever stored alongside its step rows
        if (r.getSmartcubeMethod() != null) {
          r.setSmartcubeSteps(getSmartcubeSteps(r.getSolveTimeId(), r.getSmartcubeStoppedStep()));
        }
      }
      results.addAll(curResults);
    }

    return results;
  }

  public SolveTime getSolveTime(int solveTimeId) {
    StringBuilder q = new StringBuilder();
    q.append("SELECT ").append(DB.TABLE_TIMEHISTORY).append(".").append(DB.COL_ID);
    q.append("     , ").append(DB.TABLE_TIMEHISTORY).append(".").append(DB.COL_TIMEHISTORY_TIME);
    q.append("     , ").append(DB.TABLE_TIMEHISTORY).append(".").append(DB.COL_TIMEHISTORY_TIMESTAMP);
    q.append("     , ").append(DB.TABLE_TIMEHISTORY).append(".").append(DB.COL_TIMEHISTORY_SCRAMBLE);
    q.append("     , ").append(DB.TABLE_TIMEHISTORY).append(".").append(DB.COL_TIMEHISTORY_COMMENT);
    q.append("     , ").append(DB.TABLE_TIMEHISTORY).append(".").append(DB.COL_TIMEHISTORY_PLUSTWO);
    q.append("     , ").append(DB.TABLE_TIMEHISTORY).append(".").append(DB.COL_TIMEHISTORY_PB);
    q.append("     , ").append(DB.TABLE_SOLVETYPE).append(".").append(DB.COL_ID);
    q.append("     , ").append(DB.TABLE_SOLVETYPE).append(".").append(DB.COL_SOLVETYPE_NAME);
    q.append("     , ").append(DB.TABLE_SOLVETYPE).append(".").append(DB.COL_SOLVETYPE_BLIND);
    q.append("     , ").append(DB.TABLE_SOLVETYPE).append(".").append(DB.COL_SOLVETYPE_SCRAMBLE_TYPE);
    q.append("     , ").append(DB.TABLE_SOLVETYPE).append(".").append(DB.COL_SOLVETYPE_CUBETYPE_ID);
    q.append(" FROM ").append(DB.TABLE_TIMEHISTORY);
    q.append(" JOIN ").append(DB.TABLE_SOLVETYPE);
    q.append("   ON ").append(DB.TABLE_TIMEHISTORY).append(".").append(DB.COL_TIMEHISTORY_SOLVETYPE_ID);
    q.append("    = ").append(DB.TABLE_SOLVETYPE).append(".").append(DB.COL_ID);
    q.append(" WHERE ").append(DB.TABLE_TIMEHISTORY).append(".").append(DB.COL_ID).append(" = ?");
    Cursor cursor = db.rawQuery(q.toString(), getStringArray(solveTimeId));
    SolveTime st = null;
    if (cursor != null) {
      if (cursor.moveToFirst()) {
        st = new SolveTime();
        st.setId(cursor.getInt(0));
        st.setTime(cursor.getInt(1));
        st.setTimestamp(cursor.getLong(2));
        st.setScramble(cursor.getString(3));
        st.setComment(cursor.getString(4));
        st.setPlusTwo(cursor.getInt(5) == 1, false);
        st.setPb(cursor.getInt(6) == 1);

        int cubeTypeId = cursor.getInt(11);
        CubeType cubeType = CubeType.getCubeType(cubeTypeId);
        st.setSolveType(new SolveType(cursor.getInt(7), cursor.getString(8), (cursor.getInt(9) == 1), toScrambleType(cubeType, cursor.getString(10)), cubeTypeId));
      }
      cursor.close();
    }
    return st;
  }

  public List<FrequencyData> getFrequencyData(SolveType solveType, Long from) {
    List<FrequencyData> frequencyData = new ArrayList<FrequencyData>();
    List<SolveTime> solveTimes = getHistory(solveType, from).getSolveTimes();
    if (solveTimes.isEmpty()) {
      return frequencyData;
    }
    long dayDuration = 24 * 60 * 60 * 1000;
    long dayStart = getDayStart((from == null || from == 0) ? solveTimes.get(solveTimes.size() - 1).getTimestamp() : from);
    long nextDayStart = dayStart + dayDuration;
    long currentTime = System.currentTimeMillis();
    while (dayStart < currentTime) {
      int solvesCount = 0;
      for (SolveTime solveTime : solveTimes) {
        long ts = solveTime.getTimestamp();
        if (ts >= dayStart && ts < nextDayStart) {
          solvesCount++;
        }
      }
      frequencyData.add(new FrequencyData(solvesCount, dayStart));

      dayStart = nextDayStart;
      nextDayStart = dayStart + dayDuration;
    }
    return frequencyData;
  }

  public Map<CubeType, List<ScrambleType>> getAllUsedScrambleTypes() {
    StringBuilder q = new StringBuilder();
    q.append("SELECT ").append(DB.TABLE_SOLVETYPE).append(".").append(DB.COL_ID);
    q.append("     , ").append(DB.TABLE_SOLVETYPE).append(".").append(DB.COL_SOLVETYPE_SCRAMBLE_TYPE);
    q.append("     , ").append(DB.TABLE_SOLVETYPE).append(".").append(DB.COL_SOLVETYPE_CUBETYPE_ID);
    q.append(" FROM ").append(DB.TABLE_SOLVETYPE);
    Cursor cursor = db.rawQuery(q.toString(), null);
    Map<CubeType, List<ScrambleType>> scrambleTypes = new HashMap<>();
    if (cursor != null) {
      for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
        String scrambleTypeStr = cursor.getString(1);
        CubeType cubeType = CubeType.getCubeType(cursor.getInt(2));
        ScrambleType scrambleType = toScrambleType(cubeType, scrambleTypeStr);
        if (scrambleType != null && !scrambleType.isDefault()) {
          List<ScrambleType> cubeScrambleTypes = scrambleTypes.get(cubeType);
          if (cubeScrambleTypes == null) {
            cubeScrambleTypes = new ArrayList<>();
            scrambleTypes.put(cubeType, cubeScrambleTypes);
          }
          cubeScrambleTypes.add(scrambleType);
        }
      }
      cursor.close();
    }
    return scrambleTypes;
  }

  private long getDayStart(long ts) {
    Calendar cal = Calendar.getInstance(TimeZone.getDefault());
    cal.setTimeInMillis(ts);
    cal.set(Calendar.HOUR_OF_DAY, 0);
    cal.set(Calendar.MINUTE, 0);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    return cal.getTimeInMillis();
  }

  private void recalculateAverages(long timestamp, SolveType solveType) { // TODO see if can improve (very slow, makes updating 100 times take from 1.2s (when method disabled) to 36s!) (see ServiceProviderTest.testDBPerfs())
    List<CachedTime> timesBefore = getTimesAroundTs(timestamp, solveType, true);
    List<CachedTime> timesAfter = getTimesAroundTs(timestamp, solveType, false);

    for (int i = timesAfter.size() - 1; i >= 0; i--) { // newest time is in pos 0
      CachedTime ct = timesAfter.get(i);
      List<Long> times = new ArrayList<Long>();
      for (int j = i; j < timesAfter.size() && times.size() < MAX_AVERAGE_COUNT; j++) {
        times.add(timesAfter.get(j).getTime());
      }
      for (int j = 0; j < timesBefore.size() && times.size() < MAX_AVERAGE_COUNT; j++) {
        times.add(timesBefore.get(j).getTime());
      }
      TimesStatistics session = new TimesStatistics(times);

      ContentValues values = new ContentValues();
      if (solveType.isBlind()) {
        values.put(DB.COL_TIMEHISTORY_AVG5, session.getMeanOf(3));
      } else {
        values.put(DB.COL_TIMEHISTORY_AVG5, session.getAverageOf(5));
      }
      values.put(DB.COL_TIMEHISTORY_AVG12, session.getAverageOf(12));
      values.put(DB.COL_TIMEHISTORY_AVG50, session.getAverageOf(50));
      values.put(DB.COL_TIMEHISTORY_AVG100, session.getAverageOf(100));

      db.update(DB.TABLE_TIMEHISTORY, values, DB.COL_ID + " = ?", getStringArray(ct.getSolveId()));
    }
  }

  private List<CachedTime> getTimesAroundTs(long timestamp, SolveType solveType, boolean before) {
    List<CachedTime> times = new ArrayList<CachedTime>();
    StringBuilder q = new StringBuilder();
    q.append("SELECT ").append(DB.COL_ID);
    q.append("     , ").append(DB.COL_TIMEHISTORY_TIME);
    q.append("     , ").append(DB.COL_TIMEHISTORY_TIMESTAMP);
    q.append(" FROM ").append(DB.TABLE_TIMEHISTORY);
    q.append(" WHERE ").append(DB.COL_TIMEHISTORY_SOLVETYPE_ID).append(" = ?");
    if (before) {
      q.append("   AND ").append(DB.COL_TIMEHISTORY_TIMESTAMP).append(" < ?");
    } else {
      q.append("   AND ").append(DB.COL_TIMEHISTORY_TIMESTAMP).append(" >= ?");
    }
    q.append(" ORDER BY ").append(DB.COL_TIMEHISTORY_TIMESTAMP).append(" DESC");
    if (before) { // only limit if searching before ts (otherwise it would not return the times right after ts if there are more times than MAX)
      q.append(" LIMIT ").append(MAX_AVERAGE_COUNT);
    }
    Cursor cursor = db.rawQuery(q.toString(), getStringArray(solveType.getId(), timestamp));
    if (cursor != null) {
      for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
        times.add(new CachedTime(cursor.getInt(0), cursor.getLong(1), cursor.getLong(2)));
      }
      cursor.close();
    }
    return times;
  }

  private void syncCaches(SolveType solveType) {
    if (solveType == null) {
      return;
    }
    if (cachedSolveTimes == null || currentSolveType == null || solveType.getId() != currentSolveType.getId()) {
      currentSolveType = solveType;
      loadSolveTimes(solveType.getId());
      loadBestAverages(solveType.getId());
      loadLifetimeBest(solveType.getId());
    }
  }

  private List<CachedTime> loadSolveTimes(int solveTypeId) {
    synchronized (cacheSyncHelper) {
      cachedSolveTimes = new ArrayList<CachedTime>();
      StringBuilder q = new StringBuilder();
      q.append("SELECT ").append(DB.COL_ID).append(", ").append(DB.COL_TIMEHISTORY_TIME).append(", ").append(DB.COL_TIMEHISTORY_TIMESTAMP);
      q.append(" FROM ").append(DB.TABLE_TIMEHISTORY);
      q.append(" WHERE ").append(DB.COL_TIMEHISTORY_SOLVETYPE_ID).append(" = ?");
      q.append(" ORDER BY ").append(DB.COL_TIMEHISTORY_TIMESTAMP).append(" DESC");
      q.append(" LIMIT ").append(CACHE_MAX_SIZE);
      Cursor cursor = db.rawQuery(q.toString(), getStringArray(solveTypeId));
      if (cursor != null) {
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
          cachedSolveTimes.add(new CachedTime(cursor.getInt(0), cursor.getLong(1), cursor.getLong(2)));
        }
        cursor.close();
      }
      return cachedSolveTimes;
    }
  }

  private Map<Integer, Long> loadBestAverages(int solveTypeId) {
    cachedBestAverages = new HashMap<Integer, Long>();
    Map<String, Integer> colToAvgMapping = new HashMap<String, Integer>();
    colToAvgMapping.put(DB.COL_TIMEHISTORY_AVG5, 5);
    colToAvgMapping.put(DB.COL_TIMEHISTORY_AVG12, 12);
    colToAvgMapping.put(DB.COL_TIMEHISTORY_AVG50, 50);
    colToAvgMapping.put(DB.COL_TIMEHISTORY_AVG100, 100);

    for (Entry<String, Integer> p : colToAvgMapping.entrySet()) {
      String colName = p.getKey();
      StringBuilder q = new StringBuilder();
      q.append("SELECT MIN(").append(colName).append(")");
      q.append(" FROM ").append(DB.TABLE_TIMEHISTORY);
      q.append(" WHERE ").append(DB.COL_TIMEHISTORY_SOLVETYPE_ID).append(" = ?");
      q.append("   AND ").append(colName).append(" > 0");
      Cursor cursor = db.rawQuery(q.toString(), getStringArray(solveTypeId));
      if (cursor != null) {
        if (cursor.moveToFirst()) {
          cachedBestAverages.put(p.getValue(), getCursorLong(cursor, 0));
        }
        cursor.close();
      }
    }
    return cachedBestAverages;
  }

  private Long loadLifetimeBest(int solveTypeId) {
    cachedLifetimeBest = -2l; // N/A
    StringBuilder q = new StringBuilder();
    q.append("SELECT MIN(").append(DB.COL_TIMEHISTORY_TIME).append(")");
    q.append(" FROM ").append(DB.TABLE_TIMEHISTORY);
    q.append(" WHERE ").append(DB.COL_TIMEHISTORY_SOLVETYPE_ID).append(" = ?");
    q.append("   AND ").append(DB.COL_TIMEHISTORY_TIME).append(" > 0");
    Cursor cursor = db.rawQuery(q.toString(), getStringArray(solveTypeId));
    if (cursor != null) {
      if (cursor.moveToFirst()) {
        cachedLifetimeBest = getCursorLong(cursor, 0);
      }
      cursor.close();
    }
    return cachedLifetimeBest;
  }

  /**
   * Returns the last averages of successes (non-DNF) for the current solve type.
   * Averages are returned in an array sorted in the same order than the parameters.
   * @param avgsToGet the averages to retrieve, sorted from small to big
   * @return the averages
   */
  private Long[] getSuccessAverages(int[] avgsToGet) {
    if (avgsToGet.length == 0) {
      return new Long[0];
    }
    int maxAvg = avgsToGet[avgsToGet.length - 1];
    Long[] averages = new Long[avgsToGet.length];
    List<Long> times = new ArrayList<Long>(maxAvg);
    StringBuilder q = new StringBuilder();
    q.append("SELECT ").append(DB.COL_TIMEHISTORY_TIME);
    q.append(" FROM ").append(DB.TABLE_TIMEHISTORY);
    q.append(" WHERE ").append(DB.COL_TIMEHISTORY_SOLVETYPE_ID).append(" = ?");
    q.append("   AND ").append(DB.COL_TIMEHISTORY_TIME).append(" > 0");
    q.append(" ORDER BY ").append(DB.COL_TIMEHISTORY_TIMESTAMP).append(" DESC");
    q.append(" LIMIT ?");
    Cursor cursor = db.rawQuery(q.toString(), getStringArray(currentSolveType.getId(), maxAvg));
    if (cursor != null) {
      for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
        times.add(cursor.getLong(0));
      }
      cursor.close();
    }
    TimesStatistics session = new TimesStatistics(times);
    for (int i = 0; i < avgsToGet.length; i++) {
      long avg = session.getAverageOf(avgsToGet[i]);
      averages[i] = (avg < 0) ? null : avg; // so that N/A (-2) is null
    }
    return averages;
  }

  private void clearCaches() {
    cachedSolveTimes = null;
    cachedBestAverages = new HashMap<Integer, Long>();
    cachedLifetimeBest = -2l;
  }

  /**
   * Calculate the last average based on the cached time.
   * The last average is the average of solve times, starting from the most recent time.
   * @param n number of solve times for which to retrieve the average
   * @return the last average
   */
  private Long getLastAvg(int n) {
    long avg = new TimesStatistics(getCachedTimes(n)).getAverageOf(n);
    return (avg == -2) ? null : avg;
  }

  /**
   * Calculates the last mean (not rolling average) based on the cached times.
   * @param n number of solve times for which to retrieve the mean
   * @return the mean of n
   */
  private Long getLastMean(int n) {
    long mean = new TimesStatistics(getCachedTimes(n)).getMeanOf(n);
    return (mean == -2) ? null : mean;
  }

  /**
   * Calculates the last mean (not rolling average) based on the cached times, only counting non DNF times.
   * @param n number of solve times for which to retrieve the mean
   * @param calculateAll if true, returns the mean even if the number of solves did not reach n
   * @return the mean of n
   */
  private Long getLastSuccessMean(int n, boolean calculateAll) {
    long mean = new TimesStatistics(getCachedTimes(n)).getSuccessMeanOf(n, calculateAll);
    return (mean == -2) ? null : mean;
  }

  /**
   * Calculate the accuracy (success rate) based on the cached times (used for blind mode).
   * @param n number of solve times for which to retrieve the accuracy
   * @param calculateAll if true, returns the accuracy even if the number of solves did not reach n
   * @return the last accuracy, from 0 to 100
   */
  private Integer getLastAccuracy(int n, boolean calculateAll) {
    int accuracy = new TimesStatistics(getCachedTimes(n)).getAccuracy(n, calculateAll);
    return (accuracy == -2) ? null : accuracy;
  }

  protected Long getCursorLong(Cursor cursor, int ind) {
    if (cursor == null) {
      return null;
    }
    if (cursor.isNull(ind)) {
      return null;
    } else {
      return cursor.getLong(ind);
    }
  }

  private String[] getStringArray(long... values) {
    String[] ret = new String[values.length];
    for (int i = 0; i < values.length; i++) {
      ret[i] = String.valueOf(values[i]);
    }
    return ret;
  }

  private List<Long> getCachedTimes(int max) {
    List<Long> times = new ArrayList<Long>();
    synchronized (cacheSyncHelper) {
      for (int i = 0; i < cachedSolveTimes.size() && i < max; i++) {
        times.add(cachedSolveTimes.get(i).getTime());
      }
    }
    return times;
  }

  private ScrambleType toScrambleType(CubeType cubeType, String scrambleTypeStr) {
    return cubeType.getScrambleTypeFromString(scrambleTypeStr);
  }

  boolean fakeTimesInserted = false;
  // Method used to insert times quickly (to make screenshots with times without creating them manually)
  private void insertTestTimes(int count, int min, int max, SolveType solveType) {
    if (!fakeTimesInserted) {
      long tsStart = System.currentTimeMillis() - 30000 * count;
      Random r = new Random();
      for (int i = 0; i < count; i++) {
        long time = min + r.nextInt(max - min);
//        int dnfPct = (i < 50 ? 35 : i < 100 ? 30 : i < 140 ? 26 : 22);
//        if (r.nextInt(100) < dnfPct) {
//          time = -1;
//        }
        SolveTime solveTime = new SolveTime();
        solveTime.setSolveType(solveType);
        solveTime.setScramble("U2 R2 L2");
        solveTime.setTime(time);
        solveTime.setTimestamp(tsStart + (i * 30000));
        saveTime(solveTime);
      }
      fakeTimesInserted = true;
    }
  }

  class CachedTime {
    private int solveId;
    private long time;
    private long timestamp;

    public CachedTime(SolveTime solveTime) {
      this.solveId = solveTime.getId();
      this.time = solveTime.getTime();
      this.timestamp = solveTime.getTimestamp();
    }

    public CachedTime(int solveId, long time, long timestamp) {
      this.solveId = solveId;
      this.time = time;
      this.timestamp = timestamp;
    }

    public int getSolveId() {
      return solveId;
    }

    public void setSolveId(int solveId) {
      this.solveId = solveId;
    }

    public long getTime() {
      return time;
    }

    public void setTime(long time) {
      this.time = time;
    }

    public long getTimestamp() {
      return timestamp;
    }
  }

}
