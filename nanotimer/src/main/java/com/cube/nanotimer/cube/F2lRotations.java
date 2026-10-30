package com.cube.nanotimer.cube;

import com.cube.nanotimer.coach.PairRotation;
import com.cube.nanotimer.cube.SolveMovesFormat.Move;
import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.step.CaseAlgorithms;
import com.cube.nanotimer.vo.SolveStep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The rotations a solver made during each F2L pair, read from the gyro track.
 *
 * <p><b>Not from the rotation tokens.</b> On real solves they are mostly noise: a wide move the
 * reader could not fold leaves a face turn and an {@code x}, and frame corrections come out as
 * {@code y' x'}. Measured over 996 pairs (2026-10-30): 1,648 tokens, about twice the real
 * rotations.
 *
 * <p>What is read instead: between two face turns of F2L, how far the cube turned about the cube
 * axis that points up. The gyro is gravity-referenced, so up is known, and yaw drifts only over
 * minutes. A peek comes back and nets nothing; an {@code x2} turns about a level axis and nets
 * nothing about up. A {@code d}, {@code u} or {@code E} turns the core a quarter turn about up, so
 * the gaps either side of one are read as one and its quarter turn taken off. A rotation is a twist
 * of {@link #ROTATION_DEGREES} or more: hand rotations land between 60° and 100°, and the values
 * thin out at 45-55°.
 *
 * <p>The gap from the cross into the first pair is left out: an {@code x2} after a cross built on
 * top, and the look for the first pair, are other advice.
 */
public final class F2lRotations {

  /** The twist about up, in degrees, from which a gap holds a rotation. */
  static final double ROTATION_DEGREES = 50;

  /** The quarter turn each core-turning move gives the cube about its up axis, signed. */
  private static final Map<String, Double> CORE_TURNS = new HashMap<String, Double>();

  static {
    CORE_TURNS.put("d", Double.valueOf(90));
    CORE_TURNS.put("d'", Double.valueOf(-90));
    CORE_TURNS.put("d2", Double.valueOf(180));
    CORE_TURNS.put("E", Double.valueOf(90));
    CORE_TURNS.put("E'", Double.valueOf(-90));
    CORE_TURNS.put("E2", Double.valueOf(180));
    CORE_TURNS.put("u", Double.valueOf(-90));
    CORE_TURNS.put("u'", Double.valueOf(90));
    CORE_TURNS.put("u2", Double.valueOf(180));
  }

  private static final double[][] AXES = {
      {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

  private F2lRotations() {
  }

  /**
   * @param storedMoves the solve's moves as read again, the stream the steps were cut from
   * @param steps its steps back to back from the start, F2L built in parts
   * @return one per F2L pair that was turned, in solving order; empty where the track or the F2L
   *     cannot be read
   */
  public static List<PairRotation> read(long solveId, String storedMoves, List<SolveStep> steps,
      GyroTrackFormat.GyroTrack track) {
    if (track == null || track.getKeyframes().isEmpty()) {
      return Collections.emptyList();
    }
    List<Long> offsets = new ArrayList<Long>();
    List<CubeOrientation> poses = new ArrayList<CubeOrientation>();
    for (GyroTrackFormat.Keyframe keyframe : track.getKeyframes()) {
      offsets.add(Long.valueOf(keyframe.getOffsetMs()));
      poses.add(keyframe.getOrientation());
    }
    return read(solveId, storedMoves, steps, offsets, poses);
  }

  /** As above with the track's poses, oldest first, each at its offset from the solve's start. */
  static List<PairRotation> read(long solveId, String storedMoves, List<SolveStep> steps,
      List<Long> offsets, List<CubeOrientation> poses) {
    long f2lStart = 0;
    SolveStep f2l = null;
    for (SolveStep step : steps) {
      if (SolveReading.F2L.equals(step.getName())) {
        f2l = step;
        break;
      }
      f2lStart += step.getTotalMs();
    }
    if (f2l == null || f2l.getSubSteps().isEmpty() || poses.isEmpty()) {
      return Collections.emptyList();
    }
    List<SolveStep> parts = f2l.getSubSteps();
    long[] ends = new long[parts.size()];
    long end = f2lStart;
    for (int i = 0; i < parts.size(); i++) {
      end += parts.get(i).getTotalMs();
      ends[i] = end;
    }

    List<Move> faces = new ArrayList<Move>();
    for (Move move : RotationRoundTrips.fold(SolveSolution.timedSolution(storedMoves))) {
      if (!SolveMovesFormat.isRotation(move.getNotation())) {
        faces.add(move);
      }
    }
    int[] turns = new int[parts.size()];
    int[] rotations = new int[parts.size()];
    PairRotation.Gaps[] start = new PairRotation.Gaps[parts.size()];
    PairRotation.Gaps[] inside = new PairRotation.Gaps[parts.size()];
    for (int i = 0; i < parts.size(); i++) {
      start[i] = PairRotation.Gaps.NONE;
      inside[i] = PairRotation.Gaps.NONE;
    }
    for (Move face : faces) {
      int pair = pairOf(face.getOffsetMs(), f2lStart, ends);
      if (pair >= 0) {
        turns[pair]++;
      }
    }
    int i = 0;
    while (i < faces.size() - 1) {
      Move from = faces.get(i);
      int j = i + 1;
      double coreTurn = 0;
      while (j < faces.size() - 1 && CORE_TURNS.containsKey(faces.get(j).getNotation())) {
        coreTurn += CORE_TURNS.get(faces.get(j).getNotation()).doubleValue();
        j++;
      }
      Move to = faces.get(j);
      i = j;
      int pair = pairOf(to.getOffsetMs(), f2lStart, ends);
      int fromPair = pairOf(from.getOffsetMs(), f2lStart, ends);
      if (pair < 0 || fromPair < 0 && pair == 0) {
        continue; // outside F2L, or the cross into the first pair
      }
      double twist = upTwist(at(offsets, poses, from.getOffsetMs()),
          at(offsets, poses, to.getOffsetMs()));
      boolean rotation = angleBetween(twist, coreTurn) >= ROTATION_DEGREES;
      long gapMs = to.getOffsetMs() - from.getOffsetMs();
      if (rotation) {
        rotations[pair]++;
      }
      if (fromPair == pair) {
        inside[pair] = inside[pair].with(gapMs, rotation);
      } else {
        start[pair] = start[pair].with(gapMs, rotation);
      }
    }

    List<PairRotation> read = new ArrayList<PairRotation>();
    for (int p = 0; p < parts.size(); p++) {
      if (turns[p] == 0) {
        continue; // a pair that went in on its own: nothing was turned for it
      }
      String caseCode = CaseExecutions.caseOfPart(parts.get(p).getName());
      read.add(new PairRotation(solveId, caseCode,
          caseCode != null && CaseAlgorithms.isBasicPair(caseCode), rotations[p], start[p],
          inside[p]));
    }
    return read;
  }

  /** The pair a move made at this offset belongs to, -1 outside F2L. A step owns its last move. */
  private static int pairOf(long offsetMs, long f2lStart, long[] ends) {
    if (offsetMs <= f2lStart) {
      return -1;
    }
    for (int i = 0; i < ends.length; i++) {
      if (offsetMs <= ends[i]) {
        return i;
      }
    }
    return -1;
  }

  /** The pose at or before {@code offsetMs}, the first one before the track starts. */
  private static CubeOrientation at(List<Long> offsets, List<CubeOrientation> poses,
      long offsetMs) {
    CubeOrientation pose = poses.get(0);
    for (int i = 0; i < offsets.size() && offsets.get(i).longValue() <= offsetMs; i++) {
      pose = poses.get(i);
    }
    return pose;
  }

  /**
   * How far the cube turned from {@code before} to {@code after} about the cube axis pointing most
   * nearly up at {@code before}, in degrees, signed, in (-180, 180].
   */
  static double upTwist(CubeOrientation before, CubeOrientation after) {
    CubeOrientation delta = after.multiply(before.inverse()); // in the world frame
    double[] up = null;
    for (double[] axis : AXES) {
      double[] world = rotate(before, axis);
      if (up == null || world[2] > up[2]) {
        up = world;
      }
    }
    double along = delta.getX() * up[0] + delta.getY() * up[1] + delta.getZ() * up[2];
    return normalize(2 * Math.toDegrees(Math.atan2(along, delta.getW())));
  }

  private static double[] rotate(CubeOrientation q, double[] v) {
    CubeOrientation turned = q.multiply(new CubeOrientation(0, v[0], v[1], v[2]))
        .multiply(q.inverse());
    return new double[] {turned.getX(), turned.getY(), turned.getZ()};
  }

  /** How far apart two signed angles are, in degrees, 0 to 180. */
  static double angleBetween(double a, double b) {
    return Math.abs(normalize(a - b));
  }

  private static double normalize(double degrees) {
    double d = degrees % 360;
    if (d > 180) {
      d -= 360;
    } else if (d <= -180) {
      d += 360;
    }
    return d;
  }
}
