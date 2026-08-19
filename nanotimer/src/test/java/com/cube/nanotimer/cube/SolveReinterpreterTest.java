package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * Which stored solves a method change may rewrite, read from the same recorded Roux solve
 * {@link StoredSolveReplayTest} uses.
 */
public class SolveReinterpreterTest {

  private static final String SCRAMBLE = RecordedSolveReplayTest.SCRAMBLE;
  private static final String MOVES = RecordedSolveReplayTest.MOVES;

  @Test
  public void rewritesASolveThatFitsTheMethod() {
    SolveTime solve = solve(1, SCRAMBLE, MOVES);

    List<SolveTime> rewritten =
        SolveReinterpreter.reread(Collections.singletonList(solve), CubeMethod.ROUX, null);

    assertEquals(1, rewritten.size());
    assertEquals(CubeMethod.ROUX, rewritten.get(0).getSmartcubeMethod());
    assertEquals(4, rewritten.get(0).getSmartcubeSteps().size());
  }

  @Test
  public void emptiesASolveThatCameOutSolvedAndFitsNothing() {
    SolveTime solve = solve(1, SCRAMBLE, MOVES);
    solve.setSmartcubeMethod(CubeMethod.ROUX);

    List<SolveTime> rewritten =
        SolveReinterpreter.reread(Collections.singletonList(solve), CubeMethod.LBL, null);

    assertEquals(1, rewritten.size());
    assertNull(rewritten.get(0).getSmartcubeMethod());
    assertTrue(rewritten.get(0).getSmartcubeSteps().isEmpty());
  }

  /**
   * Nothing else is touched. A solve with no moves cannot be read at all, and one whose walk never
   * came out solved may only have been walked from the wrong state — in both cases the breakdown
   * that is stored is the best there is, and emptying it would throw away what cannot be rebuilt.
   */
  @Test
  public void leavesEverythingItCannotJudge() {
    SolveTime unreadable = solve(1, SCRAMBLE, null);
    SolveTime wrongStart = solve(2, SCRAMBLE + " R", MOVES);
    unreadable.setSmartcubeMethod(CubeMethod.CFOP);
    wrongStart.setSmartcubeMethod(CubeMethod.CFOP);

    List<SolveTime> rewritten =
        SolveReinterpreter.reread(Arrays.asList(unreadable, wrongStart), CubeMethod.LBL, null);

    assertTrue(rewritten.isEmpty());
    assertEquals(CubeMethod.CFOP, unreadable.getSmartcubeMethod());
    assertEquals(CubeMethod.CFOP, wrongStart.getSmartcubeMethod());
  }

  @Test
  public void throwsTheWholeRunAwayWhenItIsStopped() {
    List<SolveTime> solves = Arrays.asList(solve(1, SCRAMBLE, MOVES), solve(2, SCRAMBLE, MOVES));

    List<SolveTime> rewritten = SolveReinterpreter.reread(solves, CubeMethod.ROUX,
        new SolveReinterpreter.Progress() {
          @Override
          public boolean onRead(int done, int total) {
            return false; // stopped on the first one
          }
        });

    assertNull(rewritten);
  }

  @Test
  public void countsTheSolvesItReads() {
    final int[] seen = new int[2];
    List<SolveTime> solves = Arrays.asList(solve(1, SCRAMBLE, MOVES), solve(2, SCRAMBLE, MOVES));

    SolveReinterpreter.reread(solves, CubeMethod.ROUX, new SolveReinterpreter.Progress() {
      @Override
      public boolean onRead(int done, int total) {
        seen[0] = done;
        seen[1] = total;
        return true;
      }
    });

    assertEquals(2, seen[0]);
    assertEquals(2, seen[1]);
  }

  private static SolveTime solve(int id, String scramble, String moves) {
    SolveTime solveTime = new SolveTime();
    solveTime.setId(id);
    solveTime.setScramble(scramble);
    solveTime.setSmartcubeMoves(moves);
    return solveTime;
  }
}
