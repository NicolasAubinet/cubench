package com.cube.nanotimer.services;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import android.test.AndroidTestCase;
import org.junit.After;
import org.junit.Before;
import com.cube.nanotimer.session.MethodStatistics;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.FrequencyData;
import com.cube.nanotimer.vo.SolveStep;
import com.cube.nanotimer.vo.SolveAverages;
import com.cube.nanotimer.vo.SolveHistory;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveTimeAverages;
import com.cube.nanotimer.vo.SolveType;
import com.cube.nanotimer.vo.TimesSort;
import junit.framework.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class ServiceProviderTest extends AndroidTestCase {

  private ServiceProviderImpl provider;
  private ServiceImpl service;

  private static final String SCRAMBLE = "F B R L U D";
  private CubeType cubeType1;
  private SolveType solveType1;
  private SolveType solveType2;
  private SolveType solveTypeSteps;
  private SolveType solveTypeBlind;
  private int timeCpt = 0;

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    // AndroidJUnit4 drives this class, so the JUnit3 context AndroidTestCase would inject never
    // arrives; the runner's own application context stands in for it.
    service = ServiceImpl.getInstance(ApplicationProvider.getApplicationContext(), "testDB");
    provider = new ServiceProviderImpl(service.getWritableDatabase());

    List<CubeType> cubeTypes = provider.getCubeTypes(true);
    for (CubeType ct : cubeTypes) {
      if ("3x3x3".equals(ct.getName())) {
        cubeType1 = ct;
        List<SolveType> solveTypes = provider.getSolveTypes(ct);
        solveType1 = solveTypes.get(0);
        solveType2 = solveTypes.get(1);
        for (SolveType st : solveTypes) {
          if ("CFOP steps".equals(st.getName())) {
            solveTypeSteps = st;
          } else if ("BLD".equals(st.getName())) {
            solveTypeBlind = st;
          }
        }
      }
    }
    if (solveTypeBlind == null) {
      provider.addSolveType(new SolveType("BLD", true, null, cubeType1.getId()));
      for (SolveType st : provider.getSolveTypes(cubeType1)) {
        if ("BLD".equals(st.getName())) {
          solveTypeBlind = st;
        }
      }
    }
  }

  @After
  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    provider.deleteHistory();
  }

  @Test
  public void testBasicAverage() {
    provider.deleteHistory();
    SolveAverages averages = provider.getSolveAverages(solveType1);
    assertAvgEquals(null, null, null, null, null, null, null, null, null, null, averages);
    saveTime(600);
    saveTime(3000);
    saveTime(2000);
    averages = saveTime(2000);
    assertAvgEquals(null, null, null, null, 1900, null, null, null, null, 600, averages);
    averages = saveTime(2000);
    assertAvgEquals(2000, null, null, null, 1920, 2000, null, null, null, 600, averages);
    averages = saveTime(21000);
    assertAvgEquals(2333, null, null, null, 5100, 2000, null, null, null, 600, averages);
    saveTimes(2000, 11);
    averages = saveTime(14);
    assertAvgEquals(2000, 2000, null, null, 2923 /*52614/18*/, 2000, 2000, null, null, 14, averages);
    averages = saveTime(3000);
    assertAvgEquals(2000, 2000, null, null, 2927 /*55614/19*/, 2000, 2000, null, null, 14, averages);
    averages = saveTime(-1); // DNF
    assertAvgEquals(2333, 2100, null, null, 2927 /*55614/19*/, 2000, 2000, null, null, 14, averages);
    averages = saveTime(1000, solveType2);
    assertAvgEquals(null, null, null, null, 1000, null, null, null, null, 1000, averages);
    averages = saveTime(2000);
    assertAvgEquals(2333, 2100, null, null, 2880 /*57614/20*/, 2000, 2000, null, null, 14, averages);
    averages = saveTime(10);
    assertAvgEquals(1671, 1901, null, null, 2744 /*57624/21*/, 1671, 1901, null, null, 10, averages);

    averages = provider.getSolveAverages(solveType1);
    assertAvgEquals(1671, 1901, null, null, 2744 /*57624/21*/, 1671, 1901, null, null, 10, averages);

    averages = saveTime(10, solveType2);
    assertAvgEquals(null, null, null, null, 505, null, null, null, null, 10, averages);
    averages = saveTime(-1); // DNF
    assertAvgEquals(-1, -1, null, null, 2744 /*57624/21*/, 1671, 1901, null, null, 10, averages);

    averages = provider.getSolveAverages(solveType2);
    assertAvgEquals(null, null, null, null, 505, null, null, null, null, 10, averages);
  }

  @Test
  public void testTimeDeletion() {
    provider.deleteHistory();
    SolveAverages averages = saveTimes(1000, 4);
    assertAvgEquals(null, null, null, null, 1000, null, null, null, null, 1000, averages); // 1000(x4)
    averages = saveTime(1000);
    assertAvgEquals(1000, null, null, null, 1000, 1000, null, null, null, 1000, averages); // 1000(x5)
    averages = provider.deleteTime(getFirstTime());
    assertAvgEquals(null, null, null, null, 1000, null, null, null, null, 1000, averages); // 1000(x4)
    saveTime(1000);
    averages = saveTime(500);
    assertAvgEquals(1000, null, null, null, 916, 1000, null, null, null, 500, averages); // 1000(x5), 500(x1)
    averages = provider.deleteTime(getLastTime());
    assertAvgEquals(1000, null, null, null, 1000, 1000, null, null, null, 1000, averages); // 1000(x5)
    averages = provider.deleteTime(getLastTime());
    assertAvgEquals(null, null, null, null, 1000, null, null, null, null, 1000, averages); // 1000(x4)
    averages = saveTime(1000);
    assertAvgEquals(1000, null, null, null, 1000, 1000, null, null, null, 1000, averages); // 1000(x5)
    averages = saveTimes(300, 6);
    assertAvgEquals(300, null, null, null, 618, 300, null, null, null, 300, averages); // 1000(x5), 300(x6)
    averages = saveTime(300);
    assertAvgEquals(300, 580, null, null, 591, 300, 580, null, null, 300, averages); // 1000(x5), 300(x7)
    averages = saveTime(200);
    assertAvgEquals(300, 510, null, null, 561, 300, 510, null, null, 200, averages); // 1000(x5), 300(x7), 200(x1)

    List<SolveTime> times = getPagedHistory(solveType1).getSolveTimes();
    averages = provider.deleteTime(times.get(1)); // remove 300
    assertAvgEquals(300, 580, null, null, 583, 300, 580, null, null, 200, averages); // 1000(x5), 300(x6), 200(x1)

    times = getPagedHistory(solveType1).getSolveTimes();
    averages = provider.deleteTime(times.get(times.size() - 4)); // remove 1000
    assertAvgEquals(300, null, null, null, 545, 300, null, null, null, 200, averages); // 1000(x4), 300(x6), 200(x1)
    averages = saveTime(150);
    assertAvgEquals(266, 500, null, null, 512, 266, 500, null, null, 150, averages); // 1000(x4), 300(x6), 200(x1), 150(x1)

    times = getPagedHistory(solveType1).getSolveTimes();
    averages = provider.deleteTime(times.get(1)); // remove 200
    assertAvgEquals(300, null, null, null, 540, 300, null, null, null, 150, averages); // 1000(x4), 300(x6), 150(x1)

    times = getPagedHistory(solveType1).getSolveTimes();
    for (int i = 4; i < 10; i++) {
      provider.deleteTime(times.get(i));
    }
    averages = provider.getSolveAverages(solveType1);
    assertAvgEquals(300, null, null, null, 410, 300, null, null, null, 150, averages); // 1000(x1) 300(x3), 150(x1)
    provider.deleteTime(getLastTime());
    averages = provider.getSolveAverages(solveType1);
    assertAvgEquals(null, null, null, null, 475, null, null, null, null, 300, averages); // 1000(x1) 300(x3)

    times = getPagedHistory(solveType1).getSolveTimes();
    for (int i = 0; i < 3; i++) {
      provider.deleteTime(times.get(i));
    }
    averages = provider.getSolveAverages(solveType1);
    assertAvgEquals(null, null, null, null, 1000, null, null, null, null, 1000, averages); // 1000(x1)
    averages = provider.deleteTime(getFirstTime());
    assertAvgEquals(null, null, null, null, null, null, null, null, null, null, averages);

    saveTime(150);
    saveTime(250);
    saveTime(350);
    saveTime(450);
    averages = saveTime(550);
    assertAvgEquals(350, null, null, null, 350, 350, null, null, null, 150, averages); // 150, 250, 350, 450, 550
    averages = provider.deleteTime(getLastTime());
    assertAvgEquals(null, null, null, null, 300, null, null, null, null, 150, averages); // 150, 250, 350, 450
    averages = saveTime(700);
    assertAvgEquals(350, null, null, null, 380, 350, null, null, null, 150, averages); // 150, 250, 350, 450, 700
    averages = saveTime(800);
    assertAvgEquals(500, null, null, null, 450, 350, null, null, null, 150, averages); // 150, 250, 350, 450, 700, 800
    averages = saveTime(600);
    assertAvgEquals(583, null, null, null, 471, 350, null, null, null, 150, averages); // 150, 250, 350, 450, 700, 800, 600
    times = getPagedHistory(solveType1).getSolveTimes();
    averages = provider.deleteTime(times.get(2)); // remove 700
    assertAvgEquals(466, null, null, null, 433, 350, null, null, null, 150, averages); // 150, 250, 350, 450, 800, 600
    times = getPagedHistory(solveType1).getSolveTimes();
    averages = provider.deleteTime(times.get(3)); // remove 350
    assertAvgEquals(433, null, null, null, 450, 433, null, null, null, 150, averages); // 150, 250, 450, 800, 600
  }

  @Test
  public void testTimeModification() {
    provider.deleteHistory();
    SolveAverages averages = saveTimes(1000, 5);
    assertAvgEquals(1000, null, null, null, 1000, 1000, null, null, null, 1000, averages); // 1000(x5)

    SolveTime st = getFirstTime();
    st.setTime(3000);
    averages = provider.saveTime(st);
    assertAvgEquals(1000, null, null, null, 1400, 1000, null, null, null, 1000, averages); // 3000, 1000(x4)
    averages = saveTimes(500, 2);
    assertAvgEquals(833, null, null, null, 1142, 833, null, null, null, 500, averages); // 3000, 1000(x4), 500(x2)

    List<SolveTime> times = getPagedHistory(solveType1).getSolveTimes();
    times.get(3).setTime(-1);
    averages = provider.saveTime(times.get(3));
    assertAvgEquals(833, null, null, null, 1166, 833, null, null, null, 500, averages); // 3000, 1000(x2), -1, 1000, 500(x2)
    times.get(4).setTime(3000);
    averages = provider.saveTime(times.get(4));
    assertAvgEquals(1500, null, null, null, 1500, 1500, null, null, null, 500, averages); // 3000, 1000, 3000, -1, 1000, 500(x2)
    times.get(2).setTime(3000);
    averages = provider.saveTime(times.get(2));
    assertAvgEquals(2166, null, null, null, 1833, 2166, null, null, null, 500, averages); // 3000, 1000, 3000, -1, 3000, 500(x2)

    saveTime(4000);
    times = getPagedHistory(solveType1).getSolveTimes();
    times.get(7).setTime(1000);
    provider.saveTime(times.get(7));
    times.get(5).setTime(1000);
    averages = provider.saveTime(times.get(5));
    assertAvgEquals(2500, null, null, null, 1571, 1500, null, null, null, 500, averages); // 1000(x3), -1, 3000, 500(x2), 4000
    times.get(1).setTime(-1);
    averages = provider.saveTime(times.get(1));
    assertAvgEquals(-1, null, null, null, 1750, 1666, null, null, null, 500, averages); // 1000(x3), -1, 3000, 500, -1, 4000
    times.get(2).setTime(2500);
    averages = provider.saveTime(times.get(2));
    assertAvgEquals(-1, null, null, null, 2083, 1666, null, null, null, 1000, averages); // 1000(x3), -1, 3000, 2500, -1, 4000
    times.get(7).setTime(4000);
    averages = provider.saveTime(times.get(7));
    assertAvgEquals(-1, null, null, null, 2583, 2166, null, null, null, 1000, averages); // 4000, 1000(x2), -1, 3000, 2500, -1, 4000
  }

  @Test
  public void testDNF() {
    provider.deleteHistory();
    SolveAverages averages = saveTimes(1000, 5);
    assertAvgEquals(1000, null, null, null, 1000, 1000, null, null, null, 1000, averages); // 1000(x5)

    SolveTime time = getLastTime();
    time.setTime(-1);
    averages = provider.saveTime(time);
    assertAvgEquals(1000, null, null, null, 1000, 1000, null, null, null, 1000, averages); // 1000(x4), -1

    averages = saveTime(6000);
    assertAvgEquals(2666, null, null, null, 2000, 1000, null, null, null, 1000, averages); // 1000(x4), -1, 6000

    averages = saveTime(2000);
    assertAvgEquals(3000, null, null, null, 2000, 1000, null, null, null, 1000, averages); // 1000(x4), -1, 6000, 2000

    List<SolveTime> times = getPagedHistory(solveType1).getSolveTimes();
    times.get(3).setTime(-1);
    averages = provider.saveTime(times.get(3));
    assertAvgEquals(-1, null, null, null, 2200, null, null, null, null, 1000, averages); // 1000(x3), -1(x2), 6000, 2000

    time = getFirstTime();
    time.setTime(-1);
    averages = provider.saveTime(time);
    assertAvgEquals(-1, null, null, null, 2500, null, null, null, null, 1000, averages); // -1, 1000(x2), -1(x2), 6000, 2000

    averages = saveTime(500);
    assertAvgEquals(-1, null, null, null, 2100, null, null, null, null, 500, averages); // -1, 1000(x2), -1(x2), 6000, 2000, 500

    time = getLastTime();
    time.setTime(-1);
    averages = provider.saveTime(time);
    assertAvgEquals(-1, null, null, null, 2500, null, null, null, null, 1000, averages); // -1, 1000(x2), -1(x2), 6000, 2000, -1

    time = getPagedHistory(solveType1).getSolveTimes().get(6);
    time.setTime(-1);
    averages = provider.saveTime(time);
    assertAvgEquals(-1, null, null, null, 3000, null, null, null, null, 1000, averages); // -1(x2), 1000, -1(x2), 6000, 2000, -1

    time = getPagedHistory(solveType1).getSolveTimes().get(5);
    time.setTime(-1);
    averages = provider.saveTime(time);
    assertAvgEquals(-1, null, null, null, 4000, null, null, null, null, 2000, averages); // -1(x5), 6000, 2000, -1

    time = getPagedHistory(solveType1).getSolveTimes().get(1);
    time.setTime(-1);
    averages = provider.saveTime(time);
    assertAvgEquals(-1, null, null, null, 6000, null, null, null, null, 6000, averages); // -1(x5), 6000, -1(x2)

    time = getPagedHistory(solveType1).getSolveTimes().get(2);
    time.setTime(-1);
    averages = provider.saveTime(time);
    assertAvgEquals(-1, null, null, null, null, null, null, null, null, null, averages); // -1(x8)

    averages = saveTime(10);
    assertAvgEquals(-1, null, null, null, 10, null, null, null, null, 10, averages); // -1(x8), 10
    averages = saveTime(30);
    assertAvgEquals(-1, null, null, null, 20, null, null, null, null, 10, averages); // -1(x8), 10, 30
    averages = saveTime(50);
    assertAvgEquals(-1, null, null, null, 30, null, null, null, null, 10, averages); // -1(x8), 10, 30, 50
    averages = saveTime(70);
    assertAvgEquals(50, -1, null, null, 40, 50, null, null, null, 10, averages); // -1(x8), 10, 30, 50, 70
    provider.deleteTime(getLastTime());

    time = getLastTime();
    time.setTime(-1);
    averages = provider.saveTime(time);
    assertAvgEquals(-1, null, null, null, 20, null, null, null, null, 10, averages);  // -1(x8), 10, 30, -1

    time = getPagedHistory(solveType1).getSolveTimes().get(2);
    time.setTime(-1);
    averages = provider.saveTime(time);
    assertAvgEquals(-1, null, null, null, 30, null, null, null, null, 30, averages);  // -1(x9), 30, -1

    saveTime(50);
    averages = saveTime(100);
    assertAvgEquals(-1, -1, null, null, 60, null, null, null, null, 30, averages);  // -1(x9), 30, -1, 50, 100

    time = getLastTime();
    time.setTime(70);
    averages = provider.saveTime(time);
    assertAvgEquals(-1, -1, null, null, 50, null, null, null, null, 30, averages);  // -1(x9), 30, -1, 50, 70

    time = getPagedHistory(solveType1).getSolveTimes().get(3);
    time.setTime(60);
    averages = provider.saveTime(time);
    assertAvgEquals(-1, -1, null, null, 60, null, null, null, null, 50, averages);  // -1(x9), 60, -1, 50, 70
    averages = saveTime(80);
    assertAvgEquals(70, -1, null, null, 65, 70, null, null, null, 50, averages);  // -1(x9), 60, -1, 50, 70, 80

    provider.deleteHistory();
    averages = saveTimes(1000, 102);
    assertAvgEquals(1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, averages);
    times = getWholeHistory();
    time = times.get(10);
    time.setTime(-1);
    averages = provider.saveTime(time);
    assertAvgEquals(1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, averages);
    time = times.get(101);
    time.setTime(-1);
    averages = provider.saveTime(time);
    assertAvgEquals(1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, averages);
    time = times.get(98);
    time.setTime(-1);
    averages = provider.saveTime(time);
    assertAvgEquals(1000, 1000, 1000, -1, 1000, 1000, 1000, 1000, null, 1000, averages);

    // Test cache clearing
    provider.deleteHistory(solveType1);
    averages = saveTimes(1000, 102);
    assertAvgEquals(1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, averages);
    times = getWholeHistory();
    time = times.get(10);
    time.setTime(-1);
    averages = provider.saveTime(time);
    assertAvgEquals(1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, averages);
    time = times.get(101);
    time.setTime(-1);
    averages = provider.saveTime(time);
    assertAvgEquals(1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, averages);
  }

  @Test
  public void testLifetimeAverage() {
    provider.deleteHistory();
    saveTime(1000);
    saveTime(1000);
    SolveAverages averages = saveTimes(5000, 999);
    assertAvgEquals(5000, 5000, 5000, 5000, 4996, 3666, 4600, 4916, 4959, 1000, averages); // 1000(x2), 5000(x999)

    averages = saveTime(5000);
    assertAvgEquals(5000, 5000, 5000, 5000, 5000, 3666, 4600, 4916, 4959, 1000, averages); // 1000(x2), 5000(x1000)

    averages = saveTime(5000);
    assertAvgEquals(5000, 5000, 5000, 5000, 5000, 3666, 4600, 4916, 4959, 1000, averages); // 1000(x2), 5000(x1001)

    provider.deleteTime(getLastTime());
    averages = provider.deleteTime(getLastTime());
    assertAvgEquals(5000, 5000, 5000, 5000, 4996, 3666, 4600, 4916, 4959, 1000, averages); // 1000(x2), 5000(x999)
  }

  @Test
  public void testAvg50AndRecalculation() {
    provider.deleteHistory();
    SolveAverages averages = saveTimes(5000, 25);
    assertAvgEquals(5000, 5000, null, null, 5000, 5000, 5000, null, null, 5000, averages); // 5000(x25)
    averages = saveTimes(10000, 24);
    assertAvgEquals(10000, 10000, null, null, 7448, 5000, 5000, null, null, 5000, averages); // 5000(x25), 10000(x24)
    averages = saveTime(10000);
    assertAvgEquals(10000, 10000, 7500, null, 7500, 5000, 5000, 7500, null, 5000, averages); // 5000(x25), 10000(x25)
    averages = saveTime(10000);
    assertAvgEquals(10000, 10000, 7604, null, 7549, 5000, 5000, 7500, null, 5000, averages); // 5000(x25), 10000(x26)

    SolveTime time = getLastTime();
    time.setTime(-1);
    averages = provider.saveTime(time);
    assertAvgEquals(10000, 10000, 7604, null, 7500, 5000, 5000, 7500, null, 5000, averages); // 5000(x25), 10000(x25), DNF

    time = getPagedHistory(solveType1).getSolveTimes().get(5);
    time.setTime(12000);
    averages = provider.saveTime(time);
    assertAvgEquals(10000, 10200, 7645, null, 7540, 5000, 5000, 7500, null, 5000, averages); // 5000(x25), 10000(x20), 12000, 10000(x4), DNF

    List<SolveTime> times = getWholeHistory();
    time = times.get(times.size() - 5);
    time.setTime(10000);
    provider.saveTime(time);
    time = times.get(times.size() - 10);
    time.setTime(10000);
    provider.saveTime(time);
    time = times.get(times.size() - 15);
    time.setTime(10000);
    provider.saveTime(time);
    time = times.get(times.size() - 20);
    time.setTime(10000);
    provider.saveTime(time);
    time = times.get(times.size() - 25);
    time.setTime(10000);
    averages = provider.saveTime(time);
    assertAvgEquals(10000, 10200, 8166, null, 8040, 5000, 5500, 8020, null, 5000, averages); // (5000(x4), 10000)(x5), 10000(x20), 12000, 10000(x4), DNF
  }

  @Test
  public void testSolveTimeAveragesAndRecalculation() {
    provider.deleteHistory();
    saveTimes(1000, 5);
    List<SolveTime> times = getPagedHistory(solveType1).getSolveTimes();
    SolveTime time = times.get(0);
    time.setTime(3000);
    SolveAverages averages = provider.saveTime(time);
    assertAvgEquals(1000, null, null, null, 1400, 1000, null, null, null, 1000, averages); // 1000(x4), 3000
    assertSolveTimeAveragesEquals(1000, null, null, null, provider.getSolveTimeAverages(time));

    times = getPagedHistory(solveType1).getSolveTimes();
    time = times.get(3);
    time.setTime(800);
    averages = provider.saveTime(time);
    assertAvgEquals(1000, null, null, null, 1360, 1000, null, null, null, 800, averages); // 1000, 800, 1000(x2), 3000
    assertSolveTimeAveragesEquals(null, null, null, null, provider.getSolveTimeAverages(time));

    times = getPagedHistory(solveType1).getSolveTimes();
    time = times.get(0);
    time.setTime(-1);
    averages = provider.saveTime(time);
    assertAvgEquals(1000, null, null, null, 950, 1000, null, null, null, 800, averages); // 1000, 800, 1000(x2), -1
    assertSolveTimeAveragesEquals(1000, null, null, null, provider.getSolveTimeAverages(time));

    saveTimes(1000, 11);

    times = getPagedHistory(solveType1).getSolveTimes();
    time = times.get(0);
    time.setTime(-1);
    averages = provider.saveTime(time);
    assertAvgEquals(1000, -1, null, null, 985, 1000, 1000, null, null, 800, averages); // 1000, 800, 1000(x2), -1, 1000(x10), -1
    assertSolveTimeAveragesEquals(1000, -1, null, null, provider.getSolveTimeAverages(times.get(0)));
    assertSolveTimeAveragesEquals(1000, 1000, null, null, provider.getSolveTimeAverages(times.get(1)));
    assertSolveTimeAveragesEquals(1000, 1000, null, null, provider.getSolveTimeAverages(times.get(2)));
    assertSolveTimeAveragesEquals(1000, 1000, null, null, provider.getSolveTimeAverages(times.get(3)));
    assertSolveTimeAveragesEquals(1000, 1000, null, null, provider.getSolveTimeAverages(times.get(4)));
    assertSolveTimeAveragesEquals(1000, null, null, null, provider.getSolveTimeAverages(times.get(5)));

    provider.deleteTime(times.get(0)); // 1000, 800, 1000(x2), -1, 1000(x10)

    times = getPagedHistory(solveType1).getSolveTimes();
    time = times.get(2);
    time.setTime(-1);
    averages = provider.saveTime(time);
    assertAvgEquals(1000, -1, null, null, 984, 1000, 1000, null, null, 800, averages); // 1000, 800, 1000(x2), -1, 1000(x7), -1, 1000(x2)
    assertSolveTimeAveragesEquals(1000, -1, null, null, provider.getSolveTimeAverages(times.get(0)));
    assertSolveTimeAveragesEquals(1000, -1, null, null, provider.getSolveTimeAverages(times.get(1)));
    assertSolveTimeAveragesEquals(1000, -1, null, null, provider.getSolveTimeAverages(times.get(2)));
    assertSolveTimeAveragesEquals(1000, 1000, null, null, provider.getSolveTimeAverages(times.get(3)));
    assertSolveTimeAveragesEquals(1000, null, null, null, provider.getSolveTimeAverages(times.get(4)));

    provider.deleteHistory();

    averages = saveTimes(1000, 205);
    assertAvgEquals(1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, averages);
    times = getWholeHistory();
    assertSolveTimeAveragesEquals(1000, 1000, 1000, 1000, provider.getSolveTimeAverages(times.get(0)));
    assertSolveTimeAveragesEquals(1000, 1000, 1000, 1000, provider.getSolveTimeAverages(times.get(50)));
    assertSolveTimeAveragesEquals(1000, 1000, 1000, 1000, provider.getSolveTimeAverages(times.get(95)));
    assertSolveTimeAveragesEquals(1000, 1000, 1000, 1000, provider.getSolveTimeAverages(times.get(99)));
    assertSolveTimeAveragesEquals(1000, 1000, 1000, 1000, provider.getSolveTimeAverages(times.get(104)));
    assertSolveTimeAveragesEquals(1000, 1000, 1000, 1000, provider.getSolveTimeAverages(times.get(105)));
    assertSolveTimeAveragesEquals(1000, 1000, 1000, null, provider.getSolveTimeAverages(times.get(106)));
    assertSolveTimeAveragesEquals(1000, 1000, 1000, null, provider.getSolveTimeAverages(times.get(107)));

    time = times.get(110);
    time.setTime(-1);
    averages = provider.saveTime(time);
    assertAvgEquals(1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, averages);
    times = getWholeHistory();
    assertSolveTimeAveragesEquals(1000, 1000, 1000, 1000, provider.getSolveTimeAverages(times.get(0)));
    assertSolveTimeAveragesEquals(1000, 1000, 1000, 1000, provider.getSolveTimeAverages(times.get(50)));
    assertSolveTimeAveragesEquals(1000, 1000, 1000, 1000, provider.getSolveTimeAverages(times.get(95)));
    assertSolveTimeAveragesEquals(1000, 1000, 1000, 1000, provider.getSolveTimeAverages(times.get(99)));
    assertSolveTimeAveragesEquals(1000, 1000, 1000, 1000, provider.getSolveTimeAverages(times.get(103)));
    assertSolveTimeAveragesEquals(1000, 1000, 1000, 1000, provider.getSolveTimeAverages(times.get(104)));
    assertSolveTimeAveragesEquals(1000, 1000, 1000, 1000, provider.getSolveTimeAverages(times.get(105)));
    assertSolveTimeAveragesEquals(1000, 1000, 1000, null, provider.getSolveTimeAverages(times.get(106)));
    assertSolveTimeAveragesEquals(1000, 1000, 1000, null, provider.getSolveTimeAverages(times.get(107)));
    assertSolveTimeAveragesEquals(1000, 1000, 1000, null, provider.getSolveTimeAverages(times.get(108)));
    assertSolveTimeAveragesEquals(1000, 1000, 1000, 1000, provider.getSolveTimeAverages(times.get(90)));

    time = times.get(109);
    time.setTime(-1);
    averages = provider.saveTime(time);
    assertAvgEquals(1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, averages);
    times = getWholeHistory();
    assertSolveTimeAveragesEquals(1000, 1000, 1000, -1, provider.getSolveTimeAverages(times.get(59)));
    assertSolveTimeAveragesEquals(1000, 1000, 1000, -1, provider.getSolveTimeAverages(times.get(60)));
    assertSolveTimeAveragesEquals(1000, 1000, -1, -1, provider.getSolveTimeAverages(times.get(61)));
    assertSolveTimeAveragesEquals(1000, 1000, -1, -1, provider.getSolveTimeAverages(times.get(90)));
    assertSolveTimeAveragesEquals(1000, 1000, -1, -1, provider.getSolveTimeAverages(times.get(98)));
    assertSolveTimeAveragesEquals(1000, -1, -1, -1, provider.getSolveTimeAverages(times.get(99)));
    assertSolveTimeAveragesEquals(1000, -1, -1, -1, provider.getSolveTimeAverages(times.get(103)));
    assertSolveTimeAveragesEquals(1000, -1, -1, -1, provider.getSolveTimeAverages(times.get(104)));
    assertSolveTimeAveragesEquals(1000, -1, -1, -1, provider.getSolveTimeAverages(times.get(105)));
    assertSolveTimeAveragesEquals(-1, -1, -1, null, provider.getSolveTimeAverages(times.get(106)));
    assertSolveTimeAveragesEquals(-1, -1, -1, null, provider.getSolveTimeAverages(times.get(107)));
    assertSolveTimeAveragesEquals(-1, -1, -1, null, provider.getSolveTimeAverages(times.get(108)));
    assertSolveTimeAveragesEquals(-1, -1, -1, null, provider.getSolveTimeAverages(times.get(109)));
    assertSolveTimeAveragesEquals(1000, 1000, 1000, null, provider.getSolveTimeAverages(times.get(110)));

    time = times.get(204);
    time.setTime(-1);
    averages = provider.saveTime(time);
    assertAvgEquals(1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, averages);
    times = getWholeHistory();
    assertSolveTimeAveragesEquals(1000, 1000, -1, -1, provider.getSolveTimeAverages(times.get(90)));
    assertSolveTimeAveragesEquals(1000, -1, -1, -1, provider.getSolveTimeAverages(times.get(102)));
    assertSolveTimeAveragesEquals(1000, -1, -1, -1, provider.getSolveTimeAverages(times.get(103)));
    assertSolveTimeAveragesEquals(1000, -1, -1, -1, provider.getSolveTimeAverages(times.get(104)));
    assertSolveTimeAveragesEquals(1000, -1, -1, -1, provider.getSolveTimeAverages(times.get(105)));
    assertSolveTimeAveragesEquals(-1, -1, -1, null, provider.getSolveTimeAverages(times.get(106)));
    assertSolveTimeAveragesEquals(null, null, null, null, provider.getSolveTimeAverages(times.get(204))); // oldest one, now DNF
    assertSolveTimeAveragesEquals(null, null, null, null, provider.getSolveTimeAverages(times.get(203)));
    assertSolveTimeAveragesEquals(null, null, null, null, provider.getSolveTimeAverages(times.get(201)));
    assertSolveTimeAveragesEquals(1000, null, null, null, provider.getSolveTimeAverages(times.get(200)));
    assertSolveTimeAveragesEquals(1000, null, null, null, provider.getSolveTimeAverages(times.get(199)));
    assertSolveTimeAveragesEquals(1000, 1000, null, null, provider.getSolveTimeAverages(times.get(193)));
    assertSolveTimeAveragesEquals(1000, 1000, null, null, provider.getSolveTimeAverages(times.get(192)));
    assertSolveTimeAveragesEquals(1000, 1000, null, null, provider.getSolveTimeAverages(times.get(191)));
    assertSolveTimeAveragesEquals(1000, 1000, null, null, provider.getSolveTimeAverages(times.get(156)));
    assertSolveTimeAveragesEquals(1000, 1000, 1000, null, provider.getSolveTimeAverages(times.get(155)));
    assertSolveTimeAveragesEquals(1000, 1000, 1000, null, provider.getSolveTimeAverages(times.get(154)));
    assertSolveTimeAveragesEquals(1000, 1000, 1000, null, provider.getSolveTimeAverages(times.get(153)));

    time = times.get(101);
    time.setTime(3000); // +2
    averages = provider.saveTime(time);
    assertAvgEquals(1000, 1000, 1000, 1000, 1009, 1000, 1000, 1000, 1000, 1000, averages);
    times = getWholeHistory();
    assertSolveTimeAveragesEquals(1000, 1000, 1000, 1000, provider.getSolveTimeAverages(times.get(0)));
    assertSolveTimeAveragesEquals(1000, 1000, 1000, 1000, provider.getSolveTimeAverages(times.get(1)));
    assertSolveTimeAveragesEquals(1000, 1000, 1000, 1000, provider.getSolveTimeAverages(times.get(2)));
    assertSolveTimeAveragesEquals(1000, 1000, 1000, 1020, provider.getSolveTimeAverages(times.get(10)));
    assertSolveTimeAveragesEquals(1000, -1, -1, -1, provider.getSolveTimeAverages(times.get(100)));
    assertSolveTimeAveragesEquals(1000, -1, -1, -1, provider.getSolveTimeAverages(times.get(101)));
    assertSolveTimeAveragesEquals(1000, -1, -1, -1, provider.getSolveTimeAverages(times.get(102)));
  }

  @Test
  public void testStepsAverages() {
    provider.deleteHistory();
    SolveAverages averages = saveStepTimes(10, 20, 30, 40);
    assertNull(averages.getStepsAvgOf5());
    assertNull(averages.getStepsAvgOf12());
    assertNull(averages.getStepsAvgOf50());
    assertNull(averages.getStepsAvgOf100());
    assertStepsEquals(averages.getStepsAvgOfLifetime(), 10, 20, 30, 40);

    averages = saveStepTimes(12, 22, 28, 42);
    assertStepsEquals(averages.getStepsAvgOfLifetime(), 11, 21, 29, 41);

    averages = saveStepTimes(8, 18, 32, 38);
    assertStepsEquals(averages.getStepsAvgOfLifetime(), 10, 20, 30, 40);

    averages = saveStepTimes(-1, -1, -1, -1); // to simulate a DNF (not taken into account)
    assertStepsEquals(averages.getStepsAvgOfLifetime(), 10, 20, 30, 40);
    assertNull(averages.getStepsAvgOf5());

    averages = saveStepTimes(30, 40, 50, 60);
    assertStepsEquals(averages.getStepsAvgOfLifetime(), 15, 25, 35, 45);
    assertNull(averages.getStepsAvgOf5());

    averages = saveStepTimes(10, 20, 30, 40);
    assertStepsEquals(averages.getStepsAvgOfLifetime(), 14, 24, 34, 44);
    assertStepsEquals(averages.getStepsAvgOf5(), 10, 20, 30, 40);

    averages = saveStepTimes(50, 30, 10, 20);
    assertStepsEquals(averages.getStepsAvgOfLifetime(), 20, 25, 30, 40);
    assertStepsEquals(averages.getStepsAvgOf5(), 17, 24, 30, 40);

    averages = saveStepTimes(60, 40, 20, 30);
    assertStepsEquals(averages.getStepsAvgOfLifetime(), 25, 27, 28, 38);
    assertStepsEquals(averages.getStepsAvgOf5(), 30, 30, 27, 36);

    for (int i = 0; i < 96; i++) {
      averages = saveStepTimes(40, 45, 35, 50);
    }
    assertStepsEquals(averages.getStepsAvgOfLifetime(), 39, 43, 34, 49);
    assertStepsEquals(averages.getStepsAvgOf5(), 40, 45, 35, 50);
    assertStepsEquals(averages.getStepsAvgOf12(), 40, 45, 35, 50);
    assertStepsEquals(averages.getStepsAvgOf50(), 40, 45, 35, 50);
    assertStepsEquals(averages.getStepsAvgOf100(), 40, 44, 34, 49);
  }

  @Test
  public void testBlindMeanOf3() {
    provider.deleteHistory();

    saveTime(1000, solveTypeBlind);
    SolveAverages averages = saveTime(2000, solveTypeBlind);
    assertEquals(null, averages.getMeanOf3());
    averages = saveTime(2500, solveTypeBlind);
    assertEquals(Long.valueOf(1833), averages.getMeanOf3());
    averages = saveTime(2700, solveTypeBlind);
    assertEquals(Long.valueOf(2400), averages.getMeanOf3());
    averages = saveTime(1200, solveTypeBlind);
    assertEquals(Long.valueOf(2133), averages.getMeanOf3());
    averages = saveTime(-1, solveTypeBlind);
    assertEquals(Long.valueOf(-1), averages.getMeanOf3());
    averages = saveTime(1400, solveTypeBlind);
    assertEquals(Long.valueOf(-1), averages.getMeanOf3());
    averages = saveTime(1500, solveTypeBlind);
    assertEquals(Long.valueOf(-1), averages.getMeanOf3());
    averages = saveTime(1300, solveTypeBlind);
    assertEquals(Long.valueOf(1400), averages.getMeanOf3());
    averages = saveTime(1200, solveTypeBlind);
    assertEquals(Long.valueOf(1333), averages.getMeanOf3());
    averages = saveTime(2200, solveTypeBlind);
    assertEquals(Long.valueOf(1566), averages.getMeanOf3());
    averages = saveTime(2600, solveTypeBlind);
    assertEquals(Long.valueOf(2000), averages.getMeanOf3());
  }

  @Test
  public void testHistory() {
    provider.deleteHistory();
    for (int i = 65; i >= 1; i--) {
      saveTime(100 * i);
    }
    // Paged history
    SolveHistory solveHistory = getPagedHistory(solveType1);
    assertEquals(solveHistory.getSolvesCount(), 65);
    List<SolveTime> solveTimes = solveHistory.getSolveTimes();
    assertEquals(solveTimes.size(), 20);
    assertEquals(solveTimes.get(0).getTime(), 100);
    assertEquals(solveTimes.get(19).getTime(), 2000);

    solveHistory = getPagedHistory(solveType1, solveTimes.get(solveTimes.size() - 1).getTimestamp());
    assertEquals(solveHistory.getSolvesCount(), 65);
    solveTimes = solveHistory.getSolveTimes();
    assertEquals(solveTimes.size(), 20);
    assertEquals(solveTimes.get(0).getTime(), 2100);
    assertEquals(solveTimes.get(19).getTime(), 4000);

    solveHistory = getPagedHistory(solveType1, solveTimes.get(solveTimes.size() - 1).getTimestamp());
    assertEquals(solveHistory.getSolvesCount(), 65);
    solveTimes = solveHistory.getSolveTimes();
    assertEquals(solveTimes.size(), 20);
    assertEquals(solveTimes.get(0).getTime(), 4100);
    assertEquals(solveTimes.get(19).getTime(), 6000);

    solveHistory = getPagedHistory(solveType1, solveTimes.get(solveTimes.size() - 1).getTimestamp());
    assertEquals(solveHistory.getSolvesCount(), 65);
    solveTimes = solveHistory.getSolveTimes();
    assertEquals(solveTimes.size(), 5);
    assertEquals(solveTimes.get(0).getTime(), 6100);
    assertEquals(solveTimes.get(4).getTime(), 6500);

    // History
    solveHistory = provider.getHistory(solveType1, (long) 1000000000);
    assertEquals(solveHistory.getSolvesCount(), 65);
    solveTimes = solveHistory.getSolveTimes();
    assertEquals(solveTimes.size(), 0);

    solveHistory = provider.getHistory(solveType1, (long) 0);
    assertEquals(solveHistory.getSolvesCount(), 65);
    solveTimes = solveHistory.getSolveTimes();
    assertEquals(solveTimes.size(), 65);
    assertEquals(solveTimes.get(0).getTime(), 100);
    assertEquals(solveTimes.get(64).getTime(), 6500);

    solveHistory = provider.getHistory(solveType1, solveTimes.get(29).getTimestamp());
    assertEquals(solveHistory.getSolvesCount(), 65);
    solveTimes = solveHistory.getSolveTimes();
    assertEquals(solveTimes.size(), 30);
    assertEquals(solveTimes.get(0).getTime(), 100);
    assertEquals(solveTimes.get(29).getTime(), 3000);
  }

  @Test
  public void testPb() {
    provider.deleteHistory();
    saveTime(100);
    saveTime(200);
    saveTime(300);
    SolveTime st = saveTime(98).getSolveTime();
    assertFalse(st.isPb()); // not a pb because not enough times
    saveTime(150);
    saveTime(250);
    saveTime(350);
    SolveTime testTime = saveTime(450).getSolveTime();
    saveTime(550);
    saveTime(650);
    st = saveTime(97).getSolveTime();
    assertFalse(st.isPb()); // not a pb because not enough times
    st = saveTime(96).getSolveTime();
    assertTrue(st.isPb()); // not a pb because not enough times
    SolveTime testTime2 = saveTime(95).getSolveTime();
    assertTrue(testTime2.isPb());
    st = saveTime(96).getSolveTime();
    assertFalse(st.isPb());
    st = saveTime(-1).getSolveTime();
    assertFalse(st.isPb());
    SolveTime testTime3 = saveTime(800).getSolveTime();
    assertFalse(testTime3.isPb());
    st = saveTime(94).getSolveTime();
    assertTrue(st.isPb());
    // 100 200 300 98 150 250 350 450 550 650 97 96(b) 95(b) 96 -1 800 94(b)

    saveTime(150);
    st.setTime(95);
    st = provider.saveTime(st).getSolveTime();
    assertFalse(st.isPb());
    st.setTime(94);
    st = provider.saveTime(st).getSolveTime();
    assertTrue(st.isPb());
    st.setTime(93);
    st = provider.saveTime(st).getSolveTime();
    assertTrue(st.isPb());
    // 100 200 300 98 150 250 350 450 550 650 97 96(b) 95(b) 96 -1 800 93(b) 150
    assertPBsInIndices(11, 12, 16);

    testTime.setTime(94); // not a pb because not enough times
    testTime = provider.saveTime(testTime).getSolveTime();
    assertFalse(testTime.isPb());
    // 100 200 300 98 150 250 350 94 550 650 97 96 95 96 -1 800 93(b) 150
    assertPBsInIndices(16);

    testTime2.setTime(85);
    testTime2 = provider.saveTime(testTime2).getSolveTime();
    assertTrue(testTime2.isPb());
    st = provider.getSolveTime(st.getId());
    assertFalse(st.isPb());
    // 100 200 300 98 150 250 350 94 550 650 97 96 85(b) 96 -1 800 93 150
    assertPBsInIndices(12);

    testTime2.setTime(-1);
    testTime2 = provider.saveTime(testTime2).getSolveTime();
    assertFalse(testTime2.isPb());
    // 100 200 300 98 150 250 350 94 550 650 97 96 -1 96 -1 800 93(b) 150
    assertPBsInIndices(16);

    testTime3.setTime(80);
    testTime3 = provider.saveTime(testTime3).getSolveTime();
    assertTrue(testTime3.isPb());
    // 100 200 300 98 150 250 350 94 550 650 97 96 -1 96 -1 80(b) 93 150
    saveTime(100);
    // 100 200 300 98 150 250 350 94 550 650 97 96 -1 96 -1 80(b) 93 150 100
    assertPBsInIndices(15);
    provider.deleteTime(testTime);
    provider.deleteTime(testTime3);
    saveTime(90);
    // 100 200 300 98 150 250 350 550 650 97 96 -1 96 -1 93(b) 150 100 90(b)
    assertPBsInIndices(14, 17);
  }

  @Test
  public void testPbUpdateAndDelete() {
    provider.deleteHistory();
    for (int i = 1; i <= 15; i++) {
      saveTime(i * 50);
    }
    assertPBsInIndices(); // no PBs

    SolveTime st = saveTime(100).getSolveTime();
    assertFalse(st.isPb());
    st = saveTime(45).getSolveTime();
    assertTrue(st.isPb());
    st = saveTime(75).getSolveTime();
    assertFalse(st.isPb());
    assertPBsInIndices(16); // 50 100 150 200 250 300 350 400 450 500 550 600 650 700 750 100 45(b) 75

    List<SolveTime> times = getPagedHistory(solveType1).getSolveTimes();
    Collections.reverse(times);
    st = times.get(0);
    st.setTime(-1);
    provider.saveTime(st);
    assertPBsInIndices(16); // -1 100 150 200 250 300 350 400 450 500 550 600 650 700 750 100 45(b) 75

    st = times.get(1);
    provider.deleteTime(st);
    assertPBsInIndices(14, 15); // -1 150 200 250 300 350 400 450 500 550 600 650 700 750 100(b) 45(b) 75

    times = getPagedHistory(solveType1).getSolveTimes();
    Collections.reverse(times);
    st = times.get(2);
    st.setTime(40);
    provider.saveTime(st);
    assertPBsInIndices(); // -1 150 40 250 300 350 400 450 500 550 600 650 700 750 100 45 75

    st.setTime(70);
    provider.saveTime(st);
    assertPBsInIndices(15); // -1 150 70 250 300 350 400 450 500 550 600 650 700 750 100 45(b) 75

    provider.deleteTime(st);
    assertPBsInIndices(13, 14); // -1 150 250 300 350 400 450 500 550 600 650 700 750 100(b) 45(b) 75

    times = getPagedHistory(solveType1).getSolveTimes();
    Collections.reverse(times);
    st = times.get(13);
    st.setTime(30);
    provider.saveTime(st);
    assertPBsInIndices(13); // -1 150 250 300 350 400 450 500 550 600 650 700 750 30(b) 45 75

    provider.deleteTime(st);
    assertPBsInIndices(13); // -1 150 250 300 350 400 450 500 550 600 650 700 750 45(b) 75

    st = times.get(3);
    st.setTime(20);
    provider.saveTime(st);
    assertPBsInIndices(); // -1 150 250 20 350 400 450 500 550 600 650 700 750 30 45 75

    provider.deleteTime(st);
    assertPBsInIndices(12); // -1 150 250 350 400 450 500 550 600 650 700 750 30(b) 45 75

    saveTime(25);
    times = getPagedHistory(solveType1).getSolveTimes();
    Collections.reverse(times);
    provider.deleteTime(times.get(12));
    assertPBsInIndices(12, 13); // -1 150 250 350 400 450 500 550 600 650 700 750 45(b) 25(b)
    provider.deleteTime(times.get(11));
    assertPBsInIndices(11, 12); // -1 150 250 350 400 450 500 550 600 650 700 45(b) 25(b)
  }

  @Test
  public void testHistorySort() {
    provider.deleteHistory();
    // Initialize arrays
    long[] times = new long[] {
            100, 300, 200, 600, 1200, 50, 30, 400, 500, 250
    };
    Long[] invertedTimes = new Long[times.length];
    for (int i = 0; i < times.length; i++) {
      invertedTimes[i] = times[i];
    }
    List<Long> invertedList = Arrays.asList(invertedTimes);
    Collections.reverse(invertedList);
    invertedTimes = invertedList.toArray(new Long[0]);

    long[] sortedTimes = new long[times.length];
    System.arraycopy(times, 0, sortedTimes, 0, sortedTimes.length);
    Arrays.sort(sortedTimes);

    for (long time : times) {
      saveTime(time);
    }

    // Test paged history
    SolveHistory solveHistory = getPagedHistory(solveType1, TimesSort.TIMESTAMP);
    assertEquals(solveHistory.getSolvesCount(), times.length);
    List<SolveTime> solveTimes = solveHistory.getSolveTimes();
    for (int i = 0; i < solveHistory.getSolvesCount(); i++) {
      assertEquals(solveTimes.get(i).getTime(), (long) invertedTimes[i]);
    }

    solveHistory = getPagedHistory(solveType1, TimesSort.TIME);
    assertEquals(solveHistory.getSolvesCount(), times.length);
    solveTimes = solveHistory.getSolveTimes();
    for (int i = 0; i < solveHistory.getSolvesCount(); i++) {
      assertEquals(solveTimes.get(i).getTime(), sortedTimes[i]);
    }

    long bestTime = 10000;
    saveTime(bestTime);

    solveHistory = getPagedHistory(solveType1, TimesSort.TIMESTAMP);
    assertEquals(solveHistory.getSolvesCount(), times.length + 1);
    solveTimes = solveHistory.getSolveTimes();
    for (int i = 1; i < solveHistory.getSolvesCount(); i++) {
      assertEquals(solveTimes.get(i).getTime(), (long) invertedTimes[i-1]);
    }
    assertEquals(solveTimes.get(0).getTime(), bestTime);

    solveHistory = getPagedHistory(solveType1, TimesSort.TIME);
    assertEquals(solveHistory.getSolvesCount(), times.length + 1);
    solveTimes = solveHistory.getSolveTimes();
    for (int i = 0; i < times.length; i++) {
      assertEquals(solveTimes.get(i).getTime(), sortedTimes[i]);
    }
    assertEquals(solveTimes.get(times.length).getTime(), bestTime);
  }

  @Test
  public void testFrequencyData() {
    provider.deleteHistory();
    saveTime(50, 1440057600000L); // 20/08 10:00
    saveTime(60, 1440059400000L); // 20/08 10:30
    saveTime(60, 1440075600000L); // 20/08 15:00
    saveTime(30, 1440248400000L); // 22/08 15:00
    saveTime(20, 1440252000000L); // 22/08 16:00
    saveTime(60, 1440576000000L); // 26/08 10:00
    saveTime(50, 1440928800000L); // 30/08 12:00
    saveTime(40, 1440932400000L); // 30/08 13:00
    saveTime(10, 1440936000000L); // 30/08 14:00
    saveTime(20, 1440939600000L); // 30/08 15:00

    List<FrequencyData> frequencyData = provider.getFrequencyData(solveType1, 1439625600000L); // 15/08 10:00
    assertFrequencyDataEquals(frequencyData.get(0), 1439589600000L, 0); // 15/08
    assertFrequencyDataEquals(frequencyData.get(1), 1439676000000L, 0); // 16/08
    assertFrequencyDataEquals(frequencyData.get(4), 1439935200000L, 0); // 19/08
    assertFrequencyDataEquals(frequencyData.get(5), 1440021600000L, 3); // 20/08
    assertFrequencyDataEquals(frequencyData.get(6), 1440108000000L, 0); // 21/08
    assertFrequencyDataEquals(frequencyData.get(7), 1440194400000L, 2); // 22/08
    assertFrequencyDataEquals(frequencyData.get(8), 1440280800000L, 0); // 23/08
    assertFrequencyDataEquals(frequencyData.get(11), 1440540000000L, 1); // 26/08
    assertFrequencyDataEquals(frequencyData.get(12), 1440626400000L, 0); // 27/08
    assertFrequencyDataEquals(frequencyData.get(14), 1440799200000L, 0); // 29/08
    assertFrequencyDataEquals(frequencyData.get(15), 1440885600000L, 4); // 30/08
  }

  @Test
  public void testImportOldTimesAveragesBug() {
    provider.deleteHistory();
    long ts = 1440057600000L; // 20/08 10:00
    for (int i = 0; i < 120; i++) {
      saveTime(30, ts + (i*60000));
    }

    ts = 1439193000000L; // 10/08 09:50;
    List<SolveTime> solveTimes = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      solveTimes.add(new SolveTime(ts + (i*60000), 60 + i, false, "", solveType1));
    }

    SolveAverages solveAverages = provider.saveTimes(solveTimes, null);
    assertEquals((long) solveAverages.getAvgOf5(), 30l);
    assertEquals((long) solveAverages.getAvgOf12(), 30l);
    assertEquals((long) solveAverages.getAvgOf50(), 30l);
    assertEquals((long) solveAverages.getAvgOf100(), 30l);
    assertEquals((long) solveAverages.getMeanOf3(), 30l);
  }

  /*@Test
  public void testPBPerfs() {
    provider.deleteHistory();
    int size = 100;
    int[] times = new int[size];
    int[] newTimes = new int[size];
    Log.i("NanoTimer", "Saving times...");
    for (int i = 0; i < times.length; i++) {
      times[i] = new Random().nextInt(1000) + 1;
      saveTime(times[i]);
      newTimes[i] = new Random().nextInt(1000) + 1;
    }
    Log.i("NanoTimer", "Times saved! Now updating...");

    List<SolveTime> history = getWholeHistory();
    int i = 0;
    long ms = System.currentTimeMillis();
    for (SolveTime st : history) {
      st.setTime(newTimes[i]);
      provider.saveTime(st);
      i++;
    }
    Log.i("NanoTimer", "Time to update: " + (System.currentTimeMillis() - ms));

    ms = System.currentTimeMillis();
    for (SolveTime st : history) {
      provider.deleteTime(st);
    }
    Log.i("NanoTimer", "Time to delete: " + (System.currentTimeMillis() - ms));
  }*/

  private void assertPBsInIndices(int... pbIndices) {
    List<SolveTime> solveTimes = getWholeHistory();
    Collections.reverse(solveTimes);
    for (int i = 0; i < solveTimes.size(); i++) {
      boolean found = false;
      for (int ind : pbIndices) { // -1 150 200 250 300 350 400 450 500 550 600 650 700 750 100(b) 45(b) 75
        if (i == ind) {
          found = true;
          break;
        }
      }
      if (found) {
        assertTrue(solveTimes.get(i).isPb());
      } else {
        assertFalse(solveTimes.get(i).isPb());
      }
    }
  }

  // The time a DNF replaced must survive the write and come back on every read path, or the
  // undo silently stops working after the history is reloaded.
  @Test
  public void testUndoADnfThroughThePersistenceLayer() {
    provider.deleteHistory();
    SolveTime st = saveTime(12345).getSolveTime();

    st.setDNF();
    st = provider.saveTime(st).getSolveTime();
    assertTrue(st.isDNF());

    // Re-read rather than trust the object we just handed in.
    SolveTime reloaded = provider.getSolveTime(st.getId());
    assertTrue(reloaded.isDNF());
    assertTrue(reloaded.canUndoDNF());
    assertEquals(Long.valueOf(12345), reloaded.getTimeBeforeDnf());
    assertEquals(Long.valueOf(12345), getFirstTime().getTimeBeforeDnf()); // and through the history

    reloaded.undoDNF();
    provider.saveTime(reloaded);
    SolveTime restored = provider.getSolveTime(reloaded.getId());
    assertFalse(restored.isDNF());
    assertEquals(12345, restored.getTime());
    assertNull(restored.getTimeBeforeDnf()); // cleared, so a second undo has nothing to work from
    assertFalse(restored.canUndoDNF());
  }

  // A DNF that never had a time — entered by hand, or migrated from a DB that overwrote it —
  // stays a DNF: there is nothing to put back.
  @Test
  public void testADnfWithNoRecordedTimeStaysADnf() {
    provider.deleteHistory();
    SolveTime st = saveTime(-1).getSolveTime();

    SolveTime reloaded = provider.getSolveTime(st.getId());
    assertTrue(reloaded.isDNF());
    assertFalse(reloaded.canUndoDNF());
    assertNull(reloaded.getTimeBeforeDnf());

    reloaded.undoDNF();
    provider.saveTime(reloaded);
    assertTrue(provider.getSolveTime(reloaded.getId()).isDNF());
  }

  // The averages must follow the time both ways: a DNF drops out of the mean, and undoing it
  // puts the solve back in. This is what makes the negative sentinel worth keeping in the column.
  @Test
  public void testUndoingADnfPutsTheSolveBackIntoTheAverages() {
    provider.deleteHistory();
    saveTime(1000);
    saveTime(2000);
    SolveTime st = saveTime(3000).getSolveTime();
    Long meanWithTheSolve = provider.getSolveAverages(solveType1).getMeanOf3();
    assertEquals(Long.valueOf(2000), meanWithTheSolve);

    st.setDNF();
    st = provider.saveTime(st).getSolveTime();
    assertEquals(Long.valueOf(-1), provider.getSolveAverages(solveType1).getMeanOf3()); // a DNF mean

    st = provider.getSolveTime(st.getId());
    st.undoDNF();
    provider.saveTime(st);
    assertEquals(meanWithTheSolve, provider.getSolveAverages(solveType1).getMeanOf3());
  }

  @Test
  public void testMethodStatisticsGroupsStepsByTheirCase() {
    provider.deleteHistory();
    saveCubeSolve(CubeMethod.CFOP, null, step("cross", 1000, 400), step("pll_gb", 3000, 1000));
    saveCubeSolve(CubeMethod.CFOP, null, step("cross", 2000, 600), step("pll_t", 1000, 400));
    saveCubeSolve(CubeMethod.CFOP, null, step("cross", 3000, 800), step("pll_t", 2000, 600));

    MethodStatistics stats = provider.getMethodStatistics(solveType1, CubeMethod.CFOP, 50);
    assertEquals(3, stats.getSolveCount());
    assertEquals(3, stats.getFamily("cross").getCount());
    assertEquals(2000, stats.getFamily("cross").getMeanMs());
    assertEquals(600, stats.getFamily("cross").getMeanRecognitionMs());
    assertEquals(2000, stats.getFamily("pll").getMeanMs());
    assertEquals(2, stats.getCases("pll").size());
    assertEquals("pll_gb", stats.getCases("pll").get(0).getCode()); // the slowest of them
    assertEquals(1000, stats.getFamily("cross").getBestMs());
  }

  // Another method's solves are not this method's steps, and a DNF has no time to average.
  @Test
  public void testMethodStatisticsLeavesOutWhatItCannotAverage() {
    provider.deleteHistory();
    saveCubeSolve(CubeMethod.CFOP, null, step("cross", 2000, 500));
    saveCubeSolve(CubeMethod.ROUX, null, step("cross", 9000, 500));
    saveDnfCubeSolve(CubeMethod.CFOP, step("cross", 9000, 500));

    MethodStatistics stats = provider.getMethodStatistics(solveType1, CubeMethod.CFOP, 50);
    assertEquals(1, stats.getFamily("cross").getCount());
    assertEquals(2000, stats.getFamily("cross").getMeanMs());
  }

  // A stopped solve keeps its finished steps: only the one it was inside was cut short.
  @Test
  public void testMethodStatisticsDropsOnlyTheStepASolveStoppedIn() {
    provider.deleteHistory();
    saveCubeSolve(CubeMethod.CFOP, null, step("cross", 2000, 500), step("oll_21", 2000, 500));
    saveCubeSolve(CubeMethod.CFOP, 2, step("cross", 2000, 500), step("oll_21", 200, 100));

    MethodStatistics stats = provider.getMethodStatistics(solveType1, CubeMethod.CFOP, 50);
    assertEquals(2, stats.getFamily("cross").getCount()); // the finished step of the stopped solve
    assertEquals(1, stats.getFamily("oll").getCount());   // but not the one it stopped in
    assertEquals(2000, stats.getFamily("oll").getMeanMs());
  }

  @Test
  public void testMethodStatisticsReadsOnlyTheLastSolvesOfTheWindow() {
    provider.deleteHistory();
    saveCubeSolve(CubeMethod.CFOP, null, step("cross", 5000, 500));
    saveCubeSolve(CubeMethod.CFOP, null, step("cross", 1000, 500));
    saveCubeSolve(CubeMethod.CFOP, null, step("cross", 3000, 500));

    MethodStatistics stats = provider.getMethodStatistics(solveType1, CubeMethod.CFOP, 2);
    assertEquals(2, stats.getSolveCount());
    assertEquals(2, stats.getFamily("cross").getCount());
    assertEquals(2000, stats.getFamily("cross").getMeanMs()); // the two most recent, not the 5000
  }

  @Test
  public void testMethodStatisticsCountsThePartsOfAStepToo() {
    provider.deleteHistory();
    SolveStep f2l = new SolveStep(1, "f2l", 1000, 7000,
        Arrays.asList(subStep(0, "pair_rf", 2000, 500), subStep(1, "pair_lb", 6000, 500)));
    saveCubeSolve(CubeMethod.CFOP, null, step("cross", 2000, 500), f2l);

    MethodStatistics stats = provider.getMethodStatistics(solveType1, CubeMethod.CFOP, 50);
    assertEquals(8000, stats.getFamily("f2l").getMeanMs());
    assertEquals(2, stats.getFamily("pair").getCount());
    assertEquals(4000, stats.getFamily("pair").getMeanMs());
    assertEquals("pair_lb", stats.getCases("pair").get(0).getCode()); // the slower slot
  }

  /** @param stoppedStep the step the timer was stopped inside, null when the solve ran to the end */
  private SolveTime saveCubeSolve(CubeMethod method, Integer stoppedStep, SolveStep... steps) {
    long time = 0;
    for (SolveStep step : steps) {
      time += step.getTotalMs();
    }
    return saveCubeSolve(method, stoppedStep, time, steps);
  }

  private SolveTime saveDnfCubeSolve(CubeMethod method, SolveStep... steps) {
    return saveCubeSolve(method, null, -1, steps);
  }

  private SolveTime saveCubeSolve(CubeMethod method, Integer stoppedStep, long time,
      SolveStep... steps) {
    SolveTime st = new SolveTime();
    st.setScramble(SCRAMBLE);
    st.setTimestamp(100 + (timeCpt++));
    st.setSolveType(solveType1);
    st.setTime(time);
    st.setSmartcubeMethod(method);
    st.setSmartcubeSteps(Arrays.asList(steps));
    st.setSmartcubeStoppedStep(stoppedStep);
    return provider.saveTime(st).getSolveTime();
  }

  private SolveStep step(String name, long totalMs, long recognitionMs) {
    return new SolveStep(stepIndexOf(name), name, recognitionMs, totalMs - recognitionMs,
        new ArrayList<SolveStep>());
  }

  private SolveStep subStep(int index, String name, long totalMs, long recognitionMs) {
    return new SolveStep(index, name, recognitionMs, totalMs - recognitionMs,
        new ArrayList<SolveStep>());
  }

  /** Where CFOP's steps sit, so a step reads back under the index the detector would have given it. */
  private int stepIndexOf(String name) {
    if (name.startsWith("f2l")) {
      return 1;
    } else if (name.startsWith("oll")) {
      return 2;
    } else if (name.startsWith("pll")) {
      return 3;
    }
    return 0;
  }

  private SolveAverages saveTimes(long time, int count) {
    SolveAverages averages = null;
    for (int i = 0; i < count; i++) {
      averages = saveTime(time);
    }
    return averages;
  }

  private SolveAverages saveTime(long time) {
    return saveTime(time, solveType1);
  }

  private SolveAverages saveTime(long time, SolveType solveType) {
    SolveTime st = new SolveTime();
    st.setScramble(SCRAMBLE);
    st.setTimestamp(100 + (timeCpt++));
    st.setSolveType(solveType);
    st.setTime(time);
    return provider.saveTime(st);
  }

  private SolveAverages saveTime(long time, long timestamp) {
    SolveTime st = new SolveTime();
    st.setScramble(SCRAMBLE);
    st.setTimestamp(timestamp);
    st.setSolveType(solveType1);
    st.setTime(time);
    return provider.saveTime(st);
  }

  private SolveAverages saveStepTimes(long... times) {
    SolveTime st = new SolveTime();
    st.setScramble(SCRAMBLE);
    st.setTimestamp(100 + (timeCpt++));
    st.setSolveType(solveTypeSteps);
    long sum = 0;
    Long[] t = new Long[times.length];
    for (int i = 0; i < times.length; i++) {
      t[i] = times[i];
      sum += t[i];
    }
    st.setTime(sum);
    st.setStepsTimes(t);
    return provider.saveTime(st);
  }

  private SolveTime getFirstTime() {
    List<SolveTime> times = getPagedHistory(solveType1).getSolveTimes();
    Assert.assertTrue(times != null && times.size() > 0);
    return times.get(times.size() - 1);
  }

  private SolveTime getLastTime() {
    List<SolveTime> times = getPagedHistory(solveType1).getSolveTimes();
    Assert.assertTrue(times != null && times.size() > 0);
    return times.get(0);
  }

  private List<SolveTime> getWholeHistory() {
    return provider.getHistoryTimes(solveType1, System.currentTimeMillis(), true, null, TimesSort.TIMESTAMP);
  }

  private SolveHistory getPagedHistory(SolveType solveType) {
    return provider.getPagedHistory(solveType, TimesSort.TIMESTAMP);
  }

  private SolveHistory getPagedHistory(SolveType solveType, TimesSort timesSort) {
    return provider.getPagedHistory(solveType, timesSort);
  }

  private SolveHistory getPagedHistory(SolveType solveType, long from) {
    return provider.getPagedHistory(solveType, from, TimesSort.TIMESTAMP);
  }

  private SolveHistory getPagedHistory(SolveType solveType, TimesSort timesSort, long from) {
    return provider.getPagedHistory(solveType, from, timesSort);
  }

  private void assertAvgEquals(Integer avg5, Integer avg12, Integer avg50, Integer avg100, Integer avgLifetime,
                               Integer best5, Integer best12, Integer best50, Integer best100, Integer bestLifetime, SolveAverages averages) {
    Long lavg5 = (avg5 == null) ? null : Long.valueOf(avg5);
    Long lavg12 = (avg12 == null) ? null : Long.valueOf(avg12);
    Long lavg50 = (avg50 == null) ? null : Long.valueOf(avg50);
    Long lavg100 = (avg100 == null) ? null : Long.valueOf(avg100);
    Long lavgLifetime = (avgLifetime == null) ? null : Long.valueOf(avgLifetime);
    Long lbest5 = (best5 == null) ? null : Long.valueOf(best5);
    Long lbest12 = (best12 == null) ? null : Long.valueOf(best12);
    Long lbest50 = (best50 == null) ? null : Long.valueOf(best50);
    Long lbest100 = (best100 == null) ? null : Long.valueOf(best100);
    Long lbestLifetime = (bestLifetime == null) ? null : Long.valueOf(bestLifetime);
    Assert.assertEquals(new SolveAverages(lavg5, lavg12, lavg50, lavg100, lavgLifetime, lbest5, lbest12, lbest50, lbest100, lbestLifetime), averages);
  }

  private void assertFrequencyDataEquals(FrequencyData frequencyData, long day, int solvesCount) {
    assertEquals(day, frequencyData.getDay());
    assertEquals(solvesCount, frequencyData.getSolvesCount());
  }

  private void assertSolveTimeAveragesEquals(Integer avg5, Integer avg12, Integer avg50, Integer avg100, SolveTimeAverages sta) {
    Assert.assertNotNull(sta);
    Assert.assertEquals((avg5 == null) ? null : Long.valueOf(avg5), sta.getAvgOf5());
    Assert.assertEquals((avg12 == null) ? null : Long.valueOf(avg12), sta.getAvgOf12());
    Assert.assertEquals((avg50 == null) ? null : Long.valueOf(avg50), sta.getAvgOf50());
    Assert.assertEquals((avg100 == null) ? null : Long.valueOf(avg100), sta.getAvgOf100());
  }

  private void assertStepsEquals(List<Long> averages, long... values) {
    assertNotNull(averages);
    assertEquals(values.length, averages.size());
    for (int i = 0; i < averages.size(); i++) {
      assertEquals(values[i], averages.get(i).longValue());
    }
  }

}
