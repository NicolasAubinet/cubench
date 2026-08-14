package com.cube.nanotimer.smartcube.drivers;

import com.cube.nanotimer.smartcube.crypto.Aes128;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure decoder + protocol parser for the QiYi Smart Cube ({@code QY-QYSC}) and the Tornado V4
 * ({@code XMD-TornadoV4-i}, same protocol), from the reverse-engineered spec at
 * {@code codeberg.org/Flying-Toast/qiyi_smartcube_protocol} and csTimer's {@code qiyicube.js}
 * (GPL-3.0). No BLE/Android dependencies.
 *
 * <p>Unlike every other brand here, <b>the cube is authoritative</b>: each state change carries the
 * full facelet state, so there is no move tracking, no {@link com.cube.nanotimer.smartcube.cube.CubieCube},
 * no serial, and consequently no desync to detect or heal. The move byte is informational — the
 * state is the truth.
 */
public final class QiyiParser {

  /** The QiYi key is fixed firmware-wide — no MAC derivation (the MAC is App Hello payload only). */
  public static final int[] FIXED_KEY = {
    0x57, 0xB1, 0xF9, 0xAB, 0xCD, 0x5A, 0xE8, 0xA7,
    0x9C, 0xB9, 0x8C, 0xE7, 0x57, 0x8C, 0x51, 0x08,
  };

  /** Cube&rarr;app opcodes, at byte 2 of a decoded message. */
  public static final int OP_CUBE_HELLO = 0x02;
  public static final int OP_STATE_CHANGE = 0x03;
  public static final int OP_SYNC_CONFIRMATION = 0x04;
  public static final int OP_CURRENT_STATE = 0x05;

  private static final int MSG_HEADER = 0xFE;

  /** App Hello content, less the trailing MAC. */
  private static final int[] APP_HELLO_PREFIX = {
    0x00, 0x6B, 0x01, 0x00, 0x00, 0x22, 0x06, 0x00, 0x02, 0x08, 0x00,
  };

  /**
   * Facelet nibble to face letter: 0=orange(L) 1=red(R) 2=yellow(D) 3=white(U) 4=green(F)
   * 5=blue(B).
   */
  private static final String FACE_BY_COLOUR = "LRDUFB";

  /**
   * Move byte {@code 1..12}, in the cube's order: L then R then D then U then F then B, each
   * counter-clockwise before clockwise. An odd code is therefore the prime turn.
   */
  private static final Face[] FACE_BY_CODE = {
    null, Face.L, Face.L, Face.R, Face.R, Face.D, Face.D,
    Face.U, Face.U, Face.F, Face.F, Face.B, Face.B,
  };

  /** Byte offsets into a decoded message. */
  private static final int TS_OFFSET = 3; // u32 big-endian, 1.6 ticks to the millisecond
  private static final int STATE_OFFSET = 7; // 27 bytes -> 54 facelet nibbles
  private static final int MOVE_OFFSET = 34; // state change only
  private static final int BATTERY_OFFSET = 35;
  private static final int NEEDS_ACK_OFFSET = 91; // state change only

  private final Aes128 aes = new Aes128(FIXED_KEY);
  private final CubeClock clock = new CubeClock();
  private final int[] mac;

  private int batteryLevel = 0;
  private boolean batteryReported; // so a genuine 0% is still reported once

  public QiyiParser(int[] macBytes) {
    this.mac = macBytes.clone();
  }

  public int getBatteryLevel() {
    return batteryLevel;
  }

  /**
   * The first thing the app must write. The cube ignores everything — and reports nothing — until
   * it has been told its own MAC.
   */
  public int[] encodeAppHello() {
    int[] content = new int[APP_HELLO_PREFIX.length + 6];
    System.arraycopy(APP_HELLO_PREFIX, 0, content, 0, APP_HELLO_PREFIX.length);
    for (int i = 0; i < 6; i++) {
      content[APP_HELLO_PREFIX.length + i] = mac[5 - i];
    }
    return encodeMessage(content);
  }

