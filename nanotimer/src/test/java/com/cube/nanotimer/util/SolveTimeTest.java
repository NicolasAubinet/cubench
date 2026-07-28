package com.cube.nanotimer.util;

import com.cube.nanotimer.vo.SolveTime;
import junit.framework.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * A DNF used to overwrite the recorded time with the sentinel and lose it. It now keeps the time
 * it replaced, so the mark can be taken back — except on the DNFs that never had one.
 */
@RunWith(JUnit4.class)
public class SolveTimeTest {

  private static SolveTime solveOf(long time) {
    SolveTime solveTime = new SolveTime();
    solveTime.setTime(time);
    return solveTime;
  }

  @Test
  public void testAnOrdinarySolveIsNeitherDnfNorUndoable() {
    SolveTime solveTime = solveOf(12345);
    Assert.assertFalse(solveTime.isDNF());
    Assert.assertFalse(solveTime.canUndoDNF());
    Assert.assertNull(solveTime.getTimeBeforeDnf());
  }

  @Test
  public void testDnfKeepsTheTimeItReplaced() {
    SolveTime solveTime = solveOf(12345);
    solveTime.setDNF();
    Assert.assertTrue(solveTime.isDNF());
    Assert.assertEquals(SolveTime.DNF_TIME, solveTime.getTime());
    Assert.assertEquals(Long.valueOf(12345), solveTime.getTimeBeforeDnf());
    Assert.assertTrue(solveTime.canUndoDNF());
  }

  @Test
  public void testUndoPutsTheTimeBackAndForgetsIt() {
    SolveTime solveTime = solveOf(12345);
    solveTime.setDNF();
    solveTime.undoDNF();
    Assert.assertFalse(solveTime.isDNF());
    Assert.assertEquals(12345, solveTime.getTime());
    Assert.assertNull(solveTime.getTimeBeforeDnf()); // nothing left to restore
    Assert.assertFalse(solveTime.canUndoDNF());
  }

  // The user may flip the mark as often as they like; every round must land on the same time.
  @Test
  public void testTheMarkCanBeFlippedRepeatedly() {
    SolveTime solveTime = solveOf(12345);
    for (int i = 0; i < 3; i++) {
      solveTime.setDNF();
      Assert.assertEquals(SolveTime.DNF_TIME, solveTime.getTime());
      solveTime.undoDNF();
      Assert.assertEquals(12345, solveTime.getTime());
    }
  }

  // Undoing twice must not resurrect anything: the second call has nothing to work from.
  @Test
  public void testUndoingTwiceChangesNothing() {
    SolveTime solveTime = solveOf(12345);
    solveTime.setDNF();
    solveTime.undoDNF();
    solveTime.undoDNF();
    Assert.assertEquals(12345, solveTime.getTime());
    Assert.assertFalse(solveTime.isDNF());
  }

  // Pressing DNF on a solve that already is one must not overwrite the remembered time with the
  // sentinel — that would turn a restorable DNF into a dead one.
  @Test
  public void testDnfOnAnExistingDnfKeepsWhatItRemembers() {
    SolveTime solveTime = solveOf(12345);
    solveTime.setDNF();
    solveTime.setDNF();
    Assert.assertEquals(Long.valueOf(12345), solveTime.getTimeBeforeDnf());
    Assert.assertTrue(solveTime.canUndoDNF());
    solveTime.undoDNF();
    Assert.assertEquals(12345, solveTime.getTime());
  }

  // A DNF recorded before the time was kept (migrated from an older DB) has nothing to go back to:
  // the tap is inert rather than restoring a sentinel.
  @Test
  public void testALegacyDnfCannotBeUndone() {
    SolveTime solveTime = solveOf(SolveTime.DNF_TIME);
    Assert.assertTrue(solveTime.isDNF());
    Assert.assertFalse(solveTime.canUndoDNF());
    solveTime.undoDNF();
    Assert.assertEquals(SolveTime.DNF_TIME, solveTime.getTime()); // still a DNF
    Assert.assertTrue(solveTime.isDNF());
  }

  // Same for a DNF entered by hand through the add-time dialog: no time was ever typed.
  @Test
  public void testAHandEnteredDnfRemembersNothing() {
    SolveTime solveTime = solveOf(SolveTime.DNF_TIME);
    solveTime.setDNF();
    Assert.assertNull(solveTime.getTimeBeforeDnf());
    Assert.assertFalse(solveTime.canUndoDNF());
  }

  // A zero time is not a solve, so it is not worth restoring either.
  @Test
  public void testAZeroTimeIsNotRemembered() {
    SolveTime solveTime = solveOf(0);
    solveTime.setDNF();
    Assert.assertTrue(solveTime.isDNF());
    Assert.assertNull(solveTime.getTimeBeforeDnf());
    Assert.assertFalse(solveTime.canUndoDNF());
  }

  // The +2 is part of the recorded time, so it must come back with it.
  @Test
  public void testAPenalizedTimeComesBackWithItsPenalty() {
    SolveTime solveTime = solveOf(12345);
    solveTime.setPlusTwo(true, true);
    Assert.assertEquals(12345 + SolveTime.PLUS_TWO_PENALTY_MS, solveTime.getTime());

    solveTime.setDNF();
    solveTime.undoDNF();
    Assert.assertEquals(12345 + SolveTime.PLUS_TWO_PENALTY_MS, solveTime.getTime());
    Assert.assertTrue(solveTime.isPlusTwo());
  }

  // The stats layer reads a DNF off the time alone, so the sentinel must stay negative whatever
  // the solve remembers: TimesStatistics and CubeSession see plain longs, never this flag.
  @Test
  public void testTheSentinelStaysNegativeForTheStatsLayer() {
    SolveTime solveTime = solveOf(12345);
    solveTime.setDNF();
    Assert.assertTrue(solveTime.getTime() < 0);
  }

}
