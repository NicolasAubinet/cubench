package com.cube.nanotimer.cube;

import com.cube.nanotimer.coach.CoachPayloadBuilder;
import com.cube.nanotimer.coach.StepTurning;
import com.cube.nanotimer.vo.SolveTime;

import java.util.ArrayList;
import java.util.List;

/**
 * Every step of a window's solves as it was turned, for the coach's moves and stalls. Read here
 * because only the app can replay a solve, and off the same kept readings the F2L pairs come from.
 */
public final class TurningReader {

  private TurningReader() {
  }

  /**
   * @param solves the solves, newest first, whatever they were solved as: only CFOP is read, and
   *     only the newest {@link CoachPayloadBuilder#FAMILY_WINDOW}, the window the step times are
   *     read over, so moves and time come off the same solves
   * @param readings where each solve's steps come from, read there only where none was kept
   */
  public static List<StepTurning> readFrom(List<SolveTime> solves, SolveReadings readings) {
    List<StepTurning> turnings = new ArrayList<StepTurning>();
    int window = Math.min(solves.size(), CoachPayloadBuilder.FAMILY_WINDOW);
    for (SolveTime solve : solves.subList(0, window)) {
      SolveReading reading = readings.readingOf(solve);
      for (SolveReading.Turning turning : reading.getTurnings()) {
        turnings.add(new StepTurning(solve.getId(), turning.getCode(), turning.getTimeMs(),
            turning.getMoves(), turning.getStalls(), turning.getStallMs(), turning.getStopMs(),
            reading.isWhole()));
      }
    }
    return turnings;
  }
}
