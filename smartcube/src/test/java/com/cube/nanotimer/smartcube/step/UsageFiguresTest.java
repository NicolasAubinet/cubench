package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Test;

public class UsageFiguresTest {

  private static final String T_LISTED = "R U R' U' R' F R2 U' R' U' R U R' F'";
  private static final String T_OTHER = "F R U' R' U R U R2 F' R U R U' R'";
  private static final String[] PLLS = {"aa", "ab", "e", "f", "ga", "gb", "gc", "gd", "h", "ja",
      "jb", "na", "nb", "ra", "rb", "t", "ua", "ub", "v", "y", "z"};

  @After
  public void clear() {
    CaseAlgorithms.setUsage(null);
  }

  @Test
  public void withoutFiguresTheVoteOrderStands() {
    assertFalse(CaseAlgorithms.hasUsage("pll_t"));
    assertEquals(moves(CaseAlgorithms.byVotes("pll_t")), moves(CaseAlgorithms.shown("pll_t")));
  }

  @Test
  public void aCaseUnderTheFloorKeepsTheVoteOrder() {
    use("pll_t", UsageFigures.CASE_FLOOR - 1, share(T_OTHER, 90), share(T_LISTED, 10));
    assertFalse(CaseAlgorithms.hasUsage("pll_t"));
    assertFalse(CaseAlgorithms.shown("pll_t").get(0).hasShare());
  }

  @Test
  public void aCasePastTheFloorIsOrderedByShareWithNoRecommendation() {
    use("pll_t", 40, share(T_LISTED, 35), share(T_OTHER, 60));
    assertTrue(CaseAlgorithms.hasUsage("pll_t"));
    List<CaseAlgorithms.Listed> shown = CaseAlgorithms.shown("pll_t");
    assertEquals(Arrays.asList(T_OTHER, T_LISTED), moves(shown));
    assertEquals(60f, shown.get(0).getShare(), 0f);
    for (CaseAlgorithms.Listed listed : shown) {
      assertFalse(listed.isRecommended());
    }
  }

  @Test
  public void rowsThatDoNotSolveTheCaseOrRepeatOneAreDropped() {
    use("pll_t", 40, share(T_LISTED, 50), share("R U R'", 30), share(" " + T_LISTED.replace(" ", "  "), 5),
        share("<b>", 10));
    assertEquals(Collections.singletonList(T_LISTED), moves(CaseAlgorithms.shown("pll_t")));
  }

  @Test
  public void aCaseWithNoValidRowFallsBackToTheVotes() {
    use("pll_t", 40, share("R U R'", 80));
    assertFalse(CaseAlgorithms.hasUsage("pll_t"));
    assertEquals(moves(CaseAlgorithms.byVotes("pll_t")), moves(CaseAlgorithms.shown("pll_t")));
  }

  @Test
  public void equalSharesKeepTheVoteOrder() {
    String code = caseWithTwoListed();
    List<String> byVotes = moves(CaseAlgorithms.byVotes(code));
    use(code, 50, share(byVotes.get(1), 40), share(byVotes.get(0), 40));
    assertEquals(byVotes.subList(0, 2), moves(CaseAlgorithms.shown(code)));
  }

  @Test
  public void aSlotCaseIgnoresFigures() {
    List<String> before = moves(CaseAlgorithms.shown("pair_27_fl"));
    use("pair_27_fl", 50, share("R U R'", 100));
    assertFalse(CaseAlgorithms.hasUsage("pair_27_fl"));
    assertEquals(before, moves(CaseAlgorithms.shown("pair_27_fl")));
  }

  private static String caseWithTwoListed() {
    for (String pll : PLLS) {
      if (CaseAlgorithms.byVotes("pll_" + pll).size() >= 2) {
        return "pll_" + pll;
      }
    }
    throw new AssertionError("no PLL lists two algorithms");
  }

  private static void use(String code, int users, UsageFigures.Share... shares) {
    Map<String, UsageFigures.Case> cases = Collections.singletonMap(code,
        new UsageFigures.Case(users, Arrays.asList(shares)));
    CaseAlgorithms.setUsage(new UsageFigures(cases));
  }

  private static UsageFigures.Share share(String moves, float percent) {
    return new UsageFigures.Share(moves, percent);
  }

  private static List<String> moves(List<CaseAlgorithms.Listed> listed) {
    List<String> moves = new ArrayList<String>();
    for (CaseAlgorithms.Listed one : listed) {
      moves.add(one.getMoves());
    }
    return moves;
  }
}
