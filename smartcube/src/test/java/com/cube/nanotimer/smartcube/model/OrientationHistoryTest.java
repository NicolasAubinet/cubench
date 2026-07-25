package com.cube.nanotimer.smartcube.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class OrientationHistoryTest {

  @Test
  public void readsBackTheSampleNearestATime() {
    OrientationHistory history = new OrientationHistory();
    history.onSample(orientation(0.1), 1000);
    history.onSample(orientation(0.2), 1050);
    history.onSample(orientation(0.3), 1100);

    assertEquals(0.2, history.at(1060).getX(), 1e-9);
    assertEquals(0.3, history.at(1090).getX(), 1e-9);
  }

  @Test
  public void hasNothingToSayAboutATimeItWasNotSampledNear() {
    OrientationHistory history = new OrientationHistory();
    history.onSample(orientation(0.1), 1000);

    assertNull(history.at(3000)); // a gap in the stream: better no reading than a stale one
    assertNull(new OrientationHistory().at(1000));
  }

  @Test
  public void keepsTheRecentPastRatherThanGrowingForever() {
    OrientationHistory history = new OrientationHistory();
    for (int i = 0; i < 5000; i++) {
      history.onSample(orientation(0.1), 1000 + i * 50L);
    }

    assertNull(history.at(1000)); // the oldest samples have fallen off
    assertEquals(0.1, history.at(1000 + 4999 * 50L).getX(), 1e-9);
  }

  private static CubeOrientation orientation(double x) {
    return new CubeOrientation(Math.sqrt(1 - x * x), x, 0, 0);
  }
}
