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
 * A one-look OLL must be read as one algorithm. The split only holds if a single algorithm never
 * passes back through a state with the first two layers standing on its way — otherwise it would
 * invent a second look the solver never took.
 *
 * <p>Run against the shipped table rather than a hand-picked list, so an algorithm someone really
 * uses is what the rule is held to. Only the rows written in face turns are run: those are the ones
 * a detector fed a cube's own turns ever sees, and a wide or slice move reaches it as the turn plus
 * the rotation it carries, which is a different thing to reconstruct and not this test's subject.
 * Each case is set up by inverting its own algorithm.
 */
public class OneLookOllTest {

  /**
   * The standard OLL 42 and 41, an Antisune or a Sune and then the T, with the layers standing in
   * between. Listed, so one algorithm however long the stop between the halves.
   */
  private static final String[] COMPOSITES = {
    "R' U' R U' R' U2 R F R U R' U' F'",
    "R U R' U R U2 R' F R U R' U' F'",
  };

  /** Where the T starts in each, after the seven turns of the Sune. */
  private static final int SECOND_HALF = 7;

  private final List<String> wrong = new ArrayList<>();
  private int run;

  @Test
  public void readsAOneLookOllAsTheOneAlgorithmItWas() {
    for (String[] row : LastLayerCaseAlgorithms.rows()) {
      if (!row[0].startsWith("oll_") || !isFaceTurns(row[1])) {
        continue;
      }
      run++;
      check(row[0], row[1]);
    }
    assertTrue("too few algorithms run to mean anything: " + run, run > 40);
    assertTrue("one-look OLLs not read as one algorithm: " + wrong, wrong.isEmpty());
  }

  @Test
  public void readsAListedCompositeAsOneAlgorithmWhateverTheStop() {
    check("oll_41", COMPOSITES[1], 350);
    check("oll_42", COMPOSITES[0], 350);
    check("oll_41", COMPOSITES[1], 2500);
    check("oll_42", COMPOSITES[0], 2500);
    assertTrue("not read as one algorithm: " + wrong, wrong.isEmpty());
  }

  @Test
  public void readsTheHalvesOfNoListedAlgorithmAsTwoLooks() {
    // Edges with F R U R' U' F', then a Sune: on OLL 40 that is two looks, not a listed algorithm.
    CFOPStepDetector detector = play("F R U R' U' F' R U2 R' U' R U' R'", 6, 700);
    assertEquals(names(detector).toString(), 2, detector.subStepCount(CFOPStepDetector.OLL));
  }

  /** The one algorithm names the case the step was handed: it is the case it solved. */
  private void check(String code, String algorithm) {
    check(code, algorithm, 0);
  }

  /** @param pauseMs added before the composite's second half, where it has one */
  private void check(String code, String algorithm, long pauseMs) {
    CFOPStepDetector detector = play(algorithm, SECOND_HALF, pauseMs);
    String expected = "ollalg_" + code.substring(code.indexOf('_') + 1);
    if (detector.subStepCount(CFOPStepDetector.OLL) != 1
        || !expected.equals(detector.subStepName(CFOPStepDetector.OLL, 0))) {
      wrong.add(code + " | " + algorithm + " -> " + names(detector));
    }
  }

  /**
   * The algorithm turned on the case it solves, one state at a time as a cube would report it, 100
   * ms a turn and {@code pauseMs} more before the token at {@code pauseAt}.
   */
  private static CFOPStepDetector play(String algorithm, int pauseAt, long pauseMs) {
    CubieCube cube = new CubieCube();
    CFOPStepDetector detector = new CFOPStepDetector();
    for (String token : inverse(algorithm)) { // the case is the algorithm undone
      applyToken(cube, token);
    }
    detector.reset(new CubeState(cube.toFaceCube()), 0);

    long timestampMs = 0;
    String[] tokens = algorithm.split(" ");
    for (int t = 0; t < tokens.length; t++) {
      String token = tokens[t];
      if (t == pauseAt) {
        timestampMs += pauseMs;
      }
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

  private static List<String> names(CFOPStepDetector detector) {
    List<String> named = new ArrayList<>();
    for (int part = 0; part < detector.subStepCount(CFOPStepDetector.OLL); part++) {
      named.add(detector.subStepName(CFOPStepDetector.OLL, part));
    }
    return named;
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
