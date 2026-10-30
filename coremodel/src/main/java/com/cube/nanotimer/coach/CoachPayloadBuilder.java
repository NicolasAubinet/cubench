package com.cube.nanotimer.coach;

import com.cube.nanotimer.session.CaseKnowledge;
import com.cube.nanotimer.session.MethodStatistics;
import com.cube.nanotimer.vo.StepStats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where a solver's history becomes the one thing a coach is allowed to see.
 *
 * <p>Everything that decides what may be said lives here rather than in whatever reads the payload:
 * the windows, the floors under which a figure is not sent at all, the outlier rule
 * ({@link StepTallies}), and the two figures that are cut outright because they are artefacts.
 * A reader can only select and explain what survived this.
 *
 * <p>The cuts, measured on real history in 2026-08. The cross reads a recognition of zero because
 * the step is timed from its own first move, which means unmeasured and not instant. And a case is
 * only quoted where a drill could be dealt a scramble of it, which is orientation and permutation
 * and nothing else: an F2L slot code is named relative to the cross face, so the same string means
 * different slots for a colour-neutral solver; a last layer part names the algorithm that was run
 * rather than the case that was dealt, so left in it ranks the same case twice and prescribes a
 * drill under a code nothing can deal; and another method's sub-steps name neither. That last one
 * is why this is written as what may be quoted rather than as what to drop — the payload builds
 * from whatever the breakdown holds, so the list of things that are not cases is open-ended, and
 * only the UI stands between a Roux sub-step and a card naming it. Only the per-case split is cut
 * in each: the F2L family and the algorithm families both survive as parts.
 *
 * <p>One window cannot serve both layers. Families want to be recent, so they track the level the
 * user solves at now; cases want the length, because 21 PLLs and 57 OLLs spread thin over any
 * window short enough to be current. The floor is what actually protects a case figure.
 */
public class CoachPayloadBuilder {

  /** How many solves the step figures are read from: recent enough to be the level solved at now. */
  public static final int FAMILY_WINDOW = 100;

  /** How many solves the case figures are read from: long enough for 57 OLLs to be seen at all. */
  public static final int CASE_WINDOW = 500;

  /**
   * How many of a case's latest occurrences its recent mean is read from: enough for the outlier rule
   * to work, few enough that a week of practice shows. Only the choice of cases to drill reads it.
   */
  public static final int RECENT_CASE_OCCURRENCES = 10;

  /** How many recorded drills the drill figures are read from. */
  public static final int DRILL_WINDOW = 50;

  /**
   * How many solves are replayed for the F2L move figures. The case window, and named separately
   * only because it is the one thing about them that could be guessed wrong: the payload carries an
   * F2L figure on the family window too, so which of the two a number came from has to be said
   * rather than inferred.
   *
   * <p><b>It was measured at the family window and put back.</b> A per-case habit is a rate, and
   * over 100 solves a pair case has 5 to 19 occurrences (median 9), where a rate cannot be told
   * from noise: on the owner's history case 27 read 27% off the list over 148 solves and 60% over
   * 100. {@link #CASE_FLOOR} was reasoned for a time mean with outliers thrown out, not for a rate,
   * so the length is doing the work here. What the short window was for, the cost of replaying, is
   * answered by not replaying twice.
   */
  public static final int F2L_MOVE_WINDOW = CASE_WINDOW;

  /**
   * Below this many occurrences a case is not quoted, in a solve or in a drill.
   *
   * <p><b>It cannot go lower, and the reason is not the one it looks like.</b> Measured on synthetic
   * solvers, this floor is expensive: with the set dealt evenly, no OLL is quotable at all until
   * about 230 solves, and at 100 solves the whole case layer is PLL. That looked like an argument
   * for lowering it until the outlier rule was read beside it, and
   * {@link StepTallies#MIN_SAMPLES_TO_FILTER} is also 5: under five occurrences a code's spread
   * cannot be measured, so nothing is thrown out and the mean is whatever happened, knocked cubes
   * included. A lower floor would not send thinner figures, it would send uninspected ones, and a
   * case is exactly where that bites since one bad occurrence in three is a third of the mean.
   *
   * <p>So the thinness of a young history is answered by the step layer and the two-look card,
   * which have every solve behind them, and not by quoting cases nobody has seen enough of.
   */
  public static final int CASE_FLOOR = 5;

  /** Below this many occurrences a step of the method is not quoted. */
  public static final int FAMILY_FLOOR = 10;

