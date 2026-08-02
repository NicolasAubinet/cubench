package com.cube.nanotimer.gui.widget.dialog;

import static org.junit.Assert.assertEquals;

import com.cube.nanotimer.cube.SolveMovesFormat;
import com.cube.nanotimer.cube.SolveSolution;

import java.util.List;

import org.junit.Test;

/**
 * The length a replay is measured against. Getting this wrong is not a rounding error — it shows a
 * time next to the solve's own that disagrees with it.
 */
public class SolveReplayDialogTest {

  private static List<SolveMovesFormat.Move> moves(String stored) {
    return SolveSolution.timedSolution(stored);
  }

  /** The timer stops when the cube reads solved, a beat after the last turn: that beat counts. */
  @Test
  public void usesTheMeasuredTimeRatherThanTheMovesSpan() {
    assertEquals(25350, SolveReplayDialog.coveredMs(moves("R@0 U@500 F@25310"), 25350));
  }

  /** Nothing before the first turn is replayed, so a blind solve's memorisation is not counted. */
  @Test
  public void dropsWhateverRanBeforeTheFirstTurn() {
    assertEquals(5350, SolveReplayDialog.coveredMs(moves("R@20000 U@25310"), 25350));
  }

  /** A DNF is handed 0, and a time that cannot span the moves is not believed either. */
  @Test
  public void fallsBackToTheMovesSpanWithoutACredibleTime() {
    assertEquals(25310, SolveReplayDialog.coveredMs(moves("R@0 U@500 F@25310"), 0));
    assertEquals(25310, SolveReplayDialog.coveredMs(moves("R@0 U@500 F@25310"), 900));
  }

  @Test
  public void noMovesCoverNoTime() {
    assertEquals(0, SolveReplayDialog.coveredMs(moves(""), 25350));
  }
}
