package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.step.CaseBotch;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.SolveStep;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/** Last layer solves, as in {@link CaseExecutionsTest}, thrown away part way through. */
public class CaseBotchesTest {

  private static final String SUNE = "R U R' U R U U R'";
  private static final String TPERM = "R U R' U' R' F R R U' R' U' R U R' F'";

  @Test
  public void namesTheCaseTheSolveStoppedInAndWhatWasTurnedInIt() {
    CaseBotches.Attempt stopped = CaseBotches.stoppedIn(solve(SUNE + " " + TPERM,
        SUNE + " R U R' U' R' F R R U R'"));

    assertNotNull(stopped);
    assertEquals("pll_t", stopped.caseCode);
    assertEquals(CaseBotch.Verdict.BOTCHED, judge(stopped));
  }

  @Test
  public void aCleanStartCutShortIsNotABotch() {
    CaseBotches.Attempt stopped = CaseBotches.stoppedIn(solve(SUNE + " " + TPERM,
        SUNE + " R U R' U' R' F"));

    assertEquals("pll_t", stopped.caseCode);
    assertEquals(CaseBotch.Verdict.CLEAN, judge(stopped));
  }

  @Test
  public void readsAnOllTheSolveStoppedIn() {
    CaseBotches.Attempt stopped = CaseBotches.stoppedIn(solve(SUNE + " " + TPERM,
        "R U R' U' F F R"));

    assertEquals("oll_27", stopped.caseCode);
    assertEquals(CaseBotch.Verdict.BOTCHED, judge(stopped));
  }

  @Test
  public void aTwoLookStoppedAfterItsFirstLookNamesThatLook() {
    String planned = "F R U R' U' F' " + SUNE + " " + TPERM;
    CaseBotches.Attempt stopped = CaseBotches.stoppedIn(solve(planned,
        "F R U R' U' F' R U R' U"));

    assertEquals("oll_40", stopped.caseCode);
    assertEquals("F R U R' U' F'", stopped.firstLook);
    assertEquals(3, CaseBotch.turnCount(stopped.moves)); // the second look, begun
  }

  @Test
  public void anotherCasesWholeAlgorithmIsAMisreadNotAFirstLook() {
    String antiSune = "R U U R' U' R U' R'";
    CaseBotches.Attempt stopped = CaseBotches.stoppedIn(solve(SUNE + " " + TPERM,
        antiSune + " R U"));

    assertEquals("oll_27", stopped.caseCode);
    assertNull(stopped.firstLook);
    assertEquals(CaseBotch.Verdict.MISREAD, judge(stopped));
  }

  @Test
  public void anExecutionTurnedOnceIsNotTheSolversOwn() {
    String moves = SUNE + " " + TPERM;
    SolveTime once = solve(moves, moves);
    SolveTime twice = solve(moves, moves);
    twice.setId(2);

    assertTrue(CaseBotches.own(Arrays.asList(once), "pll_t").isEmpty());
    assertEquals(1, CaseBotches.own(Arrays.asList(once, twice), "pll_t").size());
  }

  @Test
  public void aCmllPermutedOnArrivalIsStillReadWhole() {
    SolveTime solve = solve(SUNE, "R U R' U' R' F");
    solve.getSolveType().setMethod(CubeMethod.ROUX);

    CaseBotches.Attempt stopped = CaseBotches.stoppedIn(solve);

    assertEquals("cmll_suneleftbar", stopped.caseCode);
    assertEquals("R U R' U' R' F", stopped.moves);
  }

  @Test
  public void aFinishedSolveStoppedInNothing() {
    String moves = SUNE + " " + TPERM;
    assertNull(CaseBotches.stoppedIn(solve(moves, moves)));
  }

  @Test
  public void aBotchThatBrokeF2lAndWasRepairedKeepsItsCaseInTwoParts() {
    String redone = "R U R' F' U F R U' R'"; // a pair taken out, put back another way: a new OLL
    String moves = redone + " " + SUNE + " " + TPERM;
    SolveTime solve = solve(moves, moves);

    List<SolveStep> steps = CaseExecutions.replayAs(solve, CubeMethod.CFOP).getSteps();

    assertNull(CaseBotches.stoppedIn(solve));
    assertEquals("oll_7", steps.get(0).getName());
    assertEquals(2, steps.get(0).getSubSteps().size()); // kept, it reads as a lapse
  }

  @Test
  public void aBotchThatBrokeF2lThenStoppedIsJudgedOnItsCase() {
    String redone = "R U R' F' U F R U' R'";
    CaseBotches.Attempt stopped = CaseBotches.stoppedIn(solve(redone + " " + SUNE + " " + TPERM,
        redone + " R U R' U R"));

    assertEquals("oll_7", stopped.caseCode);
    assertEquals(redone, stopped.moves);
    assertEquals(CaseBotch.Verdict.BOTCHED, judge(stopped));
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

    CaseBotches.Attempt stopped = CaseBotches.stoppedIn(solve);

    assertEquals("cmll_picolumns", stopped.caseCode);
    assertEquals("U' R U2 R2 U' R2 U'", stopped.moves);
    assertEquals(CaseBotch.Verdict.CLEAN, judge(stopped));
  }

