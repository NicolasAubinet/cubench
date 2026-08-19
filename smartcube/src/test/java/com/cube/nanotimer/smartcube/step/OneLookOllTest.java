package com.cube.nanotimer.smartcube.step;

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
   * Two algorithms written as one, which is what the table's own rows for these cases are: a corner
   * algorithm and then an edge one, with the layers standing in between. The solve holds nothing
   * that tells them from a solver taking two looks, and reading them as two is the honest answer.
   * Two of the 48 face-turn OLL rows are like this.
   */
  private static final String[] COMPOSITES = {
    "R' U' R U' R' U2 R F R U R' U' F'",
    "R U R' U R U2 R' F R U R' U' F'",
  };

  private final List<String> wrong = new ArrayList<>();
  private int run;

  @Test
  public void readsAOneLookOllAsTheOneAlgorithmItWas() {
    for (String[] row : LastLayerCaseAlgorithms.rows()) {
      if (!row[0].startsWith("oll_") || !isFaceTurns(row[1]) || isComposite(row[1])) {
        continue;
      }
      run++;
      check(row[0], row[1]);
    }
    assertTrue("too few algorithms run to mean anything: " + run, run > 40);
    assertTrue("one-look OLLs not read as one algorithm: " + wrong, wrong.isEmpty());
  }

  /** The composites read as two, which is right: the solve holds nothing that says otherwise. */
  @Test
  public void readsAnAlgorithmWrittenAsTwoAsTwo() {
    for (String composite : COMPOSITES) {
      CFOPStepDetector detector = play(composite);
      if (detector.subStepCount(CFOPStepDetector.OLL) != 2) {
        wrong.add(composite + " -> " + names(detector));
      }
    }
    assertTrue("not read as two algorithms: " + wrong, wrong.isEmpty());
  }

  /** The one algorithm names the case the step was handed: it is the case it solved. */
  private void check(String code, String algorithm) {
    CFOPStepDetector detector = play(algorithm);
    String expected = "ollalg_" + code.substring(code.indexOf('_') + 1);
    if (detector.subStepCount(CFOPStepDetector.OLL) != 1
        || !expected.equals(detector.subStepName(CFOPStepDetector.OLL, 0))) {
      wrong.add(code + " | " + algorithm + " -> " + names(detector));
    }
  }

  /** The algorithm turned on the case it solves, one state at a time as a cube would report it. */
  private static CFOPStepDetector play(String algorithm) {
    CubieCube cube = new CubieCube();
    CFOPStepDetector detector = new CFOPStepDetector();
    for (String token : inverse(algorithm)) { // the case is the algorithm undone
      applyToken(cube, token);
    }
    detector.reset(new CubeState(cube.toFaceCube()), 0);

    long timestampMs = 0;
    for (String token : algorithm.split(" ")) {
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

  private static boolean isComposite(String algorithm) {
    for (String composite : COMPOSITES) {
      if (composite.equals(algorithm)) {
        return true;
      }
    }
    return false;
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
