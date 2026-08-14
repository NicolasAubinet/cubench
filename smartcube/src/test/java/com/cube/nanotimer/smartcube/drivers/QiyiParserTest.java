package com.cube.nanotimer.smartcube.drivers;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.crypto.Aes128;
import com.cube.nanotimer.smartcube.crypto.GanCipher;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Ports the sibling package's qiyi_parser_test.dart, fixtures and all. */
public class QiyiParserTest {

  private static final int[] MAC = GanCipher.macBytes("CC:A3:00:00:AB:CD");
  private static final Aes128 AES = new Aes128(QiyiParser.FIXED_KEY);

  /** A solved cube with U turned clockwise, in facelet terms. */
  private static final String U_FACELETS =
      "UUUUUUUUUBBBRRRRRRRRRFFFFFFDDDDDDDDDFFFLLLLLLLLLBBBBBB";

  private static QiyiParser newParser() {
    return new QiyiParser(MAC);
  }

  // ---- framing -----------------------------------------------------------------------------

  @Test
  public void crcMatchesTheStandardCheckVector() {
    int[] check = new int[9];
    for (int i = 0; i < 9; i++) {
      check[i] = '1' + i;
    }
    assertEquals(0x4B37, QiyiParser.crc16Modbus(check)); // the published MODBUS check value
  }

  @Test
  public void appHelloCarriesThePrefixAndTheMacReversedFramedAndPadded() {
    int[] msg = unframe(newParser().encodeAppHello());
    assertEquals(0xFE, msg[0]);
    assertEquals(21, msg[1]); // 11 prefix + 6 MAC + 4
    assertArrayEquals(new int[] {0x00, 0x6B, 0x01, 0x00, 0x00, 0x22, 0x06, 0x00, 0x02, 0x08, 0x00},
        Arrays.copyOfRange(msg, 2, 13));
    assertArrayEquals(new int[] {0xCD, 0xAB, 0x00, 0x00, 0xA3, 0xCC},
        Arrays.copyOfRange(msg, 13, 19));
    int crc = QiyiParser.crc16Modbus(msg, 19);
    assertEquals(crc & 0xFF, msg[19]);
    assertEquals(crc >> 8, msg[20]);
    assertEquals(0, msg.length % 16);
    for (int i = 21; i < msg.length; i++) {
      assertEquals(0, msg[i]); // zero padding
    }
  }

  @Test
  public void requestStateIsTheDocumentedContent() {
    int[] msg = unframe(newParser().encodeRequestState());
    assertEquals(9, msg[1]);
    assertArrayEquals(new int[] {5, 5, 5, 5, 5}, Arrays.copyOfRange(msg, 2, 7));
  }

  @Test
  public void aCorruptPacketIsDroppedNotDecoded() {
    int[] raw = cubeHello(CubeState.SOLVED_FACELETS);
    raw[3] ^= 0xFF; // one flipped bit in the ciphertext fails the CRC
    assertTrue(newParser().parse(raw, 1000).isEmpty());
  }

  @Test
  public void aTruncatedPacketIsDroppedWithoutThrowing() {
    int[] raw = stateChange(CubeState.SOLVED_FACELETS, 8);
    for (int len : new int[] {0, 16, 32, 64, 80}) {
      assertTrue("length " + len, newParser().parse(Arrays.copyOf(raw, len), 1000).isEmpty());
    }
  }

  // ---- state decoding ----------------------------------------------------------------------

  @Test
  public void solvedEncodesToTheBytePatternTheSpecQuotes() {
    int[] bytes = stateBytes(CubeState.SOLVED_FACELETS);
    assertArrayEquals(new int[] {0x33, 0x33, 0x33, 0x33, 0x13, 0x11, 0x11, 0x11, 0x11, 0x44},
        Arrays.copyOfRange(bytes, 0, 10));
  }

