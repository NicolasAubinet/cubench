package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.cube.CubieCube;

/**
 * A cube state written as the twisty player's own idea of one, so the live cube can be pointed at a
 * state rather than walked to it.
 *
 * <p>The player animates an alg, and an alg is a route: until this existed, the mirror could only be
 * shown a state somebody knew the moves to, which left a cube scrambled off-camera undrawable. Its
 * setup anchor also takes a <em>KTransformation</em>, which is the state itself, and that is what
 * this produces (see {@code live.html}'s {@code ntLiveState}).
 *
 * <p><b>The two libraries number the pieces differently and agree on everything else.</b> Both hold
 * "which piece is in this slot" and measure a corner's twist against U/D and an edge's flip against
 * F/B, so only the slot order has to be translated: cubing.js reads the U layer as UF UR UB UL and
 * the corners as UFR URB UBL ULF, where {@link CubieCube} (csTimer's, Kociemba's before it) reads UR
 * UF UL UB and URF UFL ULB UBR. Both swaps are their own inverse, which is why one table does both
 * directions. Verified move by move against cubing.js 0.63.3 in {@code CubePatternFormatTest}.
 */
public final class CubePatternFormat {

  /** cubing.js corner slot to ours, and back: only the two pairs either side of UFR are swapped. */
  private static final int[] CORNERS = {0, 3, 2, 1, 4, 5, 6, 7};

  /** cubing.js edge slot to ours, and back: U, D and the middle layer each read in a rotated order. */
  private static final int[] EDGES = {1, 0, 3, 2, 5, 4, 7, 6, 8, 9, 11, 10};

  private CubePatternFormat() {
  }

  /**
   * The player's number for one of our edge slots, for anything that has to name a slot to it rather
   * than hand it a whole state. The table is its own inverse, so this reads both ways.
   */
  public static int playerEdge(int slot) {
    return EDGES[slot];
  }

  /**
   * @param facelets the 54 sticker colours, faces in URFDLB order
   * @return the state as a KTransformation's data, or null where the facelets are not a cube
   */
  public static String format(String facelets) {
    CubieCube cube = new CubieCube();
    if (facelets == null || !cube.fromFacelet(facelets)) {
      return null;
    }
    int[] cp = new int[8];
    int[] co = new int[8];
    int[] ep = new int[12];
    int[] eo = new int[12];
    cube.toPermutation(cp, co, ep, eo);

    StringBuilder sb = new StringBuilder(400);
    sb.append("{\"CORNERS\":");
    appendOrbit(sb, cp, co, CORNERS);
    sb.append(",\"EDGES\":");
    appendOrbit(sb, ep, eo, EDGES);
    // The centres never move: the facelets are read in the cube's own frame, where they cannot, and
    // how the whole thing is being held is the gyro's business rather than the state's.
    sb.append(",\"CENTERS\":{\"permutation\":[0,1,2,3,4,5],\"orientationDelta\":[0,0,0,0,0,0]}}");
    return sb.toString();
  }

  /** One orbit, its slots renumbered through {@code order} on both sides of the lookup. */
  private static void appendOrbit(StringBuilder sb, int[] pieces, int[] orientations, int[] order) {
    sb.append("{\"permutation\":[");
    for (int i = 0; i < order.length; i++) {
      sb.append(i == 0 ? "" : ",").append(order[pieces[order[i]]]);
    }
    sb.append("],\"orientationDelta\":[");
    for (int i = 0; i < order.length; i++) {
      sb.append(i == 0 ? "" : ",").append(orientations[order[i]]);
    }
    sb.append("]}");
  }
}
