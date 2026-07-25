package com.cube.nanotimer.smartcube.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import java.util.List;
import org.junit.Test;

/**
 * The orientation stream, decoded the same way for Gen2 and Gen4 and only read at a different
 * offset. The frame tests are the ones that matter: bit offsets that are merely wrong give
 * nonsense, but axes that are wrong give plausible rotations with the wrong letters.
 */
public class GanGyroTest {

  private static final double HALF_ROOT_TWO = Math.sqrt(2) / 2;

  /** Gen2 puts the quaternion right after its 4-bit event type; Gen4 after its two header bytes. */
  private static final int GEN2_GYRO_BIT = 4;
  private static final int GEN4_GYRO_BIT = 16;

  private static CubeOrientation gen2(double w, double x, double y, double z) {
    int[] packet = new GanTestPacket(20).put(0, 4, 0x01)
        .putQuaternion(GEN2_GYRO_BIT, w, x, y, z).encrypted();
    List<GanEvent> events = new GanGen2Parser(GanTestPacket.mac(), false).parse(packet, 1000);
    assertEquals(1, events.size());
    return ((GanEvent.GyroEvent) events.get(0)).getOrientation();
  }

  private static CubeOrientation gen4(double w, double x, double y, double z) {
    int[] packet = new GanTestPacket(20).put(0, 8, 0xEC).put(8, 8, 16)
        .putQuaternion(GEN4_GYRO_BIT, w, x, y, z).encrypted();
    List<GanEvent> events = new GanGen4Parser(GanTestPacket.mac()).parse(packet, 1000);
    assertEquals(1, events.size());
    return ((GanEvent.GyroEvent) events.get(0)).getOrientation();
  }

  @Test
  public void anIdentityReadingRoundTrips() {
    CubeOrientation q = gen2(1, 0, 0, 0);

    assertEquals(1.0, q.getW(), 1e-4);
    assertEquals(0.0, q.getX(), 1e-4);
    assertEquals(0.0, q.getY(), 1e-4);
    assertEquals(0.0, q.getZ(), 1e-4);
  }

  /** The top bit is a sign on its own, not two's complement. */
  @Test
  public void negativeComponentsCarryTheirSign() {
    CubeOrientation q = gen2(0.5, -0.5, 0.5, -0.5);

    assertEquals(0.5, q.getW(), 1e-4);
    assertEquals(-0.5, q.getX(), 1e-4);
    assertEquals(0.5, q.getY(), 1e-4);
    assertEquals(-0.5, q.getZ(), 1e-4);
  }

  /** Each component is quantized to 15 bits of magnitude, so the norm lands near 1 rather than on it. */
  @Test
  public void everyReadingIsAUnitQuaternion() {
    assertEquals(1.0, gen2(1, 0, 0, 0).normSquared(), 1e-3);
    assertEquals(1.0, gen2(0.5, -0.5, 0.5, -0.5).normSquared(), 1e-3);
    assertEquals(1.0, gen2(HALF_ROOT_TWO, 0, 0, -HALF_ROOT_TWO).normSquared(), 1e-3);
  }

  @Test
  public void gen4ReadsTheSameQuaternionAtItsOwnOffset() {
    CubeOrientation q = gen4(HALF_ROOT_TWO, 0, 0, -HALF_ROOT_TWO);

    assertEquals(HALF_ROOT_TWO, q.getW(), 1e-4);
    assertEquals(0.0, q.getX(), 1e-4);
    assertEquals(0.0, q.getY(), 1e-4);
    assertEquals(-HALF_ROOT_TWO, q.getZ(), 1e-4);
  }

  /**
   * The reading is kept in the gyro's own axes, which {@link CubeRotation} relabels onto the cube's
   * R/U/F. These pin that the two agree: a quarter turn about each gyro axis has to come back
   * spelled the way a cuber spells it. Getting this wrong reads plausibly and replays wrongly.
   */
  @Test
  public void aQuarterTurnAboutEachAxisIsSpelledTheWayACuberSpellsIt() {
    assertRotation("y", gen2(HALF_ROOT_TWO, 0, 0, -HALF_ROOT_TWO));  // about gyro +z, the cube's U
    assertRotation("x", gen2(HALF_ROOT_TWO, -HALF_ROOT_TWO, 0, 0));  // about gyro +x, the cube's R
    assertRotation("z", gen2(HALF_ROOT_TWO, 0, HALF_ROOT_TWO, 0));   // about gyro -y, the cube's F
  }

  @Test
  public void theOppositeTurnsAreSpelledPrime() {
    assertRotation("y'", gen2(HALF_ROOT_TWO, 0, 0, HALF_ROOT_TWO));
    assertRotation("x'", gen2(HALF_ROOT_TWO, HALF_ROOT_TWO, 0, 0));
    assertRotation("z'", gen2(HALF_ROOT_TWO, 0, -HALF_ROOT_TWO, 0));
  }

  @Test
  public void aCubeThatHasNotMovedIsNoRotationAtAll() {
    CubeRotation rotation = CubeRotation.nearest(identity().deltaTo(gen2(1, 0, 0, 0)));

    assertNotNull(rotation);
    assertTrue(rotation.isIdentity());
  }

  /** A reading needs 68 of the 128 bits the shortest real packet carries, so the floor is fine. */
  @Test
  public void theShortestPacketAGanCanSendStillHoldsAReading() {
    int[] packet = new GanTestPacket(16).put(0, 4, 0x01)
        .putQuaternion(GEN2_GYRO_BIT, 1, 0, 0, 0).encrypted();

    List<GanEvent> events = new GanGen2Parser(GanTestPacket.mac(), false).parse(packet, 1000);

    assertEquals(1.0, ((GanEvent.GyroEvent) events.get(0)).getOrientation().getW(), 1e-4);
  }

  private static CubeOrientation identity() {
    return new CubeOrientation(1, 0, 0, 0);
  }

  private static void assertRotation(String expected, CubeOrientation reading) {
    CubeRotation rotation = CubeRotation.nearest(identity().deltaTo(reading));
    assertNotNull("no rotation matched " + reading, rotation);
    assertEquals(expected, rotation.getNotation());
  }
}
