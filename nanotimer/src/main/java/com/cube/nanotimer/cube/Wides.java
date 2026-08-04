package com.cube.nanotimer.cube;

import java.util.HashMap;
import java.util.Map;

/**
 * What a wide move looks like to a smart cube: the single opposite face its sensors report, and the
 * whole-cube spin the core makes while the two layers turn together.
 *
 * <p>{@code u = y D}, {@code d = y' U}, {@code r = x L}, {@code l = x' R}, {@code f = z B},
 * {@code b = z' F}; a prime inverts both halves. The face is never primed against its spin, because
 * the far layer does not move in space. {@code WidesTest} derives all of it rather than restating
 * it: a wrong sign here reads plausibly and reconstructs a solve that does not solve.
 */
final class Wides {

  /** Rows of face, core spin, wide: the whole of it, since every face has exactly one wide. */
  private static final String[][] TABLE = {
    {"D", "y", "u"}, {"D'", "y'", "u'"},
    {"U", "y'", "d"}, {"U'", "y", "d'"},
    {"L", "x", "r"}, {"L'", "x'", "r'"},
    {"R", "x'", "l"}, {"R'", "x", "l'"},
    {"B", "z", "f"}, {"B'", "z'", "f'"},
    {"F", "z'", "b"}, {"F'", "z", "b'"},
  };

  private static final Map<String, String> SPIN_BY_FACE = spinByFace();
  private static final Map<String, String> BY_FACE_AND_SPIN = byFaceAndSpin();

  private Wides() {
  }

  /** The one spin this face would have made had it been a wide: the gyro's single candidate. */
  static String spinFor(String face) {
    return SPIN_BY_FACE.get(face);
  }

  /** The wide notation for a face turn and its core spin, or null when the two are not one move. */
  static String forFaceAndSpin(String face, String spin) {
    return BY_FACE_AND_SPIN.get(face + " " + spin);
  }

  private static Map<String, String> spinByFace() {
    Map<String, String> spins = new HashMap<String, String>();
    for (String[] row : TABLE) {
      spins.put(row[0], row[1]);
    }
    return spins;
  }

  private static Map<String, String> byFaceAndSpin() {
    Map<String, String> wides = new HashMap<String, String>();
    for (String[] row : TABLE) {
      wides.put(row[0] + " " + row[1], row[2]);
    }
    return wides;
  }
}
