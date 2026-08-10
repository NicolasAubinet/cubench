package com.cube.nanotimer.vo.drill;

/**
 * One recorded drill: what was asked for, when, and how it stopped. Its reps live in
 * {@link DrillCaseRep} or {@link DrillCrossRep} rows against this row's id.
 *
 * <p><b>A drill is never a solve.</b> Nothing here reaches the solve history, the session averages
 * or the method statistics those are read into. Twenty G perms in a row would drag the PLL family
 * mean toward G, and that mean is the baseline every case is measured against, so a drill that
 * improved the numbers by polluting them would be worse than no drill at all.
 *
 * <p>The spec is kept as the text it was written or received as, rather than shredded into columns,
 * so that fields a later spec version adds survive being stored by this one.
 */
public class DrillRecord {

  private long id;
  private final long timestamp;
  private final String spec;
  private final String specId;
  private final String type;
  private final int repsAsked;
  private final DrillEnd end;
  private final int repsCompleted;

  /**
   * A drill about to be written, before it has an id or an end.
   *
   * @param spec the whole spec as its JSON text
   * @param specId the sender's handle for the drill, which is not unique: the app writes its own
   *     drills under a name per practice, so the row's own id is what reps hang off
   * @param type the spec's type code, so cross drills can be told from case ones without the JSON
   *     being parsed in a query
   */
  public DrillRecord(long timestamp, String spec, String specId, String type, int repsAsked) {
    this(0, timestamp, spec, specId, type, repsAsked, null, 0);
  }

  /** @param repsCompleted how many reps were really done, which for a stopped drill is the result */
  public DrillRecord(long id, long timestamp, String spec, String specId, String type,
      int repsAsked, DrillEnd end, int repsCompleted) {
    this.id = id;
    this.timestamp = timestamp;
    this.spec = spec;
    this.specId = specId;
    this.type = type;
    this.repsAsked = repsAsked;
    this.end = end;
    this.repsCompleted = repsCompleted;
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public String getSpec() {
    return spec;
  }

  public String getSpecId() {
    return specId;
  }

  public String getType() {
    return type;
  }

  public int getRepsAsked() {
    return repsAsked;
  }

  /** Null for a drill that was never ended, the app having been killed while it ran. */
  public DrillEnd getEnd() {
    return end;
  }

  public int getRepsCompleted() {
    return repsCompleted;
  }
}
