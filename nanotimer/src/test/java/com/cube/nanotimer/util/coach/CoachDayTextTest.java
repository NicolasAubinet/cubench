package com.cube.nanotimer.util.coach;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.Collections;

@RunWith(JUnit4.class)
public class CoachDayTextTest {

  /** A case focus is named by its set, never by whichever case it lists first. */
  @Test
  public void testACaseFocusIsNamedByItsSet() {
    Assert.assertEquals("pll", CoachDayText.caseFamily(Arrays.asList("pll_e", "pll_ga")));
    Assert.assertEquals("oll", CoachDayText.caseFamily(Arrays.asList("oll_21")));
  }

  @Test
  public void testMixedSetsOrNoCasesFallBackToTheTitle() {
    Assert.assertNull(CoachDayText.caseFamily(Arrays.asList("oll_21", "pll_e")));
    Assert.assertNull(CoachDayText.caseFamily(Collections.<String>emptyList()));
    Assert.assertNull(CoachDayText.caseFamily(Arrays.asList("pair_23")));
  }
}
