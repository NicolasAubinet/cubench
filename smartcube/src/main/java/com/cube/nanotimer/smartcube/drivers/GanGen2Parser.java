package com.cube.nanotimer.smartcube.drivers;

import com.cube.nanotimer.smartcube.crypto.GanCipher;
import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure decoder + protocol parser for GAN Gen2 cubes — the GAN 356 i3, i Carry (S), GAN12 ui,
 * GAN Mini ui FreePlay and Monster Go 3Ai, plus the MoYu AI 2023 which speaks the same protocol
 * with a different key.
 *
 * <p>Ported from {@code afedotov/gan-web-bluetooth} (MIT). No BLE/Android dependencies — feed it
 * raw packets and it emits {@link GanEvent}s while tracking full cube state.
 */
public final class GanGen2Parser implements GanProtocol {

  /** Base key/IV for GAN Gen2/3/4 cubes. */
  static final int[] BASE_KEY = {
    0x01, 0x02, 0x42, 0x28, 0x31, 0x91, 0x16, 0x07,
    0x20, 0x05, 0x18, 0x54, 0x42, 0x11, 0x12, 0x53,
  };
  static final int[] BASE_IV = {
    0x11, 0x03, 0x32, 0x28, 0x21, 0x01, 0x76, 0x27,
    0x20, 0x95, 0x78, 0x14, 0x32, 0x12, 0x02, 0x43,
  };

  /** The MoYu AI 2023 ({@code AiCube}) speaks Gen2 with its own key. */
  private static final int[] MOYU_AI_KEY = {
    0x05, 0x12, 0x02, 0x45, 0x02, 0x01, 0x29, 0x56,
    0x12, 0x78, 0x12, 0x76, 0x81, 0x01, 0x08, 0x03,
  };
  private static final int[] MOYU_AI_IV = {
    0x01, 0x44, 0x28, 0x06, 0x86, 0x21, 0x22, 0x28,
    0x51, 0x05, 0x08, 0x31, 0x82, 0x02, 0x21, 0x06,
  };

  /** Opcodes written to the cube's command characteristic. */
  private static final int OP_FACELETS = 0x04;
  private static final int OP_HARDWARE = 0x05;
  private static final int OP_BATTERY = 0x09;

  /**
   * Tells the cube its current position <em>is</em> solved. Unlike the other requests this is a
   * fixed payload, not a bare opcode.
   */
  private static final int[] RESET_REQUEST = {
    0x0A, 0x05, 0x39, 0x77, 0x00, 0x00, 0x01, 0x23, 0x45, 0x67,
    0x89, 0xAB, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
  };

  /** A move's 4-bit face code indexes this order ({@code "URFDLB"[code]}). */
  private static final Face[] FACE_BY_CODE = {Face.U, Face.R, Face.F, Face.D, Face.L, Face.B};

  /** A packet carries at most the last 7 moves. */
  private static final int MAX_RECOVERABLE_MOVES = 7;

  private final GanCipher cipher;
  private final CubieCube cube = new CubieCube();

  private int lastSerial = -1;
  private long deviceTime = 0;
  private long deviceTimeOffset = 0;
  private long lastMoveTimeMs = 0;
  private Integer batteryLevel;

  public GanGen2Parser(int[] macBytes, boolean moyuAi) {
    this.cipher = GanCipher.forMac(moyuAi ? MOYU_AI_KEY : BASE_KEY,
        moyuAi ? MOYU_AI_IV : BASE_IV, macBytes);
  }

  @Override
  public Integer getBatteryLevel() {
    return batteryLevel;
  }

  @Override
  public CubeState getCurrentState() {
    return new CubeState(cube.toFaceCube());
  }

  /**
   * True while the model is untrusted: moves are dropped until facelets re-anchor it (before the
   * first state, or after a {@link GanEvent.DesyncEvent}).
   */
  @Override
  public boolean needsAnchor() {
    return lastSerial == -1;
  }

  @Override
  public void setState(CubeState state) {
    cube.fromFacelet(state.getFacelets());
  }

  @Override
  public int[] encodeRequest(GanRequest request) {
    if (request == GanRequest.RESET) {
      return cipher.encode(RESET_REQUEST);
    }
    int[] req = new int[20];
    switch (request) {
      case FACELETS -> req[0] = OP_FACELETS;
      case HARDWARE -> req[0] = OP_HARDWARE;
      case BATTERY -> req[0] = OP_BATTERY;
      default -> throw new IllegalArgumentException("Unknown request: " + request);
    }
    return cipher.encode(req);
  }

  @Override
  public int[] encodeMoveHistory(int serial, int count) {
    return null; // a Gen2 packet already carries the last 7 moves
  }

