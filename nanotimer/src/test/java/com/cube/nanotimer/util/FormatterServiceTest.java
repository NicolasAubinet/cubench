package com.cube.nanotimer.util;

import junit.framework.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FormatterServiceTest {
  @Test
  public void testSolveTimeFormat() {
    long locTime = 2004;
    Assert.assertEquals("2.00", FormatterService.INSTANCE.formatSolveTime(locTime, "", false));
    locTime = 2005;
    Assert.assertEquals("2.01", FormatterService.INSTANCE.formatSolveTime(locTime, "", false));
    Assert.assertEquals("2.005", FormatterService.INSTANCE.formatSolveTime(locTime, "", true));
    locTime = 2009;
    Assert.assertEquals("2.01", FormatterService.INSTANCE.formatSolveTime(locTime, "", false));
    locTime = 2999;
    Assert.assertEquals("3.00", FormatterService.INSTANCE.formatSolveTime(locTime, "", false));
    Assert.assertEquals("2.999", FormatterService.INSTANCE.formatSolveTime(locTime, "", true));
    locTime = 2919;
    Assert.assertEquals("2.92", FormatterService.INSTANCE.formatSolveTime(locTime, "", false));
    Assert.assertEquals("2.919", FormatterService.INSTANCE.formatSolveTime(locTime, "", true));
    locTime = 2990;
    Assert.assertEquals("2.99", FormatterService.INSTANCE.formatSolveTime(locTime, "", false));
    Assert.assertEquals("2.990", FormatterService.INSTANCE.formatSolveTime(locTime, "", true));
    locTime = 2994;
    Assert.assertEquals("2.99", FormatterService.INSTANCE.formatSolveTime(locTime, "", false));
    Assert.assertEquals("2.994", FormatterService.INSTANCE.formatSolveTime(locTime, "", true));
    locTime = 2995;
    Assert.assertEquals("3.00", FormatterService.INSTANCE.formatSolveTime(locTime, "", false));
    Assert.assertEquals("2.995", FormatterService.INSTANCE.formatSolveTime(locTime, "", true));
  }

  @Test
  public void testSolveTimeDifferenceFormat() {
    Assert.assertEquals("-0.54", FormatterService.INSTANCE.formatSolveTimeDifference(540, false));
    Assert.assertEquals("-0.540", FormatterService.INSTANCE.formatSolveTimeDifference(540, true));
    Assert.assertEquals("-1.23", FormatterService.INSTANCE.formatSolveTimeDifference(1234, false));
    Assert.assertEquals("-1.234", FormatterService.INSTANCE.formatSolveTimeDifference(1234, true));
    // rounds to the displayed precision, like formatSolveTime
    Assert.assertEquals("-0.00", FormatterService.INSTANCE.formatSolveTimeDifference(4, false));
    Assert.assertEquals("-0.004", FormatterService.INSTANCE.formatSolveTimeDifference(4, true));
  }
}
