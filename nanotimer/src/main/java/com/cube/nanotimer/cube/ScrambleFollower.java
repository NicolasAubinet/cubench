package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import com.cube.nanotimer.smartcube.step.AlgorithmForm;
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
 *
 * <p>A slice or wide token is followed as the outer turns the cube reports for it ({@code M} as
 * {@code R L'}, {@code r} as {@code L}), since the cube measures every turn against its own
 * centres. Progress still counts tokens, which is what the scramble on screen is written in.
 */
public class ScrambleFollower {

  private static final class Step {
    final Face face;
    final int quarters; // clockwise: 1, 2 or 3

    Step(Face face, int quarters) {
      this.face = face;
      this.quarters = quarters;
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

  private final Map<String, Integer> fullStates = new HashMap<>(); // facelets -> tokens complete
  private final Map<String, Integer> partStates = new HashMap<>(); // mid-token -> its index
  private final CubieCube tracked = new CubieCube();
  private final Deque<Deviation> wrongMoves = new ArrayDeque<>(); // newest first: undo order

  private int moveCount;
  private int doneCount;
  private boolean lost;

  public ScrambleFollower(String[] scramble) {
    List<List<Step>> tokens = parse(scramble);
    if (tokens == null) {
      throw new IllegalArgumentException("Unsupported scramble: " + String.join(" ", scramble));
    }
    CubieCube cube = new CubieCube();
    fullStates.put(cube.toFaceCube(), 0);
    for (List<Step> turns : tokens) {
      if (turns.isEmpty()) {
        continue; // a blank: skipping it tracks the same scramble
      }
      putPartStates(cube.toFaceCube(), turns, 0, new int[turns.size()], moveCount);
      for (Step step : turns) {
        applyStep(cube, step, step.quarters);
      }
      fullStates.put(cube.toFaceCube(), ++moveCount);
    }
  }

  /**
   * True when every token is a turn the cube can report: face turns, and the slices and wides some
   * scramble types append, read as the outer turns they are made of.
   */
  public static boolean canFollow(String[] scramble) {
    return scramble != null && parse(scramble) != null;
  }

  /** Each token's outer turns, or null if any token is not a turn the cube reports. */
  private static List<List<Step>> parse(String[] scramble) {
    String[] tokens = new String[scramble.length];
    for (int i = 0; i < tokens.length; i++) {
      // Older scrambles, still cached and in the history, write a slice lower case.
      tokens[i] = scramble[i] == null ? "" : scramble[i].trim().replace('m', 'M')
          .replace('e', 'E').replace('s', 'S');
    }
    List<List<String>> perToken;
    try {
      perToken = AlgorithmForm.perToken(tokens);
    } catch (IllegalArgumentException e) {
      return null;
    }
    List<List<Step>> parsed = new ArrayList<>();
    for (int i = 0; i < tokens.length; i++) {
      if (!tokens[i].isEmpty() && perToken.get(i).isEmpty()) {
        return null; // a rotation turns nothing the cube can see, so it could never be followed
      }
      List<Step> turns = new ArrayList<>();
      for (String turn : perToken.get(i)) {
        turns.add(parseToken(turn));
      }
      parsed.add(turns);
    }
    return parsed;
  }

  private static Step parseToken(String token) {
    Face face = Face.valueOf(token.substring(0, 1));
    String modifier = token.substring(1);
    return new Step(face, modifier.startsWith("'") ? 3 : modifier.startsWith("2") ? 2 : 1);
  }

  /** Every state part-way through one token, its faces turned in any order (they share an axis). */
  private void putPartStates(String start, List<Step> turns, int face, int[] quarters, int index) {
    if (face == turns.size()) {
      boolean untouched = true;
      boolean done = true;
      CubieCube cube = new CubieCube();
      cube.fromFacelet(start);
      for (int i = 0; i < turns.size(); i++) {
        untouched &= quarters[i] == 0;
        done &= quarters[i] == turns.get(i).quarters;
        applyStep(cube, turns.get(i), quarters[i]);
      }
      if (!untouched && !done) {
        partStates.put(cube.toFaceCube(), index);
      }
      return;
    }
    Step step = turns.get(face);
    for (int turned = 0; turned < 4; turned++) {
      if (turned == 0 || turned == step.quarters || step.quarters == 2) {
        quarters[face] = turned;
        putPartStates(start, turns, face + 1, quarters, index);
      }
    }
  }

  private static void applyStep(CubieCube cube, Step step, int quarters) {
    for (int i = 0; i < quarters; i++) {
      cube.applyMove(step.face, false);
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
    Integer part = partStates.get(facelets);
    if (full != null) {
      doneCount = full;
      wrongMoves.clear();
      lost = false;
    } else if (part != null) {
      doneCount = part;
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
    return moveCount;
  }

  public boolean isComplete() {
    return doneCount == moveCount && wrongMoves.isEmpty();
  }
}
