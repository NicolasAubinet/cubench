package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/**
 * The picture has to be the case. Since it is read off the algorithm shown beside it, what is worth
 * checking is that it is read off correctly: an OLL shows exactly the stickers that are not yet
 * facing up, a PLL shows a layer that is all one colour, and every arrow in one points at the place
 * the piece it names actually belongs. That the algorithms agree with it is
 * {@code LastLayerCaseAlgorithmsTest}'s business.
 */
public class LastLayerDiagramTest {

  private static final int[] CORNERS = {0, 2, 6, 8};
  private static final int[] EDGES = {1, 3, 5, 7};

  /** The permutations named below, so a case cannot be added and left unnamed. */
  private final Set<String> checked = new HashSet<String>();

  @Test
  public void drawsEveryCase() {
    for (String code : LastLayerScrambles.cases()) {
      assertNotNull(code, LastLayerDiagram.forCase(code));
    }
  }

  @Test
  public void hasNoPictureForSomethingThatIsNotACase() {
    assertNull(LastLayerDiagram.forCase("pll_zz"));
    assertNull(LastLayerDiagram.forCase("skip"));
    assertNull(LastLayerDiagram.forCase(null));
  }

  @Test
  public void leavesAPermutationOriented() {
    for (String code : LastLayerScrambles.cases()) {
      if (!code.startsWith("pll_")) {
        continue;
      }
      LastLayerDiagram diagram = LastLayerDiagram.forCase(code);
      assertTrue(diagram.isPermutation());
      for (int cell = 0; cell < 9; cell++) {
        assertTrue(code + " cell " + cell, diagram.isOriented(cell));
      }
      for (int side = 0; side < 12; side++) {
        assertFalse(code + " side " + side, diagram.sideFace(side) == 'U');
      }
    }
  }

  /** Follow every arrow and the layer is solved: each piece lands where its colours say it goes. */
  @Test
  public void pointsEveryPieceHome() {
    for (String code : LastLayerScrambles.cases()) {
      if (!code.startsWith("pll_")) {
        continue;
      }
      LastLayerDiagram diagram = LastLayerDiagram.forCase(code);
      Set<Integer> arrivedAt = new HashSet<Integer>();
      for (int cell = 0; cell < 9; cell++) {
        if (cell == 4) {
          continue;
        }
        int home = diagram.arrow(cell);
        assertTrue(code + " cell " + cell, arrivedAt.add(home));
        for (int side = 0; side < 12; side++) {
          if (diagram.sideCell(side) != cell) {
            continue;
          }
          assertTrue(code + " side " + side + " goes to " + home,
              facesOfCell(home).indexOf(diagram.sideFace(side)) >= 0);
        }
      }
    }
  }

  /**
   * What each permutation moves, which is how a cuber names it: a Z swaps two pairs of edges and
   * leaves every corner alone, a G cycles three of each, an A only cycles corners. Counted rather
   * than drawn out, because a case drawn a quarter turn from where it is taught comes out as a
   * different pair of counts entirely, and that is the whole of what this is watching for.
   */
  @Test
  public void movesWhatEachPermutationIsKnownToMove() {
    moves("pll_aa", 3, 0);
    moves("pll_ab", 3, 0);
    moves("pll_e", 4, 0);
    moves("pll_f", 2, 2);
    moves("pll_ga", 3, 3);
    moves("pll_gb", 3, 3);
    moves("pll_gc", 3, 3);
    moves("pll_gd", 3, 3);
    moves("pll_h", 0, 4);
    moves("pll_ja", 2, 2);
    moves("pll_jb", 2, 2);
    moves("pll_na", 2, 2);
    moves("pll_nb", 2, 2);
    moves("pll_ra", 2, 2);
    moves("pll_rb", 2, 2);
    moves("pll_t", 2, 2);
    moves("pll_ua", 0, 3);
    moves("pll_ub", 0, 3);
    moves("pll_v", 2, 2);
    moves("pll_y", 2, 2);
    moves("pll_z", 0, 4);
    for (String code : LastLayerScrambles.cases()) {
      if (code.startsWith("pll_")) {
        assertTrue(code, checked.contains(code));
      }
    }
  }

  private void moves(String code, int corners, int edges) {
    LastLayerDiagram diagram = LastLayerDiagram.forCase(code);
    assertNotNull(code, diagram);
    assertEquals(code + " corners", corners, moved(diagram, CORNERS));
    assertEquals(code + " edges", edges, moved(diagram, EDGES));
    checked.add(code);
  }

