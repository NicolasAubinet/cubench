package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

import java.util.Arrays;

public class PairEvidenceTest {

  private static final String LISTED = "R U R'";
  private static final String OWN = "R U2 R' U' R U R'";

  @Test
  public void aListedAlgorithmCountsTheFirstTime() {
    assertArrayEquals(new boolean[] {true}, PairEvidence.clean("pair_4", Arrays.asList(LISTED)));
  }

  @Test
  public void anUnlistedExecutionTurnedOnceSaysNothing() {
    assertArrayEquals(new boolean[] {false, true},
        PairEvidence.clean("pair_4", Arrays.asList(OWN, LISTED)));
  }

  @Test
  public void anUnlistedExecutionTurnedTwiceIsTheSolversOwn() {
    assertArrayEquals(new boolean[] {true, true, true},
        PairEvidence.clean("pair_4", Arrays.asList(OWN, LISTED, OWN)));
  }

  @Test
  public void theSameExecutionFromAnotherGripIsStillOne() {
    assertArrayEquals(new boolean[] {true, true},
        PairEvidence.clean("pair_4", Arrays.asList(OWN, "y' F U2 F' U' F U F'")));
  }

  @Test
  public void unreadableMovesSayNothing() {
    assertArrayEquals(new boolean[] {false, false},
        PairEvidence.clean("pair_4", Arrays.asList("Q?", "Q?")));
  }
}
