package com.cube.nanotimer.smartcube.step;

import com.cube.nanotimer.smartcube.model.CubeState;

/**
 * Standard cube notation applied to a facelet string: face turns, wide turns, slices and whole-cube
 * rotations. Built from the cube's geometry rather than from tables — a turn is a rotation of every
 * facelet whose cubie lies in the layer, which is one rule for all of them.
 * {@code LastLayerCasesTest} pins it against {@link com.cube.nanotimer.smartcube.cube.CubieCube} and
 * {@link FaceletRotations} before trusting it with anything.
 *
 * <p>It was test-only until an algorithm typed in by a user had to be checked before being kept: the
 * check that an algorithm solves the case it is filed under is the same one either way, and running
 * two of them would have let the table and the user's own entry disagree about what counts.
 */
final class Notation {

  /** Outward direction of each face, in URFDLB order. */
  private static final int[][] NORMALS = {
    {0, 1, 0}, {1, 0, 0}, {0, 0, 1}, {0, -1, 0}, {-1, 0, 0}, {0, 0, -1},
  };

  /** A quarter turn about each axis, in the direction the R, U and F faces turn. */
  private static final int[][] MATRICES = {
    {1, 0, 0, 0, 0, 1, 0, -1, 0}, // x: F goes up
    {0, 0, -1, 0, 1, 0, 1, 0, 0}, // y: F goes left
    {0, 1, 0, -1, 0, 0, 0, 0, 1}, // z: U goes right
  };

  private static final int[] INDEX = buildIndex();

  private Notation() {
  }

  /**
   * The state the given algorithm solves: it applied backwards to a solved cube.
   *
   * <p>An algorithm written with a rotation in it leaves the cube standing somewhere other than where
   * it was picked up, so the cube it ends on is a solved one <em>turned</em> — and that is what has to
   * be walked back from, not the upright solved cube. Walk back from the wrong one and the result is
   * the case conjugated by the rotation, which is a different case entirely.
   */
  static String caseState(String alg) {
    String solvedAsLeft = asLeftBy(apply(CubeState.SOLVED_FACELETS, alg));
    String state = apply(solvedAsLeft, inverse(alg));
    if (!centresHome(state)) {
      throw new IllegalStateException("Algorithm does not end where it started: " + alg);
    }
    return state;
  }

  /** A solved cube standing the way the given state does. */
  private static String asLeftBy(String facelets) {
    for (int rotation = 0; rotation < FaceletRotations.COUNT; rotation++) {
      char[] turned = new char[CubeState.SOLVED_FACELETS.length()];
      for (int facelet = 0; facelet < turned.length; facelet++) {
        turned[FaceletRotations.apply(rotation, facelet)] =
            CubeState.SOLVED_FACELETS.charAt(facelet);
      }
      String candidate = new String(turned);
      if (sameCentres(candidate, facelets)) {
        return candidate;
      }
    }
    throw new IllegalStateException("No rotation stands a cube up like: " + facelets);
  }

  private static boolean sameCentres(String facelets, String other) {
    for (int face = 0; face < 6; face++) {
      if (facelets.charAt(face * 9 + 4) != other.charAt(face * 9 + 4)) {
        return false;
      }
    }
    return true;
  }

  static String apply(String facelets, String alg) {
    String state = facelets;
    for (String token : alg.trim().split("\\s+")) {
      if (!token.isEmpty()) {
        state = applyToken(state, token);
      }
    }
    return state;
  }

  static String inverse(String alg) {
    String[] tokens = alg.trim().split("\\s+");
    StringBuilder sb = new StringBuilder();
    for (int i = tokens.length - 1; i >= 0; i--) {
      if (sb.length() > 0) {
        sb.append(' ');
      }
      sb.append(inverseToken(tokens[i]));
    }
    return sb.toString();
  }

  private static String inverseToken(String token) {
    if (token.indexOf('2') >= 0) {
      return token.endsWith("'") ? token.substring(0, token.length() - 1) : token;
    }
    return token.endsWith("'") ? token.substring(0, token.length() - 1) : token + "'";
  }

