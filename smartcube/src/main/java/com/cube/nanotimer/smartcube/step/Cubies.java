package com.cube.nanotimer.smartcube.step;

import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import java.util.Arrays;

/**
 * The cube's geometry as facelet indices, shared by the step detectors: which facelets make up each
 * piece, and what it means for one to be home. Faces are URFDLB order, 9 facelets each, as in
 * {@link CubeState#SOLVED_FACELETS} — so a facelet's index carries both its face and its place on it,
 * and a solved cube is the one where every facelet shows its own face's colour.
 */
final class Cubies {

  static final String SOLVED = CubeState.SOLVED_FACELETS;
  static final String FACES = "URFDLB";

  static final int U = 0, R = 1, F = 2, D = 3, L = 4, B = 5;

  /** Facelet offsets within a face: corners at the four points, edges between them. */
  static final int[] CORNER_POSITIONS = {0, 2, 6, 8};
  static final int[] EDGE_POSITIONS = {1, 3, 5, 7};

  /**
   * Corner facelet indices, one triple per corner, as in {@code CubieCube}. The first four are the
   * U-layer corners in the order a U turn moves them through, each written as its U facelet followed
   * by the two side facelets in that same order — so a U turn carries the triple of one onto the
   * triple of the next, position for position.
   */
  static final int[][] CORNERS = {
    {8, 9, 20}, {6, 18, 38}, {0, 36, 47}, {2, 45, 11},
    {29, 26, 15}, {27, 44, 24}, {33, 53, 42}, {35, 17, 51},
  };

  /** Edge facelet indices, one pair per edge (UR, UF, ...), as in {@code CubieCube}. */
  static final int[][] EDGES = {
    {5, 10}, {7, 19}, {3, 37}, {1, 46}, {32, 16}, {28, 25},
    {30, 43}, {34, 52}, {23, 12}, {21, 41}, {50, 39}, {48, 14},
  };

  static final int UR = 0, UF = 1, UL = 2, UB = 3, DR = 4, DF = 5, DL = 6, DB = 7;
  static final int FR = 8, FL = 9, BL = 10, BR = 11;
  static final int DFR = 4, DLF = 5, DBL = 6, DRB = 7;

  private Cubies() {
  }

  static int opposite(int face) {
    return (face + 3) % 6;
  }

  /**
   * Whether the corners sit in an odd permutation — which on a legal cube means the edges do too,
   * since the two parities always agree. It is what decides whether a solve done in cycles has a
   * parity to fix: an odd permutation cannot be cycled away, so it leaves a pair of each type
   * swapped for one last algorithm to put back.
   *
   * <p>Worth reading off the scramble rather than watching for near the end. The pair standing is
   * only visible in the counts for as long as it is left standing, and the algorithm that resolves
   * it disturbs half the cube on the way — so the state at the start answers exactly what the state
   * at the end can only be guessed at.
   */
  static boolean isOddPermutation(String facelets) {
    int[] permutation = new int[CORNERS.length];
    for (int slot = 0; slot < CORNERS.length; slot++) {
      permutation[slot] = cornerIn(facelets, CORNERS[slot]);
      if (permutation[slot] < 0) {
        return false; // a state we cannot read is not one to claim a parity from
      }
    }
    boolean[] visited = new boolean[permutation.length];
    int cycles = 0;
    for (int slot = 0; slot < permutation.length; slot++) {
      if (visited[slot]) {
        continue;
      }
      cycles++;
      for (int next = slot; !visited[next]; next = permutation[next]) {
        visited[next] = true;
      }
    }
    return ((permutation.length - cycles) % 2) != 0;
  }

  /** Which corner is sitting in the slot, by its colours: a piece is the same piece however twisted. */
  private static int cornerIn(String facelets, int[] slot) {
    String found = coloursOf(facelets, slot);
    for (int corner = 0; corner < CORNERS.length; corner++) {
      if (homeColoursOf(CORNERS[corner]).equals(found)) {
        return corner;
      }
    }
    return -1;
  }

  private static String coloursOf(String facelets, int[] piece) {
    char[] colours = new char[piece.length];
    for (int i = 0; i < piece.length; i++) {
      colours[i] = facelets.charAt(piece[i]);
    }
    Arrays.sort(colours);
    return new String(colours);
  }

  private static String homeColoursOf(int[] piece) {
    char[] colours = new char[piece.length];
    for (int i = 0; i < piece.length; i++) {
      colours[i] = SOLVED.charAt(piece[i]);
    }
    Arrays.sort(colours);
    return new String(colours);
  }

  static Face faceAt(int face) {
    return Face.valueOf(String.valueOf(FACES.charAt(face)));
  }

  /** Whether the piece carries the given face's colour — that is, whether it belongs on that face. */
  static boolean touches(int[] piece, int face) {
    for (int facelet : piece) {
      if (SOLVED.charAt(facelet) == FACES.charAt(face)) {
        return true;
      }
    }
    return false;
  }

  static boolean inPlace(String facelets, int[] piece) {
    return inPlace(facelets, piece, FaceletRotations.IDENTITY);
  }

  /**
   * Whether the piece is home, read in the given rotation: it shows its own colours wherever that
   * rotation has carried it. Which is what a slice leaves behind — the core turned, so relative to
   * the centres the state is written against, the solver's own frame is a rotation away.
   */
  static boolean inPlace(String facelets, int[] piece, int rotation) {
    for (int facelet : piece) {
      if (facelets.charAt(FaceletRotations.apply(rotation, facelet)) != SOLVED.charAt(facelet)) {
        return false;
      }
    }
    return true;
  }
}
