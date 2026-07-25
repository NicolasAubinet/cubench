package com.cube.nanotimer.smartcube.drivers;

import com.cube.nanotimer.smartcube.model.Face;
import java.util.ArrayList;
import java.util.List;

/**
 * Reorders Gen3/Gen4 moves and detects gaps.
 *
 * <p>Those generations send one move per packet with no redundancy, so a dropped notification
 * leaves a hole that only the cube's move history can fill. Moves queue here until they are
 * contiguous; a gap asks for history and holds everything behind it, so the tracked model only ever
 * sees moves in order.
 *
 * <p>Ported from {@code afedotov/gan-web-bluetooth} (MIT).
 */
final class GanMoveBuffer {

  /**
   * Upstream disconnects the cube when the buffer runs away like this; the timer would rather
   * re-anchor and carry on.
   */
  private static final int OVERFLOW_LIMIT = 16;

  private final List<BufferedMove> buffer = new ArrayList<>();

  /** Serial of the last move handed on to the model. */
  private int lastSerial = -1;

  /** The newest serial the cube has mentioned, move or facelets. */
  private int serial = -1;

  boolean needsAnchor() {
    return lastSerial == -1;
  }

  int getLastSerial() {
    return lastSerial;
  }

  int size() {
    return buffer.size();
  }

  void reset() {
    buffer.clear();
    lastSerial = -1;
    serial = -1;
  }

  /** Anchor tracking at {@code atSerial} (from the cube's own facelets). */
  void anchor(int atSerial) {
    buffer.clear();
    lastSerial = atSerial;
    serial = atSerial;
  }

  /** Take in a live move and drain whatever is now contiguous. */
  Result push(BufferedMove move) {
    serial = move.getSerial();
    if (needsAnchor()) {
      return new Result();
    }
    buffer.add(move);
    return evict(true);
  }

  /** Inject a move recovered from the cube's history, then drain. */
  Result injectHistory(BufferedMove move) {
    if (needsAnchor()) {
      return new Result();
    }
    inject(move);
    // No history request here: this *is* the answer to one, and asking again on a still-short
    // reply would spin.
    return evict(false);
  }

  /**
   * A periodic facelets event at {@code atSerial} reveals moves we never saw when it runs ahead of
   * the model. Returns a history request to fill the gap, or null when nothing is missing.
   */
  HistoryRequest checkForMissedMoves(int atSerial) {
    serial = atSerial;
    if (needsAnchor()) {
      return null;
    }
    // Signed, so a snapshot that is *behind* the model reads as -1 rather than 255: a facelets
    // packet overtaken in flight by a move must not be mistaken for the cube being a whole serial
    // cycle ahead.
    int diff = signedSerialDiff(atSerial, lastSerial);
    // Serial 0 is skipped: the firmware reports a bogus facelets state as the move counter wraps
    // past 255.
    if (diff <= 0 || atSerial == 0) {
      return null;
    }
    int startSerial = !buffer.isEmpty() ? buffer.get(0).getSerial() : (atSerial + 1) & 0xFF;
    return new HistoryRequest(startSerial, diff + 1);
  }

  private void inject(BufferedMove move) {
    if (!buffer.isEmpty()) {
      BufferedMove head = buffer.get(0);
      for (BufferedMove held : buffer) {
        if (held.getSerial() == move.getSerial()) {
          return; // already held
        }
      }
      // Only a move that belongs in the hole between the model and the queue.
      if (!isSerialInRange(lastSerial, head.getSerial(), move.getSerial(), false, false)) {
        return;
      }
      // History arrives newest-first, so each one lands on the head in turn.
      if (move.getSerial() == ((head.getSerial() - 1) & 0xFF)) {
        buffer.add(0, move);
      }
    } else if (isSerialInRange(lastSerial, serial, move.getSerial(), false, true)) {
      // A move recovered from a periodic facelets check, with nothing queued.
      buffer.add(0, move);
    }
  }

