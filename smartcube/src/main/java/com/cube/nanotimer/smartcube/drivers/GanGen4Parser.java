package com.cube.nanotimer.smartcube.drivers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure decoder + protocol parser for GAN Gen4 cubes (GAN12 ui Maglev, GAN14 ui FreePlay).
 *
 * <p>Same shape as Gen3 — see {@link GanBufferedParser} — with its own framing and opcodes. The one
 * real difference is hardware info, which Gen4 dribbles out across four separate events instead of
 * one.
 *
 * <p>Ported from {@code afedotov/gan-web-bluetooth} (MIT).
 */
public final class GanGen4Parser extends GanBufferedParser {

  private static final int MESSAGE_BYTES = 20;

  /** The only Gen4 cube with a gyro. */
  static final String GYRO_HARDWARE = "GAN12uiM";

  private static final int EV_PRODUCT_DATE = 0xFA;
  private static final int EV_HARDWARE_NAME = 0xFC;
  private static final int EV_SOFTWARE_VERSION = 0xFD;
  private static final int EV_HARDWARE_VERSION = 0xFE;

  /** Hardware info arrives in pieces, keyed by the event that carried each. */
  private final Map<Integer, String> hwInfo = new HashMap<>();

  public GanGen4Parser(int[] macBytes) {
    super(macBytes);
  }

  /** Past the event type and length bytes. */
  @Override
  int payloadBit() {
    return 16;
  }

  @Override
  public int[] encodeRequest(GanRequest request) {
    int[] msg = new int[MESSAGE_BYTES];
    switch (request) {
      case FACELETS -> fill(msg, 0xDD, 0x04, 0x00, 0xED, 0x00, 0x00);
      case HARDWARE -> {
        hwInfo.clear(); // a fresh set of pieces is on its way
        fill(msg, 0xDF, 0x03, 0x00, 0x00, 0x00);
      }
      case BATTERY -> fill(msg, 0xDD, 0x04, 0x00, 0xEF, 0x00, 0x00);
      case RESET -> fill(msg, 0xD2, 0x0D, 0x05, 0x39, 0x77, 0x00, 0x00, 0x01,
          0x23, 0x45, 0x67, 0x89, 0xAB, 0x00, 0x00, 0x00);
    }
    return cipher.encode(msg);
  }

  @Override
  public int[] encodeMoveHistory(int serial, int count) {
    int[] window = alignHistoryWindow(serial, count);
    int[] msg = new int[MESSAGE_BYTES];
    fill(msg, 0xD1, 0x04, window[0], 0, window[1], 0);
    return cipher.encode(msg);
  }

  @Override
  public List<GanEvent> parse(int[] raw, long hostTimeMs) {
    GanPacket packet = new GanPacket(cipher.decode(raw));
    if (!packet.has(16)) {
      return List.of();
    }
    int eventType = packet.val(0, 8);
    int dataLength = packet.val(8, 8);

    switch (eventType) {
      case 0x01:
        return packet.has(72) ? parseMove(packet, hostTimeMs) : List.<GanEvent>of();
      case 0xD1:
        return parseMoveHistory(packet, dataLength, hostTimeMs);
      case 0xED:
        return packet.has(124) ? parseFacelets(packet, hostTimeMs) : List.<GanEvent>of();
      case 0xEC:
        return packet.has(payloadBit() + GanGyro.BITS)
            ? List.of(new GanEvent.GyroEvent(GanGyro.decode(packet, payloadBit())))
            : List.<GanEvent>of();
      case 0xEF:
        return packet.has(16 + dataLength * 8)
            ? parseBattery(packet.val(8 + dataLength * 8, 8)) : List.<GanEvent>of();
      default:
        if (eventType >= 0xFA && eventType <= 0xFE) {
          return parseHardware(packet, eventType, dataLength);
        }
        return List.of();
    }
  }

  /** Gen4 splits hardware info across four events; report once all have landed. */
  private List<GanEvent> parseHardware(GanPacket packet, int eventType, int dataLength) {
    switch (eventType) {
      case EV_PRODUCT_DATE:
        if (!packet.has(56)) {
          return List.of();
        }
        int year = packet.val(24, 8) | (packet.val(32, 8) << 8);
        hwInfo.put(eventType, String.format("%04d-%02d-%02d",
            year, packet.val(40, 8), packet.val(48, 8)));
        break;
      case EV_HARDWARE_NAME:
        if (!packet.has(24 + (dataLength - 1) * 8)) {
          return List.of();
        }
        hwInfo.put(eventType, packet.text(24, dataLength - 1));
        break;
      case EV_SOFTWARE_VERSION:
      case EV_HARDWARE_VERSION:
        if (!packet.has(32)) {
          return List.of();
        }
        hwInfo.put(eventType, packet.val(24, 4) + "." + packet.val(28, 4));
        break;
      default:
        return List.of();
    }

    if (hwInfo.size() < 4) {
      return List.of();
    }
    String name = hwInfo.getOrDefault(EV_HARDWARE_NAME, "");
    return List.of(new GanEvent.InfoEvent(name,
        hwInfo.getOrDefault(EV_HARDWARE_VERSION, ""),
        hwInfo.getOrDefault(EV_SOFTWARE_VERSION, ""),
        GYRO_HARDWARE.equals(name)));
  }

  private static void fill(int[] msg, int... head) {
    System.arraycopy(head, 0, msg, 0, head.length);
  }
}
