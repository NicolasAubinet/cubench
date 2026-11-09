package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.step.F2lPairCases;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.Test;

public class F2LTakeOutAlgorithmsTest {

  private static final String[] OTHER_SLOTS = {"fl", "bl", "br"};

  @Test
  public void everyWaySolvesItsCase() {
    StringBuilder wrong = new StringBuilder();
    for (String pairCase : F2LTakeOutAlgorithms.cases()) {
      for (String way : F2LTakeOutAlgorithms.forCase(pairCase)) {
        if (!F2LCaseAlgorithms.solves(pairCase, way)) {
          wrong.append(pairCase).append(": ").append(way).append('\n');
        }
      }
    }
    assertEquals("", wrong.toString());
  }

  /** The trapped piece's slot is not yet solved, so it is free; any other slot must stay in. */
  @Test
  public void onlyTheTrappedSlotIsDisturbed() {
    StringBuilder wrong = new StringBuilder();
    for (String pairCase : F2LTakeOutAlgorithms.cases()) {
      List<String> ways = F2LTakeOutAlgorithms.forCase(pairCase);
      String trapped = trappedSlot(pairCase);
      for (String way : ways) {
        String state = Notation.caseState(way);
        for (String slot : OTHER_SLOTS) {
          if (!slot.equals(trapped) && !pairIn(state, slot)) {
            wrong.append(pairCase).append(": ").append(way).append(" breaks ").append(slot)
                .append('\n');
          }
        }
      }
    }
    assertEquals("", wrong.toString());
  }

  /** Votes rank, never length: each case lists its most voted take-outs, as the table spells them. */
  @Test
  public void listsTheMostVotedTakeOutsOfEveryCaseThatHasOne() {
    StringBuilder wrong = new StringBuilder();
    List<String> withOne = new ArrayList<String>();
    for (String pairCase : oneTrappedPiece()) {
      List<String> voted = new ArrayList<String>();
      for (F2LCaseAlgorithms.Algorithm algorithm : F2LCaseAlgorithms.forCase(pairCase)) {
        if (algorithm.getShare() > 0 && voted.size() < 3
            && TakeOutShape.isTakeOut(algorithm.getMoves())) {
          voted.add(algorithm.getMoves());
        }
      }
      if (!voted.isEmpty()) {
        withOne.add(pairCase);
      }
      if (!voted.equals(F2LTakeOutAlgorithms.forCase(pairCase))) {
        wrong.append(pairCase).append(": ").append(voted).append('\n');
      }
    }
    assertEquals("", wrong.toString());
    assertEquals(withOne, F2LTakeOutAlgorithms.cases());
  }

  @Test
  public void holdsTheCasesTheCoachDeals() {
    List<String> codes = new ArrayList<String>();
    for (String pairCase : F2LTakeOutAlgorithms.cases()) {
      codes.add("pair_" + pairCase);
    }
    assertEquals(F2lPairCases.takeOutCodes(), codes);
  }

  /** Some of the advanced set's most voted are not take-outs: those are left to the advanced stage. */
  @Test
  public void anAdvancedTopAlgorithmIsNotReadAsATakeOut() {
    assertFalse(TakeOutShape.isTakeOut("y' S R' S'"));
    assertFalse(TakeOutShape.isTakeOut("y2 L F' U F L'"));
    assertTrue(TakeOutShape.isTakeOut("R' U2 R2 U' R'"));
  }

  @Test
  public void readsAWayTurnedFromAnotherGrip() {
    assertTrue(F2LTakeOutAlgorithms.isListed("a1", "F U' F' R U' R'"));
    assertFalse(F2LTakeOutAlgorithms.isListed("a1", "y' S R' S'"));
    assertTrue(F2LTakeOutAlgorithms.forCase("a25").isEmpty());
  }

  /** The cases with one piece trapped and the other in the last layer, a1 to a24 with the twins. */
  private static List<String> oneTrappedPiece() {
    List<String> cases = new ArrayList<String>();
    for (String code : F2lPairCases.stuckCodes()) {
      String pairCase = code.substring("pair_".length());
      if (Integer.parseInt(pairCase.replaceAll("\\D", "")) <= 24) {
        cases.add(pairCase);
      }
    }
    return cases;
  }

  /** The slot the first listed advanced algorithm leaves unsolved besides front right. */
  private static String trappedSlot(String pairCase) {
    String state = Notation.caseState(F2LCaseAlgorithms.forCase(pairCase).get(0).getMoves());
    String trapped = null;
    for (String slot : OTHER_SLOTS) {
      if (!pairIn(state, slot)) {
        assertEquals(pairCase + " has two slots out", null, trapped);
        trapped = slot;
      }
    }
    return trapped;
  }

  private static boolean pairIn(String state, String slot) {
    int corner = Cubies.slotNamed(("d" + slot).toUpperCase(Locale.US)) - Cubies.EDGES.length;
    int edge = Cubies.slotNamed(slot.toUpperCase(Locale.US));
    return Cubies.inPlace(state, Cubies.CORNERS[corner])
        && Cubies.inPlace(state, Cubies.EDGES[edge]);
  }
}
