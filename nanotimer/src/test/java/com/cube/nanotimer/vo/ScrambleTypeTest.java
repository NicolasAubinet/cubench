package com.cube.nanotimer.vo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

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

}
