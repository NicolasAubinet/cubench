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
public class PreviousPlanTest {

  private static final int SINCE = 30;

  @Test
  public void testAFirstPlanCarriesNoPreviousPlan() {
    Assert.assertNull(previous(null, "cfop", SINCE));
    Assert.assertNull(payload(null).getPrevious());
    Assert.assertNull(payload(null).value("previous.solves_since"));
    Assert.assertFalse(payload(null).toJson().contains("previous"));
  }

  @Test
  public void testTooFewSolvesSinceSendsNothing() {
    StoredCoachPlan last = stored(payload(null));

    Assert.assertNull(previous(last, "cfop", CoachPayloadBuilder.SINCE_FLOOR - 1));
    Assert.assertNotNull(previous(last, "cfop", CoachPayloadBuilder.SINCE_FLOOR));
  }

  /** A plan written for another method is a different activity, not a before. */
  @Test
  public void testAPlanForAnotherMethodIsNotSent() {
    Assert.assertNull(previous(stored(payload(null)), "roux", SINCE));
  }

  @Test
  public void testTheLastPlanRidesWithoutItsTextOrItsDrillSpec() {
    String json = payload(previous(stored(payload(null)), "cfop", SINCE)).toJson();

    Assert.assertFalse(json, json.contains("Plan the whole cross"));
    Assert.assertFalse(json, json.contains("coach-stalling-cross"));
    Assert.assertFalse(json, json.contains("case_targets_ms"));
  }

  @Test
  public void testWhatTheLastPlanCitedIsTheBeforeAndTheSolvesSinceTheAfter() {
    CoachPayload payload =
        CoachPayload.parse(payload(previous(stored(payload(null)), "cfop", SINCE)).toJson());

    Assert.assertEquals(SINCE, payload.value("previous.solves_since"), 1e-9);
    Assert.assertEquals(SINCE, payload.value("previous.since.windows.families"), 1e-9);
    Assert.assertEquals(40, payload.value("previous.was.windows.families"), 1e-9);
    // What the card cited wins over the step layer the plan was written from, which says 900.
    Assert.assertEquals(2044, payload.value("previous.was.turning.cross.stall_ms"), 1e-9);
    Assert.assertEquals(900, payload.value("previous.since.turning.cross.stall_ms"), 1e-9);
    // A step no card cited still has its before, off the last payload's own step layer.
    Assert.assertEquals(payload.value("families.f2l.mean_ms"),
        payload.value("previous.was.families.f2l.mean_ms"));
    Assert.assertNotNull(payload.value("previous.since.families.f2l.mean_ms"));
    Assert.assertNotNull(payload.value("previous.since.solve.mean_ms"));
    Assert.assertEquals(6046, payload.value("previous.was.cases.pll_rb.mean_ms"), 1e-9);
    Assert.assertNull(payload.value("previous.was.cases.pll_t.mean_ms")); // nothing cited it
    Assert.assertNull(payload.value("previous.since.cases.pll_rb.mean_ms")); // steps only
  }

  @Test
  public void testACardSaysWhatWasDrilledSince() {
    CoachPayload payload = payload(previous(stored(payload(null)), "cfop", SINCE));
    PreviousPlan.Card cross = payload.getPrevious().getCards().get(0);
    PreviousPlan.Card recognition = payload.getPrevious().getCards().get(1);

    Assert.assertEquals("cross", cross.getDrillType());
    Assert.assertEquals(12, payload.value("previous.cards.1.drill_reps"), 1e-9);
    Assert.assertEquals(Arrays.asList("pll_rb", "pll_t"), recognition.getDrillCases());
    Assert.assertEquals(0, payload.value("previous.cards.2.drill_reps"), 1e-9);
    Assert.assertNull(payload.getPrevious().getCards().get(2).getDrillType());
    Assert.assertNull(payload.value("previous.cards.3.drill_reps")); // no drill, not no reps
    Assert.assertNull(payload.value("previous.cards.4.run"));
  }

