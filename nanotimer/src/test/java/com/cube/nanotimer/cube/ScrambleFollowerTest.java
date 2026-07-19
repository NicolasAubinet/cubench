package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import org.junit.Test;

public class ScrambleFollowerTest {

  private static CubeMove move(Face face, boolean prime) {
    return new CubeMove(face, prime, 0L);
  }

  /** Feed a move through both the follower and a mirror cube, then reconcile with the state. */
  private static void turn(ScrambleFollower follower, CubieCube mirror, Face face, boolean prime) {
    follower.onMove(move(face, prime));
    mirror.applyMove(face, prime);
    follower.onState(new CubeState(mirror.toFaceCube()));
  }

  @Test
  public void tracksProgressThroughScramble() {
    String[] scramble = {"R", "U'", "F2"};
    ScrambleFollower follower = new ScrambleFollower(scramble);
    CubieCube mirror = new CubieCube();

    assertEquals(0, follower.getDoneCount());
    assertFalse(follower.isComplete());

    turn(follower, mirror, Face.R, false);
    assertEquals(1, follower.getDoneCount());

    turn(follower, mirror, Face.U, true);
    assertEquals(2, follower.getDoneCount());

    turn(follower, mirror, Face.F, false);
    assertEquals(2, follower.getDoneCount()); // half turn in progress
    assertFalse(follower.isComplete());

    turn(follower, mirror, Face.F, false);
    assertEquals(3, follower.getDoneCount());
    assertTrue(follower.isComplete());
    assertFalse(follower.isWrong());
  }

  @Test
  public void doubleTurnAcceptsEitherDirection() {
    String[] scramble = {"R2"};
    ScrambleFollower follower = new ScrambleFollower(scramble);
    CubieCube mirror = new CubieCube();

    turn(follower, mirror, Face.R, true); // R' toward R2 is valid, not wrong
    assertFalse(follower.isWrong());
    assertEquals(0, follower.getDoneCount());

    turn(follower, mirror, Face.R, true); // R' R' == R2
    assertTrue(follower.isComplete());
    assertFalse(follower.isWrong());
  }

  @Test
  public void reversingAPartialDoubleTurnIsNeutral() {
    String[] scramble = {"R2", "U"};
    ScrambleFollower follower = new ScrambleFollower(scramble);
    CubieCube mirror = new CubieCube();

    turn(follower, mirror, Face.R, false);
    turn(follower, mirror, Face.R, true); // undo the first quarter, back to token start
    assertFalse(follower.isWrong());
    assertEquals(0, follower.getDoneCount());

    turn(follower, mirror, Face.R, false);
    turn(follower, mirror, Face.R, false);
    assertEquals(1, follower.getDoneCount());
  }

  @Test
  public void flagsWrongMoveAndRecovers() {
    String[] scramble = {"R", "U'", "F2"};
    ScrambleFollower follower = new ScrambleFollower(scramble);
    CubieCube mirror = new CubieCube();

    turn(follower, mirror, Face.R, false);
    assertFalse(follower.isWrong());

    turn(follower, mirror, Face.D, false); // not the expected U'
    assertTrue(follower.isWrong());
    assertEquals(1, follower.getDoneCount()); // progress unchanged
    assertEquals("D'", follower.getReverseMoves());

    turn(follower, mirror, Face.D, true); // undo -> back on track
    assertFalse(follower.isWrong());
    assertEquals(1, follower.getDoneCount());
    assertEquals("", follower.getReverseMoves());
  }

  @Test
  public void reverseMovesMergeAndOrder() {
    String[] scramble = {"R"};
    ScrambleFollower follower = new ScrambleFollower(scramble);
    CubieCube mirror = new CubieCube();

    turn(follower, mirror, Face.R, false); // complete the scramble
    assertTrue(follower.isComplete());

    turn(follower, mirror, Face.L, false); // extra moves after completion
    turn(follower, mirror, Face.U, false);
    turn(follower, mirror, Face.U, false);
    assertTrue(follower.isWrong());
    assertEquals("U2 L'", follower.getReverseMoves());
  }

