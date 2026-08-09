package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;

import com.cube.nanotimer.smartcube.model.CubeRotation;
import org.junit.Test;

/** What a blind solve was left in, read off states built piece by piece from a solved cube. */
public class BlindResidualTest {

  private static final int UR = 0, UF = 1, UL = 2, UB = 3;
  private static final int URF = 12, UFL = 13, UBL = 14;

  private final BlindTargets targets = new BlindTargets(BlindTargets.UNKNOWN_FRAME);

  @Test
  public void solvedCubeHasNothingLeft() {
    BlindResidual residual = BlindResidual.of(Cubies.SOLVED, targets);
    assertEquals(BlindResidual.Shape.SOLVED, residual.getShape());
    assertEquals(0, residual.getCount());
  }

  @Test
  public void threeEdgesLeftReadAsACycle() {
    BlindResidual residual = BlindResidual.of(cycle(Cubies.SOLVED, UR, UF, UL), targets);
    assertEquals(BlindResidual.Shape.EDGE_CYCLE, residual.getShape());
    assertEquals(3, residual.getCount());
    assertEquals("UR-UL-UF", residual.getPieces());
  }

  @Test
  public void threeCornersLeftReadAsACycle() {
    BlindResidual residual = BlindResidual.of(cycle(Cubies.SOLVED, URF, UFL, UBL), targets);
    assertEquals(BlindResidual.Shape.CORNER_CYCLE, residual.getShape());
    assertEquals("UFR-UBL-UFL", residual.getPieces());
  }

  @Test
  public void twoEdgesTurnedWhereTheyStandReadAsFlipped() {
    String left = turn(turn(Cubies.SOLVED, UF, 1), UB, 1);
    BlindResidual residual = BlindResidual.of(left, targets);
    assertEquals(BlindResidual.Shape.FLIPPED, residual.getShape());
    assertEquals("UF, UB", residual.getPieces());
  }

  @Test
  public void twoCornersTurnedWhereTheyStandReadAsTwisted() {
    String left = turn(turn(Cubies.SOLVED, URF, 1), UFL, 2);
    BlindResidual residual = BlindResidual.of(left, targets);
    assertEquals(BlindResidual.Shape.TWISTED, residual.getShape());
    assertEquals("UFR, UFL", residual.getPieces());
  }

  @Test
  public void twoOfEachSwappedReadAsAParity() {
    String left = swap(swap(Cubies.SOLVED, UR, UF), URF, UFL);
    BlindResidual residual = BlindResidual.of(left, targets);
    assertEquals(BlindResidual.Shape.PARITY, residual.getShape());
    assertEquals(4, residual.getCount());
    assertEquals("UFR-UFL + UR-UF", residual.getPieces());
  }

  /** A cycle and an orientation are two mistakes, and the cycle is still the cycle it was. */
  @Test
  public void aCycleWithAnExtraTurnedPieceKeepsItsShapeAndSaysTheTurnedPieceApart() {
    String left = turn(cycle(Cubies.SOLVED, UR, UF, UL), URF, 1);
    BlindResidual residual = BlindResidual.of(left, targets);
    assertEquals(BlindResidual.Shape.EDGE_CYCLE, residual.getShape());
    assertEquals(4, residual.getCount());
    assertEquals("UR-UL-UF", residual.getPieces());
    assertEquals("UFR", residual.getTurned());
  }

  /** Shooting to the wrong sticker of the right piece: the parity landed, one of its pieces turned. */
  @Test
  public void aParityWithATurnedPieceKeepsItsShapeToo() {
    String left = turn(swap(swap(Cubies.SOLVED, UR, UF), URF, UFL), UB, 1);
    BlindResidual residual = BlindResidual.of(left, targets);
    assertEquals(BlindResidual.Shape.PARITY, residual.getShape());
    assertEquals(5, residual.getCount());
    assertEquals("UFR-UFL + UR-UF", residual.getPieces());
    assertEquals("UB", residual.getTurned());
  }

