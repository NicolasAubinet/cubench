package com.cube.nanotimer.smartcube.drill;

import java.util.Locale;

/**
 * Moves a last layer scramble onto the face the user actually solves on: a drill's case, and the
 * timer's scramble types that leave a last layer (F2L, last layer, PLL, the Roux ones).
 *
 * <p>{@link com.cube.nanotimer.smartcube.step.LastLayerScrambles} writes every case onto U, because a
 * scramble has to come out as face turns and the layer has to end up somewhere. A solved cube's U is
 * white, so a drill left as written puts the last layer on white, which is the one colour a
 * white-cross solver never sees it on.
 *
 * <p>Relabelling each face letter through a whole-cube rotation fixes it and costs nothing else: the
 * sequence stays face turns only, and rotating a case is the one thing that cannot change which case
 * it is.
 */
public final class LayerRotation {

  /**
   * Where each face goes when the cube is turned to bring U onto the named face, in URFDLB order.
   * The rotations are z2 for D, x' for F, x for B, z for R and z' for L.
   */
  private static final String FROM = "URFDLB";
  private static final String[] TO = {
    "URFDLB", // U: nothing to do
    "RDFLUB", // R (z): U->R, R->D, D->L, L->U
    "FRDBLU", // F (x'): U->F, F->D, D->B, B->U
    "DLFURB", // D (z2): U->D, D->U, R->L, L->R
    "LUFRDB", // L (z'): U->L, L->D, D->R, R->U
    "BRUFLD", // B (x): U->B, B->D, D->F, F->U
  };

  /**
   * And the way back, in the same order: the whole-cube rotation that stands each face on top.
   * Green stays in front wherever it still can, and where it cannot (green itself on top, or the
   * blue opposite it) white takes its place.
   */
  private static final String[] TO_TOP = {"", "z'", "x", "z2", "z", "x'"};

  /** The three slices, and the face each one turns with, in the same order. */
  private static final String SLICES = "MES";
  private static final String SLICE_FACES = "LDF";

  private LayerRotation() {
  }

  /**
   * The whole-cube rotation that stands {@code face} on top, as {@code x}/{@code y}/{@code z}
   * notation, or empty for U. For a screen drawing a case the user solves on that face: the cube
   * is dealt in the colours' own frame, where a last layer on yellow is drawn on the underside.
   *
   * @throws IllegalArgumentException if the face is not one of the six
   */
  public static String toTop(String face) {
    return TO_TOP[indexOf(face)];
  }

  /**
   * The scramble with every turn relabelled, so the layer it leaves unsolved is on {@code face}
   * rather than on U. Wide turns follow their face, and a slice follows the face it turns with
   * ({@code M} with L, {@code E} with D, {@code S} with F), changing direction where that face lands
   * on the far side of its new axis. Returns the same array for U.
   *
   * @param face the face the layer should end up on, as its letter
   * @throws IllegalArgumentException if the face is not one of the six
   */
  public static String[] toFace(String[] scramble, String face) {
    int target = indexOf(face);
    if (target == 0 || scramble == null) {
      return scramble;
    }
    String[] turned = new String[scramble.length];
    for (int i = 0; i < scramble.length; i++) {
      turned[i] = turn(scramble[i], TO[target]);
    }
    return turned;
  }

  /** The same for a scramble written as one line, its turns separated by spaces. */
  static String toFace(String scramble, String face) {
    if (scramble == null) {
      return null;
    }
    return String.join(" ", toFace(scramble.split(" ", -1), face));
  }

  private static int indexOf(String face) {
    int index = FROM.indexOf(face == null ? "U" : face.toUpperCase(Locale.ROOT));
    if (index < 0) {
      throw new IllegalArgumentException("Not a face: " + face);
    }
    return index;
  }

  private static String turn(String token, String to) {
    if (token == null || token.isEmpty()) {
      return token;
    }
    char letter = token.charAt(0);
    String amount = token.substring(1);
    boolean lower = Character.isLowerCase(letter);
    char upper = Character.toUpperCase(letter);
    int face = FROM.indexOf(upper);
    if (face >= 0) {
      return caseOf(to.charAt(face), lower) + amount;
    }
    int slice = SLICES.indexOf(upper);
    if (slice < 0) {
      return token;
    }
    char followed = to.charAt(FROM.indexOf(SLICE_FACES.charAt(slice)));
    int same = SLICE_FACES.indexOf(followed);
    if (same >= 0) {
      return caseOf(SLICES.charAt(same), lower) + amount;
    }
    char opposite = FROM.charAt((FROM.indexOf(followed) + 3) % FROM.length());
    return caseOf(SLICES.charAt(SLICE_FACES.indexOf(opposite)), lower) + inverted(amount);
  }

  private static String caseOf(char letter, boolean lower) {
    return String.valueOf(lower ? Character.toLowerCase(letter) : letter);
  }

  private static String inverted(String amount) {
    if (amount.startsWith("2")) {
      return amount;
    }
    return amount.startsWith("'") ? amount.substring(1) : "'" + amount;
  }
}