  /** Ask the cube for its current state; answered with {@link #OP_CURRENT_STATE}. */
  public int[] encodeRequestState() {
    return encodeMessage(new int[] {5, 5, 5, 5, 5});
  }

  public List<QiyiEvent> parse(int[] raw, long hostTimeMs) {
    int[] msg = mapBlocks(raw, false);
    // Length-bounded from here down: this decodes raw radio input, and a truncated notification
    // must not throw out of the BLE stream handler.
    if (msg.length < 4 || msg[0] != MSG_HEADER) {
      return List.of();
    }
    int length = msg[1];
    if (length < 5 || length > msg.length) {
      return List.of();
    }
    int crc = msg[length - 2] | (msg[length - 1] << 8);
    if (crc16Modbus(msg, length - 2) != crc) {
      return List.of();
    }

    switch (msg[2]) {
      case OP_CUBE_HELLO:
        return parseHello(msg, length);
      case OP_STATE_CHANGE:
        return parseStateChange(msg, length, hostTimeMs);
      // The spec names this message but not its layout, so the state offset is assumed to match the
      // two that are documented. Being length-bounded, a wrong guess drops the packet instead of
      // inventing a state — and nothing depends on it, since a pull is only ever a convenience.
      case OP_CURRENT_STATE: {
        CubeState state = decodeState(msg, length);
        return state == null ? List.of() : List.of(new QiyiEvent.StateEvent(state));
      }
      default:
        return List.of(); // including OP_SYNC_CONFIRMATION, which is only ever seen as a reply
    }
  }

  private List<QiyiEvent> parseHello(int[] msg, int length) {
    CubeState state = decodeState(msg, length);
    if (state == null) {
      return List.of();
    }
    List<QiyiEvent> events = new ArrayList<>();
    events.add(new QiyiEvent.AckRequestEvent(encodeAck(msg)));
    addBattery(events, msg, length);
    events.add(new QiyiEvent.HelloEvent(state));
    return events;
  }

  private List<QiyiEvent> parseStateChange(int[] msg, int length, long hostTimeMs) {
    if (!has(length, NEEDS_ACK_OFFSET)) {
      return List.of();
    }
    // The cube asks to be acknowledged when it believes it reached solved. Firmware glitch: solving
    // during fast slice moves (an H-perm) can make it skip the solved state change and send this one
    // with a *non-solved* state instead. The spec is explicit — needs-ACK means solved, whatever the
    // state bytes say. Trusting the bytes here would leave the model wrong for the rest of the
    // session.
    boolean needsAck = msg[NEEDS_ACK_OFFSET] == 1;
    CubeState state = needsAck ? CubeState.SOLVED : decodeState(msg, length);
    List<QiyiEvent> events = new ArrayList<>();
    if (needsAck) {
      events.add(new QiyiEvent.AckRequestEvent(encodeAck(msg)));
    }
    addBattery(events, msg, length);
    if (state == null) {
      return events;
    }
    int code = has(length, MOVE_OFFSET) ? msg[MOVE_OFFSET] : 0;
    Face face = code > 0 && code < FACE_BY_CODE.length ? FACE_BY_CODE[code] : null;
    if (face == null) {
      events.add(new QiyiEvent.StateEvent(state));
    } else {
      events.add(new QiyiEvent.MoveEvent(
          new CubeMove(face, code % 2 == 1, fitTimestamp(msg, hostTimeMs), hostTimeMs), state));
    }
    return events;
  }

  /**
   * Whether the byte at {@code index} lies inside the message content — i.e. before the trailing
   * 2-byte CRC, which a naive {@code index < length} would read as data.
   */
  private static boolean has(int length, int index) {
    return index < length - 2;
  }

  /**
   * The level rides on every state change, which is every quarter turn — and each event costs the
   * app a main-thread hop and a pass over its battery listeners. A number that moves once an hour is
   * not news sixty times a solve, so only a change is reported.
   */
  private void addBattery(List<QiyiEvent> events, int[] msg, int length) {
    if (!has(length, BATTERY_OFFSET)) {
      return;
    }
    int level = Math.min(100, Math.max(0, msg[BATTERY_OFFSET]));
    if (batteryReported && level == batteryLevel) {
      return;
    }
    batteryLevel = level;
    batteryReported = true;
    events.add(new QiyiEvent.BatteryEvent(level));
  }

