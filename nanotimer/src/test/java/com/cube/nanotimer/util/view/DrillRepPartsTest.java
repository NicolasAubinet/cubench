package com.cube.nanotimer.util.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/** The summary's mean rep, part by part, which its bar and its words are drawn from. */
public class DrillRepPartsTest {

  @Test
  public void averagesEachPartOnItsOwn() {
    List<DrillRepParts.Part> one = lookAhead(1000, 2000, 900, 1800);
    List<DrillRepParts.Part> two = lookAhead(1200, 2400, 500, 2200);

    List<DrillRepParts.Part> mean = DrillRepParts.mean(Arrays.asList(one, two));

    assertEquals(4, mean.size());
    assertEquals(1100, mean.get(0).getMs());
    assertEquals(2200, mean.get(1).getMs());
    assertEquals(700, mean.get(2).getMs());
    assertEquals(2000, mean.get(3).getMs());
  }

  @Test
  public void noRepsHaveNoMean() {
    assertTrue(DrillRepParts.mean(Collections.<List<DrillRepParts.Part>>emptyList()).isEmpty());
  }

  @Test
  public void aNegativeTimeCountsAsNone() {
    assertEquals(0, new DrillRepParts.Part(0, -40, 0, false).getMs());
  }

  private static List<DrillRepParts.Part> lookAhead(long before, long one, long pause, long two) {
    return Arrays.asList(new DrillRepParts.Part(1, before, 1, false),
        new DrillRepParts.Part(2, one, 2, false), new DrillRepParts.Part(3, pause, 3, true),
        new DrillRepParts.Part(2, two, 2, false));
  }
}
