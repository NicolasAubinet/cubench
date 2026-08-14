package com.cube.nanotimer.smartcube.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.Face;
import java.util.List;
import org.junit.Test;

/** Ports the sibling package's gan_gen2_parser_test.dart. */
public class GanGen2ParserTest {

  private static final String U_FACELET =
      "UUUUUUUUUBBBRRRRRRRRRFFFFFFDDDDDDDDDFFFLLLLLLLLLBBBBBB";

  private static GanGen2Parser newParser() {
    return new GanGen2Parser(GanTestPacket.mac(), false);
  }

  /** A facelets packet for a solved cube at {@code serial}. */
  private static int[] facelets(int serial) {
    return new GanTestPacket(20).put(0, 4, 0x04).put(4, 8, serial)
        .putSolvedPieces(12, 47).encrypted();
  }

  /** A move packet at {@code serial} carrying {@code count} moves, newest first. */
  private static int[] moves(int serial, int count, int faceCode, boolean prime, int elapsedMs) {
    GanTestPacket packet = new GanTestPacket(20).put(0, 4, 0x02).put(4, 8, serial);
    for (int i = 0; i < count; i++) {
      packet.put(12 + 5 * i, 4, faceCode);
      packet.put(16 + 5 * i, 1, prime ? 1 : 0);
      packet.put(47 + 16 * i, 16, elapsedMs);
    }
    return packet.encrypted();
  }

  @Test
  public void faceletsPacketDecodesToSolvedCubeAndAnchors() {
    GanGen2Parser parser = newParser();
    assertTrue(parser.needsAnchor());

    List<GanEvent> events = parser.parse(facelets(5), 1000);

    assertEquals(1, events.size());
    assertEquals(CubieCube.SOLVED_FACELET,
        ((GanEvent.StateEvent) events.get(0)).getState().getFacelets());
    assertFalse(parser.needsAnchor());
  }

  @Test
  public void movePacketAfterAnchorYieldsMoveAndResultingState() {
    GanGen2Parser parser = newParser();
    parser.parse(facelets(5), 1000);

    List<GanEvent> events = parser.parse(moves(6, 1, 0, false, 500), 1500);

    assertEquals(1, events.size());
    GanEvent.MoveEvent move = (GanEvent.MoveEvent) events.get(0);
    assertEquals(Face.U, move.getMove().getFace());
    assertFalse(move.getMove().isPrime());
    assertEquals(U_FACELET, move.getStateAfter().getFacelets());
    assertEquals(U_FACELET, parser.getCurrentState().getFacelets());
  }

  @Test
  public void moveBeforeAnyAnchorIsIgnored() {
    assertTrue(newParser().parse(moves(6, 1, 0, false, 500), 1000).isEmpty());
  }

  /** The packet carries the last 7 moves, so a smaller gap is recovered from the packet itself. */
  @Test
  public void aGapWithinThePacketReplaysEveryMissedMove() {
    GanGen2Parser parser = newParser();
    parser.parse(facelets(5), 1000);

    List<GanEvent> events = parser.parse(moves(8, 3, 0, false, 100), 1500);

    assertEquals(3, events.size()); // serials 6, 7, 8 — all three U turns replayed in order
    for (GanEvent event : events) {
      assertEquals(Face.U, ((GanEvent.MoveEvent) event).getMove().getFace());
    }
  }

  /** The cube's own clock is the one the moves are spaced by, whenever Android hands them over. */
  @Test
  public void aNotificationDeliveredLateDoesNotMoveTheTimelineBackwards() {
    GanGen2Parser parser = newParser();
    parser.parse(facelets(5), 1000);
    long first = stamp(parser.parse(moves(6, 1, 0, false, 100), 1500));
    long late = stamp(parser.parse(moves(7, 1, 0, false, 200), 4000)); // handed over 2.3 s late
    long prompt = stamp(parser.parse(moves(8, 1, 0, false, 200), 1900));

    assertEquals(1500, first);
    assertEquals(1700, late);
    assertEquals(1900, prompt);
  }

  /** The batch's newest move is the one that arrived: the older ones are dated back from it. */
  @Test
  public void aBatchIsAnchoredByTheMoveThatDeliveredIt() {
    GanGen2Parser parser = newParser();
    parser.parse(facelets(5), 1000);

    List<GanEvent> events = parser.parse(moves(7, 2, 0, false, 100), 1500);

    assertEquals(1400, ((GanEvent.MoveEvent) events.get(0)).getMove().getCubeTimestampMs());
    assertEquals(1500, ((GanEvent.MoveEvent) events.get(1)).getMove().getCubeTimestampMs());
  }

  private static long stamp(List<GanEvent> events) {
    return ((GanEvent.MoveEvent) events.get(events.size() - 1)).getMove().getCubeTimestampMs();
  }

  /** Only the newest move of a packet was seen live; the replayed ones carry no host time. */
  @Test
  public void onlyTheNewestMoveOfAPacketCarriesAHostTimestamp() {
    GanGen2Parser parser = newParser();
    parser.parse(facelets(5), 1000);

    List<GanEvent> events = parser.parse(moves(7, 2, 0, false, 100), 1500);

    assertEquals(2, events.size());
    assertEquals(null, ((GanEvent.MoveEvent) events.get(0)).getMove().getHostTimestampMs());
    assertEquals(Long.valueOf(1500),
        ((GanEvent.MoveEvent) events.get(1)).getMove().getHostTimestampMs());
  }