  /** Below this many solves nothing is said about the solve as a whole. */
  public static final int SOLVE_FLOOR = 10;

  /**
   * Below this many solves no plan is written. Chosen, not derived (owner, 2026-09-15): the floors
   * above stop refusing at ten solves, but a plan read off ten is mostly noise and still costs one of
   * the week's plans. At a full {@link #FAMILY_WINDOW} every plan reads the same number of solves, so
   * one week's plan compares cleanly with the next.
   *
   * <p>There is deliberately no floor on drills. A drill history only adds the solve-versus-drill
   * gap, which already needs both sides above {@link #CASE_FLOOR}; requiring drills to say anything
   * at all would shut the coach to everyone who has only ever solved.
   */
  public static final int PLAN_FLOOR = FAMILY_WINDOW;

  /**
   * How many solves must follow a plan before the next is told about it: fewer would set a few
   * days of cubing against the last plan's figures.
   */
  public static final int SINCE_FLOOR = 25;

  /** The code the whole solve's figures ride under, which is not a step and has no family. */
  public static final String SOLVE = "solve";

  /** The families whose codes name a case a drill can be dealt a scramble of, which is the only
   * kind worth quoting as one. An allowlist: what has to stay out is open-ended. Public because it
   * is a contract the drill runner has to honour, and that is checked where the scrambles are. */
  public static final Set<String> CASE_FAMILIES = Collections.unmodifiableSet(
      new LinkedHashSet<String>(Arrays.asList("oll", "pll")));

  /** The step timed from its own first move, so it has no recognition to report. */
  private static final String CROSS_FAMILY = "cross";

  /**
   * The part families a look at the last layer's orientation is recorded under: the algorithms the
   * OLL took, and — for solves recorded before an OLL was split that way — the edge and corner
   * orientation it used to be cut into. Two parts of either kind is a solve that looked twice, and
   * no other step of any method records a part under those names.
   */
  private static final Set<String> LOOK_FAMILIES = new LinkedHashSet<String>(
      Arrays.asList("ollalg", "edges", "corners"));

  /** The part family a PLL's algorithms are recorded under: two of them is a solve that permuted the
   * last layer in two looks. */
  private static final Set<String> PERMUTATION_LOOK_FAMILIES =
      Collections.singleton("alg");

  private static final String ORIENTATION = "oll";
  private static final String PERMUTATION = "pll";

  /** How far apart the two histories must be, against the solve side, before the gap is sent. */
  private static final double MIN_GAP = 0.1;

  private CoachPayloadBuilder() {
  }

  /**
   * @param puzzle the puzzle in a drill spec's vocabulary, "3x3"
   * @param method the method the solves were read as, "cfop"
   * @param solveTimes the whole times of the last {@link #FAMILY_WINDOW} solves
   * @param familySamples every step occurrence over that same window
   * @param caseSamples every step occurrence over the longer {@link #CASE_WINDOW}, newest solve
   *     first, so each case's recent mean is read from its latest occurrences
   * @param caseSolveCount how many solves that longer window actually held, which is the window or
   *     everything there is of it
   * @param drillSamples every drilled case rep over the last {@link #DRILL_WINDOW} drills
   * @param drillCount how many drills those reps came from
   * @param knowledge where every case with a status stands, of every method: the ones belonging to
   *     this method's own steps are picked out here
   * @param executions every F2L pair of the {@link #F2L_MOVE_WINDOW}'s solves, and every last layer
   *     step of them taken in one algorithm, replayed and set against the algorithms its case is
   *     usually taught with; empty where nothing could be replayed
   * @param f2lSolveCount how many solves that window actually held, sent beside the figures so
   *     that which window a move figure came from is said rather than inferred
   * @param turnings every step of the {@link #FAMILY_WINDOW}'s solves as it was turned, replayed
   *     like the pairs; empty where nothing could be
   * @param turningSolveCount how many solves they were replayed from
   */
  public static CoachPayload build(String puzzle, String method, List<Long> solveTimes,
      List<StepSample> familySamples, List<StepSample> caseSamples, int caseSolveCount,
      List<StepSample> drillSamples, int drillCount, List<CaseKnowledge> knowledge,
      List<PairExecution> executions, int f2lSolveCount, List<StepTurning> turnings,
      int turningSolveCount) {
    return build(puzzle, method, solveTimes, familySamples, caseSamples, caseSolveCount,
        drillSamples, drillCount, knowledge, executions, f2lSolveCount, turnings,
        turningSolveCount, null);
  }

