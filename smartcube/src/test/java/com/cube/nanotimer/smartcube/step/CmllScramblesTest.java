package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.step.LastLayerAlgorithms;
import org.junit.Test;

/** A CMLL drill's scrambles, read back by {@link CmllCases} with the blocks on their centres. */
public class CmllScramblesTest {

  @Test
  public void scramblesEveryCaseIntoItself() {
    for (String code : LastLayerScrambles.cmllCases()) {
      for (int before = 0; before < 4; before++) {
        for (int after = 0; after < 4; after++) {
          String scramble = LastLayerScrambles.forCase(code, before, after);
          assertEquals(code, code, CmllCases.PREFIX + CmllCases.of(state(scramble)));
        }
      }
    }
  }

  /** Every row at its own angle, as practice deals it, those ending on a turned slice included. */
  @Test
  public void dealsEveryAlgorithmAtItsOwnAngleWithTheBlocksOnTheirCentres() {
    for (String code : LastLayerScrambles.cmllCases()) {
      for (LastLayerCaseAlgorithms.Algorithm algorithm : LastLayerCaseAlgorithms.forCase(code)) {
        String scramble = LastLayerScrambles.forCmllAlgorithm(
            LastLayerScrambles.withoutLeadingY(algorithm.getMoves()));
        assertEquals(algorithm.getMoves(), code, CmllCases.PREFIX + CmllCases.of(state(scramble)));
      }
    }
  }

  /** At its own angle the algorithm finishes the corners with no turn of the layer after it. */
  @Test
  public void everyAlgorithmFinishesItsOwnDealWithNoAuf() {
    for (String code : LastLayerScrambles.cmllCases()) {
      for (LastLayerCaseAlgorithms.Algorithm algorithm : LastLayerCaseAlgorithms.forCase(code)) {
        String moves = LastLayerScrambles.withoutLeadingY(algorithm.getMoves())
            .replace("(", "").replace(")", "");
        String scramble = LastLayerScrambles.forCmllAlgorithm(moves);
        assertTrue(algorithm.getMoves(), blocksAndCornersSolved(state(scramble + " " + moves)));
      }
    }
  }

  /** Corners and block edges solved as a whole cube turned some way, the slice not read. */
  private static boolean blocksAndCornersSolved(String state) {
    int[][] edges = {Cubies.EDGES[Cubies.DL], Cubies.EDGES[Cubies.FL], Cubies.EDGES[Cubies.BL],
        Cubies.EDGES[Cubies.DR], Cubies.EDGES[Cubies.FR], Cubies.EDGES[Cubies.BR]};
    for (int rotation = 0; rotation < FaceletRotations.COUNT; rotation++) {
      boolean solved = true;
      for (int[] corner : Cubies.CORNERS) {
        for (int facelet : corner) {
          solved &= state.charAt(FaceletRotations.apply(rotation, facelet))
              == Cubies.SOLVED.charAt(facelet);
        }
      }
      for (int[] edge : edges) {
        for (int facelet : edge) {
          solved &= state.charAt(FaceletRotations.apply(rotation, facelet))
              == Cubies.SOLVED.charAt(facelet);
        }
      }
      if (solved) {
        return true;
      }
    }
    return false;
  }

  @Test
  public void putsTheBlocksBackOnTheCentresWithFaceTurnsOnly() {
    String scramble = LastLayerScrambles.forCmllAlgorithm("R' U r U2 R2 F R F' r");
    assertEquals("cmll_tbottomrow", CmllCases.PREFIX + CmllCases.of(state(scramble)));
    assertFalse(scramble, scramble.matches(".*[a-zMESxyz].*"));
  }

  /** The coach reads the OLL/PLL table's codes, which must never name a CMLL. */
  @Test
  public void keepsCmllOutOfTheCoachTable() {
    for (String code : LastLayerScrambles.cmllCases()) {
      assertFalse(code, LastLayerAlgorithms.dealsCase(code));
      assertFalse(code, LastLayerScrambles.cases().contains(code));
    }
  }

  @Test
  public void isDoneReadsCornersAgainstTheBlocksWhateverTheSliceAndLayer() {
    String solved = Cubies.SOLVED;
    assertEquals(true, CmllCases.isDone(solved, "U"));
    assertEquals(true, CmllCases.isDone(Notation.apply(solved, "M U M' U2"), "U"));
    assertEquals(true, CmllCases.isDone(Notation.apply(solved, "R L'"), "U"));
    assertEquals(true, CmllCases.isDone(Notation.apply(solved, "R L' B"), "U"));
    assertEquals(true, CmllCases.isDone(Notation.apply(solved, "z2"), "D"));
    assertFalse(CmllCases.isDone(state(LastLayerScrambles.forCase("cmll_hrows", 0, 0)), "U"));
    assertFalse(CmllCases.isDone(Notation.apply(solved, "R U R'"), "U"));
  }

  private static String state(String scramble) {
    return Notation.apply(Cubies.SOLVED, scramble);
  }
}
