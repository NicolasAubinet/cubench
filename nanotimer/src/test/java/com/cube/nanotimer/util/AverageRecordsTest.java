package com.cube.nanotimer.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.services.db.AverageRecordsStore;
import com.cube.nanotimer.session.AverageRecords;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class AverageRecordsTest {

  /** Feeds 12 averages that get slower, so the warm-up is over. */
  private static AverageRecords warmedUp(int sizes) {
    AverageRecords records = new AverageRecords(sizes);
    for (int i = 0; i < AverageRecords.WARM_UP; i++) {
      Long[] averages = new Long[sizes];
      Arrays.fill(averages, 20000L + i);
      records.next(averages);
    }
    return records;
  }

  @Test
  public void flagsNothingDuringTheWarmUp() {
    AverageRecords records = new AverageRecords(1);
    for (int i = 0; i < AverageRecords.WARM_UP; i++) {
      assertTrue(records.next(20000L - i).isEmpty());
    }
    assertEquals(Collections.singletonList(0), records.next(10000L));
  }

  @Test
  public void flagsOnlyAnAverageThatBeatsEveryEarlierOne() {
    AverageRecords records = warmedUp(1);
    assertTrue(records.next(20000L).isEmpty()); // equal to the best, not better
    assertEquals(Collections.singletonList(0), records.next(19999L));
    assertTrue(records.next(19999L).isEmpty());
  }

  @Test
  public void flagsSeveralSizesOnTheSameSolve() {
    AverageRecords records = warmedUp(3);
    assertEquals(Arrays.asList(0, 2), records.next(19000L, 25000L, 19000L));
  }

  @Test
  public void ignoresADnfOrAMissingAverage() {
    AverageRecords records = warmedUp(2);
    assertTrue(records.next(-1L, null).isEmpty());
    assertTrue(records.next(0L, 0L).isEmpty());
    assertEquals(Arrays.asList(0, 1), records.next(1L, 1L));
  }

  @Test
  public void doesNotCountADnfTowardTheWarmUp() {
    AverageRecords records = new AverageRecords(1);
    for (int i = 0; i < AverageRecords.WARM_UP - 1; i++) {
      records.next(20000L);
    }
    records.next(-1L);
    assertTrue(records.next(10000L).isEmpty()); // only 11 valid averages before it
    assertEquals(Collections.singletonList(0), records.next(9000L));
  }

  @Test
  public void storesPositionsAsBitsKeepingOnlyTheMo3OfABlindType() {
    assertEquals(Arrays.asList(5, 50), AverageRecordsStore.sizes(AverageRecordsStore.mask(Arrays.asList(0, 2), false), false));
    assertEquals(Collections.singletonList(3), AverageRecordsStore.sizes(AverageRecordsStore.mask(Arrays.asList(0, 2), true), true));
    assertTrue(AverageRecordsStore.sizes(AverageRecordsStore.mask(Arrays.asList(1, 3), true), true).isEmpty());
  }

  @Test
  public void startsFromTheGivenBests() {
    AverageRecords records = new AverageRecords(new long[] { 20000L, 0L }, new int[] { AverageRecords.WARM_UP, 0 });
    assertTrue(records.next(20000L, 5000L).isEmpty()); // equal to the given best; the second size has no history
    assertEquals(Collections.singletonList(0), records.next(19999L, 4000L));
  }
}
