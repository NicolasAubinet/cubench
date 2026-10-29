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
    samples.add(new StepSample("pair_rf", 3000, 900, true, 0));
    StepTallies tallies = new StepTallies(samples);

    Assert.assertEquals(1, tallies.getSteps().size());
    Assert.assertEquals("f2l", tallies.getSteps().get(0).getCode());
    Assert.assertEquals(1, tallies.getParts().size());
    Assert.assertEquals("pair_rf", tallies.getParts().get(0).getCode());
  }

  /** Solve 6's F2L is short but within its own spread: only the long cross before it drops it. */
  @Test
  public void testALongStepDropsTheNextStepOfItsSolve() {
    List<StepSample> samples = solves(new long[] { 2000, 2100, 2200, 2300, 2400, 10000 },
        new long[] { 8000, 8200, 8400, 8600, 8800, 7200 });
    samples.add(new StepSample("pair_rf", 1500, 500, true, 6, 0, 1));
    StepTallies tallies = new StepTallies(samples);

    Assert.assertEquals(5, tallies.get("cross").getCount());
    Assert.assertEquals(5, tallies.get("f2l").getCount());
    Assert.assertEquals(8400, tallies.get("f2l").getMeanMs());
    Assert.assertEquals(1.0 / 6, tallies.getRejectionRate("f2l"), 0.001);
    Assert.assertEquals(1, tallies.get("pair_rf").getCount()); // pairs after the fix were timed honestly
  }

  @Test
  public void testAShortStepLeavesTheNextOneAlone() {
    StepTallies tallies = new StepTallies(solves(new long[] { 2000, 2100, 2200, 2300, 2400, 200 },
        new long[] { 8000, 8200, 8400, 8600, 8800, 7200 }));

    Assert.assertEquals(5, tallies.get("cross").getCount());
    Assert.assertEquals(6, tallies.get("f2l").getCount());
  }

  @Test
  public void testASkipAfterALongStepIsKept() {
    List<StepSample> samples = solves(new long[] { 2000, 2100, 2200, 2300, 2400, 10000 },
        new long[] { 8000, 8200, 8400, 8600, 8800, 7200 });
    samples.add(new StepSample("oll_skip", 0, 0, false, 6, null, 1));
    samples.remove(11); // solve 6's f2l, so the skip is what comes next
    StepTallies tallies = new StepTallies(samples);

    Assert.assertEquals(1, tallies.get("oll_skip").getCount());
  }

  /** As the screens always read it: a code is a step if any solve had it as one. */
  @Test
  public void testACodeMetAsBothStepAndPartIsAStep() {
    List<StepSample> samples = new ArrayList<StepSample>();
    samples.add(new StepSample("oll_27", 2000, 500, false, 1, null, 2));
    samples.add(new StepSample("oll_27", 1500, 500, true, 2, 1, 2));
    StepTallies tallies = new StepTallies(samples);

    Assert.assertEquals(1, tallies.getSteps().size());
    Assert.assertTrue(tallies.getParts().isEmpty());
  }

  /** A cross then an F2L per solve, solve ids from 1. */
  private static List<StepSample> solves(long[] crosses, long[] f2ls) {
    List<StepSample> samples = new ArrayList<StepSample>();
    for (int i = 0; i < crosses.length; i++) {
      samples.add(new StepSample("cross", crosses[i], 500, false, i + 1, null, 0));
      samples.add(new StepSample("f2l", f2ls[i], 2000, false, i + 1, null, 1));
    }
    return samples;
  }

  private static List<StepSample> times(String code, long... times) {
    List<StepSample> samples = new ArrayList<StepSample>();
    for (long time : times) {
      samples.add(new StepSample(code, time, time / 3, false, 0));
    }
    return samples;
  }
}
