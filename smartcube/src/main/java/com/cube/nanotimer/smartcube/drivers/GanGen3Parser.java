package com.cube.nanotimer.smartcube.drivers;

import java.util.List;

/**
 * Pure decoder + protocol parser for GAN Gen3 cubes (the GAN 356 i Carry 2). The move handling —
 * one move per packet, gaps recovered from the cube's own history — lives in
 * {@link GanBufferedParser}; what is Gen3's own is the {@code 0x55} framing and the opcodes.
 *
 * <p>Ported from {@code afedotov/gan-web-bluetooth} (MIT).
 */
public final class GanGen3Parser extends GanBufferedParser {

  /** Every Gen3 packet opens with this. */
  private static final int MAGIC = 0x55;

  /** Command prefix, shared by every Gen3 request. */
  private static final int OP_PREFIX = 0x68;
  private static final int OP_FACELETS = 0x01;
  private static final int OP_MOVE_HISTORY = 0x03;
  private static final int OP_HARDWARE = 0x04;
  private static final int OP_RESET = 0x05;
  private static final int OP_BATTERY = 0x07;

  private static final int MESSAGE_BYTES = 16;

  public GanGen3Parser(int[] macBytes) {
    super(macBytes);
  }

  /** Past the magic, event type and length bytes. */
  @Override
  int payloadBit() {
    return 24;
  }

  @Override
  public int[] encodeRequest(GanRequest request) {
    int[] msg = new int[MESSAGE_BYTES];
    switch (request) {
      case FACELETS -> fill(msg, OP_PREFIX, OP_FACELETS);
      case HARDWARE -> fill(msg, OP_PREFIX, OP_HARDWARE);
      case BATTERY -> fill(msg, OP_PREFIX, OP_BATTERY);
      case RESET -> fill(msg, OP_PREFIX, OP_RESET, 0x05, 0x39, 0x77, 0x00, 0x00, 0x01,
          0x23, 0x45, 0x67, 0x89, 0xAB, 0x00, 0x00, 0x00);
    }
    return cipher.encode(msg);
  }

  @Override
  public int[] encodeMoveHistory(int serial, int count) {
    int[] window = alignHistoryWindow(serial, count);
    int[] msg = new int[MESSAGE_BYTES];
    fill(msg, OP_PREFIX, OP_MOVE_HISTORY, window[0], 0, window[1], 0);
    return cipher.encode(msg);
  }

  @Override
  public List<GanEvent> parse(int[] raw, long hostTimeMs) {
    GanPacket packet = new GanPacket(cipher.decode(raw));
    if (!packet.has(24) || packet.val(0, 8) != MAGIC) {
      return List.of();
    }
    int eventType = packet.val(8, 8);
    int dataLength = packet.val(16, 8);
    if (dataLength == 0) {
      return List.of();
    }
    switch (eventType) {
      case 0x01:
        return packet.has(80) ? parseMove(packet, hostTimeMs) : List.<GanEvent>of();
      case 0x06:
        return parseMoveHistory(packet, dataLength, hostTimeMs);
      case 0x02:
        return packet.has(132) ? parseFacelets(packet, hostTimeMs) : List.<GanEvent>of();
      case 0x07:
        if (!packet.has(88)) {
          return List.of();
        }
        return List.of(new GanEvent.InfoEvent(packet.text(32, 5),
            packet.val(80, 4) + "." + packet.val(84, 4),
            packet.val(72, 4) + "." + packet.val(76, 4),
            false)); // no Gen3 cube has a gyro
      case 0x10:
        return packet.has(32) ? parseBattery(packet.val(24, 8)) : List.<GanEvent>of();
      case 0x11:
        return List.of(new GanEvent.DisconnectEvent());
      default:
        return List.of();
    }
  }

  private static void fill(int[] msg, int... head) {
    System.arraycopy(head, 0, msg, 0, head.length);
  }
}
