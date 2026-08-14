package com.cube.nanotimer.util.exportimport.csvimport;

import com.cube.nanotimer.util.exportimport.CSVFormatException;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveType;
import com.cube.nanotimer.vo.SolveTypeStep;

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

/**
 * A DNF is exported with an empty steps field whatever its solve type, so a file holding a solve
 * type with steps holds rows that say nothing about them. Reading such a file used to raise a
 * solve type conflict and refuse the whole import.
 *
 * <p>The context is only read to build that conflict message, so these tests pass none.</p>
 */
@RunWith(JUnit4.class)
public class ImportTimesDataTest {

  private static SolveType stepped(String name) {
    SolveType solveType = new SolveType(name, false, null, CubeType.THREE_BY_THREE.getId());
    SolveTypeStep cross = new SolveTypeStep();
    cross.setName("Cross");
    SolveTypeStep f2l = new SolveTypeStep();
    f2l.setName("F2L");
    solveType.setSteps(new SolveTypeStep[] { cross, f2l });
    return solveType;
  }

  private static SolveType stepless(String name) {
    return new SolveType(name, false, null, CubeType.THREE_BY_THREE.getId());
  }

  // The commonest order: the timed solves come first, the DNF later.
  @Test
  public void testSteplessRowAfterSteppedOneTakesItsSteps() throws CSVFormatException {
    ImportTimesData data = new ImportTimesData(null);
    SolveType first = data.addSolveTypeIfNotExists(CubeType.THREE_BY_THREE, stepped("CFOP steps"));
    SolveType second = data.addSolveTypeIfNotExists(CubeType.THREE_BY_THREE, stepless("CFOP steps"));

    Assert.assertSame(first, second);
    Assert.assertEquals(1, data.getSolveTypes().get(CubeType.THREE_BY_THREE).size());
    Assert.assertTrue(second.hasSteps());
  }

  // The DNF is the first solve of the type, so the step-less one is the one already recorded.
  @Test
  public void testSteppedRowAfterSteplessOneGivesItItsSteps() throws CSVFormatException {
    ImportTimesData data = new ImportTimesData(null);
    SolveType first = data.addSolveTypeIfNotExists(CubeType.THREE_BY_THREE, stepless("CFOP steps"));
    data.addSolveTime(first, new SolveTime());
    SolveType second = data.addSolveTypeIfNotExists(CubeType.THREE_BY_THREE, stepped("CFOP steps"));

    Assert.assertSame(first, second);
    Assert.assertTrue(first.hasSteps());
  }

  // Upgrading the recorded solve type changes its hash, so its times have to travel with it.
  @Test
  public void testTimesRecordedBeforeTheStepsAreNotLost() throws CSVFormatException {
    ImportTimesData data = new ImportTimesData(null);
    SolveType first = data.addSolveTypeIfNotExists(CubeType.THREE_BY_THREE, stepless("CFOP steps"));
    data.addSolveTime(first, new SolveTime());
    SolveType second = data.addSolveTypeIfNotExists(CubeType.THREE_BY_THREE, stepped("CFOP steps"));
    data.addSolveTime(second, new SolveTime());

    Assert.assertEquals(2, data.getSolveTimesCount());
    List<SolveTime> times = data.getSolveTimes().get(first);
    Assert.assertNotNull(times);
    Assert.assertEquals(2, times.size());
  }
}
