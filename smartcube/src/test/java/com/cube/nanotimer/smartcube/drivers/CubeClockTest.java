package com.cube.nanotimer.smartcube.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CubeClockTest {

  private final CubeClock clock = new CubeClock();

  @Test
  public void firstMoveFitsTheOffsetOntoHostTime() {
    assertEquals(50000, clock.stamp(1200, 50000));
    assertEquals(50300, clock.stamp(1500, 50300));
  }

  @Test
  public void keepsTheCubesSpacingWhenHostTimeArrivesLate() {
    clock.stamp(1000, 50000);
    // The same three moves 250 ms apart, their notifications delivered in one late burst.
    assertEquals(50250, clock.stamp(1250, 51800));
    assertEquals(50500, clock.stamp(1500, 51800));
    assertEquals(50750, clock.stamp(1750, 51810));
  }

  @Test
  public void neverGoesBackwardsWhenHostTimeSwingsPastTheOldDriftLimit() {
    clock.stamp(1000, 50000);
    long late = clock.stamp(1250, 53000); // delivered 2.75 s late
    long prompt = clock.stamp(1500, 50500); // and the next one on time again
    assertEquals(50250, late);
    assertTrue("stamps must not go backwards mid-stream", prompt > late);
    assertEquals(50500, prompt);
  }

  @Test
  public void refitsWhenTheCubeSleptAndCameBackCountingFromZero() {
    clock.stamp(600000, 50000);
    assertEquals(120000, clock.stamp(30, 120000));
  }

  @Test
  public void refitsAtAGapBetweenSolvesOnceTheClocksHaveReallyParted() {
    clock.stamp(1000, 50000);
    assertEquals(70000, clock.stamp(18000, 70000)); // 17 s of cube time, 20 s of host time
  }

  @Test
  public void leavesTheSameGapAloneWhileTheClocksStillAgree() {
    clock.stamp(1000, 50000);
    assertEquals(70100, clock.stamp(21100, 70000));
  }

  @Test
  public void leavesAPauseInsideASolveAlone() {
    clock.stamp(1000, 50000);
    assertEquals(58000, clock.stamp(9000, 60000)); // an 8 s stall, stamped 2 s late on top
  }

  @Test
  public void aMoveWithNoTimeOfItsOwnRepeatsTheLastStamp() {
    assertEquals(0, clock.lastStamp());
    clock.stamp(1000, 50000);
    assertEquals(50000, clock.lastStamp());
  }
}
