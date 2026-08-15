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

  @Test
  public void testAStepIsMeasuredAgainstTheStepsItIsComparedWith() {
    StepShares shares = StepShares.of(payload("cfop", oneLook(), 4000, 15000, 3000, 5000));

    // 4+15+3+5 = 27 seconds, so the cross took 4/27 of it.
    Assert.assertEquals(4000d / 27000, shares.actual("cross").doubleValue(), EPSILON);
    Assert.assertEquals(0.12, shares.expected("cross").doubleValue(), EPSILON);
  }

  /** A step is worth what it takes, not what it takes when it happens: a skipped OLL costs nothing. */
  @Test
  public void testASkippedStepIsNotAveragedIn() {
    List<StepFigure> families = Arrays.asList(family("cross", 100, 4000), family("f2l", 100, 15000),
        family("oll", 10, 3000), family("pll", 100, 5000));
    StepShares shares = StepShares.of(payload("cfop", oneLook(), families));

    // The OLL happened 10 times in 100 solves, so it is a tenth of its mean across them.
    Assert.assertEquals(3000d * 10 / 2430000, shares.actual("oll").doubleValue(), EPSILON);
  }

  @Test
  public void testAMethodWithNoResearchedBaselineIsNotCompared() {
    Assert.assertTrue(StepShares.of(payload("roux", oneLook(), 4000, 15000, 3000, 5000)).isEmpty());
  }

  /** The last layer is over any one-look baseline by construction, so it is not measured at all. */
  @Test
  public void testATwoLookedLastLayerIsLeftOutOfTheComparison() {
    StepShares shares = StepShares.of(payload("cfop", twoLook(), 4000, 15000, 12000, 5000));

    Assert.assertNull(shares.actual("oll"));
    Assert.assertNull(shares.expected("oll"));
    Assert.assertEquals(Arrays.asList("cross", "f2l", "pll"),
        new ArrayList<String>(shares.families()));
  }

  /**
   * The reason dropping it has to renormalize: a two-looker spends so long on OLL that every other
   * step's share of the whole falls, and a cross that is genuinely slow would read as fine.
   */
  @Test
  public void testDroppingTheLastLayerRenormalizesWhatIsLeft() {
    StepShares shares = StepShares.of(payload("cfop", twoLook(), 6000, 15000, 12000, 5000));

    // Against the whole solve the cross is 6/38, under its 12%; against the rest it is 6/26, over it.
    Assert.assertEquals(6000d / 26000, shares.actual("cross").doubleValue(), EPSILON);
    Assert.assertEquals(0.12 / 0.835, shares.expected("cross").doubleValue(), EPSILON);
    Assert.assertTrue(shares.actual("cross") > shares.expected("cross"));
  }

  @Test
  public void testTheComparedSharesEachSumToOne() {
    StepShares shares = StepShares.of(payload("cfop", twoLook(), 4000, 15000, 12000, 5000));

    double actual = 0;
    double expected = 0;
    for (String family : shares.families()) {
      actual += shares.actual(family).doubleValue();
      expected += shares.expected(family).doubleValue();
    }
    Assert.assertEquals(1, actual, EPSILON);
    Assert.assertEquals(1, expected, EPSILON);
  }

  private static List<StepFigure> oneLook() {
    return Collections.singletonList(family("pair", 400, 3700));
  }

  private static List<StepFigure> twoLook() {
    return Arrays.asList(family("pair", 400, 3700), family("edges", 58, 2100),
        family("corners", 58, 2400));
  }

  private static CoachPayload payload(String method, List<StepFigure> parts, long cross, long f2l,
      long oll, long pll) {
    return payload(method, parts, Arrays.asList(family("cross", 100, cross),
        family("f2l", 100, f2l), family("oll", 100, oll), family("pll", 100, pll)));
  }

  private static CoachPayload payload(String method, List<StepFigure> parts,
      List<StepFigure> families) {
    return new CoachPayload(CoachPayload.VERSION, "3x3", method, 100, 500, 10,
        family("solve", 100, 27000), families, parts,
        Collections.<StepFigure>emptyList(), Collections.<StepFigure>emptyList(),
        Collections.<CaseComparison>emptyList());
  }

  private static StepFigure family(String code, int count, long meanMs) {
    return StepFigure.family(new StepStats(code, count, meanMs * count, 0, meanMs,
        (double) meanMs * meanMs * count), false, 0, null);
  }
}
