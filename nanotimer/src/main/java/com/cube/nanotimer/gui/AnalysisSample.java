package com.cube.nanotimer.gui;

import com.cube.nanotimer.session.CaseKnowledge;
import com.cube.nanotimer.session.MethodStatistics;
import com.cube.nanotimer.smartcube.step.LastLayerScrambles;
import com.cube.nanotimer.vo.StepStats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A hundred invented solves: what the hub reads for a reader who has none of their own.
 *
 * <p>The solver behind them is made up and is never named on screen. A name was tried and dropped:
 * it prevented nothing the plain word "example" does not, and left the reader asking who the person
 * was, which is a worse question than the one it was meant to answer. The figures sit at around
 * fifteen seconds, fast enough to want and slow enough to recognise, and belong to no real cuber
 * whose averages anybody could go and check. <b>The one arrangement never allowed is these figures
 * on a screen that also shows the reader's</b>, which is why the example stands in only for a
 * reader no cube has ever read a solve for. See {@link AnalysisActivity} for that condition.
 *
 * <p><b>The figures have to survive the arithmetic the screens do to them.</b> The steps are not
 * four numbers on a card: the hero mean is the sum of the step means, the hero best the sum of the
 * step bests, the legend's shares are those means over that sum, the delta bars are those shares
 * against {@link com.cube.nanotimer.coach.StepBaseline} and so cancel to zero, and each of OLL and
 * PLL is the sum of its own cases rather than a figure of its own. So the tables below are written
 * as occurrences and totals and everything else is derived, the same way a real tally is, and
 * {@code AnalysisSampleTest} holds them to it.
 *
 * <p>The history is shaped like a real one rather than like a demonstration. Forty-two of the 57
 * orientations came up in a hundred solves and five of them reached the five occurrences a cost is
 * quoted at, so most of the OLL table says N/A in its last column, which is exactly what the
 * reader's own first hundred solves will say.
 */
public final class AnalysisSample {

  /** How many solves the example is read over, which the banner and the hero line both print. */
  public static final int SOLVES = 100;

  /** The steps solved, which is CFOP: the example is a CFOP solver whatever the reader is. */
  private static final String CROSS = "cross";
  private static final String F2L = "f2l";

  /**
   * How far a case swings around its own mean, as a share of it. One constant rather than a column,
   * because no screen shows a case's spread: what it feeds is the step table's spread for OLL and
   * PLL, which is a step's own cases put together.
   */
  private static final double CASE_SPREAD = 0.22;

  /** Cases not put in with one algorithm, and how far along each of those stands. */
  private static final Map<String, CaseKnowledge.Status> STANDINGS = standings();

  /** Cases nothing is known about either way, which every set has some of. */
  private static final List<String> SILENT = Arrays.asList("oll_56", "oll_57");

  private AnalysisSample() {
  }

  /**
   * The example window, in the shape the hub's own query returns.
   *
   * <p>OLL and PLL are handed over as cases and never as a step of their own: a code carrying a case
   * already counts into its family, so a bare {@code "oll"} row beside them would double it.
   */
  public static MethodStatistics statistics() {
    List<StepStats> steps = new ArrayList<StepStats>();
    // The cross is timed from the solve's own first move, so it has nowhere to put recognition.
    steps.add(step(CROSS, SOLVES, 1780, 0, 980, 3120, 420));
    steps.add(step(F2L, SOLVES, 7550, 2760, 5810, 12400, 1350));
    steps.addAll(orientations());
    steps.addAll(permutations());
    return new MethodStatistics(steps, SOLVES);
  }

  /**
   * Where the solver stands on every case of both sets, which the Cases tab needs as much as the
   * figures: without it the ring reads nothing known and the whole set falls under "not yet seen",
   * which is a lie about a solver whose numbers say they know nearly all of it.
   *
   * <p>Read off {@link LastLayerScrambles#cases()} rather than listed, so the ring's denominator and
   * the groups under the table cannot drift apart from the set the app actually draws.
   */
  public static List<CaseKnowledge> knowledge() {
    List<CaseKnowledge> known = new ArrayList<CaseKnowledge>();
    for (String code : LastLayerScrambles.cases()) {
      if (SILENT.contains(code)) {
        continue; // no row at all: absence is never evidence
      }
      CaseKnowledge.Status standing = STANDINGS.get(code);
      known.add(new CaseKnowledge(MethodStatistics.familyOf(code), MethodStatistics.caseOf(code),
          standing == null ? CaseKnowledge.Status.KNOWN : standing, 1, 0));
    }
    return known;
  }

  private static Map<String, CaseKnowledge.Status> standings() {
    Map<String, CaseKnowledge.Status> standings =
        new LinkedHashMap<String, CaseKnowledge.Status>();
    // Two still taken in two looks and two never met, which is where a full OLL stops.
    for (String code : new String[] {"oll_18", "oll_19", "oll_41", "oll_42"}) {
      standings.put(code, CaseKnowledge.Status.TO_LEARN);
    }
    // Learnt and slipping: three of these are the orientations the table also ranks as costly.
    for (String code : new String[] {"oll_1", "oll_3", "oll_47", "oll_50", "oll_53", "pll_na"}) {
      standings.put(code, CaseKnowledge.Status.NEEDS_REVIEW);
    }
    return Collections.unmodifiableMap(standings);
  }

