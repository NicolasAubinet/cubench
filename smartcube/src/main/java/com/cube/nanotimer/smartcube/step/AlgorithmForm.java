package com.cube.nanotimer.smartcube.step;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The form two algorithms have to be in before they can be told apart: face turns only, every one of
 * them named from the frame the cube was picked up in, with the moves that undid each other folded
 * away.
 *
 * <p>Nothing else lets an execution be recognised. A solver turns a listed algorithm with a rotation
 * in front of it, a wide where the table writes a slice, and an alignment turn absorbed into the
 * first move; what a solve records is then written in the solver's own frame. So the string a solve
 * holds rarely equals the string the table holds even when the two are the same algorithm, and
 * comparing them as text answers a question nobody asked.
 *
 * <p><b>A whole-cube rotation is not written down.</b> It is folded into which face each later turn
 * names, which is the whole of what makes the form comparable: two solvers who turn the same
 * algorithm from different grips come out with the same list. The cube is left standing somewhere
 * else, and that is deliberately not kept, since a rotation moves no piece against any other.
 *
 * <p>A wide and a slice are then the same thing said once. Every turn carries some set of the three
 * layers on an axis, so it is the rotation that carries all three, minus the ones it should not have
 * carried, and both come out as the outer turns they are made of ({@code Rw} as {@code x L},
 * {@code M} as {@code x' R L'}).
 */
public final class AlgorithmForm {

  /** The face on the positive side of each axis, in the axis order {@link Notation} turns about. */
  private static final char[] PLUS = {'R', 'U', 'F'};
  private static final char[] MINUS = {'L', 'D', 'B'};

  /** Where a positive quarter turn about each axis carries the four faces it moves. */
  private static final char[][] CYCLES = {
    {'F', 'U', 'B', 'D'}, // x, the way R turns: F goes up
    {'F', 'L', 'B', 'R'}, // y, the way U turns: F goes left
    {'U', 'R', 'D', 'L'}, // z, the way F turns: U goes right
  };

  private static final char[] FACES = {'U', 'R', 'F', 'D', 'L', 'B'};

  /** The face a turn of the last layer is written on once the cube is stood the way a case is drawn. */
  private static final char LAYER = 'U';

  private AlgorithmForm() {
  }

  /**
   * @param algorithm any notation {@link Notation} reads: faces, wides, slices and rotations
   * @throws IllegalArgumentException if a token is not a turn
   */
  static List<String> of(String algorithm) {
    List<String> turns = new ArrayList<String>();
    char[] named = FACES.clone(); // which face of the starting frame each letter now names
    for (String token : algorithm.trim().split("\\s+")) {
      if (!token.isEmpty()) {
        named = read(token, named, turns);
      }
    }
    return folded(turns);
  }

  /**
   * One string naming what was turned, for telling two executions of a case apart. Null for notation
   * nothing can read, which is not the same answer as turning nothing.
   */
  public static String key(String algorithm) {
    try {
      List<String> turns = withoutAlignment(of(algorithm));
      StringBuilder key = new StringBuilder();
      for (String turn : turns) {
        key.append(key.length() == 0 ? "" : " ").append(turn);
      }
      return key.toString();
    } catch (RuntimeException e) {
      return null;
    }
  }

  /**
   * One string naming what was turned, the same however the cube was held. Null for notation
   * nothing can read.
   *
   * <p>{@link #key} is enough to tell two executions of one case apart only while they were turned
   * from the same frame. A solver regrips between solves, so the same algorithm comes back written
   * two ways, and grouping on the plain key splits one answer into two. The smallest of the 24 is
   * an arbitrary representative and deliberately so: what matters is that the same turning always
   * picks the same one, not which one it picks.
   *
   * <p><b>The alignment comes off before the cube is stood up, not after.</b> Standing it up moves
   * the layer's turns onto another face, so stripping afterwards takes off whatever happens to be
   * named {@code U} in that grip, which is not the alignment and not the same thing in each of the
   * 24. Stripping first is what makes the 24 a set the same turning always maps onto itself.
   * ⚠️ It still assumes the layer was up in the frame the moves were written in, which is true of
   * the table and not of an execution; {@link #keyAsDrawn} is the one that finds the frame first,
   * and this is its fallback.
   */
  public static String keyFromAnyGrip(String algorithm) {
    List<String> turns;
    try {
      turns = withoutAlignment(of(algorithm));
    } catch (RuntimeException e) {
      return null;
    }
    String smallest = null;
    for (char[] grip : grips()) {
      String stood = written(conjugatedBy(turns, grip));
      if (smallest == null || stood.compareTo(smallest) < 0) {
        smallest = stood;
      }
    }
    return smallest;
  }

