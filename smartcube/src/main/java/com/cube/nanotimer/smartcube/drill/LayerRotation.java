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
final class LayerRotation {

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

  private LayerRotation() {
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
