package com.cube.nanotimer.smartcube.crypto;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

/**
 * Ports the sibling package's crypto tests (crypto_test.dart) verbatim, including the
 * captured csTimer byte vectors, so the Java port is pinned to the reference impl.
 */
public class CryptoTest {

  // MoYu V10 base key/IV (LZString-decompressed from moyu32cube.js KEYS).
  private static final int[] BASE_KEY = {21, 119, 58, 92, 103, 14, 45, 31, 23, 103, 42, 19, 155, 103, 82, 87};
  private static final int[] BASE_IV = {17, 35, 38, 37, 134, 42, 44, 59, 85, 6, 127, 49, 126, 103, 33, 87};
  private static final String MAC = "CF:30:16:00:AB:CD";

  @Test
  public void aesFips197KnownAnswer() {
    int[] key = new int[16];
    for (int i = 0; i < 16; i++) {
      key[i] = i;
    }
    int[] pt = {0, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0xaa, 0xbb, 0xcc, 0xdd, 0xee, 0xff};
    int[] ct = {0x69, 0xc4, 0xe0, 0xd8, 0x6a, 0x7b, 0x04, 0x30, 0xd8, 0xcd, 0xb7, 0x80, 0x70, 0xb4, 0xc5, 0x5a};
    assertArrayEquals(ct, new Aes128(key).encrypt(pt.clone()));
    assertArrayEquals(pt, new Aes128(key).decrypt(ct.clone()));
  }

  @Test
  public void aesSingleBlockRoundTripAgainstCsTimerVector() {
    int[] key = new int[16];
    int[] pt = new int[16];
    for (int i = 0; i < 16; i++) {
      key[i] = (i * 7) & 255;
      pt[i] = (i * 13 + 1) & 255;
    }
    int[] ct = {77, 247, 194, 59, 149, 27, 121, 13, 41, 236, 122, 18, 185, 119, 136, 104};
    assertArrayEquals(ct, new Aes128(key).encrypt(pt.clone()));
    assertArrayEquals(pt, new Aes128(key).decrypt(ct.clone()));
  }

  @Test
  public void aesLeavesBytesPastTheFirstBlockUntouched() {
    int[] key = new int[16];
    for (int i = 0; i < 16; i++) {
      key[i] = i;
    }
    int[] block = new int[20];
    for (int i = 0; i < 20; i++) {
      block[i] = i;
    }
    new Aes128(key).encrypt(block);
    assertArrayEquals(new int[] {16, 17, 18, 19}, new int[] {block[16], block[17], block[18], block[19]});
  }

  @Test
  public void macBytesParsesColonSeparatedMac() {
    assertArrayEquals(new int[] {207, 48, 22, 0, 171, 205}, GanCipher.macBytes(MAC));
  }

  @Test
  public void forMacDerivesTheCsTimerKeyAndIv() {
    int[] mac = GanCipher.macBytes(MAC);
    int[] key = BASE_KEY.clone();
    int[] iv = BASE_IV.clone();
    for (int i = 0; i < 6; i++) {
      key[i] = (key[i] + mac[5 - i]) % 255;
      iv[i] = (iv[i] + mac[5 - i]) % 255;
    }
    assertArrayEquals(new int[] {226, 35, 58, 114, 151, 221, 45, 31, 23, 103, 42, 19, 155, 103, 82, 87}, key);
    assertArrayEquals(new int[] {222, 206, 38, 59, 182, 249, 44, 59, 85, 6, 127, 49, 126, 103, 33, 87}, iv);
  }

  @Test
  public void decodeMatchesCsTimerForA20BytePacket() {
    int[] cipher = {92, 208, 61, 75, 172, 41, 136, 124, 41, 183, 170, 101, 62, 114, 54, 134, 65, 179, 86, 153};
    int[] plain = {161, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19};
    GanCipher cipher0 = GanCipher.forMac(BASE_KEY, BASE_IV, GanCipher.macBytes(MAC));
    assertArrayEquals(plain, cipher0.decode(cipher));
  }

  @Test
  public void encodeIsTheInverseOfDecode() {
    GanCipher cipher = GanCipher.forMac(BASE_KEY, BASE_IV, GanCipher.macBytes(MAC));
    int[] plain = {161, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19};
    int[] expectedCipher = {92, 208, 61, 75, 172, 41, 136, 124, 41, 183, 170, 101, 62, 114, 54, 134, 65, 179, 86, 153};
    assertArrayEquals(expectedCipher, cipher.encode(plain));
    assertArrayEquals(plain, cipher.decode(cipher.encode(plain)));
  }

  @Test
  public void doesNotMutateTheCallersBuffer() {
    GanCipher cipher = GanCipher.forMac(BASE_KEY, BASE_IV, GanCipher.macBytes(MAC));
    int[] plain = new int[20];
    java.util.Arrays.fill(plain, 7);
    int[] snapshot = plain.clone();
    cipher.encode(plain);
    assertArrayEquals(snapshot, plain);
  }
}
