package com.cube.nanotimer.smartcube.drivers;

import com.cube.nanotimer.smartcube.crypto.GanCipher;
import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.drivers.GanMoveBuffer.BufferedMove;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import java.util.ArrayList;
import java.util.List;

/**
 * What Gen3 and Gen4 share: a packet carries a <em>single</em> move, so a dropped notification
 * leaves a hole nothing in the stream can fill. The cube instead keeps a move history that can be
 * asked for — moves queue in a {@link GanMoveBuffer} until they are contiguous, and a gap emits a
 * {@link GanEvent.HistoryRequestEvent}.
 *
 * <p>The two generations pack their move, facelets and history payloads identically; Gen3 simply
 * prefixes one more framing byte. So each subclass says where its payload starts ({@code payloadBit})
 * and the reading itself is done once, here. What genuinely differs — opcodes, message length and
 * hardware info — stays with the subclass.
 *
 * <p>Ported from {@code afedotov/gan-web-bluetooth} (MIT).
 */
abstract class GanBufferedParser implements GanProtocol {

  /**
   * A facelets event arriving within this of a live move is ignored for gap-checking: mid-turn, the
   * model is <em>expected</em> to trail the cube.
   */
  private static final int MOVE_SETTLE_MS = 500;

  /** A move's 6-bit face code, in {@code "URFDLB"} order. */
  private static final int[] FACE_CODES = {2, 32, 8, 1, 16, 4};

  /** The same faces again, as move history packs them into 3 bits. */
  private static final int[] HISTORY_FACE_CODES = {1, 5, 3, 0, 4, 2};

  private static final Face[] FACES = {Face.U, Face.R, Face.F, Face.D, Face.L, Face.B};

  final GanCipher cipher;
  private final CubieCube cube = new CubieCube();
  private final GanMoveBuffer buffer = new GanMoveBuffer();
  private final CubeClock clock = new CubeClock();

  private long lastMoveHostMs = 0;
  private Integer batteryLevel;

  GanBufferedParser(int[] macBytes) {
    this.cipher = GanCipher.forMac(GanGen2Parser.BASE_KEY, GanGen2Parser.BASE_IV, macBytes);
  }

  /** The first bit of an event's own payload, past this generation's framing. */
  abstract int payloadBit();

  @Override
  public final Integer getBatteryLevel() {
    return batteryLevel;
  }

  @Override
  public final CubeState getCurrentState() {
    return new CubeState(cube.toFaceCube());
  }

  @Override
  public final boolean needsAnchor() {
    return buffer.needsAnchor();
  }

  @Override
  public final void setState(CubeState state) {
    cube.fromFacelet(state.getFacelets());
  }

  /**
   * History replies are byte-aligned and always start on an odd serial with an even number of
   * moves, whatever was asked for — so ask for what will come back. The window is also kept off the
   * 255&rarr;0 edge, where the firmware answers with zero bytes that decode as 'D' moves that never
   * happened.
   *
   * @return the aligned {@code {serial, count}}
   */
  static int[] alignHistoryWindow(int serial, int count) {
    int s = serial;
    int c = count;
    if (s % 2 == 0) {
      s = (s - 1) & 0xFF;
    }
    if (c % 2 == 1) {
      c++;
    }
    c = Math.min(c, s + 1);
    return new int[] {s, c};
  }

  /** Read the single move this packet carries and drain whatever the buffer can now release. */
  final List<GanEvent> parseMove(GanPacket packet, long hostTimeMs) {
    if (needsAnchor()) {
      return List.of(); // no trusted model to apply moves to
    }
    lastMoveHostMs = hostTimeMs;
    int base = payloadBit();
    long cubeTimeMs = packet.valLe(base, 4) & 0xFFFFFFFFL;
    int serial = packet.valLe(base + 32, 2);
    boolean prime = packet.val(base + 48, 2) == 1;
    int face = indexOf(FACE_CODES, packet.val(base + 50, 6));
    if (face < 0) {
      return List.of(); // corrupt packet — drop it
    }
    return drain(buffer.push(
        new BufferedMove(serial, FACES[face], prime, cubeTimeMs, hostTimeMs)), hostTimeMs);
  }

