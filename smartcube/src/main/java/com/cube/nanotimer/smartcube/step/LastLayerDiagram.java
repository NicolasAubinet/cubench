package com.cube.nanotimer.smartcube.step;

import java.util.List;

/**
 * A last-layer case as a picture: which stickers of the layer show its colour, which face each side
 * sticker belongs to, and for a permutation, where every piece has to travel.
 *
 * <p>Drawn rather than shipped, and drawn from the very algorithm shown beside it: the picture is
 * the state the case's first algorithm solves, so the two cannot end up a quarter turn apart the way
 * a picture read off some other algorithm can. The others are written to be picked up the same way,
 * so one alignment serves the whole case and it looks the same everywhere it appears.
 *
 * <p>Positions are the nine cells of the layer as it is drawn, left to right and back to front, so
 * cell 0 is the back-left corner and cell 8 the front-right. The twelve side stickers run clockwise
 * in four strips of three: back left-to-right, right back-to-front, front left-to-right, left
 * back-to-front. Faces are named with the letter of the face they belong to on a solved cube, the
 * layer itself being {@code U}.
 */
public final class LastLayerDiagram {

  /** Cells of the layer, in drawing order. Their facelet indices on the U face. */
  private static final int[] TOP_FACELETS = {0, 1, 2, 3, 4, 5, 6, 7, 8};

  /** The side stickers, clockwise from the back-left, as facelet indices. */
  private static final int[] SIDE_FACELETS = {
    47, 46, 45,  // back, left to right
    11, 10, 9,   // right, back to front
    18, 19, 20,  // front, left to right
    36, 37, 38,  // left, back to front
  };

  /** For each side sticker, the cell of the layer it hangs off. */
  private static final int[] SIDE_CELLS = {0, 1, 2, 2, 5, 8, 6, 7, 8, 0, 3, 6};

  /** Where a piece lives, by the faces its side stickers show: {@code "B"} is the back edge. */
  private static final String[] HOME_FACES = {"BL", "B", "BR", "L", "", "R", "FL", "F", "FR"};

  private final String caseCode;
  private final boolean permutation;
  private final char[] top = new char[9];
  private final char[] sides = new char[12];
  private final int[] arrows = new int[9];

  private LastLayerDiagram(String caseCode, boolean permutation, String facelets) {
    this.caseCode = caseCode;
    this.permutation = permutation;
    for (int cell = 0; cell < top.length; cell++) {
      top[cell] = facelets.charAt(TOP_FACELETS[cell]);
    }
    for (int side = 0; side < sides.length; side++) {
      sides[side] = facelets.charAt(SIDE_FACELETS[side]);
    }
    fillArrows();
  }

  /** The picture of a case, or null if that is not a case there is one for. */
  public static LastLayerDiagram forCase(String caseCode) {
    List<LastLayerCaseAlgorithms.Algorithm> algorithms = LastLayerCaseAlgorithms.forCase(caseCode);
    if (algorithms.isEmpty()) {
      return null;
    }
    return new LastLayerDiagram(caseCode, caseCode.startsWith("pll_"),
        Notation.caseState(algorithms.get(0).getMoves()));
  }

  public String getCaseCode() {
    return caseCode;
  }

  /** Whether the layer is already one colour and only the pieces are in the wrong places. */
  public boolean isPermutation() {
    return permutation;
  }

  /** Whether the cell shows the layer's own colour, i.e. that piece is oriented. */
  public boolean isOriented(int cell) {
    return top[cell] == 'U';
  }

  /** The face a side sticker belongs to, {@code 'U'} for one of the layer's turned sideways. */
  public char sideFace(int side) {
    return sides[side];
  }

  /** The cell a side sticker hangs off, so it can be drawn against it. */
  public int sideCell(int side) {
    return SIDE_CELLS[side];
  }

  /**
   * The cell the piece in this one belongs in, or the cell itself for a piece already home. Only a
   * permutation has these: a case still being oriented is not read for where its pieces are.
   */
  public int arrow(int cell) {
    return arrows[cell];
  }

  /**
   * Where each piece has to go, worked out from the colours it shows rather than from the cube it
   * came off: an edge showing red belongs against the red centre, and a corner showing red and
   * green in the corner between them. That is the same question the picture asks the reader, so a
   * wrong arrow is a wrong picture rather than a disagreement with something invisible.
   */
  private void fillArrows() {
    for (int cell = 0; cell < arrows.length; cell++) {
      arrows[cell] = cell;
    }
    if (!permutation) {
      return;
    }
    for (int cell = 0; cell < arrows.length; cell++) {
      String faces = facesAt(cell);
      if (faces.isEmpty()) {
        continue;
      }
      for (int home = 0; home < HOME_FACES.length; home++) {
        if (sameFaces(HOME_FACES[home], faces)) {
          arrows[cell] = home;
          break;
        }
      }
    }
  }

  /** The faces the side stickers of a cell show, in no particular order. */
  private String facesAt(int cell) {
    StringBuilder faces = new StringBuilder(2);
    for (int side = 0; side < sides.length; side++) {
      if (SIDE_CELLS[side] == cell) {
        faces.append(sides[side]);
      }
    }
    return faces.toString();
  }

  private static boolean sameFaces(String home, String shown) {
    if (home.length() != shown.length()) {
      return false;
    }
    for (int i = 0; i < home.length(); i++) {
      if (shown.indexOf(home.charAt(i)) < 0) {
        return false;
      }
    }
    return true;
  }
}
