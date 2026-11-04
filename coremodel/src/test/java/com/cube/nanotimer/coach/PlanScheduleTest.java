package com.cube.nanotimer.coach;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.cube.nanotimer.coach.PlanSchedule.State;

@RunWith(JUnit4.class)
public class PlanScheduleTest {

  private static final long DAY = 24L * 60 * 60 * 1000;
  private static final long MINUTE = 60L * 1000;
  private static final long PLAN = 1000 * DAY;
  private static final int FLOOR = CoachPayloadBuilder.PLAN_FLOOR;
  private static final int SINCE = CoachPayloadBuilder.SINCE_FLOOR;
  /** A solve long before any time a test asks at. */
  private static final long OLD_SOLVE = PLAN + DAY;

  @Test
  public void testAPlanIsNotDueWithinAWeekOfTheLast() {
    PlanSchedule schedule = PlanSchedule.of(PLAN, 300, SINCE, OLD_SOLVE, PLAN + 7 * DAY - 1);

    Assert.assertEquals(State.NOT_DUE, schedule.getState());
    Assert.assertEquals(PLAN + 7 * DAY, schedule.getDueAt());
    Assert.assertFalse(schedule.mayAsk(true));
    Assert.assertEquals(State.READY,
        PlanSchedule.of(PLAN, 300, SINCE, OLD_SOLVE, PLAN + 7 * DAY).getState());
  }

  /** What the Plan tab says: "in 4 days" for anything from three days and a bit to four. */
  @Test
  public void testTheDaysLeftCountAStartedDay() {
    Assert.assertEquals(7, PlanSchedule.of(PLAN, 300, 0, 0, PLAN + 1).getDaysUntilDue());
    Assert.assertEquals(4, PlanSchedule.of(PLAN, 300, 0, 0, PLAN + 3 * DAY).getDaysUntilDue());
    Assert.assertEquals(4, PlanSchedule.of(PLAN, 300, 0, 0, PLAN + 3 * DAY + 1).getDaysUntilDue());
    Assert.assertEquals(1, PlanSchedule.of(PLAN, 300, 0, 0, PLAN + 7 * DAY - 1).getDaysUntilDue());
    Assert.assertEquals(0, PlanSchedule.of(PLAN, 300, 0, 0, PLAN + 7 * DAY).getDaysUntilDue());
    Assert.assertEquals(0, PlanSchedule.of(0, 300, 0, 0, PLAN).getDaysUntilDue());
  }

  @Test
  public void testADuePlanWaitsForTheNewSolves() {
    PlanSchedule schedule = PlanSchedule.of(PLAN, 300, SINCE - 12, OLD_SOLVE, PLAN + 30 * DAY);

    Assert.assertEquals(State.NEEDS_SOLVES, schedule.getState());
    Assert.assertEquals(12, schedule.getSolvesNeeded());
    Assert.assertFalse(schedule.mayAsk(true));
  }

  /** The count is what the Plan tab and the nudge say before the week is out. */
  @Test
  public void testTheMissingSolvesAreCountedBeforeThePlanIsDue() {
    PlanSchedule schedule = PlanSchedule.of(PLAN, 300, 5, OLD_SOLVE, PLAN + 2 * DAY);

    Assert.assertEquals(State.NOT_DUE, schedule.getState());
    Assert.assertEquals(SINCE - 5, schedule.getSolvesNeeded());
  }

  @Test
  public void testAPlanIsNotAskedForDuringASession() {
    long now = PLAN + 8 * DAY;
    PlanSchedule schedule = PlanSchedule.of(PLAN, 300, SINCE, now - 15 * MINUTE + 1, now);

    Assert.assertEquals(State.QUIET, schedule.getState());
    Assert.assertEquals(now + 1, schedule.getQuietUntil());
    Assert.assertFalse(schedule.mayAsk(true));
    Assert.assertEquals(State.READY,
        PlanSchedule.of(PLAN, 300, SINCE, now - 15 * MINUTE, now).getState());
  }

  @Test
  public void testAFirstPlanWaitsOnlyForTheFloor() {
    long now = 5 * DAY;
    PlanSchedule short3 = PlanSchedule.of(0, FLOOR - 3, 0, now - DAY, now);
    Assert.assertEquals(State.NEEDS_SOLVES, short3.getState());
    Assert.assertEquals(3, short3.getSolvesNeeded());

    PlanSchedule ready = PlanSchedule.of(0, FLOOR, 0, now - DAY, now);
    Assert.assertTrue(ready.isFirst());
    Assert.assertEquals(State.READY, ready.getState());
    Assert.assertEquals(0, ready.getDueAt());
  }

