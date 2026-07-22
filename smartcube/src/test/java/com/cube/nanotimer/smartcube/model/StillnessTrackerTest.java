package com.cube.nanotimer.smartcube.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class StillnessTrackerTest {

  private static final CubeOrientation IDENTITY = new CubeOrientation(1, 0, 0, 0);

  /** ~5° about R: inside the still tolerance. */
  private static CubeOrientation nearIdentity() {
    double half = Math.toRadians(5) / 2;
    return new CubeOrientation(Math.cos(half), Math.sin(half), 0, 0);
  }

  /** ~30° about U: an obvious mid-turn wobble. */
  private static CubeOrientation wobbled() {
    double half = Math.toRadians(30) / 2;
    return new CubeOrientation(Math.cos(half), 0, Math.sin(half), 0);
  }

  @Test
  public void aHeldGripBecomesTheStillWindow() {
    StillnessTracker tracker = new StillnessTracker();
    CubeOrientation grip = nearIdentity();
    tracker.onSample(IDENTITY, 1000);
    tracker.onSample(nearIdentity(), 1050);
    tracker.onSample(nearIdentity(), 1100);
    tracker.onSample(grip, 1150);
    assertSame(grip, tracker.getStillWindow(0).getOrientation());
  }

  /** The window carries its span, so a grip's held time can be weighed against another's. */
  @Test
  public void aWindowReportsItsSpanAndGrowsWithIt() {
    StillnessTracker tracker = new StillnessTracker();
    tracker.onSample(IDENTITY, 1000);
    tracker.onSample(nearIdentity(), 1150);
    assertEquals(1000, tracker.getStillWindow(0).getStartMs());
    assertEquals(150, tracker.getStillWindow(0).getDurationMs());
    tracker.onSample(nearIdentity(), 1400); // the same window, still going
    assertEquals(1000, tracker.getStillWindow(0).getStartMs());
    assertEquals(400, tracker.getStillWindow(0).getDurationMs());
  }

  @Test
  public void aShortPauseIsNotStillness() {
    StillnessTracker tracker = new StillnessTracker();
    tracker.onSample(IDENTITY, 1000);
    tracker.onSample(nearIdentity(), 1100);
    assertNull(tracker.getStillWindow(0));
  }

  @Test
  public void constantWobbleNeverBecomesAGrip() {
    StillnessTracker tracker = new StillnessTracker();
    for (int i = 0; i < 20; i++) {
      tracker.onSample(i % 2 == 0 ? IDENTITY : wobbled(), 1000 + i * 50);
    }
    assertNull(tracker.getStillWindow(0));
  }

  @Test
  public void aWindowFromBeforeTheCutOffIsRejected() {
    StillnessTracker tracker = new StillnessTracker();
    tracker.onSample(IDENTITY, 1000);
    tracker.onSample(nearIdentity(), 1200);
    assertNull(tracker.getStillWindow(1000)); // resting on the mat before the scramble
  }

  @Test
  public void motionRestartsTheWindowSoTheNewGripWins() {
    StillnessTracker tracker = new StillnessTracker();
    tracker.onSample(IDENTITY, 1000);
    tracker.onSample(nearIdentity(), 1200); // old grip, window started at 1000
    tracker.onSample(wobbled(), 1250);      // picked up and turned
    CubeOrientation newGrip = wobbled();
    tracker.onSample(newGrip, 1300);
    tracker.onSample(newGrip, 1500);
    assertSame(newGrip, tracker.getStillWindow(1200).getOrientation()); // new window began at 1250
  }
}
