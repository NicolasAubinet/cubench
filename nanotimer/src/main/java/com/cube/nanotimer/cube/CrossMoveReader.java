package com.cube.nanotimer.cube;

import com.cube.nanotimer.coach.CoachPayloadBuilder;
import com.cube.nanotimer.coach.CrossTurn;
import com.cube.nanotimer.vo.SolveTime;

import java.util.ArrayList;
import java.util.List;

/** Every cross of a window's solves against its optimal, for the coach, off the kept readings. */
public final class CrossMoveReader {

  private CrossMoveReader() {
  }

  /**
   * @param solves newest first: only the newest {@link CoachPayloadBuilder#FAMILY_WINDOW} are read,
   *     so moves and the cross's time come off the same solves
   */
  public static List<CrossTurn> readFrom(List<SolveTime> solves, SolveReadings readings) {
    List<CrossTurn> crosses = new ArrayList<CrossTurn>();
    int window = Math.min(solves.size(), CoachPayloadBuilder.FAMILY_WINDOW);
    for (SolveTime solve : solves.subList(0, window)) {
      CrossTurn cross = readings.readingOf(solve).getCross();
      if (cross != null) {
        crosses.add(cross);
      }
    }
    return crosses;
  }
}
