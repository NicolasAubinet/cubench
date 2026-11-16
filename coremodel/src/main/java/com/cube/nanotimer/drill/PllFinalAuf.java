package com.cube.nanotimer.drill;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.Face;

import java.util.ArrayList;
import java.util.List;

/**
 * A stored PLL drill rep's execution without its final AUF, for the reps timed before a case was
 * timed without it. The rep keeps no record of which face its layer was dealt onto, so that is read
 * from the case as dealt: after the OLL every wrong sticker sits in the ring a turn of the layer
 * moves, and in no other face's ring.
 */
public final class PllFinalAuf {

  private static final Face[] FACES = {Face.U, Face.R, Face.F, Face.D, Face.L, Face.B};

  private PllFinalAuf() {
  }

  /**
   * @param scramble the case as dealt, in face turns
   * @param moves the rep's turns as stored, {@code "D@1069 L@2148"}, offsets from the case going up
   * @param recognitionMs the rep's recognition, which ends where its algorithm starts
   * @return the execution from the algorithm's first turn to its last, or null where the rep closed
   *     on no AUF or cannot be read: not solved by its turns, or no single layer to its case
   */
  public static Long executionMs(String scramble, String moves, long recognitionMs) {
    CubieCube cube = new CubieCube();
    if (!apply(cube, scramble)) {
      return null;
    }
    Face layer = layerOf(cube);
    if (layer == null) {
      return null;
    }
    List<Face> faces = new ArrayList<Face>();
    List<Long> offsets = new ArrayList<Long>();
    for (String token : moves == null ? new String[0] : moves.trim().split(" +")) {
      int at = token.lastIndexOf('@');
      if (at <= 0) {
        continue; // a grip in brackets, or anything else that is not a turn
      }
      Face face = faceOf(token.substring(0, at));
      if (face == null) {
        continue; // a rotation: the cube's own turns are all that move it
      }
      try {
        offsets.add(Long.valueOf(token.substring(at + 1)));
      } catch (NumberFormatException e) {
        return null;
      }
      faces.add(face);
      apply(cube, token.substring(0, at));
    }
    if (!cube.isSolved()) {
      return null;
    }
    int last = faces.size() - 1;
    while (last >= 0 && faces.get(last) == layer) {
      last--;
    }
    if (last < 0 || last == faces.size() - 1) {
      return null;
    }
    long execution = offsets.get(last) - recognitionMs;
    return execution < 0 ? null : Long.valueOf(execution);
  }

  /** The face whose turn moves every wrong sticker, or null where none or several do. */
  static Face layerOf(CubieCube cube) {
    String facelets = cube.toFaceCube();
    Face found = null;
    for (Face face : FACES) {
      CubieCube turned = new CubieCube();
      turned.applyMove(face, false);
      String ring = turned.toFaceCube();
      boolean holds = true;
      boolean any = false;
      for (int i = 0; i < facelets.length() && holds; i++) {
        if (facelets.charAt(i) != CubieCube.SOLVED_FACELET.charAt(i)) {
          any = true;
          holds = ring.charAt(i) != CubieCube.SOLVED_FACELET.charAt(i);
        }
      }
      if (holds && any) {
        if (found != null) {
          return null;
        }
        found = face;
      }
    }
    return found;
  }

  private static boolean apply(CubieCube cube, String scramble) {
    if (scramble == null) {
      return false;
    }
    for (String token : scramble.trim().split("\\s+")) {
      if (token.isEmpty()) {
        continue;
      }
      Face face = faceOf(token);
      if (face == null) {
        return false;
      }
      boolean prime = token.endsWith("'");
      for (int quarter = token.indexOf('2') >= 0 ? 2 : 1; quarter > 0; quarter--) {
        cube.applyMove(face, prime);
      }
    }
    return true;
  }

  private static Face faceOf(String notation) {
    if (notation.isEmpty()) {
      return null;
    }
    switch (notation.charAt(0)) {
      case 'U': return Face.U;
      case 'R': return Face.R;
      case 'F': return Face.F;
      case 'D': return Face.D;
      case 'L': return Face.L;
      case 'B': return Face.B;
      default: return null;
    }
  }
}