  @Test
  public void cubeHelloDecodesToSolvedAcksAndReportsBattery() {
    List<QiyiEvent> events = newParser().parse(cubeHello(CubeState.SOLVED_FACELETS), 1000);

    int[] acked = unframe(single(events, QiyiEvent.AckRequestEvent.class).getMessage());
    assertEquals(9, acked[1]);
    // The ack echoes the acked message's opcode and timestamp.
    assertArrayEquals(concat(new int[] {QiyiParser.OP_CUBE_HELLO}, beU32(1000)),
        Arrays.copyOfRange(acked, 2, 7));

    assertEquals(88, single(events, QiyiEvent.BatteryEvent.class).getLevel());
    assertEquals(CubeState.SOLVED, single(events, QiyiEvent.HelloEvent.class).getState());
  }

  @Test
  public void aStateChangeCarriesTheMoveAndTheFullStateAfterIt() {
    QiyiParser parser = newParser();
    parser.parse(cubeHello(CubeState.SOLVED_FACELETS), 1000);
    List<QiyiEvent> events = parser.parse(stateChange(U_FACELETS, 8), 2000);

    QiyiEvent.MoveEvent move = single(events, QiyiEvent.MoveEvent.class);
    assertEquals(Face.U, move.getMove().getFace());
    assertFalse(move.getMove().isPrime());
    assertEquals(U_FACELETS, move.getStateAfter().getFacelets());
    assertTrue(of(events, QiyiEvent.AckRequestEvent.class).isEmpty());
  }

  @Test
  public void everyMoveCodeMapsToTheDocumentedFaceAndDirection() {
    Face[] faces = {Face.L, Face.L, Face.R, Face.R, Face.D, Face.D,
        Face.U, Face.U, Face.F, Face.F, Face.B, Face.B};
    for (int code = 1; code <= 12; code++) {
      QiyiEvent.MoveEvent move =
          single(newParser().parse(stateChange(U_FACELETS, code), 1000), QiyiEvent.MoveEvent.class);
      assertEquals("move code " + code, faces[code - 1], move.getMove().getFace());
      assertEquals("move code " + code, code % 2 == 1, move.getMove().isPrime());
    }
  }

  @Test
  public void moveCodeZeroIsAStateChangeWithNoMove() {
    List<QiyiEvent> events = newParser().parse(stateChange(U_FACELETS, 0), 1000);
    assertTrue(of(events, QiyiEvent.MoveEvent.class).isEmpty());
    assertEquals(U_FACELETS, single(events, QiyiEvent.StateEvent.class).getState().getFacelets());
  }

  @Test
  public void anOutOfRangeColourNibbleDropsThePacket() {
    int[] msg = unframe(stateChange(CubeState.SOLVED_FACELETS, 8));
    msg[7] = 0x63; // nibble 6 — no such face
    int crc = QiyiParser.crc16Modbus(msg, msg[1] - 2);
    msg[msg[1] - 2] = crc & 0xFF;
    msg[msg[1] - 1] = crc >> 8;
    List<QiyiEvent> events = newParser().parse(reframe(msg), 1000);
    assertTrue(of(events, QiyiEvent.MoveEvent.class).isEmpty());
    assertTrue(of(events, QiyiEvent.StateEvent.class).isEmpty());
  }

  // ---- the needs-ACK solved glitch ---------------------------------------------------------

  @Test
  public void needsAckMeansSolvedEvenWhenTheStateBytesDisagree() {
    // The firmware can skip the solved state change during fast slice moves and send this instead.
    // Trusting the bytes would desync the model.
    List<QiyiEvent> events =
        newParser().parse(stateChange(U_FACELETS, 8, 88, 1000, true), 1000);

    assertEquals(CubeState.SOLVED, single(events, QiyiEvent.MoveEvent.class).getStateAfter());
    assertEquals(1, of(events, QiyiEvent.AckRequestEvent.class).size());
  }

  @Test
  public void theAckEchoesTheStateChangeBeingAcknowledged() {
    List<QiyiEvent> events =
        newParser().parse(stateChange(U_FACELETS, 0, 88, 4242, true), 1000);
    int[] acked = unframe(single(events, QiyiEvent.AckRequestEvent.class).getMessage());
    assertArrayEquals(concat(new int[] {QiyiParser.OP_STATE_CHANGE}, beU32(4242)),
        Arrays.copyOfRange(acked, 2, 7));
  }

