package com.cube.nanotimer.smartcube.step;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The geometry of an algorithm that exchanges pieces rather than cycling them: a parity's two pairs,
 * and the swap Old Pochmann and M2 shoot with, which trades the buffer's piece for its target and
 * carries a second pair along doing it. Four pieces in two exchanges, either way.
 *
 * <p>Nothing here knows what a solve is shooting from. {@link BlindStepDetector} asks which pair was
 * aimed at; this only says what the pairs are.
 *
 * <p><b>Until a buffer is known, it is the piece running through every waiting algorithm</b>
 * ({@link #sharedPiece}). The carried pair can run through them too: an M2's other two edges, a
 * T perm's two corners wherever a setup leaves them. A piece exchanged with the same partner
 * {@value #CARRIED_RUN} times over was carried, since shooting the same two pieces every time gets
 * nowhere. Where that leaves more than one, the one the solver declared is the buffer. Failing
 * that, once a run that long still leaves several, it is the one exchanged with more than one
 * partner, and among those the one whose piece went home most: a shot sends it home every time but
 * a cycle break, a carried piece only when its pair swaps back. Anything left tied waits.
 */
final class BlindSwaps {

  /** Pieces two exchanged pairs make. */
  static final int PIECES = 4;

  /**
   * Algorithms exchanging a piece with the same partner before that pair counts as carried. Two is
   * only a piece turned where it stands, shot at each of its stickers in turn.
   */
  private static final int CARRIED_RUN = 3;

  private BlindSwaps() {
  }

  /** The two pairs an algorithm exchanged, or null where it did not exchange two pairs. */
  static List<List<Integer>> exchanges(String before, String after) {
    return exchanges(before, after, Cubies.moved(before, after));
  }

  /** The same, of these pieces alone: against the solved cube, the pairs a state is left swapped in. */
  static List<List<Integer>> exchanges(String before, String after, List<Integer> moved) {
    if (moved.size() != PIECES) {
      return null;
    }
    for (int other = 1; other < moved.size(); other++) {
      List<Integer> pair = Arrays.asList(moved.get(0), moved.get(other));
      List<Integer> rest = new ArrayList<>(moved);
      rest.removeAll(pair);
      if (exchanged(before, after, pair) && exchanged(before, after, rest)) {
        return Arrays.asList(pair, rest);
      }
    }
    return null;
  }

  /** Whether the algorithm exchanged two corners and two edges, which is what a parity does. */
  static boolean exchangesTwoOfEach(String before, String after) {
    List<List<Integer>> exchanges = exchanges(before, after);
    return exchanges != null
        && Cubies.isEdge(exchanges.get(0).get(0)) != Cubies.isEdge(exchanges.get(1).get(0));
  }

  /** Whichever of these pairs holds the piece, or null. */
  static List<Integer> pairWith(List<List<Integer>> pairs, int piece) {
    if (pairs != null) {
      for (List<Integer> pair : pairs) {
        if (pair.contains(piece)) {
          return pair;
        }
      }
    }
    return null;
  }

  /** The pieces among {@code slots} that belong to the pair. */
  static List<Integer> kept(List<Integer> slots, List<Integer> pair) {
    List<Integer> kept = new ArrayList<>(slots);
    kept.retainAll(pair);
    return kept;
  }

  /** An algorithm still waiting to be told its buffer: what it could have aimed at, and its states. */
  static final class Waiting {
    final List<List<Integer>> pairs; // or the one three-cycle it moved, which names no pair
    final String before;
    final String after;

    Waiting(List<List<Integer>> pairs, String before, String after) {
      this.pairs = pairs;
      this.before = before;
      this.after = after;
    }

    /** Whether the piece that was sitting in the slot went home, which is what a shot from it does. */
    boolean sentHome(int slot) {
      int home = Cubies.homeSlotOf(before, slot);
      return home != slot && Cubies.homeSlotOf(after, home) == home;
    }
  }

  /**
   * The one piece every algorithm of {@code run} (latest first) was shot from, or NO_BUFFER while
   * nothing tells it apart; {@code declared} are the solver's buffers as the cube reports them.
   */
  static int sharedPiece(List<Waiting> run, List<Integer> declared) {
    List<Integer> candidates = new ArrayList<>();
    for (List<Integer> pair : run.get(0).pairs) {
      for (int piece : pair) {
        if (inEveryOne(run, piece) && !carriedThroughout(run, piece)) {
          candidates.add(piece);
        }
      }
    }
    if (candidates.size() == 1) {
      return candidates.get(0);
    }
    for (int piece : candidates) {
      if (declared.contains(piece)) {
        return piece;
      }
    }
    if (run.size() < CARRIED_RUN) {
      return BlindTargets.NO_BUFFER;
    }
    List<Integer> movedOn = new ArrayList<>();
    for (int piece : candidates) {
      if (partnersOf(run, piece) > 1) {
        movedOn.add(piece);
      }
    }
    if (movedOn.size() < 2) {
      return movedOn.isEmpty() ? BlindTargets.NO_BUFFER : movedOn.get(0);
    }
    return sentHomeMost(run, movedOn);
  }

  /** How many different pieces the piece was exchanged with over the run. */
  private static int partnersOf(List<Waiting> run, int piece) {
    Set<Integer> partners = new HashSet<>();
    for (Waiting waiting : run) {
      List<Integer> pair = pairWith(waiting.pairs, piece);
      if (pair != null && pair.size() == 2) { // a three-cycle names no partner
        partners.add(pair.get(0) == piece ? pair.get(1) : pair.get(0));
      }
    }
    return partners.size();
  }

  /** The candidate whose piece went home in the most algorithms of the run, or none on a tie. */
  private static int sentHomeMost(List<Waiting> run, List<Integer> candidates) {
    int best = BlindTargets.NO_BUFFER;
    int most = 0;
    boolean tied = false;
    for (int piece : candidates) {
      int sent = 0;
      for (Waiting waiting : run) {
        if (waiting.sentHome(piece)) {
          sent++;
        }
      }
      if (sent > most) {
        best = piece;
        most = sent;
        tied = false;
      } else if (sent == most) {
        tied = true;
      }
    }
    return tied ? BlindTargets.NO_BUFFER : best;
  }

  /** Whether every algorithm that exchanged the piece exchanged it with the same partner. */
  private static boolean carriedThroughout(List<Waiting> run, int piece) {
    List<Integer> partner = null;
    int exchanges = 0;
    for (Waiting waiting : run) {
      List<Integer> pair = pairWith(waiting.pairs, piece);
      if (pair == null || pair.size() != 2) {
        continue; // a three-cycle says nothing about a pair being carried
      }
      if (partner != null && !pair.containsAll(partner)) {
        return false;
      }
      partner = pair;
      exchanges++;
    }
    return exchanges >= CARRIED_RUN;
  }

  private static boolean inEveryOne(List<Waiting> run, int piece) {
    for (Waiting waiting : run) {
      if (pairWith(waiting.pairs, piece) == null) {
        return false;
      }
    }
    return true;
  }

  /** Whether the two slots came out holding each other's piece. */
  private static boolean exchanged(String before, String after, List<Integer> pair) {
    return Cubies.homeSlotOf(before, pair.get(0)) == Cubies.homeSlotOf(after, pair.get(1))
        && Cubies.homeSlotOf(before, pair.get(1)) == Cubies.homeSlotOf(after, pair.get(0));
  }
}
