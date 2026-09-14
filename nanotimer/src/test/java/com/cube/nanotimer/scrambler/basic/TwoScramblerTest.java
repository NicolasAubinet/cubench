package com.cube.nanotimer.scrambler.basic;

import com.cube.nanotimer.scrambler.randomstate.RSTwoScrambler;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Guards the random-move 2x2 scramble against opposite faces. They turn a scramble into a regrip:
 * on a centreless puzzle U' D is a whole-cube rotation and changes nothing.
 */
@RunWith(JUnit4.class)
public class TwoScramblerTest {

  private static final int SCRAMBLES = 500;
  private static final Set<String> FACES = new HashSet<>(Arrays.asList("U", "R", "F"));
  private static final Set<String> DIRECTIONS = new HashSet<>(Arrays.asList("", "'", "2"));

  @Test
  public void turnsThreeAdjacentFacesOnly() {
    TwoScrambler scrambler = new TwoScrambler();
    for (int i = 0; i < SCRAMBLES; i++) {
      String previousFace = null;
      for (String move : scrambler.getNewScramble()) {
        String face = move.substring(0, 1);
        Assert.assertTrue("Unwanted face in " + move, FACES.contains(face));
        Assert.assertTrue("Unwanted direction in " + move, DIRECTIONS.contains(move.substring(1)));
        Assert.assertNotEquals("Same face twice in a row: " + move, previousFace, face);
        previousFace = face;
      }
    }
  }

  @Test
  public void isAsLongAsTheRandomStateScramble() {
    Assert.assertEquals(RSTwoScrambler.SCRAMBLE_LENGTH, new TwoScrambler().getNewScramble().length);
  }
}