  @Test
  public void aNormalStateChangeIsNotAckedAndIsTakenAtFaceValue() {
    List<QiyiEvent> events = newParser().parse(stateChange(U_FACELETS, 8), 1000);
    assertTrue(of(events, QiyiEvent.AckRequestEvent.class).isEmpty());
    assertFalse(single(events, QiyiEvent.MoveEvent.class).getStateAfter().isSolved());
  }

  /** Every turn carries the level, and every event costs the app a main-thread hop. */
  @Test
  public void theBatteryIsReportedOnlyWhenItChanges() {
    QiyiParser parser = newParser();
    assertEquals(1, of(parser.parse(cubeHello(CubeState.SOLVED_FACELETS), 1000),
        QiyiEvent.BatteryEvent.class).size());
    assertTrue(of(parser.parse(stateChange(U_FACELETS, 8), 2000),
        QiyiEvent.BatteryEvent.class).isEmpty());
    assertTrue(of(parser.parse(stateChange(U_FACELETS, 8), 3000),
        QiyiEvent.BatteryEvent.class).isEmpty());
    assertEquals(87, single(parser.parse(stateChange(U_FACELETS, 8, 87, 4000, false), 4000),
        QiyiEvent.BatteryEvent.class).getLevel());
  }

  /** A cube really down to nothing must still say so, which a bare "differs from zero" would not. */
  @Test
  public void aGenuineZeroIsReportedOnce() {
    QiyiParser parser = newParser();
    assertEquals(0, single(parser.parse(cubeHello(CubeState.SOLVED_FACELETS, 0, 1000), 1000),
        QiyiEvent.BatteryEvent.class).getLevel());
    assertTrue(of(parser.parse(stateChange(U_FACELETS, 8, 0, 2000, false), 2000),
        QiyiEvent.BatteryEvent.class).isEmpty());
  }

  @Test
  public void batteryIsClampedTo100() {
    List<QiyiEvent> events =
        newParser().parse(cubeHello(CubeState.SOLVED_FACELETS, 255, 1000), 1000);
    assertEquals(100, single(events, QiyiEvent.BatteryEvent.class).getLevel());
    assertEquals(0, newParser().getBatteryLevel());
  }

  // ---- cube clock --------------------------------------------------------------------------

  private static long stamp(QiyiParser parser, int ticks, long hostMs) {
    return single(parser.parse(stateChange(U_FACELETS, 8, 88, ticks, false), hostMs),
        QiyiEvent.MoveEvent.class).getMove().getCubeTimestampMs();
  }

  @Test
  public void theFirstMoveAnchorsTheCubeClockOntoHostTime() {
    assertEquals(50000, stamp(newParser(), 1000, 50000));
  }

  /** A 22 s solve replayed over 56 s, 2026-08-14: 1.6 the wrong way round is a factor of 2.56. */
  @Test
  public void laterMovesAdvanceByTheCubeClockAt1Point6TicksPerMs() {
    QiyiParser parser = newParser();
    stamp(parser, 1000, 50000); // anchor
    // 1600 ticks on = 1000 ms on the cube's clock, whatever the host reports.
    assertEquals(51000, stamp(parser, 2600, 50040));
  }

  /** Solve 253, 2026-08-14: a 10-move cross read as 4, its last edge falling into the first pair. */
  @Test
  public void aNotificationDeliveredLateDoesNotMoveTheTimelineBackwards() {
    QiyiParser parser = newParser();
    stamp(parser, 1000, 50000);
    assertEquals(51000, stamp(parser, 2600, 53000)); // handed over 2 s late by a busy main thread
    assertEquals(52000, stamp(parser, 4200, 50300)); // and the next one on time again
  }

  @Test
  public void aGapBetweenSolvesWithTheClocksPartedReAnchors() {
    QiyiParser parser = newParser();
    stamp(parser, 1000, 50000); // 625 ms on the cube's clock
    assertEquals(90000, stamp(parser, 33000, 90000)); // 20 s of cube time, 40 s of host time
  }