  @Test
  public void aGapBeyondThePacketDesyncsAndDropsTheAnchor() {
    GanGen2Parser parser = newParser();
    parser.parse(facelets(5), 1000);

    List<GanEvent> events = parser.parse(moves(20, 1, 0, false, 100), 1500);

    assertEquals(1, events.size());
    assertEquals(8, ((GanEvent.DesyncEvent) events.get(0)).getLostMoves()); // 15 missed, 7 recoverable
    assertTrue(parser.needsAnchor()); // moves are dropped until fresh facelets land
  }

  @Test
  public void aStaleFaceletsSnapshotDoesNotRewindTheModel() {
    GanGen2Parser parser = newParser();
    parser.parse(facelets(5), 1000);
    parser.parse(moves(6, 1, 0, false, 500), 1500);

    assertTrue(parser.parse(facelets(5), 2000).isEmpty()); // the cube has moved on since
    assertEquals(U_FACELET, parser.getCurrentState().getFacelets());
  }

  @Test
  public void batteryPacketDecodesToLevel() {
    GanGen2Parser parser = newParser();
    int[] packet = new GanTestPacket(20).put(0, 4, 0x09).put(8, 8, 77).encrypted();

    List<GanEvent> events = parser.parse(packet, 1000);

    assertEquals(77, ((GanEvent.BatteryEvent) events.get(0)).getLevel());
    assertEquals(Integer.valueOf(77), parser.getBatteryLevel());
  }

  @Test
  public void hardwarePacketReportsNameAndGyroSupport() {
    GanTestPacket packet = new GanTestPacket(20).put(0, 4, 0x05)
        .put(8, 8, 1).put(16, 8, 0)   // hardware version 1.0
        .put(24, 8, 2).put(32, 8, 3)  // software version 2.3
        .put(104, 1, 1);              // gyro supported
    String name = "GAN12ui ";
    for (int i = 0; i < name.length(); i++) {
      packet.put(40 + i * 8, 8, name.charAt(i));
    }

    GanEvent.InfoEvent info = (GanEvent.InfoEvent) newParser().parse(packet.encrypted(), 1000).get(0);

    assertEquals("GAN12ui", info.getHardwareName());
    assertEquals("1.0", info.getHardwareVersion());
    assertEquals("2.3", info.getSoftwareVersion());
    assertTrue(info.isGyroSupported());
  }

  /** What the driver makes of it is its business — see GanScannerTest; here it need only decode. */
  @Test
  public void theCubeCanAnnounceItIsPoweringDown() {
    int[] packet = new GanTestPacket(20).put(0, 4, 0x0D).encrypted();
    assertTrue(newParser().parse(packet, 1000).get(0) instanceof GanEvent.DisconnectEvent);
  }

  /**
   * Raw radio input: a notification too short to hold what its header claims must be dropped, never
   * throw. Seven moves need 159 bits and this packet has 128.
   */
  @Test
  public void aMovePacketTooShortForTheMovesItClaimsIsDropped() {
    GanGen2Parser parser = newParser();
    parser.parse(facelets(5), 1000);

    int[] packet = new GanTestPacket(16).put(0, 4, 0x02).put(4, 8, 12).encrypted();

    assertTrue(parser.parse(packet, 1500).isEmpty());
  }

  @Test
  public void noOpcodeThrowsOnAMinimumSizePacket() {
    for (int opcode = 0; opcode < 16; opcode++) {
      newParser().parse(new GanTestPacket(16).put(0, 4, opcode).encrypted(), 1000);
    }
  }

  @Test
  public void resetRequestIsTheFixedPayloadNotABareOpcode() {
    int[] reset = GanTestPacket.cipher().decode(newParser().encodeRequest(GanRequest.RESET));
    assertEquals(0x0A, reset[0]);
    assertEquals(0x05, reset[1]);
    assertEquals(0x39, reset[2]);
  }

  @Test
  public void requestOpcodesGoOutEncrypted() {
    int[] facelets = GanTestPacket.cipher().decode(newParser().encodeRequest(GanRequest.FACELETS));
    assertEquals(0x04, facelets[0]);
    int[] battery = GanTestPacket.cipher().decode(newParser().encodeRequest(GanRequest.BATTERY));
    assertEquals(0x09, battery[0]);
  }

  /** Gen2 packets carry their own history, so the driver never asks for more. */
  @Test
  public void moveHistoryIsNeverRequested() {
    assertEquals(null, newParser().encodeMoveHistory(5, 2));
  }

  /** The MoYu AI 2023 speaks Gen2 with its own key, so the base-key cipher cannot read it. */
  @Test
  public void moyuAiUsesADifferentKey() {
    GanGen2Parser moyu = new GanGen2Parser(GanTestPacket.mac(), true);
    assertFalse(java.util.Arrays.equals(
        moyu.encodeRequest(GanRequest.FACELETS), newParser().encodeRequest(GanRequest.FACELETS)));
  }
}
