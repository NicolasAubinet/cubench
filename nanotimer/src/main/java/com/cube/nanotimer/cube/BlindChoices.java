package com.cube.nanotimer.cube;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Which way the ambiguous readings of a blind solve's stream were taken: whether each wide the gyro
 * offered is believed, whether each opposite-face pair it vouched for no spin is folded into a
 * slice, and whether each slice it did vouch for really was one. Everything else about the spelling
 * follows from the stream and the grip, so this is the whole of what {@link BlindSpelling} has to
 * weigh.
 *
 * <p>Each reading is held as a departure from the answer the stream itself gives, so an untouched
 * one costs nothing to carry and a weighing can flip one and flip it back.
 *
 * <p>Immutable, because the weighing tries a reading and keeps it only if it turns out better.
 */
final class BlindChoices {

  private final Set<Long> peeked; // the stream's own answer: these wides are the halves of a peek
  private final Set<Long> wides;
  private final Set<Long> pairs;
  private final Set<Long> slices;

  private BlindChoices(Set<Long> peeked, Set<Long> wides, Set<Long> pairs, Set<Long> slices) {
    this.peeked = peeked;
    this.wides = wides;
    this.pairs = pairs;
    this.slices = slices;
  }

  /** The reading the stream alone gives: the peeks already dropped, nothing else second-guessed. */
  static BlindChoices of(Set<Long> peeked) {
    return new BlindChoices(peeked, Collections.<Long>emptySet(), Collections.<Long>emptySet(),
        Collections.<Long>emptySet());
  }

  /** The same, with the wide whose spin is dated at {@code offsetMs} taken the other way. */
  BlindChoices withWideInverted(long offsetMs) {
    return new BlindChoices(peeked, flipped(wides, offsetMs), pairs, slices);
  }

  /** The same, with the pair opening at {@code offsetMs} taken the other way. */
  BlindChoices withPairInverted(long offsetMs) {
    return new BlindChoices(peeked, wides, flipped(pairs, offsetMs), slices);
  }

  /** The same, with the slice opening at {@code offsetMs} taken the other way. */
  BlindChoices withSliceInverted(long offsetMs) {
    return new BlindChoices(peeked, wides, pairs, flipped(slices, offsetMs));
  }

  /** A wide the gyro offered: believed unless it was read as half of a peek, or inverted. */
  boolean believesWide(long offsetMs) {
    return peeked.contains(offsetMs) == wides.contains(offsetMs);
  }

  /** A pair the gyro vouched no spin for: two faces unless inverted. */
  boolean foldsPair(long offsetMs) {
    return pairs.contains(offsetMs);
  }

  /** A pair the gyro did vouch a spin for: one slice unless inverted. */
  boolean foldsSlice(long offsetMs) {
    return !slices.contains(offsetMs);
  }

  private static Set<Long> flipped(Set<Long> set, long offsetMs) {
    Set<Long> flipped = new HashSet<Long>(set);
    if (!flipped.remove(offsetMs)) {
      flipped.add(offsetMs);
    }
    return flipped;
  }
}
