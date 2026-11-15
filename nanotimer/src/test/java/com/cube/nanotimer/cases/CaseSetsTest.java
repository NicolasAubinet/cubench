package com.cube.nanotimer.cases;

import com.cube.nanotimer.smartcube.step.LastLayerScrambles;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class CaseSetsTest {

  @Test
  public void testSetsHoldEveryLastLayerCaseOnce() {
    assertEquals(57, CaseSets.OLL.cases().size());
    assertEquals(21, CaseSets.PLL.cases().size());
    Set<String> seen = new HashSet<String>();
    for (CaseSet set : CaseSets.all()) {
      seen.addAll(set.cases());
    }
    assertEquals(new HashSet<String>(LastLayerScrambles.cases()), seen);
  }

  @Test
  public void testOrderIsTheTables() {
    assertEquals("oll_1", CaseSets.OLL.cases().get(0));
    assertEquals("oll_57", CaseSets.OLL.cases().get(56));
    assertEquals("pll_aa", CaseSets.PLL.cases().get(0));
  }

  @Test
  public void testGroupsCoverEveryCaseOnce() {
    for (CaseSet set : CaseSets.all()) {
      Set<String> grouped = new HashSet<String>();
      for (CaseGroup group : set.groups()) {
        for (String code : group.getCases()) {
          assertTrue(set.id() + " " + code, grouped.add(code));
          assertSame(group, set.groupOf(code));
        }
      }
      assertEquals(set.id(), new HashSet<String>(set.cases()), grouped);
    }
    assertEquals(14, CaseSets.OLL.groups().size());
    assertEquals(4, CaseSets.PLL.groups().size());
  }

  @Test
  public void testGroupCasesKeepSetOrder() {
    List<String> dot = CaseSets.OLL.groupOf("oll_1").getCases();
    assertEquals("oll_1", dot.get(0));
    assertEquals("oll_20", dot.get(dot.size() - 1));
    assertEquals("OCLL", CaseSets.OLL.groupOf("oll_27").getId());
    assertEquals("edges", CaseSets.PLL.groupOf("pll_ub").getId());
    assertEquals("diagonal", CaseSets.PLL.groupOf("pll_y").getId());
  }

  @Test
  public void testNames() {
    assertEquals("27", CaseSets.OLL.name("oll_27"));
    assertEquals("Sune", CaseSets.OLL.shape("oll_27"));
    assertEquals("Lightning", CaseSets.OLL.shape("oll_7"));
    assertEquals("Jb", CaseSets.PLL.name("pll_jb"));
    assertNull(CaseSets.PLL.shape("pll_jb"));
    assertNull(CaseSets.PLL.name("oll_27"));
  }

  @Test
  public void testLookups() {
    assertSame(CaseSets.OLL, CaseSets.ofCase("oll_3"));
    assertSame(CaseSets.PLL, CaseSets.ofFamily("pll_"));
    assertSame(CaseSets.PLL, CaseSets.byId("pll"));
    assertNull(CaseSets.ofCase("pair_27"));
    assertFalse(CaseSets.OLL.contains("pll_t"));
  }

  @Test
  public void testAlgorithmsMostUsedFirst() {
    for (CaseSet set : CaseSets.all()) {
      for (String code : set.cases()) {
        assertFalse(code, set.algorithms(code).isEmpty());
      }
    }
  }
}
