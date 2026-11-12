package com.cube.nanotimer.gui.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.gui.widget.SetLearnedPanel.Family;
import com.cube.nanotimer.session.CaseKnowledge;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
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

  @Test
  public void aSetLearnedInDrillsButNeverCelebratedIsCelebratedLate() {
    Set<String> learned = new HashSet<String>(SetLearnedPanel.casesOf(Family.OLL));
    Set<String> drill = new HashSet<String>(Arrays.asList("oll_21", "oll_22"));
    assertEquals(Family.OLL, SetLearnedPanel.lateCelebration(learned, drill,
        new HashSet<String>()));
  }

  @Test
  public void aCelebratedSetIsNotCelebratedAgain() {
    Set<String> learned = new HashSet<String>(SetLearnedPanel.casesOf(Family.OLL));
    Set<String> drill = new HashSet<String>(Arrays.asList("oll_21"));
    assertNull(SetLearnedPanel.lateCelebration(learned, drill,
        Collections.singleton(SetLearnedPanel.celebratedKey(Family.OLL))));
  }

  @Test
  public void aSetKnownFromSolvesAloneIsNotCelebratedLate() {
    Set<String> learned = new HashSet<String>(SetLearnedPanel.casesOf(Family.OLL));
    assertNull(SetLearnedPanel.lateCelebration(learned, Collections.singleton("pll_h"),
        new HashSet<String>()));
  }

  @Test
  public void anIncompleteSetIsNotCelebratedLate() {
    Set<String> learned = new HashSet<String>(SetLearnedPanel.casesOf(Family.OLL));
    learned.remove("oll_36");
    assertNull(SetLearnedPanel.lateCelebration(learned, Collections.singleton("oll_21"),
        new HashSet<String>()));
  }

  @Test
  public void pllIsDueWhenOllWasAlreadyCelebrated() {
    Set<String> learned = new HashSet<String>(SetLearnedPanel.casesOf(Family.OLL));
    learned.addAll(SetLearnedPanel.casesOf(Family.PLL));
    assertEquals(Family.PLL, SetLearnedPanel.lateCelebration(learned,
        Collections.singleton("pll_h"),
        Collections.singleton(SetLearnedPanel.celebratedKey(Family.OLL))));
  }

  /** A case in review was done in one look before, so it still counts as learned. */
  @Test
  public void aCaseInReviewCountsAsLearned() {
    assertTrue(CaseKnowledge.Status.KNOWN.isLearned());
    assertTrue(CaseKnowledge.Status.NEEDS_REVIEW.isLearned());
    assertFalse(CaseKnowledge.Status.TO_LEARN.isLearned());
  }
}