  /**
   * Facelets are 4-bit colour nibbles over 27 bytes, <b>low nibble first</b>, in U,R,F,D,L,B
   * face-block order — which is already our facelet string, so this is a nibble walk and nothing
   * more. An out-of-range nibble means a corrupt packet (or a wrong offset): drop it rather than
   * build a bogus state.
   */
  private static CubeState decodeState(int[] msg, int length) {
    if (!has(length, STATE_OFFSET + 26)) {
      return null;
    }
    StringBuilder out = new StringBuilder(54);
    for (int i = 0; i < 27; i++) {
      int b = msg[STATE_OFFSET + i];
      int lo = b & 0x0F;
      int hi = b >> 4;
      if (lo > 5 || hi > 5) {
        return null;
      }
      out.append(FACE_BY_COLOUR.charAt(lo)).append(FACE_BY_COLOUR.charAt(hi));
    }
    return new CubeState(out.toString());
  }

  /**
   * The cube's clock, pulled onto host time the way the V10/GAN parsers do, so
   * {@link CubeMove#getCubeTimestampMs()} means the same thing on every brand.
   *
   * <p>A tick is 1/1.6 ms, as csTimer's {@code qiyicube.js} reads it. The spec calls that "units of
   * 1.6 milliseconds" and then says to divide by 1.6, which is the half that is right.
   */
  private long fitTimestamp(int[] msg, long hostTimeMs) {
    long ticks = ((long) msg[TS_OFFSET] << 24) | (msg[TS_OFFSET + 1] << 16)
        | (msg[TS_OFFSET + 2] << 8) | msg[TS_OFFSET + 3];
    return clock.stamp(Math.round(ticks / 1.6), hostTimeMs);
  }

  /** An ACK echoes bytes 2..6 of the message being acknowledged — its opcode and timestamp. */
  private int[] encodeAck(int[] msg) {
    int[] content = new int[5];
    System.arraycopy(msg, 2, content, 0, 5);
    return encodeMessage(content);
  }

  /**
   * Frame {@code content} as {@code 0xFE | length | content | crc16}, where {@code length} counts
   * the whole message, then zero-pad to a 16-byte multiple and encrypt.
   */
  private int[] encodeMessage(int[] content) {
    int length = content.length + 4;
    int[] msg = new int[(length + 15) / 16 * 16];
    msg[0] = MSG_HEADER;
    msg[1] = length;
    System.arraycopy(content, 0, msg, 2, content.length);
    int crc = crc16Modbus(msg, length - 2);
    msg[length - 2] = crc & 0xFF;
    msg[length - 1] = crc >> 8;
    return mapBlocks(msg, true);
  }

  public static int crc16Modbus(int[] data) {
    return crc16Modbus(data, data.length);
  }

  /**
   * CRC-16/MODBUS over the first {@code length} bytes — init {@code 0xFFFF}, reflected polynomial
   * {@code 0xA001}.
   */
  public static int crc16Modbus(int[] data, int length) {
    int crc = 0xFFFF;
    for (int i = 0; i < length; i++) {
      crc ^= data[i] & 0xFF;
      for (int bit = 0; bit < 8; bit++) {
        crc = (crc & 1) != 0 ? (crc >> 1) ^ 0xA001 : crc >> 1;
      }
    }
    return crc;
  }

  /** AES-128-ECB over every whole block — no IV, no chaining, so {@code GanCipher} does not fit. */
  private int[] mapBlocks(int[] data, boolean encrypt) {
    int[] out = data.clone();
    int[] block = new int[16];
    for (int off = 0; off + 16 <= out.length; off += 16) {
      System.arraycopy(out, off, block, 0, 16);
      System.arraycopy(encrypt ? aes.encrypt(block) : aes.decrypt(block), 0, out, off, 16);
    }
    return out;
  }
}
