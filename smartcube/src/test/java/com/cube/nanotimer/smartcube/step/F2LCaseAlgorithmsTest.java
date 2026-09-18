package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Locale;
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
}
