package com.cube.nanotimer.coach;

import com.cube.nanotimer.vo.StepStats;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(JUnit4.class)
public class StepSharesTest {

  private static final double EPSILON = 0.001;

  /** How many of the 100 solves needed a second algorithm to orient the last layer. */
  private static final int ONE_LOOK = 0;
  private static final int TWO_LOOK = 58;

  @Test
  public void testAStepIsMeasuredAgainstTheStepsItIsComparedWith() {
    StepShares shares = StepShares.of(payload("cfop", ONE_LOOK, 4000, 15000, 3000, 5000));

    // 4+15+3+5 = 27 seconds, so the cross took 4/27 of it.
    Assert.assertEquals(4000d / 27000, shares.actual("cross").doubleValue(), EPSILON);
    Assert.assertEquals(0.12, shares.expected("cross").doubleValue(), EPSILON);
  }

  /** A step is worth what it takes, not what it takes when it happens: a skipped OLL costs nothing. */
  @Test
  public void testASkippedStepIsNotAveragedIn() {
    List<StepFigure> families = Arrays.asList(family("cross", 100, 4000), family("f2l", 100, 15000),
        family("oll", 10, 3000), family("pll", 100, 5000));
    StepShares shares = StepShares.of(payload("cfop", ONE_LOOK, families));

    // The OLL happened 10 times in 100 solves, so it is a tenth of its mean across them.
    Assert.assertEquals(3000d * 10 / 2430000, shares.actual("oll").doubleValue(), EPSILON);
  }

  @Test
  public void testAMethodWithNoResearchedBaselineIsNotCompared() {
    Assert.assertTrue(StepShares.of(payload("roux", ONE_LOOK, 4000, 15000, 3000, 5000)).isEmpty());
  }

  /** A two-looked last layer is a step like any other: one denominator, no special case. */
  @Test
  public void testATwoLookedLastLayerIsCompared() {
    StepShares shares = StepShares.of(payload("cfop", TWO_LOOK, 4000, 15000, 12000, 5000));

    // 4+15+12+5 = 36 seconds, so the last layer took 12/36 against a 16.5% baseline.
    Assert.assertEquals(12000d / 36000, shares.actual("oll").doubleValue(), EPSILON);
    Assert.assertEquals(0.165, shares.expected("oll").doubleValue(), EPSILON);
    Assert.assertEquals(Arrays.asList("cross", "f2l", "oll", "pll"),
        new ArrayList<String>(shares.families()));
  }

  /**
   * What a big step does to the others, which is not a distortion but the truth: a solver spending a
   * third of the solve orienting the last layer does spend proportionally less of it on the cross.
   */
  @Test
  public void testAStepIsAShareOfTheWholeSolveEvenWhenAnotherIsHuge() {
    StepShares shares = StepShares.of(payload("cfop", TWO_LOOK, 6000, 15000, 12000, 5000));

    Assert.assertEquals(6000d / 38000, shares.actual("cross").doubleValue(), EPSILON);
    Assert.assertEquals(0.12, shares.expected("cross").doubleValue(), EPSILON);
    Assert.assertTrue(shares.actual("oll") > shares.expected("oll"));
  }

  @Test
  public void testTheComparedSharesEachSumToOne() {
    StepShares shares = StepShares.of(payload("cfop", TWO_LOOK, 4000, 15000, 12000, 5000));

    double actual = 0;
    double expected = 0;
    for (String family : shares.families()) {
      actual += shares.actual(family).doubleValue();
      expected += shares.expected(family).doubleValue();
    }
    Assert.assertEquals(1, actual, EPSILON);
    Assert.assertEquals(1, expected, EPSILON);
  }

  private static CoachPayload payload(String method, int twoLooks, long cross, long f2l,
      long oll, long pll) {
    return payload(method, twoLooks, Arrays.asList(family("cross", 100, cross),
        family("f2l", 100, f2l), family("oll", 100, oll), family("pll", 100, pll)));
  }

  private static CoachPayload payload(String method, int twoLooks, List<StepFigure> families) {
    return new CoachPayload(CoachPayload.VERSION, "3x3", method, 100, 500, 10, twoLooks,
        family("solve", 100, 27000), families,
        Collections.singletonList(family("pair", 400, 3700)),
        Collections.<StepFigure>emptyList(), Collections.<StepFigure>emptyList(),
        Collections.<CaseComparison>emptyList(), Collections.<String>emptyList(),
        Collections.<String>emptyList(), Collections.<String>emptyList());
  }

  private static StepFigure family(String code, int count, long meanMs) {
    return StepFigure.family(new StepStats(code, count, meanMs * count, 0, meanMs,
        (double) meanMs * meanMs * count), false, 0, null);
  }
}
