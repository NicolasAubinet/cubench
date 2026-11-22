package com.cube.nanotimer.smartcube.drill;

import com.cube.nanotimer.drill.DrillSpec;
import com.cube.nanotimer.session.MethodStatistics;
import com.cube.nanotimer.smartcube.step.CaseAlgorithms;
import com.cube.nanotimer.smartcube.step.F2LPairScrambles;
import com.cube.nanotimer.vo.StepStats;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What an F2L pair drill deals each case by: how often the case comes up
 * ({@link F2LPairScrambles#placements}), raised for a case that ran over its target in the drill
 * in their recent solves, or over the solver's basic-pair mean where the drill sets no target.
 *
 * <p>Per-case F2L times are too thin to rank on (a case is met a few times in a hundred solves), so
 * a case's own figure only leans the draw towards it; the drill's targets then measure it properly.
 * Leaning on each case's own target keeps a drill of basic and stuck pairs from dealing the stuck
 * ones first just for being slower (owner, 2026-10-18).
 */
public final class PairWeights {

  /** How much more often a case recently over its bar is dealt. */
  static final int LEAN = 3;

  private PairWeights() {
  }

  /**
   * @param statistics the solver's recent solves, or null for none: then frequency alone
   */
  public static Map<String, Long> of(DrillSpec spec, MethodStatistics statistics) {
    List<StepStats> pairs = statistics == null ? null : statistics.getCases("pair");
    Long basicMean = basicMean(pairs);
    Map<String, Long> weights = new LinkedHashMap<String, Long>();
    for (String code : spec.getCases()) {
      long weight = Math.max(1, F2LPairScrambles.placements(code));
      long target = spec.getTargetMs(code);
      Long bar = target > 0 ? Long.valueOf(target) : basicMean;
      StepStats stats = pairs == null || bar == null ? null : named(pairs, code);
      if (stats != null && stats.getCount() > 0 && stats.getMeanMs() > bar) {
        weight *= LEAN;
      }
      weights.put(code, weight);
    }
    return weights;
  }

  /**
   * The cases heaviest first, ties in the order given: what a drill with a working set opens on, so
   * a card showing its opening pairs shows the ones the drill will deal.
   */
  public static List<String> heaviestFirst(List<String> cases, final Map<String, Long> weights) {
    List<String> sorted = new ArrayList<String>(cases);
    if (weights != null) {
      Collections.sort(sorted, new Comparator<String>() {
        @Override
        public int compare(String a, String b) {
          return Long.compare(weightOf(weights, b), weightOf(weights, a));
        }
      });
    }
    return sorted;
  }

  static long weightOf(Map<String, Long> weights, String code) {
    Long weight = weights == null ? null : weights.get(code);
    return weight == null || weight <= 0 ? 1 : weight;
  }

  /** The solver's mean over every basic pair, or null where there were none. */
  static Long basicMean(List<StepStats> pairs) {
    if (pairs == null) {
      return null;
    }
    long total = 0;
    int count = 0;
    for (StepStats stats : pairs) {
      if (CaseAlgorithms.isBasicPair(stats.getCode())) {
        total += stats.getMeanMs() * stats.getCount();
        count += stats.getCount();
      }
    }
    return count == 0 ? null : Long.valueOf(total / count);
  }

  private static StepStats named(List<StepStats> pairs, String code) {
    for (StepStats stats : pairs) {
      if (code.equals(stats.getCode())) {
        return stats;
      }
    }
    return null;
  }
}
