package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

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
  public void everyBasicCaseHasAnAlgorithm() {
    for (int basic = 1; basic <= 41; basic++) {
      assertFalse(String.valueOf(basic), F2LCaseAlgorithms.forCase(String.valueOf(basic)).isEmpty());
    }
  }

  private static boolean pairIn(String state, String slot) {
    int corner = Cubies.slotNamed(("d" + slot).toUpperCase(Locale.US)) - Cubies.EDGES.length;
    int edge = Cubies.slotNamed(slot.toUpperCase(Locale.US));
    return Cubies.inPlace(state, Cubies.CORNERS[corner]) && Cubies.inPlace(state, Cubies.EDGES[edge]);
  }
}
