package com.cube.nanotimer.smartcube.drill;

import java.util.Locale;

/**
 * Moves a last layer scramble onto the face the user actually solves on.
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
    int from = FROM.indexOf(face == null ? "U" : face.toUpperCase(Locale.ROOT));
    if (from < 0) {
      throw new IllegalArgumentException("Not a face: " + face);
    }
    return TO_TOP[from];
  }

  /**
   * @param face the face the layer should end up on, as its letter
   * @throws IllegalArgumentException if the face is not one of the six
   */
  static String toFace(String scramble, String face) {
    int target = FROM.indexOf(face == null ? "U" : face.toUpperCase(Locale.ROOT));
    if (target < 0) {
      throw new IllegalArgumentException("Not a face: " + face);
    }
    if (target == 0 || scramble == null) {
      return scramble;
    }
    StringBuilder turned = new StringBuilder(scramble.length());
    for (int i = 0; i < scramble.length(); i++) {
      char c = scramble.charAt(i);
      int from = FROM.indexOf(c);
      turned.append(from < 0 ? c : TO[target].charAt(from));
    }
    return turned.toString();
  }
}