  /** The moves from the first of them that turns a layer: what comes before is how it was picked up. */
  static String withoutOpeningGrip(String moves) {
    String[] tokens = moves.trim().split("\\s+");
    int from = 0;
    while (from < tokens.length && "xyz".indexOf(tokens[from].charAt(0)) >= 0) {
      from++;
    }
    StringBuilder turned = new StringBuilder();
    for (int i = from; i < tokens.length; i++) {
      turned.append(turned.length() == 0 ? "" : " ").append(tokens[i]);
    }
    return turned.toString();
  }

  static String written(List<String> turns) {
    StringBuilder written = new StringBuilder();
    for (String turn : turns) {
      written.append(written.length() == 0 ? "" : " ").append(turn);
    }
    return written.toString();
  }

  /** The same turning done with the cube stood some other way, which is the same algorithm. */
  static List<String> conjugatedBy(List<String> turns, char[] rotated) {
    List<String> conjugated = new ArrayList<String>(turns.size());
    for (String turn : turns) {
      conjugated.add(rotated[indexOf(turn.charAt(0))] + turn.substring(1));
    }
    return folded(conjugated); // renaming can put two opposite faces the other way round
  }

  /**
   * The turns of the last layer off both ends. They align the layer to be read, or leave it where the
   * next case wants it, so an algorithm is the same algorithm with or without them.
   */
  static List<String> withoutAlignment(List<String> turns) {
    int from = 0;
    int to = turns.size();
    while (from < to && turns.get(from).charAt(0) == LAYER) {
      from++;
    }
    while (to > from && turns.get(to - 1).charAt(0) == LAYER) {
      to--;
    }
    return new ArrayList<String>(turns.subList(from, to));
  }

  /**
   * An algorithm as two are compared: its form with the alignment off both ends, or nothing at all
   * for notation that cannot be read, which nothing then matches.
   */
  static List<String> comparable(String algorithm) {
    try {
      return withoutAlignment(of(algorithm));
    } catch (RuntimeException e) {
      return new ArrayList<String>();
    }
  }

  /**
   * The first of the forms the moves turn, from whatever grip stands the cube the way the case is
   * drawn, or -1. The cube as it was held is tried first, and an empty execution matches nothing.
   *
   * <p><b>Only a grip the case is drawn in counts.</b> Stood any other way, a face that is not the
   * last layer ends up named {@code U}, and the alignment coming off both ends then takes a real turn
   * of the algorithm with it, so two different algorithms can read as one.
   */
  static int indexOfTurning(List<List<String>> forms, String moves, Drawn drawn) {
    List<String> turns;
    try {
      turns = of(moves);
    } catch (RuntimeException e) {
      return -1; // notation nothing can read is no algorithm of anything
    }
    for (char[] grip : grips()) {
      List<String> stood = conjugatedBy(turns, grip);
      List<String> executed = withoutAlignment(stood);
      if (executed.isEmpty()) {
        continue;
      }
      for (int i = 0; i < forms.size(); i++) {
        // Asked only of a form that matches: building the state is the expensive half.
        if (executed.equals(forms.get(i)) && drawn.solves(written(stood))) {
          return i;
        }
      }
    }
    return -1;
  }

