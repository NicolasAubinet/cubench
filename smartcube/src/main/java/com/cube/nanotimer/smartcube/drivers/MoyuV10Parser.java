package com.cube.nanotimer.smartcube.drivers;

import com.cube.nanotimer.smartcube.crypto.GanCipher;
import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure decoder + protocol parser for the MoYu WeiLong V10 AI ({@code WCU_MY32}), ported
 * from csTimer's moyu32cube.js (GPL-3.0). No BLE/Android dependencies: feed it raw
 * notification packets and it emits {@link MoyuEvent}s while tracking full cube state.
 */
public final class MoyuV10Parser {

  /** MoYu V10 base key/IV (LZString-decompressed from the driver's KEYS). */
  private static final int[] BASE_KEY = {21, 119, 58, 92, 103, 14, 45, 31, 23, 103, 42, 19, 155, 103, 82, 87};
  private static final int[] BASE_IV = {17, 35, 38, 37, 134, 42, 44, 59, 85, 6, 127, 49, 126, 103, 33, 87};

  /** Message opcodes for requests written to the cube. */
  public static final int OP_INFO = 161;
  public static final int OP_STATUS = 163;
  public static final int OP_POWER = 164;

  /** Move face codes index this order ({@code "FBUDLR"[code]}). */
  private static final Face[] FACE_BY_CODE = {Face.F, Face.B, Face.U, Face.D, Face.L, Face.R};

  private final GanCipher cipher;
  private final CubieCube cube = new CubieCube();

  private int prevMoveCnt = -1;
  private int moveCnt = -1;
  private long deviceTime = 0;
  private long deviceTimeOffset = 0;
  private int batteryLevel = 0;
  private boolean needsResync = false;

  public MoyuV10Parser(int[] macBytes) {
    this.cipher = GanCipher.forMac(BASE_KEY, BASE_IV, macBytes);
  }

  public int getBatteryLevel() {
    return batteryLevel;
  }

  public CubeState getCurrentState() {
    return new CubeState(cube.toFaceCube());
  }

  /** Force the next state message to re-anchor (used after packet loss). */
  public void resetAnchor() {
    prevMoveCnt = -1;
  }

  /**
   * True (once) after a move gap larger than a packet can carry, so the tracked state has
   * drifted; the driver should request a fresh state to re-anchor. Clears on read.
   */
  public boolean pollNeedsResync() {
    boolean v = needsResync;
    needsResync = false;
    return v;
  }

  /** Realign the tracked model to {@code state} without a physical resync. */
  public void setState(CubeState state) {
    cube.fromFacelet(state.getFacelets());
  }

  /** Build an encrypted request packet for {@code opcode}. */
  public int[] encodeRequest(int opcode) {
    int[] req = new int[20];
    req[0] = opcode;
    return cipher.encode(req);
  }

  public List<MoyuEvent> parse(int[] raw, long hostTimeMs) {
    String s = toBits(cipher.decode(raw));

    switch (val(s, 0, 8)) {
      case 161: {
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < 8; i++) {
          name.append((char) val(s, 8 + i * 8, 16 + i * 8));
        }
        String hw = val(s, 72, 80) + "." + val(s, 80, 88);
        String sw = val(s, 88, 96) + "." + val(s, 96, 104);
        return List.of(new MoyuEvent.InfoEvent(name.toString().trim(), hw, sw));
      }
      case 163: {
        if (prevMoveCnt != -1) {
          return List.of();
        }
        moveCnt = val(s, 152, 160);
        String facelet = parseFacelet(s.substring(8, 152));
        cube.fromFacelet(facelet);
        prevMoveCnt = moveCnt;
        return List.of(new MoyuEvent.StateEvent(new CubeState(facelet)));
      }
      case 164: {
        batteryLevel = val(s, 8, 16);
        return List.of(new MoyuEvent.BatteryEvent(batteryLevel));
      }
      case 165: {
        moveCnt = val(s, 88, 96);
        if (moveCnt == prevMoveCnt || prevMoveCnt == -1) {
          return List.of();
        }
        int[] timeOffs = new int[5];
        List<CubeMove> moves = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
          int m = val(s, 96 + i * 5, 101 + i * 5);
          timeOffs[i] = val(s, 8 + i * 16, 24 + i * 16);
          if (m >= 12) {
            return List.of(); // invalid move byte — drop the packet
          }
          moves.add(new CubeMove(FACE_BY_CODE[m >> 1], (m & 1) == 1, 0));
        }
        return emitMoves(timeOffs, moves, hostTimeMs);
      }
      default:
        return List.of();
    }
  }

  private List<MoyuEvent> emitMoves(int[] timeOffs, List<CubeMove> moves, long hostTimeMs) {
    int moveDiff = (moveCnt - prevMoveCnt) & 0xff;
    prevMoveCnt = moveCnt;
    if (moveDiff > moves.size()) {
      moveDiff = moves.size();
      needsResync = true; // lost more moves than the packet carries; tracked state has drifted
    }

    long calcTs = deviceTime + deviceTimeOffset;
    for (int i = moveDiff - 1; i >= 0; i--) {
      calcTs += timeOffs[i];
    }
    if (deviceTime == 0 || Math.abs(hostTimeMs - calcTs) > 2000) {
      deviceTime += hostTimeMs - calcTs;
    }

    List<MoyuEvent> events = new ArrayList<>();
    for (int i = moveDiff - 1; i >= 0; i--) {
      CubeMove m = moves.get(i);
      cube.applyMove(m.getFace(), m.isPrime());
      deviceTime += timeOffs[i];
      events.add(new MoyuEvent.MoveEvent(
          new CubeMove(m.getFace(), m.isPrime(), deviceTime, i == 0 ? Long.valueOf(hostTimeMs) : null),
          new CubeState(cube.toFaceCube())));
    }
    deviceTimeOffset = hostTimeMs - deviceTime;
    return events;
  }

  private static String parseFacelet(String faceletBits) {
    int[] faces = {2, 5, 0, 3, 4, 1}; // read URFDLB from the FBUDLR-ordered data
    String cs = "FBUDLR";
    StringBuilder out = new StringBuilder(54);
    for (int i = 0; i < 6; i++) {
      int base = faces[i] * 24;
      for (int j = 0; j < 8; j++) {
        out.append(cs.charAt(val(faceletBits, base + j * 3, base + j * 3 + 3)));
        if (j == 3) {
          out.append(cs.charAt(faces[i]));
        }
      }
    }
    return out.toString();
  }

  private static String toBits(int[] data) {
    StringBuilder bits = new StringBuilder(data.length * 8);
    for (int b : data) {
      String s = Integer.toBinaryString(b & 0xff);
      for (int p = s.length(); p < 8; p++) {
        bits.append('0');
      }
      bits.append(s);
    }
    return bits.toString();
  }

  private static int val(String bits, int start, int end) {
    return Integer.parseInt(bits.substring(start, end), 2);
  }
}
