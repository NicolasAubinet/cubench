package com.cube.nanotimer.scrambler.randomstate;

import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.ScrambleType;
import com.cube.nanotimer.vo.ThreeCubeState;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RouxSecondBlockScrambleTest {

  /** The scramble is only valid if the Roux first block it leaves alone really is the DBL/DFL corners and the BL, FL and DL edges. */
  @Test
  public void firstBlockStaysSolved() {
    ScrambleType type = CubeType.THREE_BY_THREE.getScrambleTypeFromString("roux_second_block");
    Assert.assertNotNull(type);
    RSThreeScrambler scrambler = new RSThreeScrambler();
    scrambler.genTables();
    for (int i = 0; i < 10; i++) {
      ThreeCubeState state = apply(scrambler.getNewScramble(new ScrambleConfig(23, type)));
      for (int c : new int[] { 4, 5 }) {
        Assert.assertEquals("corner " + c, c, state.cornerPermutations[c]);
        Assert.assertEquals("corner orient " + c, 0, state.cornerOrientations[c]);
      }
      for (int e : new int[] { 2, 3, 11 }) {
        Assert.assertEquals("edge " + e, e, state.edgePermutations[e]);
        Assert.assertEquals("edge orient " + e, 0, state.edgeOrientations[e]);
      }
    }
  }

  private ThreeCubeState apply(String[] scramble) {
    ThreeCubeState state = new ThreeCubeState();
    for (byte i = 0; i < 8; i++) {
      state.cornerPermutations[i] = i;
    }
    for (byte i = 0; i < 12; i++) {
      state.edgePermutations[i] = i;
    }
    for (String m : scramble) {
      Move move = null;
      for (Move candidate : Move.values()) {
        if (candidate.name.equals(m)) {
          move = candidate;
        }
      }
      Assert.assertNotNull("unknown move " + m, move);
      state.edgePermutations = StateTables.getPermResult(state.edgePermutations, move.edgPerm);
      state.cornerPermutations = StateTables.getPermResult(state.cornerPermutations, move.corPerm);
      state.edgeOrientations = StateTables.getOrientResult(state.edgeOrientations, move.edgPerm, move.edgOrient, 2);
      state.cornerOrientations = StateTables.getOrientResult(state.cornerOrientations, move.corPerm, move.corOrient, 3);
    }
    return state;
  }
}
