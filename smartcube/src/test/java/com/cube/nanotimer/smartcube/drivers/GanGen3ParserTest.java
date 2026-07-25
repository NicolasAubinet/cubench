package com.cube.nanotimer.smartcube.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.Face;
import java.util.List;
import org.junit.Test;

/**
 * Gen3, and with it the move buffering {@link GanBufferedParser} does for Gen3 and Gen4 alike:
 * a packet carries a single move, so a gap is recovered from the cube's own history.
 */
public class GanGen3ParserTest {

  private static final String U_FACELET =
      "UUUUUUUUUBBBRRRRRRRRRFFFFFFDDDDDDDDDFFFLLLLLLLLLBBBBBB";

  /** Face code for U, as a move packet and as move history pack it. */
  private static final int U_MOVE_CODE = 2;
  private static final int U_HISTORY_CODE = 1;

  private static GanGen3Parser newParser() {
    return new GanGen3Parser(GanTestPacket.mac());
  }

  private static GanTestPacket packet(int eventType, int dataLength) {
    return new GanTestPacket(20).put(0, 8, 0x55).put(8, 8, eventType).put(16, 8, dataLength);
  }

  private static int[] facelets(int serial) {
    return packet(0x02, 16).putLe(24, 2, serial).putSolvedPieces(40, 77).encrypted();
  }

  private static int[] move(int serial, int cubeTimeMs) {
    return packet(0x01, 8).putLe(24, 4, cubeTimeMs).putLe(56, 2, serial)
        .put(72, 2, 0).put(74, 6, U_MOVE_CODE).encrypted();
  }

  /** A history reply: {@code count} moves running back from {@code startSerial}, newest first. */
  private static int[] history(int startSerial, int count) {
    GanTestPacket packet = packet(0x06, count / 2 + 1).put(24, 8, startSerial);
    for (int i = 0; i < count; i++) {
      packet.put(32 + 4 * i, 3, U_HISTORY_CODE);
      packet.put(35 + 4 * i, 1, 0);
    }
    return packet.encrypted();
  }

  @Test
  public void faceletsPacketDecodesToSolvedCubeAndAnchors() {
    GanGen3Parser parser = newParser();
    assertTrue(parser.needsAnchor());

    List<GanEvent> events = parser.parse(facelets(5), 1000);

    assertEquals(1, events.size());
    assertEquals(CubieCube.SOLVED_FACELET,
        ((GanEvent.StateEvent) events.get(0)).getState().getFacelets());
    assertFalse(parser.needsAnchor());
  }

  @Test
  public void aPacketWithoutTheMagicByteIsDropped() {
    int[] packet = new GanTestPacket(20).put(0, 8, 0x54).put(8, 8, 0x02).put(16, 8, 16).encrypted();
    assertTrue(newParser().parse(packet, 1000).isEmpty());
  }

  @Test
  public void movePacketAfterAnchorYieldsMoveAndResultingState() {
    GanGen3Parser parser = newParser();
    parser.parse(facelets(5), 1000);

    List<GanEvent> events = parser.parse(move(6, 4200), 1500);

    assertEquals(1, events.size());
    GanEvent.MoveEvent event = (GanEvent.MoveEvent) events.get(0);
    assertEquals(Face.U, event.getMove().getFace());
    assertFalse(event.getMove().isPrime());
    assertEquals(U_FACELET, event.getStateAfter().getFacelets());
  }

  @Test
  public void moveBeforeAnyAnchorIsIgnored() {
    assertTrue(newParser().parse(move(6, 4200), 1000).isEmpty());
  }

  /**
   * The cube's clock counts from its own power-on, which means nothing to the callers reading these
   * stamps, so it is carried onto host time.
   */
  @Test
  public void theCubeClockIsFittedToHostTime() {
    GanGen3Parser parser = newParser();
    parser.parse(facelets(5), 1000);

    GanEvent.MoveEvent first = (GanEvent.MoveEvent) parser.parse(move(6, 4200), 100000).get(0);
    GanEvent.MoveEvent second = (GanEvent.MoveEvent) parser.parse(move(7, 4700), 100500).get(0);

    assertEquals(100000, first.getMove().getCubeTimestampMs());
    // The cube's own 500ms gap is what separates them, not the host's arrival times.
    assertEquals(100500, second.getMove().getCubeTimestampMs());
  }

  @Test
  public void aGapHoldsTheMoveAndAsksForHistory() {
    GanGen3Parser parser = newParser();
    parser.parse(facelets(1), 1000);

    List<GanEvent> events = parser.parse(move(3, 4200), 1500); // serial 2 never arrived

    assertEquals(1, events.size());
    GanEvent.HistoryRequestEvent request = (GanEvent.HistoryRequestEvent) events.get(0);
    assertEquals(3, request.getSerial());
    assertEquals(2, request.getCount());
    assertEquals(CubieCube.SOLVED_FACELET, parser.getCurrentState().getFacelets()); // still held back
  }

