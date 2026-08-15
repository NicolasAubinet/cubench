package com.cube.nanotimer.coach;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A solver's steps put beside what {@link StepBaseline} says they should cost, over the same set of
 * steps, so the two shares can be subtracted.
 *
 * <p>It is the arithmetic on its own, with nothing said about it: the screen draws the two numbers
 * and a coach decides whether either is worth a sentence.
 *
 * <p>Time is summed as mean times count rather than taken from the solve figure, which is what makes
 * a skipped OLL cost nothing instead of averaging in as a zero.
 */
public final class StepShares {

  /** The step whose share stops meaning anything once the last layer is taken in two looks. */
  private static final String TWO_LOOK_FAMILY = "oll";

  /** Above this share of solves taking the last layer in two looks, the last layer is not compared. */
  private static final double TWO_LOOK_SHARE = 0.25;

  private final Map<String, Double> actual;
  private final Map<String, Double> expected;
  private final String excluded;

  private StepShares(Map<String, Double> actual, Map<String, Double> expected, String excluded) {
    this.actual = Collections.unmodifiableMap(actual);
    this.expected = Collections.unmodifiableMap(expected);
    this.excluded = excluded;
  }

  /**
   * The steps of a payload worth comparing, and their two shares.
   *
   * <p>A two-looked last layer is dropped from both sides: its OLL is over any one-look baseline by
   * construction, so measuring it says nothing the two-look note does not already say better.
   *
   * @return the comparison, empty when the method has no baseline or fewer than two steps remain
   */
  public static StepShares of(CoachPayload payload) {
    Map<String, Double> none = Collections.emptyMap();
    if (payload == null || !StepBaseline.has(payload.getMethod())) {
      return new StepShares(none, none, null);
    }
    boolean twoLook = twoLooks(payload);
    String excluded = twoLook ? TWO_LOOK_FAMILY : null;
    Map<String, Long> spent = new LinkedHashMap<String, Long>();
    long total = 0;
    for (StepFigure family : payload.getFamilies()) {
      if (twoLook && TWO_LOOK_FAMILY.equals(family.getCode())) {
        continue;
      }
      long time = family.getMeanMs() * family.getCount();
      if (time > 0) {
        spent.put(family.getCode(), Long.valueOf(time));
        total += time;
      }
    }
    Map<String, Double> baseline = StepBaseline.shares(payload.getMethod(), spent.keySet());
    if (total <= 0 || baseline.size() < 2) {
      return new StepShares(none, none, null);
    }
    Map<String, Double> actual = new LinkedHashMap<String, Double>();
    for (String family : baseline.keySet()) {
      actual.put(family, Double.valueOf(spent.get(family).doubleValue() / total));
    }
    return new StepShares(actual, baseline, excluded);
  }

  /** The step left out of the comparison, or null when every step was compared. */
  public String getExcluded() {
    return excluded;
  }

  /**
   * Whether the last layer is being taken in two looks often enough to say so, which is the one
   * level difference in the splits that is real: it moves OLL and PLL together and it is a technique
   * rather than a speed.
   *
   * <p>A two-look is a solve whose OLL took more than one algorithm, which the payload counts. It
   * used to be read off the two halves an OLL was recorded in, and those are gone: an OLL is now
   * recorded as the algorithms it took, the way a PLL already was.
   */
  public static boolean twoLooks(CoachPayload payload) {
    if (payload == null || payload.getFamilyWindow() == 0) {
      return false;
    }
    return (double) payload.getTwoLookCount() / payload.getFamilyWindow() >= TWO_LOOK_SHARE;
  }

  /** The steps compared, which is not every step the solver has. */
  public Set<String> families() {
    return expected.keySet();
  }

  public boolean isEmpty() {
    return expected.isEmpty();
  }

  /** What the step actually took, as a share of the steps compared, or null if it was not one. */
  public Double actual(String family) {
    return actual.get(family);
  }

  /** What the baseline says it should have taken, over the same steps. */
  public Double expected(String family) {
    return expected.get(family);
  }
}