  /** One step of the method, written as a tally rather than as the means it is read as. */
  private static StepStats step(String code, int count, long meanMs, long recognitionMs, long bestMs,
      long worstMs, long stdDevMs) {
    double sumOfSquares = count * ((double) meanMs * meanMs + (double) stdDevMs * stdDevMs);
    return new StepStats(code, count, meanMs * count, recognitionMs * count, bestMs, worstMs,
        sumOfSquares);
  }

  /** One case of a step. Its spread is the one thing not written down, since nothing shows it. */
  private static StepStats stepCase(String code, int count, long meanMs, long recognitionMs,
      long bestMs) {
    long stdDevMs = Math.round(CASE_SPREAD * meanMs);
    return step(code, count, meanMs, recognitionMs, bestMs, meanMs + 2 * stdDevMs, stdDevMs);
  }

  /** Forty-two of the 57 orientations, 100 occurrences, and five of them worth a cost. */
  private static List<StepStats> orientations() {
    return Arrays.asList(
        stepCase("oll_2", 6, 2980, 900, 2320),
        stepCase("oll_21", 6, 2050, 620, 1700),
        stepCase("oll_27", 5, 1890, 540, 1400),
        stepCase("oll_1", 5, 3400, 1010, 2920),
        stepCase("oll_45", 5, 1930, 560, 1540),
        stepCase("oll_22", 4, 2180, 660, 1660),
        stepCase("oll_23", 4, 2040, 610, 1800),
        stepCase("oll_26", 4, 1960, 570, 1590),
        stepCase("oll_33", 4, 2210, 690, 1720),
        stepCase("oll_3", 3, 3240, 980, 2690),
        stepCase("oll_4", 3, 3040, 950, 2250),
        stepCase("oll_7", 3, 2360, 720, 2030),
        stepCase("oll_8", 3, 2410, 740, 1930),
        stepCase("oll_28", 3, 2270, 700, 1730),
        stepCase("oll_43", 3, 2330, 710, 2050),
        stepCase("oll_44", 3, 2190, 670, 1770),
        stepCase("oll_5", 2, 2620, 800, 2040),
        stepCase("oll_6", 2, 2580, 790, 2140),
        stepCase("oll_9", 2, 2480, 760, 1840),
        stepCase("oll_10", 2, 2540, 780, 2180),
        stepCase("oll_11", 2, 2690, 830, 2150),
        stepCase("oll_12", 2, 2710, 840, 2060),
        stepCase("oll_24", 2, 2120, 640, 1870),
        stepCase("oll_25", 2, 2260, 690, 1830),
        stepCase("oll_31", 2, 2300, 700, 1790),
        stepCase("oll_32", 2, 2340, 710, 1940),
        stepCase("oll_13", 1, 2920, 880, 2160),
        stepCase("oll_14", 1, 2820, 860, 2430),
        // The two that are two-looked, which is why they run twice the length of the rest.
        stepCase("oll_18", 1, 5300, 2010, 4240),
        stepCase("oll_19", 1, 5540, 2120, 4210),
        stepCase("oll_29", 1, 2640, 810, 2320),
        stepCase("oll_30", 1, 2700, 830, 2190),
        stepCase("oll_34", 1, 2450, 750, 1910),
        stepCase("oll_35", 1, 2390, 730, 1980),
        stepCase("oll_36", 1, 2530, 770, 1870),
        stepCase("oll_37", 1, 2280, 690, 1960),
        stepCase("oll_38", 1, 2560, 780, 2050),
        stepCase("oll_39", 1, 2740, 840, 2080),
        stepCase("oll_40", 1, 2660, 810, 2340),
        stepCase("oll_47", 1, 3310, 990, 2680),
        stepCase("oll_48", 1, 3020, 930, 2360),
        stepCase("oll_53", 1, 3500, 1040, 2900));
  }

  /** Nineteen of the 21 permutations, 100 occurrences, and the G perms costing the most. */
  private static List<StepStats> permutations() {
    return Arrays.asList(
        stepCase("pll_ua", 8, 2240, 470, 1750),
        stepCase("pll_ub", 8, 2210, 460, 1830),
        stepCase("pll_t", 8, 2480, 530, 1840),
        stepCase("pll_ja", 7, 2530, 540, 2180),
        stepCase("pll_jb", 6, 2460, 520, 1970),
        stepCase("pll_aa", 7, 2570, 560, 1950),
        stepCase("pll_ab", 6, 2610, 570, 2300),
        stepCase("pll_y", 5, 3040, 700, 2460),
        stepCase("pll_f", 5, 3380, 790, 2640),
        stepCase("pll_ra", 5, 3120, 720, 2590),
        stepCase("pll_rb", 5, 3170, 730, 2350),
        stepCase("pll_ga", 5, 3810, 890, 3280),
        stepCase("pll_gb", 5, 3440, 800, 2750),
        stepCase("pll_gc", 4, 3490, 820, 2650),
        stepCase("pll_gd", 4, 3790, 880, 3340),
        stepCase("pll_v", 4, 3300, 770, 2670),
        stepCase("pll_na", 3, 4300, 1020, 3350),
        stepCase("pll_nb", 3, 4200, 1000, 3490),
        stepCase("pll_z", 2, 2910, 660, 2150));
  }
}
