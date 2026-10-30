package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.OrientationHistory;
import java.util.List;

/**
 * Whether the cube was turned in the hand during a drill rep, read live from the gyro the way
 * {@link F2lRotations} reads a solve's track: how far the cube turned about its up axis between two
 * face turns, a rotation from {@link F2lRotations#ROTATION_DEGREES}. The gap from the case going up
 * to the first turn counts, since turning the cube to bring a back slot round is done right there.
 *
 * <p>A drill's turns come with no notation for a {@code d}: the cube reports a turn of the top layer
 * and the core turns a quarter under it. Such a turn is a {@code d} when the quarter turn falls
 * inside it ({@link #TURN_MS} before its report), and that quarter is not counted. A rotation made
 * between two turns falls in the gap and is.
 */
public final class RepRotations {

  /** How long before its report a turn's own twist of the core can start. */
  static final long TURN_MS = 350;
  /** How long the gyro's reading of a pose trails the turn report it belongs to. */
  static final long LAG_MS = 100;

  private RepRotations() {
  }

  /**
   * @param samples the orientation stream around the rep, oldest first; empty for no gyroscope
   * @param layerFace the face the last layer is on, which a {@code d} is reported as turning
   * @return false where there is nothing to read
   */
  public static boolean rotated(List<CubeMove> moves, long shownHostMs, long skewMs,
      List<OrientationHistory.Sample> samples, char layerFace) {
    if (moves.isEmpty() || samples.isEmpty()) {
      return false;
    }
    long from = shownHostMs;
    for (int i = 0; i < moves.size(); i++) {
      CubeMove move = moves.get(i);
      long at = hostTime(move, skewMs);
      long to = i == moves.size() - 1 ? at : at + LAG_MS;
      double twist = F2lRotations.upTwist(at(samples, from), at(samples, to));
      double core = 0;
      if (move.getFace().name().charAt(0) == layerFace) {
        double inTurn = F2lRotations.upTwist(at(samples, at - TURN_MS), at(samples, to));
        if (Math.abs(inTurn) >= F2lRotations.ROTATION_DEGREES) {
          core = 90 * Math.round(inTurn / 90);
        }
      }
      if (F2lRotations.angleBetween(twist, core) >= F2lRotations.ROTATION_DEGREES) {
        return true;
      }
      from = to;
    }
    return false;
  }

  private static long hostTime(CubeMove move, long skewMs) {
    Long host = move.getHostTimestampMs();
    return host != null ? host.longValue() : move.getCubeTimestampMs() + skewMs;
  }

  /** The last reading at or before {@code ms}, the first one where none is that early. */
  private static CubeOrientation at(List<OrientationHistory.Sample> samples, long ms) {
    CubeOrientation pose = samples.get(0).getOrientation();
    for (OrientationHistory.Sample sample : samples) {
      if (sample.getTimestampMs() > ms) {
        break;
      }
      pose = sample.getOrientation();
    }
    return pose;
  }
}
