package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class CmllCasesTest {

  @Test
  public void everyAlgorithmSolvesTheCaseItIsFiledUnder() {
    for (String[] row : CmllCaseAlgorithms.ALGORITHMS) {
      assertTrue(row[0] + ": " + row[1], CmllCases.solves(row[0], row[1]));
      assertTrue(row[1], LastLayerCaseAlgorithms.solves(row[0], row[1]));
    }
  }

  /** What a solve reads an execution against: each row, squared up with an AUF first, is itself. */
  @Test
  public void everyAlgorithmIsRecognisedFromItsOwnMoves() {
    for (String[] row : CmllCaseAlgorithms.ALGORITHMS) {
      assertNotNull(row[1], LastLayerCaseAlgorithms.matching(row[0], row[1]));
      assertNotNull(row[1], LastLayerCaseAlgorithms.matching(row[0], "U2 " + row[1]));
    }
    for (String code : CmllCases.codes()) {
      String listed = CaseAlgorithms.shown(code).get(0).getMoves();
      assertFalse(code, CaseAlgorithms.isWorthPointingOut(code, "U " + listed));
    }
  }

  @Test
  public void everyCaseIsDrawnAsItsCornersAlone() {
    for (String code : CmllCases.codes()) {
      LastLayerDiagram diagram = LastLayerDiagram.forCase(code);
      assertNotNull(code, diagram);
      assertTrue(diagram.isCornersOnly());
      assertFalse(diagram.isPermutation());
      assertEquals(LastLayerDiagram.BLANK, diagram.sideFace(1)); // the back edge
      assertFalse(CaseAlgorithms.shown(code).isEmpty());
    }
  }

  @Test
  public void everyCaseHasAlgorithms() {
    for (String code : CmllCases.codes()) {
      assertEquals(code, 4, LastLayerCaseAlgorithms.forCase(code).size());
    }
    assertEquals(42, CmllCases.codes().size());
  }

  /** Every arrangement of the four corners, placed and twisted, is one of the 42 cases or a skip. */
  @Test
  public void theKeysCoverEveryCornerState() {
    Set<String> seen = new HashSet<String>();
    int states = 0;
    for (int[] corners : permutations()) {
      for (int twists = 0; twists < 27; twists++) {
        int[] twist = {twists % 3, twists / 3 % 3, twists / 9, 0};
        twist[3] = (6 - twist[0] - twist[1] - twist[2]) % 3;
        String name = CmllCases.of(state(corners, twist));
        assertNotNull(java.util.Arrays.toString(corners) + java.util.Arrays.toString(twist), name);
        seen.add(name);
        states++;
      }
    }
    assertEquals(648, states);
    assertEquals(43, seen.size());
    assertTrue(seen.contains(CmllCases.SKIP));
  }

  @Test
  public void anAlgorithmMayLeaveTheSliceTurned() {
    // T Left Bar's r' without its r: the centres end a quarter turn round, the corners solved.
    assertTrue(CmllCases.solves("cmll_tleftbar", "R U R' U' r' F R F'"));
    assertTrue(CmllCases.solves("cmll_tleftbar", "U' R U R' U' R' F R F'"));
    assertFalse(CmllCases.solves("cmll_trightbar", "R U R' U' R' F R F'"));
  }

  @Test
  public void aStateWithTheBlocksOutIsNoCase() {
    assertNull(CmllCases.of(Notation.apply(Cubies.SOLVED, "R U R' F")));
    assertEquals(CmllCases.SKIP, CmllCases.of(Notation.apply(Cubies.SOLVED, "U M2 U'")));
  }

  @Test
  public void namesComeFromTheTable() {
    assertEquals("Sune Left Bar", CmllCases.fullName("cmll_suneleftbar"));
    assertEquals("Anti Sune", CmllCases.group("cmll_antisunex"));
    assertEquals("X", CmllCases.variant("cmll_antisunex"));
    assertTrue(CmllCases.isCase("cmll_skip"));
    assertFalse(CmllCases.isCase("cmll_orient"));
    assertEquals(8, CmllCases.groups().size());
  }

  private static String state(int[] corners, int[] twist) {
    char[] state = Cubies.SOLVED.toCharArray();
    for (int slot = 0; slot < 4; slot++) {
      for (int j = 0; j < 3; j++) {
        state[Cubies.CORNERS[slot][(j + twist[slot]) % 3]] =
            Cubies.SOLVED.charAt(Cubies.CORNERS[corners[slot]][j]);
      }
    }
    return new String(state);
  }

  private static java.util.List<int[]> permutations() {
    java.util.List<int[]> all = new java.util.ArrayList<int[]>();
    for (int a = 0; a < 4; a++) {
      for (int b = 0; b < 4; b++) {
        for (int c = 0; c < 4; c++) {
          int d = 6 - a - b - c;
          if (a != b && a != c && b != c && d >= 0 && d < 4 && d != a && d != b && d != c) {
            all.add(new int[] {a, b, c, d});
          }
        }
      }
    }
    return all;
  }
}
