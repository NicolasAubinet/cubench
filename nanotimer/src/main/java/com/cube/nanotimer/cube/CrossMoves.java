package com.cube.nanotimer.cube;

import com.cube.nanotimer.coach.CrossTurn;
import com.cube.nanotimer.scrambler.cross.CrossFace;
import com.cube.nanotimer.scrambler.cross.CrossSolvers;
import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.Face;
import com.cube.nanotimer.smartcube.step.CFOPStepDetector;
import com.cube.nanotimer.vo.SolveStep;

import java.util.ArrayList;
import java.util.List;

/**
 * A CFOP solve's cross set against the fewest moves it could take on its own face.
 *
 * <p>Moves are counted as {@code CrossDrillSession} counts them: the cube's face turns in HTM, an
 * identical pair of quarter turns folded into the half turn it was, and nothing else folded, so
 * {@code U U'} is two moves and a slice two face turns. The replay never rotates the cube, so the
 * face turns, the scramble and the optimal all share the cube's own frame.
 */
public final class CrossMoves {

  private static final String CROSS = "cross";

  /** One table per face, built on first use and kept: every reading of a window asks again. */
  private static final CrossSolvers SOLVERS = new CrossSolvers();

  private CrossMoves() {
  }

  /**
   * @param steps the solve's steps from its start, cross first
   * @return the cross, or null where the solve has no cross and F2L to read one from
   */
  static CrossTurn read(long solveId, String scramble, String storedMoves, List<SolveStep> steps) {
    Replayed replayed = replay(scramble, storedMoves, steps);
    if (replayed == null || replayed.face == null) {
      return null;
    }
    int optimal = SOLVERS.optimalLength(CrossFace.valueOf(replayed.face.name()), scramble);
    return new CrossTurn(solveId, optimal, htm(replayed.crossTurns),
        CFOPStepDetector.slotsDoneCount(replayed.atCross, replayed.face));
  }

  /** The face the cross was built on, or null where the solve has no cross and F2L to read. */
  static Face face(String scramble, String storedMoves, List<SolveStep> steps) {
    Replayed replayed = replay(scramble, storedMoves, steps);
    return replayed == null ? null : replayed.face;
  }

  private static Replayed replay(String scramble, String storedMoves, List<SolveStep> steps) {
    if (steps.size() < 2 || !CROSS.equals(steps.get(0).getName())
        || !SolveReading.F2L.equals(steps.get(1).getName())) {
      return null;
    }
    long crossEndMs = steps.get(0).getTotalMs();
    long f2lEndMs = crossEndMs + steps.get(1).getTotalMs();
    CubieCube cube = new CubieCube();
    cube.fromFacelet(CubieCube.SOLVED_FACELET);
    for (String token : scramble.trim().split("\\s+")) {
      apply(cube, token);
    }
    List<String> crossTurns = new ArrayList<String>();
    String atCross = null;
    for (SolveMovesFormat.Move move : SolveMovesFormat.parse(storedMoves)) {
      String notation = move.getNotation();
      if (SolveMovesFormat.isRotation(notation)) {
        continue;
      }
      if (move.getOffsetMs() > f2lEndMs) {
        break;
      }
      if (move.getOffsetMs() <= crossEndMs) {
        crossTurns.add(notation);
      } else if (atCross == null) {
        atCross = cube.toFaceCube();
      }
      apply(cube, notation);
    }
    if (atCross == null) {
      atCross = cube.toFaceCube(); // F2L turned nothing: the cross finished it
    }
    return new Replayed(crossTurns, atCross, crossFace(atCross, cube.toFaceCube()));
  }

  /** A solve replayed to the end of F2L. */
  private static final class Replayed {
    final List<String> crossTurns;
    final String atCross;
    final Face face;

    Replayed(List<String> crossTurns, String atCross, Face face) {
      this.crossTurns = crossTurns;
      this.atCross = atCross;
      this.face = face;
    }
  }

  /** The face whose cross was done as the cross ended and whose two layers were done after F2L. */
  private static Face crossFace(String atCross, String afterF2l) {
    for (Face face : Face.values()) {
      if (CFOPStepDetector.isCrossDone(atCross, face)
          && CFOPStepDetector.slotsDoneCount(afterF2l, face) == 4
          && CFOPStepDetector.isCrossDone(afterF2l, face)) {
        return face;
      }
    }
    return null;
  }

  /** Face turns in HTM: a written half turn is one, and so is an identical pair of quarter turns. */
  static int htm(List<String> turns) {
    int count = 0;
    for (int i = 0; i < turns.size(); i++) {
      String turn = turns.get(i);
      if (!turn.endsWith("2") && i + 1 < turns.size() && turn.equals(turns.get(i + 1))) {
        i++;
      }
      count++;
    }
    return count;
  }

  private static void apply(CubieCube cube, String token) {
    Face face = Face.valueOf(token.substring(0, 1));
    boolean prime = token.endsWith("'");
    for (int i = 0; i < (token.endsWith("2") ? 2 : 1); i++) {
      cube.applyMove(face, prime);
    }
  }
}