  @Test
  public void aRepairedBotchInAFinishedSolveThrownAwayIsJudgedWhole() {
    String redone = "R U R' F' U F R U' R'";
    String moves = redone + " " + SUNE + " " + TPERM;

    List<CaseBotches.Attempt> attempts = CaseBotches.attemptsIn(asRead(solve(moves, moves)), true);

    assertEquals(1, attempts.size());
    assertEquals("oll_7", attempts.get(0).caseCode);
    assertEquals(redone + " R U R' U R U2 R'", attempts.get(0).moves); // the Sune, as written
    assertTrue(CaseBotch.judge("oll_7", attempts.get(0).moves, Collections.<String>emptyList(),
        true).counts());
  }

  @Test
  public void aFinishedSolveKeptIsNotReadAgainForItsFinishedSteps() {
    String moves = "R U R' F' U F R U' R' " + SUNE + " " + TPERM;
    assertTrue(CaseBotches.attemptsIn(asRead(solve(moves, moves)), false).isEmpty());
  }

  @Test
  public void aFinishedDnfSaysNothingOfItsSteps() {
    String moves = "R U R' F' U F R U' R' " + SUNE + " " + TPERM;
    SolveTime solve = asRead(solve(moves, moves));
    solve.setDNF();

    assertTrue(CaseBotches.attemptsIn(solve, true).isEmpty());
  }

  @Test
  public void aFinishedSolveInOneAlgorithmEachHasNothingToJudge() {
    String moves = SUNE + " " + TPERM;
    assertTrue(CaseBotches.attemptsIn(asRead(solve(moves, moves)), true).isEmpty());
  }

  @Test
  public void aFinishedTwoLookIsTwoLookedOnlyOnALearntCase() {
    String moves = "F R U R' U' F' " + SUNE + " " + TPERM;

    List<CaseBotches.Attempt> attempts = CaseBotches.attemptsIn(asRead(solve(moves, moves)), true);

    assertEquals(1, attempts.size());
    CaseBotches.Attempt oll = attempts.get(0);
    assertEquals("oll_40", oll.caseCode);
    assertEquals(CaseBotch.Verdict.CLEAN, judge(oll));
    assertEquals(CaseBotch.Verdict.TWO_LOOKED, CaseBotch.judge(oll.caseCode, oll.moves,
        Collections.<String>emptyList(), true).getVerdict());
  }

  @Test
  public void aSolveStoppedShortGivesOnlyTheCaseItStoppedIn() {
    String planned = "F R U R' U' F' " + SUNE + " " + TPERM;
    SolveTime solve = asRead(solve(planned, "F R U R' U' F' " + SUNE + " R U R' U' R' F R R U R'"));

    List<CaseBotches.Attempt> attempts = CaseBotches.attemptsIn(solve, true);

    assertEquals(1, attempts.size()); // its finished two-look OLL is left out, never added
    assertEquals("pll_t", attempts.get(0).caseCode);
  }

  @Test
  public void aStepTheSolvesOwnReadingHasInOnePartIsNotJudged() {
    String moves = "R U R' F' U F R U' R' " + SUNE + " " + TPERM;
    SolveTime solve = asRead(solve(moves, moves));
    List<SolveStep> read = new ArrayList<SolveStep>();
    for (SolveStep step : solve.getSmartcubeSteps()) { // the OLL as read at the time: one part
      read.add(step.getName().startsWith("oll_") ? new SolveStep(step.getStepIndex(),
          step.getName(), 0, 0, new ArrayList<SolveStep>()) : step);
    }
    solve.setSmartcubeSteps(read);

    assertTrue(CaseBotches.attemptsIn(solve, true).isEmpty());
  }

  @Test
  public void aFinishedSolveWithNoReadingOfItsOwnIsNotJudged() {
    String moves = "R U R' F' U F R U' R' " + SUNE + " " + TPERM;
    assertTrue(CaseBotches.attemptsIn(solve(moves, moves), true).isEmpty());
  }

  private static CaseBotch.Verdict judge(CaseBotches.Attempt stopped) {
    return CaseBotch.judge(stopped.caseCode, stopped.moves, Collections.<String>emptyList(), false)
        .getVerdict();
  }

  /** The solve with the reading it would have been saved with. */
  private static SolveTime asRead(SolveTime solve) {
    solve.setSmartcubeSteps(CaseExecutions.replayAs(solve, CubeMethod.CFOP).getSteps());
    return solve;
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