  @Test
  public void wrongMovesThatCancelOutClear() {
    String[] scramble = {"R"};
    ScrambleFollower follower = new ScrambleFollower(scramble);
    CubieCube mirror = new CubieCube();

    turn(follower, mirror, Face.R, false);
    assertTrue(follower.isComplete());

    for (int i = 0; i < 4; i++) { // four D turns net to identity
      turn(follower, mirror, Face.D, false);
    }
    assertFalse(follower.isWrong());
    assertTrue(follower.isComplete());
  }

  @Test
  public void stepsBackWhenAMoveIsUndone() {
    String[] scramble = {"R", "U", "F"};
    ScrambleFollower follower = new ScrambleFollower(scramble);
    CubieCube mirror = new CubieCube();

    turn(follower, mirror, Face.R, false);
    turn(follower, mirror, Face.U, false);
    assertEquals(2, follower.getDoneCount());

    turn(follower, mirror, Face.U, true); // undo the U, no wrong move recorded
    assertEquals(1, follower.getDoneCount());
    assertFalse(follower.isWrong());
  }

  @Test
  public void undoesADoubleTurnInEitherDirection() {
    String[] scramble = {"U2", "R"};
    ScrambleFollower follower = new ScrambleFollower(scramble);
    CubieCube mirror = new CubieCube();

    turn(follower, mirror, Face.U, false);
    turn(follower, mirror, Face.U, false); // U2 done as U U
    assertEquals(1, follower.getDoneCount());

    // Undo the U2 the "other way" (U U again), not the strict inverse (U' U').
    turn(follower, mirror, Face.U, false);
    assertFalse(follower.isWrong()); // mid-state of the U2 is recognized, no undo prompt
    turn(follower, mirror, Face.U, false);
    assertEquals(0, follower.getDoneCount());
    assertFalse(follower.isWrong());
  }

  @Test
  public void reanchorsFromStateAfterDesync() {
    String[] scramble = {"R", "U'", "F2"};
    ScrambleFollower follower = new ScrambleFollower(scramble);

    CubieCube target = new CubieCube();
    target.applyMove(Face.R, false);
    target.applyMove(Face.U, true);
    follower.onState(new CubeState(target.toFaceCube())); // missed both moves
    assertEquals(2, follower.getDoneCount());
    assertFalse(follower.isWrong());
  }

  @Test
  public void followsASpecialScrambleTypeSequence() {
    // A last-layer scramble is a plain face-turn sequence like any other, so it must be followable.
    String[] scramble = {"R", "U", "R'", "U'"};
    assertTrue(ScrambleFollower.canFollow(scramble));

    ScrambleFollower follower = new ScrambleFollower(scramble);
    CubieCube mirror = new CubieCube();
    turn(follower, mirror, Face.R, false);
    turn(follower, mirror, Face.U, false);
    turn(follower, mirror, Face.R, true);
    turn(follower, mirror, Face.U, true);
    assertTrue(follower.isComplete());
  }

  @Test
  public void rejectsScramblesWithSliceOrWideMoves() {
    // roux_last_10_pieces appends a lowercase slice/wide move: the follower cannot track those.
    assertFalse(ScrambleFollower.canFollow(new String[] {"R", "U", "m'"}));
    assertFalse(ScrambleFollower.canFollow(new String[] {"R", "U", "r2"}));
    assertFalse(ScrambleFollower.canFollow(null));
    assertTrue(ScrambleFollower.canFollow(new String[] {"R", "", "U2"})); // blanks are harmless
  }

  @Test(expected = IllegalArgumentException.class)
  public void refusesToBuildFromAnUnsupportedMove() {
    new ScrambleFollower(new String[] {"R", "m", "U"});
  }
}
