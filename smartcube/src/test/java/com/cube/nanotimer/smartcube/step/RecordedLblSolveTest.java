package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import org.junit.Test;

/**
 * The detector against a layer-by-layer solve recorded off a cube, replayed from its scramble and
 * stored moves. The synthetic fixtures next door are textbook algorithms played end to end; this
 * one is a beginner solving at their own pace, and the rules about which states count came from it.
 *
 * <p>Read from the states the moves passed through it came out as no method at all. Its pieces flit
 * through home constantly — the corner insertion at 14.2s carries the DL edge into its slot on the
 * way and out again a move later — so the first-ever-home reading dated a middle edge before the
 * first layer had two corners in, and the solve failed the very test that tells layer-by-layer from
 * a method that pairs each corner with its edge.
 *
 * <p>Rotation tokens are skipped: a whole-cube rotation moves no piece, and the cube reports it
 * only so the moves can be written the way the solver made them.
 */
public class RecordedLblSolveTest {

  private static final String SCRAMBLE =
      "R2 U' L2 R2 U2 L2 F2 R2 B2 D' L' B' L U' B D2 L' B' D R' U2";

  private static final String MOVES =
      "[y2 x] y2@0 x@0 L'@0 B@1868 y@2530 L@2530 y'@2591 L@2591 L@2998 B'@3075 z'@3690 U@3690 "
          + "B@3760 U@5223 x@5744 F@5744 z@6405 F@6405 y'@7659 x@7659 F'@7659 z@8761 R'@8761 "
          + "x@10037 U@10037 y'@10445 x'@10445 F@10445 y@11086 x@11086 U'@11086 y@11512 z'@11512 "
          + "F@11512 x'@12163 U'@12163 F@12311 U@12599 y@14234 z2@14234 D'@14234 x'@14366 "
          + "F@14366 x@14672 D@14672 z2@17158 F'@17158 z@17791 D@17791 y@18308 F@18308 y'@18646 "
          + "x'@18646 D'@18646 y2@19744 B'@19744 z'@20574 B@20574 z@21132 B@21133 z'@21383 "
          + "B'@21384 B'@22018 B'@22522 z'@23076 B'@23077 z'@24464 F'@24464 F'@25443 F'@25879 "
          + "U'@27102 F@27233 U@27655 z'@29029 B'@29030 F@29698 U@30014 F'@30069 U'@30166 "
          + "F'@34139 R'@34868 y@35349 F'@35349 x'@36131 R'@36131 y'@36516 x@36516 F'@36516 "
          + "y@36865 R'@36865 y'@37293 F@37293 y@37979 R@37979 y'@38304 F@38304 y@38727 R@38727 "
          + "y'@40452 x@40452 F@40452 F@40662 z2@41928 U@41928 y'@42345 F@42345 L@42416 F'@42569 "
          + "L'@42688 y@42998 U'@42999 U'@44708 R'@45256 z'@45475 D'@45475 R@45679 U@46269 "
          + "R'@46679 y@47247 D@47247 R@47390 y'@48737 x'@48737 L'@48737 B@48937 L@49012 F@49170 "
          + "L'@49334 B'@49589 L@49734 F@49982 L'@50123 B@50185 L@50335 F@50469 F@50649 L'@50839 "
          + "B'@51065 L@51205";

  private final CubieCube cube = new CubieCube();
  private final LblStepDetector detector = new LblStepDetector();

