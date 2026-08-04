package com.cube.nanotimer.vo;

import java.util.List;

/**
 * A session's last solves, and for each DNF among them the time it replaced.
 *
 * <p>The two travel together because a DNF says nothing about how long it took: the time is thrown
 * away into a negative marker the whole stats layer reads by. What was thrown away is kept in the
 * database so the DNF can be taken back, and it is the only thing that can give a DNF a height in
 * the session strip.
 *
 * <p>Both lists are newest first and the same length. A DNF's entry is null where there is nothing
 * to restore, which is every DNF recorded before the column existed.
 */
public class SessionTimes {

  private final List<Long> times;
  private final List<Long> dnfTimes;

  public SessionTimes(List<Long> times, List<Long> dnfTimes) {
    this.times = times;
    this.dnfTimes = dnfTimes;
  }

  /** Newest first, a DNF held as a negative. */
  public List<Long> getTimes() {
    return times;
  }

  /** Aligned with {@link #getTimes}: the time a DNF replaced, and null for anything else. */
  public List<Long> getDnfTimes() {
    return dnfTimes;
  }
}