  /**
   * The moves as they read from the frame their case is drawn in: named from the first grip that
   * leaves them solving it, the cube as it was held tried first. Null where no grip does, and for
   * notation nothing can read.
   */
  static List<String> asDrawn(String moves, Drawn drawn) {
    List<String> turns;
    try {
      turns = of(moves);
    } catch (RuntimeException e) {
      return null;
    }
    for (char[] grip : grips()) {
      List<String> stood = conjugatedBy(turns, grip);
      if (drawn.solves(written(stood))) {
        return stood;
      }
    }
    return null;
  }

  /**
   * One string naming what an execution turned, from the frame its case is drawn in and with the
   * alignment off both ends. Two executions of one algorithm come out equal however the solver was
   * holding the cube and whichever way they left the layer facing, which is what makes counting how
   * often each is turned mean anything.
   *
   * <p>Every grip that leaves the execution solving the case is tried, and the smallest key answers
   * for all of them: a last layer case is drawn in four, one per way the layer faces. An execution
   * that solves the case from no grip at all falls back to {@link #keyFromAnyGrip}, which is the
   * same question asked without the case. Null for notation nothing can read.
   */
  static String keyAsDrawn(String moves, Drawn drawn) {
    List<String> turns;
    try {
      turns = of(moves);
    } catch (RuntimeException e) {
      return null; // notation nothing can read groups with nothing, not with everything
    }
    String smallest = null;
    for (char[] grip : grips()) {
      List<String> stood = conjugatedBy(turns, grip);
      if (!drawn.solves(written(stood))) {
        continue;
      }
      String key = written(withoutAlignment(stood));
      if (smallest == null || key.compareTo(smallest) < 0) {
        smallest = key;
      }
    }
    return smallest == null ? keyFromAnyGrip(moves) : smallest;
  }

  /**
   * How many turns an execution takes, counted from the frame its case is drawn in with the
   * alignment off both ends, or 0 for notation nothing can read.
   *
   * <p><b>Which frame is not a detail here.</b> A solver holds the cube where their hands want it,
   * so the turns that align the layer only look like alignment once the cube is stood up the way
   * the case is drawn. Counted anywhere else, the same eleven-move algorithm reads as ten or as
   * thirteen depending on how it was held, and a solver is told their own algorithm is long.
   */
  static int lengthAsDrawn(String moves, Drawn drawn) {
    List<String> stood = asDrawn(moves, drawn);
    if (stood != null) {
      return withoutAlignment(stood).size();
    }
    try {
      return withoutAlignment(of(moves)).size(); // nothing it does names the case
    } catch (RuntimeException e) {
      return 0;
    }
  }

  /** Whether moves, written in some grip, solve the case they are being matched against. */
  interface Drawn {
    boolean solves(String moves);
  }

  /**
   * Every way the cube can be stood up, as the faces each letter would then name. Grown from the
   * three rotations rather than listed, which is what makes it all 24 of them and no repeats.
   *
   * <p>The cube as it is already held comes first, so a caller trying them in turn tries doing
   * nothing first. Anything picking one of these has more than one answer to choose between.
   */
  static List<char[]> grips() {
    List<char[]> grips = new ArrayList<char[]>();
    grips.add(FACES.clone());
    for (int i = 0; i < grips.size(); i++) {
      for (int axis = 0; axis < CYCLES.length; axis++) {
        char[] stood = turned(grips.get(i), axis);
        if (!holds(grips, stood)) {
          grips.add(stood);
        }
      }
    }
    return grips;
  }