  /**
   * A Gb undoes a Ga, a Gd undoes a Gc, and an A or a U undoes the other of its pair. Two pictures
   * of a pair drawn a quarter turn out of step with each other still show the right pieces moving
   * the right number of places, so this is what says they are drawn where the case is taught.
   */
  @Test
  public void drawsEachPairOfCasesAsOneUndoingTheOther() {
    undoes("pll_ga", "pll_gb");
    undoes("pll_gc", "pll_gd");
    undoes("pll_aa", "pll_ab");
    undoes("pll_ua", "pll_ub");
  }

  private static void undoes(String code, String other) {
    int[] reversed = new int[9];
    LastLayerDiagram diagram = LastLayerDiagram.forCase(code);
    for (int cell = 0; cell < 9; cell++) {
      reversed[diagram.arrow(cell)] = cell;
    }
    LastLayerDiagram theirs = LastLayerDiagram.forCase(other);
    int[] arrows = new int[9];
    for (int cell = 0; cell < 9; cell++) {
      arrows[cell] = theirs.arrow(cell);
    }
    for (int quarters = 0; quarters < 4; quarters++) {
      if (Arrays.equals(reversed, arrows)) {
        return;
      }
      reversed = turned(reversed);
    }
    fail(code + " is not what " + other + " undoes");
  }

  /** The same arrows with the cube held a quarter turn round. */
  private static int[] turned(int[] arrows) {
    int[] round = {2, 5, 8, 1, 4, 7, 0, 3, 6};
    int[] moved = new int[9];
    for (int cell = 0; cell < 9; cell++) {
      moved[round[cell]] = round[arrows[cell]];
    }
    return moved;
  }

  /** An oriented layer is one no arrow leaves, which is what a solved permutation looks like. */
  @Test
  public void movesNothingInAnOrientation() {
    LastLayerDiagram sune = LastLayerDiagram.forCase("oll_27");
    assertFalse(sune.isPermutation());
    for (int cell = 0; cell < 9; cell++) {
      assertEquals(cell, sune.arrow(cell));
    }
  }

  /** Sune: three corners twisted, the edges already up, and its side stickers say which way. */
  @Test
  public void drawsSune() {
    LastLayerDiagram sune = LastLayerDiagram.forCase("oll_27");
    assertEquals("- + -\n+ + +\n+ + -", topOf(sune));
    assertEquals(3, unorientedCorners(sune));
    assertEquals(0, unorientedEdges(sune));
  }

  /** The dot cases are the ones with no edge facing up at all, and there are eight of them. */
  @Test
  public void drawsTheDots() {
    int dots = 0;
    for (String code : LastLayerScrambles.cases()) {
      if (!code.startsWith("oll_")) {
        continue;
      }
      LastLayerDiagram diagram = LastLayerDiagram.forCase(code);
      if (unorientedEdges(diagram) == 4) {
        dots++;
        assertEquals(code, "Dot", LastLayerCaseNames.shape(code));
      }
    }
    assertEquals(8, dots);
  }

  @Test
  public void namesEveryCase() {
    for (String code : LastLayerScrambles.cases()) {
      String name = LastLayerCaseNames.shortName(code);
      assertNotNull(code, name);
      assertFalse(code, name.isEmpty());
      if (code.startsWith("oll_")) {
        assertNotNull(code, LastLayerCaseNames.shape(code));
      } else {
        assertNull(code, LastLayerCaseNames.shape(code));
      }
    }
    assertEquals("Ga", LastLayerCaseNames.shortName("pll_ga"));
    assertEquals("21", LastLayerCaseNames.shortName("oll_21"));
    assertEquals("Sune", LastLayerCaseNames.shape("oll_27"));
  }

  private static int moved(LastLayerDiagram diagram, int[] cells) {
    int count = 0;
    for (int cell : cells) {
      if (diagram.arrow(cell) != cell) {
        count++;
      }
    }
    return count;
  }

  /** The faces a cell's stickers show when the piece in it is home. */
  private static String facesOfCell(int cell) {
    String[] faces = {"BL", "B", "BR", "L", "", "R", "FL", "F", "FR"};
    return faces[cell];
  }

  private static String topOf(LastLayerDiagram diagram) {
    StringBuilder drawn = new StringBuilder();
    for (int cell = 0; cell < 9; cell++) {
      if (cell > 0) {
        drawn.append(cell % 3 == 0 ? "\n" : " ");
      }
      drawn.append(diagram.isOriented(cell) ? '+' : '-');
    }
    return drawn.toString();
  }

  private static int unorientedCorners(LastLayerDiagram diagram) {
    int count = 0;
    for (int cell : new int[] {0, 2, 6, 8}) {
      if (!diagram.isOriented(cell)) {
        count++;
      }
    }
    return count;
  }

  private static int unorientedEdges(LastLayerDiagram diagram) {
    int count = 0;
    for (int cell : new int[] {1, 3, 5, 7}) {
      if (!diagram.isOriented(cell)) {
        count++;
      }
    }
    return count;
  }
}
