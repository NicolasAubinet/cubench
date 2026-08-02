package com.cube.nanotimer.smartcube.step;

import java.util.ArrayList;
import java.util.List;

/**
 * What to call an algorithm: the cycle it shot, written the way the solver named it — the buffer it
 * started from, then the target it was shot to, then the one after that.
 *
 * <p><b>The buffer is named too</b>, because a solver may <em>float</em> theirs and start each cycle
 * from whichever piece they please — the piece left out would be one the memo has a letter for.
 *
 * <p>A name carries two things a list of slots does not: it is spelled in the frame the cube is
 * <em>held</em> in, not the one it reports in; and it names a sticker, so {@code UBL} is not
 * {@code LUB}. Both come of following the cycle by where each piece was <b>sent</b>, which is where
 * the solver aimed it — and not by where it belongs, which on a cycle break is somewhere else.
 *
 * <p><b>An algorithm that turned its pieces where they stand has no shot</b> — a flip or a twist is
 * memorised as the pieces themselves, so it is said as all of them and marked for what was done to
 * them. The mark is a code the display translates: pieces are spelled the same in every language.
 */
final class BlindTargets {

  /** No frame established yet: names are spelled as the cube reports them. */
  static final int UNKNOWN_FRAME = -1;

  /** Nothing says which piece the algorithm was shot from, so it is said as pieces and not a cycle. */
  static final int NO_BUFFER = -1;

  /** What a name is prefixed with when the algorithm turned its pieces where they stand. */
  private static final String FLIP = "flip:", TWIST = "twist:";

  /** The order a piece's faces are said in, in pairs: U/D first, then F/B, then R/L. */
  private static final String SAID_ORDER = "UDFBRL";

  private final int holding; // the frame, or nothing carried anywhere when none is known
  private final int reading; // the frame taken back off, to read a reported facelet as a held one

  BlindTargets(int frame) {
    this.holding = frame == UNKNOWN_FRAME ? FaceletRotations.IDENTITY : frame;
    this.reading = FaceletRotations.inverse(holding);
  }

  /**
   * The name of an algorithm that turned its pieces where they stand: a flip or a twist, said as
   * every piece it turned. Nothing was shot anywhere, so there is no target order to read and no
   * buffer to leave out — a buffer turned in place is as much of a memo item as any other piece.
   *
   * <p>A twist has a direction, and the state before the algorithm carries it: a corner is said from
   * the face its U or D sticker was <em>sitting</em> on, so a corner belonging at UBL with white
   * turned onto its left reads {@code LUB}. A flip has no direction to tell, so an edge is said as
   * it stands.
   */
  String turnedName(String before, List<Integer> turned) {
    boolean edges = Cubies.isEdge(turned.get(0));
    List<String> names = new ArrayList<String>(turned.size());
    for (int slot : turned) {
      names.add(edges ? spell(slot) : spellFrom(twistedOnto(before, slot)));
    }
    return (edges ? FLIP : TWIST) + join(names);
  }

  /** The facelet a twisted piece's U or D sticker is sitting on, which is what says which way. */
  private int twistedOnto(String before, int slot) {
    for (int held : Cubies.PIECES[heldSlotOf(slot)]) {
      if (SAID_ORDER.indexOf(Cubies.SOLVED.charAt(held)) > 1) {
        continue; // not the U or D sticker: it says nothing about which way the piece was turned
      }
      char sticker = Cubies.SOLVED.charAt(FaceletRotations.apply(holding, held));
      for (int facelet : Cubies.PIECES[slot]) {
        if (before.charAt(facelet) == sticker) {
          return facelet;
        }
      }
    }
    return Cubies.PIECES[slot][0];
  }

  /**
   * The name of a parity: the two corners it swapped and then the two edges. Nothing was shot
   * anywhere, and two swaps rather than one four-piece cycle is what the solver memorised.
   *
   * <p>Each pair opens on the buffer of its type, the way every other algorithm's name does. A
   * parity swaps the buffer with one other piece, so the buffer is the piece the pair is read from
   * even though nothing was shot at it; said in slot order instead, half of them came out backwards.
   */
  String swapName(List<Integer> corners, List<Integer> edges, int cornerBuffer, int edgeBuffer) {
    return join(spellAll(bufferFirst(corners, cornerBuffer))) + " + "
        + join(spellAll(bufferFirst(edges, edgeBuffer)));
  }

