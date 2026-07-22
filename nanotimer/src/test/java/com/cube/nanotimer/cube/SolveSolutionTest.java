package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.vo.SolveStep;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class SolveSolutionTest {

  private static SolveStep step(String name, long recognitionMs, long executionMs, SolveStep... parts) {
    return new SolveStep(0, name, recognitionMs, executionMs, Arrays.asList(parts));
  }

  private static String moves(String... notationAtOffset) {
    StringBuilder sb = new StringBuilder();
    for (String move : notationAtOffset) {
      if (sb.length() > 0) {
        sb.append(' ');
      }
      sb.append(move);
    }
    return sb.toString();
  }

  /**
   * After a y the cube's B face is on the solver's right, so the turn the cube still calls B is
   * the one they would write as R. Without this the sequence cannot be followed at all.
   */
  @Test
  public void faceLettersAreRewrittenIntoTheSolversFrame() {
    SolveSolution solution =
        SolveSolution.from(moves("y@0", "B@10"), Arrays.asList(step("cross", 0, 100)), 100);

    assertEquals("y R", solution.getSteps().get(0).getMoves());
  }

  /**
   * Rotations need the same treatment. After a y the cube's F axis has swung to the solver's
   * left, so a further half turn about it is one they would write as x2, not z2.
   */
  @Test
  public void rotationsAreRewrittenIntoTheSolversFrame() {
    SolveSolution solution =
        SolveSolution.from(moves("y@0", "z2@10"), Arrays.asList(step("cross", 0, 100)), 100);

    assertEquals("y x2", solution.getSteps().get(0).getMoves());
  }

  /**
   * A reorientation of more than a quarter turn is stored as its tokens at one offset. They must
   * be read back as one rotation: "y z2" is y then z2 where the solver stands, and relabelling
   * the z2 as if the y had already turned its axes would name a different reorientation.
   */
  @Test
  public void aStoredReorientationsTokensAreReadBackAsOneRotation() {
    // The 28.24 solve's opening: y' x2, minted by the tracker under its minimal spelling y z2.
    SolveSolution solution = SolveSolution.from(moves("y@0", "z2@0", "U@10", "F@20"),
        Arrays.asList(step("cross", 0, 100)), 100);

    // It flips the cube over, so the cube's U is at the solver's D and its F at their R.
    assertEquals("y z2 D R", solution.getSteps().get(0).getMoves());
  }

  @Test
  public void withoutRotationsTheLettersAreLeftAlone() {
    SolveSolution solution = SolveSolution.from(moves("R@0", "U'@10", "B@20"),
        Arrays.asList(step("cross", 0, 100)), 100);

    assertEquals("R U' B", solution.getSteps().get(0).getMoves());
  }

  @Test
  public void rotationsAreShownButNotCounted() {
    // Turning the whole cube solves nothing, so it must not inflate the count or deflate the TPS.
    String stored = moves("y@0", "R@10", "U@20", "x@30", "F@40");
    List<SolveStep> steps = Arrays.asList(step("cross", 0, 100));

    SolveSolution solution = SolveSolution.from(stored, steps, 1000);

    // Stored cube-frame "y R U x F" reads as "y F U z U" where the solver stands: the body-frame
    // x is their z, and after it the cube's F faces up.
    assertEquals("y F U z U", solution.getSteps().get(0).getMoves());
    assertEquals(3, solution.getMoveCount()); // the three turns, not the two rotations
    assertEquals(3, solution.getSteps().get(0).getMoveCount());
    assertEquals(3.0, solution.getTps(), 1e-9); // 3 moves in one second, not 5
  }

  @Test
  public void aStepOfNothingButRotationsCountsZeroMoves() {
    String stored = moves("y@0", "x@10");
    List<SolveStep> steps = Arrays.asList(step("cross", 0, 100));

    SolveSolution solution = SolveSolution.from(stored, steps, 100);

    // A turn about the cube's R axis, after a y has swung it to the front, is the solver's z.
    assertEquals("y z", solution.getSteps().get(0).getMoves());
    assertEquals(0, solution.getMoveCount());
  }

  @Test
  public void splitsMovesAcrossStepsOnTheirDurations() {
    String stored = moves("R@0", "U@100", "F@200", "L@300", "D@400");
    List<SolveStep> steps = Arrays.asList(step("cross", 0, 200), step("f2l", 0, 200),
        step("oll", 0, 0), step("pll", 0, 0));

    SolveSolution solution = SolveSolution.from(stored, steps, 400);

    assertEquals("R U F", solution.getSteps().get(0).getMoves()); // through offset 200
    assertEquals("L D", solution.getSteps().get(1).getMoves()); // through offset 400
    assertEquals("", solution.getSteps().get(2).getMoves()); // a skip owns no moves
    assertEquals(5, solution.getMoveCount());
  }

  @Test
  public void foldsConsecutiveSameFaceTurnsIntoAHalfTurn() {
    // The cube reports R2 as two quarter turns; a cuber counts it as one move.
    String stored = moves("R@0", "R@50", "U'@100", "U'@150", "F@200");
    List<SolveStep> steps = Arrays.asList(step("cross", 0, 200));

    SolveSolution solution = SolveSolution.from(stored, steps, 200);

    assertEquals("R2 U2 F", solution.getSteps().get(0).getMoves()); // two U' turns are U2, not U'2
    assertEquals(3, solution.getMoveCount());
  }

  @Test
  public void keepsOppositeTurnsOfTheSameFaceApart() {
    // R then R' is a turn and a turn back, not a half turn: two moves, honestly shown.
    SolveSolution solution =
        SolveSolution.from(moves("R@0", "R'@50"), Arrays.asList(step("cross", 0, 50)), 50);

    assertEquals("R R'", solution.getSteps().get(0).getMoves());
    assertEquals(2, solution.getMoveCount());
  }

  @Test
  public void separatesAStepsPartsSoSlotBoundariesStayReadable() {
    String stored = moves("R@0", "U@100", "L@200", "D@300");
    List<SolveStep> steps = Arrays.asList(
        step("f2l", 0, 300, step("pair", 0, 100), step("pair", 0, 200)));

    SolveSolution solution = SolveSolution.from(stored, steps, 300);

    assertEquals("R U · L D", solution.getSteps().get(0).getMoves());
  }

  @Test
  public void countsAPartThatTookNoMoveWithoutShiftingTheOnesAfterIt() {
    // The middle pair was already in its slot: it owns no move, and must not take the next one's.
    String stored = moves("R@0", "U@100", "L@200");
    List<SolveStep> steps = Arrays.asList(step("f2l", 0, 200,
        step("pair_rf", 0, 100), step("pair_fl", 0, 0), step("pair_lb", 0, 100)));

    SolveSolution.Step f2l = SolveSolution.from(stored, steps, 200).getSteps().get(0);

    assertEquals(2, f2l.getPartMoveCount(0));
    assertEquals(0, f2l.getPartMoveCount(1));
    assertEquals(1, f2l.getPartMoveCount(2));
    assertEquals("R U · L", f2l.getMoves()); // the empty part leaves no stray separator
  }

  @Test
  public void rebuildsARealSolveRecordedOnTheCube() {
    // Step durations and move offsets taken from a solve recorded on a MoYu WeiLong V10.
    List<SolveStep> steps = Arrays.asList(
        step("cross", 0, 2251), step("f2l", 2538, 9758), step("oll", 1519, 1196),
        step("pll", 2701, 5453));
    long[] offsets = {0, 151, 320, 610, 910, 999, 1290, 1437, 2085, 2251, // cross: 10
        2794, 3431, 3904, 4053, 4252, 4451, 4908, 14000, 14547, // f2l (abridged): 9
        14600, 15000, 15500, 16000, 16500, 17000, 17100, 17262, // oll: 8
        18000, 19000, 20000, 25416}; // pll: 4
    String[] faces = {"R", "U", "F", "L", "D", "B"}; // cycled so nothing folds and counts stay exact
    List<String> tokens = new ArrayList<String>();
    for (int i = 0; i < offsets.length; i++) {
      tokens.add(faces[i % faces.length] + "@" + offsets[i]);
    }

    SolveSolution solution = SolveSolution.from(moves(tokens.toArray(new String[0])), steps, 25424);

    assertEquals(4, solution.getSteps().size());
    assertEquals("cross", solution.getSteps().get(0).getName());
    assertEquals(10, solution.getSteps().get(0).getMoveCount());
    assertEquals(9, solution.getSteps().get(1).getMoveCount());
    assertEquals(8, solution.getSteps().get(2).getMoveCount());
    assertEquals(4, solution.getSteps().get(3).getMoveCount());
    // Every move lands in exactly one step: what makes storing only the offsets enough.
    assertEquals(offsets.length, solution.getMoveCount());
    assertTrue(solution.getTps() > 0);
  }

  @Test
  public void hasNothingToShowWithoutMoves() {
    assertTrue(SolveSolution.from(null, Arrays.asList(step("cross", 0, 10)), 10).isEmpty());
    assertTrue(SolveSolution.from("", Arrays.asList(step("cross", 0, 10)), 10).isEmpty());
    assertTrue(SolveSolution.from("R@0", null, 10).isEmpty());
  }

  @Test
  public void survivesACorruptedMove() {
    SolveSolution solution =
        SolveSolution.from("R@0 U@bad F@100", Arrays.asList(step("cross", 0, 100)), 100);

    assertEquals("R F", solution.getSteps().get(0).getMoves());
  }

}
