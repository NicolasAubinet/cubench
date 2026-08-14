package com.cube.nanotimer.scrambler.cross;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashSet;
import java.util.Set;

@RunWith(JUnit4.class)
public class CrossFormatterTest {

  @Test
  public void testDFaceIsUnchanged() {
    String[] solution = { "R", "U'", "F2" };
    Assert.assertSame(solution, CrossFormatter.toCrossOnBottom(CrossFace.D, solution));
    Assert.assertEquals("", CrossFormatter.rotationPrefix(CrossFace.D));
  }

  @Test
  public void testNonDFacesPrependCanonicalRotation() {
    Assert.assertEquals("x2", CrossFormatter.rotationPrefix(CrossFace.U));
    Assert.assertEquals("x'", CrossFormatter.rotationPrefix(CrossFace.F));
    Assert.assertEquals("x", CrossFormatter.rotationPrefix(CrossFace.B));
    Assert.assertEquals("z", CrossFormatter.rotationPrefix(CrossFace.R));
    Assert.assertEquals("z'", CrossFormatter.rotationPrefix(CrossFace.L));

    String[] formatted = CrossFormatter.toCrossOnBottom(CrossFace.U, new String[] { "R", "U'" });
    Assert.assertEquals(3, formatted.length);
    Assert.assertEquals("x2", formatted[0]);
  }

  @Test
  public void testModifiersArePreserved() {
    // U face uses x2: U -> D, so "U'" -> "D'", "F2" -> "B2", "R" -> "R".
    String[] formatted = CrossFormatter.toCrossOnBottom(CrossFace.U, new String[] { "U'", "F2", "R" });
    Assert.assertArrayEquals(new String[] { "x2", "D'", "B2", "R" }, formatted);
  }

  // The same relabeling with nothing in front, for a screen that writes every row in this frame.
  @Test
  public void testMovesOnBottomLeavesTheRotationOut() {
    String[] solution = { "U'", "F2", "R" };
    Assert.assertArrayEquals(new String[] { "D'", "B2", "R" },
        CrossFormatter.movesOnBottom(CrossFace.U, solution));
    Assert.assertSame(solution, CrossFormatter.movesOnBottom(CrossFace.D, solution));
  }

  // A move at a time, which is how the user's own turns arrive.
  @Test
  public void testOneMoveReadsTheSameAsTheWholeWay() {
    for (CrossFace face : CrossFace.values()) {
      String[] moves = { "U", "D'", "R2", "L", "F'", "B2" };
      String[] way = CrossFormatter.movesOnBottom(face, moves);
      for (int i = 0; i < moves.length; i++) {
        Assert.assertEquals(way[i], CrossFormatter.moveOnBottom(face, moves[i]));
      }
    }
  }

  @Test
  public void testRelabelIsABijectionForEveryFace() {
    String[] faceLetters = { "U", "D", "R", "L", "F", "B" };
    for (CrossFace face : CrossFace.values()) {
      if (face == CrossFace.D) {
        continue;
      }
      String[] mapped = CrossFormatter.toCrossOnBottom(face, faceLetters);
      Set<String> seen = new HashSet<>();
      // skip the rotation prefix at index 0
      for (int i = 1; i < mapped.length; i++) {
        Assert.assertTrue("Relabel for " + face + " is not a bijection (collision on " + mapped[i] + ")",
            seen.add(mapped[i]));
      }
      Assert.assertEquals(6, seen.size());
    }
  }
}
