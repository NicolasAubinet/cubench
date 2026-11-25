package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.cube.nanotimer.smartcube.step.CaseBotch;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveType;

import java.util.Collections;

import org.junit.Test;

/** Last layer solves, as in {@link CaseExecutionsTest}, thrown away part way through. */
public class CaseBotchesTest {

  private static final String SUNE = "R U R' U R U U R'";
  private static final String TPERM = "R U R' U' R' F R R U' R' U' R U R' F'";

  @Test
  public void namesTheCaseTheSolveStoppedInAndWhatWasTurnedInIt() {
    CaseBotches.Stopped stopped = CaseBotches.stoppedIn(solve(SUNE + " " + TPERM,
        SUNE + " R U R' U' R' F R R U R'"));

    assertNotNull(stopped);
    assertEquals("pll_t", stopped.caseCode);
    assertEquals(CaseBotch.Verdict.BOTCHED, judge(stopped));
  }

  @Test
  public void aCleanStartCutShortIsNotABotch() {
    CaseBotches.Stopped stopped = CaseBotches.stoppedIn(solve(SUNE + " " + TPERM,
        SUNE + " R U R' U' R' F"));

    assertEquals("pll_t", stopped.caseCode);
    assertEquals(CaseBotch.Verdict.CLEAN, judge(stopped));
  }

  @Test
  public void readsAnOllTheSolveStoppedIn() {
    CaseBotches.Stopped stopped = CaseBotches.stoppedIn(solve(SUNE + " " + TPERM,
        "R U R' U' F F R"));

    assertEquals("oll_27", stopped.caseCode);
    assertEquals(CaseBotch.Verdict.BOTCHED, judge(stopped));
  }

  @Test
  public void aFinishedSolveStoppedInNothing() {
    String moves = SUNE + " " + TPERM;
    assertNull(CaseBotches.stoppedIn(solve(moves, moves)));
  }

  @Test
  public void readsTheCmllARouxSolveStoppedIn() {
    String[] moves = RecordedSolveReplayTest.MOVES.split(" ");
    StringBuilder cut = new StringBuilder();
    for (int i = 0; i < 68; i++) { // inside its two-look CMLL's orientation
      cut.append(i == 0 ? "" : " ").append(moves[i]);
    }
    SolveTime solve = solve("", "R");
    solve.getSolveType().setMethod(CubeMethod.ROUX);
    solve.setScramble(RecordedSolveReplayTest.SCRAMBLE);
    solve.setSmartcubeMoves(cut.toString());
    solve.setTime(70_000);

    CaseBotches.Stopped stopped = CaseBotches.stoppedIn(solve);

    assertEquals("cmll_picolumns", stopped.caseCode);
    assertEquals("U' R U2 R2 U' R2 U'", stopped.moves);
    assertEquals(CaseBotch.Verdict.CLEAN, judge(stopped));
  }

  private static CaseBotch.Verdict judge(CaseBotches.Stopped stopped) {
    return CaseBotch.judge(stopped.caseCode, stopped.moves, Collections.<String>emptyList(), false)
        .getVerdict();
  }

  /** A solve scrambled for {@code planned} that turned {@code turned} before it was stopped. */
  private static SolveTime solve(String planned, String turned) {
    SolveType solveType = new SolveType(1, "3x3", false, null, CubeType.THREE_BY_THREE.getId());
    solveType.setMethod(CubeMethod.CFOP);
    SolveTime solve = new SolveTime();
    solve.setId(1);
    solve.setSolveType(solveType);
    solve.setScramble(CaseExecutionsTest.inverted(planned));
    StringBuilder played = new StringBuilder();
    int offsetMs = 200;
    for (String token : turned.split(" ")) {
      played.append(played.length() == 0 ? "" : " ").append(token).append('@').append(offsetMs);
      offsetMs += 200;
    }
    solve.setSmartcubeMoves(played.toString());
    solve.setTime(offsetMs + 1000);
    return solve;
  }
}
