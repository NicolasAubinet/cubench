package com.cube.nanotimer.vo;

public enum TimesSort {
  TIMESTAMP(0),
  TIME(0),
  AVG5(5), // the avg5 column, which holds the Mo3 on a blind type
  AVG12(12),
  AVG50(50),
  AVG100(100);

  private final int averageSize;

  TimesSort(int averageSize) {
    this.averageSize = averageSize;
  }

  public boolean isAverage() {
    return averageSize > 0;
  }

  /** The average size this order ranks by, 0 when it is not an average. */
  public int getAverageSize() {
    return averageSize;
  }
}