  /** A card the plan before also carried is in its second plan, and a new one in its first. */
  @Test
  public void testACardCountsThePlansItHasRunFor() {
    CoachPayload second = payload(previous(stored(payload(null)), "cfop", SINCE));
    Assert.assertEquals(1, second.value("previous.cards.1.run"), 1e-9);

    // The plan after carried the cross card again and dropped the other two for a new one.
    List<FocusArea> focus = new ArrayList<FocusArea>(plan().getFocus().subList(0, 1));
    focus.add(new FocusArea(FocusArea.Reason.SLOW_STEP, Arrays.asList("f2l"),
        Collections.<Evidence>emptyList(), null, null));
    StoredCoachPlan next = new StoredCoachPlan(
        new CoachPlan(CoachPlan.VERSION, CoachPlan.Source.COACH, null, focus), second, 2000);
    CoachPayload third = CoachPayload.parse(payload(previous(next, "cfop", SINCE)).toJson());

    Assert.assertEquals(2, third.value("previous.cards.1.run"), 1e-9);
    Assert.assertEquals(1, third.value("previous.cards.2.run"), 1e-9);
  }

  private static StoredCoachPlan stored(CoachPayload payload) {
    return new StoredCoachPlan(plan(), payload, 1000);
  }

  /** A stalling cross with the cross drill, a slow Rb with a recognition drill, and a card with none. */
  private static CoachPlan plan() {
    String crossDrill = DrillSpec.cross("coach-stalling-cross", "D", 10, 15000, null).toJson();
    String caseDrill = new DrillSpec("coach-slow_case-pll", DrillSpec.Type.CASE_RECOGNITION,
        DrillSpec.Delivery.VIRTUAL, Arrays.asList("pll_rb", "pll_t"),
        DrillSpec.Selection.WEIGHTED, 40, 0, null).toJson();
    return new CoachPlan(CoachPlan.VERSION, CoachPlan.Source.COACH, "Plan the whole cross.",
        Arrays.asList(
            new FocusArea(FocusArea.Reason.STALLING, Arrays.asList("cross"),
                Arrays.asList(new Evidence("turning.cross.stall_ms", 2044)),
                "Plan the whole cross.", crossDrill),
            new FocusArea(FocusArea.Reason.SLOW_CASE, Arrays.asList("pll_rb"),
                Arrays.asList(new Evidence("cases.pll_rb.mean_ms", 6046)), "Rb is slow.",
                caseDrill),
            new FocusArea(FocusArea.Reason.STUCK_PIECES, Arrays.asList("f2l_stuck"),
                Collections.<Evidence>emptyList(), "Stuck pieces.", null)));
  }

  /** The last plan against thirty solves since, each stopping once in the cross for 900 ms. */
  private static PreviousPlan previous(StoredCoachPlan last, String method, int solvesSince) {
    return CoachPayloadBuilder.previous(last, method, solvesSince, solveTimes(SINCE),
        samples(SINCE), turnings(SINCE), Arrays.asList(Integer.valueOf(12), Integer.valueOf(0),
            null));
  }

  private static CoachPayload payload(PreviousPlan previous) {
    return CoachPayloadBuilder.build("3x3", "cfop", solveTimes(40), samples(40), samples(40), 40,
        Collections.<StepSample>emptyList(), 0, Collections.<CaseKnowledge>emptyList(),
        Collections.<PairExecution>emptyList(), 0, turnings(40), 40, previous);
  }

  private static List<Long> solveTimes(int solves) {
    List<Long> times = new ArrayList<Long>();
    for (int i = 0; i < solves; i++) {
      times.add(Long.valueOf(20000 + i * 100));
    }
    return times;
  }

  private static List<StepSample> samples(int solves) {
    List<StepSample> samples = new ArrayList<StepSample>();
    for (int solve = 0; solve < solves; solve++) {
      long spread = (solve % 2 == 0 ? 1 : -1) * (solve + 1) * 20L;
      samples.add(new StepSample("cross", 3000 + spread, 0, false, solve));
      samples.add(new StepSample("f2l", 12000 + spread, 4000, false, solve));
    }
    return samples;
  }

  private static List<StepTurning> turnings(int solves) {
    List<StepTurning> turnings = new ArrayList<StepTurning>();
    for (int solve = 0; solve < solves; solve++) {
      turnings.add(new StepTurning(solve, "cross", 3000, 8, 1, 900, 900, true));
    }
    return turnings;
  }
}
