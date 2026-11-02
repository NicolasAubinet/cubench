package com.cube.nanotimer.util.coach;

import java.util.Locale;

/**
 * The service's refusal codes, which are its contract with the app; its English messages are not.
 *
 * <p>The app ships on Play's cycle and the service does not, so a code added there after this app
 * was built must still read, as {@link #UNKNOWN}.
 */
public enum CoachRefusal {
  UNAUTHORIZED,
  /** The purchase sent buys nothing any more: the subscription has ended. */
  NO_ACCESS,
  /** The access code entered lets nobody in: never given out, or used up. */
  CODE_REFUSED,
  /** Play could not be asked about the purchase. A failure, and never "no subscription". */
  PURCHASE_UNAVAILABLE,
  PAYLOAD_INVALID,
  PAYLOAD_TOO_LARGE,
  PAYLOAD_TOO_NEW,
  INSUFFICIENT_DATA,
  RATE_LIMITED,
  WEEKLY_CAP_REACHED,
  SPEND_CAP_REACHED,
  PLAN_REJECTED,
  UPSTREAM_UNAVAILABLE,
  STORE_UNAVAILABLE,
  /** A beta member's last plan has no stars yet, and the next one waits on them. */
  FEEDBACK_OWED,
  /** Every seat of the beta is taken. */
  BETA_FULL,
  /** The beta takes nobody new, or has ended. */
  BETA_CLOSED,
  /** No plan of this install's has the id asked about. */
  NOT_FOUND,
  UNKNOWN;

  static CoachRefusal of(String code) {
    for (CoachRefusal refusal : values()) {
      if (refusal != UNKNOWN && refusal.name().toLowerCase(Locale.ROOT).equals(code)) {
        return refusal;
      }
    }
    return UNKNOWN;
  }
}