  /**
   * As above, with the last plan and what has been solved since ({@link #previous}), or null.
   *
   * @param solveTimes newest first, so the solves since the last plan are its head
   */
  public static CoachPayload build(String puzzle, String method, List<Long> solveTimes,
      List<StepSample> familySamples, List<StepSample> caseSamples, int caseSolveCount,
      List<StepSample> drillSamples, int drillCount, List<CaseKnowledge> knowledge,
      List<PairExecution> executions, int f2lSolveCount, List<StepTurning> turnings,
      int turningSolveCount, PreviousPlan previous) {
    List<PairExecution> lastLayer = LastLayerHabits.of(executions);
    List<PairExecution> pairs = new ArrayList<PairExecution>(executions);
    pairs.removeAll(lastLayer);
    StepTallies familyTallies = new StepTallies(familySamples);
    MethodStatistics families =
        new MethodStatistics(familyTallies.getSteps(), familyTallies.getParts(), solveTimes.size());
    StepTallies caseTallies = new StepTallies(caseSamples);
    MethodStatistics cases =
        new MethodStatistics(caseTallies.getSteps(), caseTallies.getParts(), caseSolveCount);
    StepTallies recentTallies = new StepTallies(latestOfEachCase(caseSamples));
    MethodStatistics recentCases = new MethodStatistics(recentTallies.getSteps(),
        recentTallies.getParts(), caseSolveCount);
    StepTallies drillTallies = new StepTallies(drillSamples);

    List<StepFigure> caseFigures = caseFigures(cases, caseTallies, recentCases);
    List<StepFigure> drillFigures = drillFigures(drillTallies);
    Set<String> sets = familiesOf(cases);
    MoveFigure f2lMoves = F2lHabits.total(pairs, FAMILY_FLOOR);
    List<MoveFigure> llMoves = LastLayerHabits.steps(lastLayer, FAMILY_FLOOR);
    Set<Long> twoLooked = twoLookSolves(familySamples, LOOK_FAMILIES);
    Set<Long> twoPermuted = twoLookSolves(familySamples, PERMUTATION_LOOK_FAMILIES);
    return new CoachPayload(CoachPayload.VERSION, puzzle, method, solveTimes.size(),
        caseSolveCount, drillCount, Integer.valueOf(twoLooked.size()),
        solveFigure(solveTimes),
        familyFigures(families.getFamilies(), families, familyTallies),
        familyFigures(families.getParts(), families, familyTallies), caseFigures, drillFigures,
        comparisons(caseFigures, drillFigures, caseTallies, drillTallies),
        casesAt(knowledge, sets, CaseKnowledge.Status.KNOWN),
        casesAt(knowledge, sets, CaseKnowledge.Status.NEEDS_REVIEW,
            CaseKnowledge.Status.TO_LEARN),
        casesAt(knowledge, sets, CaseKnowledge.Status.TO_LEARN),
        F2lGroups.of(familySamples, FAMILY_FLOOR), f2lMoves,
        f2lMoves == null ? null : F2lHabits.cases(pairs, CASE_FLOOR),
        f2lMoves == null ? null : Integer.valueOf(f2lSolveCount),
        Turnings.of(turnings, FAMILY_FLOOR), Integer.valueOf(turningSolveCount), llMoves,
        LastLayerHabits.cases(lastLayer, CASE_FLOOR), Integer.valueOf(f2lSolveCount),
        LastLayerLooks.of(familySamples, ORIENTATION, twoLooked, FAMILY_FLOOR), previous,
        Integer.valueOf(twoPermuted.size()),
        LastLayerLooks.of(familySamples, PERMUTATION, twoPermuted, FAMILY_FLOOR));
  }

