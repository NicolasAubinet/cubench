package com.cube.nanotimer.cube;

import com.cube.nanotimer.cube.SolveMovesFormat.Move;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import com.cube.nanotimer.smartcube.step.AlgorithmSlots;
import com.cube.nanotimer.vo.SolveStep;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Settles a blind solve's ambiguous readings against the names the detector gave its algorithms.
 *
 * <p>The gyro is meant to be the arbiter of what the hands did, and the reading is faithful to it —
 * no spin, no slice — which is what keeps a deliberate {@code U D'} two-hander from being folded
 * into an {@code E}. But the gyro can be silent where a slice really rocked the core and loud where
 * nothing did, and where it is both at once in one solve the frame is left a quarter turn out and
 * every algorithm after it is spelled somewhere the solver never held the cube.
 *
 * <p><b>What settles it is that a spelled algorithm must shift exactly the slots its own name
 * names.</b> The name is independent evidence: it comes from the detector, which reads cube states
 * and never sees the gyro. So each algorithm's own ambiguous readings are tried, and the one that
 * spells it as its name is kept — the stream's own reading first, which is what leaves a solve that
 * already reads correctly untouched and both of the solver's exceptions (a two-handed {@code U D'},
 * a genuine wide in a blind solve) standing.
 *
 * <p>Trying them <em>per algorithm</em> rather than over the solve at once is what makes the answer
 * the name's and not the search's: the frame carries forward, so an algorithm read wrong takes the
 * rest of the solve with it, and there is no credit to be had for a reading that repairs seven
 * algorithms while leaving the one that broke them wrong. Where several readings spell an algorithm
 * right, the one whose own evidence was weakest is doubted first, and among equals the one that
 * leaves the most of the solve standing is taken.
 *
 * <p>Nothing here needs the scramble or the gyro readings, neither of which is stored, so it repairs
 * blind solves already recorded as well as ones read live. See {@link AlgorithmSlots}.
 */
final class BlindSpelling {

  /** How many readings may be inverted together, for the cases where neither half alone helps. */
  private static final int AT_ONCE = 2;

  /** Enough for a solve with a handful of ambiguities; past it the search is not worth its cost. */
  private static final int MAX_READINGS = 400;

  /** Where two readings both put an algorithm right, the weaker evidence is the doubted one. */
  private static final Comparator<Reading> LEAST_VOUCHED_FIRST = new Comparator<Reading>() {
    @Override
    public int compare(Reading a, Reading b) {
      return a.vouching - b.vouching;
    }
  };

  private BlindSpelling() {
  }

  /**
   * The choices that spell the most of the solve as its names, or the ones passed in where nothing
   * does better. An algorithm that already matches is left alone and costs nothing to weigh.
   */
  static BlindChoices arbitrate(List<Move> stored, CubeRotation grip, BlindChoices choices,
      List<SolveStep> solveSteps) {
    try {
      return settled(stored, grip, choices, solveSteps);
    } catch (RuntimeException e) {
      return choices; // a token this cannot turn a cube by costs the check, never the breakdown
    }
  }

  private static BlindChoices settled(List<Move> stored, CubeRotation grip, BlindChoices choices,
      List<SolveStep> solveSteps) {
    List<Named> named = namedAlgorithms(solveSteps, ambiguities(stored, choices));
    Weighing weighing = new Weighing(stored, grip, solveSteps, named);
    for (Named algorithm : named) {
      if (weighing.spellsAsNamed(choices, algorithm)) {
        continue;
      }
      BlindChoices settled = settle(weighing, choices, algorithm);
      if (settled != null) {
        choices = settled;
      }
    }
    return choices;
  }

