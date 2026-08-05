package com.cube.nanotimer.session;

import java.util.LinkedList;
import java.util.List;

public class CubeSession extends TimesStatistics {

  public static final int SESSION_MAX_SIZE = 13; // 12, + 1 for deletion

  /**
   * For each solve, the time a DNF replaced, and null for anything else.
   *
   * <p>Kept beside {@code times} rather than in it: a DNF is a negative there, and every average,
   * best, worst and accuracy in {@link TimesStatistics} reads it by that sign. This list is only
   * ever asked how long a DNF took, which is a thing the strip draws and no statistic counts.
   */
  private final LinkedList<Long> dnfTimes = new LinkedList<Long>();

  public CubeSession() {
    super();
  }

  public CubeSession(List<Long> times) {
    this(times, null);
  }

  public CubeSession(List<Long> times, List<Long> dnfTimes) {
    super(times);
    for (int i = 0; i < this.times.size(); i++) {
      this.dnfTimes.add((dnfTimes != null && i < dnfTimes.size()) ? dnfTimes.get(i) : null);
    }
  }

  /** Aligned with {@link #getTimes}. */
  public List<Long> getDnfTimes() {
    return (dnfTimes.size() > 12) ? dnfTimes.subList(0, 12) : dnfTimes;
  }

  public long getAverageOfFive() {
    return getAverageOf(5);
  }

  public long getAverageOfTwelve() {
    return getAverageOf(12);
  }

  public void addTime(long time) {
    times.addFirst(time);
    dnfTimes.addFirst(null);
    if (times.size() > SESSION_MAX_SIZE) {
      times.removeLast();
      dnfTimes.removeLast();
    }
  }

  public void setLastAsDNF() {
    if (!times.isEmpty()) {
      long replaced = times.get(0);
      dnfTimes.set(0, (replaced > 0) ? replaced : null);
      times.set(0, (long) -1);
    }
  }

  public void setLastAsPlusTwo(boolean plusTwo) {
    if (!times.isEmpty()) {
      long curLastTime = times.get(0);
      if (curLastTime > 0) {
        long time = (plusTwo) ? curLastTime + 2000 : curLastTime - 2000;
        times.set(0, time);
      }
    }
  }

  /** @param timeBeforeDnf what this solve's DNF replaced, when it is one and there is one */
  public void setLastTime(long time, Long timeBeforeDnf) {
    if (!times.isEmpty()) {
      times.set(0, time);
      dnfTimes.set(0, timeBeforeDnf);
    }
  }

  public void deleteLast() {
    if (!times.isEmpty()) {
      times.removeFirst();
      dnfTimes.removeFirst();
    }
  }

  public void clearSession() {
    if (times != null) {
      times.clear();
    }
    dnfTimes.clear();
  }

  public List<Long> getTimes() {
    if (times.size() > 12) {
      return times.subList(0, 12);
    } else {
      return times;
    }
  }

  public void setTimes(LinkedList<Long> times) {
    this.times = times;
    dnfTimes.clear();
    for (int i = 0; i < times.size(); i++) {
      dnfTimes.add(null);
    }
  }

}
