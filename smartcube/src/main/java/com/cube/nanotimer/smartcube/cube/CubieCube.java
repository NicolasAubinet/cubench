package com.cube.nanotimer.smartcube.cube;

import com.cube.nanotimer.smartcube.model.Face;

/**
 * Cube state in the corner/edge permutation+orientation model, ported from csTimer's
 * mathlib.CubieCube (GPL-3.0).
 *
 * <p>{@code ca} holds the 8 corners — permutation in bits 0-2 ({@code & 7}), orientation
 * in bits 3-4 ({@code >> 3}, 0-2 twist). {@code ea} holds the 12 edges — permutation in
 * bits 1-11 ({@code >> 1}), flip in bit 0 ({@code & 1}). Whole-cube reorientation is
 * omitted: move tracking never rotates the model (the gyro stream handles orientation).
 */
public final class CubieCube {

  /** Solved-cube facelet string, faces in URFDLB order (9 stickers each). */
  public static final String SOLVED_FACELET =
      "UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB";

  /** Corner facelet indices, one triple per corner (URF, UFL, ...). */
  private static final int[][] C_FACELET = {
    {8, 9, 20}, {6, 18, 38}, {0, 36, 47}, {2, 45, 11},
    {29, 26, 15}, {27, 44, 24}, {33, 53, 42}, {35, 17, 51},
  };

  /** Edge facelet indices, one pair per edge (UR, UF, ...). */
  private static final int[][] E_FACELET = {
    {5, 10}, {7, 19}, {3, 37}, {1, 46}, {32, 16}, {28, 25},
    {30, 43}, {34, 52}, {23, 12}, {21, 41}, {50, 39}, {48, 14},
  };

  /** Face order used by {@link #MOVE_CUBE} indices: U R F D L B. */
  private static final Face[] MOVE_FACE_ORDER = {Face.U, Face.R, Face.F, Face.D, Face.L, Face.B};

  /** The 18 basic moves (6 faces x {CW, 180, CCW}) as cube states. */
  private static final CubieCube[] MOVE_CUBE = buildMoveCube();

  private final int[] ca;
  private final int[] ea;

  public CubieCube() {
    ca = new int[] {0, 1, 2, 3, 4, 5, 6, 7};
    ea = new int[] {0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22};
  }

  private void init(int[] ca, int[] ea) {
    System.arraycopy(ca, 0, this.ca, 0, 8);
    System.arraycopy(ea, 0, this.ea, 0, 12);
  }

  private static void cornMult(CubieCube a, CubieCube b, CubieCube prod) {
    for (int corn = 0; corn < 8; corn++) {
      int ori = ((a.ca[b.ca[corn] & 7] >> 3) + (b.ca[corn] >> 3)) % 3;
      prod.ca[corn] = (a.ca[b.ca[corn] & 7] & 7) | (ori << 3);
    }
  }

  private static void edgeMult(CubieCube a, CubieCube b, CubieCube prod) {
    for (int ed = 0; ed < 12; ed++) {
      prod.ea[ed] = a.ea[b.ea[ed] >> 1] ^ (b.ea[ed] & 1);
    }
  }

  private static void cubeMult(CubieCube a, CubieCube b, CubieCube prod) {
    cornMult(a, b, prod);
    edgeMult(a, b, prod);
  }

  private int[] toPerm() {
    int[] f = new int[54];
    for (int i = 0; i < 54; i++) {
      f[i] = i;
    }
    for (int c = 0; c < 8; c++) {
      int j = ca[c] & 0x7;
      int ori = ca[c] >> 3;
      for (int n = 0; n < 3; n++) {
        f[C_FACELET[c][(n + ori) % 3]] = C_FACELET[j][n];
      }
    }
    for (int e = 0; e < 12; e++) {
      int j = ea[e] >> 1;
      int ori = ea[e] & 1;
      for (int n = 0; n < 2; n++) {
        f[E_FACELET[e][(n + ori) % 2]] = E_FACELET[j][n];
      }
    }
    return f;
  }

  /** The 54-character facelet string for this state (URFDLB face order). */
  public String toFaceCube() {
    String ts = "URFDLB";
    int[] perm = toPerm();
    StringBuilder sb = new StringBuilder(54);
    for (int i = 0; i < 54; i++) {
      sb.append(ts.charAt(perm[i] / 9));
    }
    return sb.toString();
  }

