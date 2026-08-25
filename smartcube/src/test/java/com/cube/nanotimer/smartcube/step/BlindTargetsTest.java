package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.Face;
import org.junit.Test;

/** The naming on states put together by hand, where a recorded solve happens not to reach one. */
public class BlindTargetsTest {

  /** Swaps the {@code UR} and {@code UL} edges and two corners with them, and nothing else. */
  private static final String T_PERM = "R U R' U' R' F R2 U' R' U' R U R' F'";

  /**
   * A cycle closing on its one target with nothing else of the type out is said as that target
   * alone. There is nowhere to break into: what was owed really was the single shot, for a last
   * algorithm or the parity to carry.
   *
   * <p>The mirror of the marked case, and the reason the mark is not simply put on every closing
   * cycle. A parity state is the only shape a lone pair takes on a legal cube, so it is written as
   * one — two edges swapped and two corners with them.
   */
  @Test
  public void saysTheOneTargetAloneWhereNothingIsLeftToBreakInto() {
    BlindTargets targets = new BlindTargets(FaceletRotations.IDENTITY);
    String parity = after(T_PERM);

    assertEquals("UR-UL", targets.wantedName(parity, Cubies.UR));
    assertEquals("UFR-UBR", targets.wantedName(parity, Cubies.EDGES.length)); // the URF corner
  }

  private static String after(String moves) {
    CubieCube cube = new CubieCube();
    for (String token : moves.split("\\s+")) {
      Face face = Face.valueOf(token.substring(0, 1));
      for (int i = 0; i < (token.endsWith("2") ? 2 : 1); i++) {
        cube.applyMove(face, token.endsWith("'"));
      }
    }
    return cube.toFaceCube();
  }
}
