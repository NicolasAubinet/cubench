package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.SolveStep;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/** The Analysis strip's moves and TPS against the figures each solve's own detail sheet shows. */
public class TurningFiguresTest {

  private static final String SUNE = "R U R' U R U U R'";
  private static final String TPERM = "R U R' U' R' F R R U' R' U' R U R' F'";

  /** A real Roux solve off a cube, as stored. */
  private static final String ROUX_SCRAMBLE =
      "U' B2 U' F2 D B2 U' F2 U L2 U F' D' U R F D' F D U' F";
  private static final String ROUX_MOVES = "z2@0 L'@0 U'@101 R'@399 D'@766 L@1491 D'@1654 L@2059"
      + " y@2768 D'@2768 D'@3105 F@3453 D'@3605 F'@3796 y'@4340 L@4340 D@4610 L'@4785 D'@5569"
      + " y'@6114 B'@6114 D'@6284 D'@6416 B@6579 D'@6675 B'@7020 D@7156 B@7483 B@7638 D@7962"
      + " B'@8154 D@8609 y2@9437 F@9437 D@9524 D@9645 F'@9711 D'@9852 F@10369 D@10504 F'@10622"
      + " B@11948 F'@12009 z@12010 x2@12149 R@12149 F@12302 z'@12382 D'@12382 F'@12525 z@12686"
      + " R'@12686 B'@12905 F@12914 z'@12915 D@13960 L@14243 D@14421 B@14697 D'@14955 B'@15168"
      + " L'@15347 B@16080 D'@16254 D'@16368 B'@16495 B'@16574 D'@16698 B@16839 B@16918"
      + " D'@17033 B'@17190 B'@17266 D'@17343 D'@17453 z'@17598 B@17598 y'@19485 z@19485"
      + " R@19485 R@19563 D@19684 R@19850 D@19942 R'@20020 D'@20115 R'@20280 D'@20430 R'@20674"
      + " D@20718 R'@20766";

  @Test
  public void cfopMatchesTheDetailSheet() {
    List<SolveTime> solves = CaseExecutionsTest.solves(SUNE + " " + TPERM);
    SolveSolution sheet = sheetOf(solves.get(0), CubeMethod.CFOP);

    TurningFigures figures =
        TurningFigures.readFrom(solves, SolveReadings.none(), CubeMethod.CFOP);

    assertEquals(1, figures.getSolves());
    assertEquals(sheet.getMoveCount(), figures.getMeanMoves(), 0.001);
    assertEquals(sheet.getTps(), figures.getTps(), 0.01);
  }

  @Test
  public void anotherMethodMatchesTheDetailSheet() {
    SolveTime solve = solve(ROUX_SCRAMBLE, ROUX_MOVES, CubeMethod.ROUX, 20772);
    SolveSolution sheet = sheetOf(solve, CubeMethod.ROUX);

    TurningFigures figures = TurningFigures.readFrom(Collections.singletonList(solve),
        SolveReadings.none(), CubeMethod.ROUX);

    assertEquals(1, figures.getSolves());
    assertEquals(sheet.getMoveCount(), figures.getMeanMoves(), 0.001);
    assertEquals(sheet.getTps(), figures.getTps(), 0.01);
  }

  @Test
  public void aSolveThatCannotBeReadIsLeftOut() {
    SolveTime solve = solve(ROUX_SCRAMBLE, ROUX_MOVES, CubeMethod.ROUX, 20772);
    solve.setScramble("R++ D-- U'"); // another puzzle's notation

    TurningFigures figures = TurningFigures.readFrom(Collections.singletonList(solve),
        SolveReadings.none(), CubeMethod.ROUX);

    assertEquals(0, figures.getSolves());
    assertEquals(0, figures.getTps(), 0);
  }

  @Test
  public void fixedFiguresGiveBackWhatTheyWereMadeFrom() {
    TurningFigures figures = TurningFigures.of(100, 57.4, 3.96);

    assertEquals(57.4, figures.getMeanMoves(), 0.001);
    assertEquals(3.96, figures.getTps(), 0.001);
  }

  /** What HistoryDetailDialog computes for one solve. */
  private static SolveSolution sheetOf(SolveTime solve, CubeMethod method) {
    StoredSolveReplay.Result reread = StoredSolveReplay.reinterpret(solve.getScramble(),
        solve.getSmartcubeMoves(), method, null);
    assertNotNull(reread);
    List<SolveStep> steps = SolveBreakdown.withTail(reread.getSteps(), reread.getStoppedStep(),
        SolveBreakdown.solvingDurationMs(solve), reread.getMoves(), reread.getMethod());
    return SolveSolution.from(reread.getMoves(), steps, reread.getMethod());
  }

  private static SolveTime solve(String scramble, String moves, CubeMethod method, long timeMs) {
    SolveType solveType = new SolveType(1, "3x3", false, null, CubeType.THREE_BY_THREE.getId());
    solveType.setMethod(method);
    SolveTime solve = new SolveTime();
    solve.setId(1);
    solve.setSolveType(solveType);
    solve.setScramble(scramble);
    solve.setSmartcubeMoves(moves);
    solve.setSmartcubeMethod(method);
    solve.setTime(timeMs);
    return solve;
  }
}
