package com.cube.nanotimer.smartcube.step;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.Face;
import java.util.ArrayList;
import java.util.List;

/**
 * How many algorithms a stored OLL, PLL or CMLL drill rep was turned in, read off its scramble and
 * turns the way a solve's last layer step is: a case put in with two looks is not known, in a drill
 * as in a solve (owner, 2026-11-17). The halves of a listed algorithm are one, as in a solve. Moves
 * that came back to the case they left are a fumble, not a look, so they count for nothing.
 */
public final class RepAlgorithms {

  private static final Face[] FACES = {Face.U, Face.R, Face.F, Face.D, Face.L, Face.B};

  private RepAlgorithms() {
  }

  /**
   * @param caseCode the rep's case, {@code oll_21} or {@code pll_ga}
   * @param scramble the case as dealt, in face turns
   * @param moves the rep's turns as stored, {@code "R@1069 U@2148"}
   * @return the algorithms the case was turned in, or null where the rep cannot be read: not a last
   *     layer case, no single cross face to its case, or its turns do not finish it
   */
  public static Integer count(String caseCode, String scramble, String moves) {
    if (caseCode == null || scramble == null || moves == null) {
      return null;
    }
    if (CmllCases.isCase(caseCode)) {
      return cmll(scramble, moves);
    }
    boolean permutes = caseCode.startsWith("pll_");
    if (!permutes && !caseCode.startsWith("oll_")) {
      return null;
    }
    CubieCube cube = new CubieCube();
    for (String turn : scramble.trim().split("\\s+")) {
      if (!turn.isEmpty() && !apply(cube, turn)) {
        return null;
      }
    }
    String handed = cube.toFaceCube();
    int crossFace = crossFaceOf(handed);
    if (crossFace < 0) {
      return null;
    }
    AlgorithmTrack track = new AlgorithmTrack(permutes);
    track.track(handed, true, handed, 0, crossFace);
    for (String token : moves.trim().split(" +")) {
      int at = token.lastIndexOf('@');
      if (at <= 0 || faceOf(token.charAt(0)) == null) {
        continue; // a grip in brackets or a rotation: only the cube's own turns move it
      }
      long offsetMs;
      try {
        offsetMs = Long.parseLong(token.substring(at + 1));
      } catch (NumberFormatException e) {
        return null;
      }
      apply(cube, token.substring(0, at));
      String facelets = cube.toFaceCube();
      track.track(handed, AlgorithmTrack.resting(facelets, crossFace, permutes), facelets,
          offsetMs, crossFace);
    }
    if (!track.isFinished()) {
      return null;
    }
    List<String> algorithms = track.algorithms();
    if (algorithms.size() > 1
        && AlgorithmTrack.isListed(permutes, track.handedCase(crossFace), algorithms)) {
      return Integer.valueOf(1); // a fumble undone inside a listed algorithm's halves
    }
    return Integer.valueOf(algorithms.size());
  }

  /** A CMLL rep, read against its blocks with the slice anywhere: see {@link CmllCases#looks}. */
  private static Integer cmll(String scramble, String moves) {
    CubieCube cube = new CubieCube();
    for (String turn : scramble.trim().split("\\s+")) {
      if (!turn.isEmpty() && !apply(cube, turn)) {
        return null;
      }
    }
    List<String> states = new ArrayList<String>();
    states.add(cube.toFaceCube());
    for (String token : moves.trim().split(" +")) {
      int at = token.lastIndexOf('@');
      if (at > 0 && faceOf(token.charAt(0)) != null) {
        apply(cube, token.substring(0, at));
        states.add(cube.toFaceCube());
      }
    }
    return CmllCases.looks(states);
  }

  /** The one face the first two layers are built on, or -1 where none or several are. */
  private static int crossFaceOf(String facelets) {
    int found = -1;
    for (int face = 0; face < 6; face++) {
      if (AlgorithmTrack.firstTwoLayers(facelets, face)) {
        if (found >= 0) {
          return -1;
        }
        found = face;
      }
    }
    return found;
  }

  /** One face turn, {@code R}, {@code R'} or {@code R2}; false for anything else. */
  private static boolean apply(CubieCube cube, String turn) {
    Face face = faceOf(turn.charAt(0));
    if (face == null) {
      return false;
    }
    boolean prime = turn.endsWith("'");
    for (int quarter = turn.indexOf('2') > 0 ? 2 : 1; quarter > 0; quarter--) {
      cube.applyMove(face, prime);
    }
    return true;
  }

  private static Face faceOf(char letter) {
    for (Face face : FACES) {
      if (face.name().charAt(0) == letter) {
        return face;
      }
    }
    return null;
  }
}
