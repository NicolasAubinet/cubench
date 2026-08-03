package com.cube.nanotimer.util;

import com.cube.nanotimer.session.MethodStatistics;
import com.cube.nanotimer.vo.StepStats;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

@RunWith(JUnit4.class)
public class MethodStatisticsTest {

  @Test
  public void testFamilyIsItsCasesTogether() {
    List<StepStats> steps = new ArrayList<StepStats>();
    steps.add(tally("pll_t", 3, 3000));    // 1000 each
    steps.add(tally("pll_gb", 1, 3000));   // 3000 once
    MethodStatistics stats = new MethodStatistics(steps, 4);

    Assert.assertEquals(4, stats.getFamily("pll").getCount());
    Assert.assertEquals(1500, stats.getFamily("pll").getMeanMs()); // by solve, not by case
  }

  @Test
  public void testSkipsAreCountedApartFromTheMean() {
    List<StepStats> steps = new ArrayList<StepStats>();
    steps.add(tally("oll_21", 3, 6000));
    steps.add(tally("oll_skip", 1, 0));
    MethodStatistics stats = new MethodStatistics(steps, 4);

    Assert.assertEquals(3, stats.getFamily("oll").getCount());
    Assert.assertEquals(2000, stats.getFamily("oll").getMeanMs());
    Assert.assertEquals(0.25, stats.getSkipRate("oll"), 0.001);
    Assert.assertEquals(1, stats.getCases("oll").size()); // the skip is not a case that was solved
  }

  @Test
  public void testFamilyThatOnlyEverSkipped() {
    List<StepStats> steps = new ArrayList<StepStats>();
    steps.add(tally("oll_skip", 2, 0));
    MethodStatistics stats = new MethodStatistics(steps, 2);

    Assert.assertNull(stats.getFamily("oll"));
    Assert.assertEquals(1.0, stats.getSkipRate("oll"), 0.001);
    Assert.assertTrue(stats.getCases("oll").isEmpty());
    Assert.assertEquals(0, stats.getTimeLostMs("oll_skip"));
  }

  @Test
  public void testStepWithoutCases() {
    List<StepStats> steps = new ArrayList<StepStats>();
    steps.add(tally("cross", 10, 20000));
    MethodStatistics stats = new MethodStatistics(steps, 10);

    Assert.assertEquals(2000, stats.getFamily("cross").getMeanMs());
    Assert.assertTrue(stats.getCases("cross").isEmpty());
    Assert.assertEquals(0.0, stats.getSkipRate("cross"), 0.001);
  }

  @Test
  public void testTimeLostIsHowFarOverTimesHowOften() {
    List<StepStats> steps = new ArrayList<StepStats>();
    steps.add(tally("pll_t", 9, 9000));   // 1000 each
    steps.add(tally("pll_gb", 1, 3000));  // 2000 over the family's 1200 mean
    MethodStatistics stats = new MethodStatistics(steps, 10);

    Assert.assertEquals(1200, stats.getFamily("pll").getMeanMs());
    Assert.assertEquals(1800, stats.getTimeLostMs("pll_gb"));
    Assert.assertEquals(0, stats.getTimeLostMs("pll_t")); // under its family, so nothing lost
    Assert.assertEquals(0, stats.getTimeLostMs("pll_v")); // never seen
  }

  @Test
  public void testWorstCasesRankByCostNotBySlowness() {
    List<StepStats> steps = new ArrayList<StepStats>();
    steps.add(tally("pll_t", 20, 20000));  // 1000 each, the bulk of the window
    steps.add(tally("pll_gb", 10, 20000)); // 2000 each, ten times over
    steps.add(tally("pll_v", 1, 5000));    // 5000, but once
    MethodStatistics stats = new MethodStatistics(steps, 31);

    List<StepStats> worst = stats.getWorstCases("pll", 1);
    Assert.assertEquals("pll_gb", worst.get(0).getCode()); // slower overall, though V is slower
    Assert.assertEquals("pll_v", worst.get(1).getCode());
    Assert.assertEquals(2, worst.size());

    // and the one-off drops out entirely once a case has to have been seen a few times
    List<StepStats> seenEnough = stats.getWorstCases("pll", 5);
    Assert.assertEquals(1, seenEnough.size());
    Assert.assertEquals("pll_gb", seenEnough.get(0).getCode());
  }

