package com.cube.nanotimer.gui.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.gui.widget.SetLearnedPanel.Family;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/** The "both sets" line shows only once the other last-layer set is complete too. */
public class SetLearnedPanelTest {

  @Test
  public void setsHaveTheirFullCounts() {
    assertEquals(57, SetLearnedPanel.casesOf(Family.OLL).size());
    assertEquals(21, SetLearnedPanel.casesOf(Family.PLL).size());
  }

  @Test
  public void fullOllWithPllMissingOneCaseIsNotBoth() {
    Set<String> learned = new HashSet<String>(SetLearnedPanel.casesOf(Family.OLL));
    learned.addAll(SetLearnedPanel.casesOf(Family.PLL));
    learned.remove("pll_h");
    assertFalse(SetLearnedPanel.otherSetLearned(Family.OLL, learned));
  }

  @Test
  public void fullOllWithOnlyOnePllIsNotBoth() {
    Set<String> learned = new HashSet<String>(SetLearnedPanel.casesOf(Family.OLL));
    learned.add("pll_h");
    assertFalse(SetLearnedPanel.otherSetLearned(Family.OLL, learned));
  }

  @Test
  public void bothSetsCompleteIsBoth() {
    Set<String> learned = new HashSet<String>(SetLearnedPanel.casesOf(Family.OLL));
    learned.addAll(SetLearnedPanel.casesOf(Family.PLL));
    assertTrue(SetLearnedPanel.otherSetLearned(Family.OLL, learned));
    assertTrue(SetLearnedPanel.otherSetLearned(Family.PLL, learned));
  }

  @Test
  public void fullPllWithOllMissingOneCaseIsNotBoth() {
    Set<String> learned = new HashSet<String>(SetLearnedPanel.casesOf(Family.PLL));
    learned.addAll(SetLearnedPanel.casesOf(Family.OLL));
    learned.remove("oll_36");
    assertFalse(SetLearnedPanel.otherSetLearned(Family.PLL, learned));
  }
}
