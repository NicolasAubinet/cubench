package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import org.junit.Test;

public class ScrambleFollowerTest {

  private static CubeState state(CubieCube cube) {
    return new CubeState(cube.toFaceCube());
  }

  @Test
  public void tracksProgressThroughScramble() {
    String[] scramble = {"R", "U'", "F2"};
    ScrambleFollower follower = new ScrambleFollower(scramble);
    CubieCube cube = new CubieCube();

    follower.onState(state(cube));
    assertEquals(0, follower.getDoneCount());
    assertFalse(follower.isComplete());

    cube.applyMove(Face.R, false);
    follower.onState(state(cube));
    assertEquals(1, follower.getDoneCount());

    cube.applyMove(Face.U, true);
    follower.onState(state(cube));
    assertEquals(2, follower.getDoneCount());

    cube.applyMove(Face.F, false);
    cube.applyMove(Face.F, false);
    follower.onState(state(cube));
    assertEquals(3, follower.getDoneCount());
    assertTrue(follower.isComplete());
    assertFalse(follower.isWrong());
  }

  @Test
  public void flagsWrongMoveAndRecovers() {
    String[] scramble = {"R", "U'", "F2"};
    ScrambleFollower follower = new ScrambleFollower(scramble);
    CubieCube cube = new CubieCube();

    cube.applyMove(Face.R, false);
    follower.onState(state(cube));
    assertEquals(1, follower.getDoneCount());
    assertFalse(follower.isWrong());

    cube.applyMove(Face.D, false); // not the expected U'
    follower.onState(state(cube));
    assertTrue(follower.isWrong());
    assertEquals(1, follower.getDoneCount()); // progress unchanged

    cube.applyMove(Face.D, true); // undo -> back on track
    follower.onState(state(cube));
    assertFalse(follower.isWrong());
    assertEquals(1, follower.getDoneCount());
  }

  @Test
  public void stepsBackWhenAMoveIsUndone() {
    String[] scramble = {"R", "U", "F"};
    ScrambleFollower follower = new ScrambleFollower(scramble);
    CubieCube cube = new CubieCube();

    cube.applyMove(Face.R, false);
    cube.applyMove(Face.U, false);
    follower.onState(state(cube));
    assertEquals(2, follower.getDoneCount());

    cube.applyMove(Face.U, true); // undo the U
    follower.onState(state(cube));
    assertEquals(1, follower.getDoneCount());
    assertFalse(follower.isWrong());
  }
}
