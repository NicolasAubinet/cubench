package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CaseAlgorithmsTest {

  @Test
  public void pointsOutALastLayerAlgorithmHardlyAnybodyUses() {
    assertTrue(CaseAlgorithms.isWorthPointingOut("pll_jb", "R U2 R' U' R U2 L' U R' U' L"));
    assertFalse(CaseAlgorithms.isWorthPointingOut("pll_jb", "R U R' F' R U R' U' R' F R2 U' R'"));
  }

  @Test
  public void pointsOutAPairTurnedWithNoneOfItsAlgorithms() {
    assertTrue(CaseAlgorithms.isWorthPointingOut("pair_4", "R U2 R' U' R U R'"));
    assertFalse(CaseAlgorithms.isWorthPointingOut("pair_4", "R U R'"));
    assertFalse(CaseAlgorithms.isWorthPointingOut("pair_6", "R' U' R U' F' U F")); // a free slot
  }

  @Test
  public void showsAMirrorTheWayItWasTurned() {
    assertEquals("y M' U M2 U M2 U M' U2 M2",
        CaseAlgorithms.asAlgorithm("pll_z", "M' U M2 U M2 U M' U2 M2"));
  }

  @Test
  public void showsAMirrorFoldedIntoARowInTheHandItWasTurnedWith() {
    assertEquals("y R U' R U R' D R D' R U' D R2 U R2 D' R2",
        CaseAlgorithms.asAlgorithm("pll_v", "R U' R U R' D R D' R U' D R2 U R2 D' R2"));
  }

  @Test
  public void showsASpellingFoldedIntoARowOnTheFacesItWasTurnedOn() {
    assertEquals("R U B' U' R' U R B R'",
        CaseAlgorithms.asAlgorithm("oll_32", "R U B' U' R' U R B R'"));
  }

  @Test
  public void writesAMirrorTokenForToken() {
    assertEquals("l' U' L2 M x Lw'", AlgorithmForm.mirroredNotation("r U	R2 M x Rw"));
  }

  @Test
  public void pointsOutNothingWithoutACaseOrMoves() {
    assertFalse(CaseAlgorithms.isWorthPointingOut(null, "R U R'"));
    assertFalse(CaseAlgorithms.isWorthPointingOut("oll_27", " "));
  }
}