  /**
   * The last plan as the next payload carries it, or null where it may not ride: no plan yet, one
   * written for another method, or fewer than {@link #SINCE_FLOOR} solves since.
   *
   * @param solvesSince how many solves of this method followed the last plan
   * @param sinceTimes the whole times of the newest of them, at most {@link #FAMILY_WINDOW}
   * @param sinceSamples every step occurrence of those same solves
   * @param turnings the turnings the payload is built from; the ones of those solves are kept
   * @param drillReps for each card of the last plan in order, the reps done since on its drill,
   *     null where it had none
   */
  public static PreviousPlan previous(StoredCoachPlan last, String method, int solvesSince,
      List<Long> sinceTimes, List<StepSample> sinceSamples, List<StepTurning> turnings,
      List<Integer> drillReps) {
    if (last == null || !method.equals(last.getPayload().getMethod())
        || solvesSince < SINCE_FLOOR) {
      return null;
    }
    Set<Long> sinceSolves = new LinkedHashSet<Long>();
    for (StepSample sample : sinceSamples) {
      sinceSolves.add(Long.valueOf(sample.getSolveId()));
    }
    List<StepTurning> sinceTurnings = new ArrayList<StepTurning>();
    for (StepTurning turning : turnings) {
      if (sinceSolves.contains(Long.valueOf(turning.getSolveId()))) {
        sinceTurnings.add(turning);
      }
    }
    StepTallies tallies = new StepTallies(sinceSamples);
    MethodStatistics statistics =
        new MethodStatistics(tallies.getSteps(), tallies.getParts(), sinceTimes.size());
    PreviousPlan before = last.getPayload().getPrevious();
    List<PreviousPlan.Card> cards = new ArrayList<PreviousPlan.Card>();
    List<FocusArea> focus = last.getPlan().getFocus();
    for (int i = 0; i < focus.size(); i++) {
      FocusArea area = focus.get(i);
      // A card the plan before also carried is one plan further into its run.
      PreviousPlan.Card earlier =
          before == null ? null : before.card(area.getReasonCode(), area.getCodes());
      cards.add(PreviousPlan.Card.of(area, earlier == null ? 1 : earlier.getRun() + 1,
          drillReps != null && i < drillReps.size() ? drillReps.get(i) : null));
    }
    CoachPayload written = last.getPayload();
    return new PreviousPlan(solvesSince,
        new PreviousPlan.Steps(written.getFamilyWindow(), written.getTwoLookCount(),
            written.getTwoLookPllCount(), written.getSolve(), written.getFamilies(),
            written.getF2lGroups(), written.getTurning()),
        new PreviousPlan.Steps(sinceTimes.size(),
            Integer.valueOf(twoLookSolves(sinceSamples, LOOK_FAMILIES).size()),
            Integer.valueOf(twoLookSolves(sinceSamples, PERMUTATION_LOOK_FAMILIES).size()),
            solveFigure(sinceTimes),
            familyFigures(statistics.getFamilies(), statistics, tallies),
            F2lGroups.of(sinceSamples, FAMILY_FLOOR), Turnings.of(sinceTurnings, FAMILY_FLOOR)),
        cards);
  }

  /** As above with no step's turning replayed. */
  public static CoachPayload build(String puzzle, String method, List<Long> solveTimes,
      List<StepSample> familySamples, List<StepSample> caseSamples, int caseSolveCount,
      List<StepSample> drillSamples, int drillCount, List<CaseKnowledge> knowledge,
      List<PairExecution> pairs, int f2lSolveCount) {
    return build(puzzle, method, solveTimes, familySamples, caseSamples, caseSolveCount,
        drillSamples, drillCount, knowledge, pairs, f2lSolveCount,
        Collections.<StepTurning>emptyList(), 0);
  }

  /** As above with no F2L pairs replayed, so the payload carries no F2L moves. */
  public static CoachPayload build(String puzzle, String method, List<Long> solveTimes,
      List<StepSample> familySamples, List<StepSample> caseSamples, int caseSolveCount,
      List<StepSample> drillSamples, int drillCount, List<CaseKnowledge> knowledge) {
    return build(puzzle, method, solveTimes, familySamples, caseSamples, caseSolveCount,
        drillSamples, drillCount, knowledge, Collections.<PairExecution>emptyList(), 0);
  }

  /**
   * The F2L rotations figure for {@link CoachPayload#withF2lRotations}, or null under
   * {@link #SOLVE_FLOOR} gyro solves. Its time excess needs {@link #FAMILY_FLOOR} rotations behind
   * it, and a case {@link #CASE_FLOOR} pairs.
   *
   * @param pairs every F2L pair of the {@link #F2L_MOVE_WINDOW}'s gyro solves; a solve without a
   *     gyro has none here
   */
  public static F2lRotationFigure f2lRotations(List<PairRotation> pairs) {
    return F2lRotationFigure.of(pairs, SOLVE_FLOOR, FAMILY_FLOOR, CASE_FLOOR);
  }