  /** Pieces out of place in no shape at all still say which of them are merely turned. */
  @Test
  public void pieceOutOfPlaceAndPieceTurnedAreSaidApartWhenThereIsNoShape() {
    String left = turn(turn(swap(Cubies.SOLVED, UR, UF), URF, 1), UFL, 2);
    BlindResidual residual = BlindResidual.of(left, targets);
    assertEquals(BlindResidual.Shape.MIXED, residual.getShape());
    assertEquals(4, residual.getCount());
    assertEquals("UR, UF", residual.getPieces());
    assertEquals("UFR, UFL", residual.getTurned());
  }

  /** Nothing out of place: the turned pieces are the whole of what was left, and are said as it. */
  @Test
  public void turnedPiecesAloneAreNotSaidTwice() {
    BlindResidual residual = BlindResidual.of(turn(turn(Cubies.SOLVED, UF, 1), UB, 1), targets);
    assertEquals(BlindResidual.Shape.FLIPPED, residual.getShape());
    assertEquals("UF, UB", residual.getPieces());
    assertEquals("", residual.getTurned());
  }

  @Test
  public void aCubeTooFarOffIsCountedRatherThanNamed() {
    String left = Cubies.SOLVED;
    left = swap(swap(swap(left, UR, UF), UL, UB), 4, 5);
    left = swap(swap(swap(left, 6, 7), 8, 9), 10, 11);
    BlindResidual residual = BlindResidual.of(left, targets);
    assertEquals(BlindResidual.Shape.SCATTERED, residual.getShape());
    assertEquals(12, residual.getCount());
    assertEquals("", residual.getPieces());
  }

  /** The slices turned the core, so the same mistake has to read the same however the cube sits. */
  @Test
  public void driftDoesNotChangeWhatWasLeft() {
    String left = cycle(Cubies.SOLVED, UR, UF, UL);
    for (int rotation = 0; rotation < FaceletRotations.COUNT; rotation++) {
      BlindResidual residual = BlindResidual.of(rotated(left, rotation), targets);
      assertEquals(BlindResidual.Shape.EDGE_CYCLE, residual.getShape());
      assertEquals(3, residual.getCount());
    }
  }

  /** The pieces are said in the grip the solve was held in, as every other blind name is. */
  @Test
  public void namesAreSpelledInTheGrip() {
    BlindTargets held = new BlindTargets(FaceletRotations.of(CubeRotation.byNotation("y")));
    BlindResidual residual = BlindResidual.of(cycle(Cubies.SOLVED, UR, UF, UL), held);
    assertEquals(BlindResidual.Shape.EDGE_CYCLE, residual.getShape());
    assertEquals("UB-UF-UR", residual.getPieces()); // the same UR-UL-UF, said a y round
  }

  /** The piece at {@code a} moved to {@code b}, the one at {@code b} to {@code c}, and so round. */
  private static String cycle(String facelets, int a, int b, int c) {
    char[] left = facelets.toCharArray();
    for (int i = 0; i < Cubies.PIECES[a].length; i++) {
      left[Cubies.PIECES[b][i]] = facelets.charAt(Cubies.PIECES[a][i]);
      left[Cubies.PIECES[c][i]] = facelets.charAt(Cubies.PIECES[b][i]);
      left[Cubies.PIECES[a][i]] = facelets.charAt(Cubies.PIECES[c][i]);
    }
    return new String(left);
  }

  private static String swap(String facelets, int a, int b) {
    char[] left = facelets.toCharArray();
    for (int i = 0; i < Cubies.PIECES[a].length; i++) {
      left[Cubies.PIECES[b][i]] = facelets.charAt(Cubies.PIECES[a][i]);
      left[Cubies.PIECES[a][i]] = facelets.charAt(Cubies.PIECES[b][i]);
    }
    return new String(left);
  }

  /** The piece turned where it stands, its stickers carried {@code by} places round it. */
  private static String turn(String facelets, int slot, int by) {
    char[] left = facelets.toCharArray();
    int[] piece = Cubies.PIECES[slot];
    for (int i = 0; i < piece.length; i++) {
      left[piece[(i + by) % piece.length]] = facelets.charAt(piece[i]);
    }
    return new String(left);
  }

  private static String rotated(String facelets, int rotation) {
    char[] turned = new char[facelets.length()];
    for (int facelet = 0; facelet < turned.length; facelet++) {
      turned[FaceletRotations.apply(rotation, facelet)] = facelets.charAt(facelet);
    }
    return new String(turned);
  }
}
