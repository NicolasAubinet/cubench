package com.cube.nanotimer.vo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@RunWith(JUnit4.class)
public class ScrambleTypeTest {

  @Test
  public void testRandomStateIsNeverSolved() {
    for (ScrambleType scrambleType : ScrambleTypes.THREE_BY_THREE) {
      for (int i = 0; i < 10000; i++) {
        assertFalse("Solved state generated for scramble type " + scrambleType.getName(),
            scrambleType.getRandomState().isSolved());
      }
    }
  }

  // Pins both halves by name so a new scramble type cannot quietly inherit a verdict: whether its
  // solves end solved decides whether the cube may mark them, and only the type itself knows.
  @Test
  public void testEndsSolvedIsDecidedForEveryScrambleType() {
    Set<String> endsSolved = new TreeSet<>();
    Set<String> endsUnsolved = new TreeSet<>();
    for (ScrambleType scrambleType : ScrambleTypes.THREE_BY_THREE) {
      (scrambleType.endsSolved() ? endsSolved : endsUnsolved).add(scrambleType.getName());
    }

    assertEquals("A drill that stops on an unsolved cube must not be judged by the cube",
        new TreeSet<>(Arrays.asList("f2l", "roux_second_block")), endsUnsolved);
    assertEquals("A new scramble type needs deciding: does its solve end on a solved cube?",
        new TreeSet<>(Arrays.asList("default", "last_layer", "pll", "corners", "edges",
            "roux_last_10_pieces", "roux_last_6_edges", "parity")), endsSolved);
  }

}
