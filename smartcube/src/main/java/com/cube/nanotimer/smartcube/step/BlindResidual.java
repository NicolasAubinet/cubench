package com.cube.nanotimer.smartcube.step;

import java.util.ArrayList;
import java.util.List;

/**
 * What a blind solve was left in: the pieces not home when it stopped, and the shape they make.
 *
 * <p>A blind solver who comes out wrong cannot see how wrong, and the cube in their hands is the
 * only record of it — so it is picked up, inspected, and by then the solve is over. The state at the
 * last move says it exactly: three pieces in a cycle is a misfire or a memo slip, two edges flipped
 * is an orientation item dropped, two of each swapped is the parity that was never done.
 *
 * <p><b>Read modulo whole-cube rotation</b>, like everything else here: a blind solve is full of
 * slices and the core ends up turned, so the state is compared under the rotation that leaves the
 * fewest pieces out. Two rotations can only tie far from solved, where the shape is
 * {@link Shape#SCATTERED} and no piece is named anyway.
 *
 * <p>Names are spelled through {@link BlindTargets}, in the grip the solve was held in, and are the
 * same words in every language: the display picks the sentence, never the pieces.
 */
public final class BlindResidual {

  /** Beyond this the leftover is not a mistake with a shape, it is a solve that fell apart. */
  private static final int MAX_NAMED = 6;

  /** What the leftover pieces make of themselves, which is what says what kind of mistake it was. */
  public enum Shape {
    /** The cube came out: nothing left over. */
    SOLVED,
    EDGE_CYCLE,
    CORNER_CYCLE,
    /** Two corners and two edges swapped: the parity, not done or not done right. */
    PARITY,
    /** Edges turned where they stand. */
    FLIPPED,
    /** Corners turned where they stand. */
    TWISTED,
    /** Both types turned where they stand. */
    TURNED,
    /** Few enough to name, but no shape a single mistake makes. */
    MIXED,
    /** Too far off to say anything but how far. */
    SCATTERED,
  }

  private final Shape shape;
  private final String pieces;
  private final int count;

  private BlindResidual(Shape shape, String pieces, int count) {
    this.shape = shape;
    this.pieces = pieces;
    this.count = count;
  }

  public Shape getShape() {
    return shape;
  }

  /** The pieces left over, said in the order the shape reads in. Empty when there are none to name. */
  public String getPieces() {
    return pieces;
  }

  /** How many pieces are not home. */
  public int getCount() {
    return count;
  }

  /** What was left in the state the solve stopped at, or null when there is no state to read. */
  static BlindResidual of(String facelets, BlindTargets targets) {
    if (facelets == null) {
      return null;
    }
    String steady = withoutDrift(facelets, closestToSolved(facelets));
    List<Integer> misplaced = new ArrayList<Integer>();
    List<Integer> turned = new ArrayList<Integer>();
    for (int slot = 0; slot < Cubies.PIECES.length; slot++) {
      if (Cubies.inPlace(steady, Cubies.PIECES[slot])) {
        continue;
      }
      (Cubies.homeSlotOf(steady, slot) == slot ? turned : misplaced).add(slot);
    }
    int count = misplaced.size() + turned.size();
    if (count == 0) {
      return new BlindResidual(Shape.SOLVED, "", 0);
    }
    if (misplaced.isEmpty()) {
      return new BlindResidual(turnedShape(turned), said(targets, turned, ", "), count);
    }
    if (turned.isEmpty()) {
      List<Integer> cycle = cycleOf(steady, misplaced);
      if (cycle != null) {
        Shape shape = Cubies.isEdge(cycle.get(0)) ? Shape.EDGE_CYCLE : Shape.CORNER_CYCLE;
        return new BlindResidual(shape, said(targets, cycle, "-"), count);
      }
      String parity = parityOf(steady, misplaced, targets);
      if (parity != null) {
        return new BlindResidual(Shape.PARITY, parity, count);
      }
    }
    if (count > MAX_NAMED) {
      return new BlindResidual(Shape.SCATTERED, "", count);
    }
    List<Integer> all = new ArrayList<Integer>(misplaced);
    all.addAll(turned);
    return new BlindResidual(Shape.MIXED, said(targets, all, ", "), count);
  }

  private static Shape turnedShape(List<Integer> turned) {
    boolean edges = false, corners = false;
    for (int slot : turned) {
      if (Cubies.isEdge(slot)) {
        edges = true;
      } else {
        corners = true;
      }
    }
    return edges && corners ? Shape.TURNED : edges ? Shape.FLIPPED : Shape.TWISTED;
  }

  // The three pieces in the order the cycle runs, or null unless three of one type is what they are.
  // Said from the first slot round: a cycle has no start, and the shot it came from is not known here.
  private static List<Integer> cycleOf(String steady, List<Integer> misplaced) {
    if (misplaced.size() != 3) {
      return null;
    }
    for (int slot : misplaced) {
      if (Cubies.isEdge(slot) != Cubies.isEdge(misplaced.get(0))) {
        return null; // an algorithm moves pieces of one type, so a mixed three is not a cycle of one
      }
    }
    List<Integer> cycle = new ArrayList<Integer>(3);
    int slot = misplaced.get(0);
    for (int i = 0; i < 3; i++) {
      if (!misplaced.contains(slot) || cycle.contains(slot)) {
        return null;
      }
      cycle.add(slot);
      slot = Cubies.homeSlotOf(steady, slot);
    }
    return slot == cycle.get(0) ? cycle : null;
  }

  // The two corners and the two edges a parity leaves swapped, corners first as the algorithm is.
  // Null unless the four pieces are exactly two swapped pairs.
  private static String parityOf(String steady, List<Integer> misplaced, BlindTargets targets) {
    if (misplaced.size() != 4) {
      return null;
    }
    List<Integer> edges = new ArrayList<Integer>();
    List<Integer> corners = new ArrayList<Integer>();
    for (int slot : misplaced) {
      (Cubies.isEdge(slot) ? edges : corners).add(slot);
    }
    if (edges.size() != 2 || !swapped(steady, edges) || !swapped(steady, corners)) {
      return null;
    }
    return said(targets, corners, "-") + " + " + said(targets, edges, "-");
  }

  /** Whether the two slots hold each other's piece. */
  private static boolean swapped(String steady, List<Integer> pair) {
    return Cubies.homeSlotOf(steady, pair.get(0)) == pair.get(1)
        && Cubies.homeSlotOf(steady, pair.get(1)) == pair.get(0);
  }

  private static String said(BlindTargets targets, List<Integer> slots, String separator) {
    StringBuilder said = new StringBuilder();
    for (String name : targets.spellAll(slots)) {
      if (said.length() > 0) {
        said.append(separator);
      }
      said.append(name);
    }
    return said.toString();
  }

  /** The rotation the state reads as most nearly solved in, which is the drift the slices left. */
  private static int closestToSolved(String facelets) {
    int closest = FaceletRotations.IDENTITY;
    int fewest = Integer.MAX_VALUE;
    for (int rotation = 0; rotation < FaceletRotations.COUNT; rotation++) {
      int out = 0;
      for (int[] piece : Cubies.PIECES) {
        if (!Cubies.inPlace(facelets, piece, rotation)) {
          out++;
        }
      }
      if (out < fewest) {
        fewest = out;
        closest = rotation;
      }
    }
    return closest;
  }

  private static String withoutDrift(String facelets, int rotation) {
    char[] steady = new char[facelets.length()];
    for (int facelet = 0; facelet < steady.length; facelet++) {
      steady[facelet] = facelets.charAt(FaceletRotations.apply(rotation, facelet));
    }
    return new String(steady);
  }
}
