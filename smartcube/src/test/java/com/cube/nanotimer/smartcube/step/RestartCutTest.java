package com.cube.nanotimer.smartcube.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * A last layer case the solver leaves and turns back to. Nothing about the moves themselves says
 * they were a wrecking and a rebuild — they do end at the case, and the real algorithm does follow
 * them — so left uncut the whole excursion reads as one very long algorithm the solver uses.
 *
 * <p>The cut fires on the state, not on a length: standing again at the case you left is the whole
 * signal. An AUF cannot trigger it, a U turn never leaving the family the step rests in. The guard
 * below is the other half — no algorithm anyone actually turns may pass back through its own start
 * case, or a clean execution would be cut in two. Face-turn rows only, for the reason
 * {@link OneLookOllTest} gives.
 */
public class RestartCutTest {

  private static final String PLL_RESTART = "pllrestart";
  private static final String OLL_RESTART = "ollrestart";

  private static final String T_PERM = "R U R' U' R' F R2 U' R' U' R U R' F'";
  private static final String SUNE = "R U R' U R U2 R'";

  /** Wrecks the first two layers and puts them back, leaving the layer at the case it started from. */
  private static final String FUMBLE = "R U R' U' U R U' R'";

  private final List<String> wrong = new ArrayList<>();

  @Test
  public void cutsAPllTheSolverLeftAndCameBackTo() {
    CFOPStepDetector detector = play(T_PERM, FUMBLE + " " + T_PERM);
    assertEquals(names(detector, CFOPStepDetector.PLL).toString(), 2,
        detector.subStepCount(CFOPStepDetector.PLL));
    assertEquals(PLL_RESTART, detector.subStepName(CFOPStepDetector.PLL, 0));
    assertEquals("alg_t", detector.subStepName(CFOPStepDetector.PLL, 1));
  }

  @Test
  public void cutsAnOllTheSolverLeftAndCameBackTo() {
    CFOPStepDetector detector = play(SUNE, FUMBLE + " " + SUNE);
    assertEquals(names(detector, CFOPStepDetector.OLL).toString(), 2,
        detector.subStepCount(CFOPStepDetector.OLL));
    assertEquals(OLL_RESTART, detector.subStepName(CFOPStepDetector.OLL, 0));
    assertEquals("ollalg_27", detector.subStepName(CFOPStepDetector.OLL, 1));
  }

  /** The algorithm that followed is dated from the return, not from where the fumble began. */
  @Test
  public void datesTheAlgorithmFromWhereTheFumbleEnded() {
    CFOPStepDetector detector = play(T_PERM, FUMBLE + " " + T_PERM);
    Long restartEnd = detector.getSubStepTimestampMs(CFOPStepDetector.PLL, 0);
    Long algorithmEnd = detector.getSubStepTimestampMs(CFOPStepDetector.PLL, 1);
    assertEquals(Long.valueOf(turnCount(FUMBLE) * 100L), restartEnd);
    assertTrue(restartEnd + " then " + algorithmEnd, algorithmEnd.longValue() > restartEnd.longValue());
  }

  /** Uncut without the fumble, so the two parts above are the fumble's doing and nothing else. */
  @Test
  public void leavesACleanExecutionWhole() {
    CFOPStepDetector detector = play(T_PERM, T_PERM);
    assertEquals(names(detector, CFOPStepDetector.PLL).toString(), 1,
        detector.subStepCount(CFOPStepDetector.PLL));
    assertEquals("alg_t", detector.subStepName(CFOPStepDetector.PLL, 0));
  }

  /** An AUF is not a restart: a U turn never leaves the family the step rests in. */
  @Test
  public void doesNotCutAnAuf() {
    String withAuf = "U " + T_PERM;
    CFOPStepDetector detector = play(withAuf, withAuf);
    assertEquals(names(detector, CFOPStepDetector.PLL).toString(), 1,
        detector.subStepCount(CFOPStepDetector.PLL));
    assertEquals("alg_t", detector.subStepName(CFOPStepDetector.PLL, 0));
  }

