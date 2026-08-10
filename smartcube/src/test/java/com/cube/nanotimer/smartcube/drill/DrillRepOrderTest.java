package com.cube.nanotimer.smartcube.drill;

import static org.junit.Assert.assertEquals;

import com.cube.nanotimer.smartcube.model.CubeMove;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/** How a finished drill's reps are ranked, and where the ones nobody finished go. */
public class DrillRepOrderTest {

  @Test
  public void ranksOnTheKeyItIsAskedFor() {
    List<DrillRep> reps = list(rep("pll_ga", 3000, 1000), rep("pll_t", 1000, 4000));

    DrillRepOrder.sort(reps, DrillRepOrder.Key.RECOGNITION, true);
    assertEquals("pll_ga", reps.get(0).getCaseCode());

    DrillRepOrder.sort(reps, DrillRepOrder.Key.EXECUTION, true);
    assertEquals("pll_t", reps.get(0).getCaseCode());

    DrillRepOrder.sort(reps, DrillRepOrder.Key.TOTAL, false);
    assertEquals("pll_ga", reps.get(0).getCaseCode());
  }

  /** It has no time to compare, so it is neither the fastest nor the slowest of anything. */
  @Test
  public void skippedRepsStayAtTheEndBothWaysRound() {
    List<DrillRep> reps = list(rep("pll_ga", 1000, 1000), skipped("pll_t"),
        rep("pll_aa", 2000, 2000));

    DrillRepOrder.sort(reps, DrillRepOrder.Key.TOTAL, true);
    assertEquals("pll_t", reps.get(2).getCaseCode());

    DrillRepOrder.sort(reps, DrillRepOrder.Key.TOTAL, false);
    assertEquals("pll_t", reps.get(2).getCaseCode());
  }

  @Test
  public void skippedRepsKeepTheOrderTheyWereDealtIn() {
    List<DrillRep> reps = list(skipped("pll_ga"), rep("pll_aa", 1000, 1000), skipped("pll_t"));

    DrillRepOrder.sort(reps, DrillRepOrder.Key.TOTAL, true);
    assertEquals(Arrays.asList("pll_aa", "pll_ga", "pll_t"), codes(reps));
  }

  @Test
  public void repsThatCostTheSameStayInTheOrderTheyWereDealt() {
    List<DrillRep> reps = list(rep("pll_ga", 1000, 1000), rep("pll_t", 1000, 1000),
        rep("pll_aa", 1000, 1000));

    DrillRepOrder.sort(reps, DrillRepOrder.Key.TOTAL, true);
    assertEquals(Arrays.asList("pll_ga", "pll_t", "pll_aa"), codes(reps));
  }

  @Test
  public void eachRepIsRankedOnItsOwnEvenWhenTheCaseComesUpTwice() {
    List<DrillRep> reps = list(rep("pll_ga", 1000, 1000), rep("pll_t", 500, 500),
        rep("pll_ga", 3000, 3000));

    DrillRepOrder.sort(reps, DrillRepOrder.Key.TOTAL, true);
    assertEquals(Arrays.asList("pll_ga", "pll_ga", "pll_t"), codes(reps));
    assertEquals(6000, reps.get(0).getTotalMs());
    assertEquals(2000, reps.get(1).getTotalMs());
  }

  private static List<DrillRep> list(DrillRep... reps) {
    return new ArrayList<DrillRep>(Arrays.asList(reps));
  }

  private static List<String> codes(List<DrillRep> reps) {
    List<String> codes = new ArrayList<String>();
    for (DrillRep rep : reps) {
      codes.add(rep.getCaseCode());
    }
    return codes;
  }

  private static DrillRep rep(String caseCode, long recognitionMs, long executionMs) {
    return new DrillRep(caseCode, "", Collections.<CubeMove>emptyList(), 0, recognitionMs,
        executionMs, 10, 0, false, false);
  }

  private static DrillRep skipped(String caseCode) {
    return new DrillRep(caseCode, "", Collections.<CubeMove>emptyList(), 0, 9000, 0, 0, 0, false,
        true);
  }
}
