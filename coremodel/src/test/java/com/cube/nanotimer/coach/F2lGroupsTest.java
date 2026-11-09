package com.cube.nanotimer.coach;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class F2lGroupsTest {

  private static final int FLOOR = CoachPayloadBuilder.FAMILY_FLOOR;

  @Test
  public void testStuckPairsAreWeighedAgainstBasicOnes() {
    List<StepSample> samples = new ArrayList<StepSample>();
    samples.addAll(pairs("pair_27_rf", 30, 3000, 800));
    samples.addAll(pairs("pair_4_fl", 30, 3000, 800));
    samples.addAll(pairs("pair_a12_rf", 12, 5000, 1500));
    samples.addAll(pairs("pair_a3a_br", 8, 5000, 1500));

    List<StepFigure> groups = F2lGroups.of(samples, FLOOR);
    StepFigure basic = figure(groups, F2lGroups.BASIC);
    StepFigure stuck = figure(groups, F2lGroups.STUCK);

    Assert.assertEquals(60, basic.getCount());
    Assert.assertEquals(20, stuck.getCount());
    Assert.assertEquals(3000, basic.getMeanMs(), 50);
    Assert.assertEquals(5000, stuck.getMeanMs(), 50);
    Assert.assertEquals(1500, stuck.getRecognitionMs().longValue());
    Assert.assertEquals(0.25, stuck.getShare().doubleValue(), 1e-9);
    Assert.assertNull(basic.getShare());
  }

  /** A placement neither set names, a skip and a slot-only pair say nothing about stuck pieces. */
  @Test
  public void testUnnamedSkippedAndSlotOnlyPairsAreInNeitherGroup() {
    List<StepSample> samples = new ArrayList<StepSample>();
    samples.addAll(pairs("pair_27_rf", 30, 3000, 800));
    samples.addAll(pairs("pair_a12_rf", 10, 5000, 1500));
    samples.addAll(pairs("pair_other_rf", 40, 9000, 3000));
    samples.addAll(pairs("pair_skip", 40, 0, 0));
    samples.addAll(pairs("pair_rf", 40, 9000, 3000));

    List<StepFigure> groups = F2lGroups.of(samples, FLOOR);

    Assert.assertEquals(30, figure(groups, F2lGroups.BASIC).getCount());
    Assert.assertEquals(3000, figure(groups, F2lGroups.BASIC).getMeanMs(), 50);
    Assert.assertEquals(0.25, figure(groups, F2lGroups.STUCK).getShare().doubleValue(), 1e-9);
  }

  /** Too few stuck pairs to stand, so the basic ones have nothing to be weighed against either. */
  @Test
  public void testAComparisonUnderItsFloorIsSentNeitherWay() {
    List<StepSample> samples = new ArrayList<StepSample>();
    samples.addAll(pairs("pair_27_rf", 60, 3000, 800));
    samples.addAll(pairs("pair_a12_rf", FLOOR - 1, 5000, 1500));

    List<StepFigure> groups = F2lGroups.of(samples, FLOOR);

    Assert.assertNull(figure(groups, F2lGroups.BASIC));
    Assert.assertNull(figure(groups, F2lGroups.STUCK));
  }

  @Test
  public void testTheOutlierRuleJudgesAGroupAsOneCode() {
    List<StepSample> samples = new ArrayList<StepSample>();
    samples.addAll(pairs("pair_27_rf", 30, 3000, 800));
    samples.addAll(pairs("pair_a12_rf", 20, 5000, 1500));
    samples.add(new StepSample("pair_a3_rf", 60000, 50000, true, 99, 1)); // a knocked cube

    List<StepFigure> groups = F2lGroups.of(samples, FLOOR);
    StepFigure stuck = figure(groups, F2lGroups.STUCK);

    Assert.assertEquals(20, stuck.getCount());
    Assert.assertEquals(5000, stuck.getMeanMs(), 50);
    Assert.assertTrue(stuck.getRejectionRate() > 0);
    Assert.assertEquals(21 / 51.0, stuck.getShare().doubleValue(), 1e-9); // seen, not kept
  }

  @Test
  public void testTheFirstPairIsReadFromWhereItCameInItsSolve() {
    List<StepSample> samples = new ArrayList<StepSample>();
    for (int solve = 0; solve < 12; solve++) {
      long spread = solve * 10L;
      // stored out of order, so the place is read from the index rather than the row order
      samples.add(new StepSample("pair_3_fl", 2500 + spread, 400 + spread, true, solve, 2));
      samples.add(new StepSample("pair_27_rf", 4000 + spread, 2000 + spread, true, solve, 0));
      samples.add(new StepSample("pair_a1_lb", 2500 + spread, 400 + spread, true, solve, 1));
      samples.add(new StepSample("pair_other_br", 2500 + spread, 400 + spread, true, solve, 3));
    }

    List<StepFigure> groups = F2lGroups.of(samples, FLOOR);

    Assert.assertEquals(12, figure(groups, F2lGroups.FIRST).getCount());
    Assert.assertEquals(36, figure(groups, F2lGroups.LATER).getCount());
    Assert.assertEquals(2055, figure(groups, F2lGroups.FIRST).getRecognitionMs().longValue());
    Assert.assertEquals(455, figure(groups, F2lGroups.LATER).getRecognitionMs().longValue());
  }

  /** A free pair had no pause of its own, so the first pair is the first one worked on. */
  @Test
  public void testAPairAfterASkipIsTheFirstPair() {
    List<StepSample> samples = new ArrayList<StepSample>();
    for (int solve = 0; solve < 12; solve++) {
      samples.add(new StepSample("pair_skip", 0, 0, true, solve, 0));
      samples.add(new StepSample("pair_27_rf", 4000 + solve, 2000, true, solve, 1));
      samples.add(new StepSample("pair_3_fl", 2500 + solve, 400, true, solve, 2));
    }

    List<StepFigure> groups = F2lGroups.of(samples, FLOOR);

    Assert.assertEquals(2000, figure(groups, F2lGroups.FIRST).getRecognitionMs().longValue());
    Assert.assertEquals(400, figure(groups, F2lGroups.LATER).getRecognitionMs().longValue());
  }

  /** A stuck pair is never a solve's last, so the basic ones are also sent without the last. */
  @Test
  public void testTheBasicPairsBeforeTheLastRideBesideTheComparison() {
    List<StepSample> samples = new ArrayList<StepSample>();
    for (int solve = 0; solve < 12; solve++) {
      samples.add(new StepSample("pair_a12_rf", 5000 + solve, 1500, true, solve, 0));
      samples.add(new StepSample("pair_27_fl", 3000 + solve * 10, 800, true, solve, 1));
      boolean skipped = solve % 2 == 0; // its last slot already in, the pair before is the last
      samples.add(new StepSample("pair_3_br", 3000 + solve * 10, 800, true, solve, 2));
      samples.add(skipped ? new StepSample("pair_skip", 0, 0, true, solve, 3)
          : new StepSample("pair_4_bl", 2000 + solve, 500, true, solve, 3));
    }

    List<StepFigure> groups = F2lGroups.of(samples, FLOOR);

    StepFigure early = figure(groups, F2lGroups.BASIC_EARLY);
    Assert.assertEquals(18, early.getCount());
    Assert.assertEquals(3057, early.getMeanMs(), 5);
    Assert.assertNull(early.getShare());
  }

  @Test
  public void testTheBasicPairsBeforeTheLastAreNotSentWithoutTheComparison() {
    List<StepSample> samples = new ArrayList<StepSample>();
    for (int solve = 0; solve < 30; solve++) {
      samples.add(new StepSample("pair_27_fl", 3200, 800, true, solve, 0));
      samples.add(new StepSample("pair_4_bl", 2000, 500, true, solve, 1));
    }

    Assert.assertNull(figure(F2lGroups.of(samples, FLOOR), F2lGroups.BASIC_EARLY));
  }

  @Test
  public void testAPairWithNoPlaceIsNotGuessedAt() {
    List<StepSample> samples = new ArrayList<StepSample>();
    for (int solve = 0; solve < 20; solve++) {
      samples.add(new StepSample("pair_27_rf", 4000 + solve, 2000, true, solve));
    }

    Assert.assertEquals(Collections.<StepFigure>emptyList(), F2lGroups.of(samples, FLOOR));
  }

  /** Occurrences of one pair code around a mean, one per solve, each the solve's first pair. */
  private static List<StepSample> pairs(String code, int count, long meanMs, long recognitionMs) {
    List<StepSample> samples = new ArrayList<StepSample>();
    for (int i = 0; i < count; i++) {
      long offset = (i % 2 == 0 ? 1 : -1) * (i + 1) * 5L;
      samples.add(new StepSample(code, meanMs == 0 ? 0 : meanMs + offset, recognitionMs, true, i,
          0));
    }
    return samples;
  }

  private static StepFigure figure(List<StepFigure> figures, String code) {
    for (StepFigure figure : figures) {
      if (figure.getCode().equals(code)) {
        return figure;
      }
    }
    return null;
  }
}
