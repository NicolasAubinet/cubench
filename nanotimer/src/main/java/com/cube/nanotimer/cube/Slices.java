package com.cube.nanotimer.cube;

import java.util.HashMap;
import java.util.Map;

/**
 * What a slice looks like to a smart cube: the two opposite faces its sensors report, and the
 * whole-cube spin the core makes while the middle layer turns. The core carries the gyro, so
 * {@code pair · spin = slice} holds exactly — pinned on an orientation-inclusive simulator, then
 * measured on a V10, where a slice steps the gyro 85–90° and a genuine two-handed turn of the same
 * two faces leaves it at 4°, inside the noise floor.
 *
 * <p>Only same-spatial-direction pairs are slices; {@code R L} turns the two faces opposite ways
 * and is always a real two-hander, so it is absent here and can never fold.
 */
final class Slices {

  /**
   * How far apart a slice's two turns may land. Measured over nine captured solves: a slow L4E
   * reports them up to 233 ms apart, and at the old 70 ms a third of them were never even looked at,
   * printing as the {@code R L'} the cube reported. Widening cannot fold a deliberate pair, since
   * the gyro still has to have seen the core rock; what it does risk is a whole-cube rotation
   * between the two turns matching that rock exactly, which is why this stops at the gap two halves
   * of an {@code M2} are allowed rather than going wider.
   */
  static final long WINDOW_MS = 250;

  private static final Map<String, String[]> BY_PAIR = byPair();

  private Slices() {
  }

  /**
   * The slice notation and the core spin for the pair {@code a}, {@code b} in either order, or
   * null when the two are not one slice's faces.
   */
  static String[] forPair(String a, String b) {
    String key = canonicalKey(a, b);
    return key == null ? null : BY_PAIR.get(key);
  }

  static boolean isFace(String notation) {
    return !notation.isEmpty() && axis(notation.charAt(0)) >= 0;
  }

  private static Map<String, String[]> byPair() {
    Map<String, String[]> pairs = new HashMap<String, String[]>();
    pairs.put("R L'", new String[] {"M", "x'"});
    pairs.put("R' L", new String[] {"M'", "x"});
    pairs.put("U D'", new String[] {"E", "y'"});
    pairs.put("U' D", new String[] {"E'", "y"});
    pairs.put("F' B", new String[] {"S", "z"});
    pairs.put("F B'", new String[] {"S'", "z'"});
    return pairs;
  }

  /** Orders an opposite-face pair positive-face first, or null when it is not one same-axis pair. */
  private static String canonicalKey(String a, String b) {
    if (!isFace(a) || !isFace(b)) {
      return null;
    }
    char fa = a.charAt(0);
    char fb = b.charAt(0);
    if (axis(fa) != axis(fb) || fa == fb) {
      return null;
    }
    return isPositiveFace(fa) ? a + " " + b : b + " " + a;
  }

  private static int axis(char face) {
    switch (face) {
      case 'R': case 'L': return 0;
      case 'U': case 'D': return 1;
      case 'F': case 'B': return 2;
      default: return -1;
    }
  }

  private static boolean isPositiveFace(char face) {
    return face == 'R' || face == 'U' || face == 'F';
  }
}
