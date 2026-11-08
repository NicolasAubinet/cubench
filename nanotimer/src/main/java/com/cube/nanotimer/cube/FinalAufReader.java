package com.cube.nanotimer.cube;

import com.cube.nanotimer.coach.CoachPayloadBuilder;
import com.cube.nanotimer.coach.FinalAufFigure;
import com.cube.nanotimer.vo.SolveTime;

import java.util.ArrayList;
import java.util.List;

/** The pause before a window's final AUFs, for the coach, off the kept readings. */
public final class FinalAufReader {

  private FinalAufReader() {
  }

  /**
   * @param solves newest first: only the newest {@link CoachPayloadBuilder#FAMILY_WINDOW} are read,
   *     the window the PLL's time is read over
   * @return the figure, or null under the floor
   */
  public static FinalAufFigure readFrom(List<SolveTime> solves, SolveReadings readings) {
    int plls = 0;
    List<Long> pauses = new ArrayList<Long>();
    int window = Math.min(solves.size(), CoachPayloadBuilder.FAMILY_WINDOW);
    for (SolveTime solve : solves.subList(0, window)) {
      Long pause = readings.readingOf(solve).getFinalAufMs();
      if (pause == null) {
        continue;
      }
      plls++;
      if (pause.longValue() != FinalAuf.NONE) {
        pauses.add(pause);
      }
    }
    return CoachPayloadBuilder.finalAuf(plls, pauses);
  }
}
