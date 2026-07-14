package com.cube.nanotimer.cube;

import static org.junit.Assert.assertFalse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import java.util.Random;
import org.junit.Test;

/**
 * A wrong follow must always be able to name the moves that undo it. The screen prints the "undo"
 * header and the reverse moves under it, so a wrong state with nothing to show strands the user.
 */
public class ScrambleFollowerInvariantTest {

  private static final String[] SCRAMBLE = {"R", "U2", "F'", "D", "L2", "B"};
  private static final Face[] FACES = Face.values();

  @Test
  public void alwaysNamesTheMovesThatUndoAWrongFollow() {
    Random random = new Random(7);
    for (int run = 0; run < 20000; run++) {
      ScrambleFollower follower = new ScrambleFollower(SCRAMBLE);
      follower.reset();
      StringBuilder played = new StringBuilder();
      for (int i = 0; i < 12; i++) {
        Face face = FACES[random.nextInt(FACES.length)];
        boolean prime = random.nextBoolean();
        played.append(face).append(prime ? "' " : " ");
        follower.onMove(new CubeMove(face, prime, i * 100));

        assertFalse("wrong with no moves to undo, after: " + played,
            follower.isWrong() && follower.getReverseMoves().isEmpty());
      }
    }
  }

  /**
   * A smart cube whose own model has drifted reports a state the moves never produced. The follow
   * is then meaningless — it was anchored to a cube we no longer have — and must say so instead of
   * judging the next honest turn against a path it left long ago.
   */
  @Test
  public void givesUpTheFollowWhenTheCubeTurnsUpSomewhereTheMovesCannotExplain() {
    ScrambleFollower follower = new ScrambleFollower(SCRAMBLE);
    follower.reset();
    follower.onMove(new CubeMove(Face.R, false, 0)); // the scramble's first move: on track
    assertEquals(1, follower.getDoneCount());

    // Two corners twisted against each other: a legal cube state, but not one these moves reached.
    boolean changed = follower.onState(new CubeState(
        "UUUUUUUUURRRRRRFRBFFFFFFFFDDDRDDDDDRLLLLLLLLLBBBBBBDBB"));

    assertTrue("the screen must refresh", changed);
    assertTrue("the follow is lost", follower.isLost());
    assertFalse("and it must not claim the user made a wrong move", follower.isWrong());
    assertEquals("nor keep progress it can no longer vouch for", 0, follower.getDoneCount());
  }
}
