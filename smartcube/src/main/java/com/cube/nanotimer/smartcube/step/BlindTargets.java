package com.cube.nanotimer.smartcube.step;

import java.util.ArrayList;
import java.util.List;

/**
 * What to call an algorithm: the two targets it was shot at, written the way the solver named them.
 *
 * <p><b>Shot at, not put home.</b> An algorithm aims at two targets and usually solves both, but a
 * cycle break solves only the second — the first receives the buffer's piece, which does not belong
 * there. Both were still aimed at and both are named, because the pair is what the solver memorised.
 * They are the two pieces the algorithm moved that are not the buffer.
 *
 * <p>A target carries three things a slot index does not: it is spelled in the frame the cube is
 * <em>held</em> in, not the one it reports in; it names a sticker, so {@code UBL} is not
 * {@code LUB}; and it is never the buffer, which is where the cycle starts rather than something
 * the algorithm was for.
 *
 * <p>All three need the shot followed, which state alone gives: <b>the buffer's sticker lands
 * somewhere — that facelet is the first target; whatever sticker sat there beforehand belongs
 * somewhere — that facelet is the second.</b> Read from where the buffer's piece ended up rather
 * than from where it belongs, so a cycle break stays readable: there it is shot into a piece it does
 * not belong to, and only the second target comes home.
 *
 * <p><b>An algorithm that turned its pieces where they stand has no shot and no targets</b> — a flip
 * or a twist is memorised as the pieces themselves, so it is said as all of them, buffer included,
 * and marked for what was done to them. The mark is a code the display translates, since the pieces
 * are spelled the same in every language and only the word for the turn is not.
 */
final class BlindTargets {

  /** No frame established yet: names are spelled as the cube reports them, and nothing is dropped. */
  static final int UNKNOWN_FRAME = -1;

  /** What a name is prefixed with when the algorithm turned its pieces where they stand. */
  private static final String FLIP = "flip:", TWIST = "twist:";

  /** The buffers, as facelets of the frame the solver holds: the U sticker of UF and of UFR. */
  private static final int EDGE_BUFFER = 7, CORNER_BUFFER = 8;

  /** The order a piece's faces are said in, in pairs: U/D first, then F/B, then R/L. */
  private static final String SAID_ORDER = "UDFBRL";

  private final int frame;
  private final int holding; // the same, usable when no frame is known: nothing is carried anywhere
  private final int reading; // the frame taken back off, to read a reported facelet as a held one

  BlindTargets(int frame) {
    this.frame = frame;
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
   * The name of one algorithm, from the drift-free states either side of it, the pieces it moved and
   * the ones it put home. Only a shot — pieces cycled round rather than turned where they stand —
   * has a buffer.
   */
  String name(String before, String after, List<Integer> moved, List<Integer> gained, boolean shot) {
    // What to fall back on: what it put home, or what it merely moved when it put nothing home.
    List<Integer> plainly = gained.isEmpty() ? moved : gained;
    if (frame == UNKNOWN_FRAME || !shot) {
      return join(spellAll(plainly));
    }
    int buffer = FaceletRotations.apply(frame, bufferFor(moved));
    int first = shotTo(before, after, buffer);
    int second = first < 0 ? -1 : homeOfStickerAt(before, first);
    int bufferSlot = Cubies.slotOf(buffer);
    List<String> names = new ArrayList<String>();
    List<Integer> named = new ArrayList<Integer>();
    // Two stickers of one piece are two targets, not one: a piece flipped where it stands is shot to
    // each of its stickers in turn. So the pair is separated by sticker, and only the buffer's own
    // piece drops out -- a shot resolving onto it never left its place, and was not aimed anywhere.
    for (int target : new int[] {first, second}) {
      int slot = target < 0 ? -1 : Cubies.slotOf(target);
      if (slot >= 0 && slot != bufferSlot && (names.isEmpty() || first != second)) {
        names.add(spellFrom(target));
        named.add(slot);
      }
    }
    // Whatever the shot does not account for is named as it stands, and the buffer never is.
    for (int slot : gained) {
      if (!named.contains(slot) && slot != bufferSlot) {
        names.add(spell(slot));
      }
    }
    return names.isEmpty() ? join(spellAll(plainly)) : join(names);
  }

  /** Whose buffer to follow: the type the algorithm worked on is the type it was shot from. */
  private static int bufferFor(List<Integer> moved) {
    return Cubies.isEdge(moved.get(0)) ? EDGE_BUFFER : CORNER_BUFFER;
  }

  /** The facelet the buffer's sticker landed on: on a cycle break that is not where it belongs. */
  private static int shotTo(String before, String after, int buffer) {
    int home = Cubies.homeSlotOf(before, Cubies.slotOf(buffer));
    if (home < 0) {
      return -1;
    }
    for (int slot = 0; slot < Cubies.PIECES.length; slot++) {
      if (Cubies.homeSlotOf(after, slot) != home) {
        continue;
      }
      for (int facelet : Cubies.PIECES[slot]) {
        if (after.charAt(facelet) == before.charAt(buffer)) {
          return facelet;
        }
      }
    }
    return -1;
  }

  /** Where the sticker sitting on this facelet belongs: the next target the shot walks to. */
  private static int homeOfStickerAt(String facelets, int facelet) {
    int home = Cubies.homeSlotOf(facelets, Cubies.slotOf(facelet));
    if (home < 0) {
      return -1;
    }
    for (int candidate : Cubies.PIECES[home]) {
      if (Cubies.SOLVED.charAt(candidate) == facelets.charAt(facelet)) {
        return candidate;
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
