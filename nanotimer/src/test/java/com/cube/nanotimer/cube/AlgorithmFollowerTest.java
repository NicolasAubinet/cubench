package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.Face;
import com.cube.nanotimer.smartcube.step.AlgorithmForm;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class AlgorithmFollowerTest {

  @Test
  public void greysTheMovesTurnedSoFar() {
    AlgorithmFollower follower = new AlgorithmFollower("R U R' U'");
    assertEquals(0, follower.getDoneCount());
    turn(follower, "R U");
    assertEquals(2, follower.getDoneCount());
    turn(follower, "R' U'");
    assertEquals(4, follower.getDoneCount());
  }

  @Test
  public void aTurnOfUBeforeTheAlgorithmIsNotAMove() {
    AlgorithmFollower follower = new AlgorithmFollower("R U R' U'");
    turn(follower, "U'");
    assertEquals(0, follower.getDoneCount());
    turn(follower, "R U");
    assertEquals(2, follower.getDoneCount());
  }

  /** U' before an algorithm starting U lands on the start state again, which is not U done. */
  @Test
  public void turnsBackToTheStartAreNotTheFirstMoveDone() {
    AlgorithmFollower follower = new AlgorithmFollower("U R U' R' U R U' R' L U2 L'");
    for (int i = 0; i < 6; i++) {
      turn(follower, "R U R' U'");
    }
    assertEquals(0, follower.getDoneCount());
    turn(follower, "U R");
    assertEquals(2, follower.getDoneCount());
  }

  @Test
  public void followsTheAlgorithmFromAnotherSide() {
    AlgorithmFollower follower = new AlgorithmFollower("R U R' U'");
    turn(follower, "F U F'");
    assertEquals(3, follower.getDoneCount());
  }

  @Test
  public void aRotationGreysWithTheMoveBeforeIt() {
    AlgorithmFollower follower = new AlgorithmFollower("(R U) y R'");
    turn(follower, "R U");
    assertEquals(3, follower.getDoneCount());
  }

  @Test
  public void aWrongTurnHoldsTheProgress() {
    AlgorithmFollower follower = new AlgorithmFollower("R U R' U'");
    turn(follower, "R D");
    assertEquals(1, follower.getDoneCount());
  }

  /** Yellow on top: the cube reports the solver's R U R' U' on its own faces, as L D L' D'. */
  @Test
  public void followsTheAlgorithmOnTheLayerFace() {
    AlgorithmFollower follower = new AlgorithmFollower("R U R' U'", "D", null);
    turn(follower, "L D L' D'");
    assertEquals(4, follower.getDoneCount());
  }

  @Test
  public void aTurnOfTheLayerFaceBeforeTheAlgorithmIsNotAMove() {
    AlgorithmFollower follower = new AlgorithmFollower("R U R' U'", "D", null);
    turn(follower, "D' F D");
    assertEquals(2, follower.getDoneCount());
  }

  private static final String SUNE = "R U R' U R U2 R'";

  /** A Sune dealt so that it is started from the front, R first. */
  @Test
  public void greysFromTheAngleThatFinishesTheCase() {
    AlgorithmFollower follower = new AlgorithmFollower(SUNE, "U", finishes(SUNE));
    turn(follower, "R U R'");
    assertEquals(3, follower.getDoneCount());
    assertFalse(follower.isWrongAngle());
  }

  /** The same Sune started from the left side: an R turned as an F, then its U. */
  @Test
  public void aSideTurnFromAnotherAngleIsAWrongAngle() {
    AlgorithmFollower follower = new AlgorithmFollower(SUNE, "U", finishes(SUNE));
    turn(follower, "F U");
    assertTrue(follower.isWrongAngle());
    assertEquals(0, follower.getDoneCount());
  }

  /** An F where the R was due is more likely a wrong face than a wrong grip: undone as F'. */
  @Test
  public void oneSideTurnIsASlipNotAWrongAngle() {
    AlgorithmFollower follower = new AlgorithmFollower(SUNE, "U", finishes(SUNE));
    turn(follower, "F");
    assertFalse(follower.isWrongAngle());
    assertEquals("F'", follower.getUndoMoves());
    turn(follower, "F'");
    assertEquals("", follower.getUndoMoves());
  }

  @Test
  public void aTurnOfTheLayerIsNeverAWrongAngle() {
    AlgorithmFollower follower = new AlgorithmFollower(SUNE, "U", finishes(SUNE));
    turn(follower, "U");
    assertFalse(follower.isWrongAngle());
    turn(follower, "U'");
    assertFalse(follower.isWrongAngle());
  }

  @Test
  public void undoingTheWrongTurnClearsIt() {
    AlgorithmFollower follower = new AlgorithmFollower(SUNE, "U", finishes(SUNE));
    turn(follower, "F F'");
    assertFalse(follower.isWrongAngle());
    turn(follower, "R U");
    assertEquals(2, follower.getDoneCount());
  }

  /** Squaring the layer up first and then starting from the front is a right angle too. */
  @Test
  public void aLayerTurnThenTheRightSideIsNotWrong() {
    AlgorithmFollower follower = new AlgorithmFollower(SUNE, "U", finishes("U' " + SUNE));
    turn(follower, "U' R U");
    assertFalse(follower.isWrongAngle());
    assertEquals(2, follower.getDoneCount());
  }

  @Test
  public void onTheAlgorithmThereIsNothingToUndo() {
    AlgorithmFollower follower = new AlgorithmFollower(SUNE, "U", finishes(SUNE));
    turn(follower, "R U");
    assertEquals("", follower.getUndoMoves());
  }

  /** A slip off the right angle is undone as the timer undoes a scramble's: its inverse. */
  @Test
  public void aSlipIsUndoneByItsInverse() {
    AlgorithmFollower follower = new AlgorithmFollower(SUNE, "U", finishes(SUNE));
    turn(follower, "R U D");
    assertEquals("D'", follower.getUndoMoves());
    turn(follower, "D'");
    assertEquals("", follower.getUndoMoves());
    assertEquals(2, follower.getDoneCount());
  }

  /** Yellow on top, the solver's R turned as the cube's L: the undo is named as they hold it. */
  @Test
  public void theUndoIsNamedAsTheCubeIsHeld() {
    AlgorithmFollower follower = new AlgorithmFollower("R U R' U'", "D", null);
    turn(follower, "L D L' R");
    assertEquals("L'", follower.getUndoMoves());
  }

  /** An R turned from the left side reaches the cube as F, and is undone as the R' it was. */
  @Test
  public void aWrongAngleIsUndoneAsHeld() {
    AlgorithmFollower follower = new AlgorithmFollower(SUNE, "U", finishes(SUNE));
    turn(follower, "F U");
    assertTrue(follower.isWrongAngle());
    assertEquals("U' R'", follower.getUndoMoves());
  }

  /** A slip put right leaves nothing to undo, but the rep still strayed from the algorithm. */
  @Test
  public void aSlipPutRightIsStillRemembered() {
    AlgorithmFollower follower = new AlgorithmFollower(SUNE, "U", finishes(SUNE));
    turn(follower, "R U");
    assertFalse(follower.hasStrayed());
    turn(follower, "D D'");
    assertEquals("", follower.getUndoMoves());
    assertTrue(follower.hasStrayed());
    follower.reset();
    assertFalse(follower.hasStrayed());
  }

  @Test
  public void aWrongAnglePutRightIsStillRemembered() {
    AlgorithmFollower follower = new AlgorithmFollower(SUNE, "U", finishes(SUNE));
    turn(follower, "F U U' F'");
    assertFalse(follower.isWrongAngle());
    assertTrue(follower.hasStrayed());
  }

  /** Squaring the layer up first and then the algorithm is no stray. */
  @Test
  public void theAlgorithmAfterALayerTurnNeverStrays() {
    AlgorithmFollower follower = new AlgorithmFollower(SUNE, "U", finishes("U' " + SUNE));
    turn(follower, "U' R U R' U R U U R'"); // the cube reports U2 as two quarters
    assertFalse(follower.hasStrayed());
  }
  /** After a y the solver's R is another face of the cube, and their slip on it is undone as R. */
  @Test
  public void aSlipAfterARotationIsNamedFromTheNewGrip() {
    Solver solver = new Solver("R U R' y R U R'");
    solver.turn("R", "U", "R'", "y", "R'");
    assertEquals("R", solver.follower.getUndoMoves());
  }

  /** A wide u turns the solver's hands round the cube like a y. */
  @Test
  public void aSlipAfterAWideTurnIsNamedFromTheNewGrip() {
    Solver solver = new Solver("R U R' u R U R'");
    solver.turn("R", "U", "R'", "u", "R'");
    assertEquals("R", solver.follower.getUndoMoves());
  }

  /** A slice tilts the frame: after M' the solver's U is what was their front. */
  @Test
  public void aSlipAfterASliceIsNamedFromTheNewFrame() {
    Solver solver = new Solver("R U R' M' U R U' R'");
    solver.turn("R", "U", "R'", "M'", "U'");
    assertEquals("U", solver.follower.getUndoMoves());
  }

  /** f R U R' U' f' lands on a state another grip reaches too, which must not name the undo. */
  @Test
  public void aGripThatOnlyLandsOnTheStateDoesNotNameTheUndo() {
    Solver solver = new Solver("f R U R' U' f' U' F R U R' U' F'"); // OLL 3
    solver.turn("f", "R", "U", "R'", "U'", "f'", "R");
    assertEquals("R'", solver.follower.getUndoMoves());
  }

  /** A turn the cube dropped, recovered from its next state, is followed like a reported one. */
  @Test
  public void aTurnTheCubeDroppedIsFollowedOnceRecovered() {
    AlgorithmFollower follower = new AlgorithmFollower(SUNE, "U", finishes(SUNE));
    turn(follower, "R U");
    CubieCube dropped = new CubieCube();
    dropped.applyMove(Face.R, true);
    follower.onMissed(dropped);
    turn(follower, "U R U U R'");
    assertEquals("", follower.getUndoMoves());
    assertFalse(follower.hasStrayed());
    assertEquals(7, follower.getDoneCount()); // all of it
  }

  /** Two dropped at once cannot be told apart as turns, and are placed by the state they leave. */
  @Test
  public void twoDroppedTurnsArePlacedByTheirState() {
    AlgorithmFollower follower = new AlgorithmFollower(SUNE, "U", finishes(SUNE));
    turn(follower, "R");
    CubieCube dropped = new CubieCube();
    dropped.applyMove(Face.U, false);
    dropped.applyMove(Face.R, true);
    follower.onMissed(dropped);
    assertEquals("", follower.getUndoMoves());
    assertEquals(3, follower.getDoneCount());
  }

  /** A solver turning tokens from the algorithm's frame, with the cube reporting outer turns. */
  private static final class Solver {

    final AlgorithmFollower follower;
    private final List<String> turned = new ArrayList<>();

    Solver(String algorithm) {
      List<String> cube = new ArrayList<>();
      for (List<String> turns : AlgorithmForm.perToken(algorithm.split(" "))) {
        cube.addAll(turns);
      }
      follower = new AlgorithmFollower(algorithm, "U", finishes(String.join(" ", cube)));
    }

    /** Each token as the cube reports it, named from where the tokens before it left the hands. */
    void turn(String... tokens) {
      for (String token : tokens) {
        turned.add(token);
        List<List<String>> per = AlgorithmForm.perToken(turned.toArray(new String[0]));
        for (String turn : per.get(per.size() - 1)) {
          Face face = Face.valueOf(turn.substring(0, 1));
          for (int q = 0; q < (turn.endsWith("2") ? 2 : 1); q++) {
            follower.onMove(new CubeMove(face, turn.endsWith("'"), 0));
          }
        }
      }
    }
  }

  /** Whether a path finishes the case the given moves solve, allowing a last turn of U. */
  private static Predicate<List<String>> finishes(String solution) {
    CubieCube dealt = new CubieCube();
    String[] moves = solution.split(" ");
    for (int i = moves.length - 1; i >= 0; i--) {
      String m = moves[i];
      boolean prime = !m.endsWith("'");
      Face face = Face.valueOf(m.substring(0, 1));
      for (int q = 0; q < (m.endsWith("2") ? 2 : 1); q++) {
        dealt.applyMove(face, prime);
      }
    }
    String start = dealt.toFaceCube();
    return path -> {
      CubieCube state = new CubieCube();
      state.fromFacelet(start);
      for (String turn : path) {
        for (int q = 0; q < (turn.endsWith("2") ? 2 : 1); q++) {
          state.applyMove(Face.valueOf(turn.substring(0, 1)), turn.endsWith("'"));
        }
      }
      for (int u = 0; u < 4; u++) {
        if (state.isSolved()) {
          return true;
        }
        state.applyMove(Face.U, false);
      }
      return false;
    };
  }

  private static void turn(AlgorithmFollower follower, String moves) {
    for (String move : moves.split(" ")) {
      Face face = Face.valueOf(move.substring(0, 1));
      follower.onMove(new CubeMove(face, move.endsWith("'"), 0));
    }
  }
}
