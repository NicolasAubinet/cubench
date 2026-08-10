package com.cube.nanotimer.vo.drill;

/**
 * How a recorded drill stopped. The ids are stored in the database and must stay stable.
 *
 * <p>A drill can also have no end at all, which is why nothing here stands for one: the app was
 * killed while it ran, and the reps it had written are all it will ever say. Distinguishing that
 * from the user walking away is the whole reason this is stored rather than inferred from the rep
 * count, since a drill stopped at rep 6 of 20 and one whose cube died at rep 6 are not the same
 * result.
 */
public enum DrillEnd {

  /** Every rep asked for was done. */
  FINISHED(1),
  /** The user stopped it where it stood. */
  STOPPED(2),
  /** The cube went away, which ends a drill rather than losing it. */
  CUBE_LOST(3);

  private final int id;

  DrillEnd(int id) {
    this.id = id;
  }

  public int getId() {
    return id;
  }

  /** Null for a drill that was never ended, and for an id written by a version this one is older than. */
  public static DrillEnd fromId(Integer id) {
    if (id != null) {
      for (DrillEnd end : values()) {
        if (end.id == id) {
          return end;
        }
      }
    }
    return null;
  }
}
