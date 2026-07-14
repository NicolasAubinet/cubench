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

  private static final class Turn {
    final Face face;
    final boolean prime;

    Turn(Face face, boolean prime) {
      this.face = face;
      this.prime = prime;
    }
  }

  private final List<Step> steps = new ArrayList<>();
  private final Map<String, Integer> fullStates = new HashMap<>(); // facelets -> tokens complete
  private final Map<String, Integer> halfStates = new HashMap<>(); // mid of a half turn -> token index
  private final CubieCube tracked = new CubieCube();
  private final Deque<Turn> wrongMoves = new ArrayDeque<>(); // deviating quarter turns to undo

  private int doneCount;
  private boolean lost;

  public ScrambleFollower(String[] scramble) {
    CubieCube cube = new CubieCube();
    fullStates.put(cube.toFaceCube(), 0);
    for (String token : scramble) {
      Step step = parseToken(token);
      if (step == null) {
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

  private static Step parseToken(String token) {
    if (token == null || token.trim().isEmpty()) {
      return null;
    }
    token = token.trim();
    Face face = Face.valueOf(token.substring(0, 1));
    String modifier = token.substring(1);
    if (modifier.startsWith("'")) {
      return new Step(face, -1);
    } else if (modifier.startsWith("2")) {
      return new Step(face, 2);
    }
    return new Step(face, 1);
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
    return apply(tracked.toFaceCube(), new Turn(move.getFace(), move.isPrime()));
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

  private boolean apply(String facelets, Turn move) {
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
      if (!wrongMoves.isEmpty() && isInverse(wrongMoves.peek(), move)) {
        wrongMoves.pop();
      } else {
        wrongMoves.push(move);
      }
    } else {
      // The cube jumped somewhere the moves cannot account for, so the follow is worthless: the
      // wrong moves are unknown, and doneCount is a memory of a cube that is no longer this one.
      wrongMoves.clear();
      doneCount = 0;
      lost = true;
    }
    return doneCount != prevDone || lost != prevLost || !getReverseMoves().equals(prevReverse);
  }

  private static boolean isInverse(Turn a, Turn b) {
    return a.face == b.face && a.prime != b.prime;
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
    List<Turn> undo = new ArrayList<>();
    for (Turn t : wrongMoves) { // iterates most-recent first: exactly undo order
      undo.add(new Turn(t.face, !t.prime));
    }
    return mergeNotation(undo);
  }

  private static String mergeNotation(List<Turn> turns) {
    StringBuilder sb = new StringBuilder();
    int i = 0;
    while (i < turns.size()) {
      Face face = turns.get(i).face;
      int net = 0;
      while (i < turns.size() && turns.get(i).face == face) {
        net += turns.get(i).prime ? -1 : 1;
        i++;
      }
      net = ((net % 4) + 4) % 4;
      if (net == 0) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append(' ');
      }
      sb.append(face.name());
      if (net == 2) {
        sb.append('2');
      } else if (net == 3) {
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
