package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.model.CubeState;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.Test;

public class F2LCaseAlgorithmsTest {

  @Test
  public void everyAlgorithmSolvesTheCaseItIsFiledUnder() {
    StringBuilder wrong = new StringBuilder();
    for (String[] row : F2LCaseAlgorithms.rows()) {
      String read = F2LCases.pairCase(Notation.caseState(row[1]), Cubies.D, Cubies.DFR, Cubies.FR);
      if (!row[0].equals(read)) {
        wrong.append(row[0]).append(": ").append(row[1]).append(" reads ").append(read).append('\n');
      }
    }
    assertEquals("", wrong.toString());
  }

  @Test
  public void onlyTheNamedEmptySlotIsDisturbed() {
    StringBuilder wrong = new StringBuilder();
    for (String[] row : F2LCaseAlgorithms.rows()) {
      if (row[0].startsWith("a")) {
        continue; // an advanced case's trapped piece belongs to another slot, which it leaves out
      }
      String state = Notation.caseState(row[1]);
      for (String slot : new String[] {"fl", "bl", "br"}) {
        if (!slot.equals(row[2]) && !pairIn(state, slot)) {
          wrong.append(row[0]).append(": ").append(row[1]).append(" needs ").append(slot).append('\n');
        }
      }
    }
    assertEquals("", wrong.toString());
  }

  @Test
  public void matchesAnAlgorithmTurnedAsItIsListed() {
    assertEquals("R U R'", F2LCaseAlgorithms.matching("4", "R U R'").getMoves());
  }

  @Test
  public void matchesThroughTheSetUpTurnAndMovesTakenBack() {
    assertEquals("R U R'", F2LCaseAlgorithms.matching("4", "U U' R U2 U' R'").getMoves());
    assertEquals("U R U' R'", F2LCaseAlgorithms.matching("1", "U2 R U' R' U").getMoves());
  }

  @Test
  public void matchesAPairTurnedIntoAnotherSlot() {
    assertEquals("R U R'", F2LCaseAlgorithms.matching("4", "F U F'").getMoves()); // front left
    assertEquals("R U R'", F2LCaseAlgorithms.matching("4", "y2 R U R'").getMoves());
  }

  @Test
  public void matchesASliceAlgorithmTurnedFromEveryGrip() {
    for (char[] grip : AlgorithmForm.grips()) {
      String stood = String.join(" ",
          AlgorithmForm.conjugatedBy(AlgorithmForm.of("M U r U' r' U' M'"), grip));
      assertNotNull(stood, F2LCaseAlgorithms.matching("15", stood));
    }
  }

  @Test
  public void matchesNothingForAnExecutionOffTheList() {
    assertNull(F2LCaseAlgorithms.matching("4", "R U2 R' U' R U R'"));
    assertNull(F2LCaseAlgorithms.matching("4", "R U R' U R U' R'"));
    assertNull(F2LCaseAlgorithms.matching("4", "L' U' L")); // a mirror is not a grip
    assertNull(F2LCaseAlgorithms.matching("4", "garbage"));
  }

  @Test
  public void listsACaseMostVotedFirst() {
    Set<String> cases = new LinkedHashSet<>();
    for (String[] row : F2LCaseAlgorithms.rows()) {
      cases.add(row[0]);
    }
    for (String pairCase : cases) {
      int previous = Integer.MAX_VALUE;
      for (F2LCaseAlgorithms.Algorithm algorithm : F2LCaseAlgorithms.forCase(pairCase)) {
        assertTrue(pairCase, algorithm.getShare() <= previous);
        previous = algorithm.getShare();
      }
    }
    assertEquals("U R U' R'", F2LCaseAlgorithms.forCase("1").get(0).getMoves());
  }

  @Test
  public void holdsEachAlgorithmOnce() {
    for (String[] row : F2LCaseAlgorithms.rows()) {
      int turnings = 0;
      for (F2LCaseAlgorithms.Algorithm algorithm : F2LCaseAlgorithms.forCase(row[0])) {
        if (AlgorithmForm.indexOfTurning(
            Collections.singletonList(AlgorithmForm.comparable(algorithm.getMoves())), row[1],
            stood -> F2LCaseAlgorithms.solves(row[0], stood)) >= 0) {
          turnings++;
        }
      }
      assertEquals(row[1], 1, turnings);
    }
  }

  @Test
  public void everyBasicCaseHasAnAlgorithm() {
    for (int basic = 1; basic <= 41; basic++) {
      assertFalse(String.valueOf(basic), F2LCaseAlgorithms.forCase(String.valueOf(basic)).isEmpty());
    }
  }

  @Test
  public void everyAdvancedCaseHasAnAlgorithm() {
    for (int advanced = 1; advanced <= 42; advanced++) {
      assertFalse("a" + advanced, F2LCaseAlgorithms.forCase("a" + advanced).isEmpty());
    }
    for (int advanced = 1; advanced <= 9; advanced++) {
      assertFalse("a" + advanced + "a", F2LCaseAlgorithms.forCase("a" + advanced + "a").isEmpty());
    }
  }