  /** Asked for from the screen, a first plan is written mid-session. In the background it waits. */
  @Test
  public void testAFirstPlanAskedFromTheScreenSkipsTheQuietWait() {
    long now = 5 * DAY;
    PlanSchedule schedule = PlanSchedule.of(0, FLOOR, 0, now - MINUTE, now);

    Assert.assertEquals(State.QUIET, schedule.getState());
    Assert.assertTrue(schedule.mayAsk(true));
    Assert.assertFalse(schedule.mayAsk(false));
  }

  /** Plans moved to another solve type: the week still runs, and the type needs its own floor. */
  @Test
  public void testAMovedPlanKeepsTheClockAndNeedsItsOwnFloor() {
    PlanSchedule young = PlanSchedule.of(PLAN, FLOOR - 40, SINCE, OLD_SOLVE, PLAN + 8 * DAY);
    Assert.assertEquals(State.NEEDS_SOLVES, young.getState());
    Assert.assertEquals(40, young.getSolvesNeeded());

    Assert.assertEquals(State.NOT_DUE,
        PlanSchedule.of(PLAN, 300, 300, OLD_SOLVE, PLAN + DAY).getState());
  }

  @Test
  public void testTheNudgeComesOncePerPlanTheDayBeforeItIsDue() {
    Assert.assertFalse(schedule(5, PLAN + 6 * DAY - 1).nudgeDue(0));
    Assert.assertTrue(schedule(5, PLAN + 6 * DAY).nudgeDue(0));
    Assert.assertTrue(schedule(5, PLAN + 6 * DAY).nudgeDue(PLAN - DAY)); // told before that plan
    Assert.assertFalse(schedule(5, PLAN + 30 * DAY).nudgeDue(PLAN + 6 * DAY));
  }

  @Test
  public void testNoNudgeWithEnoughSolvesOrBeforeAFirstPlanOrDuringASession() {
    long now = PLAN + 6 * DAY + MINUTE;
    Assert.assertFalse(schedule(SINCE, now).nudgeDue(0));
    Assert.assertFalse(PlanSchedule.of(0, FLOOR - 3, 0, DAY, now).nudgeDue(0));
    Assert.assertFalse(PlanSchedule.of(PLAN, 300, 5, now - MINUTE, now).nudgeDue(0));
  }

  /** A nudge a session holds back is still owed, so the job comes back for it. */
  @Test
  public void testANudgeHeldBackByASessionIsStillOwed() {
    long now = PLAN + 6 * DAY + MINUTE;
    PlanSchedule inSession = PlanSchedule.of(PLAN, 300, 5, now - MINUTE, now);

    Assert.assertTrue(inSession.nudgeOwed(0));
    Assert.assertFalse(inSession.nudgeOwed(PLAN + 6 * DAY));
    Assert.assertFalse(schedule(SINCE, now).nudgeOwed(0));
  }

  /** A plan held for a rating is worth telling with enough solves too, still once per plan. */
  @Test
  public void testAnOwedRatingIsToldEvenWithEnoughSolves() {
    long now = PLAN + 6 * DAY + MINUTE;
    Assert.assertTrue(schedule(SINCE, now).nudgeDue(0, true));
    Assert.assertTrue(schedule(5, now).nudgeDue(0, true));
    Assert.assertFalse(schedule(SINCE, PLAN + 6 * DAY - 1).nudgeDue(0, true));
    Assert.assertFalse(schedule(SINCE, now).nudgeDue(PLAN + 6 * DAY, true));
    Assert.assertFalse(PlanSchedule.of(0, 300, 0, DAY, now).nudgeDue(0, true));
    PlanSchedule inSession = PlanSchedule.of(PLAN, 300, SINCE, now - MINUTE, now);
    Assert.assertFalse(inSession.nudgeDue(0, true));
    Assert.assertTrue(inSession.nudgeOwed(0, true));
  }

  /** After the clock is set back, the week runs from now. The caller stores now for it to hold. */
  @Test
  public void testAPlanDatedAheadOfTheClockCountsFromNow() {
    long now = PLAN - 300 * DAY;
    PlanSchedule schedule = PlanSchedule.of(PLAN, 300, SINCE, 0, now);

    Assert.assertEquals(State.NOT_DUE, schedule.getState());
    Assert.assertEquals(now + 7 * DAY, schedule.getDueAt());
  }

  /** Or a clock set back a year would hold every plan for a year. */
  @Test
  public void testASolveDatedAheadOfTheClockIsNoSession() {
    long now = PLAN + 8 * DAY;
    PlanSchedule schedule = PlanSchedule.of(PLAN, 300, SINCE, now + 300 * DAY, now);

    Assert.assertEquals(State.READY, schedule.getState());
    Assert.assertEquals(0, schedule.getQuietUntil());
  }

  private static PlanSchedule schedule(int solvesSince, long now) {
    return PlanSchedule.of(PLAN, 300, solvesSince, OLD_SOLVE, now);
  }
}
