package com.cube.nanotimer.smartcube.drill;

import com.cube.nanotimer.drill.DrillSpec;
import com.cube.nanotimer.smartcube.step.CaseAlgorithms;
import com.cube.nanotimer.smartcube.step.F2LPairScrambles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What an F2L pair drill deals each case by, and which cases it opens on: how often the case comes
 * up ({@link F2LPairScrambles#placements}), leaned towards the basic pairs the solver's own solves
 * rank as costing them most.
 *
 * <p>The rank (owner, 2026-11-22) puts three signals on one scale, each a z-score across the
 * solver's basic pairs: how often a pair had moves messed up and taken back, its time over what a
 * pair of its length takes this solver (a straight line fitted through all their basic pairs), and,
 * at half weight, how often it had a stop inside it. Each is pulled toward the solver's average by
 * {@link #PULL} sightings, and a pair seen fewer than {@link #MIN_SIGHTINGS} times is not ranked.
 * Which algorithm a pair went in with never counts, however long: an own way is not a weakness.
 *
 * <p>Measured on the 2026-11-14 sample (361 solves), the rank agrees with itself between random
 * halves of the solves at r 0.47. Raw time does not rank (it tracks algorithm length, r 0.71), and
 * stuck pairs do not rank from solves at all (halves agree at chance), so they are left to the
 * drill's targets.
 */
public final class PairWeights {

  /** How much more often a pair ranked over average is dealt. */
  static final int LEAN = 3;
  static final int MIN_SIGHTINGS = 6;
  /** Sightings' worth of the solver's average each pair's figures start from. */
  static final double PULL = 5;
  static final double STOP_WEIGHT = 0.5;

  private PairWeights() {
  }

  /**
   * @param sightings the pairs of the solver's recent solves, or null for none: then frequency
   *     alone, in the spec's order
   * @return each case's weight, iterating in the order a working set opens on: the pairs ranked
   *     over average worst first, then the rest in the spec's order
   */
  public static Map<String, Long> of(DrillSpec spec, List<PairSighting> sightings) {
    Map<String, Double> scores = scores(sightings);
    List<String> leaned = new ArrayList<String>();
    for (String code : spec.getCases()) {
      Double score = scores.get(CaseAlgorithms.frontCode(code));
      if (score != null && score > 0) {
        leaned.add(code);
      }
    }
    Collections.sort(leaned, (a, b) -> Double.compare(scores.get(CaseAlgorithms.frontCode(b)),
        scores.get(CaseAlgorithms.frontCode(a))));
    Map<String, Long> weights = new LinkedHashMap<String, Long>();
    for (String code : leaned) {
      weights.put(code, LEAN * placements(code));
    }
    for (String code : spec.getCases()) {
      if (!weights.containsKey(code)) {
        weights.put(code, placements(code));
      }
    }
    return weights;
  }

  /**
   * The cases in the order a working set opens on them, as {@link #of} lists its weights; cases it
   * does not list keep their order, after the rest.
   */
  public static List<String> openingOrder(List<String> cases, Map<String, Long> weights) {
    if (weights == null) {
      return new ArrayList<String>(cases);
    }
    final Map<String, Integer> place = new HashMap<String, Integer>();
    for (String code : weights.keySet()) {
      place.put(code, place.size());
    }
    List<String> sorted = new ArrayList<String>(cases);
    Collections.sort(sorted, new Comparator<String>() {
      @Override
      public int compare(String a, String b) {
        return Integer.compare(placeOf(place, a), placeOf(place, b));
      }
    });
    return sorted;
  }

  /**
   * Each ranked basic pair's score, by its front-right code: 0 is the solver's average pair, and
   * higher costs them more.
   */
  static Map<String, Double> scores(List<PairSighting> sightings) {
    Map<String, List<PairSighting>> byCase = new LinkedHashMap<String, List<PairSighting>>();
    List<PairSighting> pool = new ArrayList<PairSighting>();
    for (PairSighting sighting : sightings == null ? Collections.<PairSighting>emptyList()
        : sightings) {
      String code = sighting.getCaseCode() == null ? null
          : CaseAlgorithms.frontCode(sighting.getCaseCode());
      if (code != null && CaseAlgorithms.isBasicPair(code)) {
        List<PairSighting> list = byCase.get(code);
        if (list == null) {
          list = new ArrayList<PairSighting>();
          byCase.put(code, list);
        }
        list.add(sighting);
        pool.add(sighting);
      }
    }
    Map<String, Double> scores = new HashMap<String, Double>();
    if (byCase.size() < 2) {
      return scores; // no spread to rank by
    }
    double[] line = lengthLine(pool);
    double undoneRate = rate(pool, true);
    double stopRate = rate(pool, false);
    Map<String, double[]> figures = new HashMap<String, double[]>();
    for (Map.Entry<String, List<PairSighting>> entry : byCase.entrySet()) {
      List<PairSighting> list = entry.getValue();
      figures.put(entry.getKey(), new double[] {pulled(list, true, undoneRate),
          excess(list, line), pulled(list, false, stopRate)});
    }
    double[] mean = new double[3];
    double[] spread = new double[3];
    for (int i = 0; i < 3; i++) {
      double sum = 0;
      double squares = 0;
      for (double[] figure : figures.values()) {
        sum += figure[i];
        squares += figure[i] * figure[i];
      }
      mean[i] = sum / figures.size();
      spread[i] = Math.sqrt(Math.max(1e-12, squares / figures.size() - mean[i] * mean[i]));
    }
    for (Map.Entry<String, double[]> entry : figures.entrySet()) {
      if (byCase.get(entry.getKey()).size() < MIN_SIGHTINGS) {
        continue;
      }
      double[] figure = entry.getValue();
      scores.put(entry.getKey(), ((figure[0] - mean[0]) / spread[0]
          + (figure[1] - mean[1]) / spread[1]
          + STOP_WEIGHT * (figure[2] - mean[2]) / spread[2]) / (2 + STOP_WEIGHT));
    }
    return scores;
  }

  /** Intercept and slope of the solver's pair time against the pair's usual length. */
  private static double[] lengthLine(List<PairSighting> pool) {
    double sx = 0;
    double sy = 0;
    double sxx = 0;
    double sxy = 0;
    int n = 0;
    for (PairSighting sighting : pool) {
      if (sighting.getUsualMoves() > 0) {
        double x = sighting.getUsualMoves();
        sx += x;
        sy += sighting.getTimeMs();
        sxx += x * x;
        sxy += x * sighting.getTimeMs();
        n++;
      }
    }
    double denominator = n * sxx - sx * sx;
    if (n == 0 || denominator == 0) {
      return new double[] {n == 0 ? 1 : sy / n, 0}; // one length only: its mean
    }
    double slope = (n * sxy - sx * sy) / denominator;
    return new double[] {(sy - slope * sx) / n, slope};
  }

  /** The pair's mean time over the line's for its length, pulled toward 1. */
  private static double excess(List<PairSighting> list, double[] line) {
    double sum = 0;
    int n = 0;
    for (PairSighting sighting : list) {
      double expected = line[0] + line[1] * sighting.getUsualMoves();
      if (sighting.getUsualMoves() > 0 && expected > 0) {
        sum += sighting.getTimeMs() / expected;
        n++;
      }
    }
    return (sum + PULL) / (n + PULL);
  }

  private static double rate(List<PairSighting> list, boolean undone) {
    int hits = 0;
    for (PairSighting sighting : list) {
      hits += (undone ? sighting.isUndone() : sighting.isStopped()) ? 1 : 0;
    }
    return list.isEmpty() ? 0 : hits / (double) list.size();
  }

  private static double pulled(List<PairSighting> list, boolean undone, double average) {
    return (rate(list, undone) * list.size() + PULL * average) / (list.size() + PULL);
  }

  private static long placements(String code) {
    return Math.max(1, F2LPairScrambles.placements(code));
  }

  private static int placeOf(Map<String, Integer> place, String code) {
    Integer at = place.get(code);
    return at == null ? Integer.MAX_VALUE : at;
  }
}
