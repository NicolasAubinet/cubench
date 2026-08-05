package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import org.junit.Test;

/** The drift a cube that cannot be told it is solved keeps reporting, and how it is corrected. */
public class CubeStateCorrectionTest {

  private static CubeState state(String moves) {
    return new CubeState(cube(moves).toFaceCube());
  }

  private static CubieCube cube(String moves) {
    CubieCube c = new CubieCube();
    for (String mv : moves.trim().split("\\s+")) {
      if (mv.isEmpty()) {
        continue;
      }
      Face face = Face.valueOf(mv.substring(0, 1));
      c.applyMove(face, mv.endsWith("'"));
      if (mv.endsWith("2")) {
        c.applyMove(face, false);
      }
    }
    return c;
  }

  /**
   * Solved but for two corners twisted the same way. No sequence of turns reaches it, which is the
   * shape a corner twisted by hand leaves behind and the one this whole class exists for.
   */
  private static CubieCube twoTwistedCorners() {
    int[] cp = {0, 1, 2, 3, 4, 5, 6, 7};
    int[] co = {1, 1, 0, 0, 0, 0, 0, 0};
    int[] ep = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
    int[] eo = new int[12];
    CubieCube c = new CubieCube();
    assertTrue(c.fromPermutation(cp, co, ep, eo));
    return c;
  }

  /** What a cube whose model is out by {@code drift} reports, with the solver at {@code real}. */
  private static CubeState reportedBy(CubieCube drift, CubieCube real) {
    return new CubeState(drift.inverse().multiply(real).toFaceCube());
  }

  @Test
  public void noCorrectionPassesEveryStateThrough() {
    CubeState scrambled = state("R U2 F' L");
    assertTrue(CubeStateCorrection.none().isNone());
    assertEquals(scrambled, CubeStateCorrection.none().apply(scrambled));
    assertNull(CubeStateCorrection.none().getFacelets());
  }

  @Test
  public void aCubeAlreadyReportingSolvedNeedsNoCorrection() {
    assertTrue(CubeStateCorrection.capturedFrom(CubeState.SOLVED).isNone());
  }

  @Test
  public void capturedCorrectionReadsTheCubeAsSolved() {
    CubeState reported = new CubeState(twoTwistedCorners().toFaceCube());
    CubeStateCorrection correction = CubeStateCorrection.capturedFrom(reported);

    assertFalse(correction.isNone());
    assertTrue(correction.apply(reported).isSolved());
  }

  /** The point of it: one capture, and every later reading comes out right without a new one. */
  @Test
  public void correctionHoldsThroughLaterTurns() {
    for (CubieCube drift : new CubieCube[] {twoTwistedCorners(), cube("R U R'")}) {
      CubeStateCorrection correction =
          CubeStateCorrection.capturedFrom(reportedBy(drift, new CubieCube()));

      for (String real : new String[] {"U", "R U2 F'", "B2 D L' F", "R U2 F' L D B2 R' U L2 F"}) {
        assertEquals("after " + real, state(real), correction.apply(reportedBy(drift, cube(real))));
      }
    }
  }

  @Test
  public void storedCorrectionRestoresTheCapturedOne() {
    CubeState reported = new CubeState(twoTwistedCorners().toFaceCube());
    CubeStateCorrection captured = CubeStateCorrection.capturedFrom(reported);
    CubeStateCorrection restored = CubeStateCorrection.stored(captured.getFacelets());

    assertEquals(captured.getFacelets(), restored.getFacelets());
    assertTrue(restored.apply(reported).isSolved());
  }

  // The cube's own model having been put right behind our back (another app, a battery drain) is
  // undone by the same button: what it captures next is nothing at all.
  @Test
  public void recapturingOnASolvedCubeDropsTheCorrection() {
    CubeStateCorrection recaptured = CubeStateCorrection.capturedFrom(CubeState.SOLVED);
    assertTrue(recaptured.isNone());
    assertNull(recaptured.getFacelets());
  }

  @Test
  public void unreadableStatesAreLeftAlone() {
    CubeState reported = new CubeState(twoTwistedCorners().toFaceCube());
    CubeStateCorrection correction = CubeStateCorrection.capturedFrom(reported);
    CubeState nonsense = new CubeState("X".repeat(54));

    assertTrue(CubeStateCorrection.capturedFrom(nonsense).isNone());
    assertTrue(CubeStateCorrection.stored("nope").isNone());
    assertEquals(nonsense, correction.apply(nonsense));
    assertNull(correction.apply(null));
  }
}