  private Result evict(boolean canRequestHistory) {
    List<BufferedMove> evicted = new ArrayList<>();
    HistoryRequest request = null;
    while (!buffer.isEmpty()) {
      BufferedMove head = buffer.get(0);
      int diff = needsAnchor() ? 1 : (head.getSerial() - lastSerial) & 0xFF;
      if (diff > 1) {
        // A hole: hold the queue until the cube fills it.
        if (canRequestHistory) {
          request = new HistoryRequest(head.getSerial(), diff);
        }
        break;
      }
      buffer.remove(0);
      lastSerial = head.getSerial();
      evicted.add(head);
    }
    if (buffer.size() > OVERFLOW_LIMIT) {
      int lost = buffer.size();
      reset();
      return new Result(evicted, null, true, lost);
    }
    return new Result(evicted, request, false, 0);
  }

  /**
   * How far {@code a} is ahead of {@code b} on the mod-256 serial ring, as a signed value in
   * [-128, 127]. Negative means {@code a} is behind.
   */
  static int signedSerialDiff(int a, int b) {
    return ((a - b + 128) & 0xFF) - 128;
  }

  /** Whether the circular (mod 256) {@code value} falls in the range ({@code start}, {@code end}). */
  private static boolean isSerialInRange(int start, int end, int value, boolean closedStart,
      boolean closedEnd) {
    return ((end - start) & 0xFF) >= ((value - start) & 0xFF)
        && (closedStart || ((start - value) & 0xFF) > 0)
        && (closedEnd || ((end - value) & 0xFF) > 0);
  }

  /** A move held in the buffer, before it is known to be in order. */
  static final class BufferedMove {
    private final int serial;
    private final Face face;
    private final boolean prime;
    private final Long cubeTimeMs;
    private final Long hostTimeMs;

    BufferedMove(int serial, Face face, boolean prime, Long cubeTimeMs, Long hostTimeMs) {
      this.serial = serial;
      this.face = face;
      this.prime = prime;
      this.cubeTimeMs = cubeTimeMs;
      this.hostTimeMs = hostTimeMs;
    }

    int getSerial() {
      return serial;
    }

    Face getFace() {
      return face;
    }

    boolean isPrime() {
      return prime;
    }

    /** The cube's own clock, in ms. Null for a move recovered from history — those are not stamped. */
    Long getCubeTimeMs() {
      return cubeTimeMs;
    }

    /** Null for a recovered move: it was never seen live, so no host time means anything for it. */
    Long getHostTimeMs() {
      return hostTimeMs;
    }
  }

  /** The window of the cube's move history to ask for. */
  static final class HistoryRequest {
    private final int serial;
    private final int count;

    HistoryRequest(int serial, int count) {
      this.serial = serial;
      this.count = count;
    }

    int getSerial() {
      return serial;
    }

    int getCount() {
      return count;
    }
  }

  /** What the buffer concluded after taking a move in. */
  static final class Result {
    private final List<BufferedMove> evicted;
    private final HistoryRequest historyRequest;
    private final boolean desynced;
    private final int lostMoves;

    Result() {
      this(new ArrayList<>(), null, false, 0);
    }

    Result(List<BufferedMove> evicted, HistoryRequest historyRequest, boolean desynced,
        int lostMoves) {
      this.evicted = evicted;
      this.historyRequest = historyRequest;
      this.desynced = desynced;
      this.lostMoves = lostMoves;
    }

    /** Moves now known to be contiguous, oldest first. */
    List<BufferedMove> getEvicted() {
      return evicted;
    }

    /** Set when moves are missing and the cube should be asked for them. */
    HistoryRequest getHistoryRequest() {
      return historyRequest;
    }

    /** Set when the gap can no longer be recovered. */
    boolean isDesynced() {
      return desynced;
    }

    int getLostMoves() {
      return lostMoves;
    }
  }
}
