package com.cube.nanotimer.cube;

import com.cube.nanotimer.coach.PairRotation;
import com.cube.nanotimer.vo.SolveTime;

import java.util.ArrayList;
import java.util.List;

/**
 * Every F2L pair's rotations over a window's gyro solves, for the coach. Kept in each solve's
 * reading, so it costs no replay beyond {@link F2lHabitReader}'s.
 */
public final class F2lRotationReader {

  private F2lRotationReader() {
  }

  /** @return the pairs of the solves with a gyro track; the others add nothing */
  public static List<PairRotation> readFrom(List<SolveTime> solves, SolveReadings readings) {
    List<PairRotation> pairs = new ArrayList<PairRotation>();
    for (SolveTime solve : solves) {
      pairs.addAll(readings.readingOf(solve).getRotations());
    }
    return pairs;
  }
}
