package com.cube.nanotimer.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.coach.StepBaseline;
import com.cube.nanotimer.session.CaseKnowledge;
import com.cube.nanotimer.session.MethodStatistics;
import com.cube.nanotimer.session.AlgorithmFigures;
import com.cube.nanotimer.smartcube.step.CmllCases;
import com.cube.nanotimer.smartcube.step.LastLayerScrambles;
import com.cube.nanotimer.vo.CubeMethod;
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

  /** A Roux type gets a Roux solver, about as fast, whose CMLL is its cases put together. */
  @Test
  public void rouxReadsAsAWholeRouxSolve() {
    MethodStatistics roux = AnalysisSample.statistics(CubeMethod.ROUX);
    List<String> codes = new ArrayList<String>();
    long total = 0;
    for (StepStats family : roux.getFamilies()) {
      codes.add(family.getCode());
      total += family.getMeanMs();
    }
    assertEquals("[fb, sb, cmll, lse]", codes.toString());
    assertEquals(14745, total);
    List<StepStats> cases = roux.getCases("cmll");
    assertEquals(36, cases.size());
    int count = 0;
    for (StepStats stepCase : cases) {
      assertTrue(stepCase.getCode(), CmllCases.isCase(stepCase.getCode()));
      count += stepCase.getCount();
    }
    assertEquals(AnalysisSample.SOLVES, count);
    assertEquals(36, AnalysisSample.oneAlgorithmCases(CubeMethod.ROUX).size());
  }

  /** Every CMLL has a standing but the two left silent, and only an unmet case is still to learn. */
  @Test
  public void rouxRingCoversTheCmlls() {
    assertEquals(40, AnalysisSample.knowledge(CubeMethod.ROUX).size());
    int toLearn = 0;
    int review = 0;
    for (CaseKnowledge caseKnown : AnalysisSample.knowledge(CubeMethod.ROUX)) {
      assertEquals("cmll", caseKnown.getCaseSet());
      toLearn += caseKnown.getStatus() == CaseKnowledge.Status.TO_LEARN ? 1 : 0;
      review += caseKnown.getStatus() == CaseKnowledge.Status.NEEDS_REVIEW ? 1 : 0;
    }
    assertEquals(2, toLearn);
    assertEquals(3, review);
  }

  /** The Algorithms card's sets see exactly the cases the Cases tab lists. */
  @Test
  public void algorithmsSeeTheSampledCases() {
    Map<String, AlgorithmFigures> cfop = AnalysisSample.algorithms(CubeMethod.CFOP);
    assertEquals("[f2l, oll, pll]", cfop.keySet().toString());
    assertEquals(57 - 42, cfop.get("oll").getNotSeen());
    assertEquals(21 - 19, cfop.get("pll").getNotSeen());
    assertEquals(0, cfop.get("f2l").getNotSeen());
    assertEquals(4, cfop.get("oll").getNotStandard());
    AlgorithmFigures cmll = AnalysisSample.algorithms(CubeMethod.ROUX).get("cmll");
    assertEquals(42 - 36, cmll.getNotSeen());
    assertEquals(3, cmll.getNotStandard());
    assertEquals(7, cmll.getExtraMoves());
  }

  /** LBL and blind types are shown the CFOP solver: there is no example of their own. */
  @Test
  public void otherMethodsSampleCfop() {
    assertEquals(CubeMethod.CFOP, AnalysisSample.methodFor(CubeMethod.LBL));
    assertEquals(CubeMethod.CFOP, AnalysisSample.methodFor(CubeMethod.BLIND));
    assertEquals(CubeMethod.ROUX, AnalysisSample.methodFor(CubeMethod.ROUX));
  }

  private int percent(String code) {
    long total = 0;
    for (StepStats family : STATISTICS.getFamilies()) {
      total += family.getMeanMs();
    }
    return (int) Math.round((double) step(code).getMeanMs() / total * 100);
  }
}