  private static String applyToken(String facelets, String token) {
    boolean wide = token.indexOf('w') >= 0;
    char face = wide ? Character.toLowerCase(token.charAt(0)) : token.charAt(0);
    int[] layer = layerOf(face);
    int amount = token.indexOf('2') >= 0 ? 2 : 1;
    if (token.indexOf('\'') >= 0) {
      amount = -amount;
    }
    return turn(facelets, layer[0], layer[1], layer[2], (layer[3] * amount % 4 + 4) % 4);
  }

  /** The axis a letter turns about, the slice of cubies it carries, and which way round. */
  private static int[] layerOf(char face) {
    switch (face) {
      case 'U': return new int[] {1, 1, 1, 1};
      case 'D': return new int[] {1, -1, -1, -1};
      case 'E': return new int[] {1, 0, 0, -1};
      case 'u': return new int[] {1, 0, 1, 1};
      case 'd': return new int[] {1, -1, 0, -1};
      case 'y': return new int[] {1, -1, 1, 1};
      case 'R': return new int[] {0, 1, 1, 1};
      case 'L': return new int[] {0, -1, -1, -1};
      case 'M': return new int[] {0, 0, 0, -1};
      case 'r': return new int[] {0, 0, 1, 1};
      case 'l': return new int[] {0, -1, 0, -1};
      case 'x': return new int[] {0, -1, 1, 1};
      case 'F': return new int[] {2, 1, 1, 1};
      case 'B': return new int[] {2, -1, -1, -1};
      case 'S': return new int[] {2, 0, 0, 1};
      case 'f': return new int[] {2, 0, 1, 1};
      case 'b': return new int[] {2, -1, 0, -1};
      case 'z': return new int[] {2, -1, 1, 1};
      default: throw new IllegalArgumentException("Unknown turn: " + face);
    }
  }

  private static String turn(String facelets, int axis, int low, int high, int quarters) {
    char[] turned = facelets.toCharArray();
    for (int facelet = 0; facelet < facelets.length(); facelet++) {
      int[] position = position(facelet);
      if (position[axis] < low || position[axis] > high) {
        continue;
      }
      int[] normal = NORMALS[facelet / 9];
      for (int i = 0; i < quarters; i++) {
        position = transform(MATRICES[axis], position);
        normal = transform(MATRICES[axis], normal);
      }
      turned[INDEX[key(position, normal)]] = facelets.charAt(facelet);
    }
    return new String(turned);
  }

  private static boolean centresHome(String facelets) {
    for (int face = 0; face < 6; face++) {
      if (facelets.charAt(face * 9 + 4) != Cubies.FACES.charAt(face)) {
        return false;
      }
    }
    return true;
  }

  private static int[] transform(int[] matrix, int[] vector) {
    int[] result = new int[3];
    for (int row = 0; row < 3; row++) {
      result[row] = matrix[row * 3] * vector[0]
          + matrix[row * 3 + 1] * vector[1]
          + matrix[row * 3 + 2] * vector[2];
    }
    return result;
  }

  private static int[] buildIndex() {
    int[] index = new int[729];
    for (int facelet = 0; facelet < 54; facelet++) {
      index[key(position(facelet), NORMALS[facelet / 9])] = facelet;
    }
    return index;
  }

  private static int key(int[] position, int[] normal) {
    int packed = 0;
    for (int value : new int[] {
        position[0], position[1], position[2], normal[0], normal[1], normal[2]}) {
      packed = packed * 3 + (value + 1);
    }
    return packed;
  }

  /** The cubie a facelet sits on, in x (right), y (up), z (front) — as {@link FaceletRotations}. */
  private static int[] position(int facelet) {
    int face = facelet / 9;
    int row = (facelet % 9) / 3;
    int column = facelet % 3;
    switch (face) {
      case Cubies.U: return new int[] {column - 1, 1, row - 1};
      case Cubies.R: return new int[] {1, 1 - row, 1 - column};
      case Cubies.F: return new int[] {column - 1, 1 - row, 1};
      case Cubies.D: return new int[] {column - 1, -1, 1 - row};
      case Cubies.L: return new int[] {-1, 1 - row, column - 1};
      default: return new int[] {1 - column, 1 - row, -1};
    }
  }
}
