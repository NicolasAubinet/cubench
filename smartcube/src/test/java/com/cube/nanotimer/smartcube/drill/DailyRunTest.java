package com.cube.nanotimer.smartcube.drill;

import com.cube.nanotimer.drill.DrillSpec;
import com.cube.nanotimer.smartcube.model.CubeMove;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@RunWith(JUnit4.class)
public class DailyRunTest {

  private static final String REPLY = "{\"blocks\":["
      + "{\"family\":\"oll\",\"cases\":[{\"code\":\"oll_21\",\"reason\":\"due\",\"days\":9,"
      + "\"reps\":2}],\"drill\":{}},"
      + "{\"family\":\"pll\",\"cases\":["
      + "{\"code\":\"pll_t\",\"reason\":\"review\",\"days\":2,\"slip_kind\":\"two_looks\","
      + "\"reps\":5},"
      + "{\"code\":\"pll_h\",\"reason\":\"pause\",\"recognition_ms\":1800,\"reps\":2}],"
      + "\"drill\":{}}]}";

  @Test
  public void parsesTheBlocksWithEachCasesReasonAndFigure() {
    List<DailyRun.Block> blocks = DailyRun.parse(REPLY);

    Assert.assertEquals(2, blocks.size());
    DailyRun.Entry review = blocks.get(1).getEntries().get(0);
    Assert.assertTrue(review.isReview());
    Assert.assertEquals(Integer.valueOf(2), review.getDays());
    Assert.assertEquals("two_looks", review.getSlipKind());
    Assert.assertEquals(Long.valueOf(1800), blocks.get(1).getEntries().get(1).getRecognitionMs());
    Assert.assertTrue(DailyRun.parse("not an answer").isEmpty());
  }

  @Test
  public void dealsBlockByBlockAndAReviewCaseUntilThreeCleanRunning() {
    DailyRun run = new DailyRun(DailyRun.parse(REPLY));
    List<String> dealt = new ArrayList<String>();
    String next;
    int rep = 0;
    while ((next = run.next()) != null && dealt.size() < 20) {
      dealt.add(next);
      // The T perm's second rep is looked up, which starts its run again.
      run.record(rep(next, next.equals("pll_t") && count(dealt, "pll_t") == 2));
      rep++;
    }

    Assert.assertEquals(Arrays.asList("oll_21", "oll_21", "pll_t", "pll_h", "pll_t", "pll_h",
        "pll_t", "pll_t", "pll_t"), dealt);
    Assert.assertEquals(Collections.singletonList("pll_t"), run.getBackToKnown());
    Assert.assertTrue(run.getSlipped().isEmpty());
    Assert.assertEquals(8, run.getCleanReps());
  }

  @Test
  public void aReviewCaseStopsAtItsPlannedRepsAndComesBackTomorrow() {
    DailyRun run = new DailyRun(DailyRun.parse(REPLY));
    String next;
    while ((next = run.next()) != null) {
      run.record(rep(next, next.equals("pll_t") && run.getRepsDealt() % 2 == 0));
    }

    Assert.assertTrue(run.getBackToKnown().isEmpty());
    Assert.assertEquals(Collections.singletonList("pll_t"), run.getSlipped());
  }

  @Test
  public void opensABlockOnlyAtTheFamilyChange() {
    DailyRun run = new DailyRun(DailyRun.parse(REPLY));
    Assert.assertFalse(run.opensBlock("oll_21")); // the first case has no pause before it
    run.record(rep("oll_21", false));
    Assert.assertFalse(run.opensBlock("oll_21"));
    run.record(rep("oll_21", false));
    Assert.assertTrue(run.opensBlock("pll_t"));
  }

  @Test
  public void theBlocksRepCountGrowsWithASlip() {
    DailyRun run = new DailyRun(DailyRun.parse(REPLY));
    DailyRun.Block pll = run.getBlocks().get(1);
    Assert.assertEquals(3 + 2, run.repsInBlock(pll));
    run.record(rep("pll_t", true));
    Assert.assertEquals(1 + 3 + 2, run.repsInBlock(pll));
    Assert.assertEquals(2, run.repInBlock(pll));
  }

  @Test
  public void aSessionDealsWhatTheRunSaysAndEndsWithIt() {
    List<DailyRun.Block> blocks = DailyRun.parse(REPLY);
    DrillSpec spec = DailyRun.spec(blocks, null);
    Assert.assertTrue(spec.isDaily());
    Assert.assertEquals(9, spec.getReps());
    DrillSession session = new DrillSession(spec, new Random(1));
    DailyRun run = new DailyRun(DailyRun.dealable(blocks, session.getCases()));
    session.setDealer(run);

    Assert.assertTrue(session.nextRep());
    Assert.assertEquals("oll_21", session.getCurrentCase());
    run.record(rep("oll_21", false));
    run.record(rep("oll_21", false));
    Assert.assertTrue(session.nextRep());
    Assert.assertEquals("pll_t", session.getCurrentCase());
    for (String code : Arrays.asList("pll_t", "pll_t", "pll_t", "pll_h", "pll_h")) {
      run.record(rep(code, false));
    }
    Assert.assertFalse(session.nextRep());
  }

  @Test
  public void aCaseThisAppCannotDealIsDropped() {
    List<DailyRun.Block> blocks = DailyRun.dealable(DailyRun.parse(REPLY),
        Arrays.asList("pll_t", "pll_h"));
    Assert.assertEquals(1, blocks.size());
    Assert.assertEquals("pll", blocks.get(0).getFamily());
  }

  @Test
  public void theBlocksSurviveARoundTrip() {
    List<DailyRun.Block> blocks = DailyRun.parse(DailyRun.toJson(DailyRun.parse(REPLY)));
    Assert.assertEquals(DailyRun.toJson(DailyRun.parse(REPLY)), DailyRun.toJson(blocks));
    Assert.assertEquals("two_looks", blocks.get(1).getEntries().get(0).getSlipKind());
  }

  private static int count(List<String> dealt, String code) {
    return Collections.frequency(dealt, code);
  }

  private static DrillRep rep(String code, boolean revealed) {
    return new DrillRep(code, "", Collections.<CubeMove>emptyList(), 0, 800, 1500, 12, 0,
        revealed, false);
  }
}
