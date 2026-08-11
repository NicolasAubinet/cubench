package com.cube.nanotimer.vo.drill;

/**
 * One stored rep read back on its own, out of the drill it was done in: the rep, plus when it
 * happened and which drill it belongs to.
 *
 * <p>The two extra fields are what a rep needs to be read outside its own drill. The moment is what
 * a list of attempts at one case is ordered and dated by, and the drill id is half of the rep's
 * name: a rep is stored as a position within a drill, so nothing can be thrown out or put back
 * without knowing which drill it fell in.
 */
public class DrillCaseAttempt {

  private final long drillId;
  private final long timestamp;
  private final DrillCaseRep rep;

  /** @param timestamp when the drill this rep belongs to started, which is what dates the rep */
  public DrillCaseAttempt(long drillId, long timestamp, DrillCaseRep rep) {
    this.drillId = drillId;
    this.timestamp = timestamp;
    this.rep = rep;
  }

  public long getDrillId() {
    return drillId;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public DrillCaseRep getRep() {
    return rep;
  }

  /** Whether the rep measured the case, which is what the figures above a list of them are made of. */
  public boolean isCounted() {
    return !rep.isDeleted() && !rep.isAbandoned() && !rep.wasRevealed();
  }
}