  /** The pair with its buffer at the front, or untouched where the buffer is not one of them. */
  private static List<Integer> bufferFirst(List<Integer> pieces, int buffer) {
    if (buffer == NO_BUFFER || pieces.size() < 2 || pieces.get(0) == buffer
        || !pieces.contains(buffer)) {
      return pieces;
    }
    List<Integer> ordered = new ArrayList<Integer>(pieces);
    ordered.remove(Integer.valueOf(buffer));
    ordered.add(0, buffer);
    return ordered;
  }

  /**
   * The name of one algorithm, from the drift-free states either side of it, the piece it was shot
   * from and the pieces it moved. Where nothing says which piece it was shot from — a parity, or a
   * cycle the solve has not settled the buffer of — the pieces are named as they stand instead.
   */
  String name(String before, String after, int buffer, List<Integer> pieces) {
    List<String> shot = buffer == NO_BUFFER ? null : shotNames(before, after, buffer, pieces.size());
    return join(shot == null ? spellAll(pieces) : shot);
  }

  /**
   * The cycle an algorithm shot, in the order the solver said it: the buffer, then each target at
   * the sticker the piece before it landed on. Null unless the shot comes back round to the buffer,
   * which is the one thing a cycle does.
   */
  private List<String> shotNames(String before, String after, int buffer, int length) {
    List<String> names = new ArrayList<String>(length);
    names.add(spell(buffer));
    int start = FaceletRotations.apply(holding, Cubies.PIECES[heldSlotOf(buffer)][0]);
    int facelet = start;
    for (int i = 1; i < length; i++) {
      facelet = sentTo(before, after, facelet);
      if (facelet < 0) {
        return null;
      }
      names.add(spellFrom(facelet));
    }
    return sentTo(before, after, facelet) == start ? names : null;
  }

  /** Where the sticker on this facelet ended up: a piece by its colours, a sticker by its own. */
  private static int sentTo(String before, String after, int facelet) {
    int home = Cubies.homeSlotOf(before, Cubies.slotOf(facelet));
    if (home < 0) {
      return -1;
    }
    for (int slot = 0; slot < Cubies.PIECES.length; slot++) {
      if (Cubies.homeSlotOf(after, slot) != home) {
        continue;
      }
      for (int candidate : Cubies.PIECES[slot]) {
        if (after.charAt(candidate) == before.charAt(facelet)) {
          return candidate;
        }
      }
    }
    return -1;
  }

  private List<String> spellAll(List<Integer> slots) {
    List<String> names = new ArrayList<String>(slots.size());
    for (int slot : slots) {
      names.add(spell(slot));
    }
    return names;
  }

  /** The piece by the faces it belongs on, said in the order they are usually said. */
  private String spell(int slot) {
    return letters(Cubies.PIECES[heldSlotOf(slot)], 0);
  }

  /** The same piece as the solver names it: where the slot the cube reports sits in their frame. */
  private int heldSlotOf(int slot) {
    return Cubies.slotOf(FaceletRotations.apply(reading, Cubies.PIECES[slot][0]));
  }

  /** The piece from this facelet round: the sticker shot to first, then the rest as it turns. */
  private String spellFrom(int facelet) {
    int held = FaceletRotations.apply(reading, facelet);
    int[] piece = Cubies.PIECES[Cubies.slotOf(held)];
    int start = 0;
    for (int i = 0; i < piece.length; i++) {
      if (piece[i] == held) {
        start = i;
      }
    }
    return letters(piece, start);
  }

  /**
   * A piece said from one of its stickers: that sticker's face first — which is the whole of what a
   * target carries beyond the piece — and the rest in the order a piece is said in, U/D then F/B
   * then R/L. That order is the standard one and is not the order the facelets are stored in, which
   * runs round the piece: the corner at U, B and R is {@code UBR}, and shot to its B sticker it is
   * {@code BUR} rather than the {@code BRU} that going round it gives.
   */
  private static String letters(int[] piece, int start) {
    StringBuilder name = new StringBuilder(piece.length);
    name.append(Cubies.SOLVED.charAt(piece[start]));
    for (int axis = 0; axis < SAID_ORDER.length() / 2; axis++) {
      for (int i = 0; i < piece.length; i++) {
        char letter = Cubies.SOLVED.charAt(piece[i]);
        if (i != start && SAID_ORDER.indexOf(letter) / 2 == axis) {
          name.append(letter);
        }
      }
    }
    return name.toString();
  }

  private static String join(List<String> names) {
    StringBuilder joined = new StringBuilder();
    for (String name : names) {
      if (joined.length() > 0) {
        joined.append('-');
      }
      joined.append(name);
    }
    return joined.toString();
  }
}
