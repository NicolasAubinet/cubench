package com.cube.nanotimer.smartcube.model;

/**
 * A single quarter-turn reported by the cube. A double turn arrives as two moves.
 */
public final class CubeMove {

  private final Face face;
  private final boolean prime;
  private final long cubeTimestampMs;
  private final Long hostTimestampMs;

  public CubeMove(Face face, boolean prime, long cubeTimestampMs) {
    this(face, prime, cubeTimestampMs, null);
  }

  public CubeMove(Face face, boolean prime, long cubeTimestampMs, Long hostTimestampMs) {
    this.face = face;
    this.prime = prime;
    this.cubeTimestampMs = cubeTimestampMs;
    this.hostTimestampMs = hostTimestampMs;
  }

  public Face getFace() {
    return face;
  }

  /** {@code true} for a counter-clockwise (prime) turn, {@code false} for clockwise. */
  public boolean isPrime() {
    return prime;
  }

  /** The move's timestamp on the cube's clock, fitted to host time. Monotonic within a connection. */
  public long getCubeTimestampMs() {
    return cubeTimestampMs;
  }

  /** Host wall-clock (ms since epoch) the move was processed; null except on a packet's most recent move. */
  public Long getHostTimestampMs() {
    return hostTimestampMs;
  }

  public String getNotation() {
    return face.name() + (prime ? "'" : "");
  }

  @Override
  public String toString() {
    return "CubeMove(" + getNotation() + " @ " + cubeTimestampMs + "ms)";
  }
}
