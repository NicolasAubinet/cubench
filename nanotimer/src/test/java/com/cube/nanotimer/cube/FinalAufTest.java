package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.coach.FinalAufFigure;
import com.cube.nanotimer.vo.SolveTime;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Solves are built as {@link CrossMovesTest} builds them, D cross, with chosen gaps between turns. */
public class FinalAufTest {

  private static final String SOLVE = "U F R R U R' B U B' L U L' F U F' "
      + "R U R' U R U U R' R U R' U' R' F R R U' R' U' R U R' F'";

  /** A solve whose turns come 200 ms apart, except {@code pauseMs} before the turn at {@code at}. */
  private static SolveTime solve(String moves, int at, long pauseMs) {
    SolveTime solve = CaseExecutionsTest.solves(moves).get(0);
    String[] tokens = moves.split(" ");
    StringBuilder played = new StringBuilder();
    long offsetMs = 200;
    for (int i = 0; i < tokens.length; i++) {
      offsetMs += i == 0 ? 0 : i == at ? pauseMs : 200;
      played.append(i == 0 ? "" : " ").append(tokens[i]).append('@').append(offsetMs);
    }
    solve.setSmartcubeMoves(played.toString());
    solve.setTime(offsetMs + 300);
    return solve;
  }

  private static SolveTime withId(int id, SolveTime solve) {
    solve.setId(id);
    return solve;
  }

  private static Long pauseOf(SolveTime solve) {
    SolveReading reading = SolveReading.read(solve);
    assertTrue("the fixture should read as a whole CFOP solve", reading.isWhole());
    return reading.getFinalAufMs();
  }

  @Test
  public void timesTheGapBeforeTheFinalAuf() {
    String moves = SOLVE + " U";
    assertEquals(Long.valueOf(650), pauseOf(solve(moves, moves.split(" ").length - 1, 650)));
  }

  @Test
  public void aQuarterTurnPairAfterThePauseIsOneAuf() {
    String moves = SOLVE + " U U";
    assertEquals(Long.valueOf(500), pauseOf(solve(moves, moves.split(" ").length - 2, 500)));
  }

  @Test
  public void aPllEndingOnItsAlgorithmHasNoAuf() {
    assertEquals(Long.valueOf(FinalAuf.NONE), pauseOf(solve(SOLVE, 0, 0)));
  }

  @Test
  public void keptReadingGivesTheSamePause() {
    String moves = SOLVE + " U";
    SolveReading fresh = SolveReading.read(solve(moves, moves.split(" ").length - 1, 650));
    SolveReading kept = SolveReading.parse(SolveReading.toJson(fresh), 1);

    assertNotNull(kept);
    assertEquals(fresh.getFinalAufMs(), kept.getFinalAufMs());
  }

  @Test
  public void theReaderSendsNothingUnderTheFloorAndMeansThePausesAbove() {
    List<SolveTime> solves = new ArrayList<SolveTime>();
    String moves = SOLVE + " U";
    for (int i = 0; i < 12; i++) {
      solves.add(withId(i, i % 4 == 0 ? solve(SOLVE, 0, 0)
          : solve(moves, moves.split(" ").length - 1, 400)));
    }
    FinalAufFigure figure = FinalAufReader.readFrom(solves, SolveReadings.none());
    assertNull("9 final AUFs are under the floor", figure);

    for (int i = 0; i < 4; i++) {
      solves.add(withId(100 + i, solve(moves, moves.split(" ").length - 1, 400)));
    }
    figure = FinalAufReader.readFrom(solves, SolveReadings.none());
    assertNotNull(figure);
    assertEquals(16, figure.getSolves());
    assertEquals(13, figure.getAufs());
    assertEquals(400, figure.getPauseMs());
  }
}
