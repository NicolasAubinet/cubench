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

  @Test
  public void noWayTurnsTheCubeOrAHardFace() {
    for (String pairCase : F2LTakeOutAlgorithms.cases()) {
      for (String way : F2LTakeOutAlgorithms.forCase(pairCase)) {
        for (String move : way.split(" ")) {
          assertTrue(pairCase + ": " + way, "RULF".indexOf(move.charAt(0)) >= 0);
        }
      }
    }
  }

  @Test
  public void holdsTheCasesTheCoachDeals() {
    List<String> codes = new ArrayList<String>();
    for (String pairCase : F2LTakeOutAlgorithms.cases()) {
      codes.add("pair_" + pairCase);
    }
    assertEquals(F2lPairCases.takeOutCodes(), codes);
  }

  @Test
  public void readsAWayTurnedFromAnotherGrip() {
    assertTrue(F2LTakeOutAlgorithms.isListed("a1", "y' R U' R' y R U' R'"));
    assertFalse(F2LTakeOutAlgorithms.isListed("a1", "y' S R' S'"));
    assertTrue(F2LTakeOutAlgorithms.forCase("a25").isEmpty());
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
