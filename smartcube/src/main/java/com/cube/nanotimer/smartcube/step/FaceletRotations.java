package com.cube.nanotimer.smartcube.step;

import com.cube.nanotimer.smartcube.model.CubeRotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The 24 ways the cube can sit in space, as permutations of the 54 facelet positions: rotation
 * {@code r} carries the sticker at facelet {@code i} to facelet {@code apply(r, i)}, and the face
 * {@code f} to {@code face(r, f)}.
 *
 * <p>Why a detector needs these. A smart cube's state is written against its own centres, and no
 * face turn ever moves a centre — so for CFOP, whose milestones are exact facelet placements, the
 * frame is fixed and rotations never come up. A slice turn is the exception: the cube reports it as
 * a pair of opposite face turns ({@code M} arrives as {@code R} then {@code L'}), because the core —
 * centres and all — is what physically moved. Relative to the centres, then, everything <em>else</em>
 * has rotated. Roux spends its whole last step in M moves, so a block that the solver is holding
 * perfectly still walks around the state a quarter turn at a time, and a milestone written as an
 * exact placement would go missing. Reading each milestone in the rotation the blocks are actually
 * sitting in puts them back.
 *
 * <p>The tables are built from the cube's geometry — each facelet's cubie position and the direction
 * it faces, both in {-1,0,1} — so a rotation is just a signed permutation of the axes applied to
 * both.
 */
final class FaceletRotations {

  static final int COUNT = 24;
  static final int IDENTITY;

  private static final int[][] FACELETS = new int[COUNT][];
  private static final int[][] FACE_MAPS = new int[COUNT][];
  private static final int[] INVERSES = new int[COUNT];

  /** Outward direction of each face, in URFDLB order. */
  private static final int[][] NORMALS = {
    {0, 1, 0}, {1, 0, 0}, {0, 0, 1}, {0, -1, 0}, {-1, 0, 0}, {0, 0, -1},
  };

  /** A quarter turn about the R–L axis (an {@code x} rotation): F goes up. */
  private static final int[] X = {1, 0, 0, 0, 0, 1, 0, -1, 0};

  /** A quarter turn about the U–D axis (a {@code y} rotation): F goes left. */
  private static final int[] Y = {0, 0, -1, 0, 1, 0, 1, 0, 0};

  static {
    int[] index = buildFaceletIndex();
    List<int[]> matrices = allRotations();
    int identity = -1;
    for (int r = 0; r < COUNT; r++) {
      int[] matrix = matrices.get(r);
      FACELETS[r] = faceletPermutation(matrix, index);
      FACE_MAPS[r] = faceMap(matrix);
      if (Arrays.equals(FACE_MAPS[r], new int[] {0, 1, 2, 3, 4, 5})) {
        identity = r;
      }
    }
    IDENTITY = identity;
    for (int r = 0; r < COUNT; r++) {
      for (int s = 0; s < COUNT; s++) {
        if (undoes(r, s)) {
          INVERSES[r] = s;
          break;
        }
      }
    }
  }

  private static boolean undoes(int rotation, int candidate) {
    for (int facelet = 0; facelet < 54; facelet++) {
      if (FACELETS[candidate][FACELETS[rotation][facelet]] != facelet) {
        return false;
      }
    }
    return true;
  }

  private FaceletRotations() {
  }

  /** Where the rotation carries the sticker sitting at the given facelet. */
  static int apply(int rotation, int facelet) {
    return FACELETS[rotation][facelet];
  }

  static int face(int rotation, int face) {
    return FACE_MAPS[rotation][face];
  }

  /** The rotation that undoes this one: how to read a facelet back in the frame it came from. */
  static int inverse(int rotation) {
    return INVERSES[rotation];
  }

  /** The same rotation as the gyro writes it, so a tracked whole-cube turn can be read as a frame. */
  static int of(CubeRotation rotation) {
    for (int r = 0; r < COUNT; r++) {
      boolean same = true;
      for (int face = 0; face < 6 && same; face++) {
        same = FACE_MAPS[r][face] == Cubies.FACES.indexOf(rotation.mapFace(Cubies.FACES.charAt(face)));
      }
      if (same) {
        return r;
      }
    }
    return IDENTITY;
  }

  /** The rotations that leave the given face where it is — a quarter turn about it, and its powers. */
  static int[] about(int face) {
    int[] found = new int[4];
    int count = 0;
    for (int r = 0; r < COUNT; r++) {
      if (FACE_MAPS[r][face] == face) {
        found[count++] = r;
      }
    }
    return found;
  }

  /** Every rotation, reached by composing the two generators until nothing new turns up. */
  private static List<int[]> allRotations() {
    Set<String> seen = new LinkedHashSet<>();
    List<int[]> rotations = new ArrayList<>();
    List<int[]> pending = new ArrayList<>();
    int[] identity = {1, 0, 0, 0, 1, 0, 0, 0, 1};
    seen.add(Arrays.toString(identity));
    rotations.add(identity);
    pending.add(identity);
    while (!pending.isEmpty()) {
      int[] matrix = pending.remove(0);
      for (int[] generator : new int[][] {X, Y}) {
        int[] composed = multiply(generator, matrix);
        if (seen.add(Arrays.toString(composed))) {
          rotations.add(composed);
          pending.add(composed);
        }
      }
    }
    return rotations;
  }

  private static int[] multiply(int[] left, int[] right) {
    int[] product = new int[9];
    for (int row = 0; row < 3; row++) {
      for (int column = 0; column < 3; column++) {
        int sum = 0;
        for (int k = 0; k < 3; k++) {
          sum += left[row * 3 + k] * right[k * 3 + column];
        }
        product[row * 3 + column] = sum;
      }
    }
    return product;
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

  private static int[] faceletPermutation(int[] matrix, int[] index) {
    int[] permutation = new int[54];
    for (int facelet = 0; facelet < 54; facelet++) {
      int[] position = transform(matrix, position(facelet));
      int[] normal = transform(matrix, NORMALS[facelet / 9]);
      permutation[facelet] = index[key(position, normal)];
    }
    return permutation;
  }

  private static int[] faceMap(int[] matrix) {
    int[] map = new int[6];
    for (int face = 0; face < 6; face++) {
      int[] normal = transform(matrix, NORMALS[face]);
      for (int other = 0; other < 6; other++) {
        if (Arrays.equals(NORMALS[other], normal)) {
          map[face] = other;
        }
      }
    }
    return map;
  }

  private static int[] buildFaceletIndex() {
    int[] index = new int[729];
    Arrays.fill(index, -1);
    for (int facelet = 0; facelet < 54; facelet++) {
      index[key(position(facelet), NORMALS[facelet / 9])] = facelet;
    }
    return index;
  }

  private static int key(int[] position, int[] normal) {
    int packed = 0;
    for (int value : new int[] {position[0], position[1], position[2], normal[0], normal[1], normal[2]}) {
      packed = packed * 3 + (value + 1);
    }
    return packed;
  }

  /**
   * The cubie a facelet sits on, in x (right), y (up), z (front). Each face reads its rows and
   * columns the way the facelet string does: U from the back row forwards, D from the front row
   * back, and every side face from its top row down, left to right as seen from outside it.
   */
  private static int[] position(int facelet) {
    int face = facelet / 9;
    int row = (facelet % 9) / 3;
    int column = facelet % 3;
    switch (face) {
      case Cubies.U:
        return new int[] {column - 1, 1, row - 1};
      case Cubies.R:
        return new int[] {1, 1 - row, 1 - column};
      case Cubies.F:
        return new int[] {column - 1, 1 - row, 1};
      case Cubies.D:
        return new int[] {column - 1, -1, 1 - row};
      case Cubies.L:
        return new int[] {-1, 1 - row, column - 1};
      default:
        return new int[] {1 - column, 1 - row, -1};
    }
  }
}
