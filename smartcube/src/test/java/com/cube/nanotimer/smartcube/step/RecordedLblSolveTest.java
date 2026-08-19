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

  /** A third solve: a corner insertion drops a middle edge home on its way, and it stays there. */
  private static final String SCRAMBLE_3 =
      "U2 F2 L2 U F2 U' L2 R2 D L' D' F' R B2 R' U B U L' D2 R2";

  private static final String MOVES_3 =
      "[y z'] y@0 z'@0 D@0 B'@85 B'@1178 z'@2063 L@2063 B@2200 D@3399 F@3780 z'@4104 D'@4104 "
          + "y2@5559 x'@5559 F'@5559 z@5677 F'@5678 y@6589 x'@6589 U@6589 U@6640 U'@7066 y@7499 "
          + "x@7499 F@7499 y'@7763 z@7763 U@7763 z'@9597 F@9598 F@9724 R'@10219 F'@10487 R@10758 "
          + "R@11595 z'@11940 F@11940 z'@12258 R'@12258 x2@13128 B@13128 z2@13558 B@13558 "
          + "F'@14438 D'@14808 F@15239 D@15825 z@16512 B@16512 z@16977 B@16978 B@17781 F'@18340 "
          + "z@18882 D@18882 F'@18913 D'@19011 z'@19318 B'@19319 F'@19910 U'@20366 y@20652 "
          + "x'@20652 F'@20652 F'@20749 x'@21233 U@21233 F@21510 z'@21846 L'@21846 y'@22339 "
          + "z@22339 U@22339 z@23063 L@23063 U'@23159 x@24295 F@24295 z@24787 U@24787 z'@25076 "
          + "F@25076 z@25355 U@25355 z'@25681 F@25681 U@25987 F'@26096 x@26659 U'@26659 y@26885 "
          + "F'@26885 y'@27218 x'@27218 U'@27218 y@28360 F@28360 z'@30557 L'@30557 x'@30971 "
          + "U'@30971 R@31127 U@31299 x@31983 L@31983 x'@32410 U'@32410 R'@32592 U@32741 x@33656 "
          + "R'@33656 B@33826 R@34236 F@34359 F@34515 R'@34629 z@34899 B'@34899 z'@34990 R@34990 "
          + "F'@35100 R'@35163 B@35481 R@35705 F'@35768 R'@35865 B'@36183 R@36790";

  /**
   * A fourth solve, keyholing in earnest: the first layer is left turned from 17.6s to 21.5s while
   * two middle edges go in through the empty corner slot.
   */
  private static final String SCRAMBLE_4 =
      "R2 U R2 F2 U B2 D2 L2 R2 D B2 L F2 D' R' U2 B R' D U2 F";

  private static final String MOVES_4 =
      "[y' z] y@0 x2@0 F'@0 x@1610 U@1610 y'@1687 U@1688 U'@2020 B'@2489 y@3548 x2@3548 L@3548 "
          + "L@3884 B'@5344 z'@6020 L@6020 z@6209 B@6209 z'@6986 U@6986 z'@8075 R'@8075 F'@8364 "
          + "R@8640 F@8857 z'@9150 R'@9150 F'@9427 R@9669 y'@10892 F@10892 y@11674 x@11674 "
          + "F@11674 L'@11913 F'@12214 y'@12472 x'@12472 L@12472 y2@13034 x'@13034 D'@13034 "
          + "y'@13217 F@13217 D@13481 R@13788 F@14155 y@14993 x2@14993 R'@14993 y2@16115 "
          + "z'@16115 B'@16115 z'@16296 B'@16297 B@16806 B@16848 B@17618 D@18555 z2@18802 "
          + "F'@18802 z@18952 D'@18952 z'@19276 B@19276 z2@20733 F@20733 R@21037 F'@21115 "
          + "R'@21182 z@21476 B@21477 B@21521 B'@21691 z'@22874 F'@22874 z@23441 F'@23441 "
          + "R'@23843 F@24385 R@24800 z'@25149 B@25149 y@25752 z@25752 F'@25752 y@26635 z@26635 "
          + "U@26635 y'@26937 z@26937 L'@26937 U'@27293 L@27470 y2@28746 z'@28746 F'@28746 "
          + "y'@29351 z@29351 L'@29351 x'@29689 F'@29689 y@29959 L'@29959 F'@30278 y'@30634 "
          + "L'@30634 y@31146 F@31146 L@31397 F@31736 L@32070 x'@33066 R@33067 F@33468 U@33583 "
          + "F'@33691 U'@33858 x@34231 R'@34232 F@35679 R@37376 F@37450 z@37612 R'@37612 F@38223 "
          + "z'@38388 R@38388 F'@38499 R'@38683 z@38764 F@38764 z'@38919 R@38919 F'@39022 "
          + "F'@39373 R'@39417 y@40778 x'@40778 F'@40778 y2@42521 x'@42521 L@42521 z'@42680 "
          + "F@42680 L'@42773 z@42921 F@42921 z'@43133 L@43133 F'@43319 L'@43517 z@43631 F@43631 "
          + "z'@43831 L@43831 F'@43963 F'@44540 L'@44768 R'@45538 z@45689 F@45689 L@46070 "
          + "F'@46117 R@46446 z'@46761 F@46761 L'@46956 z@47008 F'@47008 z'@48055 U@48055 "
          + "F'@48117 z'@48478 D'@48478 z@48617 F@48617 z'@49016 U'@49016 F'@49096 z@49577 "
          + "D@49577 F@49699 z@50455 L'@50455 B@50611 L@50746 F@50814 z'@50998 F@50998 L'@51149 "
          + "z@51374 B'@51374 L@51537 F'@51609 L'@51805 B@51938 L@52015 F'@52135 L'@52258 "
          + "B'@52468 L@52678";

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

    assertEquals("corner_bdl", detector.subStepName(1, 2));
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
    assertEquals("corner_bur", detector.subStepName(3, 0));
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

  /**
   * The second corner's insertion drops a middle edge home on its way and leaves it there — three
   * moves that were turned for the corner alone, the edge having been nowhere near its slot. Read as
   * the second layer being started there, the solve had two corners in at that moment and failed the
   * one thing this method is asked for. The edge the solver actually turned for is the next one, and
   * three corners were in by then.
   */
  @Test
  public void doesNotCountAnEdgeACornerInsertionDroppedHomeAsStartingTheSecondLayer() {
    replay(SCRAMBLE_3, MOVES_3);

    assertTrue(detector.isComplete());
    assertTrue(detector.matchesMethod());
    assertEquals(Face.B, detector.getCrossFace());
    assertEquals("edge_ur", detector.subStepName(2, 0));
    assertEquals(10758L, (long) detector.getSubStepTimestampMs(1, 1)); // the corner's own move
    assertEquals(10758L, (long) detector.getSubStepTimestampMs(2, 0));
    assertEquals("edge_dr", detector.subStepName(4, 0)); // the first one turned for, three in
    assertEquals(15825L, (long) detector.getSubStepTimestampMs(4, 0));
  }

  /**
   * The solver turns the first layer at 17.6s and does not turn it back until 21.5s, putting two
   * middle edges in through the empty corner slot meanwhile. Read where the layer stood, nothing at
   * all settled across those four seconds: both edges were dated together on the turn that squared
   * the layer up, one of them a step of no moves and no time, and the moves that inserted them fell
   * to the edge before.
   */
  @Test
  public void readsEdgesPutInWhileTheFirstLayerStoodTurned() {
    replay(SCRAMBLE_4, MOVES_4);

    assertTrue(detector.isComplete());
    assertTrue(detector.matchesMethod());
    assertEquals(Face.B, detector.getCrossFace());
    assertEquals(3, detector.subStepCount(2));
    assertEquals("edge_dl", detector.subStepName(2, 0));
    assertEquals(18952L, (long) detector.getSubStepTimestampMs(2, 0));
    assertEquals("edge_dr", detector.subStepName(2, 1));
    assertEquals(21182L, (long) detector.getSubStepTimestampMs(2, 1));
    assertEquals("edge_ur", detector.subStepName(2, 2));
    assertEquals(24800L, (long) detector.getSubStepTimestampMs(2, 2));
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
