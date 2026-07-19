package com.cube.nanotimer.smartcube.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.crypto.GanCipher;
import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.Face;
import java.util.List;
import org.junit.Test;

/**
 * Ports the sibling package's moyu_v10_parser_test.dart. The packets are encrypted by
 * csTimer's cipher for MAC CF:30:16:00:AB:CD (c164 = battery 77, c163 = solved cube with
 * move counter 5, c165 = one U move with counter 6), so they pin the whole decode path.
 */
public class MoyuV10ParserTest {

  private static final int[] C164 = {183, 222, 38, 107, 141, 80, 66, 141, 196, 87, 86, 24, 165, 186, 206, 194, 224, 45, 123, 22};
  private static final int[] C163 = {20, 81, 108, 156, 152, 10, 152, 58, 229, 121, 98, 221, 11, 123, 49, 53, 221, 107, 154, 186};
  private static final int[] C165 = {223, 209, 150, 204, 116, 21, 65, 40, 149, 201, 145, 0, 11, 185, 99, 221, 222, 17, 54, 129};

  private static final String U_FACELET = "UUUUUUUUUBBBRRRRRRRRRFFFFFFDDDDDDDDDFFFLLLLLLLLLBBBBBB";

  private static MoyuV10Parser newParser() {
    return new MoyuV10Parser(GanCipher.macBytes("CF:30:16:00:AB:CD"));
  }

  @Test
  public void batteryPacketDecodesToLevel() {
    List<MoyuEvent> events = newParser().parse(C164, 1000);
    assertEquals(1, events.size());
    assertTrue(events.get(0) instanceof MoyuEvent.BatteryEvent);
    assertEquals(77, ((MoyuEvent.BatteryEvent) events.get(0)).getLevel());
  }

  @Test
  public void statePacketDecodesToSolvedCubeAndAnchors() {
    List<MoyuEvent> events = newParser().parse(C163, 1000);
    assertEquals(1, events.size());
    MoyuEvent.StateEvent event = (MoyuEvent.StateEvent) events.get(0);
    assertEquals(CubieCube.SOLVED_FACELET, event.getState().getFacelets());
  }

  @Test
  public void movePacketAfterStateAnchorYieldsMoveAndResultingState() {
    MoyuV10Parser parser = newParser();
    parser.parse(C163, 1000); // anchor at solved, moveCnt 5
    List<MoyuEvent> events = parser.parse(C165, 1500); // one U move, moveCnt 6

    assertEquals(1, events.size());
    MoyuEvent.MoveEvent event = (MoyuEvent.MoveEvent) events.get(0);
    assertEquals(Face.U, event.getMove().getFace());
    assertFalse(event.getMove().isPrime());
    assertEquals(U_FACELET, event.getStateAfter().getFacelets());
    assertEquals(U_FACELET, parser.getCurrentState().getFacelets());
  }

  @Test
  public void moveBeforeAnyStateAnchorIsIgnored() {
    assertTrue(newParser().parse(C165, 1000).isEmpty());
  }

  @Test
  public void resetAnchorLetsALaterStatePacketReAnchor() {
    MoyuV10Parser parser = newParser();
    parser.parse(C163, 1000);
    assertTrue(parser.parse(C163, 1100).isEmpty()); // already anchored → ignored
    parser.resetAnchor();
    assertEquals(1, parser.parse(C163, 1200).size()); // re-anchors
  }

  @Test
  public void normalMoveDoesNotRequestResync() {
    MoyuV10Parser parser = newParser();
    parser.parse(C163, 1000); // anchor at moveCnt 5
    parser.parse(C165, 1500); // one move -> moveCnt 6
    assertFalse(parser.pollNeedsResync());
  }

  @Test
  public void isAnchoredTracksTheAnchorState() {
    MoyuV10Parser parser = newParser();
    assertFalse(parser.isAnchored());   // no state yet
    parser.parse(C163, 1000);
    assertTrue(parser.isAnchored());
    parser.resetAnchor();
    assertFalse(parser.isAnchored());   // dropping moves until a fresh state arrives
    parser.parse(C163, 1100);
    assertTrue(parser.isAnchored());    // re-anchored
  }

