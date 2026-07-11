package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks progress through a 3x3 scramble by matching the live cube state against the
 * scramble's per-move target states (computed from a solved cube). State-based, so it is
 * robust to double-turn direction, regrips and undo/redo. Pure and unit-testable — no
 * Android or BLE.
 */
public class ScrambleFollower {

  // Facelets after 0..n scramble moves (index 0 = solved, index n = fully scrambled).
  private final List<String> targetStates = new ArrayList<>();
  private int doneCount;
  private boolean wrong;

  public ScrambleFollower(String[] scramble) {
    CubieCube cube = new CubieCube();
    targetStates.add(cube.toFaceCube());
    for (String token : scramble) {
      applyToken(cube, token);
      targetStates.add(cube.toFaceCube());
    }
  }

  private static void applyToken(CubieCube cube, String token) {
    if (token == null || token.trim().isEmpty()) {
      return;
    }
    token = token.trim();
    Face face = Face.valueOf(token.substring(0, 1));
    String modifier = token.substring(1);
    if (modifier.startsWith("'")) {
      cube.applyMove(face, true);
    } else if (modifier.startsWith("2")) {
      cube.applyMove(face, false);
      cube.applyMove(face, false);
    } else {
      cube.applyMove(face, false);
    }
  }

  /** Feed a new cube state; returns true if the progress or wrong flag changed. */
  public boolean onState(CubeState state) {
    int prevDone = doneCount;
    boolean prevWrong = wrong;
    String facelets = state.getFacelets();
    int match = -1;
    for (int k = targetStates.size() - 1; k >= 0; k--) {
      if (targetStates.get(k).equals(facelets)) {
        match = k;
        break;
      }
    }
    if (match >= 0) {
      doneCount = match;
      wrong = false;
    } else {
      wrong = true;
    }
    return doneCount != prevDone || wrong != prevWrong;
  }

  public void reset() {
    doneCount = 0;
    wrong = false;
  }

  public int getDoneCount() {
    return doneCount;
  }

  public boolean isWrong() {
    return wrong;
  }

  public int getMoveCount() {
    return targetStates.size() - 1;
  }

  public boolean isComplete() {
    return doneCount == getMoveCount();
  }
}
