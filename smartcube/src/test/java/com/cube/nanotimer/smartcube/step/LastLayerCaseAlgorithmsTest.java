package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/**
 * What makes a table taken off a website safe to ship: every algorithm in it is applied to a solved
 * cube backwards and handed to {@link LastLayerCases}, which must name the case it is filed under.
 * A mistyped turn, a dropped prime or a row filed against the wrong case all land somewhere else and
 * fail here rather than in front of someone trying to learn it.
 *
 * <p>The vote counts cannot be checked this way, and are not: they only ever decide the order and
 * how many of the algorithms are shown.
 */
public class LastLayerCaseAlgorithmsTest {

  /** The last layer is up, so the cross is on D. */
  private static final int CROSS = Cubies.D;

  @Test
  public void everyAlgorithmSolvesTheCaseItIsFiledUnder() {
    List<String> wrong = new ArrayList<String>();
    for (String[] row : LastLayerCaseAlgorithms.rows()) {
      String named;
      try {
        named = nameOf(row[0], Notation.caseState(row[1]));
      } catch (RuntimeException e) {
        named = e.getClass().getSimpleName() + ": " + e.getMessage();
      }
      if (!row[0].equals(named)) {
        wrong.add(row[0] + " | " + row[1] + " | landed on " + named);
      }
    }
    assertEquals(join(wrong), 0, wrong.size());
  }

  @Test
  public void namesOnlyCasesThereAreScramblesFor() {
    Set<String> cases = new HashSet<String>(LastLayerScrambles.cases());
    for (String[] row : LastLayerCaseAlgorithms.rows()) {
      assertTrue(row[0], cases.contains(row[0]));
    }
  }

  @Test
  public void hasAtLeastOneAlgorithmForEveryCase() {
    for (String code : LastLayerScrambles.cases()) {
      assertFalse(code, LastLayerCaseAlgorithms.forCase(code).isEmpty());
    }
  }

  /** Most used first, and never anything but the first one recommended. */
  @Test
  public void putsTheMostUsedFirst() {
    for (String code : LastLayerScrambles.cases()) {
      List<LastLayerCaseAlgorithms.Algorithm> shown = LastLayerCaseAlgorithms.forCase(code);
      int previous = shown.get(0).getShare();
      for (int i = 1; i < shown.size(); i++) {
        assertFalse(code, shown.get(i).isRecommended());
        assertTrue(code, shown.get(i).getShare() <= previous);
        previous = shown.get(i).getShare();
      }
    }
  }

  /** A case the world is split on is not handed a recommendation it does not have. */
  @Test
  public void recommendsOnlyWhereTheVoteIsNotClose() {
    // OLL 13's top two are level; OLL 5 and the Na are two to one.
    assertFalse(LastLayerCaseAlgorithms.forCase("oll_13").get(0).isRecommended());
    assertTrue(LastLayerCaseAlgorithms.forCase("oll_5").get(0).isRecommended());
    assertTrue(LastLayerCaseAlgorithms.forCase("pll_na").get(0).isRecommended());
    // Nothing to be clearer than, so nothing is claimed.
    List<LastLayerCaseAlgorithms.Algorithm> alone = LastLayerCaseAlgorithms.forCase("pll_jb");
    assertEquals(1, alone.size());
    assertFalse(alone.get(0).isRecommended());
  }

  /** What guards an algorithm a user types in: it has to solve the case it is being filed under. */
  @Test
  public void acceptsOnlyAnAlgorithmThatSolvesTheCase() {
    assertTrue(LastLayerCaseAlgorithms.solves("oll_27", "R U R' U R U2 R'"));
    assertTrue(LastLayerCaseAlgorithms.solves("pll_t", "R U R' U' R' F R2 U' R' U' R U R' F'"));
    // Right algorithm, wrong case.
    assertFalse(LastLayerCaseAlgorithms.solves("oll_26", "R U R' U R U2 R'"));
    // An OLL is done when the layer is oriented, so another correct algorithm still passes.
    assertTrue(LastLayerCaseAlgorithms.solves("oll_27", "y' R' U2 R U R' U R"));
    assertFalse(LastLayerCaseAlgorithms.solves("oll_27", "R U R' U R U2 R"));
    assertFalse(LastLayerCaseAlgorithms.solves("oll_27", "R U R' banana"));
    assertFalse(LastLayerCaseAlgorithms.solves("oll_27", ""));
    assertFalse(LastLayerCaseAlgorithms.solves("oll_27", null));
    assertFalse(LastLayerCaseAlgorithms.solves(null, "R U R' U R U2 R'"));
  }

  /** Every algorithm on file passes the check the user's own entries are put through. */
  @Test
  public void acceptsEveryAlgorithmItAlreadyHolds() {
    for (String[] row : LastLayerCaseAlgorithms.rows()) {
      assertTrue(row[0] + " | " + row[1], LastLayerCaseAlgorithms.solves(row[0], row[1]));
    }
  }

  /** Nothing quiet is shown beside the most used one, and no case turns into a catalogue. */
  @Test
  public void showsOnlyTheAlgorithmsPeopleUse() {
    for (String code : LastLayerScrambles.cases()) {
      List<LastLayerCaseAlgorithms.Algorithm> shown = LastLayerCaseAlgorithms.forCase(code);
      assertTrue(code, shown.size() <= 4);
      for (int i = 1; i < shown.size(); i++) {
        assertTrue(code + " " + shown.get(i).getShare() + "%",
            shown.get(i).getShare() >= LastLayerCaseAlgorithms.DEFAULT_MIN_SHARE);
      }
    }
  }

  /** A case everyone solves the same way is shown one way, whatever else is on file. */
  @Test
  public void showsOneAlgorithmForACaseWithOneAnswer() {
    assertEquals(1, LastLayerCaseAlgorithms.forCase("pll_jb").size());
    assertEquals(1, LastLayerCaseAlgorithms.forCase("oll_27").size());
    assertTrue(LastLayerCaseAlgorithms.forCase("pll_ua").size() > 1);
  }

  @Test
  public void raisingTheThresholdNarrowsTheChoice() {
    assertEquals(1, LastLayerCaseAlgorithms.forCase("pll_ua", 100).size());
    assertTrue(LastLayerCaseAlgorithms.forCase("pll_ua", 1).size()
        >= LastLayerCaseAlgorithms.forCase("pll_ua").size());
  }

  private static String nameOf(String code, String state) {
    String name = code.startsWith("oll_") ? LastLayerCases.orientation(state, CROSS)
        : LastLayerCases.permutation(state, CROSS);
    assertNotNull(code, name);
    return (code.startsWith("oll_") ? "oll_" : "pll_") + name;
  }

  private static String join(List<String> lines) {
    StringBuilder joined = new StringBuilder("\n");
    for (String line : lines) {
      joined.append(line).append("\n");
    }
    return joined.toString();
  }
}
