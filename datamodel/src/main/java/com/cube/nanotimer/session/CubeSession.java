package com.cube.nanotimer.session;

import java.util.LinkedList;
import java.util.List;

public class CubeSession extends TimesStatistics {

  public static final int SESSION_MAX_SIZE = 13; // 12, + 1 for deletion

  public CubeSession() {
    super();
  }

  public CubeSession(List<Long> times) {
    super(times);
  }

  public long getAverageOfFive() {
    return getAverageOf(5);
  }

  public long getAverageOfTwelve() {
    return getAverageOf(12);
  }

  public void addTime(long time) {
    times.addFirst(time);
    if (times.size() > SESSION_MAX_SIZE) {
      times.removeLast();
    }
  }

  public void setLastAsDNF() {
    if (!times.isEmpty()) {
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

  public void setLastTime(long time) {
    if (!times.isEmpty()) {
      times.set(0, time);
    }
  }

  public void deleteLast() {
    if (!times.isEmpty()) {
      times.removeFirst();
    }
  }

  public void clearSession() {
    if (times != null) {
      times.clear();
    }
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
  }

}
