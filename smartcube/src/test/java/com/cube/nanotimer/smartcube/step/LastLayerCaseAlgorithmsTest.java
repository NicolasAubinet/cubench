package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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

  /** The stickers the picture shows: the nine cells of the layer, then the twelve down its sides. */
  private static final int[] LAYER = {
    0, 1, 2, 3, 4, 5, 6, 7, 8, 45, 46, 47, 9, 10, 11, 18, 19, 20, 36, 37, 38,
  };

  /** A permutation is solved whichever way the layer is left facing. */
  private static final String[] AUFS = {"", "U", "U2", "U'"};

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

  /**
   * What the picture beside them promises: every algorithm of a case is written for the cube held
   * one way, so the reader turns nothing between recognising the case and starting. The first one is
   * the one {@link LastLayerDiagram} draws, so the rest are held to it.
   */
  @Test
  public void writesEveryAlgorithmForThePicture() {
    List<String> wrong = new ArrayList<String>();
    String code = null;
    String picture = null;
    for (String[] row : LastLayerCaseAlgorithms.rows()) {
      if (!row[0].equals(code)) {
        code = row[0];
        picture = Notation.caseState(row[1]);
      } else if (!worksFrom(picture, row[1], code.startsWith("oll_"))) {
        wrong.add(code + " | " + row[1]);
      }
    }
    assertEquals(join(wrong), 0, wrong.size());
  }

  /** A turn of the cube before the most used algorithm would be a turn of the picture. */
  @Test
  public void turnsNothingBeforeTheFirstAlgorithm() {
    String code = null;
    for (String[] row : LastLayerCaseAlgorithms.rows()) {
      if (row[0].equals(code)) {
        continue;
      }
      code = row[0];
      assertFalse(code + " | " + row[1], row[1].split(" ")[0].matches("y('|2)?"));
    }
  }

  /**
   * Whether an algorithm does its job on the case as it is drawn: an orientation has to find the
   * same stickers facing up, and a permutation the same pieces in the same places. An orientation is
   * held to where the layer starts rather than where it ends, since it is the picture it has to
   * agree with; a permutation may leave the layer facing any way it likes.
   */
  /** Every algorithm on file is recognised from its own moves, which is the floor for the rest. */
  @Test
  public void recognisesEveryAlgorithmItHolds() {
    List<String> missed = new ArrayList<String>();
    for (String[] row : LastLayerCaseAlgorithms.rows()) {
      LastLayerCaseAlgorithms.Algorithm matched = LastLayerCaseAlgorithms.matching(row[0], row[1]);
      if (matched == null || !sameAlgorithm(matched.getMoves(), row[1])) {
        missed.add(row[0] + " | " + row[1] + " | read as "
            + (matched == null ? "none of them" : matched.getMoves()));
      }
    }
    assertEquals(join(missed), 0, missed.size());
  }

  /** What an execution actually looks like: aligned, held somewhere, and turned into the next case. */
  @Test
  public void recognisesOneThroughAnAlignmentAndAGrip() {
    String tperm = "R U R' U' R' F R2 U' R' U' R U R' F'";
    assertNotNull(LastLayerCaseAlgorithms.matching("pll_t", "U " + tperm + " U2"));
    assertNotNull(LastLayerCaseAlgorithms.matching("pll_t", "y2 " + tperm));
    assertNotNull(LastLayerCaseAlgorithms.matching("pll_t", "U' y " + tperm + " y' U"));
    // The regrip in the middle of it, turned back before the algorithm goes on.
    assertNotNull(LastLayerCaseAlgorithms.matching("pll_t", "R U R' U' R' F R2 U' R' U' R U R' D D' F'"));
  }

  /** A misread is the whole point of asking: the algorithm run is not the case it was handed. */
  @Test
  public void doesNotRecogniseAnAlgorithmOfAnotherCase() {
    assertNull(LastLayerCaseAlgorithms.matching("oll_21", "R U R' U R U2 R'")); // that is OLL 27
    assertNull(LastLayerCaseAlgorithms.matching("oll_27", "R U R' banana"));
    assertNull(LastLayerCaseAlgorithms.matching("oll_27", ""));
    assertNull(LastLayerCaseAlgorithms.matching("oll_27", null));
    assertNull(LastLayerCaseAlgorithms.matching(null, "R U R' U R U2 R'"));
  }

  /**
   * A sixth of the table is one algorithm written twice, and the mirrored spelling turns the cube
   * exactly as the first one does. Nothing reading the moves can tell those apart, so the most used
   * of them answers for all of them rather than a coin being tossed.
   */
  @Test
  public void answersWithTheMostUsedOfTheSpellingsThatTurnTheCubeAlike() {
    String mirrored = "y2 l' U2 L U L' U l"; // OLL 5's most used, mirrored behind the rotation
    assertTrue(sameAlgorithm("r' U2 R U R' U r", mirrored));
    assertEquals("r' U2 R U R' U r", LastLayerCaseAlgorithms.matching("oll_5", mirrored).getMoves());
    assertEquals("r' U2 R U R' U r",
        LastLayerCaseAlgorithms.matching("oll_5", "r' U2 R U R' U r").getMoves());
    // And for a cube that was standing on its side when the case came up.
    assertEquals("r' U2 R U R' U r",
        LastLayerCaseAlgorithms.matching("oll_5", "z r' U2 R U R' U r z'").getMoves());
  }

  private static boolean sameAlgorithm(String one, String other) {
    return AlgorithmForm.withoutAlignment(AlgorithmForm.of(one))
        .equals(AlgorithmForm.withoutAlignment(AlgorithmForm.of(other)));
  }

  private static boolean worksFrom(String picture, String alg, boolean orientation) {
    for (String auf : orientation ? new String[] {""} : AUFS) {
      String state = Notation.caseState((alg + " " + auf).trim());
      if (orientation ? sameStickersUp(state, picture) : state.equals(picture)) {
        return true;
      }
    }
    return false;
  }

  private static boolean sameStickersUp(String state, String picture) {
    for (int facelet : LAYER) {
      if ((state.charAt(facelet) == 'U') != (picture.charAt(facelet) == 'U')) {
        return false;
      }
    }
    return true;
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