  // Measured on real solves: an F2L slot 0.11s over a 3.74s family, seen 93 times, outranked slots
  // 2s over seen 6 times. A case has to be clear of its family before its count can speak for it.
  @Test
  public void testACaseBarelyOverItsFamilyIsNotWorthNaming() {
    List<StepStats> steps = new ArrayList<StepStats>();
    steps.add(tally("pair_fl", 93, 93 * 3470L));
    steps.add(tally("pair_lb", 93, 93 * 3850L)); // 0.11s over the family, but seen every solve
    steps.add(tally("pair_rb", 6, 6 * 5890L));   // 2.15s over, and seen a handful of times
    MethodStatistics stats = new MethodStatistics(steps, 93);

    Assert.assertTrue(stats.getTimeLostMs("pair_lb") > 0); // the arithmetic still says it costs time
    List<StepStats> worst = stats.getWorstCases("pair", 5);
    Assert.assertEquals(1, worst.size());
    Assert.assertEquals("pair_rb", worst.get(0).getCode());
  }

  @Test
  public void testCasesAreSortedSlowestFirst() {
    List<StepStats> steps = new ArrayList<StepStats>();
    steps.add(tally("pll_t", 2, 2000));
    steps.add(tally("pll_gb", 2, 6000));
    steps.add(tally("pll_ua", 2, 3000));
    MethodStatistics stats = new MethodStatistics(steps, 6);

    List<StepStats> cases = stats.getCases("pll");
    Assert.assertEquals("pll_gb", cases.get(0).getCode());
    Assert.assertEquals("pll_ua", cases.get(1).getCode());
    Assert.assertEquals("pll_t", cases.get(2).getCode());
  }

  @Test
  public void testRecognitionShareAndSpread() {
    // two solves of the same case: 1000 (400 looking) and 3000 (1600 looking)
    StepStats step = new StepStats("pll_gb", 2, 4000, 2000, 1000,
        1000d * 1000 + 3000d * 3000);
    Assert.assertEquals(2000, step.getMeanMs());
    Assert.assertEquals(1000, step.getMeanRecognitionMs());
    Assert.assertEquals(1000, step.getMeanExecutionMs());
    Assert.assertEquals(0.5, step.getRecognitionShare(), 0.001);
    Assert.assertEquals(1000, step.getStdDevMs());
    Assert.assertEquals(1000, step.getBestMs());
  }

  @Test
  public void testEmptyWindow() {
    MethodStatistics stats = new MethodStatistics(new ArrayList<StepStats>(), 0);
    Assert.assertTrue(stats.getFamilies().isEmpty());
    Assert.assertNull(stats.getFamily("oll"));
    Assert.assertTrue(stats.getCases("oll").isEmpty());
    Assert.assertTrue(stats.getWorstCases("oll", 1).isEmpty());
    Assert.assertEquals(0.0, stats.getSkipRate("oll"), 0.001);
  }

  @Test
  public void testFamiliesKeepTheOrderTheyAreGivenIn() {
    List<StepStats> steps = new ArrayList<StepStats>();
    steps.add(tally("cross", 1, 1000));
    steps.add(tally("f2l", 1, 8000));
    steps.add(tally("pair_rf", 4, 8000));
    steps.add(tally("oll_21", 1, 2000));
    steps.add(tally("pll_t", 1, 2000));
    MethodStatistics stats = new MethodStatistics(steps, 1);

    List<StepStats> families = stats.getFamilies();
    Assert.assertEquals("cross", families.get(0).getCode());
    Assert.assertEquals("f2l", families.get(1).getCode());
    Assert.assertEquals("pair", families.get(2).getCode());
    Assert.assertEquals("oll", families.get(3).getCode());
    Assert.assertEquals("pll", families.get(4).getCode());
    Assert.assertEquals("rf", MethodStatistics.caseOf("pair_rf"));
    Assert.assertNull(MethodStatistics.caseOf("cross"));
  }

  /** A tally of one code: {@code count} solves totalling {@code totalMs}, evenly spread. */
  private StepStats tally(String code, int count, long totalMs) {
    long each = count == 0 ? 0 : totalMs / count;
    return new StepStats(code, count, totalMs, totalMs / 2, each, (double) each * each * count);
  }
}