  /**
   * The fewest inversions of this algorithm's own readings that spell it as its name, or null where
   * none does. Among them the one that leaves the most of the whole solve standing wins.
   *
   * <p><b>It does not have to leave more standing than the reading it replaces.</b> That was the
   * rule, and it holds a wrong frame in place: the incumbent spells this algorithm as something
   * other than its name, and no count of coincidences elsewhere outweighs the one piece of evidence
   * being asked for. The 2026-08-26 solve is what says so — dropping a real wide as a peek left its
   * whole frame a quarter turn out, and the one algorithm that still happened to match through that
   * frame was enough to block the repair.
   */
  private static BlindChoices settle(Weighing weighing, BlindChoices choices, Named algorithm) {
    for (int size = 1; size <= AT_ONCE && size <= algorithm.readings.size(); size++) {
      BlindChoices best = null;
      int standing = -1;
      for (int[] combination : combinations(algorithm.readings.size(), size)) {
        BlindChoices candidate = choices;
        for (int reading : combination) {
          candidate = algorithm.readings.get(reading).invertedIn(candidate);
        }
        if (!weighing.spellsAsNamed(candidate, algorithm)) {
          continue;
        }
        int solve = weighing.standing(candidate);
        if (solve > standing) {
          standing = solve;
          best = candidate;
        }
      }
      if (best != null) {
        return best;
      }
    }
    return null;
  }

  /** Spells the solve under one set of choices and says how much of it comes out as its names. */
  private static final class Weighing {

    private final List<Move> stored;
    private final CubeRotation grip;
    private final List<SolveStep> solveSteps;
    private final List<Named> named;
    private final Map<String, int[]> shifted = new HashMap<String, int[]>();
    private BlindChoices spelled;
    private List<SolveSolution.Step> steps;
    private int spent;

    private Weighing(List<Move> stored, CubeRotation grip, List<SolveStep> solveSteps,
        List<Named> named) {
      this.stored = stored;
      this.grip = grip;
      this.solveSteps = solveSteps;
      this.named = named;
    }

    private boolean spellsAsNamed(BlindChoices choices, Named algorithm) {
      return spent < MAX_READINGS && matches(spell(choices), algorithm);
    }

    private int standing(BlindChoices choices) {
      List<SolveSolution.Step> steps = spell(choices);
      int standing = 0;
      for (Named algorithm : named) {
        if (matches(steps, algorithm)) {
          standing++;
        }
      }
      return standing;
    }

    /** The last spelling is kept, so a solve every algorithm of which already reads costs one. */
    private List<SolveSolution.Step> spell(BlindChoices choices) {
      if (choices != spelled) {
        spent++;
        spelled = choices;
        steps = SolveSolution.stepsOf(SolveSolution.spell(stored, grip, choices), solveSteps);
      }
      return steps;
    }

    private boolean matches(List<SolveSolution.Step> steps, Named algorithm) {
      String moves = steps.get(algorithm.step).getPartMoves(algorithm.part);
      int[] slots = shifted.get(moves);
      if (slots == null) {
        slots = AlgorithmSlots.shiftedBy(moves);
        shifted.put(moves, slots);
      }
      return Arrays.equals(algorithm.slots, slots);
    }
  }

  /**
   * Every part of the breakdown whose name says which pieces its algorithm moved, each holding the
   * readings the stream left open inside it.
   *
   * <p>Which readings those are is the step windows' answer, the same as which moves a part owns: a
   * step that turned nothing owns neither, and what the parts of a step leave over trails behind
   * them.
   */
  private static List<Named> namedAlgorithms(List<SolveStep> solveSteps, List<Reading> readings) {
    List<Named> named = new ArrayList<Named>();
    long boundaryMs = 0;
    long fromMs = Long.MIN_VALUE;
    for (int s = 0; s < solveSteps.size(); s++) {
      SolveStep step = solveSteps.get(s);
      long stepStartMs = boundaryMs;
      boundaryMs += step.getTotalMs();
      if (step.getExecutionMs() <= 0) {
        continue;
      }
      long partMs = stepStartMs;
      for (int p = 0; p < step.getSubSteps().size(); p++) {
        partMs += step.getSubSteps().get(p).getTotalMs();
        int[] slots = AlgorithmSlots.named(step.getSubSteps().get(p).getName());
        if (slots != null) {
          named.add(new Named(s, p, slots, between(readings, fromMs, partMs)));
        }
        fromMs = partMs;
      }
      fromMs = boundaryMs;
    }
    return named;
  }

