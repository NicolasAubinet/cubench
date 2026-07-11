package com.cube.nanotimer.smartcube.crypto;

/**
 * AES-128 single-block (ECB) cipher, ported from csTimer's sha256.js (GPL-3.0) and
 * validated against the FIPS-197 known-answer vector. {@link #encrypt}/{@link #decrypt}
 * operate in place on the first 16 bytes of the array and return it; any bytes past
 * index 16 are left untouched (the GAN/MoYu two-block scheme relies on this).
 */
public final class Aes128 {

  private static final int[] SBOX = {
    99, 124, 119, 123, 242, 107, 111, 197, 48, 1, 103, 43, 254, 215, 171, 118,
    202, 130, 201, 125, 250, 89, 71, 240, 173, 212, 162, 175, 156, 164, 114, 192,
    183, 253, 147, 38, 54, 63, 247, 204, 52, 165, 229, 241, 113, 216, 49, 21,
    4, 199, 35, 195, 24, 150, 5, 154, 7, 18, 128, 226, 235, 39, 178, 117,
    9, 131, 44, 26, 27, 110, 90, 160, 82, 59, 214, 179, 41, 227, 47, 132,
    83, 209, 0, 237, 32, 252, 177, 91, 106, 203, 190, 57, 74, 76, 88, 207,
    208, 239, 170, 251, 67, 77, 51, 133, 69, 249, 2, 127, 80, 60, 159, 168,
    81, 163, 64, 143, 146, 157, 56, 245, 188, 182, 218, 33, 16, 255, 243, 210,
    205, 12, 19, 236, 95, 151, 68, 23, 196, 167, 126, 61, 100, 93, 25, 115,
    96, 129, 79, 220, 34, 42, 144, 136, 70, 238, 184, 20, 222, 94, 11, 219,
    224, 50, 58, 10, 73, 6, 36, 92, 194, 211, 172, 98, 145, 149, 228, 121,
    231, 200, 55, 109, 141, 213, 78, 169, 108, 86, 244, 234, 101, 122, 174, 8,
    186, 120, 37, 46, 28, 166, 180, 198, 232, 221, 116, 31, 75, 189, 139, 138,
    112, 62, 181, 102, 72, 3, 246, 14, 97, 53, 87, 185, 134, 193, 29, 158,
    225, 248, 152, 17, 105, 217, 142, 148, 155, 30, 135, 233, 206, 85, 40, 223,
    140, 161, 137, 13, 191, 230, 66, 104, 65, 153, 45, 15, 176, 84, 187, 22,
  };

  private static final int[] SHIFT_TAB_I = {0, 13, 10, 7, 4, 1, 14, 11, 8, 5, 2, 15, 12, 9, 6, 3};

  private static final int[] SBOX_I = buildSboxI();
  private static final int[] XTIME = buildXtime();

  private final int[] key; // 176-byte expanded key schedule

  public Aes128(int[] key) {
    this.key = expandKey(key);
  }

  private static int[] buildSboxI() {
    int[] s = new int[256];
    for (int i = 0; i < 256; i++) {
      s[SBOX[i]] = i;
    }
    return s;
  }

  private static int[] buildXtime() {
    int[] x = new int[256];
    for (int i = 0; i < 128; i++) {
      x[i] = i << 1;
      x[128 + i] = (i << 1) ^ 0x1b;
    }
    return x;
  }

  private static int[] expandKey(int[] key) {
    int[] ex = new int[176];
    for (int i = 0; i < 16; i++) {
      ex[i] = key[i];
    }
    int rcon = 1;
    for (int i = 16; i < 176; i += 4) {
      int[] tmp = {ex[i - 4], ex[i - 3], ex[i - 2], ex[i - 1]};
      if (i % 16 == 0) {
        tmp = new int[] {SBOX[tmp[1]] ^ rcon, SBOX[tmp[2]], SBOX[tmp[3]], SBOX[tmp[0]]};
        rcon = XTIME[rcon];
      }
      for (int j = 0; j < 4; j++) {
        ex[i + j] = ex[i + j - 16] ^ tmp[j];
      }
    }
    return ex;
  }

  private void addRoundKey(int[] state, int off) {
    for (int i = 0; i < 16; i++) {
      state[i] ^= key[off + i];
    }
  }

  private void shiftSubAdd(int[] state, int off) {
    int[] s0 = copyBlock(state);
    for (int i = 0; i < 16; i++) {
      state[i] = SBOX_I[s0[SHIFT_TAB_I[i]]] ^ key[off + i];
    }
  }

  private void shiftSubAddI(int[] state, int off) {
    int[] s0 = copyBlock(state);
    for (int i = 0; i < 16; i++) {
      state[SHIFT_TAB_I[i]] = SBOX[s0[i] ^ key[off + i]];
    }
  }

  private void mixColumns(int[] state) {
    for (int i = 12; i >= 0; i -= 4) {
      int s0 = state[i], s1 = state[i + 1], s2 = state[i + 2], s3 = state[i + 3];
      int h = s0 ^ s1 ^ s2 ^ s3;
      state[i] ^= h ^ XTIME[s0 ^ s1];
      state[i + 1] ^= h ^ XTIME[s1 ^ s2];
      state[i + 2] ^= h ^ XTIME[s2 ^ s3];
      state[i + 3] ^= h ^ XTIME[s3 ^ s0];
    }
  }

  private void mixColumnsInv(int[] state) {
    for (int i = 0; i < 16; i += 4) {
      int s0 = state[i], s1 = state[i + 1], s2 = state[i + 2], s3 = state[i + 3];
      int h = s0 ^ s1 ^ s2 ^ s3;
      int xh = XTIME[h];
      int h1 = XTIME[XTIME[xh ^ s0 ^ s2]] ^ h;
      int h2 = XTIME[XTIME[xh ^ s1 ^ s3]] ^ h;
      state[i] ^= h1 ^ XTIME[s0 ^ s1];
      state[i + 1] ^= h2 ^ XTIME[s1 ^ s2];
      state[i + 2] ^= h1 ^ XTIME[s2 ^ s3];
      state[i + 3] ^= h2 ^ XTIME[s3 ^ s0];
    }
  }

  /** Decrypt the first 16 bytes of {@code block} in place; returns {@code block}. */
  public int[] decrypt(int[] block) {
    addRoundKey(block, 160);
    for (int i = 144; i >= 16; i -= 16) {
      shiftSubAdd(block, i);
      mixColumnsInv(block);
    }
    shiftSubAdd(block, 0);
    return block;
  }

  /** Encrypt the first 16 bytes of {@code block} in place; returns {@code block}. */
  public int[] encrypt(int[] block) {
    shiftSubAddI(block, 0);
    for (int i = 16; i < 160; i += 16) {
      mixColumns(block);
      shiftSubAddI(block, i);
    }
    addRoundKey(block, 160);
    return block;
  }

  private static int[] copyBlock(int[] state) {
    int[] s = new int[16];
    System.arraycopy(state, 0, s, 0, 16);
    return s;
  }
}
