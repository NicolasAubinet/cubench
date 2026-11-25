package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class CaseBotchTest {

  private static final String AA = "x R' U R' D2 R U' R' D2 R2 x'";
  private static final List<String> NONE = Collections.emptyList();

  @Test
  public void wholeListedAlgorithmIsClean() {
    assertEquals(CaseBotch.Verdict.CLEAN, CaseBotch.judge("pll_aa", AA, NONE, false).getVerdict());
  }

  @Test
  public void listedAlgorithmFromAnotherGripAndWithAufsIsClean() {
    assertEquals(CaseBotch.Verdict.CLEAN,
        CaseBotch.judge("pll_aa", "U y " + AA + " U2", NONE, false).getVerdict());
  }

  @Test
  public void cleanStartCutShortIsAPopNotABotch() {
    assertEquals(CaseBotch.Verdict.CLEAN,
        CaseBotch.judge("pll_aa", "x R' U R' D2 R", NONE, false).getVerdict());
  }

  @Test
  public void halfOfALastTurnStillReadsAsTheStart() {
    assertEquals(CaseBotch.Verdict.CLEAN,
        CaseBotch.judge("pll_aa", "x R' U R' D", NONE, false).getVerdict());
  }

  @Test
  public void undoneMovesAreFoldedAway() {
    assertEquals(CaseBotch.Verdict.CLEAN,
        CaseBotch.judge("pll_aa", "U R L2 L2 R' U'", NONE, false).getVerdict());
  }

  @Test
  public void undoneDetourInsideACleanStartIsStillClean() {
    assertEquals(CaseBotch.Verdict.CLEAN,
        CaseBotch.judge("pll_aa", "x R' U R' F F' D2 R", NONE, false).getVerdict());
  }

  @Test
  public void startThatGoesWrongIsBotched() {
    CaseBotch botch = CaseBotch.judge("pll_aa", "x R' U R' D2 R U R' D2 F", NONE, false);
    assertEquals(CaseBotch.Verdict.BOTCHED, botch.getVerdict());
    assertTrue(botch.counts());
  }

  @Test
  public void solversOwnExecutionIsNotABotch() {
    String own = "x R' U R' D2 R U R' D2 F";
    assertEquals(CaseBotch.Verdict.CLEAN,
        CaseBotch.judge("pll_aa", own, Arrays.asList(own), false).getVerdict());
  }

  @Test
  public void anotherCasesAlgorithmIsAMisread() {
    String gb = LastLayerCaseAlgorithms.listed("pll_gb").get(0).getMoves();
    CaseBotch botch = CaseBotch.judge("pll_ga", gb, NONE, false);
    assertEquals(CaseBotch.Verdict.MISREAD, botch.getVerdict());
    assertEquals("pll_gb", botch.getMisreadAs());
  }

  @Test
  public void mirrorCaseIsAMisreadNotTheSameAlgorithm() {
    String rb = LastLayerCaseAlgorithms.listed("pll_rb").get(0).getMoves();
    assertEquals(CaseBotch.Verdict.MISREAD, CaseBotch.judge("pll_ra", rb, NONE, false).getVerdict());
  }

  @Test
  public void twoLookOllStoppedInItsEdgesIsClean() {
    assertEquals(CaseBotch.Verdict.CLEAN,
        CaseBotch.judge("oll_40", "F R U R' U'", NONE, false).getVerdict());
  }

  @Test
  public void twoLookCmllStoppedInItsOrientationIsClean() {
    assertEquals(CaseBotch.Verdict.CLEAN,
        CaseBotch.judge("cmll_picolumns", "U' R U2 R2 U' R2 U'", NONE, false).getVerdict());
  }

  @Test
  public void twoLookPllStoppedInItsCornersIsCleanOnACaseNotLearnt() {
    String tPerm = LastLayerCaseAlgorithms.listed("pll_t").get(0).getMoves();
    assertEquals(CaseBotch.Verdict.CLEAN, CaseBotch.judge("pll_ga", tPerm, NONE, false).getVerdict());
  }

  @Test
  public void firstLookOnALearntCaseIsTwoLooked() {
    String tPerm = LastLayerCaseAlgorithms.listed("pll_t").get(0).getMoves();
    CaseBotch botch = CaseBotch.judge("pll_ga", tPerm, NONE, true);
    assertEquals(CaseBotch.Verdict.TWO_LOOKED, botch.getVerdict());
    assertTrue(botch.counts());
    assertEquals(CaseBotch.Verdict.TWO_LOOKED,
        CaseBotch.judge("oll_40", "F R U R' U'", NONE, true).getVerdict());
  }

  @Test
  public void aLearntCasesOwnAlgorithmStaysClean() {
    assertEquals(CaseBotch.Verdict.CLEAN, CaseBotch.judge("pll_aa", AA, NONE, true).getVerdict());
  }

  @Test
  public void aCornerAlgorithmOnACornerCaseIsAMisreadNotAFirstLook() {
    String aa = LastLayerCaseAlgorithms.listed("pll_aa").get(0).getMoves();
    CaseBotch botch = CaseBotch.judge("pll_ab", aa, NONE, false);
    assertEquals(CaseBotch.Verdict.MISREAD, botch.getVerdict());
    assertEquals("pll_aa", botch.getMisreadAs());
  }

  @Test
  public void anOcllHasNoEdgeFirstLook() {
    assertTrue(CaseBotch.firstLooks("oll_27").isEmpty());
    assertTrue(CaseBotch.firstLooks("pll_ua").isEmpty());
    assertTrue(CaseBotch.firstLooks("cmll_oadjacent").isEmpty());
  }

  @Test
  public void lastTurnTheWrongWayIsASlip() {
    assertEquals(CaseBotch.Verdict.BOTCHED,
        CaseBotch.judge("pll_aa", "x R' U R' D2 R U' R", NONE, false).getVerdict());
  }

  @Test
  public void regripHalfwayIsStillAStart() {
    assertEquals(CaseBotch.Verdict.CLEAN,
        CaseBotch.judge("pll_aa", "x R' U R' y2 D2 R U'", NONE, false).getVerdict());
  }

  @Test
  public void cmllThatGoesWrongIsBotched() {
    assertEquals(CaseBotch.Verdict.BOTCHED,
        CaseBotch.judge("cmll_picolumns", "R U R' F2 D B R'", NONE, false).getVerdict());
  }

  @Test
  public void unreadableMovesSayNothing() {
    assertFalse(CaseBotch.judge("pll_aa", "Q R U", NONE, false).counts());
  }

  @Test
  public void casesLikeStaysInTheFamily() {
    List<String> plls = LastLayerCaseAlgorithms.casesLike("pll_aa");
    assertTrue(plls.contains("pll_t"));
    assertFalse(plls.contains("oll_21"));
    assertEquals(21, plls.size());
  }
}
