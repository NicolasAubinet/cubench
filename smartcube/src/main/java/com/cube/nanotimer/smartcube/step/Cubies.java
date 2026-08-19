package com.cube.nanotimer.smartcube.step;

import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

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

  /** Every piece, edges first: the slot order the detector and the naming both read pieces by. */
  static final int[][] PIECES = new int[EDGES.length + CORNERS.length][];

  static {
    System.arraycopy(EDGES, 0, PIECES, 0, EDGES.length);
    System.arraycopy(CORNERS, 0, PIECES, EDGES.length, CORNERS.length);
  }

  private Cubies() {
  }

  static int opposite(int face) {
    return (face + 3) % 6;
  }

  static boolean isEdge(int slot) {
    return slot < EDGES.length;
  }

  static int slotOf(int facelet) {
    for (int slot = 0; slot < PIECES.length; slot++) {
      for (int candidate : PIECES[slot]) {
        if (candidate == facelet) {
          return slot;
        }
      }
    }
    return -1;
  }

  /** Where the piece currently sitting in the slot belongs — the same piece however twisted. */
  static int homeSlotOf(String facelets, int slot) {
    String found = coloursOf(facelets, PIECES[slot]);
    for (int home = 0; home < PIECES.length; home++) {
      if (PIECES[home].length == PIECES[slot].length && homeColoursOf(PIECES[home]).equals(found)) {
        return home;
      }
    }
    return -1;
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
    return coloursOf(facelets, piece, FaceletRotations.IDENTITY);
  }

  private static String coloursOf(String facelets, int[] piece, int rotation) {
    char[] colours = new char[piece.length];
    for (int i = 0; i < piece.length; i++) {
      colours[i] = facelets.charAt(FaceletRotations.apply(rotation, piece[i]));
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

  /** The four edges of a face are home: its cross is built. */
  static boolean crossDone(String facelets, int face) {
    for (int[] edge : EDGES) {
      if (touches(edge, face) && !inPlace(facelets, edge)) {
        return false;
      }
    }
    return true;
  }

  /** The given positions of the face opposite this one all show its own colour: on the last layer
   * of a solve built on {@code face}, those pieces are oriented. */
  static boolean lastLayerOriented(String facelets, int face, int[] positions) {
    int opposite = opposite(face);
    char colour = FACES.charAt(opposite);
    for (int position : positions) {
      if (facelets.charAt(opposite * 9 + position) != colour) {
        return false;
      }
    }
    return true;
  }

  static int placingTurns(String facelets, int[][] slots) {
    return placingTurns(facelets, slots, FaceletRotations.IDENTITY);
  }

  /**
   * Which turns of a layer would place every one of these pieces, as a bit per quarter turn, and 0
   * when none would. The slots must be given in the order a turn of that layer carries them through,
   * so bit {@code a} says every piece is sitting {@code a} slots short of home. Pieces are read by
   * their colours, so one placed but twisted counts as placed: orientation is a question of its own.
   *
   * <p>Read as a set rather than as a yes: two piece types placed under turns that have none in
   * common are not both placed, since only one turn can be made.
   */
  static int placingTurns(String facelets, int[][] slots, int rotation) {
    int turns = 0;
    for (int auf = 0; auf < slots.length; auf++) {
      boolean placed = true;
      for (int slot = 0; slot < slots.length && placed; slot++) {
        placed = coloursOf(facelets, slots[slot], rotation)
            .equals(homeColoursOf(slots[(slot + auf) % slots.length]));
      }
      if (placed) {
        turns |= 1 << auf;
      }
    }
    return turns;
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
   * Where every sticker went between two states, as the facelet each one moved to. Turning permutes
   * places and not pieces, so the motion read off one pair of states is the motion those same turns
   * would make from any other state — which is what lets a solve be replayed with one algorithm
   * changed and nothing else. Read piece by piece, since only a cubie's own colours identify it.
   */
  static int[] motionBetween(String from, String to) {
    int[] motion = new int[from.length()];
    for (int facelet = 0; facelet < motion.length; facelet++) {
      motion[facelet] = facelet; // centres, and anything the pieces below do not claim
    }
    Map<String, Integer> landedIn = new HashMap<>(); // where each piece ended up, by its colours
    for (int slot = 0; slot < PIECES.length; slot++) {
      landedIn.put(coloursOf(to, PIECES[slot]), slot);
    }
    for (int slot = 0; slot < PIECES.length; slot++) {
      Integer landed = landedIn.get(coloursOf(from, PIECES[slot]));
      if (landed == null) {
        continue;
      }
      for (int facelet : PIECES[slot]) {
        for (int candidate : PIECES[landed]) {
          if (to.charAt(candidate) == from.charAt(facelet)) {
            motion[facelet] = candidate;
          }
        }
      }
    }
    return motion;
  }

  /** A state with the given motion made from it: every sticker carried where the motion sends it. */
  static String applyMotion(int[] motion, String facelets) {
    char[] moved = new char[facelets.length()];
    for (int facelet = 0; facelet < moved.length; facelet++) {
      moved[motion[facelet]] = facelets.charAt(facelet);
    }
    return new String(moved);
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
