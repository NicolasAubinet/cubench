package com.cube.nanotimer.coach;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A solver's steps put beside what {@link StepBaseline} says they should cost, each as a share of
 * the whole solve, so the two can be subtracted.
 *
 * <p>It is the arithmetic on its own, with nothing said about it: the screen draws the two numbers
 * and a coach decides whether either is worth a sentence.
 *
 * <p>Time is summed as mean times count rather than taken from the solve figure, which is what makes
 * a skipped OLL cost nothing instead of averaging in as a zero.
 */
public final class StepShares {
  private final Map<String, Double> actual;
  private final Map<String, Double> expected;

  private StepShares(Map<String, Double> actual, Map<String, Double> expected) {
    this.actual = Collections.unmodifiableMap(actual);
    this.expected = Collections.unmodifiableMap(expected);
  }

  /**
   * Every step of a payload, and its two shares.
   *
   * <p>One denominator, the whole solve, for every step including a last layer taken in two looks.
   * A step's share of the solve is what it is: a solver whose OLL is half again what it should be
   * does spend proportionally less of the solve on the others, and a panel saying so is pointing at
   * the step that is actually out of line. Nothing is renormalized and nothing is set aside.
   *
   * @return the comparison, empty when the method has no baseline or has fewer than two of its steps
   */
  public static StepShares of(CoachPayload payload) {
    Map<String, Double> none = Collections.emptyMap();
    if (payload == null || !StepBaseline.has(payload.getMethod())) {
      return new StepShares(none, none);
    }
    Map<String, Long> spent = new LinkedHashMap<String, Long>();
    for (StepFigure family : payload.getFamilies()) {
      long time = family.getMeanMs() * family.getCount();
      if (time > 0) {
        spent.put(family.getCode(), Long.valueOf(time));
      }
    }
    Map<String, Double> baseline = StepBaseline.shares(payload.getMethod(), spent.keySet());
    if (baseline.size() < 2) {
      return new StepShares(none, none);
    }
    // Over the steps the baseline knows, so a step it has never heard of cannot shrink the rest.
    long total = 0;
    for (String family : baseline.keySet()) {
      total += spent.get(family).longValue();
    }
    if (total <= 0) {
      return new StepShares(none, none);
    }
    Map<String, Double> actual = new LinkedHashMap<String, Double>();
    for (String family : baseline.keySet()) {
      actual.put(family, Double.valueOf(spent.get(family).doubleValue() / total));
    }
    return new StepShares(actual, baseline);
  }
  /** The steps compared, which is every step the baseline and the solver both have. */
  public Set<String> families() {
    return expected.keySet();
  }

  public boolean isEmpty() {
    return expected.isEmpty();
  }

  /** What the step actually took, as a share of the whole solve, or null if it was not compared. */
  public Double actual(String family) {
    return actual.get(family);
  }

  /** What the baseline says it should have taken, on the same denominator. */
  public Double expected(String family) {
    return expected.get(family);
  }
}
