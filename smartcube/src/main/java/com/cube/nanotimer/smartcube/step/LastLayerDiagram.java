package com.cube.nanotimer.smartcube.step;

import com.cube.nanotimer.step.LastLayerAlgorithms;
import java.util.List;

/**
 * A last-layer case as a picture: which stickers of the layer show its colour, which face each side
 * sticker belongs to, and for a permutation, where every piece has to travel.
 *
 * <p>Drawn rather than shipped, and drawn from the very algorithm shown beside it: the picture is a
 * state the case's first algorithm solves, taken up the way that algorithm takes the cube up, so the
 * two cannot end up a rotation apart the way a picture read off some other algorithm can. The others
 * are written to be picked up the same way, so one alignment serves the whole case and it looks the
 * same everywhere it appears.
 *
 * <p>A permutation is drawn at the turn it is recognised at. The turn a cuber makes to put the layer
 * home after an algorithm is the cuber's, not the algorithm's, and is never written down, so a
 * permutation algorithm as it is published can leave the layer a quarter turn out. The state it
 * solves outright is therefore the case turned by that quarter, which draws a Z whose corners cycle
 * rather than one that only swaps its edges, and a Jb, an Ra and an Rb as three corners and three
 * edges, which is a G perm's shape under a J perm's name. {@link #recognised} picks instead among the
 * four states the algorithm solves once that last turn is made.
 *
 * <p>Which of the four is not a matter of taste: it is the one showing fewest pieces out of place,
 * since the turn that puts the layer home is the one that leaves the most of it home. Where that
 * ties, {@link #asTaught} breaks it against {@link LastLayerAlgorithms}. A G perm ties four ways and
 * reads as three corners and three edges at two of them, and the wrong one of the two draws a Ga
 * that is not what a Gb undoes.
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

  /** The turns that put the layer home after an algorithm, which the solver makes and nobody writes. */
  private static final String[] LAYER_HOME = {"", "U", "U2", "U'"};

  private final String caseCode;
  private final boolean permutation;
  private final String facelets;
  private final char[] top = new char[9];
  private final char[] sides = new char[12];
  private final int[] arrows = new int[9];

  private LastLayerDiagram(String caseCode, boolean permutation, String facelets) {
    this.caseCode = caseCode;
    this.permutation = permutation;
    this.facelets = facelets;
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
    String moves = algorithms.get(0).getMoves();
    return caseCode.startsWith("pll_")
        ? recognised(caseCode, moves)
        : new LastLayerDiagram(caseCode, false, Notation.caseState(moves));
  }

  /**
   * The case as the solver sees it when they recognise it: of the four states the algorithm solves
   * once they have put the layer home, the one with fewest pieces out of place.
   *
   * <p>Fewest is the right reading because the turn that puts the layer home is by definition the one
   * that leaves the most of the layer home, so the case a cuber names is the sparsest of the four. It
   * picks out the two-corner-two-edge Jb over the three-and-three that is a G perm's shape, and the
   * four-edge Z over the one whose corners cycle.
   */
  private static LastLayerDiagram recognised(String caseCode, String moves) {
    LastLayerDiagram[] candidates = new LastLayerDiagram[LAYER_HOME.length];
    int fewest = Integer.MAX_VALUE;
    int sparsest = 0;
    int ties = 0;
    for (int turn = 0; turn < LAYER_HOME.length; turn++) {
      candidates[turn] = new LastLayerDiagram(caseCode, true,
          Notation.caseState((moves + " " + LAYER_HOME[turn]).trim()));
      int outOfPlace = candidates[turn].outOfPlace();
      if (outOfPlace < fewest) {
        fewest = outOfPlace;
        sparsest = turn;
        ties = 1;
      } else if (outOfPlace == fewest) {
        ties++;
      }
    }
    return ties == 1 ? candidates[sparsest] : asTaught(caseCode, candidates, candidates[sparsest]);
  }

  /**
   * The candidate standing where {@link LastLayerAlgorithms} leaves the case, for a case whose four
   * turns are equally sparse and so cannot be told apart by counting.
   *
   * <p>That table's algorithm is the one a drill deals the case with, layer-home turn and all, so
   * the state it leaves is the case as it is taught: the one a Ga is drawn at is the one a Gb undoes.
   * Looked for from every angle, since two algorithms for a case need not hold the cube the same way.
   */
  private static LastLayerDiagram asTaught(
      String caseCode, LastLayerDiagram[] candidates, LastLayerDiagram sparsest) {
    String reference = Notation.caseState(LastLayerAlgorithms.algorithm(
        LastLayerAlgorithms.PERMUTATIONS, caseCode.substring(caseCode.indexOf('_') + 1)));
    for (LastLayerDiagram candidate : candidates) {
      if (standsLike(candidate.facelets, reference)) {
        return candidate;
      }
    }
    return sparsest;
  }

  /** How many pieces of the layer are not home, which is how sparse the case reads. */
  private int outOfPlace() {
    int count = 0;
    for (int cell = 0; cell < arrows.length; cell++) {
      if (arrows[cell] != cell) {
        count++;
      }
    }
    return count;
  }

  /** The same state as the reference, allowing for the cube being held a quarter turn round. */
  private static boolean standsLike(String state, String reference) {
    for (int rotation = 0; rotation < FaceletRotations.COUNT; rotation++) {
      if (FaceletRotations.face(rotation, Cubies.U) == Cubies.U
          && state.equals(LastLayerCases.inFrame(reference, rotation))) {
        return true;
      }
    }
    return false;
  }

  public String getCaseCode() {
    return caseCode;
  }

  /** The state drawn, so a test can check the case's algorithms solve the picture they sit beside. */
  String drawnState() {
    return facelets;
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
