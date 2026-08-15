package com.cube.nanotimer.smartcube.cube;

import static org.junit.Assert.assertEquals;

import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import org.junit.Test;

public class StopPenaltyTest {

  /** The state a solved cube is left in by a sequence, which is what a stop is judged on. */
  private static CubeState state(String moves) {
    CubieCube c = new CubieCube();
    for (String mv : moves.trim().split("\\s+")) {
      if (mv.isEmpty()) {
        continue;
      }
      Face face = Face.valueOf(mv.substring(0, 1));
      if (mv.endsWith("'")) {
        c.applyMove(face, true);
      } else if (mv.endsWith("2")) {
        c.applyMove(face, false);
        c.applyMove(face, false);
      } else {
        c.applyMove(face, false);
      }
    }
    return new CubeState(c.toFaceCube());
  }

  private static void assertPlusTwo(String movesLeftUndone) {
    assertEquals(movesLeftUndone, StopPenalty.Type.PLUS_TWO,
        StopPenalty.of(state(movesLeftUndone)).getType());
  }

  private static void assertDnf(String moves) {
    assertEquals(moves, StopPenalty.Type.DNF, StopPenalty.of(state(moves)).getType());
  }

  @Test
  public void solvedEarnsNothing() {
    assertEquals(StopPenalty.Type.NONE, StopPenalty.of(CubeState.SOLVED).getType());
  }

  @Test
  public void oneOuterTurnOnAnyFaceIsAPlusTwo() {
    for (Face face : Face.values()) {
      assertPlusTwo(face.name());
      assertPlusTwo(face.name() + "'");
      assertPlusTwo(face.name() + "2");
    }
  }

  @Test
  public void aHalfTurnIsStillOneMove() {
    assertPlusTwo("U2");
  }

  @Test
  public void twoTurnedFacesAreTwoMoves() {
    assertDnf("U D");
    assertDnf("U R");
  }

  @Test
  public void aSliceLeftOffIsTwoInterfaces() {
    assertDnf("R L'"); // both outer layers turned the same way in space: the M slice left behind
    assertDnf("R2 L2");
  }

  @Test
  public void anUnfinishedSolveIsADnf() {
    assertDnf("R U R' U' R' F R2 U' R' U' R U R' F'"); // T-perm never executed
    assertDnf("R U R' U R U2 R'"); // nor the last OLL
    assertDnf("U R2 F B R B2 R U2 L B2 R U' D' R2 F R' L B2 U2 F2");
  }

  /** What a blind solve is left in by a piece shot to the wrong sticker: home, and turned. */
  @Test
  public void piecesTurnedWhereTheyStandAreADnf() {
    assertEquals(StopPenalty.Type.DNF, StopPenalty.of(everythingHome(1, 2, 0)).getType());
    assertEquals(StopPenalty.Type.DNF, StopPenalty.of(everythingHome(0, 0, 2)).getType());
  }

  /** Every piece in its own slot, with the first corners twisted and the first edges flipped. */
  private static CubeState everythingHome(int firstTwist, int secondTwist, int flippedEdges) {
    int[] cp = new int[8];
    int[] co = new int[8];
    int[] ep = new int[12];
    int[] eo = new int[12];
    for (int i = 0; i < 8; i++) {
      cp[i] = i;
    }
    for (int i = 0; i < 12; i++) {
      ep[i] = i;
      eo[i] = i < flippedEdges ? 1 : 0;
    }
    co[0] = firstTwist;
    co[1] = secondTwist;
    CubieCube c = new CubieCube();
    c.fromPermutation(cp, co, ep, eo);
    return new CubeState(c.toFaceCube());
  }

  @Test
  public void aCoreTurnedDuringTheSolveStillReadsAsSolved() {
    // Every face matching its own centre, in a frame the solve rotated: solved, and no string
    // comparison against the solved facelets would say so.
    CubeState rotated = new CubeState(
        "FFFFFFFFF" + "RRRRRRRRR" + "DDDDDDDDD" + "BBBBBBBBB" + "LLLLLLLLL" + "UUUUUUUUU");
    assertEquals(StopPenalty.Type.NONE, StopPenalty.of(rotated).getType());
  }

  @Test
  public void anUnreadableStateEarnsNothing() {
    assertEquals(StopPenalty.Type.NONE, StopPenalty.of(null).getType());
    assertEquals(StopPenalty.Type.NONE, StopPenalty.of(new CubeState("nonsense")).getType());
    assertEquals(StopPenalty.Type.NONE,
        StopPenalty.of(new CubeState(CubeState.SOLVED_FACELETS.replace('U', 'R'))).getType());
  }
}