  /**
   * The cases standing at any of the given statuses, as codes. Kept to the sets this method's own
   * steps are made
   * of: a payload for one method has no business carrying what the solver knows of another's, and a
   * case code means nothing without the set it belongs to.
   *
   * <p>Nothing is sent for a case that has no status. {@link CaseKnowledge} gives one to any case
   * a solve or a revealed drill rep has said anything about, so a case without one is one nothing
   * has been seen of at all: sending it in either list would be the guess that rule exists to
   * prevent. <b>There is no floor</b> — one solve that went in unaided puts a case in the known
   * list, and this is the payload that leaves the device.
   */
  private static List<String> casesAt(List<CaseKnowledge> knowledge, Set<String> sets,
      CaseKnowledge.Status... statuses) {
    List<String> codes = new ArrayList<String>();
    List<CaseKnowledge.Status> wanted = Arrays.asList(statuses);
    for (CaseKnowledge stepCase : knowledge == null
        ? Collections.<CaseKnowledge>emptyList() : knowledge) {
      if (wanted.contains(stepCase.getStatus()) && sets.contains(stepCase.getCaseSet())) {
        codes.add(stepCase.getCode());
      }
    }
    return codes;
  }

  /**
   * The window's solves that took more than one look at a last layer step, counted per solve rather
   * than per look: a two-look is a technique the solver chose, and half a look is not a thing.
   * Counted from the step's algorithms rather than from it being recorded in parts at all, since a
   * one-look step carries its one algorithm as a part too.
   *
   * @param families the part families the step's algorithms are recorded under
   */
  private static Set<Long> twoLookSolves(List<StepSample> samples, Set<String> families) {
    Map<Long, Integer> looks = new LinkedHashMap<Long, Integer>();
    for (StepSample sample : samples) {
      if (!sample.isPart() || !families.contains(MethodStatistics.familyOf(sample.getCode()))) {
        continue;
      }
      Long solve = Long.valueOf(sample.getSolveId());
      Integer seen = looks.get(solve);
      looks.put(solve, Integer.valueOf(seen == null ? 1 : seen.intValue() + 1));
    }
    Set<Long> twoLooks = new LinkedHashSet<Long>();
    for (Map.Entry<Long, Integer> solve : looks.entrySet()) {
      if (solve.getValue().intValue() > 1) {
        twoLooks.add(solve.getKey());
      }
    }
    return twoLooks;
  }

  /** The solve as a whole, which is the only norm there is for whether a step is long. */
  private static StepFigure solveFigure(List<Long> solveTimes) {
    if (solveTimes.size() < SOLVE_FLOOR) {
      return null;
    }
    List<StepSample> samples = new ArrayList<StepSample>();
    for (Long time : solveTimes) {
      samples.add(new StepSample(SOLVE, time.longValue(), 0, false, 0));
    }
    StepTallies tallies = new StepTallies(samples);
    StepStats stats = tallies.get(SOLVE);
    return stats.getCount() < SOLVE_FLOOR ? null
        : StepFigure.plain(stats, false, tallies.getRejectionRate(SOLVE));
  }

  private static List<StepFigure> familyFigures(List<StepStats> stats, MethodStatistics statistics,
      StepTallies tallies) {
    List<StepFigure> figures = new ArrayList<StepFigure>();
    for (StepStats family : stats) {
      if (family.getCount() < FAMILY_FLOOR) {
        continue;
      }
      double skipRate = statistics.getSkipRate(family.getCode());
      figures.add(StepFigure.family(family, measuresRecognition(family.getCode()),
          tallies.getRejectionRate(family.getCode()),
          skipRate == 0 ? null : Double.valueOf(skipRate)));
    }
    return figures;
  }

  /** Every case above its floor, worst cost first, from the families that name one. */
  private static List<StepFigure> caseFigures(MethodStatistics statistics, StepTallies tallies,
      MethodStatistics recent) {
    final List<StepFigure> figures = new ArrayList<StepFigure>();
    for (String family : familiesOf(statistics)) {
      if (!CASE_FAMILIES.contains(family)) {
        continue;
      }
      StepStats familyStats = statistics.getFamily(family);
      Map<String, Long> recentMeans = meansByCode(recent.getCases(family));
      for (StepStats stepCase : statistics.getCases(family)) {
        if (stepCase.getCount() >= CASE_FLOOR) {
          figures.add(StepFigure.stepCase(stepCase, measuresRecognition(stepCase.getCode()),
              tallies.getRejectionRate(stepCase.getCode()),
              statistics.getTimeLostMs(stepCase.getCode()),
              familyStats == null ? 0 : familyStats.getMeanMs(),
              recentMeans.get(stepCase.getCode())));
        }
      }
    }
    Collections.sort(figures, new Comparator<StepFigure>() {
      @Override
      public int compare(StepFigure a, StepFigure b) {
        return Long.compare(b.getTimeLostMs().longValue(), a.getTimeLostMs().longValue());
      }
    });
    return figures;
  }

