package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.step.LastLayerCaseAlgorithms;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.SolveTime;
import com.cube.nanotimer.vo.SolveType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * Reading back what was turned for a case, from solves built the way a cube records them: quarter
 * turns with their offsets, against a scramble that is the solve taken backwards.
 *
 * <p>The solves here are last layer only, which is a solve whose cross and F2L came out of the
 * scramble already done. That is a legal solve and the shortest one that has both cases in it.
 */
public class CaseExecutionsTest {

  /** Written in quarter turns, since that is what a cube reports and what a stored stream holds. */
  private static final String SUNE = "R U R' U R U U R'";
  private static final String TPERM = "R U R' U' R' F R R U' R' U' R U R' F'";

  @Test
  public void readsTheMovesTurnedForEachCaseOfASolve() {
    Map<String, String> turned = CaseExecutions.readFrom(solves(SUNE + " " + TPERM));

    assertEquals("R U R' U R U2 R'", turned.get("oll_27"));
    assertEquals("R U R' U' R' F R2 U' R' U' R U R' F'", turned.get("pll_t"));
  }

  /** What the screen is for: the moves that come back are the algorithm the table knows. */
  @Test
  public void whatComesBackIsRecognisedAsTheAlgorithmItWas() {
    Map<String, String> turned = CaseExecutions.readFrom(solves(SUNE + " " + TPERM));

    assertNotNull(LastLayerCaseAlgorithms.matching("oll_27", turned.get("oll_27")));
    assertEquals("R U R' U' R' F R2 U' R' U' R U R' F'",
        LastLayerCaseAlgorithms.matching("pll_t", turned.get("pll_t")).getMoves());
  }

  /** A solve read under another method says nothing about these cases rather than guessing at them. */
  @Test
  public void readsNothingOutOfASolveThatIsNotCfop() {
    List<SolveTime> solves = solves(SUNE + " " + TPERM);
    solves.get(0).getSolveType().setMethod(CubeMethod.ROUX);

    assertTrue(CaseExecutions.readFrom(solves).isEmpty());
  }

  @Test
  public void readsNothingOutOfASolveThatCannotBeReadAgain() {
    List<SolveTime> solves = solves(SUNE + " " + TPERM);
    solves.get(0).setScramble("R++ D-- U'"); // another puzzle's notation

    assertTrue(CaseExecutions.readFrom(solves).isEmpty());
  }

  /**
   * The usual answer, not the last one. The first two here are the same turning said two ways, one
   * of them behind an alignment turn; the odd one out is the most recent and does not win.
   */
  @Test
  public void answersWithWhatIsUsuallyTurnedAndNotWithTheLatest() {
    List<String> answers = Arrays.asList("R U2 R'", "R U R' U'", "U R U R' U' U'");

    assertEquals("R U R' U'", CaseExecutions.spreadOf(answers).getTurned().get(0).getMoves());
  }

  /** With nothing to go on twice, the most recent stands: it is at least something they turned. */
  @Test
  public void answersWithTheLatestWhenNothingWasTurnedTwice() {
    List<String> answers = Arrays.asList("R U2 R'", "R U R' U'");

    assertEquals("R U2 R'", CaseExecutions.spreadOf(answers).getTurned().get(0).getMoves());
  }

  /**
   * A solver really can have two answers to one case, picked by the angle it came up at. Both are
   * kept, most turned first, and each says how many of the answers it was.
   */
  @Test
  public void keepsEveryThingTheSolverTurnsForOneCase() {
    CaseExecutions.Spread spread = CaseExecutions.spreadOf(
        Arrays.asList("R U2 R'", "R U R' U'", "U R U R' U' U'", "R U R' U'"));

    assertEquals(2, spread.getTurned().size());
    assertEquals(4, spread.getOf());
    assertEquals("R U R' U'", spread.getTurned().get(0).getMoves());
    assertEquals(3, spread.getTurned().get(0).getTimes());
    assertEquals("R U2 R'", spread.getTurned().get(1).getMoves());
    assertEquals(1, spread.getTurned().get(1).getTimes());
  }

  /** One answer is one answer: nothing to compare it against and nothing to say about a spread. */
  @Test
  public void hasOneAnswerForACaseAlwaysTurnedTheSameWay() {
    CaseExecutions.Spread spread =
        CaseExecutions.spreadOf(Arrays.asList("R U R' U'", "U R U R' U' U'"));

    assertEquals(1, spread.getTurned().size());
    assertEquals(2, spread.getOf());
    assertEquals(2, spread.getTurned().get(0).getTimes());
  }

