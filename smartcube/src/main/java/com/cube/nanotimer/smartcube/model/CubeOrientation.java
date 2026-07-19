package com.cube.nanotimer.smartcube.model;

/**
 * The cube's physical orientation in space, as a unit quaternion {@code (w, x, y, z)}.
 *
 * <p>Reported by the MoYu V10's gyro stream (opcode 171) at ~20 Hz, independently of moves.
 * Identity ({@code w = 1}) is whatever orientation the cube's own reference happens to be —
 * it is not re-zeroed, so only <em>changes</em> between orientations are meaningful.
 */
public final class CubeOrientation {

  private final double w;
  private final double x;
  private final double y;
  private final double z;

  public CubeOrientation(double w, double x, double y, double z) {
    this.w = w;
    this.x = x;
    this.y = y;
    this.z = z;
  }

  public double getW() {
    return w;
  }

  public double getX() {
    return x;
  }

  public double getY() {
    return y;
  }

  public double getZ() {
    return z;
  }

  /** Squared norm; a well-formed orientation is 1. Used to sanity-check decoding. */
  public double normSquared() {
    return w * w + x * x + y * y + z * z;
  }

  /** Rotation angle in degrees, ignoring the axis. */
  public double angleDegrees() {
    double clamped = Math.max(-1, Math.min(1, w));
    return Math.toDegrees(2 * Math.atan2(Math.sqrt(1 - clamped * clamped), clamped));
  }

  /** This orientation's inverse (the conjugate, since it is a unit quaternion). */
  public CubeOrientation inverse() {
    return new CubeOrientation(w, -x, -y, -z);
  }

  /** Quaternion product {@code this · other}. */
  public CubeOrientation multiply(CubeOrientation o) {
    return new CubeOrientation(
        w * o.w - x * o.x - y * o.y - z * o.z,
        w * o.x + x * o.w + y * o.z - z * o.y,
        w * o.y - x * o.z + y * o.w + z * o.x,
        w * o.z + x * o.y - y * o.x + z * o.w);
  }

  /**
   * The rotation carrying this orientation to {@code later}, expressed in the <em>cube's own
   * frame</em>. The world frame cancels, so the result needs no reference or calibration.
   */
  public CubeOrientation deltaTo(CubeOrientation later) {
    return inverse().multiply(later);
  }

  public double dot(CubeOrientation o) {
    return w * o.w + x * o.x + y * o.y + z * o.z;
  }

  @Override
  public String toString() {
    return String.format("(%.4f, %.4f, %.4f, %.4f)", w, x, y, z);
  }
}
