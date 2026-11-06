package com.cube.nanotimer.coach;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * How one focus of the current plan has moved since it was written, worked out on the phone
 * between plans: the figure the focus leads with when the plan was written, and the same figure
 * over the solves done since.
 *
 * <p>The before and after are the ones the next plan will be sent ({@link PreviousPlan}), read here
 * with no floor on the solves since: a figure shows once it has {@link CoachPayloadBuilder#FAMILY_FLOOR}
 * occurrences since the plan. A focus about cases, long pairs or rotations has no figure in that
 * layer, so it shows the drill reps done since instead. Every figure here is better lower. A change
 * counts as clear past {@link #CLEAR_TIME_RATIO} of a time, or {@link #CLEAR_SHARE_POINTS} of a
 * share; anything smaller is said to have held.
 */
public final class FocusProgress {

  /** A time has clearly moved once it is this share of its before away from it. */
  public static final double CLEAR_TIME_RATIO = 0.10;
  /** A share has clearly moved once it is this far from its before, as a fraction. */
  public static final double CLEAR_SHARE_POINTS = 0.05;

  public enum Kind {
    /** A figure, before and since. */
    FIGURE,
    /** A figure that has too few occurrences since the plan yet. */
    WAITING,
    /** The drill reps done since the plan. */
    REPS,
    /** Nothing to show: no figure in the step layer and no drill. */
    NONE
  }

  public enum Unit {
    MS,
    /** A fraction from 0 to 1. */
    SHARE
  }

  public enum Change {
    BETTER,
    WORSE,
    HELD
  }

  private final FocusArea area;
  private final Kind kind;
  private final Unit unit;
  private final double before;
  private final double since;
  private final int occurrences;
  private final int solvesNeeded;
  private final int reps;

  private FocusProgress(FocusArea area, Kind kind, Unit unit, double before, double since,
      int occurrences, int solvesNeeded, int reps) {
    this.area = area;
    this.kind = kind;
    this.unit = unit;
    this.before = before;
    this.since = since;
    this.occurrences = occurrences;
    this.solvesNeeded = solvesNeeded;
    this.reps = reps;
  }

  /**
   * Each focus of the plan in its order.
   *
   * @param plan the current plan
   * @param sincePlan the plan against the solves since it ({@link CoachPayloadBuilder#sincePlan}),
   *     null when there is none for this method, which leaves every focus with nothing to show
   */
  public static List<FocusProgress> of(StoredCoachPlan plan, PreviousPlan sincePlan) {
    List<FocusProgress> progress = new ArrayList<FocusProgress>();
    List<FocusArea> focus = plan.getPlan().getFocus();
    for (int i = 0; i < focus.size(); i++) {
      FocusArea area = focus.get(i);
      if (sincePlan == null) {
        progress.add(none(area));
        continue;
      }
      PreviousPlan.Card card = i < sincePlan.getCards().size() ? sincePlan.getCards().get(i) : null;
      progress.add(of(area, plan.getPayload().getMethod(), sincePlan,
          card == null ? null : card.getDrillReps()));
    }
    return Collections.unmodifiableList(progress);
  }

  private static FocusProgress of(FocusArea area, String method, PreviousPlan sincePlan,
      Integer drillReps) {
    FocusArea.Reason reason = area.getReason();
    String family = area.getCodes().isEmpty() ? null : area.getCodes().get(0);
    if (reason != null) {
      switch (reason) {
        case RECOGNITION_HEAVY:
          return family == null ? none(area)
              : time(area, sincePlan, "families." + family + ".recognition_ms",
                  "families." + family + ".count");
        case UNEVEN_STEP:
          return family == null ? none(area)
              : time(area, sincePlan, "families." + family + ".std_dev_ms",
                  "families." + family + ".count");
        case STALLING:
          return family == null ? none(area)
              : time(area, sincePlan, "turning." + family + ".stall_ms",
                  "turning." + family + ".count");
        case SLOW_FIRST_PAIR:
          return time(area, sincePlan, "f2l_groups.f2l_first.recognition_ms",
              "f2l_groups.f2l_first.count");
        case STUCK_PIECES:
          return share(area, sincePlan, wasValue(sincePlan, "f2l_groups.f2l_stuck.share"),
              sincePlan.getSince().value("f2l_groups.f2l_stuck.share"),
              "f2l_groups.f2l_stuck.count");
        case SLOW_STEP:
          return family == null ? none(area) : stepShare(area, method, family, sincePlan);
        case TWO_LOOK_OLL:
        case TWO_LOOK_PLL:
          return twoLook(area, sincePlan,
              reason == FocusArea.Reason.TWO_LOOK_PLL ? "two_look_pll_count" : "two_look_count");
        default:
          break;
      }
    }
    return drillReps == null ? none(area)
        : new FocusProgress(area, Kind.REPS, null, 0, 0, 0, 0, drillReps.intValue());
  }

  private static FocusProgress time(FocusArea area, PreviousPlan sincePlan, String path,
      String countPath) {
    Double was = wasValue(sincePlan, path);
    if (was == null) {
      return none(area);
    }
    Double now = sincePlan.getSince().value(path);
    Double count = sincePlan.getSince().value(countPath);
    if (now == null || count == null || count.intValue() < CoachPayloadBuilder.FAMILY_FLOOR) {
      return waiting(area, Unit.MS, sincePlan, countPath);
    }
    return new FocusProgress(area, Kind.FIGURE, Unit.MS, was, now, count.intValue(), 0, 0);
  }

  private static FocusProgress share(FocusArea area, PreviousPlan sincePlan, Double was,
      Double now, String countPath) {
    if (was == null) {
      return none(area);
    }
    Double count = sincePlan.getSince().value(countPath);
    if (now == null || count == null || count.intValue() < CoachPayloadBuilder.FAMILY_FLOOR) {
      return waiting(area, Unit.SHARE, sincePlan, countPath);
    }
    return new FocusProgress(area, Kind.FIGURE, Unit.SHARE, was, now, count.intValue(), 0, 0);
  }

  private static FocusProgress stepShare(FocusArea area, String method, String family,
      PreviousPlan sincePlan) {
    Double was = StepShares.of(method, sincePlan.getBefore().getFamilies()).actual(family);
    Double now = StepShares.of(method, sincePlan.getSince().getFamilies()).actual(family);
    return share(area, sincePlan, was, now, "families." + family + ".count");
  }

  private static FocusProgress twoLook(FocusArea area, PreviousPlan sincePlan, String countPath) {
    Double wasLooks = sincePlan.getBefore().value(countPath);
    Double wasSolves = sincePlan.getBefore().value("windows.families");
    if (wasLooks == null || wasSolves == null || wasSolves.intValue() == 0) {
      return none(area);
    }
    Double looks = sincePlan.getSince().value(countPath);
    int solves = sincePlan.getSince().getSolves();
    Double now = looks == null || solves == 0 ? null : Double.valueOf(looks / solves);
    return share(area, sincePlan, Double.valueOf(wasLooks / wasSolves), now, "windows.families");
  }

  /** What the plan cited for a path, or its step layer's figure where it cited nothing there. */
  private static Double wasValue(PreviousPlan sincePlan, String path) {
    return sincePlan.value("was." + path);
  }

  /**
   * A figure short of its floor, with the solves still needed reckoned from how often it came up
   * per solve when the plan was written.
   */
  private static FocusProgress waiting(FocusArea area, Unit unit, PreviousPlan sincePlan,
      String countPath) {
    int floor = CoachPayloadBuilder.FAMILY_FLOOR;
    Double count = sincePlan.getSince().value(countPath);
    int sinceSolves = sincePlan.getSolvesSince();
    int needed;
    if (count != null && count.intValue() > 0 && sinceSolves > 0) {
      double perSolve = count / Math.min(sinceSolves, sincePlan.getSince().getSolves());
      needed = (int) Math.ceil((floor - count) / perSolve);
    } else {
      Double wasCount = sincePlan.getBefore().value(countPath);
      int wasSolves = sincePlan.getBefore().getSolves();
      double perSolve = wasCount == null || wasSolves == 0 ? 1 : wasCount / wasSolves;
      needed = (int) Math.ceil(floor / Math.max(perSolve, 1e-9)) - sinceSolves;
    }
    return new FocusProgress(area, Kind.WAITING, unit, 0, 0,
        count == null ? 0 : count.intValue(), Math.max(needed, 1), 0);
  }

  private static FocusProgress none(FocusArea area) {
    return new FocusProgress(area, Kind.NONE, null, 0, 0, 0, 0, 0);
  }

  public FocusArea getArea() {
    return area;
  }

  public Kind getKind() {
    return kind;
  }

  /** Null for {@link Kind#REPS} and {@link Kind#NONE}. */
  public Unit getUnit() {
    return unit;
  }

  /** The figure when the plan was written: ms, or a fraction for a share. */
  public double getBefore() {
    return before;
  }

  /** The same figure over the solves since. */
  public double getSince() {
    return since;
  }

  /** How many occurrences the figure since is read from. */
  public int getOccurrences() {
    return occurrences;
  }

  /** For {@link Kind#WAITING}, about how many more solves the figure needs. */
  public int getSolvesNeeded() {
    return solvesNeeded;
  }

  /** For {@link Kind#REPS}, the drill reps done since the plan. */
  public int getReps() {
    return reps;
  }

  /** Whether the figure moved clearly, and which way; {@link Change#HELD} for anything but a figure. */
  public Change getChange() {
    if (kind != Kind.FIGURE) {
      return Change.HELD;
    }
    double diff = since - before;
    double clear = unit == Unit.SHARE ? CLEAR_SHARE_POINTS : before * CLEAR_TIME_RATIO;
    if (Math.abs(diff) < clear || diff == 0) {
      return Change.HELD;
    }
    return diff < 0 ? Change.BETTER : Change.WORSE;
  }

  /**
   * How far the figure moved for the better, on one scale for times and shares, so the recap can
   * lead with the focus that moved most; negative for the worse, 0 for anything but a figure.
   */
  public double getGain() {
    if (kind != Kind.FIGURE) {
      return 0;
    }
    if (unit == Unit.SHARE) {
      return (before - since) / CLEAR_SHARE_POINTS;
    }
    return before <= 0 ? 0 : (before - since) / (before * CLEAR_TIME_RATIO);
  }
}