  /**
   * Read the moves the cube sent back from its own history, newest first, and drain each into the
   * buffer's hole.
   *
   * @param dataLength the cube's own length field, which decides how many moves follow
   */
  final List<GanEvent> parseMoveHistory(GanPacket packet, int dataLength, long hostTimeMs) {
    if (needsAnchor()) {
      return List.of();
    }
    int base = payloadBit();
    int startSerial = packet.val(base, 8);
    // Clamp the cube's count to what the packet can actually hold.
    int count = Math.max(0, Math.min((dataLength - 1) * 2, (packet.bitLength() - base - 8) / 4));

    List<GanEvent> events = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      int face = indexOf(HISTORY_FACE_CODES, packet.val(base + 8 + 4 * i, 3));
      if (face < 0) {
        continue;
      }
      events.addAll(drain(buffer.injectHistory(new BufferedMove((startSerial - i) & 0xFF,
          FACES[face], packet.val(base + 11 + 4 * i, 1) == 1, null, null)), hostTimeMs));
    }
    return events;
  }

  /**
   * Read the cube's own full state. The cube sends facelets periodically, so they double as a check
   * that the model has not silently fallen behind.
   */
  final List<GanEvent> parseFacelets(GanPacket packet, long hostTimeMs) {
    int base = payloadBit();
    int serial = packet.valLe(base, 2);

    int[] cp = new int[8];
    int[] co = new int[8];
    int[] ep = new int[12];
    int[] eo = new int[12];
    int pieces = base + 16;
    for (int i = 0; i < 7; i++) {
      cp[i] = packet.val(pieces + i * 3, 3);
      co[i] = packet.val(pieces + 21 + i * 2, 2);
    }
    for (int i = 0; i < 11; i++) {
      ep[i] = packet.val(pieces + 37 + i * 4, 4);
      eo[i] = packet.val(pieces + 81 + i, 1);
    }
    GanFacelets.completeLastPiece(cp, co, ep, eo);

    if (!needsAnchor()) {
      if (hostTimeMs - lastMoveHostMs <= MOVE_SETTLE_MS) {
        return List.of();
      }
      GanMoveBuffer.HistoryRequest request = buffer.checkForMissedMoves(serial);
      if (request != null) {
        return List.of(new GanEvent.HistoryRequestEvent(request.getSerial(), request.getCount()));
      }
      // Only a snapshot level with the model can be applied; an older one would rewind it.
      if (((serial - buffer.getLastSerial()) & 0xFF) != 0) {
        return List.of();
      }
    }

    if (!cube.fromPermutation(cp, co, ep, eo)) {
      return List.of(); // corrupt packet
    }
    buffer.anchor(serial);
    return List.of(new GanEvent.StateEvent(new CubeState(cube.toFaceCube())));
  }

  final List<GanEvent> parseBattery(int level) {
    batteryLevel = Math.min(100, Math.max(0, level));
    return List.of(new GanEvent.BatteryEvent(batteryLevel));
  }

  /** Turn a buffer verdict into events, applying the moves it released. */
  private List<GanEvent> drain(GanMoveBuffer.Result result, long hostTimeMs) {
    List<GanEvent> events = new ArrayList<>();
    for (BufferedMove move : result.getEvicted()) {
      cube.applyMove(move.getFace(), move.isPrime());
      events.add(new GanEvent.MoveEvent(
          new CubeMove(move.getFace(), move.isPrime(), stampOf(move, hostTimeMs),
              move.getHostTimeMs()),
          new CubeState(cube.toFaceCube())));
    }
    if (result.isDesynced()) {
      events.add(new GanEvent.DesyncEvent(result.getLostMoves()));
    } else if (result.getHistoryRequest() != null) {
      events.add(new GanEvent.HistoryRequestEvent(result.getHistoryRequest().getSerial(),
          result.getHistoryRequest().getCount()));
    }
    return events;
  }

  /** A move's timestamp on the cube's clock, carried onto host time by {@link CubeClock}. */
  private long stampOf(BufferedMove move, long hostTimeMs) {
    Long cubeTimeMs = move.getCubeTimeMs();
    if (cubeTimeMs == null) {
      return clock.lastStamp(); // recovered from history: never seen live, so it has no time
    }
    return clock.stamp(cubeTimeMs, hostTimeMs);
  }

  private static int indexOf(int[] values, int value) {
    for (int i = 0; i < values.length; i++) {
      if (values[i] == value) {
        return i;
      }
    }
    return -1;
  }
}
