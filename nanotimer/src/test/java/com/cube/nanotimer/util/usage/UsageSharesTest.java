package com.cube.nanotimer.util.usage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.smartcube.step.CaseAlgorithms;
import com.cube.nanotimer.smartcube.step.UsageFigures;

import org.junit.After;
import org.junit.Test;

public class UsageSharesTest {

  private static final String T_LISTED = "R U R' U' R' F R2 U' R' U' R U R' F'";
  private static final String T_OTHER = "F R U' R' U R U R2 F' R U R U' R'";

  @After
  public void clear() {
    CaseAlgorithms.setUsage(null);
  }

  @Test
  public void readsTheServicesSnapshot() {
    UsageFigures figures = UsageShares.parse("{\"cases\": {"
        + "\"pll_t\": {\"users\": 40, \"algorithms\": [{\"moves\": \"" + T_OTHER + "\", \"share\": 55.0},"
        + " {\"moves\": \"" + T_LISTED + "\", \"share\": 45.0}]},"
        + "\"pll_v\": {\"users\": 12, \"algorithms\": [{\"moves\": \"R U R'\", \"share\": 100}]}}}");
    assertEquals(1, figures.size()); // pll_v is under the floor
    CaseAlgorithms.setUsage(figures);
    assertTrue(CaseAlgorithms.hasUsage("pll_t"));
    assertEquals(T_OTHER, CaseAlgorithms.shown("pll_t").get(0).getMoves());
    assertFalse(CaseAlgorithms.hasUsage("pll_v"));
  }

  @Test
  public void skipsBadRowsAndCases() {
    UsageFigures figures = UsageShares.parse("{\"cases\": {"
        + "\"pll_t\": {\"users\": 40, \"algorithms\": [{\"moves\": \"" + T_LISTED + "\", \"share\": 140},"
        + " {\"share\": 20}, 7, {\"moves\": \"" + T_OTHER + "\", \"share\": 30}]},"
        + "\"pll_v\": \"nothing\"}}");
    CaseAlgorithms.setUsage(figures);
    assertEquals(1, CaseAlgorithms.shown("pll_t").size());
    assertEquals(T_OTHER, CaseAlgorithms.shown("pll_t").get(0).getMoves());
  }

  @Test
  public void anUnreadableSnapshotIsNone() {
    assertNull(UsageShares.parse("<html>"));
    assertNull(UsageShares.parse("{\"shares\": {}}"));
  }
}
