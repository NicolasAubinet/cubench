package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class F2LBackSlotAlgorithmsTest {

  @Test
  public void everyRowPutsItsPairInItsSlotAndNothingElseOut() {
    for (String[] row : F2LBackSlotAlgorithms.rows()) {
      assertTrue(row[0] + " " + row[1] + ": " + row[2],
          F2LBackSlotAlgorithms.solves(row[0], row[1], row[2]));
    }
  }

  @Test
  public void noRowTurnsTheCubeOrTheBackFace() {
    for (String[] row : F2LBackSlotAlgorithms.rows()) {
      assertFalse(row[2], row[2].matches(".*[xyzB].*"));
    }
  }

  @Test
  public void everyBasicCaseHasOneForEachBackSlot() {
    for (String name : F2LCases.named()) {
      if (F2LCases.isBasic(name)) {
        assertFalse(name, F2LBackSlotAlgorithms.forCase(name, "br").isEmpty());
        assertFalse(name, F2LBackSlotAlgorithms.forCase(name, "bl").isEmpty());
      }
    }
  }

  @Test
  public void theFrontAlgorithmDoesNotSolveTheBackSlot() {
    assertFalse(F2LBackSlotAlgorithms.solves("4", "br", "R U R'"));
    assertTrue(F2LBackSlotAlgorithms.solves("4", "bl", "L U L'"));
  }

  @Test
  public void aTurnIsReadFromTheGripTheCaseWasDealtIn() {
    // d is a turn of the top layer as the cube reports it, then the faces are named a quarter on.
    assertTrue(F2LBackSlotAlgorithms.sameTurning("d L U' L'", "U B U' B'"));
    assertTrue(F2LBackSlotAlgorithms.sameTurning("U L U' L'", "U2 L U' L' U"));
    assertFalse(F2LBackSlotAlgorithms.sameTurning("L U L'", "R U R'"));
  }

  @Test
  public void aBackSlotCodeIsItsOwnCase() {
    assertEquals("br", CaseAlgorithms.backSlotOf("pair_4_br"));
    assertNull(CaseAlgorithms.backSlotOf("pair_4"));
    assertNull(CaseAlgorithms.backSlotOf("oll_21"));
    assertEquals("pair_4", CaseAlgorithms.frontCode("pair_4_bl"));
    assertEquals("4", CaseAlgorithms.pairCase("pair_4_bl"));
    assertEquals("pair_a12_br", CaseAlgorithms.inBackSlot("pair_a12", "br"));
    assertTrue(CaseAlgorithms.isBasicPair("pair_4_br"));
    assertEquals("L U L'", CaseAlgorithms.shortestListed("pair_4_bl"));
    assertTrue(CaseAlgorithms.solves("pair_4_bl", "L U L'"));
    assertFalse(CaseAlgorithms.solves("pair_4_bl", "R U R'"));
    assertFalse(CaseAlgorithms.isWorthPointingOut("pair_4_bl", "R U R'"));
  }
}
