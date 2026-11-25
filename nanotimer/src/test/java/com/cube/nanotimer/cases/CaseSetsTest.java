package com.cube.nanotimer.cases;

import com.cube.nanotimer.smartcube.step.CaseAlgorithms;
import com.cube.nanotimer.smartcube.step.LastLayerScrambles;

import org.junit.Test;

import java.util.Arrays;
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
    Set<String> seen = new HashSet<String>(CaseSets.OLL.cases());
    seen.addAll(CaseSets.PLL.cases());
    assertEquals(new HashSet<String>(LastLayerScrambles.cases()), seen);
  }

  @Test
  public void testF2LHoldsEveryNamedPairCaseBasicFirst() {
    assertEquals(new HashSet<String>(CaseAlgorithms.pairCases()),
        new HashSet<String>(CaseSets.F2L.cases()));
    assertEquals(92, CaseSets.F2L.cases().size());
    assertEquals("pair_1", CaseSets.F2L.cases().get(0));
    assertEquals("pair_a1", CaseSets.F2L.cases().get(41));
    int[] sizes = {24, 6, 6, 5, 18, 15, 18};
    for (int i = 0; i < sizes.length; i++) {
      assertEquals(CaseSets.F2L.groups().get(i).getId(), sizes[i],
          CaseSets.F2L.groups().get(i).getCases().size());
    }
  }

  /** Each F2L group is where its picture puts the pieces: own slot, another slot, or on top. */
  @Test
  public void testF2LGroupsAreWhereThePiecesAre() {
    // Each slot's corner and edge facelets, in fr, fl, br, bl order.
    int[][] corners = {{29, 26, 15}, {27, 44, 24}, {35, 17, 51}, {33, 53, 42}};
    int[][] edges = {{23, 12}, {21, 41}, {48, 14}, {50, 39}};
    for (String code : CaseSets.F2L.cases()) {
      String picture = CaseAlgorithms.pairPicture(code);
      String group = CaseSets.F2L.groupOf(code).getId();
      int own = Arrays.asList("fr", "fl", "br", "bl").indexOf(CaseAlgorithms.pictureSlot(code));
      int[] ownCorner = corners[own];
      int[] ownEdge = edges[own];
      int[] otherCorners = new int[0];
      int[] otherEdges = new int[0];
      for (int slot = 0; slot < 4; slot++) {
        if (slot != own) {
          otherCorners = concat(otherCorners, corners[slot]);
          otherEdges = concat(otherEdges, edges[slot]);
        }
      }
      boolean cornerHome = lit(picture, ownCorner);
      boolean edgeHome = lit(picture, ownEdge);
      boolean cornerStuck = lit(picture, otherCorners);
      boolean edgeStuck = lit(picture, otherEdges);
      String read = cornerStuck && edgeStuck ? "stuck_both" : cornerStuck ? "stuck_corner"
          : edgeStuck ? "stuck_edge" : cornerHome && edgeHome ? "both" : cornerHome ? "corner"
          : edgeHome ? "edge" : "top";
      assertEquals(code, read, group);
    }
  }

  private static int[] concat(int[] a, int[] b) {
    int[] both = Arrays.copyOf(a, a.length + b.length);
    System.arraycopy(b, 0, both, a.length, b.length);
    return both;
  }

  private static boolean lit(String picture, int[] facelets) {
    for (int facelet : facelets) {
      if (Character.isUpperCase(picture.charAt(facelet))) {
        return true;
      }
    }
    return false;
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
    assertNull(CaseSets.F2L.name("pair_27"));
  }

  @Test
  public void testLookups() {
    assertSame(CaseSets.OLL, CaseSets.ofCase("oll_3"));
    assertSame(CaseSets.PLL, CaseSets.ofFamily("pll_"));
    assertSame(CaseSets.PLL, CaseSets.byId("pll"));
    assertSame(CaseSets.F2L, CaseSets.ofCase("pair_27"));
    assertSame(CaseSets.F2L, CaseSets.ofCase("pair_a1a"));
    assertNull(CaseSets.ofCase("pair_27_fl"));
    assertSame(CaseSets.F2L, CaseSets.ofFamily("pair_"));
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
