package com.cube.nanotimer.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.coach.StepBaseline;
import com.cube.nanotimer.session.CaseKnowledge;
import com.cube.nanotimer.session.MethodStatistics;
import com.cube.nanotimer.smartcube.step.LastLayerScrambles;
import com.cube.nanotimer.vo.StepStats;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

/**
 * That the example adds up. Every figure the hub prints off it is derived from the tables in
 * {@link AnalysisSample}, so a number edited there without its neighbours produces a screen that
 * contradicts itself in a way no reader could be expected to forgive: a sample is only worth
 * anything while it looks exactly like a real reading.
 */
public class AnalysisSampleTest {

  private static final MethodStatistics STATISTICS = AnalysisSample.statistics();

  private static StepStats step(String code) {
    StepStats step = STATISTICS.getFamily(code);
    assertTrue("no step " + code, step != null);
    return step;
  }

  /** The hero number is the steps put together, which is the one figure everything else hangs off. */
  @Test
  public void stepMeansSumToTheHeroMean() {
    long total = 0;
    for (StepStats family : STATISTICS.getFamilies()) {
      total += family.getMeanMs();
    }
    assertEquals(14800, total);
  }

  /** And so is the best, which is the sum of the step bests rather than any one solve. */
  @Test
  public void stepBestsSumToTheHeroBest() {
    long best = 0;
    for (StepStats family : STATISTICS.getFamilies()) {
      best += family.getBestMs();
    }
    assertEquals(9940, best);
  }

  /** The four steps, in the order CFOP solves them, which is what the palette keys on. */
  @Test
  public void readsAsAWholeCfopSolve() {
    List<String> codes = new ArrayList<String>();
    for (StepStats family : STATISTICS.getFamilies()) {
      codes.add(family.getCode());
    }
    assertEquals("[cross, f2l, oll, pll]", codes.toString());
    assertEquals(AnalysisSample.SOLVES, STATISTICS.getSolveCount());
  }

  /** The shares the legend prints, which have to be a hundred and not ninety-nine. */
  @Test
  public void sharesRoundToAHundred() {
    assertEquals(100, percent("cross") + percent("f2l") + percent("oll") + percent("pll"));
    assertEquals(12, percent("cross"));
    assertEquals(51, percent("f2l"));
    assertEquals(17, percent("oll"));
    assertEquals(20, percent("pll"));
  }

  /**
   * The delta card's own honest limit, printed under it: these are shares of one solve, so if one
   * step is over another has to be under. Off a sample that does not cancel, the card is nonsense.
   */
  @Test
  public void deltasAgainstTheBaselineCancel() {
    List<String> codes = new ArrayList<String>();
    long measured = 0;
    for (StepStats family : STATISTICS.getFamilies()) {
      codes.add(family.getCode());
      measured += family.getMeanMs();
    }
    Map<String, Double> expected = StepBaseline.shares("cfop", codes);
    assertEquals(4, expected.size());
    double sum = 0;
    for (StepStats family : STATISTICS.getFamilies()) {
      sum += (double) family.getMeanMs() / measured - expected.get(family.getCode()).doubleValue();
    }
    assertEquals(0, sum, 1e-9);
  }

  /** A step's figures are its cases put together, so the two tabs cannot disagree about OLL. */
  @Test
  public void everyCaseCountsIntoItsStep() {
    assertCasesMakeTheStep("oll", 42);
    assertCasesMakeTheStep("pll", 19);
  }

  private void assertCasesMakeTheStep(String family, int expectedCases) {
    List<StepStats> cases = STATISTICS.getCases(family);
    assertEquals(expectedCases, cases.size());
    int count = 0;
    long total = 0;
    long best = Long.MAX_VALUE;
    for (StepStats stepCase : cases) {
      count += stepCase.getCount();
      total += stepCase.getMeanMs() * stepCase.getCount();
      best = Math.min(best, stepCase.getBestMs());
    }
    assertEquals(AnalysisSample.SOLVES, count);
    assertEquals(step(family).getMeanMs() * count, total);
    assertEquals(step(family).getBestMs(), best);
  }

  /**
   * Most of the example's cases came up more than once, so its spread column reads as a table of
   * figures rather than a column of N/A: a case seen once has no spread and says so.
   */
  @Test
  public void mostCasesHaveASpread() {
    assertTrue("oll", withASpread("oll") * 2 > STATISTICS.getCases("oll").size());
    assertTrue("pll", withASpread("pll") * 2 > STATISTICS.getCases("pll").size());
  }

  private int withASpread(String family) {
    int seenTwice = 0;
    for (StepStats stepCase : STATISTICS.getCases(family)) {
      if (stepCase.getCount() >= 2) {
        seenTwice++;
      }
    }
    return seenTwice;
  }

  /**
   * The ring counts the closed set and not the window, so a standing is owed for every case the app
   * draws. A case left out is one the ring puts under "not yet seen", which of Ana would be false.
   */
  @Test
  public void everyDrawnCaseHasAStanding() {
    Set<String> covered = new HashSet<String>();
    for (CaseKnowledge caseKnown : AnalysisSample.knowledge()) {
      covered.add(caseKnown.getCode());
    }
    List<String> silent = new ArrayList<String>();
    for (String code : LastLayerScrambles.cases()) {
      if (!covered.contains(code)) {
        silent.add(code);
      }
    }
    assertEquals("[oll_56, oll_57]", silent.toString());
  }

  /** What the ring says of her, which the groups under the table have to add up to. */
  @Test
  public void theRingReadsAsASolverWhoKnowsNearlyAllOfIt() {
    assertEquals(46, standing("oll", CaseKnowledge.Status.KNOWN));
    assertEquals(5, standing("oll", CaseKnowledge.Status.NEEDS_REVIEW));
    assertEquals(4, standing("oll", CaseKnowledge.Status.TO_LEARN));
    assertEquals(20, standing("pll", CaseKnowledge.Status.KNOWN));
    assertEquals(1, standing("pll", CaseKnowledge.Status.NEEDS_REVIEW));
    assertEquals(0, standing("pll", CaseKnowledge.Status.TO_LEARN));
  }

  private int standing(String family, CaseKnowledge.Status status) {
    int count = 0;
    for (CaseKnowledge caseKnown : AnalysisSample.knowledge()) {
      if (family.equals(caseKnown.getCaseSet()) && caseKnown.getStatus() == status) {
        count++;
      }
    }
    return count;
  }

  /** Nobody thinks in a way she cannot: every step spends some of itself looking, except the cross. */
  @Test
  public void everyStepSplitsIntoLookingAndTurning() {
    assertEquals(0, step("cross").getMeanRecognitionMs());
    for (StepStats family : STATISTICS.getFamilies()) {
      assertTrue(family.getCode(), family.getMeanExecutionMs() > 0);
      assertTrue(family.getCode(), family.getBestMs() < family.getMeanMs());
      assertTrue(family.getCode(), family.getStdDevMs() > 0);
      assertTrue(family.getCode(), family.getStdDevMs() < family.getMeanMs());
    }
  }

  private int percent(String code) {
    long total = 0;
    for (StepStats family : STATISTICS.getFamilies()) {
      total += family.getMeanMs();
    }
    return (int) Math.round((double) step(code).getMeanMs() / total * 100);
  }
}
