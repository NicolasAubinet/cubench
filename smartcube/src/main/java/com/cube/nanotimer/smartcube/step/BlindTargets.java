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

  /** What a wanted name is prefixed with when its second target was the solver's to choose. */
  private static final String BREAK_IN = "breakin:";

  /** The order a piece's faces are said in, in pairs: U/D first, then F/B, then R/L. */
  private static final String SAID_ORDER = "UDFBRL";

  private final int holding; // the frame, or nothing carried anywhere when none is known
  private final int reading; // the frame taken back off, to read a reported facelet as a held one

  /**
   * A name and the slot each of its parts stands for, in the order they are said. The slots are what
   * lets a display say which of the pieces an algorithm named it actually put home: the parts are
   * spelled as stickers in the solver's frame, so nothing downstream can work back from the text.
   */
  static final class Named {

    final String name;
    final List<Integer> slots;

    Named(String name, List<Integer> slots) {
      this.name = name;
      this.slots = slots;
    }
  }

  BlindTargets(int frame) {
    this.holding = frame == UNKNOWN_FRAME ? FaceletRotations.IDENTITY : frame;
    this.reading = FaceletRotations.inverse(holding);
  }

  /**
   * The name of an algorithm that turned its pieces where they stand: a flip or a twist, said as
   * every piece it turned. Nothing was shot anywhere, so there is no target order to read and no
   * buffer to leave out — a buffer turned in place is as much of a memo item as any other piece.
   *
   * <p>It opens on the buffer all the same, where it turned one, the way every other name does.
   * Said in slot order the buffer lands wherever the cube happens to store it, and a name opening
   * anywhere else reads as one shot from there.
   *
   * <p>A twist has a direction, and the state before the algorithm carries it: a corner is said from
   * the face its U or D sticker was <em>sitting</em> on, so a corner belonging at UBL with white
   * turned onto its left reads {@code LUB}. A flip has no direction to tell, so an edge is said as
   * it stands.
   */
  Named turnedName(String before, List<Integer> turned, int buffer) {
    List<Integer> said = bufferFirst(turned, buffer);
    boolean edges = Cubies.isEdge(said.get(0));
    List<String> names = new ArrayList<String>(said.size());
    for (int slot : said) {
      names.add(edges ? spell(slot) : spellFrom(twistedOnto(before, slot)));
    }
    return new Named((edges ? FLIP : TWIST) + join(names), new ArrayList<Integer>(said));
  }

  /**
   * The facelet a twisted piece's U or D sticker is sitting on, which is what says which way. It is
   * the sticker of the piece <em>sitting</em> in the slot, not of the piece that belongs there: a
   * corner carries exactly one of the two whatever slot it is in, and a blind solve holds a foreign
   * corner in its buffer right up until the parity.
   */
  private int twistedOnto(String before, int slot) {
    for (int facelet : Cubies.PIECES[slot]) {
      char sticker = before.charAt(facelet);
      if (sticker == heldColour(Cubies.U) || sticker == heldColour(Cubies.D)) {
        return facelet;
      }
    }
    return Cubies.PIECES[slot][0];
  }

  /** The colour of one of the solver's faces, said as the face the cube reports it on. */
  private char heldColour(int face) {
    return Cubies.FACES.charAt(FaceletRotations.face(holding, face));
  }

  /**
   * The name of a parity: the two corners it swapped and then the two edges. Nothing was shot
   * anywhere, and two swaps rather than one four-piece cycle is what the solver memorised.
   *
   * <p>Each pair opens on the buffer of its type, the way every other algorithm's name does. A
   * parity swaps the buffer with one other piece, so the buffer is the piece the pair is read from
   * even though nothing was shot at it; said in slot order instead, half of them came out backwards.
   */
  Named swapName(List<Integer> corners, List<Integer> edges, int cornerBuffer, int edgeBuffer) {
    List<Integer> saidCorners = bufferFirst(corners, cornerBuffer);
    List<Integer> saidEdges = bufferFirst(edges, edgeBuffer);
    List<Integer> slots = new ArrayList<Integer>(saidCorners);
    slots.addAll(saidEdges);
    return new Named(join(spellAll(saidCorners)) + " + " + join(spellAll(saidEdges)), slots);
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
  Named name(String before, String after, int buffer, List<Integer> pieces) {
    Named shot = buffer == NO_BUFFER ? null : shotNames(before, after, buffer, pieces.size());
    return shot != null ? shot
        : new Named(join(spellAll(pieces)), new ArrayList<Integer>(pieces));
  }

  /**
   * The cycle an algorithm shot, in the order the solver said it: the buffer, then each target at
   * the sticker the piece before it landed on. Null unless the shot comes back round to the buffer,
   * which is the one thing a cycle does.
   */
  private Named shotNames(String before, String after, int buffer, int length) {
    List<String> names = new ArrayList<String>(length);
    List<Integer> slots = new ArrayList<Integer>(length);
    names.add(spell(buffer));
    slots.add(buffer);
    int start = FaceletRotations.apply(holding, Cubies.PIECES[heldSlotOf(buffer)][0]);
    int facelet = start;
    for (int i = 1; i < length; i++) {
      facelet = sentTo(before, after, facelet);
      if (facelet < 0) {
        return null;
      }
      names.add(spellFrom(facelet));
      slots.add(Cubies.slotOf(facelet));
    }
    return sentTo(before, after, facelet) == start ? new Named(join(names), slots) : null;
  }

  /**
   * The cycle the cube was standing in: the buffer, the sticker its piece belongs on, and then the
   * sticker the piece waiting there belongs on. What the memo asked for at that moment, read off the
   * cube rather than guessed at, since a shot that lands its piece home is the whole of what a
   * target means.
   *
   * <p>Two targets, because that is what one algorithm shoots. None at all where the buffer already
   * holds its own piece, which is a cycle closed and a break-in the solver is free to make anywhere.
   *
   * <p><b>Where the cycle closes on the first target the second is not the cube's to say.</b> The
   * piece waiting at that target is the buffer's own, so the algorithm shooting it lands one target
   * and breaks into a new cycle with the other, at whichever piece of the type the solver pleases —
   * an ordinary cycle break, and no more a parity than any other. The name is marked for it rather
   * than cut short at two, since an algorithm shoots three pieces however it is written. Cut short
   * it stays only when the type has nothing left to break into, where what was owed really was the
   * one target, for a last algorithm or a parity to carry.
   */
  String wantedName(String before, int buffer) {
    int start = FaceletRotations.apply(holding, Cubies.PIECES[heldSlotOf(buffer)][0]);
    int first = homeFacelet(before, start);
    if (first < 0 || Cubies.slotOf(first) == buffer) {
      return null;
    }
    List<String> names = new ArrayList<String>(3);
    names.add(spell(buffer));
    names.add(spellFrom(first));
    int second = homeFacelet(before, first);
    if (second >= 0 && Cubies.slotOf(second) != buffer) {
      names.add(spellFrom(second));
      return join(names);
    }
    return (breaksIn(before, buffer, Cubies.slotOf(first)) ? BREAK_IN : "") + join(names);
  }

  /**
   * What the cube owed an algorithm that opened a new cycle: the break-in as the solver made it,
   * and then the sticker the piece it takes into the buffer belongs on.
   *
   * <p>A closed cycle leaves the first target the solver's own — any piece of the type still out
   * will do — so it is said back rather than named, and only the second is the cube's. Read off the
   * state the algorithm found and the one it left, which is what says where the buffer's own piece
   * was parked.
   */
  String wantedAfterABreakIn(String before, String after, int buffer) {
    int start = FaceletRotations.apply(holding, Cubies.PIECES[heldSlotOf(buffer)][0]);
    int first = sentTo(before, after, start);
    int second = first < 0 ? -1 : homeFacelet(before, first);
    if (second < 0) {
      return null;
    }
    List<String> names = new ArrayList<String>(3);
    names.add(spell(buffer));
    names.add(spellFrom(first));
    names.add(spellFrom(second));
    return join(names);
  }

  /** Whether a cycle closing on that target leaves the solver a piece of the type to break into. */
  private static boolean breaksIn(String before, int buffer, int first) {
    for (int slot = 0; slot < Cubies.PIECES.length; slot++) {
      if (slot != buffer && slot != first && Cubies.isEdge(slot) == Cubies.isEdge(buffer)
          && !Cubies.inPlace(before, Cubies.PIECES[slot])) {
        return true;
      }
    }
    return false;
  }

  /** Where the sticker sitting on this facelet belongs, which is where shooting it would send it. */
  private static int homeFacelet(String facelets, int facelet) {
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

  /** Where the sticker on this facelet ended up: a piece by its colours, a sticker by its own. */
  static int sentTo(String before, String after, int facelet) {
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

  /** Some pieces said by the faces they belong on, in the order they are given. */
  List<String> spellAll(List<Integer> slots) {
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
    return said(FaceletRotations.apply(reading, facelet));
  }

  /** The same, of a facelet already in the frame it is to be said in. */
  static String said(int facelet) {
    int[] piece = Cubies.PIECES[Cubies.slotOf(facelet)];
    int start = 0;
    for (int i = 0; i < piece.length; i++) {
      if (piece[i] == facelet) {
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
