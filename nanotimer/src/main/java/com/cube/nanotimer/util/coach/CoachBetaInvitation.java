package com.cube.nanotimer.util.coach;

import com.cube.nanotimer.cube.SmartCubeManager;
import com.cube.nanotimer.vo.SolveType;

/**
 * The beta's invitation: the join sheet, opened by itself once, when the solver could use the
 * coach today. That is a smart cube connected and at least {@link #MIN_SOLVES} solves the coach
 * can read on a solve type it writes plans for. The drawer's Coaching row leads back to it after.
 *
 * <p>Nobody who already has the coach is invited, and nobody is while the beta has no seat to give:
 * a full beta is not news worth interrupting for, and a seat freed later brings the invitation back.
 */
public final class CoachBetaInvitation {

  /** "About 10 solves": enough for the first plan to have something to say. */
  static final int MIN_SOLVES = 10;

  private CoachBetaInvitation() {
  }

  /** Whether the solves are worth counting, which is everything but their number. */
  public static boolean mayInvite(CoachClient client, CoachBeta beta, SolveType solveType) {
    return solveType != null
        && may(beta.isInvitationShown(), beta.offersJoin(), client.paidAccess(),
            SmartCubeManager.INSTANCE.isConnected(), CoachPlans.eligible(solveType));
  }

  /** @param coachSolves the solves the coach can read on the solve type */
  public static boolean due(int coachSolves) {
    return coachSolves >= MIN_SOLVES;
  }

  static boolean may(boolean shown, boolean offersJoin, boolean paidAccess, boolean cubeConnected,
      boolean eligible) {
    return !shown && offersJoin && !paidAccess && cubeConnected && eligible;
  }
}
