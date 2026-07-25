package com.cube.nanotimer.smartcube.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.Face;
import java.util.List;
import org.junit.Test;

/**
 * Gen4. The move buffering it shares with Gen3 is covered by {@link GanGen3ParserTest}; what is
 * pinned here is Gen4's own framing — one byte shorter — and the hardware info it dribbles out
 * across four separate events.
 */
public class GanGen4ParserTest {

  private static final String U_FACELET =
      "UUUUUUUUUBBBRRRRRRRRRFFFFFFDDDDDDDDDFFFLLLLLLLLLBBBBBB";

  private static final int U_MOVE_CODE = 2;
  private static final int U_HISTORY_CODE = 1;

  private static GanGen4Parser newParser() {
    return new GanGen4Parser(GanTestPacket.mac());
  }

  private static GanTestPacket packet(int eventType, int dataLength) {
    return new GanTestPacket(20).put(0, 8, eventType).put(8, 8, dataLength);
  }

  private static int[] facelets(int serial) {
    return packet(0xED, 16).putLe(16, 2, serial).putSolvedPieces(32, 69).encrypted();
  }

  private static int[] move(int serial, int cubeTimeMs) {
    return packet(0x01, 8).putLe(16, 4, cubeTimeMs).putLe(48, 2, serial)
        .put(64, 2, 0).put(66, 6, U_MOVE_CODE).encrypted();
  }

  @Test
  public void faceletsPacketDecodesToSolvedCubeAndAnchors() {
    GanGen4Parser parser = newParser();
    assertTrue(parser.needsAnchor());

    List<GanEvent> events = parser.parse(facelets(5), 1000);

    assertEquals(1, events.size());
    assertEquals(CubieCube.SOLVED_FACELET,
        ((GanEvent.StateEvent) events.get(0)).getState().getFacelets());
    assertFalse(parser.needsAnchor());
  }

  @Test
  public void movePacketAfterAnchorYieldsMoveAndResultingState() {
    GanGen4Parser parser = newParser();
    parser.parse(facelets(5), 1000);

    List<GanEvent> events = parser.parse(move(6, 4200), 1500);

    assertEquals(1, events.size());
    GanEvent.MoveEvent event = (GanEvent.MoveEvent) events.get(0);
    assertEquals(Face.U, event.getMove().getFace());
    assertEquals(U_FACELET, event.getStateAfter().getFacelets());
  }

  @Test
  public void theHistoryReplyReleasesTheHeldMovesInOrder() {
    GanGen4Parser parser = newParser();
    parser.parse(facelets(1), 1000);
    parser.parse(move(3, 4200), 1500); // held: serial 2 is missing

    GanTestPacket history = packet(0xD1, 2).put(16, 8, 3);
    for (int i = 0; i < 2; i++) {
      history.put(24 + 4 * i, 3, U_HISTORY_CODE);
      history.put(27 + 4 * i, 1, 0);
    }

    List<GanEvent> events = parser.parse(history.encrypted(), 1600);

    assertEquals(2, events.size());
    for (GanEvent event : events) {
      assertEquals(Face.U, ((GanEvent.MoveEvent) event).getMove().getFace());
    }
  }

  @Test
  public void batteryPacketDecodesToLevel() {
    GanGen4Parser parser = newParser();
    int[] packet = packet(0xEF, 2).put(24, 8, 42).encrypted();

    assertEquals(42, ((GanEvent.BatteryEvent) parser.parse(packet, 1000).get(0)).getLevel());
  }

  /** Hardware info is reported only once every piece has landed. */
  @Test
  public void hardwareInfoIsReportedOnlyOnceAllFourPiecesLand() {
    GanGen4Parser parser = newParser();

    assertTrue(parser.parse(productDate(), 1000).isEmpty());
    assertTrue(parser.parse(version(0xFD, 1, 2), 1000).isEmpty());
    assertTrue(parser.parse(version(0xFE, 3, 4), 1000).isEmpty());
    List<GanEvent> events = parser.parse(hardwareName("GAN12uiM"), 1000);

    assertEquals(1, events.size());
    GanEvent.InfoEvent info = (GanEvent.InfoEvent) events.get(0);
    assertEquals("GAN12uiM", info.getHardwareName());
    assertEquals("3.4", info.getHardwareVersion());
    assertEquals("1.2", info.getSoftwareVersion());
    assertTrue(info.isGyroSupported()); // the one Gen4 cube with a gyro
  }

  @Test
  public void anotherGen4ModelReportsNoGyro() {
    GanGen4Parser parser = newParser();
    parser.parse(productDate(), 1000);
    parser.parse(version(0xFD, 1, 2), 1000);
    parser.parse(version(0xFE, 3, 4), 1000);

    GanEvent.InfoEvent info =
        (GanEvent.InfoEvent) parser.parse(hardwareName("GAN14ui"), 1000).get(0);

    assertFalse(info.isGyroSupported());
  }

  @Test
  public void requestsGoOutUnderTheGen4Opcodes() {
    int[] facelets = GanTestPacket.cipher().decode(newParser().encodeRequest(GanRequest.FACELETS));
    assertEquals(0xDD, facelets[0]);
    assertEquals(0xED, facelets[3]);
    int[] history = GanTestPacket.cipher().decode(newParser().encodeMoveHistory(4, 3));
    assertEquals(0xD1, history[0]);
    assertEquals(3, history[2]); // aligned the same way Gen3 aligns it
    assertEquals(4, history[4]);
  }

  @Test
  public void noEventTypeThrowsOnAMinimumSizePacket() {
    for (int eventType = 0; eventType < 256; eventType++) {
      newParser().parse(new GanTestPacket(16).put(0, 8, eventType).put(8, 8, 8).encrypted(), 1000);
    }
  }

  private static int[] productDate() {
    return packet(0xFA, 6).put(24, 8, 2024 & 0xFF).put(32, 8, 2024 >> 8)
        .put(40, 8, 6).put(48, 8, 12).encrypted();
  }

  private static int[] version(int eventType, int major, int minor) {
    return packet(eventType, 2).put(24, 4, major).put(28, 4, minor).encrypted();
  }

  private static int[] hardwareName(String name) {
    GanTestPacket packet = packet(0xFC, name.length() + 1);
    for (int i = 0; i < name.length(); i++) {
      packet.put(24 + i * 8, 8, name.charAt(i));
    }
    return packet.encrypted();
  }
}
