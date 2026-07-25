package com.cube.nanotimer.smartcube.drivers;

/**
 * The piece every GAN generation leaves out of its facelets packet. The last corner and the last
 * edge are not sent — they are whatever makes the permutation sum and the orientation parity work
 * out — so each parser reads seven corners and eleven edges and derives the rest here.
 */
final class GanFacelets {

  /** Corner permutations 0..7 sum to this, edge permutations 0..11 to {@link #EDGE_PERM_SUM}. */
  private static final int CORNER_PERM_SUM = 28;
  private static final int EDGE_PERM_SUM = 66;

  private GanFacelets() {
  }

  /**
   * Fill in the last corner and edge of arrays whose earlier entries the cube sent.
   *
   * @param cp 8 corner permutations, 0..6 filled
   * @param co 8 corner orientations, 0..6 filled
   * @param ep 12 edge permutations, 0..10 filled
   * @param eo 12 edge orientations, 0..10 filled
   */
  static void completeLastPiece(int[] cp, int[] co, int[] ep, int[] eo) {
    cp[7] = CORNER_PERM_SUM - sum(cp, 7);
    co[7] = (3 - sum(co, 7) % 3) % 3;
    ep[11] = EDGE_PERM_SUM - sum(ep, 11);
    eo[11] = (2 - sum(eo, 11) % 2) % 2;
  }

  private static int sum(int[] values, int count) {
    int total = 0;
    for (int i = 0; i < count; i++) {
      total += values[i];
    }
    return total;
  }
}
