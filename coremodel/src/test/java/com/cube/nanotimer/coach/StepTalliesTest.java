package com.cube.nanotimer.coach;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

@RunWith(JUnit4.class)
public class StepTalliesTest {

  @Test
  public void testFarOutlierIsDroppedAndCounted() {
    List<StepSample> samples = times("pll_gb", 1800, 1900, 2000, 2100, 2200, 2300, 20000);
    StepTallies tallies = new StepTallies(samples);

    Assert.assertEquals(6, tallies.get("pll_gb").getCount());
    Assert.assertEquals(2050, tallies.get("pll_gb").getMeanMs());
    Assert.assertEquals(1.0 / 7, tallies.getRejectionRate("pll_gb"), 0.001);
  }

  @Test
  public void testNothingIsDroppedFromTooFewOccurrences() {
    StepTallies tallies = new StepTallies(times("pll_gb", 1800, 2000, 20000));

    Assert.assertEquals(3, tallies.get("pll_gb").getCount());
    Assert.assertEquals(0, tallies.getRejectionRate("pll_gb"), 0.001);
  }

  /** A skip is a step that never happened rather than a fast one, so it has no spread to judge. */
  @Test
  public void testSkipsAreNotFiltered() {
    StepTallies tallies = new StepTallies(times("oll_skip", 0, 0, 0, 0, 0, 0, 900));

    Assert.assertEquals(7, tallies.get("oll_skip").getCount());
    Assert.assertEquals(0, tallies.getRejectionRate("oll_skip"), 0.001);
  }

  @Test
  public void testAFamilyIsRejectedAsOftenAsItsCasesPutTogether() {
    List<StepSample> samples = times("pll_gb", 1800, 1900, 2000, 2100, 2200, 2300, 20000);
    samples.addAll(times("pll_t", 1000, 1100, 1200, 1300, 1400, 1500));
    StepTallies tallies = new StepTallies(samples);

    Assert.assertEquals(1.0 / 13, tallies.getRejectionRate("pll"), 0.001);
  }

  @Test
  public void testStepsAndPartsAreKeptApart() {
    List<StepSample> samples = times("f2l", 12000, 13000, 14000);
    samples.add(new StepSample("pair_rf", 3000, 900, true));
    StepTallies tallies = new StepTallies(samples);

    Assert.assertEquals(1, tallies.getSteps().size());
    Assert.assertEquals("f2l", tallies.getSteps().get(0).getCode());
    Assert.assertEquals(1, tallies.getParts().size());
    Assert.assertEquals("pair_rf", tallies.getParts().get(0).getCode());
  }

  private static List<StepSample> times(String code, long... times) {
    List<StepSample> samples = new ArrayList<StepSample>();
    for (long time : times) {
      samples.add(new StepSample(code, time, time / 3, false));
    }
    return samples;
  }
}
