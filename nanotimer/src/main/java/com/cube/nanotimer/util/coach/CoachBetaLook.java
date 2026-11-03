package com.cube.nanotimer.util.coach;

import android.content.Context;

import com.cube.nanotimer.R;

/**
 * Which version of each coach screen to draw: the beta's or the paid one. Every beta text, tag and
 * layout is chosen here from the last status the service sent ({@link CoachBeta}), read at draw
 * time, so ending the beta on the service turns every screen back to the paid version with no app
 * update.
 *
 * <p>Plain values in, string ids and flags out, so each pair is checked by a unit test for every
 * phase and mode.
 */
public final class CoachBetaLook {

  /** What the purchase sheet shows. */
  public enum Sheet {
    /** The prices and the purchase. */
    PAID,
    /** Free while in beta, the weekly loop, and Join. */
    JOIN,
    /** No seat for this install: only Close. No purchase is offered while the coach is in beta. */
    FULL
  }

  /** The drawer's Coaching row. */
  public enum Drawer {
    PREMIUM,
    /** The Beta tag alone: this install has the coach already. */
    BETA,
    /** The Beta tag and "Free while in beta" under the row's name. */
    BETA_FREE
  }

  private final CoachBeta.Phase phase;
  private final boolean member;
  private final CoachBeta.FeedbackMode mode;
  private final int seatsLeft;
  private final boolean paid;
  private final boolean endSeen;

  /**
   * @param phase null when this install never heard, which draws the paid screens
   * @param paid whether the install has the coach by a token, a purchase or a code
   * @param endSeen whether the end-of-beta thanks was already shown and closed
   */
  public CoachBetaLook(CoachBeta.Phase phase, boolean member, CoachBeta.FeedbackMode mode,
      int seatsLeft, boolean paid, boolean endSeen) {
    this.phase = phase;
    this.member = member;
    this.mode = mode == null ? CoachBeta.FeedbackMode.HIDDEN : mode;
    this.seatsLeft = seatsLeft;
    this.paid = paid;
    this.endSeen = endSeen;
  }

  /** As the install stands now. Null in a build with no coach. */
  public static CoachBetaLook of(Context context) {
    CoachClient client = CoachClient.configured(context);
    if (client == null) {
      return null;
    }
    CoachBeta beta = CoachBeta.of(context);
    return new CoachBetaLook(beta.getPhase(), beta.isMember(), beta.getFeedbackMode(),
        beta.getSeatsLeft(), client.paidAccess(), beta.isEndSeen());
  }

  private boolean running() {
    return CoachBeta.running(phase);
  }

  public Sheet sheet() {
    if (!running() || paid || member) {
      return Sheet.PAID;
    }
    return phase == CoachBeta.Phase.OPEN && seatsLeft > 0 ? Sheet.JOIN : Sheet.FULL;
  }

  /** Whether the Plan tab's pitch card offers the beta rather than the purchase. */
  public boolean pitchIsBeta() {
    return sheet() != Sheet.PAID;
  }

  public int pitchPointIcon() {
    return pitchIsBeta() ? R.drawable.ic_coach_point_star : R.drawable.ic_coach_point_support;
  }

  public int pitchPointText() {
    return pitchIsBeta() ? R.string.coach_beta_point_ratings : R.string.analysis_plan_point_support;
  }

  public int pitchPrivateText() {
    return pitchIsBeta() ? R.string.coach_beta_private : R.string.analysis_plan_private;
  }

  public Drawer drawer() {
    if (!running()) {
      return Drawer.PREMIUM;
    }
    return member || paid ? Drawer.BETA : Drawer.BETA_FREE;
  }

  /** The "and an AI coach, free while it's in beta" line on the smart cube news sheet. */
  public boolean newsLine() {
    return running();
  }

  /** Whether the plan is asked about at all: each focus, the stars, last week's work. */
  public boolean asksFeedback() {
    return mode != CoachBeta.FeedbackMode.HIDDEN;
  }

  /** Whether every question must be answered before the stars go, and the next plan waits. */
  public boolean holds() {
    return mode == CoachBeta.FeedbackMode.REQUIRED;
  }

  public int starsSubtitle() {
    return holds() ? R.string.coach_fb_stars_held : R.string.coach_fb_stars_free;
  }

  /** The Beta tag on the plan's own card, so a plan that misses reads as a beta miss. */
  public boolean planTag() {
    return running() && asksFeedback();
  }

  /** The once-only thanks, with the members' offer, at the top of the Plan tab. */
  public boolean endCard() {
    return phase == CoachBeta.Phase.ENDED && member && !paid && !endSeen;
  }
}
