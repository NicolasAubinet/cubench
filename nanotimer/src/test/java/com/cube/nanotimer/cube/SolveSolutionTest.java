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
        SolveSolution.from(moves("y@0", "B@10"), Arrays.asList(step("cross", 0, 100)));

    assertEquals("y R", solution.getSteps().get(0).getMoves());
  }

  /**
   * Rotations need the same treatment. After a y the cube's F axis has swung to the solver's
   * left, so a further half turn about it is one they would write as x2, not z2.
   */
  @Test
  public void rotationsAreRewrittenIntoTheSolversFrame() {
    SolveSolution solution =
        SolveSolution.from(moves("y@0", "z2@10"), Arrays.asList(step("cross", 0, 100)));

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
        Arrays.asList(step("cross", 0, 100)));

    // It flips the cube over, so the cube's U is at the solver's D and its F at their R.
    assertEquals("y z2 D R", solution.getSteps().get(0).getMoves());
  }

  @Test
  public void foldsASlicePairThatCarriesItsCoreSpin() {
    // A real slice turns the middle layer and rocks the core, which the gyro reports as x'. The
    // pair plus that spin is one M; the spin is not shown but still turns the frame, so the U the
    // cube reports next — measured from the rocked core — reads as an F.
    SolveSolution solution = SolveSolution.from(
        moves("R@0", "L'@10", "x'@20", "U@30"), Arrays.asList(step("cross", 0, 100)));

    assertEquals("M F", solution.getSteps().get(0).getMoves());
    assertEquals(2, solution.getMoveCount()); // the slice and the U, not the hidden spin
  }

  @Test
  public void leavesASlicePairLiteralWithoutItsCoreSpin() {
    // No spin means no slice: either a cube with no gyro, or a genuine two-handed R then L'. Either
    // way the raw faces stand and still replay to a solved cube — the whole point for gyroless cubes.
    SolveSolution solution = SolveSolution.from(
        moves("R@0", "L'@10", "U@20"), Arrays.asList(step("cross", 0, 100)));

    assertEquals("R L' U", solution.getSteps().get(0).getMoves());
    assertEquals(3, solution.getMoveCount());
  }

  @Test
  public void leavesAGenuineTwoHandedFaceMoveLiteral() {
    // The bug that started this: F then B' with no core-spin is two honest face turns, not an S'.
    SolveSolution solution = SolveSolution.from(
        moves("F@0", "B'@10", "R@20", "D@30"), Arrays.asList(step("cross", 0, 100)));

    assertEquals("F B' R D", solution.getSteps().get(0).getMoves());
    assertEquals(4, solution.getMoveCount());
  }

  @Test
  public void keepsANonSlicePairLiteral() {
    // R and L turn opposite ways in space: never a slice, so it never folds even with a core spin.
    SolveSolution solution =
        SolveSolution.from(moves("R@0", "L@10"), Arrays.asList(step("cross", 0, 100)));

    assertEquals("R L", solution.getSteps().get(0).getMoves());
    assertEquals(2, solution.getMoveCount());
  }

  @Test
  public void doesNotConsumeAGenuineRotationAsACoreSpin() {
    // The pair is followed by a y, not the x' a real slice spins, so it does not fold: the faces
    // stay literal and the y is a real reorientation that relabels what comes after.
    SolveSolution solution = SolveSolution.from(
        moves("R@0", "L'@10", "y@20", "F@30"), Arrays.asList(step("cross", 0, 100)));

    assertEquals("R L' y L", solution.getSteps().get(0).getMoves());
    assertEquals(3, solution.getMoveCount()); // R, L', F; the y is not counted
  }

  @Test
  public void foldsTwoSlicesWithCoreSpinsIntoAHalfSlice() {
    // Each M rocks the core, so both pairs carry an x'; M then M is M2 — one move, the way the
    // double-turn fold already handles faces.
    String stored = moves("R@0", "L'@10", "x'@20", "R@30", "L'@40", "x'@50");
    SolveSolution solution =
        SolveSolution.from(stored, Arrays.asList(step("cross", 0, 100)));

    assertEquals("M2", solution.getSteps().get(0).getMoves());
    assertEquals(1, solution.getMoveCount());
  }

  @Test
  public void namesTheSliceInTheSolversFrameAfterARotation() {
    // After a y the cube's R/L axis has swung to front/back; an M turns like L, and L has gone to
    // B, so the solver would write the same slice as S'.
    SolveSolution solution = SolveSolution.from(
        moves("y@0", "R@10", "L'@20", "x'@30"), Arrays.asList(step("cross", 0, 100)));

    assertEquals("y S'", solution.getSteps().get(0).getMoves());
    assertEquals(1, solution.getSteps().get(0).getMoveCount());
  }

  @Test
  public void withoutRotationsTheLettersAreLeftAlone() {
    SolveSolution solution = SolveSolution.from(moves("R@0", "U'@10", "B@20"),
        Arrays.asList(step("cross", 0, 100)));

    assertEquals("R U' B", solution.getSteps().get(0).getMoves());
  }

  @Test
  public void rotationsAreShownButNotCounted() {
    // Turning the whole cube solves nothing, so it must not inflate the count or deflate the TPS.
    String stored = moves("y@0", "R@10", "U@20", "x@30", "F@40");
    List<SolveStep> steps = Arrays.asList(step("cross", 0, 1000));

    SolveSolution solution = SolveSolution.from(stored, steps);

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

    SolveSolution solution = SolveSolution.from(stored, steps);

    // A turn about the cube's R axis, after a y has swung it to the front, is the solver's z.
    assertEquals("y z", solution.getSteps().get(0).getMoves());
    assertEquals(0, solution.getMoveCount());
  }

  @Test
  public void splitsMovesAcrossStepsOnTheirDurations() {
    String stored = moves("R@0", "U@100", "F@200", "L@300", "D@400");
    List<SolveStep> steps = Arrays.asList(step("cross", 0, 200), step("f2l", 0, 200),
        step("oll", 0, 0), step("pll", 0, 0));

    SolveSolution solution = SolveSolution.from(stored, steps);

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

    SolveSolution solution = SolveSolution.from(stored, steps);

    assertEquals("R2 U2 F", solution.getSteps().get(0).getMoves()); // two U' turns are U2, not U'2
    assertEquals(3, solution.getMoveCount());
  }

  @Test
  public void keepsOppositeTurnsOfTheSameFaceApart() {
    // R then R' is a turn and a turn back, not a half turn: two moves, honestly shown.
    SolveSolution solution =
        SolveSolution.from(moves("R@0", "R'@50"), Arrays.asList(step("cross", 0, 50)));

    assertEquals("R R'", solution.getSteps().get(0).getMoves());
    assertEquals(2, solution.getMoveCount());
  }

  @Test
  public void separatesAStepsPartsSoSlotBoundariesStayReadable() {
    String stored = moves("R@0", "U@100", "L@200", "D@300");
    List<SolveStep> steps = Arrays.asList(
        step("f2l", 0, 300, step("pair", 0, 100), step("pair", 0, 200)));

    SolveSolution solution = SolveSolution.from(stored, steps);

    assertEquals("R U · L D", solution.getSteps().get(0).getMoves());
  }

  @Test
  public void countsAPartThatTookNoMoveWithoutShiftingTheOnesAfterIt() {
    // The middle pair was already in its slot: it owns no move, and must not take the next one's.
    String stored = moves("R@0", "U@100", "L@200");
    List<SolveStep> steps = Arrays.asList(step("f2l", 0, 200,
        step("pair_rf", 0, 100), step("pair_fl", 0, 0), step("pair_lb", 0, 100)));

    SolveSolution.Step f2l = SolveSolution.from(stored, steps).getSteps().get(0);

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

    SolveSolution solution = SolveSolution.from(moves(tokens.toArray(new String[0])), steps);

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
    assertTrue(SolveSolution.from(null, Arrays.asList(step("cross", 0, 10))).isEmpty());
    assertTrue(SolveSolution.from("", Arrays.asList(step("cross", 0, 10))).isEmpty());
    assertTrue(SolveSolution.from("R@0", null).isEmpty());
  }

  @Test
  public void survivesACorruptedMove() {
    SolveSolution solution =
        SolveSolution.from("R@0 U@bad F@100", Arrays.asList(step("cross", 0, 100)));

    assertEquals("R F", solution.getSteps().get(0).getMoves());
  }

  /**
   * A blind solve is mostly a step that moves nothing: 10s of memorising, 5s of turning, then 2s
   * before the timer was stopped. The rate the hands went at is the middle one — over the whole 17s
   * it would read as a third of what it was.
   */
  @Test
  public void measuresTheRateOverTheStepsThatTurnedSomething() {
    List<SolveStep> steps = Arrays.asList(step("memo", 10000, 0),
        step("execution", 0, 5000), step("gap", 2000, 0));

    SolveSolution solution =
        SolveSolution.from(moves("R@10000", "U@11000", "F@12000", "L@13000", "B@14000"), steps);

    assertEquals(5, solution.getMoveCount());
    assertEquals(1.0, solution.getTps(), 1e-9);
  }

  /**
   * What a replay plays is not what the breakdown prints. The displayed reconstruction folds a
   * sensed pair into a half turn; the replay must not, because the offsets of the two turns are
   * what tell one flick apart from two deliberate moves.
   */
  @Test
  public void keepsQuarterTurnsAndTheirOffsetsForReplay() {
    List<SolveMovesFormat.Move> replayed = SolveSolution.timedSolution(moves("R@0", "R@40", "U@500"));

    assertEquals(3, replayed.size());
    assertEquals("R", replayed.get(0).getNotation());
    assertEquals(0, replayed.get(0).getOffsetMs());
    assertEquals("R", replayed.get(1).getNotation());
    assertEquals(40, replayed.get(1).getOffsetMs());
    assertEquals("U", replayed.get(2).getNotation());
    assertEquals(500, replayed.get(2).getOffsetMs());

    // The same stream shown as a reconstruction does fold them.
    assertEquals("R2 U", SolveSolution.from(moves("R@0", "R@40", "U@500"),
        Arrays.asList(step("cross", 0, 1000))).getSteps().get(0).getPartMoves(0));
  }

  /**
   * A replay turns the cube with the solver, so rotations stay in the stream — and the faces after
   * one are named in the frame it left, not the frame the cube reports in.
   */
  @Test
  public void keepsRotationsAndRelabelsWhatFollowsThem() {
    List<SolveMovesFormat.Move> replayed = SolveSolution.timedSolution(moves("y@0", "B@100"));

    assertEquals(2, replayed.size());
    assertEquals("y", replayed.get(0).getNotation());
    assertEquals("R", replayed.get(1).getNotation()); // the cube's B is on the solver's right after a y
  }

}
