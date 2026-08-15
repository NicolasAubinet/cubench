package com.cube.nanotimer.smartcube.cube;

import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;

/**
 * What the state a solve was stopped in earns it: nothing, a +2, or a DNF.
 *
 * <p>WCA Regulation 10e counts interfaces rather than moves. Every pair of adjacent parts
 * misaligned past the tolerance of 10f (45 degrees for an NxN) adds one move to the count: none
 * needed is solved, one is a +2, more than one is a DNF. On a 3x3 that comes down to "exactly one
 * outer face turned, by a quarter or a half", so the whole judgement is the 18 outer turns tried
 * against a solved cube. The tolerance costs nothing to honour, a cube reporting whole turns being
 * either aligned or a long way past 45 degrees.
 *
 * <p>A misplaced slice lands where the regulation puts it, on the DNF side, and for the regulation's
 * own reason: it is two misaligned interfaces, and the cube reports it as the two face turns it
 * physically is.
 *
 * <p>Read through {@link CubieCube} rather than off the facelets, so a cube whose core turned during
 * the solve still reads as solved. Being solved means every face matching its own centre, which is
 * what the cubie reading compares, and a string comparison would not.
 */
public final class StopPenalty {

  /** Mildest first, which is the order {@link #isMilderThan} reads them in. */
  public enum Type { NONE, PLUS_TWO, DNF }

  /** Also what an unreadable state earns: nothing to say about it is the safe thing to say. */
  private static final StopPenalty NO_PENALTY = new StopPenalty(Type.NONE, null);
  private static final StopPenalty DNF = new StopPenalty(Type.DNF, null);

  private final Type type;
  private final String missingMove;

  private StopPenalty(Type type, String missingMove) {
    this.type = type;
    this.missingMove = missingMove;
  }

  /** What a solve nothing was read of earns: nothing. */
  public static StopPenalty none() {
    return NO_PENALTY;
  }

  /** Judges the state a solve was left in. A state that cannot be read earns no penalty. */
  public static StopPenalty of(CubeState state) {
    CubieCube cube = new CubieCube();
    if (state == null || state.getFacelets() == null
        || !cube.fromFacelet(state.getFacelets())) {
      return NO_PENALTY;
    }
    if (cube.isSolved()) {
      return NO_PENALTY;
    }
    for (Face face : Face.values()) {
      // Four quarter turns bring the face home again, so the same cube serves for every face.
      for (int quarters = 1; quarters <= 4; quarters++) {
        cube.applyMove(face, false);
        if (quarters < 4 && cube.isSolved()) {
          return new StopPenalty(Type.PLUS_TWO, notation(face, quarters));
        }
      }
    }
    return DNF;
  }

  private static String notation(Face face, int quarters) {
    switch (quarters) {
      case 2:
        return face.name() + "2";
      case 3:
        return face.name() + "'";
      default:
        return face.name();
    }
  }

  public Type getType() {
    return type;
  }

  public boolean isNone() {
    return type == Type.NONE;
  }

  public boolean isPlusTwo() {
    return type == Type.PLUS_TWO;
  }

  /** Whether this verdict costs the solve less than the given one. */
  public boolean isMilderThan(StopPenalty other) {
    return type.ordinal() < other.type.ordinal();
  }

  public boolean isDnf() {
    return type == Type.DNF;
  }

  /** The move that would have finished the solve, in WCA notation. Null unless this is a +2. */
  public String getMissingMove() {
    return missingMove;
  }

  @Override
  public String toString() {
    return "StopPenalty(" + type + (missingMove != null ? ", " + missingMove : "") + ")";
  }
}
