package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import org.junit.Test;

/**
 * The detector against layer-by-layer solves recorded off a cube, replayed from their scramble and
 * stored moves. The synthetic fixtures next door are textbook algorithms played end to end; these
 * are a beginner solving at their own pace, and the rules about which states count came from them.
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

  /** A second solve of the same session: the same reading missed the last of its middle edges. */
  private static final String SCRAMBLE_2 =
      "D R2 D2 R2 U F2 L2 D L2 B2 R' F' U L U2 B' F2 R2 F L2 U";

  private static final String MOVES_2 =
      "[y z'] y@0 z'@0 B@0 z'@705 R@705 z@1012 D@1012 y'@1595 R'@1595 y@1771 x@1771 R'@1771 "
          + "z2@2605 R'@2605 F'@2895 y'@3297 z@3297 F'@3297 y@3926 x'@3926 L@3926 L@3971 R@4406 "
          + "L'@5637 y@5795 z'@5795 F@5795 L@6074 y'@6804 U'@6804 y@6969 F@6969 y'@7216 U@7216 "
          + "z@8656 F'@8657 z@9230 F@9230 z'@9627 R@9627 F'@10036 x@10431 R'@10432 y'@11131 "
          + "z'@11131 U@11131 F@11362 U'@11433 F'@11686 z@12019 B@12019 L@12356 F'@12396 "
          + "L'@12485 z@12797 B@12798 B@15154 F'@16463 z@16800 D'@16800 F@17042 z@17322 D@17322 "
          + "y@17736 z'@17736 B@17736 y'@18998 x@18998 B'@18998 B'@19077 z2@19720 F@19720 "
          + "F@19860 F@20988 D@21305 F'@21369 D'@21418 z'@21767 B'@21768 B'@21889 R'@23886 "
          + "F@24162 y2@24725 R@24725 x'@25735 R@25736 U'@26042 x@26379 R'@26380 U@26557 "
          + "y2@27785 z@27785 R@27785 F@28073 R@28318 F@28612 R@28930 F'@29090 y'@29417 R'@29417 "
          + "F'@29750 R'@30324 y@31402 x2@31402 R@31402 x'@31753 F@31753 U@31847 F'@31928 "
          + "U'@32077 x@32347 R'@32348 F@34115 z@36960 R@36960 F@37077 R'@37142 F@37383 R@37553 "
          + "F'@37653 R'@37835 F@37936 R@38128 F'@38192 F'@38604 R'@38985 z@40694 U@40694 "
          + "F'@40784 D'@42560 F@42673 U'@42939 F'@43103 D@44181 F@44784 z'@45665 R'@45665 "
          + "B@45853 R@46072 F@46239 F@46404 R'@46584 B'@46824 R@46959 F@47149 F@47318 R'@47477 "
          + "B@47560 R@47699 F'@47765 R'@47915 B'@48534 R@48697 F@48893 F@49072 R'@49210 B@49279 "
          + "R@49444 F@49514 F@49661 R'@49844 B'@50114 R@50199 F@51564";

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

  /**
   * The last of the second layer's edges goes in nine moves after the corner before it, and the
   * first reading had it home already — carried through its slot by that corner's insertion. The
   * second layer's last step was then a step of no moves at all, and the nine moves that really
   * inserted the edge fell into the last layer, where they read as the longest edge orientation of
   * the solve. One flicker, and two steps wrong at once.
   */
  @Test
  public void keepsTheMovesThatInsertedAnEdgeInTheLayerTheyBelongTo() {
    replay(SCRAMBLE_2, MOVES_2);

    assertEquals("layer2", detector.stepName(4));
    assertEquals("edge_ur", detector.subStepName(4, 0));
    assertEquals(30324L, (long) detector.getSubStepTimestampMs(4, 0));
    assertEquals(32348L, (long) detector.getSubStepTimestampMs(5, 0)); // and the last layer's own
  }

  /**
   * This solve permutes its last layer's edges before orienting the corners, and the algorithm it
   * then turns for the corners leaves the layer a turn off. There the edges are placed by one turn
   * and the corners by another, which is an H perm and a diagonal swap read from either end — the
   * same state, and nothing to choose between them.
   *
   * <p>So the edges are unproven there rather than undone, and the six seconds the solver spent
   * permuting them stay theirs. Read as undone they were dated at the turn that squared the layer
   * up again, and the algorithm that did the work was counted into orienting the corners.
   */
  @Test
  public void keepsAPermutationAStateCannotDisprove() {
    replay(SCRAMBLE_2, MOVES_2);

    assertEquals(38985L, (long) detector.getSubStepTimestampMs(5, 3)); // llep
    assertEquals(44181L, (long) detector.getSubStepTimestampMs(5, 1)); // llco, after it
  }

  private void replay() {
    replay(SCRAMBLE, MOVES);
  }

  private void replay(String scramble, String moves) {
    for (String token : scramble.trim().split("\\s+")) {
      Face face = Face.valueOf(token.substring(0, 1));
      for (int i = 0; i < (token.endsWith("2") ? 2 : 1); i++) {
        cube.applyMove(face, token.endsWith("'"));
      }
    }
    detector.reset(new CubeState(cube.toFaceCube()), 0);
    for (String token : moves.trim().split("\\s+")) {
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