  /**
   * The solve as the solver built it: the cross, three corners, three edges, then the corner held
   * back and the edge its empty slot was carrying — a keyhole, turned by someone who did not
   * necessarily mean one. The last edge takes nine moves of its own at the end.
   */
  @Test
  public void readsARecordedSolveAsTheLayersItWasBuiltIn() {
    replay();

    assertTrue(detector.isComplete());
    assertTrue(detector.matchesMethod());
    assertEquals(Face.B, detector.getCrossFace());
    assertEquals(6, detector.stepCount());
    assertEquals("cross", detector.stepName(0));
    assertEquals("layer1", detector.stepName(1));
    assertEquals("layer2", detector.stepName(2));
    assertEquals("layer1", detector.stepName(3));
    assertEquals("layer2", detector.stepName(4));
    assertEquals("ll", detector.stepName(5));
    assertEquals(8761L, (long) detector.getStepTimestampMs(0));
    assertEquals(3, detector.subStepCount(1));
    assertEquals(3, detector.subStepCount(2));
    assertEquals(1, detector.subStepCount(3));
    assertEquals(1, detector.subStepCount(4));
  }

  /**
   * The DL edge is carried through its own slot by the corner insertion at 14.2s and is out again
   * one move later. Dated there it opened a second layer between the first layer's corners, and
   * three corners were then never in before the second layer started — which is the whole of what
   * this method is asked for. It belongs to the insertion that left it there, four seconds on.
   */
  @Test
  public void datesAPieceWhereItStayedRatherThanWhereItPassedThrough() {
    replay();

    assertEquals("corner_dl", detector.subStepName(1, 2));
    assertEquals(18646L, (long) detector.getSubStepTimestampMs(1, 2));
    assertEquals("edge_dl", detector.subStepName(2, 0));
    assertEquals(18646L, (long) detector.getSubStepTimestampMs(2, 0));
  }

  /** The corner and the edge that land on the same move: the one continuing the stretch already
   * open comes first, and the other opens the stretch after it. */
  @Test
  public void keepsThePieceContinuingAStretchAheadOfTheOneOpeningTheNext() {
    replay();

    assertEquals("edge_ur", detector.subStepName(2, 2));
    assertEquals("corner_ur", detector.subStepName(3, 0));
    assertEquals(30166L, (long) detector.getSubStepTimestampMs(2, 2));
    assertEquals(30166L, (long) detector.getSubStepTimestampMs(3, 0));
  }

  /**
   * The last layer, in the order this solver was taught it: the edges oriented and permuted
   * together, then the corners oriented, then the corners permuted on the last algorithm.
   *
   * <p>Its corners come out of the first two layers <b>already permuted</b> — a half turn away, and
   * the solver turns exactly that half turn — and the algorithm they then turn for the edges takes
   * it apart again. So corner permutation is dated where it was reached for good, at the end of the
   * solve, rather than at the moment it was first true and about to be lost.
   */
  @Test
  public void datesALastLayerPartWhereItWasReachedForGood() {
    replay();

    assertEquals(42999L, (long) detector.getSubStepTimestampMs(5, 0)); // lleo
    assertEquals(42999L, (long) detector.getSubStepTimestampMs(5, 3)); // llep
    assertEquals(47390L, (long) detector.getSubStepTimestampMs(5, 1)); // llco
    assertEquals(51205L, (long) detector.getSubStepTimestampMs(5, 2)); // llcp
    assertEquals(51205L, (long) detector.getStepTimestampMs(5));
  }

  private void replay() {
    for (String token : SCRAMBLE.trim().split("\\s+")) {
      Face face = Face.valueOf(token.substring(0, 1));
      for (int i = 0; i < (token.endsWith("2") ? 2 : 1); i++) {
        cube.applyMove(face, token.endsWith("'"));
      }
    }
    detector.reset(new CubeState(cube.toFaceCube()), 0);
    for (String token : MOVES.trim().split("\\s+")) {
      int at = token.indexOf('@');
      if (at < 0) {
        continue; // the grip, which has no offset and turns nothing
      }
      String notation = token.substring(0, at);
      if ("xyz".indexOf(notation.charAt(0)) >= 0) {
        continue;
      }
      Face face = Face.valueOf(notation.substring(0, 1));
      boolean prime = notation.endsWith("'");
      cube.applyMove(face, prime);
      detector.onState(new CubeState(cube.toFaceCube()),
          new CubeMove(face, prime, Long.parseLong(token.substring(at + 1))));
    }
  }
}
