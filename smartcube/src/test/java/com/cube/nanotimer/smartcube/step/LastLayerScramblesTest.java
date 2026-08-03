package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.model.CubeState;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

/**
 * What pins the scrambles down: each one is applied to a solved cube and handed to
 * {@link LastLayerCases}, which must name the case it was asked for. Naming and scrambling read the
 * same table in opposite directions, so this is the round trip — and since the naming is itself
 * pinned against every state the last layer has, neither side can drift without the other noticing.
 */
public class LastLayerScramblesTest {

  /** The last layer is up, so the cross is on D. */
  private static final int CROSS = Cubies.D;

  @Test
  public void scramblesEveryCaseIntoItself() {
    for (String code : LastLayerScrambles.cases()) {
      for (int before = 0; before < 4; before++) {
        for (int after = 0; after < 4; after++) {
          String scramble = LastLayerScrambles.forCase(code, before, after);
          assertEquals(code, code, nameOf(code, state(scramble)));
        }
      }
    }
  }

  @Test
  public void leavesTheFirstTwoLayersStanding() {
    Set<Integer> lastLayer = lastLayerFacelets();
    for (String code : LastLayerScrambles.cases()) {
      String state = state(LastLayerScrambles.forCase(code, 1, 3));
      for (int facelet = 0; facelet < state.length(); facelet++) {
        if (!lastLayer.contains(facelet)) {
          assertEquals(code + " moved facelet " + facelet,
              Cubies.SOLVED.charAt(facelet), state.charAt(facelet));
        }
      }
    }
  }

  /**
   * The scramble is the case's own algorithm undone, so executing it ends on a solved cube — an OLL
   * on a skip rather than on a permutation still to solve. Read without the alignment turns, which
   * only move last-layer pieces around and so cannot make a skip of anything else.
   */
  @Test
  public void endsSolvedWhenTheAlgorithmIsExecuted() {
    for (String code : LastLayerScrambles.cases()) {
      String state = state(LastLayerScrambles.forCase(code, 0, 0));
      assertTrue(code, isSolved(Notation.apply(state, algorithmFor(code))));
    }
  }

  /**
   * The alignment turns have to be doing something. Four states is the least a case can have: one
   * whose pattern repeats every quarter turn, like the H perm, has no more.
   */
  @Test
  public void alignsTheCaseFourWaysOrMore() {
    for (String code : LastLayerScrambles.cases()) {
      Set<String> states = new HashSet<String>();
      for (int before = 0; before < 4; before++) {
        for (int after = 0; after < 4; after++) {
          states.add(state(LastLayerScrambles.forCase(code, before, after)));
        }
      }
      assertTrue(code + " has " + states.size() + " states", states.size() >= 4);
    }
  }

  /** A scramble is turns of a cube standing still: a rotation in the algorithm is folded away. */
  @Test
  public void writesNoRotationIntoTheScramble() {
    for (String code : LastLayerScrambles.cases()) {
      String scramble = LastLayerScrambles.forCase(code, 3, 2);
      for (String move : scramble.split("\\s+")) {
        assertTrue(code + ": " + scramble, "xyz".indexOf(move.charAt(0)) < 0);
      }
    }
  }

  @Test
  public void drawsItsAlignmentTurnsAtRandom() {
    Random random = new Random(20260803L);
    Set<String> states = new HashSet<String>();
    for (int draw = 0; draw < 200; draw++) {
      String scramble = LastLayerScrambles.forCase("pll_t", random);
      assertEquals(scramble, "t", LastLayerCases.permutation(state(scramble), CROSS));
      states.add(state(scramble));
    }
    assertTrue("only " + states.size() + " states drawn", states.size() >= 4);
  }

  /** The codes are the ones a solve is recorded under, or a weakness could not be handed back. */
  @Test
  public void namesCasesTheWayASolveDoes() {
    CFOPStepDetector solve = new CFOPStepDetector();
    List<String> cases = LastLayerScrambles.cases();
    assertEquals(57 + 21, cases.size());
    assertTrue(cases.contains(solve.stepName(CFOPStepDetector.OLL) + "_21"));
    assertTrue(cases.contains(solve.stepName(CFOPStepDetector.PLL) + "_ga"));
  }

  @Test
  public void answersWithNothingForWhatItHasNoCaseFor() {
    assertNull(LastLayerScrambles.forCase(null, 0, 0));
    assertNull(LastLayerScrambles.forCase("pll", 0, 0));
    assertNull(LastLayerScrambles.forCase("pll_zz", 0, 0));
    assertNull(LastLayerScrambles.forCase("oll_58", 0, 0));
    assertNull(LastLayerScrambles.forCase("f2l_rf", 0, 0));
    assertNull(LastLayerScrambles.forCase("pll_ga_2", 0, 0));
  }

  private static String state(String scramble) {
    return Notation.apply(CubeState.SOLVED_FACELETS, scramble);
  }

  private static String algorithmFor(String code) {
    String[][] cases = code.startsWith("oll_")
        ? LastLayerAlgorithms.ORIENTATIONS : LastLayerAlgorithms.PERMUTATIONS;
    return LastLayerAlgorithms.algorithm(cases, code.substring(code.indexOf('_') + 1));
  }

  /** Solved, wherever the cube happens to be standing: an algorithm may leave it turned. */
  private static boolean isSolved(String state) {
    for (int face = 0; face < 6; face++) {
      for (int facelet = 1; facelet < 9; facelet++) {
        if (state.charAt(face * 9 + facelet) != state.charAt(face * 9)) {
          return false;
        }
      }
    }
    return true;
  }

  private static String nameOf(String code, String state) {
    return code.startsWith("oll_")
        ? "oll_" + LastLayerCases.orientation(state, CROSS)
        : "pll_" + LastLayerCases.permutation(state, CROSS);
  }

  /** The facelets of the pieces a last-layer case is allowed to have moved. */
  private static Set<Integer> lastLayerFacelets() {
    Set<Integer> facelets = new HashSet<Integer>();
    for (int slot = 0; slot < 4; slot++) {
      for (int facelet : Cubies.EDGES[slot]) {
        facelets.add(facelet);
      }
      for (int facelet : Cubies.CORNERS[slot]) {
        facelets.add(facelet);
      }
    }
    return facelets;
  }
}
