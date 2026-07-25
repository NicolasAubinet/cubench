package com.cube.nanotimer.smartcube.drivers;

import com.cube.nanotimer.smartcube.crypto.GanCipher;

/**
 * Builds a GAN packet by writing fields at the same bit offsets the parsers read them from, then
 * encrypts it with the cipher the parser will decrypt with. Round-tripping through the real cipher
 * is what makes these fixtures worth having: a packet that decodes is one the whole path handled.
 */
final class GanTestPacket {

  static final String MAC = "AB:12:34:56:78:9A";

  private final int[] bytes;

  GanTestPacket(int sizeBytes) {
    bytes = new int[sizeBytes];
  }

  /** Write {@code length} bits of {@code value}, most significant first, at bit {@code startBit}. */
  GanTestPacket put(int startBit, int length, int value) {
    for (int i = 0; i < length; i++) {
      int pos = startBit + i;
      int mask = 1 << (7 - pos % 8);
      if (((value >> (length - 1 - i)) & 1) == 1) {
        bytes[pos / 8] |= mask;
      } else {
        bytes[pos / 8] &= ~mask;
      }
    }
    return this;
  }

  /** The same, for a little-endian field of whole bytes. */
  GanTestPacket putLe(int startBit, int byteCount, int value) {
    for (int i = 0; i < byteCount; i++) {
      put(startBit + i * 8, 8, (value >> (i * 8)) & 0xFF);
    }
    return this;
  }

  int[] encrypted() {
    return cipher().encode(bytes.clone());
  }

  static GanCipher cipher() {
    return GanCipher.forMac(GanGen2Parser.BASE_KEY, GanGen2Parser.BASE_IV, GanCipher.macBytes(MAC));
  }

  static int[] mac() {
    return GanCipher.macBytes(MAC);
  }

  /**
   * Write a solved cube's pieces: seven corners from {@code cornerBit}, eleven edges from
   * {@code edgeBit}. The last corner and edge are left out — the cube does not send them.
   *
   * <p>The two offsets are given separately because Gen3/Gen4 leave two bits between the corners
   * and the edges where Gen2 runs them together.
   */
  GanTestPacket putSolvedPieces(int cornerBit, int edgeBit) {
    for (int i = 0; i < 7; i++) {
      put(cornerBit + i * 3, 3, i);         // corner permutation
      put(cornerBit + 21 + i * 2, 2, 0);    // corner orientation
    }
    for (int i = 0; i < 11; i++) {
      put(edgeBit + i * 4, 4, i);           // edge permutation
      put(edgeBit + 44 + i, 1, 0);          // edge orientation
    }
    return this;
  }
}
