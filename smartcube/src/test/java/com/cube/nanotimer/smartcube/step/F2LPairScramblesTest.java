package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.model.CubeState;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

/**
 * The round trip with {@link F2LCases}: every scramble, applied to a solved cube, must be read back
 * as the case it was asked for, at front right with the cross down.
 */
public class F2LPairScramblesTest {

  @Test
  public void scramblesEveryCaseIntoItself() {
    Random random = new Random(11);
    for (String code : F2LPairScrambles.cases()) {
      for (int draw = 0; draw < 8; draw++) {
        String state = state(F2LPairScrambles.forCase(code, random));
        assertEquals(code, code.substring("pair_".length()),
            F2LCases.pairCase(state, Cubies.D, Cubies.DFR, Cubies.FR));
      }
    }
  }

  /** 41 basic and 51 advanced, the two sets the case reader names. */
  /** The basic cases and the stuck ones split the list, and undoing alone is still the case. */
  @Test
  public void basicCasesAreTheOnesNotStuck() {
    List<String> basic = F2LPairScrambles.basicCases();
    assertEquals(F2LPairScrambles.cases().size(),
        basic.size() + F2LPairScrambles.stuckCases().size());
    Random random = new Random(2);
    for (String code : basic) {
      assertFalse(code, F2LPairScrambles.stuckCases().contains(code));
      assertEquals(code, code.substring("pair_".length()), F2LCases.pairCase(
          state(F2LPairScrambles.undoing(code, random)), Cubies.D, Cubies.DFR, Cubies.FR));
    }
    assertNull(F2LPairScrambles.undoing("pll_t", random));
  }

  @Test
  public void dealsEveryNamedCase() {
    List<String> cases = F2LPairScrambles.cases();
    assertEquals(92, cases.size());
    assertEquals(51, F2LPairScrambles.stuckCases().size());
    assertEquals("pair_1", cases.get(0));
    assertEquals("pair_a1", cases.get(41));
    assertEquals(cases.size(), new HashSet<String>(cases).size());
  }

  /** A basic case moves only its own pair; a stuck one also disturbs the slot its piece is in. */
  @Test
  public void aBasicCaseLeavesTheOtherSlotsIn() {
    Random random = new Random(3);
    for (String code : F2LPairScrambles.cases()) {
      String state = state(F2LPairScrambles.forCase(code, random));
      boolean othersIn = slotIn(state, Cubies.DLF, Cubies.FL) && slotIn(state, Cubies.DBL, Cubies.BL)
          && slotIn(state, Cubies.DRB, Cubies.BR);
      assertEquals(code, !F2LPairScrambles.stuckCases().contains(code), othersIn);
    }
  }

  /** The last layer is scrambled, so a case cannot be read off a finished top. */
  @Test
  public void theLastLayerVaries() {
    Random random = new Random(5);
    Set<String> tops = new HashSet<String>();
    for (int i = 0; i < 20; i++) {
      tops.add(state(F2LPairScrambles.forCase("pair_a12", random)).substring(0, 9));
    }
    assertTrue(tops.size() > 10);
  }

  @Test
  public void aPairIsInOnlyWithItsCross() {
    String solved = CubeState.SOLVED_FACELETS;
    assertTrue(F2LPairScrambles.isPairIn(solved, 'D', "DFR", "FR"));
    assertTrue(F2LPairScrambles.isPairIn(solved, 'U', "RFU", "RF"));
    String state = state(F2LPairScrambles.forCase("pair_a12", new Random(1)));
    assertFalse(F2LPairScrambles.isPairIn(state, 'D', "DFR", "FR"));
    assertFalse(F2LPairScrambles.isPairIn(Notation.apply(solved, "F"), 'D', "DBL", "BL"));
  }

  /** A case with a piece in the last layer is met under four turns of it; one with none, once. */
  @Test
  public void countsEachCaseByItsPlacements() {
    int total = 0;
    for (String code : F2LPairScrambles.cases()) {
      int placements = F2LPairScrambles.placements(code);
      assertTrue(code + " " + placements, placements == 1 || placements == 4);
      total += placements;
    }
    assertTrue(total < 384);
    assertEquals(4, F2LPairScrambles.placements("pair_1"));
    assertEquals(1, F2LPairScrambles.placements("pair_a25")); // corner and edge both in slots
    assertEquals(0, F2LPairScrambles.placements("pll_t"));
  }

  @Test
  public void anUnknownCaseHasNoScramble() {
    assertNull(F2LPairScrambles.forCase("pair_99", new Random(1)));
    assertNull(F2LPairScrambles.forCase("pair_skip", new Random(1)));
    assertNull(F2LPairScrambles.forCase("pll_t", new Random(1)));
  }

  private static boolean slotIn(String state, int corner, int edge) {
    return Cubies.inPlace(state, Cubies.CORNERS[corner]) && Cubies.inPlace(state, Cubies.EDGES[edge]);
  }

  private static String state(String scramble) {
    return Notation.apply(CubeState.SOLVED_FACELETS, scramble);
  }
}
