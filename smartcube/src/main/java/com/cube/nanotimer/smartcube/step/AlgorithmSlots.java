package com.cube.nanotimer.smartcube.step;

import com.cube.nanotimer.smartcube.model.CubeState;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * The slots a blind algorithm's name says, against the slots its spelled moves really shift.
 *
 * <p>The two are independent evidence. A name is the detector's word, and the detector reads cube
 * states and never sees the gyro; a spelling is the move stream's, resolved through the gyro's
 * rotations. So where they disagree the spelling is what is wrong — a slice folded that was two
 * turns, a slice left unfolded that was one, a wide the gyro invented — and this is what says so.
 *
 * <p>Neither side needs the scramble. A sequence of moves permutes slots whatever is sitting in
 * them, so the shifted set can be read off a solved cube; and both sides are spelled in the frame
 * the solver held the cube in, which is the frame a facelet index already stands for.
 */
public final class AlgorithmSlots {

  private static final Map<String, Integer> BY_LETTERS = byLetters();

  private AlgorithmSlots() {
  }

  /**
   * The pieces a step's name names, in the order they are said — the mark saying what was done to
   * them taken off, and the rest split on everything a name joins pieces with. A name that names
   * none comes back as itself, and nothing marks one: it carries no mark either.
   */
  public static String[] pieces(String name) {
    return name == null ? new String[0] : name.substring(name.indexOf(':') + 1).split("-| \\+ ");
  }

  /** The slots those pieces stand in, or null where the name is not a list of pieces at all. */
  public static int[] named(String name) {
    String[] pieces = pieces(name);
    if (pieces.length == 0) {
      return null;
    }
    int[] slots = new int[pieces.length];
    for (int i = 0; i < pieces.length; i++) {
      Integer slot = BY_LETTERS.get(sorted(pieces[i]));
      if (slot == null) {
        return null;
      }
      slots[i] = slot;
    }
    Arrays.sort(slots);
    return slots;
  }

  /**
   * The slots the given moves shift, sorted.
   *
   * <p>A rotation leading the sequence is the frame it is spelled in rather than turning the solver
   * did — the grip a blind reconstruction opens on — so it is applied and not counted. Nothing
   * later can be: a blind spelling carries no other rotation.
   */
  public static int[] shiftedBy(String moves) {
    String[] tokens = moves.trim().isEmpty() ? new String[0] : moves.trim().split("\\s+");
    int i = 0;
    String before = CubeState.SOLVED_FACELETS;
    while (i < tokens.length && "xyz".indexOf(tokens[i].charAt(0)) >= 0) {
      before = Notation.apply(before, tokens[i++]);
    }
    String after = before;
    for (; i < tokens.length; i++) {
      after = Notation.apply(after, tokens[i]);
    }
    int[] shifted = new int[Cubies.PIECES.length];
    int count = 0;
    for (int slot = 0; slot < Cubies.PIECES.length; slot++) {
      for (int facelet : Cubies.PIECES[slot]) {
        if (before.charAt(facelet) != after.charAt(facelet)) {
          shifted[count++] = slot;
          break;
        }
      }
    }
    return Arrays.copyOf(shifted, count);
  }

  /** Every slot by the letters of its home colours, which name it whichever way it is said. */
  private static Map<String, Integer> byLetters() {
    Map<String, Integer> slots = new HashMap<String, Integer>();
    for (int slot = 0; slot < Cubies.PIECES.length; slot++) {
      StringBuilder letters = new StringBuilder();
      for (int facelet : Cubies.PIECES[slot]) {
        letters.append(Cubies.SOLVED.charAt(facelet));
      }
      slots.put(sorted(letters.toString()), slot);
    }
    return slots;
  }

  private static String sorted(String letters) {
    char[] chars = letters.toCharArray();
    Arrays.sort(chars);
    return new String(chars);
  }
}