  @Test
  public void aGapTheCubeClockKeptUpWithDoesNot() {
    QiyiParser parser = newParser();
    stamp(parser, 1000, 50000);
    assertEquals(90000, stamp(parser, 65000, 90040)); // 40 s on both clocks
  }

  // ---- fixture builders --------------------------------------------------------------------

  private static int[] cubeHello(String facelets) {
    return cubeHello(facelets, 88, 1000);
  }

  private static int[] cubeHello(String facelets, int battery, int ticks) {
    return frame(concat(new int[] {QiyiParser.OP_CUBE_HELLO}, beU32(ticks), stateBytes(facelets),
        new int[] {0, battery})); // byte 34 is not a field read on this message
  }

  private static int[] stateChange(String facelets, int move) {
    return stateChange(facelets, move, 88, 1000, false);
  }

  private static int[] stateChange(String facelets, int move, int battery, int ticks,
      boolean needsAck) {
    return frame(concat(new int[] {QiyiParser.OP_STATE_CHANGE}, beU32(ticks), stateBytes(facelets),
        new int[] {move, battery}, new int[55], new int[] {needsAck ? 1 : 0})); // bytes 36..90 spare
  }

  /** 54 facelets to 27 colour-nibble bytes, low nibble first. */
  private static int[] stateBytes(String facelets) {
    int[] out = new int[27];
    for (int i = 0; i < 27; i++) {
      out[i] = colour(facelets.charAt(i * 2)) | (colour(facelets.charAt(i * 2 + 1)) << 4);
    }
    return out;
  }

  private static int colour(char face) {
    return "LRDUFB".indexOf(face);
  }

  private static int[] beU32(int value) {
    return new int[] {(value >> 24) & 0xFF, (value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF};
  }

  /** Frame + CRC + zero-pad + encrypt, the way the cube would send it. */
  private static int[] frame(int[] content) {
    int length = content.length + 4;
    int[] msg = new int[(length + 15) / 16 * 16];
    msg[0] = 0xFE;
    msg[1] = length;
    System.arraycopy(content, 0, msg, 2, content.length);
    int crc = QiyiParser.crc16Modbus(msg, length - 2);
    msg[length - 2] = crc & 0xFF;
    msg[length - 1] = crc >> 8;
    return reframe(msg);
  }

  private static int[] reframe(int[] plain) {
    return mapBlocks(plain, true);
  }

  private static int[] unframe(int[] raw) {
    return mapBlocks(raw, false);
  }

  private static int[] mapBlocks(int[] data, boolean encrypt) {
    int[] out = data.clone();
    int[] block = new int[16];
    for (int off = 0; off + 16 <= out.length; off += 16) {
      System.arraycopy(out, off, block, 0, 16);
      System.arraycopy(encrypt ? AES.encrypt(block) : AES.decrypt(block), 0, out, off, 16);
    }
    return out;
  }

  private static int[] concat(int[]... parts) {
    int total = 0;
    for (int[] part : parts) {
      total += part.length;
    }
    int[] out = new int[total];
    int at = 0;
    for (int[] part : parts) {
      System.arraycopy(part, 0, out, at, part.length);
      at += part.length;
    }
    return out;
  }

  private static <T extends QiyiEvent> List<T> of(List<QiyiEvent> events, Class<T> type) {
    List<T> out = new ArrayList<>();
    for (QiyiEvent event : events) {
      if (type.isInstance(event)) {
        out.add(type.cast(event));
      }
    }
    return out;
  }

  private static <T extends QiyiEvent> T single(List<QiyiEvent> events, Class<T> type) {
    List<T> found = of(events, type);
    assertEquals("expected exactly one " + type.getSimpleName(), 1, found.size());
    return found.get(0);
  }

  @Test
  public void syncConfirmationIsIgnoredRatherThanReadAsAState() {
    int[] reply = frame(concat(new int[] {QiyiParser.OP_SYNC_CONFIRMATION}, beU32(1000),
        stateBytes(U_FACELETS), new int[] {0, 88}));
    assertTrue(newParser().parse(reply, 1000).isEmpty());
  }
}