  private static boolean holds(List<char[]> grips, char[] stood) {
    for (char[] grip : grips) {
      if (Arrays.equals(grip, stood)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Reads one token as the outer turns it is made of, and returns the frame the ones after it are
   * named from.
   *
   * <p>The turns are named before the frame moves, since they are what the solver had in front of
   * them at the time; a rotation about the same axis leaves them where they are either way.
   */
  private static char[] read(String token, char[] named, List<String> turns) {
    char letter = token.indexOf('w') > 0 ? Character.toLowerCase(token.charAt(0)) : token.charAt(0);
    int[] layer = Notation.layerOf(letter);
    int amount = token.indexOf('2') >= 0 ? 2 : 1;
    if (token.indexOf('\'') >= 0) {
      amount = -amount;
    }
    int axis = layer[0];
    int quarters = amount * layer[3]; // in the direction the axis turns
    boolean carriesPlus = layer[2] >= 1;
    boolean carriesMinus = layer[1] <= -1;
    if (!(layer[1] <= 0 && layer[2] >= 0)) { // one outer layer: no rotation to fold away
      add(turns, named, carriesPlus ? PLUS[axis] : MINUS[axis], carriesPlus ? quarters : -quarters);
      return named;
    }
    if (!carriesPlus) {
      add(turns, named, PLUS[axis], -quarters);
    }
    if (!carriesMinus) {
      add(turns, named, MINUS[axis], quarters);
    }
    char[] rotated = named;
    for (int quarter = 0; quarter < (quarters % 4 + 4) % 4; quarter++) {
      rotated = turned(rotated, axis);
    }
    return rotated;
  }

  private static void add(List<String> turns, char[] named, char face, int quarters) {
    turns.add(write(named[indexOf(face)], quarters));
  }

  /** The faces each letter names once the cube has been rotated a quarter turn about an axis. */
  private static char[] turned(char[] named, int axis) {
    char[] rotated = named.clone();
    char[] cycle = CYCLES[axis];
    for (int i = 0; i < cycle.length; i++) {
      rotated[indexOf(cycle[(i + 1) % cycle.length])] = named[indexOf(cycle[i])];
    }
    return rotated;
  }

  /**
   * Consecutive turns of one face folded into the turn they add up to, the ones adding up to nothing
   * dropped, and a pair of opposite faces always written the same way round. A solver's moves carry
   * all three: a regrip turned back, one algorithm's alignment running into the next one's opening,
   * and two faces that can be turned in either order because neither is in the other's way.
   */
  private static List<String> folded(List<String> turns) {
    List<String> reduced = new ArrayList<String>(turns);
    for (boolean settling = true; settling; ) {
      settling = false;
      for (int i = 1; i < reduced.size() && !settling; i++) {
        settling = settle(reduced, i);
      }
    }
    return reduced;
  }

  /** True when the pair ending at {@code i} was added up, dropped, or put the other way round. */
  private static boolean settle(List<String> turns, int i) {
    char face = turns.get(i).charAt(0);
    char before = turns.get(i - 1).charAt(0);
    if (face == before) {
      int quarters = quartersOf(turns.remove(i)) + quartersOf(turns.remove(i - 1));
      if ((quarters % 4 + 4) % 4 != 0) {
        turns.add(i - 1, write(face, quarters));
      }
      return true;
    }
    if (facesAcross(face, before) && indexOf(face) < indexOf(before)) {
      turns.add(i - 1, turns.remove(i)); // one order for the pair, so the turn behind it can be met
      return true;
    }
    return false;
  }

  /** Whether the two faces are the ends of one axis, and so turn out of each other's way. */
  private static boolean facesAcross(char face, char other) {
    for (int axis = 0; axis < PLUS.length; axis++) {
      if ((PLUS[axis] == face && MINUS[axis] == other)
          || (MINUS[axis] == face && PLUS[axis] == other)) {
        return true;
      }
    }
    return false;
  }

  private static int quartersOf(String turn) {
    int quarters = turn.indexOf('2') >= 0 ? 2 : 1;
    return turn.indexOf('\'') >= 0 ? -quarters : quarters;
  }

  private static String write(char face, int quarters) {
    switch ((quarters % 4 + 4) % 4) {
      case 2: return face + "2";
      case 3: return face + "'";
      default: return String.valueOf(face);
    }
  }

  private static int indexOf(char face) {
    for (int i = 0; i < FACES.length; i++) {
      if (FACES[i] == face) {
        return i;
      }
    }
    throw new IllegalArgumentException("Not a face: " + face);
  }
}
