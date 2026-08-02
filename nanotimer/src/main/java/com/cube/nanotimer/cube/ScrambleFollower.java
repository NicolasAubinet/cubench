package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks progress through a 3x3 scramble by matching the live cube state against the scramble's
 * per-move target states (including the two mid-states of each half turn, so a double can be
 * done or undone in either direction). When the cube leaves the scramble path the deviating
 * moves are remembered so their reverse can be shown. Pure and unit-testable — no Android or BLE.
 */
public class ScrambleFollower {

  private static final class Step {
    final Face face;
    final int type; // +1 = cw, -1 = ccw, 2 = half turn

    Step(Face face, int type) {
      this.face = face;
      this.type = type;
    }
  }

  /** A face the user turned off the path, and by how many quarter turns clockwise (1, 2 or 3). */
  private static final class Deviation {
    final Face face;
    final int quarters;

    Deviation(Face face, int quarters) {
      this.face = face;
      this.quarters = quarters;
    }
  }

  private final List<Step> steps = new ArrayList<>();
  private final Map<String, Integer> fullStates = new HashMap<>(); // facelets -> tokens complete
  private final Map<String, Integer> halfStates = new HashMap<>(); // mid of a half turn -> token index
  private final CubieCube tracked = new CubieCube();
  private final Deque<Deviation> wrongMoves = new ArrayDeque<>(); // newest first: undo order

  private int doneCount;
  private boolean lost;

  public ScrambleFollower(String[] scramble) {
    CubieCube cube = new CubieCube();
    fullStates.put(cube.toFaceCube(), 0);
    for (String token : scramble) {
      Step step = parseToken(token);
      if (step == null) {
        if (token != null && !token.trim().isEmpty()) { // skipping it would track a different scramble
          throw new IllegalArgumentException("Unsupported scramble move: " + token);
        }
        continue;
      }
      int index = steps.size();
      steps.add(step);
      if (step.type == 2) {
        cube.applyMove(step.face, false);
        halfStates.put(cube.toFaceCube(), index);
        cube.applyMove(step.face, true);
        cube.applyMove(step.face, true);
        halfStates.put(cube.toFaceCube(), index);
        cube.applyMove(step.face, false);
      }
      applyStep(cube, step);
      fullStates.put(cube.toFaceCube(), index + 1);
    }
  }

  /**
   * True when every token is a plain face turn, the only notation the follower can track. Rules out
   * the slice and wide moves some scramble types append (they also move the centres, which the
   * facelet targets assume fixed).
   */
  public static boolean canFollow(String[] scramble) {
    if (scramble == null) {
      return false;
    }
    for (String token : scramble) {
      if (token != null && !token.trim().isEmpty() && parseToken(token) == null) {
        return false;
      }
    }
    return true;
  }

  private static Step parseToken(String token) {
    if (token == null || token.trim().isEmpty()) {
      return null;
    }
    token = token.trim();
    Face face = parseFace(token.substring(0, 1));
    if (face == null) {
      return null;
    }
    String modifier = token.substring(1);
    if (modifier.startsWith("'")) {
      return new Step(face, -1);
    } else if (modifier.startsWith("2")) {
      return new Step(face, 2);
    }
    return new Step(face, 1);
  }

  private static Face parseFace(String letter) {
    for (Face face : Face.values()) {
      if (face.name().equals(letter)) {
        return face;
      }
    }
    return null;
  }

  private static void applyStep(CubieCube cube, Step step) {
    if (step.type == 2) {
      cube.applyMove(step.face, false);
      cube.applyMove(step.face, false);
    } else {
      cube.applyMove(step.face, step.type < 0);
    }
  }

  /** Feed one quarter turn from the cube; returns true if the display should refresh. */
  public boolean onMove(CubeMove move) {
    tracked.applyMove(move.getFace(), move.isPrime());
    return apply(tracked.toFaceCube(), move);
  }

  /** Reconcile against an absolute state; only re-anchors when the move stream desynced. */
  public boolean onState(CubeState state) {
    String facelets = state.getFacelets();
    if (tracked.toFaceCube().equals(facelets)) {
      return false;
    }
    tracked.fromFacelet(facelets);
    return apply(facelets, null);
  }

  private boolean apply(String facelets, CubeMove move) {
    int prevDone = doneCount;
    String prevReverse = getReverseMoves();
    boolean prevLost = lost;
    Integer full = fullStates.get(facelets);
    Integer half = halfStates.get(facelets);
    if (full != null) {
      doneCount = full;
      wrongMoves.clear();
      lost = false;
    } else if (half != null) {
      doneCount = half;
      wrongMoves.clear();
      lost = false;
    } else if (move != null) {
      addWrongMove(move.getFace(), move.isPrime());
    } else {
      // The cube jumped somewhere the moves cannot account for, so the follow is worthless: the
      // wrong moves are unknown, and doneCount is a memory of a cube that is no longer this one.
      wrongMoves.clear();
      doneCount = 0;
      lost = true;
    }
    return doneCount != prevDone || lost != prevLost || !getReverseMoves().equals(prevReverse);
  }

  /**
   * Folds a deviating turn into the newest deviation on the same face, and drops that face once it
   * has come full circle. A face back where it started must leave nothing behind: kept, it would sit
   * between two turns of a face still to undo and split them, and the undo is printed off these one
   * for one, so its first move would no longer be the one that answers the user's next turn.
   */
  private void addWrongMove(Face face, boolean prime) {
    Deviation newest = wrongMoves.peek();
    int quarters = prime ? 3 : 1;
    if (newest != null && newest.face == face) {
      wrongMoves.pop();
      quarters = (newest.quarters + quarters) % 4;
    }
    if (quarters != 0) {
      wrongMoves.push(new Deviation(face, quarters));
    }
  }

  public void reset() {
    tracked.fromFacelet(CubieCube.SOLVED_FACELET);
    wrongMoves.clear();
    doneCount = 0;
    lost = false;
  }

  /** True when the cube turned up somewhere the moves cannot explain: the follow means nothing. */
  public boolean isLost() {
    return lost;
  }

  public int getDoneCount() {
    return doneCount;
  }

  public boolean isWrong() {
    return !wrongMoves.isEmpty();
  }

  /** The moves the user must execute to undo their wrong moves, e.g. "U' R2". Empty when on track. */
  public String getReverseMoves() {
    StringBuilder sb = new StringBuilder();
    for (Deviation deviation : wrongMoves) { // iterates newest first: exactly undo order
      if (sb.length() > 0) {
        sb.append(' ');
      }
      sb.append(deviation.face.name());
      int undo = 4 - deviation.quarters;
      if (undo == 2) {
        sb.append('2');
      } else if (undo == 3) {
        sb.append('\'');
      }
    }
    return sb.toString();
  }

  public int getMoveCount() {
    return steps.size();
  }

  public boolean isComplete() {
    return doneCount == steps.size() && wrongMoves.isEmpty();
  }
}
