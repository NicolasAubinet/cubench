package com.cube.nanotimer.smartcube.step;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A scramble that leaves a named last-layer case, for drilling one case rather than whichever the
 * cube happens to hand out. The case is named the way a solve names it — {@code "oll_21"},
 * {@code "pll_ga"} — so a weakness read out of a history can be practised by handing that same code
 * straight back.
 *
 * <p>A scramble is the case's algorithm undone, wrapped in a turn of the last layer at either end.
 * Those two turns are the whole of the randomness, and they are enough: between them they reach every
 * state the case has. Turning the cube round would add nothing, since a last-layer case moves only
 * last-layer pieces — so holding the cube a quarter turn further round leaves the same state a turn
 * of the layer already gives. The scramble is meant for a solved cube.
 */
public final class LastLayerScrambles {

  /** Step codes, as {@link CFOPStepDetector} writes them. Pinned by the test. */
  private static final String ORIENTATION_STEP = "oll";
  private static final String PERMUTATION_STEP = "pll";

  private LastLayerScrambles() {
  }

  /** Every case a scramble can be asked for, under the codes a solve is recorded with. */
  public static List<String> cases() {
    List<String> cases = new ArrayList<String>();
    for (String[] row : LastLayerAlgorithms.ORIENTATIONS) {
      cases.add(ORIENTATION_STEP + "_" + row[0]);
    }
    for (String[] row : LastLayerAlgorithms.PERMUTATIONS) {
      cases.add(PERMUTATION_STEP + "_" + row[0]);
    }
    return Collections.unmodifiableList(cases);
  }

  /**
   * A scramble leaving the given case, or null if that is not a case there is one for. Since it is
   * the case's own algorithm undone, executing that algorithm ends on a solved cube: an OLL is
   * followed by a skip rather than by a permutation to solve.
   *
   * @param caseCode the case as a solve records it, {@code "oll_21"} or {@code "pll_ga"}
   */
  public static String forCase(String caseCode, Random random) {
    return forCase(caseCode, random.nextInt(4), random.nextInt(4));
  }

  /** The scramble with its two alignment turns chosen rather than drawn. */
  static String forCase(String caseCode, int beforeQuarters, int afterQuarters) {
    String algorithm = algorithmFor(caseCode);
    if (algorithm == null) {
      return null;
    }
    List<Move> moves = new ArrayList<Move>();
    alignment(moves, beforeQuarters);
    moves.addAll(upright(inverse(parse(algorithm))));
    alignment(moves, afterQuarters);
    return format(merged(moves));
  }

  private static String algorithmFor(String caseCode) {
    int split = caseCode == null ? -1 : caseCode.indexOf('_');
    if (split < 0) {
      return null;
    }
    String step = caseCode.substring(0, split);
    String name = caseCode.substring(split + 1);
    if (ORIENTATION_STEP.equals(step)) {
      return LastLayerAlgorithms.algorithm(LastLayerAlgorithms.ORIENTATIONS, name);
    }
    if (PERMUTATION_STEP.equals(step)) {
      return LastLayerAlgorithms.algorithm(LastLayerAlgorithms.PERMUTATIONS, name);
    }
    return null;
  }

  private static void alignment(List<Move> moves, int quarters) {
    if (quarters != 0) {
      moves.add(new Move(Move.OUTER, Cubies.U, quarters));
    }
  }

  private static List<Move> parse(String algorithm) {
    List<Move> moves = new ArrayList<Move>();
    for (String token : algorithm.trim().split("\\s+")) {
      if (!token.isEmpty()) {
        moves.add(Move.parse(token));
      }
    }
    return moves;
  }

  private static List<Move> inverse(List<Move> moves) {
    List<Move> inverted = new ArrayList<Move>();
    for (int i = moves.size() - 1; i >= 0; i--) {
      inverted.add(moves.get(i).reversed());
    }
    return inverted;
  }

  /**
   * The same turns written as face turns of a cube standing still, which is what a scramble has to
   * be: only face turns leave the centres where they were, and the last layer is only on top of a
   * cube whose centres are home. A rotation is dropped and the turns after it are renamed to the
   * faces they land on, and the cube is picked up already turned by as much as the whole sequence
   * turns it, so that it ends standing where it began.
   */
  private static List<Move> upright(List<Move> moves) {
    List<Move> turns = faceTurns(moves);
    int[] at = pickedUpAs(turns);
    List<Move> upright = new ArrayList<Move>();
    for (Move move : turns) {
      if (move.kind == Move.ROTATION) {
        at = turned(at, move);
      } else {
        upright.add(new Move(move.kind, at[move.face], move.quarters));
      }
    }
    return upright;
  }

  /**
   * Wide turns and slices rewritten as face turns and rotations: a wide turn is the whole cube turned
   * with the far layer put back, and a slice is that with the near layer put back too.
   */
  private static List<Move> faceTurns(List<Move> moves) {
    List<Move> turns = new ArrayList<Move>();
    for (Move move : moves) {
      if (move.kind == Move.OUTER || move.kind == Move.ROTATION) {
        turns.add(move);
        continue;
      }
      turns.add(new Move(Move.ROTATION, move.face, move.quarters));
      turns.add(new Move(Move.OUTER, Cubies.opposite(move.face), move.quarters));
      if (move.kind == Move.SLICE) {
        turns.add(new Move(Move.OUTER, move.face, 4 - move.quarters));
      }
    }
    return turns;
  }

