package com.cube.nanotimer.cube;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Which way the ambiguous readings of a blind solve's stream were taken: which wides are believed,
 * and which opposite-face pairs are folded into a slice. Everything else about the spelling follows
 * from the stream and the grip, so this is the whole of what {@link BlindSpelling} has to weigh.
 *
 * <p>Immutable, because the weighing tries a reading and keeps it only if it turns out better.
 */
final class BlindChoices {

  private final Set<Long> peeked;
  private final Set<Long> disbelieved;
  private final Set<Long> folded;

  private BlindChoices(Set<Long> peeked, Set<Long> disbelieved, Set<Long> folded) {
    this.peeked = peeked;
    this.disbelieved = disbelieved;
    this.folded = folded;
  }

  /** The reading the stream alone gives: the peeks already dropped, nothing else second-guessed. */
  static BlindChoices of(Set<Long> peeked) {
    return new BlindChoices(peeked, Collections.<Long>emptySet(), Collections.<Long>emptySet());
  }

  /** The same, with the wide whose spin is dated at {@code offsetMs} taken for gyro noise. */
  BlindChoices withoutWide(long offsetMs) {
    return new BlindChoices(peeked, plus(disbelieved, offsetMs), folded);
  }

  /** The same, with the pair opening at {@code offsetMs} read as the one slice it turned. */
  BlindChoices withPairFolded(long offsetMs) {
    return new BlindChoices(peeked, disbelieved, plus(folded, offsetMs));
  }

  boolean believesWide(long offsetMs) {
    return !peeked.contains(offsetMs) && !disbelieved.contains(offsetMs);
  }

  boolean foldsPair(long offsetMs) {
    return folded.contains(offsetMs);
  }

  private static Set<Long> plus(Set<Long> set, long offsetMs) {
    Set<Long> grown = new HashSet<Long>(set);
    grown.add(offsetMs);
    return grown;
  }
}
