package com.cube.nanotimer.smartcube.drivers;

import com.cube.nanotimer.smartcube.model.CubeOrientation;

/**
 * The orientation quaternion a GAN cube streams, decoded from the one layout Gen2 and Gen4 share:
 * four 16-bit components in {@code w, x, y, z} order, each a sign bit and a magnitude out of
 * {@code 0x7FFF}. Only the offset at which they start differs.
 *
 * <p>Ported from {@code afedotov/gan-web-bluetooth} (MIT).
 *
 * <p>The components are kept exactly as the cube reports them, in the gyro's own axes.
 * {@link com.cube.nanotimer.smartcube.model.CubeRotation} relabels those onto the cube's R/U/F
 * axes when it interprets a reading, and the swap it makes is the same one GAN's own sample
 * applies — so a reading that went through here needs no further correction.
 */
final class GanGyro {

  /** Bit width of one component, and the magnitude a full-scale one carries. */
  private static final int COMPONENT_BITS = 16;
  private static final int FULL_SCALE = 0x7FFF;

  /** How many bits of packet a reading occupies, for the caller's length guard. */
  static final int BITS = 4 * COMPONENT_BITS;

  private GanGyro() {
  }

  /** Read a reading whose first component starts at {@code firstBit}. */
  static CubeOrientation decode(GanPacket packet, int firstBit) {
    return new CubeOrientation(
        component(packet.val(firstBit, COMPONENT_BITS)),
        component(packet.val(firstBit + COMPONENT_BITS, COMPONENT_BITS)),
        component(packet.val(firstBit + 2 * COMPONENT_BITS, COMPONENT_BITS)),
        component(packet.val(firstBit + 3 * COMPONENT_BITS, COMPONENT_BITS)));
  }

  /** Sign and magnitude, not two's complement: the top bit is the sign on its own. */
  private static double component(int raw) {
    double magnitude = (double) (raw & FULL_SCALE) / FULL_SCALE;
    return (raw & 0x8000) != 0 ? -magnitude : magnitude;
  }
}