  /** How far the cube has to be turned before the first move for the last one to leave it upright. */
  private static int[] pickedUpAs(List<Move> turns) {
    int[] at = {Cubies.U, Cubies.R, Cubies.F, Cubies.D, Cubies.L, Cubies.B};
    for (Move move : turns) {
      if (move.kind == Move.ROTATION) {
        at = turned(at, move);
      }
    }
    int[] pickedUp = new int[at.length];
    for (int face = 0; face < at.length; face++) {
      pickedUp[at[face]] = face;
    }
    return pickedUp;
  }

  /** Which face sits where once the cube is turned: whatever is at a place moves on to the next. */
  private static int[] turned(int[] at, Move rotation) {
    int[] cycle = Move.ROTATION_CYCLES[rotation.axis()];
    int[] moved = at.clone();
    for (int quarter = 0; quarter < rotation.quartersAboutAxis(); quarter++) {
      int[] before = moved.clone();
      for (int face = 0; face < 6; face++) {
        moved[cycle[face]] = before[face];
      }
    }
    return moved;
  }

  /** Turns of one layer run together, and drop out entirely when they come to nothing. */
  private static List<Move> merged(List<Move> moves) {
    List<Move> merged = new ArrayList<Move>();
    for (Move move : moves) {
      Move current = move;
      while (current != null && !merged.isEmpty()
          && merged.get(merged.size() - 1).sameLayer(current)) {
        int quarters = (merged.remove(merged.size() - 1).quarters + current.quarters) % 4;
        current = quarters == 0 ? null : new Move(current.kind, current.face, quarters);
      }
      if (current != null) {
        merged.add(current);
      }
    }
    return merged;
  }

  private static String format(List<Move> moves) {
    StringBuilder scramble = new StringBuilder();
    for (Move move : moves) {
      if (scramble.length() > 0) {
        scramble.append(' ');
      }
      scramble.append(move);
    }
    return scramble.toString();
  }

  /**
   * One turn, held as the face it turns about rather than as its letter, so that renaming it to
   * another face is all a rotation has to do to it. A slice or a rotation is read against the face it
   * follows: {@code M} follows L, {@code E} follows D, {@code S} follows F, and {@code x}, {@code y}
   * and {@code z} follow R, U and F. Every kind can be read; only face turns are ever written, since
   * they are the only ones a scramble is allowed to be made of.
   */
  private static final class Move {

    static final int OUTER = 0, WIDE = 1, SLICE = 2, ROTATION = 3;

    /** Where each face lands under a quarter turn of the whole cube, one row per axis. */
    static final int[][] ROTATION_CYCLES = {
      {Cubies.B, Cubies.R, Cubies.U, Cubies.F, Cubies.L, Cubies.D}, // x
      {Cubies.U, Cubies.F, Cubies.L, Cubies.D, Cubies.B, Cubies.R}, // y
      {Cubies.R, Cubies.D, Cubies.F, Cubies.L, Cubies.U, Cubies.B}, // z
    };

    private static final String FACE_LETTERS = "URFDLB";

    final int kind;
    final int face;
    final int quarters;

    Move(int kind, int face, int quarters) {
      this.kind = kind;
      this.face = face;
      this.quarters = quarters;
    }

    static Move parse(String token) {
      int quarters = token.endsWith("'") ? 3 : token.indexOf('2') >= 0 ? 2 : 1;
      char letter = token.charAt(0);
      int face = FACE_LETTERS.indexOf(Character.toUpperCase(letter));
      if (face >= 0) {
        boolean wide = Character.isLowerCase(letter) || token.indexOf('w') > 0;
        return new Move(wide ? WIDE : OUTER, face, quarters);
      }
      switch (letter) {
        case 'M': return new Move(SLICE, Cubies.L, quarters);
        case 'E': return new Move(SLICE, Cubies.D, quarters);
        case 'S': return new Move(SLICE, Cubies.F, quarters);
        case 'x': return new Move(ROTATION, Cubies.R, quarters);
        case 'y': return new Move(ROTATION, Cubies.U, quarters);
        case 'z': return new Move(ROTATION, Cubies.F, quarters);
        default: throw new IllegalArgumentException("Unknown turn: " + token);
      }
    }

    Move reversed() {
      return new Move(kind, face, 4 - quarters);
    }

    boolean sameLayer(Move other) {
      return kind == other.kind && face == other.face;
    }

    /** x, y or z: the axis the turn lies on, whichever of its two faces it is read against. */
    int axis() {
      return face == Cubies.R || face == Cubies.L ? 0 : face == Cubies.U || face == Cubies.D ? 1 : 2;
    }

    /** As far as the axis turns, which is the other way round when read against its far end. */
    int quartersAboutAxis() {
      boolean near = face == Cubies.R || face == Cubies.U || face == Cubies.F;
      return near ? quarters : 4 - quarters;
    }

    @Override
    public String toString() {
      return FACE_LETTERS.charAt(face) + (quarters == 2 ? "2" : quarters == 3 ? "'" : "");
    }
  }
}
