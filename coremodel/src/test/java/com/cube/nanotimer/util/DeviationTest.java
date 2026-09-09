package com.cube.nanotimer.util;

import com.cube.nanotimer.vo.Deviation;

import org.junit.Assert;
import org.junit.Test;

public class DeviationTest {

  @Test
  public void testSpreadOfTwoTimes() {
    // 1000 and 3000: a mean of 2000, and each of them 1000 off it
    Assert.assertEquals(1000, Deviation.of(2, 4000, 1000d * 1000 + 3000d * 3000));
  }

  @Test
  public void testSameTimeEveryTimeHasNoSpread() {
    Assert.assertEquals(0, Deviation.of(3, 6000, 3 * 2000d * 2000));
  }

  @Test
  public void testEmptyTally() {
    Assert.assertEquals(0, Deviation.of(0, 0, 0));
  }

  /** Rounding can put the variance a hair under zero on a tally that has no spread at all. */
  @Test
  public void testNegativeVarianceReadsAsNone() {
    Assert.assertEquals(0, Deviation.of(2, 4000, 2d * 2000 * 2000 - 0.5));
  }
}