  /**
   * Parse a 54-character facelet string into this cube. Returns {@code false} if the
   * facelets are structurally invalid (wrong colour counts / no matching piece).
   */
  public boolean fromFacelet(String facelet) {
    if (facelet.length() != 54) {
      return false;
    }
    String centers = "" + facelet.charAt(4) + facelet.charAt(13) + facelet.charAt(22)
        + facelet.charAt(31) + facelet.charAt(40) + facelet.charAt(49);
    int[] f = new int[54];
    int count = 0;
    for (int i = 0; i < 54; i++) {
      int c = centers.indexOf(facelet.charAt(i));
      if (c == -1) {
        return false;
      }
      f[i] = c;
      count += 1 << (c << 2);
    }
    if (count != 0x999999) {
      return false;
    }

    for (int i = 0; i < 8; i++) {
      int ori = 0;
      for (; ori < 3; ori++) {
        if (f[C_FACELET[i][ori]] == 0 || f[C_FACELET[i][ori]] == 3) {
          break;
        }
      }
      int col1 = f[C_FACELET[i][(ori + 1) % 3]];
      int col2 = f[C_FACELET[i][(ori + 2) % 3]];
      for (int j = 0; j < 8; j++) {
        if (col1 == C_FACELET[j][1] / 9 && col2 == C_FACELET[j][2] / 9) {
          ca[i] = j | ((ori % 3) << 3);
          break;
        }
      }
    }
    for (int i = 0; i < 12; i++) {
      for (int j = 0; j < 12; j++) {
        if (f[E_FACELET[i][0]] == E_FACELET[j][0] / 9 && f[E_FACELET[i][1]] == E_FACELET[j][1] / 9) {
          ea[i] = j << 1;
          break;
        }
        if (f[E_FACELET[i][0]] == E_FACELET[j][1] / 9 && f[E_FACELET[i][1]] == E_FACELET[j][0] / 9) {
          ea[i] = (j << 1) | 1;
          break;
        }
      }
    }
    return true;
  }

  /** Apply a single quarter turn in place. */
  public void applyMove(Face face, boolean prime) {
    CubieCube tmp = new CubieCube();
    cubeMult(this, MOVE_CUBE[moveIndex(face, prime)], tmp);
    init(tmp.ca, tmp.ea);
  }

  public boolean isSolved() {
    for (int i = 0; i < 8; i++) {
      if (ca[i] != i) {
        return false;
      }
    }
    for (int i = 0; i < 12; i++) {
      if (ea[i] != i << 1) {
        return false;
      }
    }
    return true;
  }

  /** Index into {@link #MOVE_CUBE} for a quarter turn (power 0 = CW, 2 = CCW/prime). */
  public static int moveIndex(Face face, boolean prime) {
    for (int i = 0; i < MOVE_FACE_ORDER.length; i++) {
      if (MOVE_FACE_ORDER[i] == face) {
        return i * 3 + (prime ? 2 : 0);
      }
    }
    throw new IllegalArgumentException("Unknown face: " + face);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof CubieCube)) {
      return false;
    }
    CubieCube o = (CubieCube) other;
    for (int i = 0; i < 8; i++) {
      if (ca[i] != o.ca[i]) {
        return false;
      }
    }
    for (int i = 0; i < 12; i++) {
      if (ea[i] != o.ea[i]) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    int ret = 0;
    for (int i = 0; i < 20; i++) {
      ret = ret * 31 + (i < 12 ? ea[i] : ca[i - 12]);
    }
    return ret;
  }

  private static CubieCube[] buildMoveCube() {
    CubieCube[] mc = new CubieCube[18];
    for (int i = 0; i < 18; i++) {
      mc[i] = new CubieCube();
    }
    mc[0].init(new int[] {3, 0, 1, 2, 4, 5, 6, 7}, new int[] {6, 0, 2, 4, 8, 10, 12, 14, 16, 18, 20, 22}); // U
    mc[3].init(new int[] {20, 1, 2, 8, 15, 5, 6, 19}, new int[] {16, 2, 4, 6, 22, 10, 12, 14, 8, 18, 20, 0}); // R
    mc[6].init(new int[] {9, 21, 2, 3, 16, 12, 6, 7}, new int[] {0, 19, 4, 6, 8, 17, 12, 14, 3, 11, 20, 22}); // F
    mc[9].init(new int[] {0, 1, 2, 3, 5, 6, 7, 4}, new int[] {0, 2, 4, 6, 10, 12, 14, 8, 16, 18, 20, 22}); // D
    mc[12].init(new int[] {0, 10, 22, 3, 4, 17, 13, 7}, new int[] {0, 2, 20, 6, 8, 10, 18, 14, 16, 4, 12, 22}); // L
    mc[15].init(new int[] {0, 1, 11, 23, 4, 5, 18, 14}, new int[] {0, 2, 4, 23, 8, 10, 12, 21, 16, 18, 7, 15}); // B
    for (int a = 0; a < 18; a += 3) {
      for (int p = 0; p < 2; p++) {
        cubeMult(mc[a + p], mc[a], mc[a + p + 1]);
      }
    }
    return mc;
  }
}