  @Test
  public void largeMoveGapRequestsResyncOnce() {
    MoyuV10Parser parser = newParser();
    parser.parse(C163, 1000);           // anchor at moveCnt 5
    parser.parse(movePacket(12), 1500); // jumped 7 moves, more than a packet carries
    assertTrue(parser.pollNeedsResync());
    assertFalse(parser.pollNeedsResync()); // clears on read
  }

  /**
   * Real gyro packets captured off a WeiLong V10 (probe 4.11a), re-encrypted here because the
   * capture logged them post-decrypt. Cube held still, then after a ~90° turn, then after a
   * second perpendicular one.
   */
  private static final int[] G_REST = {171, 243, 138, 199, 63, 227, 155, 185, 250, 98, 8, 112, 255, 208, 53, 50, 0, 0, 0, 0};
  private static final int[] G_QUARTER = {171, 226, 72, 253, 46, 9, 2, 163, 255, 255, 164, 159, 255, 241, 83, 141, 212, 0, 0, 0};
  private static final int[] G_DIAGONAL = {171, 206, 111, 112, 31, 217, 28, 101, 223, 103, 39, 232, 223, 226, 3, 38, 224, 0, 0, 0};

  private static CubeOrientation gyro(int[] plain) {
    List<MoyuEvent> events = newParser().parse(encrypted(plain), 1000);
    assertEquals(1, events.size());
    return ((MoyuEvent.GyroEvent) events.get(0)).getOrientation();
  }

  @Test
  public void gyroPacketDecodesToUnitQuaternion() {
    CubeOrientation rest = gyro(G_REST);
    assertEquals(0.9966, rest.getW(), 1e-4);
    assertEquals(-0.0824, rest.getX(), 1e-4);
    assertEquals(-0.0088, rest.getY(), 1e-4);
    assertEquals(0.0031, rest.getZ(), 1e-4);
  }

  @Test
  public void everyGyroPacketIsNormalised() {
    for (int[] packet : new int[][] {G_REST, G_QUARTER, G_DIAGONAL}) {
      assertEquals(1.0, gyro(packet).normSquared(), 1e-6);
    }
  }

  @Test
  public void gyroTracksPhysicalRotation() {
    assertEquals(9.5, gyro(G_REST).angleDegrees(), 0.5);        // at rest, near identity
    assertEquals(85.5, gyro(G_QUARTER).angleDegrees(), 0.5);    // one quarter turn of the whole cube
    assertEquals(121.2, gyro(G_DIAGONAL).angleDegrees(), 0.5);  // two perpendicular ones: body diagonal
  }

  /** A quarter turn is a rotation about a single axis: the other two components vanish. */
  @Test
  public void quarterTurnIsASingleAxisRotation() {
    CubeOrientation q = gyro(G_QUARTER);
    assertEquals(0.0, q.getX(), 0.01);
    assertEquals(0.0, q.getY(), 0.01);
    assertEquals(-0.679, q.getZ(), 1e-3);
  }

  private static int[] encrypted(int[] plain) {
    return GanCipher.forMac(BASE_KEY, BASE_IV, GanCipher.macBytes("CF:30:16:00:AB:CD")).encode(plain);
  }

  // MoYu V10 base key/IV (same constants the parser derives its cipher from).
  private static final int[] BASE_KEY = {21, 119, 58, 92, 103, 14, 45, 31, 23, 103, 42, 19, 155, 103, 82, 87};
  private static final int[] BASE_IV = {17, 35, 38, 37, 134, 42, 44, 59, 85, 6, 127, 49, 126, 103, 33, 87};

  /** An encrypted 165 packet with the given move counter and five (valid, zero-code) moves. */
  private static int[] movePacket(int moveCnt) {
    int[] plain = new int[20];
    plain[0] = 165;
    plain[11] = moveCnt; // move counter lives in bits 88..96
    return encrypted(plain);
  }
}
