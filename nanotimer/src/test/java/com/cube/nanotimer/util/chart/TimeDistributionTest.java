package com.cube.nanotimer.util.chart;

import com.cube.nanotimer.util.chart.TimeDistribution.Bucket;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TimeDistributionTest {

  @Test
  public void testEmptyGivesNoBuckets() {
    assertTrue(TimeDistribution.of(new ArrayList<Long>()).getBuckets().isEmpty());
    assertTrue(TimeDistribution.of(null).getBuckets().isEmpty());
  }

  @Test
  public void testDnfsAreLeftOut() {
    assertTrue(TimeDistribution.of(Arrays.asList(-1L, -1L)).getBuckets().isEmpty());

    TimeDistribution distribution = TimeDistribution.of(Arrays.asList(10000L, -1L, 10500L));
    assertEquals(2, totalCount(distribution));
  }

  @Test
  public void testEveryTimeIsCountedOnce() {
    List<Long> times = new ArrayList<Long>();
    for (int i = 0; i < 200; i++) {
      times.add(12000L + (i * 37) % 5000);
    }
    assertEquals(times.size(), totalCount(TimeDistribution.of(times)));
  }

  @Test
  public void testIdenticalTimesGiveOneBucket() {
    TimeDistribution distribution = TimeDistribution.of(Arrays.asList(15000L, 15000L, 15000L));
    assertEquals(1, distribution.getBuckets().size());
    assertEquals(3, distribution.getBuckets().get(0).getCount());
  }

  @Test
  public void testBucketsAreContiguousAndAscending() {
    List<Long> times = new ArrayList<Long>();
    for (int i = 0; i < 100; i++) {
      times.add(9000L + i * 90);
    }
    TimeDistribution distribution = TimeDistribution.of(times);
    long width = distribution.getBucketWidthMs();
    List<Bucket> buckets = distribution.getBuckets();
    assertTrue(buckets.size() > 1);
    for (int i = 1; i < buckets.size(); i++) {
      assertEquals(buckets.get(i - 1).getLowerMs() + width, buckets.get(i).getLowerMs());
    }
  }

  @Test
  public void testFirstBucketHoldsTheBestTime() {
    List<Long> times = new ArrayList<Long>();
    for (int i = 0; i < 60; i++) {
      times.add(20000L + i * 100);
    }
    TimeDistribution distribution = TimeDistribution.of(times);
    Bucket first = distribution.getBuckets().get(0);
    assertTrue(first.getLowerMs() <= 20000L);
    assertTrue(first.getLowerMs() + distribution.getBucketWidthMs() > 20000L);
    assertTrue(first.getCount() > 0);
  }

  /** A disaster solve must not stretch the range until every real solve shares one bar. */
  @Test
  public void testOutlierGoesToAnOverflowBucket() {
    List<Long> times = new ArrayList<Long>();
    for (int i = 0; i < 50; i++) {
      times.add(12000L + (i * 60) % 3000);
    }
    times.add(600000L); // a ten minute solve

    TimeDistribution distribution = TimeDistribution.of(times);
    List<Bucket> buckets = distribution.getBuckets();
    Bucket last = buckets.get(buckets.size() - 1);

    assertTrue(last.isOverflow());
    assertEquals(1, last.getCount());
    assertTrue(last.getLabel().endsWith("+"));
    assertTrue("the real solves must still be spread out", buckets.size() > 3);
    assertEquals(times.size(), totalCount(distribution));
  }

  @Test
  public void testNoOverflowBucketWithoutOutliers() {
    List<Long> times = new ArrayList<Long>();
    for (int i = 0; i < 40; i++) {
      times.add(12000L + (i * 70) % 2000);
    }
    for (Bucket bucket : TimeDistribution.of(times).getBuckets()) {
      assertFalse(bucket.isOverflow());
    }
  }

  /** Under the clipping threshold the whole range is drawn, outlier included. */
  @Test
  public void testFewSolvesKeepTheirWholeRange() {
    TimeDistribution distribution = TimeDistribution.of(Arrays.asList(10000L, 11000L, 60000L));
    List<Bucket> buckets = distribution.getBuckets();
    assertFalse(buckets.get(buckets.size() - 1).isOverflow());
    assertEquals(3, totalCount(distribution));
  }

  @Test
  public void testLabelsReadAsTimes() {
    List<Long> secondsApart = new ArrayList<Long>();
    for (int i = 0; i < 60; i++) {
      secondsApart.add(20000L + i * 1000);
    }
    assertEquals("20", TimeDistribution.of(secondsApart).getBuckets().get(0).getLabel());

    List<Long> tightlyGrouped = new ArrayList<Long>();
    for (int i = 0; i < 60; i++) {
      tightlyGrouped.add(20000L + i * 50);
    }
    assertEquals("20.0", TimeDistribution.of(tightlyGrouped).getBuckets().get(0).getLabel());

    List<Long> overAMinute = new ArrayList<Long>();
    for (int i = 0; i < 60; i++) {
      overAMinute.add(90000L + i * 1000);
    }
    assertEquals("1:30", TimeDistribution.of(overAMinute).getBuckets().get(0).getLabel());
  }

  private int totalCount(TimeDistribution distribution) {
    int total = 0;
    for (Bucket bucket : distribution.getBuckets()) {
      total += bucket.getCount();
    }
    return total;
  }

}
