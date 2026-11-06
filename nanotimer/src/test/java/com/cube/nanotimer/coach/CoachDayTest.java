package com.cube.nanotimer.coach;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.cube.nanotimer.session.CoachDay;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(JUnit4.class)
public class CoachDayTest {

  @Test
  public void testTodayShowsFromTheFirstSolve() {
    CoachDay day = day(15000L);

    Assert.assertEquals(1, day.getSolveCount());
    Assert.assertEquals(15000, day.getMean());
    Assert.assertEquals(-1, day.getBestAo5());
    Assert.assertTrue(day.isActive());
  }

  @Test
  public void testTheMeanLeavesDnfsOut() {
    Assert.assertEquals(15000, day(14000L, -1L, 16000L).getMean());
    Assert.assertEquals(-1, day(-1L, -1L).getMean());
  }

  /** Every five in a row is an Ao5; the best one wins, and one DNF in it is trimmed as the worst. */
  @Test
  public void testTheBestAo5IsTheBestOfEveryFiveInARow() {
    CoachDay day = day(20000L, 10000L, 11000L, -1L, 12000L, 13000L, 30000L);

    Assert.assertEquals(12000, day.getBestAo5()); // 10, 11, DNF, 12, 13: 11, 12, 13 count
  }

  @Test
  public void testTwoDnfsInEveryFiveLeaveNoAo5() {
    Assert.assertEquals(-1, day(-1L, 10000L, -1L, 11000L, 12000L).getBestAo5());
  }

  @Test
  public void testADayWithOnlyDrillRepsIsActive() {
    CoachDay day = new CoachDay(Collections.<Long>emptyList(), 12, 0, null);

    Assert.assertTrue(day.isActive());
    Assert.assertFalse(new CoachDay(Collections.<Long>emptyList(), 0, 0, null).isActive());
    Assert.assertNull(day.getBestMove());
  }

  private static CoachDay day(Long... times) {
    List<Long> list = new ArrayList<Long>(Arrays.asList(times));
    return new CoachDay(list, 0, 0, null);
  }
}
