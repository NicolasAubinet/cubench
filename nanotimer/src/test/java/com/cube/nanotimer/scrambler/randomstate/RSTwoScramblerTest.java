package com.cube.nanotimer.scrambler.randomstate;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@RunWith(JUnit4.class)
public class RSTwoScramblerTest {

  /**
   * Official 2x2 scrambles are eleven moves of U, R and F, every one of them: 450,851 in the WCA
   * results export without a single exception. These are meant to be indistinguishable.
   */
  @Test
  public void testScramblesLookOfficial() {
    TwoSolver.genTables();
    RSTwoScrambler scrambler = new RSTwoScrambler();
    Map<String, Integer> faceCounts = new HashMap<String, Integer>();
    int scrambles = 300;
    for (int i = 0; i < scrambles; i++) {
      String[] scramble = scrambler.getNewScramble(new ScrambleConfig(RSTwoScrambler.SCRAMBLE_LENGTH));
      Assert.assertEquals(Arrays.toString(scramble), RSTwoScrambler.SCRAMBLE_LENGTH, scramble.length);
      String previousFace = null;
      for (String move : scramble) {
        String face = move.substring(0, 1);
        Assert.assertTrue("Unwanted face in " + Arrays.toString(scramble), "URF".contains(face));
        Assert.assertTrue("Unwanted direction in " + move,
            move.length() == 1 || "'2".contains(move.substring(1)));
        Assert.assertNotEquals("Same face twice in a row in " + Arrays.toString(scramble),
            previousFace, face);
        previousFace = face;
        Integer count = faceCounts.get(face);
        faceCounts.put(face, count == null ? 1 : count + 1);
      }
    }

    // A fixed search order would lean on whichever face it tries first, as the official ones do.
    System.out.println("Face counts: " + faceCounts);
    for (Map.Entry<String, Integer> e : faceCounts.entrySet()) {
      float share = ((float) e.getValue()) / (scrambles * RSTwoScrambler.SCRAMBLE_LENGTH);
      Assert.assertTrue("Face " + e.getKey() + " takes " + share + " of the moves",
          share > 0.25f && share < 0.42f);
    }
  }

  @Test
  public void testGenerateScrambles() {
    RSTwoScrambler scrambler = new RSTwoScrambler();
    int nScrambles = 100;
    long min = Integer.MAX_VALUE;
    long max = 0;
    int minLength = Integer.MAX_VALUE;
    int maxLength = 0;
    int totalLength = 0;
    Map<Integer, Integer> lengthRepartition = new HashMap<Integer, Integer>();
    System.out.println("Generating first scramble to generate tables (not counting this time)");
    long startTs = System.currentTimeMillis();
    scrambler.genTables();
    System.out.println("Tables generated in " + (System.currentTimeMillis() - startTs) + "ms");
    System.out.println("Generating " + nScrambles + " scrambles...");
    for (int i = 0; i < nScrambles; i++) {
      long ts = System.currentTimeMillis();
      String[] scramble = scrambler.getNewScramble(new ScrambleConfig(RSTwoScrambler.SCRAMBLE_LENGTH));
      long t = System.currentTimeMillis() - ts;
      if (t < min) {
        min = t;
      }
      if (t > max) {
        max = t;
      }
      if (scramble.length < minLength) {
        minLength = scramble.length;
      }
      if (scramble.length > maxLength) {
        maxLength = scramble.length;
      }
      if (lengthRepartition.get(scramble.length) == null) {
        lengthRepartition.put(scramble.length, 1);
      } else {
        lengthRepartition.put(scramble.length, lengthRepartition.get(scramble.length) + 1);
      }
      totalLength += scramble.length;
    }
    long total = System.currentTimeMillis() - startTs;
    System.out.println("Total time: " + total + " avg: " + (total / nScrambles) + " min: " + min + " max: " + max);
    System.out.println("Scramble min: " + minLength + " max: " + maxLength + " avg length: " + (((float) totalLength) / nScrambles));
    System.out.println("Length repartition:");
    for (Integer s : lengthRepartition.keySet()) {
      System.out.println("  length " + s + ": " + lengthRepartition.get(s));
    }
  }

}
