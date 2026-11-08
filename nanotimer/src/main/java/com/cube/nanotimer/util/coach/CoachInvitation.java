package com.cube.nanotimer.util.coach;

import android.content.Context;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.coach.CoachPayloadBuilder;
import com.cube.nanotimer.vo.SolveType;

/**
 * The coach's invitation: a sheet opened by itself once, when the solver could get a plan today:
 * enough smart cube solves for a plan on a solve type the coach writes plans for. No cube needs to
 * be connected, so it can open as the app starts as well as on a return to the main screen. The
 * sheet leads to the Plan tab; the drawer's Coaching row leads there after.
 *
 * <p>Nobody who has or ever had the coach is invited, and nobody is while the beta is full: a beta
 * with no seat to give is not news worth interrupting for, and a seat freed later brings it back.
 */
public final class CoachInvitation {

  /** The solves a plan needs, so the invitation never promises a plan the coach would refuse. */
  static final int MIN_SOLVES = CoachPayloadBuilder.PLAN_FLOOR;

  private CoachInvitation() {
  }

  /** Whether the solves are worth counting, which is everything but their number. */
  public static boolean mayInvite(Context context, CoachClient client, CoachBeta beta,
      SolveType solveType) {
    CoachBetaLook look = CoachBetaLook.of(context);
    boolean full = look != null && look.sheet() == CoachBetaLook.Sheet.FULL;
    return solveType != null
        && may(beta.isInvitationShown(), full, knowsCoach(client),
            CoachPlans.eligible(solveType));
  }

  /** Has the coach, or had it: weekly plans still on, or an access that ended. */
  private static boolean knowsCoach(CoachClient client) {
    return client.hasAccess() || client.accessEnded() || Options.INSTANCE.isCoachPlansOn();
  }

  /** @param coachSolves the solves the coach can read on the solve type */
  public static boolean due(int coachSolves) {
    return coachSolves >= MIN_SOLVES;
  }

  static boolean may(boolean shown, boolean betaFull, boolean knowsCoach, boolean eligible) {
    return !shown && !betaFull && !knowsCoach && eligible;
  }
}
