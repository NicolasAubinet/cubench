package com.cube.nanotimer.coach;

import com.cube.nanotimer.session.MethodStatistics;
import com.cube.nanotimer.vo.StepStats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-occurrence step samples folded into the tallies the rest of the payload is written from, with
 * far outliers left out of the means and counted in the open.
 *
 * <p>An occurrence can be junk: the wrong algorithm was fired, the cube was knocked, the solver
 * stopped to think about something else. One of those must not move a mean, and no single cutoff
 * serves a vocabulary where 1.6s and 9s are both normal figures, so a sample is judged against its
 * own code's spread by a median-absolute-deviation rule (modified z-score, |z| &gt; 3.5).
 *
 * <p>A step thrown out as too long takes the next step of its solve with it. A step only ends when
 * its milestone is reached, so a cross with two edges swapped runs on while the solver builds
 * pairs, and the F2L after it is short by those pairs. Its parts stay: a pair made during the long
 * cross is stored as a skip, and the pairs made after it were timed honestly.
 *
 * <p>The rejection rate is kept rather than hidden, because "this case is thrown out a fifth of the
 * time" is a diagnosis in its own right and not a measurement error.
 */
public class StepTallies {

  /**
   * Below this many occurrences a code's spread is not worth measuring, so nothing is dropped.
   *
   * <p>{@link CoachPayloadBuilder#CASE_FLOOR} may never go under it: a figure quoted from fewer
   * occurrences than this is one the outlier rule never looked at, so a single knocked cube would
   * ride into the mean and take the top card.
   */
  static final int MIN_SAMPLES_TO_FILTER = 5;

  /** The modified z-score beyond which an occurrence is treated as junk (Iglewicz and Hoaglin). */
  private static final double MAX_Z = 3.5;

  private static final double MAD_TO_SIGMA = 0.6745;

  private final Map<String, StepStats> tallies = new LinkedHashMap<String, StepStats>();
  private final Map<String, Integer> rejected = new LinkedHashMap<String, Integer>();
  private final Map<String, Boolean> parts = new LinkedHashMap<String, Boolean>();

  /** @param samples in the order their codes should be listed in, first occurrence first */
  public StepTallies(List<StepSample> samples) {
    Map<String, List<StepSample>> byCode = new LinkedHashMap<String, List<StepSample>>();
    for (StepSample sample : samples) {
      List<StepSample> ofCode = byCode.get(sample.getCode());
      if (ofCode == null) {
        ofCode = new ArrayList<StepSample>();
        byCode.put(sample.getCode(), ofCode);
      }
      ofCode.add(sample);
      // A code ever met as a step of the method is one, as the screens always counted it.
      Boolean wasPart = parts.get(sample.getCode());
      parts.put(sample.getCode(), (wasPart == null || wasPart) && sample.isPart());
    }
    Set<StepSample> dropped = Collections.newSetFromMap(new IdentityHashMap<StepSample, Boolean>());
    List<StepSample> tooLong = new ArrayList<StepSample>();
    for (List<StepSample> ofCode : byCode.values()) {
      judge(ofCode, dropped, tooLong);
    }
    dropFollowers(samples, tooLong, dropped);
    for (Map.Entry<String, List<StepSample>> entry : byCode.entrySet()) {
      tally(entry.getKey(), entry.getValue(), dropped);
    }
  }

  /** Adds the code's outliers to {@code dropped}, and the ones over its median to {@code tooLong}. */
  private static void judge(List<StepSample> ofCode, Set<StepSample> dropped,
      List<StepSample> tooLong) {
    // A skip is a step that never happened rather than a fast one, so there is no spread to judge.
    if (ofCode.size() < MIN_SAMPLES_TO_FILTER || isSkip(ofCode.get(0).getCode())) {
      return;
    }
    double[] times = times(ofCode);
    boolean[] kept = kept(times);
    double median = median(times);
    for (int i = 0; i < kept.length; i++) {
      if (!kept[i]) {
        dropped.add(ofCode.get(i));
        if (times[i] > median) {
          tooLong.add(ofCode.get(i));
        }
      }
    }
  }

  /** Drops the step that came after each step thrown out as too long, in the same solve. */
  private static void dropFollowers(List<StepSample> samples, List<StepSample> tooLong,
      Set<StepSample> dropped) {
    Map<Long, List<StepSample>> stepsBySolve = new HashMap<Long, List<StepSample>>();
    for (StepSample sample : samples) {
      if (!sample.isPart() && sample.getStepIndex() != null) {
        List<StepSample> ofSolve = stepsBySolve.get(sample.getSolveId());
        if (ofSolve == null) {
          ofSolve = new ArrayList<StepSample>();
          stepsBySolve.put(sample.getSolveId(), ofSolve);
        }
        ofSolve.add(sample);
      }
    }
    for (StepSample longOne : tooLong) {
      if (longOne.isPart() || longOne.getStepIndex() == null) {
        continue;
      }
      StepSample next = null;
      for (StepSample step : stepsBySolve.get(longOne.getSolveId())) {
        int index = step.getStepIndex();
        if (index > longOne.getStepIndex() && (next == null || index < next.getStepIndex())) {
          next = step;
        }
      }
      if (next != null && !isSkip(next.getCode())) {
        dropped.add(next);
      }
    }
  }

