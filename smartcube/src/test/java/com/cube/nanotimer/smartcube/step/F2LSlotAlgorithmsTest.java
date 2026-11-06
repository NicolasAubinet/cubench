package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class F2LSlotAlgorithmsTest {

  @Test
  public void everyBasicCaseHasOneForEachSlot() {
    List<String> missing = new ArrayList<String>();
    for (String name : F2LCases.named()) {
      for (String slot : F2LSlotAlgorithms.SLOTS) {
        if (F2LCases.isBasic(name) && F2LSlotAlgorithms.forCase(name, slot).isEmpty()) {
          missing.add(name + " " + slot);
        }
      }
    }
    assertEquals(new ArrayList<String>(), missing);
  }

  @Test
  public void everyFrontRowPutsItsPairInItsSlotWithoutTurningTheCube() {
    for (String name : F2LCases.named()) {
      for (String slot : new String[] {"fr", "fl"}) {
        for (String moves : F2LSlotAlgorithms.forCase(name, slot)) {
          assertTrue(name + " " + slot + ": " + moves,
              F2LSlotAlgorithms.solves(name, slot, moves));
          assertFalse(moves, moves.matches(".*[xyzuE].*"));
        }
      }
    }
  }

  /** Front left is the left hand's mirror of the front-right algorithm, never one with a y. */
  @Test
  public void frontLeftIsTheMirrorOfFrontRight() {
    assertEquals("L' U' L", F2LSlotAlgorithms.forCase("3", "fl").get(0));
    assertEquals(AlgorithmForm.mirroredNotation(F2LSlotAlgorithms.forCase("3", "fr").get(0)),
        F2LSlotAlgorithms.forCase("4", "fl").get(0));
    assertTrue(F2LSlotAlgorithms.solves("4", "fr", "R U R'"));
    assertFalse(F2LSlotAlgorithms.solves("4", "fl", "R U R'"));
    assertFalse(F2LSlotAlgorithms.solves("4", "front", "R U R'"));
  }

  @Test
  public void aFrontSlotCodeIsItsOwnCase() {
    assertEquals("fl", CaseAlgorithms.slotOf("pair_4_fl"));
    assertEquals("pair_4", CaseAlgorithms.frontCode("pair_4_fr"));
    assertTrue(CaseAlgorithms.isBasicPair("pair_4_fl"));
    assertTrue(CaseAlgorithms.solves("pair_4_fr", "R U R'"));
    assertFalse(CaseAlgorithms.solves("pair_4_fl", "R U R'"));
    assertFalse(CaseAlgorithms.isWorthPointingOut("pair_4_fl", "y' R U R'"));
  }
}