  @Test
  public void theHistoryReplyReleasesTheHeldMovesInOrder() {
    GanGen3Parser parser = newParser();
    parser.parse(facelets(1), 1000);
    parser.parse(move(3, 4200), 1500); // held: serial 2 is missing

    List<GanEvent> events = parser.parse(history(3, 2), 1600);

    assertEquals(2, events.size()); // the recovered serial 2, then the queued serial 3
    for (GanEvent event : events) {
      assertEquals(Face.U, ((GanEvent.MoveEvent) event).getMove().getFace());
    }
  }

  /** A move recovered from history was never seen live, so it carries no host timestamp. */
  @Test
  public void recoveredMovesCarryNoHostTimestamp() {
    GanGen3Parser parser = newParser();
    parser.parse(facelets(1), 1000);
    parser.parse(move(3, 4200), 1500);

    List<GanEvent> events = parser.parse(history(3, 2), 1600);

    assertEquals(null, ((GanEvent.MoveEvent) events.get(0)).getMove().getHostTimestampMs());
    assertEquals(Long.valueOf(1500),
        ((GanEvent.MoveEvent) events.get(1)).getMove().getHostTimestampMs());
  }

  /** Mid-turn the model is expected to trail the cube, so a facelets snapshot then proves nothing. */
  @Test
  public void faceletsArrivingRightAfterAMoveAreIgnored() {
    GanGen3Parser parser = newParser();
    parser.parse(facelets(5), 1000);
    parser.parse(move(6, 4200), 1500);

    assertTrue(parser.parse(facelets(6), 1600).isEmpty()); // within the settle window
  }

  @Test
  public void aFaceletsSnapshotRunningAheadOfTheModelAsksForTheMissedMoves() {
    GanGen3Parser parser = newParser();
    parser.parse(facelets(1), 1000);

    List<GanEvent> events = parser.parse(facelets(3), 5000); // two moves never reached us

    assertEquals(1, events.size());
    GanEvent.HistoryRequestEvent request = (GanEvent.HistoryRequestEvent) events.get(0);
    assertEquals(4, request.getSerial());
    assertEquals(3, request.getCount());
  }

  @Test
  public void batteryPacketDecodesToLevel() {
    GanGen3Parser parser = newParser();
    int[] packet = packet(0x10, 2).put(24, 8, 64).encrypted();

    assertEquals(64, ((GanEvent.BatteryEvent) parser.parse(packet, 1000).get(0)).getLevel());
    assertEquals(Integer.valueOf(64), parser.getBatteryLevel());
  }

  @Test
  public void hardwarePacketReportsNameAndVersions() {
    GanTestPacket packet = packet(0x07, 12)
        .put(72, 4, 1).put(76, 4, 2)   // software 1.2
        .put(80, 4, 3).put(84, 4, 4);  // hardware 3.4
    String name = "GAN3v";
    for (int i = 0; i < name.length(); i++) {
      packet.put(32 + i * 8, 8, name.charAt(i));
    }

    GanEvent.InfoEvent info =
        (GanEvent.InfoEvent) newParser().parse(packet.encrypted(), 1000).get(0);

    assertEquals("GAN3v", info.getHardwareName());
    assertEquals("3.4", info.getHardwareVersion());
    assertEquals("1.2", info.getSoftwareVersion());
    assertFalse(info.isGyroSupported()); // no Gen3 cube has a gyro
  }

  @Test
  public void disconnectPacketEndsTheSession() {
    int[] packet = packet(0x11, 1).encrypted();
    assertTrue(newParser().parse(packet, 1000).get(0) instanceof GanEvent.DisconnectEvent);
  }

  @Test
  public void aZeroLengthPacketIsDropped() {
    assertTrue(newParser().parse(packet(0x02, 0).encrypted(), 1000).isEmpty());
  }

  @Test
  public void requestsGoOutUnderTheGen3Prefix() {
    int[] facelets = GanTestPacket.cipher().decode(newParser().encodeRequest(GanRequest.FACELETS));
    assertEquals(0x68, facelets[0]);
    assertEquals(0x01, facelets[1]);
    int[] reset = GanTestPacket.cipher().decode(newParser().encodeRequest(GanRequest.RESET));
    assertEquals(0x05, reset[1]);
    assertEquals(0x39, reset[3]);
  }

  /**
   * History replies are byte-aligned: they start on an odd serial and carry an even number of
   * moves, whatever was asked for, so the request is aligned to what will come back.
   */
  @Test
  public void theHistoryWindowIsAlignedToWhatTheCubeWillSendBack() {
    int[] msg = GanTestPacket.cipher().decode(newParser().encodeMoveHistory(4, 3));
    assertEquals(0x03, msg[1]);
    assertEquals(3, msg[2]); // an even serial is pulled back to the odd one below it
    assertEquals(4, msg[4]); // an odd count is rounded up
  }

  /** Asking past the 255→0 edge returns zero bytes that decode as moves that never happened. */
  @Test
  public void theHistoryWindowNeverReachesPastSerialZero() {
    int[] msg = GanTestPacket.cipher().decode(newParser().encodeMoveHistory(3, 8));
    assertEquals(3, msg[2]);
    assertEquals(4, msg[4]); // capped at serial + 1
  }
}
