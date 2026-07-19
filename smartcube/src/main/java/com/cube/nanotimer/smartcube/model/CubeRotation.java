package com.cube.nanotimer.smartcube.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One of the 24 ways a cube can be held, with the shortest {@code x}/{@code y}/{@code z}
 * sequence that reaches it from the standard position (white up, green front).
 *
 * <p>Built by breadth-first search over the nine quarter/half rotations, so every entry carries
 * a minimal notation — {@code "z2"} rather than {@code "x2 y2"}.
 */
public final class CubeRotation {

  /**
   * The gyro's axes are not the cube's. Measured on a real V10 (probe 4.11a): a physical
   * {@code y} rotates about gyro −Z and an {@code x} about gyro −Y, which makes the cube's
   * right-handed (R, U, F) frame (−Y, −Z, +X) in gyro axes.
   */
  private static final double ROOT_HALF = Math.sqrt(0.5);

  /**
   * Generators in the cube's own frame: R = (1,0,0), U = (0,1,0), F = (0,0,1). Ordered by how
   * naturally a cuber writes them — several orientations have more than one shortest spelling
   * ({@code y x} and {@code x z'} are the same cube), and the search keeps whichever it meets
   * first, so {@code y} leads.
   */
  private static final String[] GEN_NAMES = {"y", "y'", "y2", "x", "x'", "x2", "z", "z'", "z2"};
  private static final double[][] GEN_AXES = {
    {0, 1, 0}, {0, 1, 0}, {0, 1, 0},
    {1, 0, 0}, {1, 0, 0}, {1, 0, 0},
    {0, 0, 1}, {0, 0, 1}, {0, 0, 1},
  };
  private static final double[] GEN_ANGLES = {90, -90, 180, 90, -90, 180, 90, -90, 180};

  /** Beyond this the reading is not a clean orientation change and is treated as unknown. */
  private static final double MATCH_TOLERANCE_DEGREES = 35;

  private static final List<CubeRotation> ALL = buildAll();

  private final String notation;
  private final CubeOrientation quaternion;

  private CubeRotation(String notation, CubeOrientation quaternion) {
    this.notation = notation;
    this.quaternion = quaternion;
  }

  /** Empty for the identity, otherwise e.g. {@code "y"} or {@code "x z'"}. */
  public String getNotation() {
    return notation;
  }

  public boolean isIdentity() {
    return notation.isEmpty();
  }

  public static List<CubeRotation> all() {
    return ALL;
  }

  /**
   * The rotation best matching {@code delta}, a body-frame delta straight from the gyro, or null
   * if it is too far from any of the 24 to call (a reading taken mid-turn, say).
   */
  public static CubeRotation nearest(CubeOrientation delta) {
    CubeOrientation inCubeFrame = toCubeFrame(delta);
    CubeRotation best = null;
    double bestDot = -1;
    for (CubeRotation candidate : ALL) {
      double dot = Math.abs(inCubeFrame.dot(candidate.quaternion)); // q and -q are one rotation
      if (dot > bestDot) {
        bestDot = dot;
        best = candidate;
      }
    }
    double errorDegrees = Math.toDegrees(2 * Math.acos(Math.min(1, bestDot)));
    return errorDegrees <= MATCH_TOLERANCE_DEGREES ? best : null;
  }

  /** Re-expresses a gyro-frame rotation in the cube's frame: R = −Y, U = −Z, F = +X. */
  static CubeOrientation toCubeFrame(CubeOrientation gyro) {
    return new CubeOrientation(gyro.getW(), -gyro.getY(), -gyro.getZ(), gyro.getX());
  }

  private static List<CubeRotation> buildAll() {
    Map<String, CubeRotation> found = new LinkedHashMap<>();
    CubeRotation identity = new CubeRotation("", new CubeOrientation(1, 0, 0, 0));
    found.put(key(identity.quaternion), identity);

    List<CubeRotation> frontier = new ArrayList<>(List.of(identity));
    while (!frontier.isEmpty() && found.size() < 24) {
      List<CubeRotation> next = new ArrayList<>();
      for (CubeRotation from : frontier) {
        for (int g = 0; g < GEN_NAMES.length; g++) {
          CubeOrientation turned = from.quaternion.multiply(generator(g));
          if (found.containsKey(key(turned))) {
            continue;
          }
          String notation = from.isIdentity() ? GEN_NAMES[g] : from.notation + " " + GEN_NAMES[g];
          CubeRotation reached = new CubeRotation(notation, turned);
          found.put(key(turned), reached);
          next.add(reached);
        }
      }
      frontier = next;
    }
    return List.copyOf(found.values());
  }

  private static CubeOrientation generator(int index) {
    double half = Math.toRadians(GEN_ANGLES[index]) / 2;
    double s = Math.sin(half);
    double[] axis = GEN_AXES[index];
    return new CubeOrientation(Math.cos(half), axis[0] * s, axis[1] * s, axis[2] * s);
  }

  /**
   * Key folding q and −q together, so each rotation is found once. The sign is fixed by the
   * first component that is meaningfully non-zero — a 180° rotation has {@code w} at ~1e-17,
   * whose float sign is noise and must not decide anything.
   */
  private static String key(CubeOrientation q) {
    double[] parts = {q.getW(), q.getX(), q.getY(), q.getZ()};
    int sign = 1;
    for (double part : parts) {
      if (Math.abs(part) > 1e-6) {
        sign = part < 0 ? -1 : 1;
        break;
      }
    }
    StringBuilder key = new StringBuilder();
    for (double part : parts) {
      key.append(Math.round(sign * part * 1000)).append(',');
    }
    return key.toString();
  }

  @Override
  public String toString() {
    return notation.isEmpty() ? "(none)" : notation;
  }
}
