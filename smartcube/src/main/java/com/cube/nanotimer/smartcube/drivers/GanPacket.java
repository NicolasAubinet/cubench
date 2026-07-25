package com.cube.nanotimer.smartcube.drivers;

/**
 * A decrypted GAN packet, read as a bit string. Every GAN generation packs its fields at bit
 * offsets that cross byte boundaries, so the protocol documentation — and the reference
 * implementations it came from — address them as (offset, length) in bits; this reads them the
 * same way rather than making each parser translate to shifts and masks.
 *
 * <p>Reads are guarded by {@link #has}: this runs on raw radio input, and a truncated notification
 * must be dropped rather than throw.
 */
final class GanPacket {

  private final String bits;

  GanPacket(int[] data) {
    StringBuilder sb = new StringBuilder(data.length * 8);
    for (int b : data) {
      String s = Integer.toBinaryString(b & 0xff);
      for (int p = s.length(); p < 8; p++) {
        sb.append('0');
      }
      sb.append(s);
    }
    bits = sb.toString();
  }

  /** The unsigned value of {@code length} bits starting at bit {@code start}. */
  int val(int start, int length) {
    return Integer.parseInt(bits.substring(start, start + length), 2);
  }

  /** The same, for a little-endian field of whole bytes. */
  int valLe(int start, int bytes) {
    int v = 0;
    for (int i = 0; i < bytes; i++) {
      v |= val(start + i * 8, 8) << (i * 8);
    }
    return v;
  }

  /** Whether the packet is long enough to hold {@code bitCount} bits. */
  boolean has(int bitCount) {
    return bits.length() >= bitCount;
  }

  int bitLength() {
    return bits.length();
  }

  /** {@code length} bytes read as characters from bit {@code start}, trimmed. */
  String text(int start, int length) {
    StringBuilder name = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      name.append((char) val(start + i * 8, 8));
    }
    return name.toString().trim();
  }
}
