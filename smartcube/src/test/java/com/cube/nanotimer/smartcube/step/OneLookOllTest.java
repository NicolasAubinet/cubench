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
 * A one-look OLL must not be reported as a two-look. The split only holds if a single algorithm
 * never orients the edges strictly before the corners on its way through — otherwise it would invent
 * a second look the solver never took. Each case here is set up by inverting its own algorithm.
 */
public class OneLookOllTest {

  /** One-look OLLs whose edges and corners are both unoriented — the ones that could split. */
  private static final String[] ONE_LOOK_OLLS = {
    "F R U R' U' F'", // OLL 45
    "F U R U' R' F'", // OLL 44
    "R U R' U' R' F R F'", // OLL 33
    "R U2 R2 F R F' U2 R' F R F'", // OLL 1
    "R U B' U' R' U R B R'", // OLL 32
    "F' U' L' U L F", // OLL 43
    "R' U' R U' R' U2 R F R U R' U' F'", // OLL 34-ish
    "R U R' U R' F R F' U2 R' F R F'", // OLL 30-ish
  };

  private final List<String> split = new ArrayList<>();

  @Test
  public void neverSplitsAOneLookOllIntoTwoLooks() {
    for (String alg : ONE_LOOK_OLLS) {
      CubieCube cube = new CubieCube();
      SolveAnalyzer analyzer = new SolveAnalyzer(new CFOPStepDetector());

      for (String token : inverse(alg)) { // the case is the algorithm undone
        applyToken(cube, token);
      }
      analyzer.start(new CubeState(cube.toFaceCube()), 0);

      long timestampMs = 0;
      for (String token : alg.split(" ")) {
        Face face = Face.valueOf(token.substring(0, 1));
        boolean prime = token.endsWith("'");
        for (int i = 0; i < (token.endsWith("2") ? 2 : 1); i++) {
          cube.applyMove(face, prime);
          timestampMs += 100;
          analyzer.onMove(new CubeMove(face, prime, timestampMs));
          analyzer.onState(new CubeState(cube.toFaceCube()));
        }
      }

      List<StepTime> steps = analyzer.getStepTimes();
      StepTime oll = steps.get(CFOPStepDetector.OLL);
      if (!oll.getSubSteps().isEmpty()) {
        split.add(alg + " -> " + oll.getSubSteps());
      }
    }
    assertTrue("one-look OLLs reported as two-look: " + split, split.isEmpty());
  }

  private static List<String> inverse(String alg) {
    String[] tokens = alg.split(" ");
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
