package com.cube.nanotimer.smartcube.drill;

import com.cube.nanotimer.drill.DrillSpec;
import com.cube.nanotimer.smartcube.model.CubeMove;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DrillRunsTest {

  private static final List<String> CASES = Arrays.asList("pll_t", "pll_ua", "pll_ub", "pll_ja",
      "pll_jb", "pll_ra", "pll_rb", "pll_v");

  /** Two wins clear a case; 1000 ms beats every target. */
  private static DrillSpec spec(String id, long target) {
    Map<String, Long> targets = new LinkedHashMap<String, Long>();
    for (String code : CASES) {
      targets.put(code, target);
    }
    return new DrillSpec(id, DrillSpec.Type.CASE_EXECUTION, DrillSpec.Delivery.VIRTUAL, CASES,
        DrillSpec.Selection.WEIGHTED, 40, 0, targets, 2, 2, null).withWorkingSet(5);
  }

  private static final DrillSpec SPEC = spec("coach-stalling-pll", 2000);

  @Test
  public void onlyACoachSpeedDrillWithAWorkingSetKeepsItsRuns() {
    assertTrue(DrillRuns.applies(SPEC));
    assertFalse(DrillRuns.applies(spec("manual", 2000)));
    assertFalse(DrillRuns.applies(SPEC.withWorkingSet(0)));
  }

  @Test
  public void aFirstRunTakesTheCasesInTheSpecsOrder() {
    assertEquals(CASES, DrillRuns.of(SPEC, null).order(CASES));
  }

  /** T took four reps, Ja three, Ua two; Ub was dealt and missed; the rest were never dealt. */
  @Test
  public void aRunOpensOnTwoReviewsHardestFirstThenTheMissedThenNewOnes() {
    DrillRuns runs = DrillRuns.of(SPEC, null).after(SPEC, Arrays.asList(
        rep("pll_t", 3000), rep("pll_t", 3000), rep("pll_t", 1000), rep("pll_t", 1000),
        rep("pll_ua", 1000), rep("pll_ua", 1000), rep("pll_ub", 3000),
        rep("pll_ja", 1000), rep("pll_ja", 3000), rep("pll_ja", 1000)));

    List<String> order = runs.order(CASES);

    assertEquals(Arrays.asList("pll_t", "pll_ja", "pll_ub", "pll_jb", "pll_ra"),
        order.subList(0, 5));
    assertEquals(new HashSet<String>(CASES), new HashSet<String>(order));
    assertEquals("pll_ua", order.get(order.size() - 1));
  }

  @Test
  public void aReviewMissedGoesBackAmongTheMissed() {
    DrillRuns first = DrillRuns.of(SPEC, null).after(SPEC,
        Arrays.asList(rep("pll_t", 1000), rep("pll_t", 1000)));
    DrillRuns second = first.after(SPEC, Arrays.asList(rep("pll_t", 3000)));

    assertEquals("pll_t", second.order(CASES).get(0));
    assertEquals(CASES, second.order(CASES));
  }

  @Test
  public void onceEveryCaseIsBeatenTheyComeInARandomOrder() {
    List<DrillRep> reps = new ArrayList<DrillRep>();
    for (String code : CASES) {
      reps.add(rep(code, 1000));
      reps.add(rep(code, 1000));
    }
    DrillRuns runs = DrillRuns.of(SPEC, null).after(SPEC, reps);

    List<String> one = runs.order(CASES);
    List<String> later = runs.after(SPEC, Arrays.asList(rep("pll_t", 1000), rep("pll_t", 1000),
        rep("pll_t", 1000))).order(CASES);

    assertEquals(new HashSet<String>(CASES), new HashSet<String>(one));
    assertEquals(one, runs.order(CASES)); // the card shows what the run opens on
    assertFalse(one.equals(later));
  }

  @Test
  public void runsAreSavedAndReadBackForTheSameSpecOnly() {
    DrillRuns runs = DrillRuns.of(SPEC, null).after(SPEC,
        Arrays.asList(rep("pll_ub", 3000), rep("pll_t", 1000), rep("pll_t", 1000)));
    String text = runs.toText();

    assertEquals(runs.order(CASES),
        DrillRuns.of(SPEC, text).order(CASES));
    DrillSpec newPlan = spec("coach-stalling-pll", 2500);
    assertEquals(CASES, DrillRuns.of(newPlan, text).order(CASES));
    assertEquals(CASES, DrillRuns.of(SPEC, "garbage").order(CASES));
  }

  @Test
  public void aPracticeRepDealsNothing() {
    DrillRep practice = new DrillRep("pll_v", "", Collections.<CubeMove>emptyList(), 0, 300, 700,
        12, 0, false, false, true);
    DrillRuns runs = DrillRuns.of(SPEC, null).after(SPEC, Collections.singletonList(practice));

    assertEquals(CASES, runs.order(CASES));
  }

  private static DrillRep rep(String code, long totalMs) {
    return new DrillRep(code, "", Collections.<CubeMove>emptyList(), 0, totalMs / 2,
        totalMs - totalMs / 2, 12, 0, false, false);
  }
}
