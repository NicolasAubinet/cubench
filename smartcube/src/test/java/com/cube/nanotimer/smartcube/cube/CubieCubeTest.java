package com.cube.nanotimer.smartcube.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.model.Face;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

/**
 * Ports the sibling package's cubie_cube_test.dart. Reference facelets come from
 * csTimer's mathlib.CubieCube (the ported source), pinning this port to the reference.
 */
public class CubieCubeTest {

  private static final Map<String, String> VECTORS = buildVectors();

  private static Map<String, String> buildVectors() {
    Map<String, String> v = new LinkedHashMap<>();
    v.put("", CubieCube.SOLVED_FACELET);
    v.put("U", "UUUUUUUUUBBBRRRRRRRRRFFFFFFDDDDDDDDDFFFLLLLLLLLLBBBBBB");
    v.put("R", "UUFUUFUUFRRRRRRRRRFFDFFDFFDDDBDDBDDBLLLLLLLLLUBBUBBUBB");
    v.put("F", "UUUUUULLLURRURRURRFFFFFFFFFRRRDDDDDDLLDLLDLLDBBBBBBBBB");
    v.put("D", "UUUUUUUUURRRRRRFFFFFFFFFLLLDDDDDDDDDLLLLLLBBBBBBBBBRRR");
    v.put("L", "BUUBUUBUURRRRRRRRRUFFUFFUFFFDDFDDFDDLLLLLLLLLBBDBBDBBD");
    v.put("B", "RRRUUUUUURRDRRDRRDFFFFFFFFFDDDDDDLLLULLULLULLBBBBBBBBB");
    v.put("U'", "UUUUUUUUUFFFRRRRRRLLLFFFFFFDDDDDDDDDBBBLLLLLLRRRBBBBBB");
    v.put("R'", "UUBUUBUUBRRRRRRRRRFFUFFUFFUDDFDDFDDFLLLLLLLLLDBBDBBDBB");
    v.put("B2", "DDDUUUUUURRLRRLRRLFFFFFFFFFDDDDDDUUURLLRLLRLLBBBBBBBBB");
    v.put("U R2 F B R B2 R U2 L B2 R U' D' R2 F R' L B2 U2 F2",
        "UBULURUFURURFRBRDRFUFLFRFDFDFDLDRDBDLULBLFLDLBUBRBLBDB");
    v.put("R U2 F' L D B2 R' U L2 F D' B U R' F2 L2 D2 R2 U",
        "RLLFUDFRRUBDFRBBRLRUBLFUUFRLRDLDDLBBFDDLLBFDBFUUUBFDRU");
    return v;
  }

  private static Face faceOf(char c) {
    switch (c) {
      case 'U': return Face.U;
      case 'R': return Face.R;
      case 'F': return Face.F;
      case 'D': return Face.D;
      case 'L': return Face.L;
      case 'B': return Face.B;
      default: throw new IllegalArgumentException("Bad face: " + c);
    }
  }

  /** Apply a WCA-notation scramble to a fresh solved cube via {@link CubieCube#applyMove}. */
  private static CubieCube scramble(String seq) {
    CubieCube c = new CubieCube();
    for (String mv : seq.trim().split("\\s+")) {
      if (mv.isEmpty()) {
        continue;
      }
      Face face = faceOf(mv.charAt(0));
      if (mv.length() == 1) {
        c.applyMove(face, false);
      } else if (mv.charAt(1) == '\'') {
        c.applyMove(face, true);
      } else if (mv.charAt(1) == '2') {
        c.applyMove(face, false);
        c.applyMove(face, false);
      }
    }
    return c;
  }

  @Test
  public void toFaceCubeAgainstReferenceVectors() {
    for (Map.Entry<String, String> e : VECTORS.entrySet()) {
      assertEquals("scramble \"" + e.getKey() + "\"", e.getValue(), scramble(e.getKey()).toFaceCube());
    }
  }

  @Test
  public void solvedCube() {
    CubieCube c = new CubieCube();
    assertTrue(c.isSolved());
    assertEquals(CubieCube.SOLVED_FACELET, c.toFaceCube());
  }

  @Test
  public void quarterTurnAndInverseCancel() {
    for (Face f : Face.values()) {
      CubieCube c = new CubieCube();
      c.applyMove(f, false);
      c.applyMove(f, true);
      assertTrue(f + " then " + f + "'", c.isSolved());
    }
  }

  @Test
  public void quarterTurnHasOrderFour() {
    for (Face f : Face.values()) {
      CubieCube c = new CubieCube();
      for (int i = 0; i < 4; i++) {
        c.applyMove(f, false);
      }
      assertTrue(f + " x4", c.isSolved());
    }
  }

  @Test
  public void sexyMoveSixTimesReturnsToSolved() {
    CubieCube c = new CubieCube();
    for (int i = 0; i < 6; i++) {
      c.applyMove(Face.R, false);
      c.applyMove(Face.U, false);
      c.applyMove(Face.R, true);
      c.applyMove(Face.U, true);
    }
    assertTrue(c.isSolved());
    assertEquals(CubieCube.SOLVED_FACELET, c.toFaceCube());
  }

  @Test
  public void fromFaceletRoundTripsThroughToFaceCube() {
    for (String facelet : VECTORS.values()) {
      CubieCube c = new CubieCube();
      assertTrue(c.fromFacelet(facelet));
      assertEquals(facelet, c.toFaceCube());
    }
  }

  @Test
  public void fromFaceletReconstructsSamePermutationAsMoveApplication() {
    CubieCube scrambled = scramble("R U2 F' L D B2 R' U L2 F");
    CubieCube parsed = new CubieCube();
    parsed.fromFacelet(scrambled.toFaceCube());
    assertEquals(scrambled, parsed);
  }

  @Test
  public void fromFaceletRejectsInvalidFacelets() {
    CubieCube c = new CubieCube();
    assertFalse(c.fromFacelet("X".repeat(54)));
    assertFalse(c.fromFacelet(CubieCube.SOLVED_FACELET.substring(0, 53)));
    assertFalse(c.fromFacelet("U".repeat(54)));
  }

  @Test
  public void moveIndexMapsFacesToUrfdlbOrderedTable() {
    assertEquals(0, CubieCube.moveIndex(Face.U, false));
    assertEquals(2, CubieCube.moveIndex(Face.U, true));
    assertEquals(3, CubieCube.moveIndex(Face.R, false));
    assertEquals(6, CubieCube.moveIndex(Face.F, false));
    assertEquals(9, CubieCube.moveIndex(Face.D, false));
    assertEquals(12, CubieCube.moveIndex(Face.L, false));
    assertEquals(17, CubieCube.moveIndex(Face.B, true));
  }
}
