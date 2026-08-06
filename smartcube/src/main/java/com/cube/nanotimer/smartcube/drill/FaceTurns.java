package com.cube.nanotimer.smartcube.drill;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.Face;

/**
 * Applies a scramble written in face turns to a cube.
 *
 * <p>Face turns only, which is what both scramble sources a drill uses write: a last layer case has
 * to come out that way or its layer would not be on top, and the app's 3x3 scrambles never held
 * anything else. A wide turn or a rotation is not silently half-read here; it is not accepted.
 */
final class FaceTurns {

  private FaceTurns() {
  }

  /** @throws IllegalArgumentException if a token is not a face turn */
  static void apply(CubieCube cube, String scramble) {
    if (scramble == null) {
      return;
    }
    for (String token : scramble.trim().split("\\s+")) {
      if (token.isEmpty()) {
        continue;
      }
      Face face;
      try {
        face = Face.valueOf(token.substring(0, 1));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Not a face turn: '" + token + "'");
      }
      boolean prime = token.endsWith("'");
      int quarters = token.indexOf('2') >= 0 ? 2 : 1;
      for (int quarter = 0; quarter < quarters; quarter++) {
        cube.applyMove(face, prime);
      }
    }
  }
}