  /** The readings inside one algorithm, least-vouched first: the order they are tried in. */
  private static List<Reading> between(List<Reading> readings, long fromMs, long toMs) {
    List<Reading> inside = new ArrayList<Reading>();
    for (Reading reading : readings) {
      if (reading.atMs > fromMs && reading.atMs <= toMs) {
        inside.add(reading);
      }
    }
    Collections.sort(inside, LEAST_VOUCHED_FIRST);
    return inside;
  }

  /**
   * The readings the stream leaves open, walked the way the spelling walks it: a wide shape the
   * gyro's word alone decides, an opposite-face pair left standing for want of a spin, and a pair
   * folded on the strength of one.
   *
   * <p><b>Every one of them is listed, whichever way the stream took it</b>, because the gyro is
   * wrong in both directions: it reports a spin where the hands only rocked the cube, and it misses
   * one where they really did turn a slice. Listing a reading the stream got right costs nothing —
   * inverting it spells the algorithm wrong, and a spelling that does not answer to its name is
   * never kept — and each carries how much the stream's own answer for it was worth, so that where
   * two of them both put an algorithm right the weaker evidence is the one doubted.
   */
  private static List<Reading> ambiguities(List<Move> stored, BlindChoices choices) {
    List<Reading> readings = new ArrayList<Reading>();
    for (int i = 0; i < stored.size(); i++) {
      Move move = stored.get(i);
      String notation = move.getNotation();
      if (SolveMovesFormat.isRotation(notation)) {
        Move face = SolveSolution.wideFace(stored, i);
        if (face != null && Wides.forFaceAndSpin(face.getNotation(), notation) != null) {
          // A peek is a rule of thumb about a rock that came back, not something the gyro said.
          readings.add(new Reading(move.getOffsetMs(), Reading.WIDE,
              choices.believesWide(move.getOffsetMs()) ? 2 : 0));
          i++; // the face is spoken for: it is half of the wide
        }
        continue;
      }
      if (SolveSolution.sliceCoreSpin(stored, i) != null) {
        // A spin the gyro reported and two opposite faces corroborate: the strongest there is.
        readings.add(new Reading(move.getOffsetMs(), Reading.SLICE, 3));
        i += 2;
        continue;
      }
      int far = SolveSolution.nextFace(stored, i + 1);
      if (far > i && Slices.forPair(notation, stored.get(far).getNotation()) != null) {
        // No rock seen, which is also what the gyro reports when it missed one.
        readings.add(new Reading(move.getOffsetMs(), Reading.PAIR, 1));
      }
    }
    return readings;
  }

  /** Every choice of {@code size} readings out of {@code count}, in index order. */
  private static List<int[]> combinations(int count, int size) {
    List<int[]> combinations = new ArrayList<int[]>();
    int[] indices = new int[size];
    for (int i = 0; i < size; i++) {
      indices[i] = i;
    }
    while (size > 0 && indices[size - 1] < count) {
      combinations.add(indices.clone());
      int at = size - 1;
      while (at >= 0 && indices[at] == count - size + at) {
        at--;
      }
      if (at < 0) {
        break;
      }
      indices[at]++;
      for (int i = at + 1; i < size; i++) {
        indices[i] = indices[i - 1] + 1;
      }
    }
    return combinations;
  }

  /** One reading of the stream that could have gone either way, and where the solve made it. */
  private static final class Reading {

    private static final int WIDE = 0, PAIR = 1, SLICE = 2;

    private final long atMs;
    private final int kind;
    private final int vouching; // how much the stream's own answer for it is worth

    private Reading(long atMs, int kind, int vouching) {
      this.atMs = atMs;
      this.kind = kind;
      this.vouching = vouching;
    }

    private BlindChoices invertedIn(BlindChoices choices) {
      switch (kind) {
        case WIDE: return choices.withWideInverted(atMs);
        case PAIR: return choices.withPairInverted(atMs);
        default: return choices.withSliceInverted(atMs);
      }
    }
  }

  /** One algorithm of the breakdown: the slots its name says, and the readings inside it. */
  private static final class Named {

    private final int step;
    private final int part;
    private final int[] slots;
    private final List<Reading> readings;

    private Named(int step, int part, int[] slots, List<Reading> readings) {
      this.step = step;
      this.part = part;
      this.slots = slots;
      this.readings = readings;
    }
  }
}
