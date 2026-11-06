package com.cube.nanotimer.util.coach;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Calendar;

@RunWith(JUnit4.class)
public class CoachRecapTimingTest {

  private static final int SEVEN_PM = 19 * 60;

  @Test
  public void testBeforeTheRecapTimeItRunsToday() {
    long now = at(2026, 11, 6, 15, 0);

    Assert.assertEquals(at(2026, 11, 6, 19, 0),
        CoachRecapWorker.nextRunAt(now, SEVEN_PM, 0, false));
  }

  @Test
  public void testAFinishedRunPastTheRecapTimeWaitsForTomorrow() {
    long now = at(2026, 11, 6, 20, 30);

    Assert.assertEquals(at(2026, 11, 7, 19, 0),
        CoachRecapWorker.nextRunAt(now, SEVEN_PM, 0, false));
  }

  /** An app start past the time, with no recap sent today, runs the one that may have been lost. */
  @Test
  public void testAnAppStartPastTheRecapTimeCatchesUp() {
    long now = at(2026, 11, 6, 20, 30);

    Assert.assertEquals(now, CoachRecapWorker.nextRunAt(now, SEVEN_PM, 0, true));
  }

  @Test
  public void testADayWithItsRecapSentWaitsForTomorrow() {
    long now = at(2026, 11, 6, 15, 0);

    Assert.assertEquals(at(2026, 11, 7, 19, 0),
        CoachRecapWorker.nextRunAt(now, SEVEN_PM, CoachDays.dayKey(now), true));
  }

  @Test
  public void testTheChosenTimeIsKept() {
    long now = at(2026, 12, 31, 22, 0);

    Assert.assertEquals(at(2027, 1, 1, 7, 45),
        CoachRecapWorker.nextRunAt(now, 7 * 60 + 45, 0, false));
    Assert.assertEquals(20261231, CoachDays.dayKey(now));
  }

  private static long at(int year, int month, int day, int hour, int minute) {
    Calendar at = Calendar.getInstance();
    at.clear();
    at.set(year, month - 1, day, hour, minute);
    return at.getTimeInMillis();
  }
}
