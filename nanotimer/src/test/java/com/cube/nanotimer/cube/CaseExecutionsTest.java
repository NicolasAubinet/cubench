package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.step.AlgorithmExecution;
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
  /** F2L case 4 into each slot in turn, front right first. */
  private static final String PAIRS = "R U R' B U B' L U L' F U F'";

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

    assertEquals("R U R' U'",
        CaseExecutions.spreadOf(null, answers).getTurned().get(0).getMoves());
  }

  /** With nothing to go on twice, the most recent stands: it is at least something they turned. */
  @Test
  public void answersWithTheLatestWhenNothingWasTurnedTwice() {
    List<String> answers = Arrays.asList("R U2 R'", "R U R' U'");

    assertEquals("R U2 R'",
        CaseExecutions.spreadOf(null, answers).getTurned().get(0).getMoves());
  }

  /**
   * A solver really can have two answers to one case, picked by the angle it came up at. Both are
   * kept, most turned first, and each says how many of the answers it was.
   */
  @Test
  public void keepsEveryThingTheSolverTurnsForOneCase() {
    CaseExecutions.Spread spread = CaseExecutions.spreadOf(null,
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
        CaseExecutions.spreadOf(null, Arrays.asList("R U R' U'", "U R U R' U' U'"));

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

  /**
   * A solver regrips between solves and leaves the layer facing wherever the next case wants it, so
   * one algorithm comes back written several ways. That is one answer turned three times, not three
   * answers turned once each.
   */
  @Test
  public void countsOneAlgorithmRegrippedAsOneAnswer() {
    CaseExecutions.Spread spread = CaseExecutions.spreadOf("oll_27", Arrays.asList(
        "R U R' U R U2 R'", "F R F R' F R F2 R'", "U R U R' U R U2 R' U'"));

    assertEquals(1, spread.getTurned().size());
    assertEquals(3, spread.getTurned().get(0).getTimes());
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

    AlgorithmExecution execution =
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

  /**
   * The rule that keeps a wrecked case out of the list of algorithms a solver uses. The moves that
   * put a misread case right do solve the case, so nothing about them says they were a scramble and
   * a rebuild — what says so is that they happened once and the real answer happened twice.
   */
  @Test
  public void anAnswerTurnedOnlyOnceIsNotOneOfTheirAlgorithms() {
    List<CaseExecutions.Shown> shown = CaseExecutions.shownFrom("pll_t",
        spread(3, turned(TPERM, 2), turned("R U R' U'", 1)));

    assertEquals(1, shown.size());
    assertEquals("R U R' U' R' F R2 U' R' U' R U R' F'", shown.get(0).getMoves());
    assertEquals(2, shown.get(0).getTimes());
    assertEquals(3, shown.get(0).getOf()); // out of every answer, not out of the ones shown
  }

  /** Or there would be a case that had been solved and had nothing to show for it. */
  @Test
  public void theOneTurnedMostIsShownWhetherOrNotItWasTurnedTwice() {
    List<CaseExecutions.Shown> shown = CaseExecutions.shownFrom("pll_t",
        spread(2, turned(TPERM, 1), turned("R U R' U'", 1)));

    assertEquals(1, shown.size());
    assertEquals(1, shown.get(0).getTimes());
  }

  /** A case really can have two answers, and both are theirs once both have happened twice. */
  @Test
  public void bothAnswersAreShownOnceBothHaveRepeated() {
    assertEquals(2, CaseExecutions.shownFrom("pll_t",
        spread(4, turned(TPERM, 2), turned("R U R' U'", 2))).size());
  }

  /**
   * Executions that come out written alike are one row holding both counts, and adding them up can
   * carry that row past the one that was in front of it — which is the row the words go on.
   */
  @Test
  public void addingUpTwoSpellingsCanPutTheirRowInFront() {
    List<CaseExecutions.Shown> shown = CaseExecutions.shownFrom("pll_t", spread(5,
        turned("R U R' U'", 2), turned(TPERM, 1), turned("U " + TPERM + " U'", 1),
        turned("U2 " + TPERM + " U2", 1)));

    assertEquals(2, shown.size());
    assertEquals("R U R' U' R' F R2 U' R' U' R U R' F'", shown.get(0).getMoves());
    assertEquals(3, shown.get(0).getTimes());
    assertEquals("R U R' U'", shown.get(1).getMoves());
  }

  @Test
  public void showsNothingForACaseWithNoAnswer() {
    assertTrue(CaseExecutions.shownFrom("pll_t", null).isEmpty());
  }

  /** The moves the execution was read from stay with it: what it turned is not how it is written. */
  @Test
  public void keepsTheMovesAsTheyWereTurnedBesideTheWayTheyAreWritten() {
    CaseExecutions.Shown shown = CaseExecutions.shownFrom("pll_t",
        spread(2, turned("U " + TPERM + " U'", 2))).get(0);

    assertEquals("U " + TPERM + " U'", shown.getExecuted());
    assertEquals("R U R' U' R' F R2 U' R' U' R U R' F'", shown.getMoves());
  }

  /**
   * Four pairs of one case, one into each slot, are one way of turning it four times over, and are
   * read only when that case is asked for, never among the last layer cases.
   */
  @Test
  public void readsAPairOnlyWhenItsCaseIsAskedFor() {
    List<SolveTime> solves = solves(PAIRS + " " + SUNE + " " + TPERM);

    CaseExecutions.Spread pair = CaseExecutions.spreadFrom(solves, "pair_4");
    assertNotNull(pair);
    assertEquals(4, pair.getTurned().get(0).getTimes());
    assertEquals("R U R'", CaseExecutions.shownFrom("pair_4", pair).get(0).getMoves());
    assertFalse(CaseExecutions.readFrom(solves).containsKey("pair_4"));
  }

  @Test
  public void asksForAPairInEverySlotItCanGoInto() {
    List<String> codes = CaseExecutions.codesFor("pair_27");

    assertEquals(24, codes.size());
    assertTrue(codes.contains("pair_27_rf"));
  }

  @Test
  public void readsNoCaseOutOfAPairPlacedNoNamedWayOrSkipped() {
    assertEquals("pair_27", CaseExecutions.caseOfPart("pair_27_rf"));
    assertNull(CaseExecutions.caseOfPart("pair_other_rf"));
    assertNull(CaseExecutions.caseOfPart("pair_skip"));
    assertNull(CaseExecutions.caseOfPart("pair_rf"));
  }

  private static CaseExecutions.Turned turned(String moves, int times) {
    return new CaseExecutions.Turned(moves, times);
  }

  private static CaseExecutions.Spread spread(int of, CaseExecutions.Turned... turned) {
    return new CaseExecutions.Spread(Arrays.asList(turned), of);
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