  private static boolean isSkip(String code) {
    return MethodStatistics.SKIP.equals(MethodStatistics.caseOf(code));
  }

  private void tally(String code, List<StepSample> ofCode, Set<StepSample> droppedSamples) {
    int count = 0;
    int dropped = 0;
    long totalMs = 0;
    long recognitionMs = 0;
    long bestMs = Long.MAX_VALUE;
    long worstMs = 0;
    double sumOfSquares = 0;
    for (StepSample sample : ofCode) {
      if (droppedSamples.contains(sample)) {
        dropped++;
        continue;
      }
      count++;
      totalMs += sample.getTimeMs();
      recognitionMs += sample.getRecognitionMs();
      // An occurrence that took no time came free rather than fast, so it is nobody's best.
      if (sample.getTimeMs() > 0) {
        bestMs = Math.min(bestMs, sample.getTimeMs());
      }
      worstMs = Math.max(worstMs, sample.getTimeMs());
      sumOfSquares += (double) sample.getTimeMs() * sample.getTimeMs();
    }
    if (bestMs == Long.MAX_VALUE) {
      bestMs = 0; // nothing but free occurrences: there is no best to report
    }
    tallies.put(code, count == 0 ? new StepStats(code, 0, 0, 0, 0, 0, 0)
        : new StepStats(code, count, totalMs, recognitionMs, bestMs, worstMs, sumOfSquares));
    rejected.put(code, dropped);
  }

  /**
   * Which of one code's occurrences the rule keeps, judged by their times: every one of them when
   * there are too few to measure a spread, or no spread to measure.
   */
  static boolean[] kept(double[] times) {
    boolean[] kept = new boolean[times.length];
    Arrays.fill(kept, true);
    if (times.length < MIN_SAMPLES_TO_FILTER) {
      return kept;
    }
    double median = median(times);
    double limit = outlierLimit(times, median);
    for (int i = 0; limit >= 0 && i < times.length; i++) {
      kept[i] = Math.abs(times[i] - median) <= limit;
    }
    return kept;
  }

  /** How far from the median an occurrence may sit, or -1 when the spread cannot be measured. */
  private static double outlierLimit(double[] times, double median) {
    double[] deviations = new double[times.length];
    for (int i = 0; i < times.length; i++) {
      deviations[i] = Math.abs(times[i] - median);
    }
    double mad = median(deviations);
    return mad == 0 ? -1 : MAX_Z * mad / MAD_TO_SIGMA;
  }

  private static double[] times(List<StepSample> ofCode) {
    double[] times = new double[ofCode.size()];
    for (int i = 0; i < times.length; i++) {
      times[i] = ofCode.get(i).getTimeMs();
    }
    return times;
  }

  private static double median(double[] values) {
    double[] sorted = values.clone();
    Arrays.sort(sorted);
    int middle = sorted.length / 2;
    return sorted.length % 2 == 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
  }

  /** The method's own steps, and their cases, outliers dropped. */
  public List<StepStats> getSteps() {
    return of(false);
  }

  /** The codes a step was built in parts under: an F2L slot, a look of a 2-look OLL, a PLL algorithm. */
  public List<StepStats> getParts() {
    return of(true);
  }

  private List<StepStats> of(boolean part) {
    List<StepStats> stats = new ArrayList<StepStats>();
    for (Map.Entry<String, StepStats> entry : tallies.entrySet()) {
      if (Boolean.valueOf(part).equals(parts.get(entry.getKey()))) {
        stats.add(entry.getValue());
      }
    }
    return stats;
  }

  /** What a code's figures are read from, once the junk is out. */
  public StepStats get(String code) {
    return tallies.get(code);
  }

  /**
   * How often a code's occurrences were thrown out, from 0 to 1. A family is asked for the rate over
   * every code under it, since its own figures are its cases put together.
   */
  public double getRejectionRate(String code) {
    int dropped = 0;
    int seen = 0;
    for (Map.Entry<String, Integer> entry : rejected.entrySet()) {
      if (!matches(entry.getKey(), code)) {
        continue;
      }
      dropped += entry.getValue().intValue();
      seen += entry.getValue().intValue() + tallies.get(entry.getKey()).getCount();
    }
    return seen == 0 ? 0 : (double) dropped / seen;
  }

  private static boolean matches(String code, String asked) {
    return MethodStatistics.caseOf(asked) == null
        ? asked.equals(MethodStatistics.familyOf(code))
        : asked.equals(code);
  }
}
