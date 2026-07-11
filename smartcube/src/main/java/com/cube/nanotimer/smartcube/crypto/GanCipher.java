package com.cube.nanotimer.smartcube.crypto;

/**
 * The GAN Gen2/3 encryption scheme shared by GAN cubes and the MoYu V10, ported from
 * csTimer's moyu32cube.js / gancube.js (GPL-3.0). A packet is transformed by AES-128
 * over its first 16-byte block and, when longer than 16 bytes, its last 16-byte block,
 * each XOR'd with a 16-byte IV. The per-cube key and IV are derived from a base key/IV
 * plus the cube's MAC — see {@link #forMac}. All buffers are unsigned bytes held as ints.
 */
public final class GanCipher {

  private final Aes128 aes;
  private final int[] iv;

  public GanCipher(int[] key, int[] iv) {
    this.aes = new Aes128(key);
    this.iv = iv.clone();
  }

  /**
   * Derive a per-cube cipher: {@code key[i] = (baseKey[i] + mac[5-i]) % 255} for the
   * first 6 bytes (same for the IV), MAC in natural byte order.
   */
  public static GanCipher forMac(int[] baseKey, int[] baseIv, int[] mac) {
    int[] key = baseKey.clone();
    int[] iv = baseIv.clone();
    for (int i = 0; i < 6; i++) {
      key[i] = (key[i] + mac[5 - i]) % 255;
      iv[i] = (iv[i] + mac[5 - i]) % 255;
    }
    return new GanCipher(key, iv);
  }

  /** Decrypt a received packet; returns the plaintext bytes (the caller's buffer is untouched). */
  public int[] decode(int[] data) {
    int[] ret = data.clone();
    if (ret.length > 16) {
      int off = ret.length - 16;
      int[] block = aes.decrypt(slice(ret, off));
      for (int i = 0; i < 16; i++) {
        ret[off + i] = block[i] ^ iv[i];
      }
    }
    aes.decrypt(ret);
    for (int i = 0; i < 16; i++) {
      ret[i] ^= iv[i];
    }
    return ret;
  }

  /** Encrypt a request packet; returns the ciphertext bytes (the caller's buffer is untouched). */
  public int[] encode(int[] data) {
    int[] ret = data.clone();
    for (int i = 0; i < 16; i++) {
      ret[i] ^= iv[i];
    }
    aes.encrypt(ret);
    if (ret.length > 16) {
      int off = ret.length - 16;
      int[] block = slice(ret, off);
      for (int i = 0; i < 16; i++) {
        block[i] ^= iv[i];
      }
      aes.encrypt(block);
      for (int i = 0; i < 16; i++) {
        ret[off + i] = block[i];
      }
    }
    return ret;
  }

  /**
   * Parse a MAC like {@code "CF:30:16:00:AB:CD"} into its 6 bytes. Any single separator
   * works (two hex digits are read every 3 characters).
   */
  public static int[] macBytes(String mac) {
    int[] out = new int[6];
    for (int i = 0; i < 6; i++) {
      out[i] = Integer.parseInt(mac.substring(i * 3, i * 3 + 2), 16);
    }
    return out;
  }

  private static int[] slice(int[] src, int from) {
    int[] out = new int[src.length - from];
    System.arraycopy(src, from, out, 0, out.length);
    return out;
  }
}
