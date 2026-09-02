package com.cube.nanotimer.coach;

import java.util.List;

/**
 * A plan and the figures it was written from, kept together because neither is worth much alone.
 *
 * <p>A plan on its own cannot be checked: {@link CoachPlan#uncited} needs the payload the claims
 * were supposed to come from, and a plan that arrives from a coach is exactly the one nobody should
 * take on trust. Keeping the pair is also what lets two plans off the same payload be read side by
 * side.
 */
public class StoredCoachPlan {

  private final CoachPlan plan;
  private final CoachPayload payload;
  private final long writtenAt;

  public StoredCoachPlan(CoachPlan plan, CoachPayload payload, long writtenAt) {
    this.plan = plan;
    this.payload = payload;
    this.writtenAt = writtenAt;
  }

  public CoachPlan getPlan() {
    return plan;
  }

  public CoachPayload getPayload() {
    return payload;
  }

  /** When it was written, which never leaves the device: a payload carries counts, never dates. */
  public long getWrittenAt() {
    return writtenAt;
  }

  /** What the plan claims that its own payload does not bear out. */
  public List<Evidence> uncited() {
    return plan.uncited(payload);
  }

  /** The cases and steps it is about that its own payload never mentioned. */
  public List<String> unknownCodes() {
    return plan.unknownCodes(payload);
  }
}
