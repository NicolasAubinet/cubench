package com.cube.nanotimer.util.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.vo.SolveType;
import java.util.List;
import org.junit.Test;

public class HeroStatTest {

  private static SolveType solveType(boolean blind) {
    return new SolveType(1, "3x3", blind, null, 1);
  }

  @Test
  public void offersTheSuccessRatesToABlindSolveTypeOnly() {
    List<HeroStat> blind = HeroStat.optionsFor(solveType(true));
    List<HeroStat> sighted = HeroStat.optionsFor(solveType(false));

    assertTrue(blind.contains(HeroStat.ACC50));
    assertFalse(sighted.contains(HeroStat.ACC50));
    // A blind attempt is counted in threes, so the average of five is the sighted side's.
    assertFalse(blind.contains(HeroStat.AO5));
    assertTrue(sighted.contains(HeroStat.AO5));
  }

  @Test
  public void startsEachSideOnTheStatisticsItsSolversWatch() {
    assertEquals(HeroStat.AO5, HeroStat.defaultFor(0, false));
    assertEquals(HeroStat.AO12, HeroStat.defaultFor(1, false));
    assertEquals(HeroStat.PB, HeroStat.defaultFor(2, false));

    assertEquals(HeroStat.AO12, HeroStat.defaultFor(0, true));
    assertEquals(HeroStat.ACC50, HeroStat.defaultFor(1, true));
    assertEquals(HeroStat.PB, HeroStat.defaultFor(2, true));
  }

  @Test
  public void everyDefaultIsOneOfTheOptionsItsSolveTypeIsOffered() {
    for (int cell = 0; cell < 3; cell++) {
      assertTrue(HeroStat.optionsFor(solveType(true)).contains(HeroStat.defaultFor(cell, true)));
      assertTrue(HeroStat.optionsFor(solveType(false)).contains(HeroStat.defaultFor(cell, false)));
    }
  }
}
