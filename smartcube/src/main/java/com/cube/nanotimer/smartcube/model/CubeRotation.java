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
  /**
   * Negative, because cube notation turns the opposite way to the right-hand rule: {@code y} is
   * clockwise seen from above, which about +U is −90°. Getting this backwards labels every
   * rotation as its own inverse, which reads plausibly and replays wrongly.
   */
  private static final double[] GEN_ANGLES = {-90, 90, 180, -90, 90, 180, -90, 90, 180};

  /** Beyond this the reading is not a clean orientation change and is treated as unknown. */
  private static final double MATCH_TOLERANCE_DEGREES = 35;

  /** Outward normals of each face in the cube's own frame. */
  private static final Map<Character, double[]> FACE_NORMALS = faceNormals();

  private static Map<Character, double[]> faceNormals() {
    Map<Character, double[]> normals = new LinkedHashMap<Character, double[]>();
    normals.put('R', new double[] {1, 0, 0});
    normals.put('L', new double[] {-1, 0, 0});
    normals.put('U', new double[] {0, 1, 0});
    normals.put('D', new double[] {0, -1, 0});
    normals.put('F', new double[] {0, 0, 1});
    normals.put('B', new double[] {0, 0, -1});
    return normals;
  }

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

  /** True for the nine 180° cells ({@code y2}, {@code x2}, the edge flips and their kin). */
  public boolean isHalfTurn() {
    return Math.abs(quaternion.getW()) < 0.26; // |w| is 0 at 180°, 0.5 at the 120° corners
  }

  public static List<CubeRotation> all() {
    return ALL;
  }

  /** The rotation spelled {@code notation}, or null if nothing spells it. */
  public static CubeRotation byNotation(String notation) {
    for (CubeRotation rotation : ALL) {
      if (rotation.notation.equals(notation)) {
        return rotation;
      }
    }
    return null;
  }

  /** This rotation followed by {@code next}. The later rotation goes on the left. */
  public CubeRotation then(CubeRotation next) {
    return ofQuaternion(next.quaternion.multiply(quaternion));
  }

  /** The rotation as seen by a solver who has already turned the cube by {@code frame}. */
  public CubeRotation seenFrom(CubeRotation frame) {
    return ofQuaternion(frame.quaternion.multiply(quaternion).multiply(frame.quaternion.inverse()));
  }

  /**
   * Where the face labelled {@code face} has been carried to. With the cube turned by this
   * rotation, a turn the cube still calls {@code face} is the one a solver would now write as
   * the returned letter.
   */
  public char mapFace(char face) {
    double[] normal = FACE_NORMALS.get(face);
    if (normal == null) {
      return face;
    }
    double[] turned = rotate(normal);
    char best = face;
    double bestDot = -2;
    for (Map.Entry<Character, double[]> candidate : FACE_NORMALS.entrySet()) {
      double[] n = candidate.getValue();
      double dot = turned[0] * n[0] + turned[1] * n[1] + turned[2] * n[2];
      if (dot > bestDot) {
        bestDot = dot;
        best = candidate.getKey();
      }
    }
    return best;
  }

  /** Carries a face normal through this rotation: {@code q·v·q⁻¹}. */
  private double[] rotate(double[] v) {
    CubeOrientation p = new CubeOrientation(0, v[0], v[1], v[2]);
    CubeOrientation r = quaternion.multiply(p).multiply(quaternion.inverse());
    return new double[] {r.getX(), r.getY(), r.getZ()};
  }

  private static CubeRotation ofQuaternion(CubeOrientation q) {
    CubeRotation best = null;
    double bestDot = -1;
    for (CubeRotation candidate : ALL) {
      double dot = Math.abs(q.dot(candidate.quaternion));
      if (dot > bestDot) {
        bestDot = dot;
        best = candidate;
      }
    }
    return best;
  }

  /**
   * The closest of the 24 to {@code delta}, with no tolerance at all. For readings known to be a
   * real reorientation — the opening of a solve — where snapping a noisy reading beats dropping it.
   */
  public static CubeRotation closest(CubeOrientation delta) {
    return ofQuaternion(toCubeFrame(delta));
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

  /**
   * Re-expresses a gyro-frame delta in the cube's frame: R = +X, U = +Z, F = −Y. A proper
   * rotation (one swap, one negation), which it must be — an improper map is a mirror, and a
   * mirrored delta depends on the frame it was composed in, i.e. on the session's arbitrary
   * gyro zero. Pinned together with {@link CubeOrientation#deltaTo} by three captured sessions
   * with scripted {@code y}/{@code x}/{@code z} sections; the axis letters only mean anything
   * for that delta order, so don't re-derive one without the other.
   */
  static CubeOrientation toCubeFrame(CubeOrientation gyro) {
    return new CubeOrientation(gyro.getW(), gyro.getX(), gyro.getZ(), -gyro.getY());
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
          // A notation reads left to right in the solver's fixed frame, so the added generator
          // multiplies on the left, as in then(). On the right it would read about moved axes.
          CubeOrientation turned = generator(g).multiply(from.quaternion);
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
