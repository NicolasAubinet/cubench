package com.cube.nanotimer.coach;

import com.cube.nanotimer.drill.DrillSpec;
import com.cube.nanotimer.step.LastLayerAlgorithms;
import com.cube.nanotimer.vo.StepStats;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The third check a plan is put through, and the two ways it says no.
 *
 * <p>{@link CoachPlan#uncited} and {@link CoachPlan#unknownCodes} both read what a card says. This
 * one reads what its button would do, which is the failure neither of them can see: a card citing
 * only real figures and naming only a real case, whose drill asks for something no scrambler has
 * heard of.
 */
@RunWith(JUnit4.class)
public class CoachPlanTest {

  @Test
  public void testADrillOfACaseThePayloadSentAndTheAppCanDealIsKept() {
    CoachPlan plan = planDrilling("pll_gb");

    Assert.assertEquals(Collections.<String>emptyList(), plan.undealableDrills(payload()));
  }

  /** The half the server already had: a coach may only speak of what it was told. */
  @Test
  public void testADrillOfACaseThePayloadNeverSentIsCaught() {
    CoachPlan plan = planDrilling("pll_ja");

    Assert.assertEquals(Collections.singletonList("pll_ja"), plan.undealableDrills(payload()));
  }

  /**
   * The half it did not have, and the one that needs the algorithm table rather than the payload. A
   * Roux solver's CMLL is sent as a part, so {@link CoachPayload#holds} answers yes to {@code cmll}
   * and only the table knows there is no scramble behind it.
   */
  @Test
  public void testADrillOfAPartThePayloadHoldsIsStillCaught() {
    CoachPayload payload = payload();
    CoachPlan plan = planDrilling("pair");

    Assert.assertTrue("the payload sent it, as a part", payload.holds("pair"));
    Assert.assertEquals(Collections.singletonList("pair"), plan.undealableDrills(payload));
  }

  /**
   * A family is not a case. {@code oll} is a family the app deals cases of and the payload certainly
   * holds it, so both of the other conditions pass and the drill is still a button over nothing.
   */
  @Test
  public void testADrillOfAFamilyRatherThanACaseIsCaught() {
    CoachPayload payload = payload();
    CoachPlan plan = planDrilling("oll");

    Assert.assertTrue(payload.holds("oll"));
    Assert.assertTrue(CoachPayloadBuilder.CASE_FAMILIES.contains("oll"));
    Assert.assertEquals(Collections.singletonList("oll"), plan.undealableDrills(payload));
  }

  /** A card with no drill has no button to be wrong about. */
  @Test
  public void testACardWithNoDrillIsNothingToCheck() {
    CoachPlan plan = new CoachPlan(CoachPlan.VERSION, CoachPlan.Source.COACH, null,
        Collections.singletonList(new FocusArea(FocusArea.Reason.RECOGNITION_HEAVY,
            Collections.singletonList("pll"), Collections.<Evidence>emptyList(), null, null)));

    Assert.assertEquals(Collections.<String>emptyList(), plan.undealableDrills(payload()));
  }

  /** A drill that will not parse names no cases, so the card is named instead of nothing. */
  @Test
  public void testADrillThatWillNotParseIsCaughtUnderItsReasonCode() {
    CoachPlan plan = new CoachPlan(CoachPlan.VERSION, CoachPlan.Source.COACH, null,
        Collections.singletonList(new FocusArea(FocusArea.Reason.SLOW_CASE,
            Collections.singletonList("pll_gb"), Collections.<Evidence>emptyList(), null,
            "{not json")));

    Assert.assertEquals(Collections.singletonList("slow_case"), plan.undealableDrills(payload()));
  }

  /** Every case the app can deal is a case this check accepts, which is the two ends meeting. */
  @Test
  public void testEveryCaseTheAlgorithmTableHoldsIsDealable() {
    List<String> codes = LastLayerAlgorithms.caseCodes();
    Assert.assertEquals(57 + 21, codes.size());

    for (String code : codes) {
      Assert.assertTrue(code, LastLayerAlgorithms.dealsCase(code));
    }
    for (String code : Arrays.asList("oll", "pll", "pair", "pair_rf", "cmll_orient", "lse_eo",
        "ollalg_45", "alg_gb", "oll_99", "pll_zz", null)) {
      Assert.assertFalse(String.valueOf(code), LastLayerAlgorithms.dealsCase(code));
    }
  }

  /** A plan of one card whose drill asks for exactly these cases. */
  private static CoachPlan planDrilling(String... cases) {
    List<String> codes = Arrays.asList(cases);
    DrillSpec drill = new DrillSpec("test", DrillSpec.Type.CASE_EXECUTION,
        DrillSpec.Delivery.VIRTUAL, codes, DrillSpec.Selection.ROUND_ROBIN, 5, 2000, "drill");
    return new CoachPlan(CoachPlan.VERSION, CoachPlan.Source.COACH, null,
        Collections.singletonList(new FocusArea(FocusArea.Reason.SLOW_CASE, codes,
            Collections.<Evidence>emptyList(), null, drill.toJson())));
  }

  /**
   * A CFOP solver who has one perm and one OLL worth quoting, and an F2L pair recorded as a part,
   * which is the shape that tells the three conditions apart.
   */
  private static CoachPayload payload() {
    List<StepFigure> families = Arrays.asList(family("cross", 100, 2400, 0),
        family("f2l", 100, 10000, 3400), family("oll", 100, 3300, 1300),
        family("pll", 100, 4300, 1700));
    List<StepFigure> parts = Collections.singletonList(family("pair", 400, 3700, 1200));
    List<StepFigure> cases = Arrays.asList(stepCase("pll_gb", 9), stepCase("oll_33", 8));
    return new CoachPayload(CoachPayload.VERSION, "3x3", "cfop", 100, 500, 10, Integer.valueOf(3),
        family("solve", 100, 20000, 0), families, parts, cases,
        Collections.<StepFigure>emptyList(), Collections.<CaseComparison>emptyList(),
        Collections.<String>emptyList(), Collections.<String>emptyList(),
        Collections.<String>emptyList());
  }

  private static StepFigure stepCase(String code, int count) {
    return StepFigure.stepCase(stats(code, count, 4000, 1500), true, 0, 0, 3500);
  }

  private static StepFigure family(String code, int count, long meanMs, long recognitionMs) {
    return StepFigure.family(stats(code, count, meanMs, recognitionMs), recognitionMs > 0, 0, null);
  }

  private static StepStats stats(String code, int count, long meanMs, long recognitionMs) {
    return new StepStats(code, count, meanMs * count, recognitionMs * count, meanMs,
        (double) meanMs * meanMs * count);
  }
}
