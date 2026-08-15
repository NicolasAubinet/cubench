package com.cube.nanotimer.coach;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What each step of a method typically costs, as a share of the whole solve, so a step can be called
 * slow against something other than the solver's own other steps.
 *
 * <p>Everything else in the payload is self-relative: a case against its family's mean, a drilled
 * case against a solved one, a step's looking against its turning. That finds an outlier inside one
 * history but can never say a step is slow for the level, because the history holds nobody else.
 * This table is the only outside number in the payload's reach, and adding it is what lets the step
 * layer say anything at all.
 *
 * <p>The figures are CubeSkills' idealised CFOP proportions, which are partly derived from sub-10
 * solver data rather than being opinion: cross 12%, F2L 50% excluding the cross, OLL 16.5% and PLL
 * 21.5%, the last two including recognition and AUF. They are treated as level-invariant, which the
 * sources support more strongly than expected: a measured 31.5-second solver reads 15.6/48.9/17.1/
 * 18.4 and J Perm's sub-30 targets are 13.3/53.3/13.3/20, both within a few points of a table drawn
 * from solvers three times faster. The spread between two solvers at one level is wider than the
 * spread between levels, so brackets would cost more than they buy.
 *
 * <p>Two honest limits ride with them. They are a good-solver target rather than a population mean,
 * and no public dataset of per-level splits exists to do better, WCA results being total times only.
 * {@link #VERSION} is what lets a measured table replace them once there is one to replace them
 * with.
 */
public final class StepBaseline {

  /** Bumped whenever the shares move, so a plan can record which table it was read against. */
  public static final int VERSION = 1;

  private static final String CFOP = "cfop";

  private static final Map<String, Map<String, Double>> SHARES;

  static {
    Map<String, Double> cfop = new LinkedHashMap<String, Double>();
    cfop.put("cross", Double.valueOf(0.12));
    cfop.put("f2l", Double.valueOf(0.50));
    cfop.put("oll", Double.valueOf(0.165));
    cfop.put("pll", Double.valueOf(0.215));
    Map<String, Map<String, Double>> shares = new LinkedHashMap<String, Map<String, Double>>();
    shares.put(CFOP, Collections.unmodifiableMap(cfop));
    SHARES = Collections.unmodifiableMap(shares);
  }

  private StepBaseline() {
  }

  /**
   * Whether a method has been researched at all. Only CFOP has, so a Roux or a blind solve is left
   * alone rather than measured against somebody else's steps.
   */
  public static boolean has(String method) {
    return method != null && SHARES.containsKey(method);
  }

  /**
   * The shares a method's steps should take, renormalized over the ones asked for.
   *
   * <p>Dropping a step from the comparison has to drop it from the baseline too. A solver two-looking
   * the last layer spends far more than 16.5% on OLL, which makes every other step's share of the
   * whole look better than it is, and comparing what is left against un-renormalized figures would
   * hide a step that really is slow.
   *
   * @param method the method the solves were read as, "cfop"
   * @param families the steps being compared; ones the table does not know are ignored
   * @return each named step's share of the steps named, or empty when there is nothing to compare
   */
  public static Map<String, Double> shares(String method, Collection<String> families) {
    Map<String, Double> table = SHARES.get(method);
    if (table == null) {
      return Collections.emptyMap();
    }
    Map<String, Double> kept = new LinkedHashMap<String, Double>();
    double total = 0;
    for (String family : families) {
      Double share = table.get(family);
      if (share != null) {
        kept.put(family, share);
        total += share.doubleValue();
      }
    }
    if (total <= 0) {
      return Collections.emptyMap();
    }
    Map<String, Double> shares = new LinkedHashMap<String, Double>();
    for (Map.Entry<String, Double> entry : kept.entrySet()) {
      shares.put(entry.getKey(), Double.valueOf(entry.getValue().doubleValue() / total));
    }
    return Collections.unmodifiableMap(shares);
  }
}
