package com.cube.nanotimer.coach;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.cube.nanotimer.session.TodayVsUsual;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(JUnit4.class)
public class TodayVsUsualTest {

  @Test
  public void testAClearlyFasterDayIsReported() {
    TodayVsUsual.Gain gain = TodayVsUsual.best(
        figures(TodayVsUsual.SOLVE, around(11000, 300, 6)),
        figures(TodayVsUsual.SOLVE, around(12000, 500, 40)));

    Assert.assertNotNull(gain);
    Assert.assertEquals(TodayVsUsual.SOLVE, gain.getCode());
    Assert.assertEquals(1000, gain.getGainMs(), 50);
  }

  /** A day that is a little faster with times all over the place is not news. */
  @Test
  public void testALuckyDayIsNot() {
    Assert.assertNull(TodayVsUsual.best(figures("cross", around(2300, 1500, 6)),
        figures("cross", around(2500, 1500, 40))));
  }

  @Test
  public void testTooFewSolvesSayNothing() {
    Assert.assertNull(TodayVsUsual.best(figures("cross", around(1500, 100, 4)),
        figures("cross", around(2500, 100, 40))));
    Assert.assertNull(TodayVsUsual.best(figures("cross", around(1500, 100, 6)),
        figures("cross", around(2500, 100, 19))));
  }

  @Test
  public void testTheLargestShareOfItsUsualWins() {
    Map<String, List<Long>> today = figures("cross", around(2000, 100, 8));
    today.put("f2l", around(9000, 200, 8)); // 1 s of 10: less than 0.5 s of 2.5
    Map<String, List<Long>> usual = figures("cross", around(2500, 100, 40));
    usual.put("f2l", around(10000, 200, 40));

    Assert.assertEquals("cross", TodayVsUsual.best(today, usual).getCode());
  }

  /** One popped solve in the usual does not turn an ordinary day into a fast one. */
  @Test
  public void testAPoppedSolveDoesNotMakeAGain() {
    List<Long> usual = around(12000, 200, 39);
    usual.add(Long.valueOf(90000));

    Assert.assertNull(TodayVsUsual.best(figures(TodayVsUsual.SOLVE, around(12000, 200, 8)),
        figures(TodayVsUsual.SOLVE, usual)));
  }

  private static Map<String, List<Long>> figures(String code, List<Long> times) {
    Map<String, List<Long>> figures = new HashMap<String, List<Long>>();
    figures.put(code, times);
    return figures;
  }

  /** Times alternating below and above the mean by the spread. */
  private static List<Long> around(long mean, long spread, int count) {
    List<Long> times = new ArrayList<Long>();
    for (int i = 0; i < count; i++) {
      times.add(Long.valueOf(mean + (i % 2 == 0 ? -spread : spread)));
    }
    return times;
  }
}
