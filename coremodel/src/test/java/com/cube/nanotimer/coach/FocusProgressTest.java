package com.cube.nanotimer.coach;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.cube.nanotimer.drill.DrillSpec;
import com.cube.nanotimer.session.CaseKnowledge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(JUnit4.class)
public class FocusProgressTest {

  @Test
  public void testAFigureReadsTheCitedBeforeAgainstTheSolvesSince() {
    FocusProgress stalling = progress(30, 4000).get(0);

    Assert.assertEquals(FocusProgress.Kind.FIGURE, stalling.getKind());
    Assert.assertEquals(FocusProgress.Unit.MS, stalling.getUnit());
    Assert.assertEquals(2044, stalling.getBefore(), 1e-9); // cited, not the step layer's 1500
    Assert.assertEquals(900, stalling.getSince(), 1e-9);
    Assert.assertEquals(30, stalling.getOccurrences());
    Assert.assertEquals(FocusProgress.Change.BETTER, stalling.getChange());
  }

  /** No 25-solve floor: a figure shows once it has ten occurrences since the plan. */
  @Test
  public void testAFigureShowsFromTenOccurrences() {
    Assert.assertEquals(FocusProgress.Kind.FIGURE, progress(10, 4000).get(0).getKind());

    FocusProgress waiting = progress(6, 4000).get(0);
    Assert.assertEquals(FocusProgress.Kind.WAITING, waiting.getKind());
    Assert.assertEquals(4, waiting.getSolvesNeeded()); // one cross a solve when the plan was written
  }

  @Test
  public void testACaseFocusShowsTheDrillRepsSince() {
    FocusProgress slowCase = progress(30, 4000).get(1);

    Assert.assertEquals(FocusProgress.Kind.REPS, slowCase.getKind());
    Assert.assertEquals(12, slowCase.getReps());
    Assert.assertEquals(FocusProgress.Change.HELD, slowCase.getChange());
  }

  @Test
  public void testASmallMoveHolds() {
    FocusProgress held = progress(30, 3800).get(2); // looking per F2L, 4000 when written

    Assert.assertEquals(FocusProgress.Kind.FIGURE, held.getKind());
    Assert.assertEquals(4000, held.getBefore(), 1e-9);
    Assert.assertEquals(3800, held.getSince(), 1e-9);
    Assert.assertEquals(FocusProgress.Change.HELD, held.getChange());
    Assert.assertEquals(FocusProgress.Change.WORSE, progress(30, 4600).get(2).getChange());
  }

  @Test
  public void testAStepShareIsReadOnTheMethodBaseline() {
    FocusProgress share = progress(30, 4000).get(3);

    Assert.assertEquals(FocusProgress.Kind.FIGURE, share.getKind());
    Assert.assertEquals(FocusProgress.Unit.SHARE, share.getUnit());
    Assert.assertTrue(share.getBefore() > 0 && share.getBefore() < 1);
  }

  @Test
  public void testAFocusWithNoFigureAndNoDrillShowsNothing() {
    Assert.assertEquals(FocusProgress.Kind.NONE, progress(30, 4000).get(4).getKind());
  }

  @Test
  public void testAPlanForAnotherMethodShowsNothing() {
    StoredCoachPlan stored = new StoredCoachPlan(plan(), payload(40, 4000), 1000);
    PreviousPlan none = CoachPayloadBuilder.sincePlan(stored, "roux", 30, times(30),
        samples(30, 4000), turnings(30), null);

    Assert.assertNull(none);
    for (FocusProgress one : FocusProgress.of(stored, none)) {
      Assert.assertEquals(FocusProgress.Kind.NONE, one.getKind());
    }
  }

  @Test
  public void testTheGainPutsTheBiggestMoveFirst() {
    List<FocusProgress> all = progress(30, 3000);

    Assert.assertTrue(all.get(0).getGain() > all.get(2).getGain()); // 56% better beats 25%
    Assert.assertTrue(all.get(2).getGain() > 0);
    Assert.assertEquals(0, all.get(1).getGain(), 1e-9);
  }

  private static List<FocusProgress> progress(int solvesSince, long f2lLooking) {
    StoredCoachPlan stored = new StoredCoachPlan(plan(), payload(40, 4000), 1000);
    PreviousPlan sincePlan = CoachPayloadBuilder.sincePlan(stored, "cfop", solvesSince,
        times(solvesSince), samples(solvesSince, f2lLooking), turnings(solvesSince),
        Arrays.asList(null, Integer.valueOf(12), null, null, null));
    return FocusProgress.of(stored, sincePlan);
  }

  private static CoachPlan plan() {
    String caseDrill = new DrillSpec("coach-slow_case-pll", DrillSpec.Type.CASE_RECOGNITION,
        DrillSpec.Delivery.VIRTUAL, Arrays.asList("pll_rb"), DrillSpec.Selection.WEIGHTED, 40, 0,
        null).toJson();
    List<Evidence> none = Collections.<Evidence>emptyList();
    return new CoachPlan(CoachPlan.VERSION, CoachPlan.Source.COACH, null, Arrays.asList(
        new FocusArea(FocusArea.Reason.STALLING, Arrays.asList("cross"),
            Arrays.asList(new Evidence("turning.cross.stall_ms", 2044)), null, null),
        new FocusArea(FocusArea.Reason.SLOW_CASE, Arrays.asList("pll_rb"), none, null, caseDrill),
        new FocusArea(FocusArea.Reason.RECOGNITION_HEAVY, Arrays.asList("f2l"), none, null, null),
        new FocusArea(FocusArea.Reason.SLOW_STEP, Arrays.asList("f2l"), none, null, null),
        new FocusArea(FocusArea.Reason.LONG_PAIR, Collections.<String>emptyList(), none, null,
            null)));
  }

  private static CoachPayload payload(int solves, long f2lLooking) {
    return CoachPayloadBuilder.build("3x3", "cfop", times(solves), samples(solves, f2lLooking),
        samples(solves, f2lLooking), solves, Collections.<StepSample>emptyList(), 0,
        Collections.<CaseKnowledge>emptyList(), Collections.<PairExecution>emptyList(), 0,
        turnings(solves, 1500), solves, null);
  }

  private static List<Long> times(int solves) {
    List<Long> times = new ArrayList<Long>();
    for (int i = 0; i < solves; i++) {
      times.add(Long.valueOf(20000 + i * 100));
    }
    return times;
  }

  private static List<StepSample> samples(int solves, long f2lLooking) {
    List<StepSample> samples = new ArrayList<StepSample>();
    for (int solve = 0; solve < solves; solve++) {
      long spread = (solve % 2 == 0 ? 1 : -1) * 20L;
      samples.add(new StepSample("cross", 3000 + spread, 0, false, solve));
      samples.add(new StepSample("f2l", 12000 + spread, f2lLooking, false, solve));
      samples.add(new StepSample("oll", 3000 + spread, 800, false, solve));
      samples.add(new StepSample("pll", 3000 + spread, 800, false, solve));
    }
    return samples;
  }

  private static List<StepTurning> turnings(int solves) {
    return turnings(solves, 900);
  }

  private static List<StepTurning> turnings(int solves, long stallMs) {
    List<StepTurning> turnings = new ArrayList<StepTurning>();
    for (int solve = 0; solve < solves; solve++) {
      turnings.add(new StepTurning(solve, "cross", 3000, 8, 1, stallMs, stallMs, true));
    }
    return turnings;
  }
}
