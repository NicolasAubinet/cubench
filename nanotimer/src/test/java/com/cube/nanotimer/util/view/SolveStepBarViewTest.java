package com.cube.nanotimer.util.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.vo.SolveStep;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * The bar's geometry: a moment of the solve has to land on the segment that draws it, and a touch
 * has to come back as the moment it hit. The segments and the playhead once used different layouts,
 * which put the marker up to 700 ms either side of the step it belonged to on the solve below.
 */
public class SolveStepBarViewTest {

  private static final float WIDTH = 320f;
  private static final float HEIGHT = 22f; // solvereplay_dialog.xml

  /** The measured CFOP capture: cross, F2L, OLL, PLL, in ms. */
  private static final long[] CFOP_159 = {3319, 20014, 5165, 5284};

  private static List<SolveStep> steps(long... totals) {
    List<SolveStep> steps = new ArrayList<SolveStep>();
    for (int i = 0; i < totals.length; i++) {
      steps.add(new SolveStep(i, "s" + i, 0, totals[i], Collections.<SolveStep>emptyList()));
    }
    return steps;
  }

  private static long totalOf(long... totals) {
    long total = 0;
    for (long ms : totals) {
      total += ms;
    }
    return total;
  }

  @Test
  public void aMomentOfTheSolveDrawsWhereTheBarReadsItBack() {
    List<SolveStep> steps = steps(CFOP_159);
    long total = totalOf(CFOP_159);
    for (long ms = 0; ms <= total; ms += 10) {
      float x = SolveStepBarView.xAt(steps, WIDTH, HEIGHT, ms / (float) total);
      float back = SolveStepBarView.fractionAt(steps, WIDTH, HEIGHT, x) * total;
      assertEquals("at " + ms + " ms", ms, back, 1f);
    }
  }

  /** The two ends are the two ends, whatever the gaps do in between. */
  @Test
  public void theBarSpansTheWholeView() {
    List<SolveStep> steps = steps(CFOP_159);
    assertEquals(0f, SolveStepBarView.xAt(steps, WIDTH, HEIGHT, 0f), 0.01f);
    assertEquals(WIDTH, SolveStepBarView.xAt(steps, WIDTH, HEIGHT, 1f), 0.01f);
  }

  /**
   * The regression itself: reading the playhead off the view's width alone put it hundreds of ms
   * from the segment it belonged to. A gap-aware layout has to beat that by a wide margin.
   */
  @Test
  public void thePlayheadNoLongerRunsAheadOfTheSegments() {
    List<SolveStep> steps = steps(CFOP_159);
    long total = totalOf(CFOP_159);
    float worstNaive = 0;
    float worst = 0;
    for (long ms = 0; ms <= total; ms += 10) {
      float fraction = ms / (float) total;
      float naiveX = WIDTH * fraction; // what drawPlayhead used to do
      worstNaive = Math.max(worstNaive,
          Math.abs(SolveStepBarView.fractionAt(steps, WIDTH, HEIGHT, naiveX) * total - ms));
      float x = SolveStepBarView.xAt(steps, WIDTH, HEIGHT, fraction);
      worst = Math.max(worst,
          Math.abs(SolveStepBarView.fractionAt(steps, WIDTH, HEIGHT, x) * total - ms));
    }
    assertTrue("the old geometry was off by " + worstNaive + " ms", worstNaive > 400);
    assertTrue("still off by " + worst + " ms", worst < 5);
  }

  /** A skipped step is given no width, so it must be given no gap either. */
  @Test
  public void aSkippedStepTakesNoRoom() {
    List<SolveStep> withSkip = steps(3319, 20014, 0, 5284);
    List<SolveStep> without = steps(3319, 20014, 5284);
    long total = totalOf(3319, 20014, 5284);
    for (long ms = 0; ms <= total; ms += 10) {
      float fraction = ms / (float) total;
      assertEquals("at " + ms + " ms",
          SolveStepBarView.xAt(without, WIDTH, HEIGHT, fraction),
          SolveStepBarView.xAt(withSkip, WIDTH, HEIGHT, fraction), 0.01f);
    }
  }

  /** A touch in the gap between two steps means the boundary, not the far end of the bar. */
  @Test
  public void aTouchInAGapSeeksToTheBoundary() {
    List<SolveStep> steps = steps(CFOP_159);
    long total = totalOf(CFOP_159);
    float justAfterCross = SolveStepBarView.xAt(steps, WIDTH, HEIGHT, CFOP_159[0] / (float) total);
    float inTheGap = justAfterCross + HEIGHT * 0.25f / 2f;
    assertEquals(CFOP_159[0],
        SolveStepBarView.fractionAt(steps, WIDTH, HEIGHT, inTheGap) * total, 1f);
  }

  @Test
  public void anEmptyBarSeeksNowhere() {
    assertEquals(0f, SolveStepBarView.fractionAt(
        new ArrayList<SolveStep>(), WIDTH, HEIGHT, 100f), 0.001f);
    assertEquals(0f, SolveStepBarView.xAt(
        Arrays.asList(new SolveStep(0, "s", 0, 0, Collections.<SolveStep>emptyList())),
        WIDTH, HEIGHT, 0.5f), 0.001f);
  }

  /** A bar too narrow for its own gaps leaves the segments no width: it must not seek to NaN. */
  @Test
  public void aBarWithNoRoomLeftSeeksNowhere() {
    List<SolveStep> steps = steps(CFOP_159);
    float noRoom = HEIGHT * 0.25f * 3;
    assertEquals(0f, SolveStepBarView.fractionAt(steps, noRoom, HEIGHT, 10f), 0.001f);
    assertEquals(0f, SolveStepBarView.xAt(steps, noRoom, HEIGHT, 0.5f), 0.001f);
  }
}
