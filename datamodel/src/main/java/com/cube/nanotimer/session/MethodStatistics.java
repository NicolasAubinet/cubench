package com.cube.nanotimer.session;

import com.cube.nanotimer.vo.StepStats;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a solve type's method has been costing over a stretch of solves, read from the step codes the
 * breakdown stores, so the same rows answer both "how is my PLL" and "how is my Gb perm".
 *
 * <p>A skipped step is not a fast case but a case that never happened, so skips are counted on their
 * own rather than into a mean they would flatter. And with 57 OLLs and 21 PLLs, a window that gives a
 * solid figure per family gives a handful of reps per case: every figure carries its count, and
 * {@link #getWorstCases} takes the floor to hold it to.
 */
public class MethodStatistics implements Serializable {

  /** The case a step is under when the step was already solved on arrival. */
  public static final String SKIP = "skip";

  private final int solveCount;
  private final Map<String, StepStats> families = new LinkedHashMap<String, StepStats>();
  private final Map<String, List<StepStats>> cases = new LinkedHashMap<String, List<StepStats>>();
  private final Map<String, StepStats> skips = new LinkedHashMap<String, StepStats>();

  /**
   * @param steps one tally per step code, in the order the steps are solved in
   * @param solveCount how many solves those tallies were read from
   */
  public MethodStatistics(List<StepStats> steps, int solveCount) {
    this.solveCount = solveCount;
    for (StepStats step : steps) {
      String family = familyOf(step.getCode());
      String caseName = caseOf(step.getCode());
      if (SKIP.equals(caseName)) {
        skips.put(family, StepStats.merge(family, skips.get(family), step));
        if (!cases.containsKey(family)) { // so a family that only ever skipped is still known
          cases.put(family, new ArrayList<StepStats>());
        }
        continue;
      }
      families.put(family, StepStats.merge(family, families.get(family), step));
      if (!cases.containsKey(family)) {
        cases.put(family, new ArrayList<StepStats>());
      }
      if (caseName != null) {
        cases.get(family).add(step);
      }
    }
  }

  /** The family a step code belongs to: everything up to its case, or the whole code when it has none. */
  public static String familyOf(String code) {
    int separator = code.indexOf('_');
    return separator < 0 ? code : code.substring(0, separator);
  }

  /** The case a step code names, or null when the code is a family on its own. */
  public static String caseOf(String code) {
    int separator = code.indexOf('_');
    return separator < 0 ? null : code.substring(separator + 1);
  }

  public int getSolveCount() {
    return solveCount;
  }

  /** Each family the window holds, in solving order, skips left out of the figures. */
  public List<StepStats> getFamilies() {
    return new ArrayList<StepStats>(families.values());
  }

  /** One family's figures, or null when the window holds none of it. */
  public StepStats getFamily(String family) {
    return families.get(family);
  }

  /** The cases of a family, slowest mean first. Empty when the family is not split into cases. */
  public List<StepStats> getCases(String family) {
    List<StepStats> familyCases = cases.get(family);
    if (familyCases == null) {
      return new ArrayList<StepStats>();
    }
    List<StepStats> sorted = new ArrayList<StepStats>(familyCases);
    Collections.sort(sorted, new Comparator<StepStats>() {
      @Override
      public int compare(StepStats a, StepStats b) {
        return Long.compare(b.getMeanMs(), a.getMeanMs());
      }
    });
    return sorted;
  }

  /** How often the family was already solved on arrival, against how often it was reached at all. */
  public double getSkipRate(String family) {
    StepStats skipped = skips.get(family);
    StepStats solved = families.get(family);
    int skippedCount = skipped == null ? 0 : skipped.getCount();
    int reached = skippedCount + (solved == null ? 0 : solved.getCount());
    return reached == 0 ? 0 : (double) skippedCount / reached;
  }

  /**
   * What a case cost over what its family usually costs: how far above the family's mean it runs,
   * times how often it came up. The slowest case there is may be worth nothing to fix.
   *
   * @return 0 for a case at or under its family's mean, and for one the window does not hold
   */
  public long getTimeLostMs(String code) {
    StepStats family = families.get(familyOf(code));
    if (family == null) {
      return 0;
    }
    for (StepStats stepCase : cases.get(familyOf(code))) {
      if (stepCase.getCode().equals(code)) {
        long over = stepCase.getMeanMs() - family.getMeanMs();
        return over <= 0 ? 0 : over * stepCase.getCount();
      }
    }
    return 0;
  }

  /**
   * The cases of a family that cost it the most time, worst first: seen at least {@code minCount}
   * times, slower than the family's mean, and ranked by what that costs rather than by how slow.
   */
  public List<StepStats> getWorstCases(String family, int minCount) {
    List<StepStats> worst = new ArrayList<StepStats>();
    for (StepStats stepCase : getCases(family)) {
      if (stepCase.getCount() >= minCount && getTimeLostMs(stepCase.getCode()) > 0) {
        worst.add(stepCase);
      }
    }
    Collections.sort(worst, new Comparator<StepStats>() {
      @Override
      public int compare(StepStats a, StepStats b) {
        return Long.compare(getTimeLostMs(b.getCode()), getTimeLostMs(a.getCode()));
      }
    });
    return worst;
  }
}