  @Override
  public List<GanEvent> parse(int[] raw, long hostTimeMs) {
    GanPacket packet = new GanPacket(cipher.decode(raw));
    if (!packet.has(4)) {
      return List.of();
    }
    switch (packet.val(0, 4)) {
      case 0x02:
        return parseMoves(packet, hostTimeMs);
      case 0x04:
        return packet.has(102) ? parseFacelets(packet) : List.<GanEvent>of();
      case 0x05:
        if (!packet.has(105)) {
          return List.of();
        }
        return List.of(new GanEvent.InfoEvent(packet.text(40, 8),
            packet.val(8, 8) + "." + packet.val(16, 8),
            packet.val(24, 8) + "." + packet.val(32, 8),
            packet.val(104, 1) == 1));
      case 0x09:
        if (!packet.has(16)) {
          return List.of();
        }
        batteryLevel = Math.min(100, Math.max(0, packet.val(8, 8)));
        return List.of(new GanEvent.BatteryEvent(batteryLevel));
      case 0x0D:
        return List.of(new GanEvent.DisconnectEvent());
      default:
        // 0x01 is the gyro stream, which nothing decodes yet: the cubes that have one report
        // orientation, but the timer gets none from them until it is.
        return List.of();
    }
  }

  private List<GanEvent> parseMoves(GanPacket packet, long hostTimeMs) {
    if (!packet.has(12) || needsAnchor()) {
      return List.of(); // no trusted model to apply moves to
    }
    int serial = packet.val(4, 8);
    int missed = (serial - lastSerial) & 0xFF;
    if (missed == 0) {
      return List.of();
    }
    lastSerial = serial;

    // Beyond what the packet carries, the moves in between are gone for good: applying the ones we
    // did get would leave the model silently wrong, and nothing would ever complete again. Declare
    // the model dead instead.
    if (missed > MAX_RECOVERABLE_MOVES) {
      lastSerial = -1;
      return List.of(new GanEvent.DesyncEvent(missed - MAX_RECOVERABLE_MOVES));
    }
    if (!packet.has(47 + 16 * missed)) {
      return List.of();
    }

    // Move 0 is the newest; walk oldest-first so the model and clock advance in the order the turns
    // actually happened.
    Face[] faces = new Face[missed];
    boolean[] primes = new boolean[missed];
    long[] elapsed = new long[missed];
    for (int i = 0; i < missed; i++) {
      int face = packet.val(12 + 5 * i, 4);
      if (face >= FACE_BY_CODE.length) {
        return List.of(); // corrupt packet — drop it
      }
      faces[i] = FACE_BY_CODE[face];
      primes[i] = packet.val(16 + 5 * i, 1) == 1;
      long ms = packet.val(47 + 16 * i, 16);
      // A zero elapsed means the cube's 16-bit clock register wrapped; fall back to how long the
      // host waited (nothing to measure against on the first move).
      if (ms == 0) {
        ms = lastMoveTimeMs == 0 ? 0 : hostTimeMs - lastMoveTimeMs;
      }
      elapsed[i] = ms;
    }

    long calcTs = deviceTime + deviceTimeOffset;
    for (int i = missed - 1; i >= 0; i--) {
      calcTs += elapsed[i];
    }
    if (deviceTime == 0 || Math.abs(hostTimeMs - calcTs) > 2000) {
      deviceTime += hostTimeMs - calcTs;
    }

    List<GanEvent> events = new ArrayList<>();
    for (int i = missed - 1; i >= 0; i--) {
      cube.applyMove(faces[i], primes[i]);
      deviceTime += elapsed[i];
      events.add(new GanEvent.MoveEvent(
          // Recovered moves were never seen live, so they have no host time.
          new CubeMove(faces[i], primes[i], deviceTime, i == 0 ? Long.valueOf(hostTimeMs) : null),
          new CubeState(cube.toFaceCube())));
    }
    deviceTimeOffset = hostTimeMs - deviceTime;
    lastMoveTimeMs = hostTimeMs;
    return events;
  }

  private List<GanEvent> parseFacelets(GanPacket packet) {
    int serial = packet.val(4, 8);
    // Facelets snapshot the cube at *its* serial. Once anchored, an older snapshot means moves have
    // landed since — applying it would rewind the model, so leave it and wait for a fresh pull.
    if (!needsAnchor() && serial != lastSerial) {
      return List.of();
    }
    int[] cp = new int[8];
    int[] co = new int[8];
    int[] ep = new int[12];
    int[] eo = new int[12];
    for (int i = 0; i < 7; i++) {
      cp[i] = packet.val(12 + i * 3, 3);
      co[i] = packet.val(33 + i * 2, 2);
    }
    for (int i = 0; i < 11; i++) {
      ep[i] = packet.val(47 + i * 4, 4);
      eo[i] = packet.val(91 + i, 1);
    }
    GanFacelets.completeLastPiece(cp, co, ep, eo);

    if (!cube.fromPermutation(cp, co, ep, eo)) {
      return List.of(); // corrupt packet
    }
    lastSerial = serial;
    return List.of(new GanEvent.StateEvent(new CubeState(cube.toFaceCube())));
  }
}