  /** Each case's newest {@link #RECENT_CASE_OCCURRENCES} occurrences, from samples newest first. */
  static List<StepSample> latestOfEachCase(List<StepSample> newestFirst) {
    Map<String, Integer> taken = new HashMap<String, Integer>();
    List<StepSample> latest = new ArrayList<StepSample>();
    for (StepSample sample : newestFirst) {
      String caseCode = MethodStatistics.caseCodeOf(sample.getCode());
      String key = caseCode == null ? sample.getCode() : caseCode;
      Integer count = taken.get(key);
      int already = count == null ? 0 : count.intValue();
      if (already < RECENT_CASE_OCCURRENCES) {
        latest.add(sample);
        taken.put(key, Integer.valueOf(already + 1));
      }
    }
    return latest;
  }

  private static Map<String, Long> meansByCode(List<StepStats> stats) {
    Map<String, Long> means = new HashMap<String, Long>();
    for (StepStats one : stats) {
      if (one.getCount() > 0) {
        means.put(one.getCode(), Long.valueOf(one.getMeanMs()));
      }
    }
    return means;
  }

  /** The same cases as a drill runs them, slowest first, with no family to weigh them against. */
  private static List<StepFigure> drillFigures(StepTallies tallies) {
    List<StepFigure> figures = new ArrayList<StepFigure>();
    for (StepStats drilled : tallies.getSteps()) {
      if (drilled.getCount() >= CASE_FLOOR) {
        figures.add(StepFigure.plain(drilled, measuresRecognition(drilled.getCode()),
            tallies.getRejectionRate(drilled.getCode())));
      }
    }
    Collections.sort(figures, new Comparator<StepFigure>() {
      @Override
      public int compare(StepFigure a, StepFigure b) {
        return Long.compare(b.getMeanMs(), a.getMeanMs());
      }
    });
    return figures;
  }

  /**
   * The cases both histories hold enough of, where the two are far enough apart to be worth saying.
   * One good drill and two bad solves must not produce a diagnosis, so the gap has to clear a tenth
   * of the solve figure and the noise of both samples before it is sent at all.
   */
  private static List<CaseComparison> comparisons(List<StepFigure> caseFigures,
      List<StepFigure> drillFigures, StepTallies caseTallies, StepTallies drillTallies) {
    List<CaseComparison> comparisons = new ArrayList<CaseComparison>();
    for (StepFigure solved : caseFigures) {
      StepFigure drilled = figure(drillFigures, solved.getCode());
      if (drilled == null) {
        continue;
      }
      long gap = Math.abs(solved.getMeanMs() - drilled.getMeanMs());
      double noise = standardError(caseTallies.get(solved.getCode()))
          + standardError(drillTallies.get(solved.getCode()));
      if (gap > solved.getMeanMs() * MIN_GAP && gap > noise) {
        comparisons.add(new CaseComparison(solved.getCode(), solved.getCount(), solved.getMeanMs(),
            drilled.getCount(), drilled.getMeanMs()));
      }
    }
    return comparisons;
  }

  private static double standardError(StepStats stats) {
    return stats == null || stats.getCount() == 0 ? 0
        : stats.getStdDevMs() / Math.sqrt(stats.getCount());
  }

  private static StepFigure figure(List<StepFigure> figures, String code) {
    for (StepFigure figure : figures) {
      if (figure.getCode().equals(code)) {
        return figure;
      }
    }
    return null;
  }

  /** Every family the window holds, steps and parts together, in the order they are solved in. */
  private static Set<String> familiesOf(MethodStatistics statistics) {
    Set<String> families = new LinkedHashSet<String>();
    for (StepStats step : statistics.getFamilies()) {
      families.add(step.getCode());
    }
    for (StepStats part : statistics.getParts()) {
      families.add(part.getCode());
    }
    return families;
  }

  private static boolean measuresRecognition(String code) {
    return !CROSS_FAMILY.equals(MethodStatistics.familyOf(code));
  }
}