  @Test
  public void hasNothingToSayAboutASolveWithNoMoves() {
    List<SolveTime> solves = solves(SUNE + " " + TPERM);
    solves.get(0).setSmartcubeMoves(null);

    assertTrue(CaseExecutions.readFrom(solves).isEmpty());
    assertNull(CaseExecutions.readFrom(new ArrayList<SolveTime>()).get("pll_t"));
  }

  /** Both codes a case is recorded under, since a step taking two algorithms names only the part. */
  @Test
  public void namesBothCodesACaseIsRecordedUnder() {
    assertEquals(Arrays.asList("oll_53", "ollalg_53"), CaseExecutions.codesFor("oll_53"));
    assertEquals(Arrays.asList("pll_jb", "alg_jb"), CaseExecutions.codesFor("pll_jb"));
    assertEquals(Arrays.asList("cross"), CaseExecutions.codesFor("cross"));
    assertTrue(CaseExecutions.codesFor(null).isEmpty());
  }

  /** One rule for how an execution is spelled, shared by the screen and the case dialog. */
  @Test
  public void writesAnExecutionTheWayTheTableWritesIt() {
    assertEquals("R U R' U R U2 R'",
        CaseExecutions.asAlgorithm("oll_27", "y U' R U R' U R U U R' U"));
    assertEquals("R U R' banana", CaseExecutions.asAlgorithm("oll_27", "R U R' banana"));
    assertNull(CaseExecutions.asAlgorithm("oll_27", null));
  }

  /**
   * What the chip on a row is drawn from, end to end: the moves cut out of a solve, read against the
   * algorithms the case is usually turned with. OLL 33 holds two percent of its case's votes for
   * this spelling and eight moves for the one nearly everybody turns.
   */
  @Test
  public void flagsAnExecutionHardlyAnybodyTurns() {
    Map<String, String> turned = CaseExecutions.readFrom(solves("R U R' F' U' F R U' R'"));

    LastLayerCaseAlgorithms.Execution execution =
        LastLayerCaseAlgorithms.read("oll_33", turned.get("oll_33"));
    assertTrue(execution.isUnusual());
    assertTrue(execution.isLonger());
    assertEquals(9, execution.getMoves());
    assertEquals(8, execution.getUsualMoves());
  }

  /** And the case turned the way the world turns it says nothing at all. */
  @Test
  public void saysNothingAboutAnExecutionEverybodyTurns() {
    Map<String, String> turned = CaseExecutions.readFrom(solves(SUNE + " " + TPERM));

    assertFalse(LastLayerCaseAlgorithms.read("oll_27", turned.get("oll_27")).isUnusual());
    assertFalse(LastLayerCaseAlgorithms.read("pll_t", turned.get("pll_t")).isUnusual());
  }

  /** One solve of the given moves, scrambled so that exactly they solve it. */
  private static List<SolveTime> solves(String moves) {
    SolveType solveType = new SolveType(1, "3x3", false, null, CubeType.THREE_BY_THREE.getId());
    solveType.setMethod(CubeMethod.CFOP); // named, so nothing reaches for the preferred one
    SolveTime solve = new SolveTime();
    solve.setId(1);
    solve.setSolveType(solveType);
    solve.setScramble(inverted(moves));
    solve.setSmartcubeMoves(played(moves));
    solve.setSmartcubeMethod(CubeMethod.CFOP);
    solve.setTime(200 * moves.split(" ").length + 1000);
    List<SolveTime> solves = new ArrayList<SolveTime>();
    solves.add(solve);
    return solves;
  }

  /** The stream a cube would have sent: one token per turn, each with its offset. */
  private static String played(String moves) {
    StringBuilder sb = new StringBuilder();
    int offsetMs = 200;
    for (String token : moves.split(" ")) {
      sb.append(sb.length() == 0 ? "" : " ").append(token).append('@').append(offsetMs);
      offsetMs += 200;
    }
    return sb.toString();
  }

  /** The scramble that leaves a cube needing exactly these moves: the solve, taken backwards. */
  private static String inverted(String moves) {
    String[] tokens = moves.split(" ");
    StringBuilder sb = new StringBuilder();
    for (int i = tokens.length - 1; i >= 0; i--) {
      String token = tokens[i];
      sb.append(sb.length() == 0 ? "" : " ").append(token.endsWith("2") ? token
          : token.endsWith("'") ? token.substring(0, 1) : token + "'");
    }
    return sb.toString();
  }
}