  /**
   * The guard. Every face-turn algorithm the shipped table holds, turned on the case it solves, must
   * reach the end without ever standing at its own start case again.
   *
   * <p><b>It covers 81 of the table's 304 rows, which is 51 of the 78 cases.</b> The rest carry a
   * wide, a slice or a rotation, and reach a detector as the outer turns plus the frame they move,
   * which is a different thing to feed and is the shape {@link OneLookOllTest} excludes for the same
   * reason. So this says no <em>common</em> algorithm trips the cut, not that none can.
   */
  @Test
  public void noShippedAlgorithmPassesBackThroughItsOwnStartCase() {
    int run = 0;
    for (String[] row : LastLayerCaseAlgorithms.rows()) {
      if (!isFaceTurns(row[1])) {
        continue;
      }
      run++;
      int step = row[0].startsWith("oll_") ? CFOPStepDetector.OLL : CFOPStepDetector.PLL;
      List<String> named = names(play(row[1], row[1]), step);
      if (named.contains(PLL_RESTART) || named.contains(OLL_RESTART)) {
        wrong.add(row[0] + " | " + row[1] + " -> " + named);
      }
    }
    assertTrue("too few algorithms run to mean anything: " + run, run > 75);
    assertTrue("cut inside a real algorithm: " + wrong, wrong.isEmpty());
  }

  /** Sets the case up by inverting the algorithm that solves it, then turns the moves one state at
   * a time as a cube would report them. */
  private static CFOPStepDetector play(String setup, String moves) {
    CubieCube cube = new CubieCube();
    CFOPStepDetector detector = new CFOPStepDetector();
    for (String token : inverse(setup)) {
      applyToken(cube, token);
    }
    detector.reset(new CubeState(cube.toFaceCube()), 0);

    long timestampMs = 0;
    for (String token : moves.split(" ")) {
      Face face = Face.valueOf(token.substring(0, 1));
      boolean prime = token.endsWith("'");
      for (int i = 0; i < (token.endsWith("2") ? 2 : 1); i++) {
        cube.applyMove(face, prime);
        timestampMs += 100;
        detector.onState(new CubeState(cube.toFaceCube()), new CubeMove(face, prime, timestampMs));
      }
    }
    return detector;
  }

  private static List<String> names(CFOPStepDetector detector, int step) {
    List<String> named = new ArrayList<>();
    for (int part = 0; part < detector.subStepCount(step); part++) {
      named.add(detector.subStepName(step, part));
    }
    return named;
  }

  /** Quarter turns, which is what the detector is fed and what the timestamps count. */
  private static int turnCount(String algorithm) {
    int turns = 0;
    for (String token : algorithm.split(" ")) {
      turns += token.endsWith("2") ? 2 : 1;
    }
    return turns;
  }

  private static boolean isFaceTurns(String algorithm) {
    for (String token : algorithm.split(" ")) {
      if ("UDFBLR".indexOf(token.charAt(0)) < 0) {
        return false;
      }
    }
    return true;
  }

  private static List<String> inverse(String algorithm) {
    String[] tokens = algorithm.split(" ");
    List<String> inverted = new ArrayList<>();
    for (int i = tokens.length - 1; i >= 0; i--) {
      String token = tokens[i];
      if (token.endsWith("2")) {
        inverted.add(token);
      } else if (token.endsWith("'")) {
        inverted.add(token.substring(0, 1));
      } else {
        inverted.add(token + "'");
      }
    }
    return inverted;
  }

  private static void applyToken(CubieCube cube, String token) {
    Face face = Face.valueOf(token.substring(0, 1));
    for (int i = 0; i < (token.endsWith("2") ? 2 : 1); i++) {
      cube.applyMove(face, token.endsWith("'"));
    }
  }
}