  private static boolean pairIn(String state, String slot) {
    int corner = Cubies.slotNamed(("d" + slot).toUpperCase(Locale.US)) - Cubies.EDGES.length;
    int edge = Cubies.slotNamed(slot.toUpperCase(Locale.US));
    return Cubies.inPlace(state, Cubies.CORNERS[corner]) && Cubies.inPlace(state, Cubies.EDGES[edge]);
  }

  /** Listed by the last layer rules: case 7's top three hold 71%, so a fourth is needed for 80. */
  @Test
  public void listsUntilMostVotesAreHeldWithoutARecommendationOnACloseVote() {
    List<F2LCaseAlgorithms.Algorithm> shown = F2LCaseAlgorithms.listed("7");

    assertEquals(4, shown.size());
    assertEquals("U' R U2 R' U' R U2 R'", shown.get(0).getMoves());
    assertFalse(shown.get(0).isRecommended());
  }

  @Test
  public void showsOnlyTheMostVotedWhereNothingElseComesClose() {
    assertEquals(1, F2LCaseAlgorithms.listed("3").size());
  }

  /** Into front left: the regrips folded into the faces turned, one rotation stands it in FR. */
  @Test
  public void writesAPairTurnedInAnotherSlotInTheSolversOwnFaces() {
    assertEquals("y U R U2 R' U' F U' F'",
        F2LCaseAlgorithms.asTurned("7", "y' U R U2 R' U' x U y' z U' R'"));
  }

  @Test
  public void writesAPairTurnedIntoFrontRightWithNoRotation() {
    assertEquals("U R U2 R' U2 R U' R'", F2LCaseAlgorithms.asTurned("7", "y U R U2 R' U2 R U' R'"));
    assertNull(F2LCaseAlgorithms.asTurned("7", "R U R'"));
  }

  @Test
  public void groupsOneWayOfTurningACaseWhicheverSlotItWasTurnedIn() {
    assertTrue(F2LCaseAlgorithms.sameTurning("7", "y' U R U2 R' U' x U y' z U' R'",
        "U' R U2 R' U' F U' y' R'"));
    assertFalse(F2LCaseAlgorithms.sameTurning("7", "U' R U2 R' U' R U2 R'",
        "U' R U2 R' U2 R U' R'"));
  }

  @Test
  public void readsAnExecutionNoAlgorithmMatchesAsUnusual() {
    assertTrue(F2LCaseAlgorithms.read("4", "R U2 R' U' R U R'").isUnusual());
    assertFalse(F2LCaseAlgorithms.read("7", "U' R U2 R' U' R U2 R'").isUnusual());
  }

  /** Two pairs built through a free slot, both off every list and neither longer than the usual. */
  @Test
  public void readsAPairBuiltThroughAFreeSlotInAsFewTurnsAsUsual() {
    assertNull(F2LCaseAlgorithms.matching("6", "R' U' R U' F' U F"));
    assertFalse(F2LCaseAlgorithms.read("6", "R' U' R U' F' U F").isUnusual());
    assertFalse(F2LCaseAlgorithms.read("5", "U' L U R L' U' R'").isUnusual());
    assertFalse(F2LCaseAlgorithms.read("7", "U B U2 B' U' R U' R'").isUnusual());
    assertFalse(CaseAlgorithms.isWorthPointingOut("pair_6", "R' U' R U' F' U F"));
  }

  @Test
  public void readsAPairBuiltThroughAFreeSlotInMoreTurnsAsUnusual() {
    assertTrue(F2LCaseAlgorithms.read("6", "F' U' F R' U R U F' U F").isUnusual());
    assertTrue(CaseAlgorithms.isWorthPointingOut("pair_6", "F' U' F R' U R U F' U F"));
  }

  @Test
  public void readsNoFreeSlotInAnAlgorithmThatLeavesTheOtherSlotsAlone() {
    assertFalse(F2LCaseAlgorithms.usesFreeSlot("6", "d R' U' R U2 R' U R"));
    assertTrue(F2LCaseAlgorithms.usesFreeSlot("6", "R' U' R U' F' U F"));
  }

  /** The picture's state: turned over and solved with the case's own algorithm, it is solved. */
  @Test
  public void drawsTheCaseTheMostVotedAlgorithmSolves() {
    String facelets = F2LCaseAlgorithms.crossUpFacelets("7");

    assertEquals(CubeState.SOLVED_FACELETS,
        Notation.apply(facelets, "z2 U' R U2 R' U' R U2 R' z2"));
  }

  @Test
  public void keepsOnlyWhatSolvesTheCase() {
    assertTrue(F2LCaseAlgorithms.solves("7", "U' R U2 R' U' R U2 R'"));
    assertFalse(F2LCaseAlgorithms.solves("7", "R U R'"));
    assertFalse(F2LCaseAlgorithms.solves("7", "R U 7"));
    assertFalse(F2LCaseAlgorithms.solves("7", " "));
  }
}
