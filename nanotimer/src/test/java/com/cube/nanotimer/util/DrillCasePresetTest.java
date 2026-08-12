package com.cube.nanotimer.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DrillCasePresetTest {

  @Test
  public void testRoundTrip() {
    List<DrillCasePreset> presets = new ArrayList<DrillCasePreset>();
    presets.add(new DrillCasePreset("2-look", Arrays.asList("oll_21", "oll_22")));
    presets.add(new DrillCasePreset("Newly learned", Arrays.asList("oll_1")));

    List<DrillCasePreset> read = DrillCasePreset.fromJson(DrillCasePreset.toJson(presets));
    assertEquals(2, read.size());
    assertEquals("2-look", read.get(0).getName());
    assertEquals(Arrays.asList("oll_21", "oll_22"),
        new ArrayList<String>(read.get(0).getCases()));
    assertEquals("Newly learned", read.get(1).getName());
  }

  /** The name is the user's own text, so it has to survive whatever they type into it. */
  @Test
  public void testNameWithSeparators() {
    List<DrillCasePreset> presets = new ArrayList<DrillCasePreset>();
    presets.add(new DrillCasePreset("R, U \"and\" away;", Arrays.asList("pll_t")));

    List<DrillCasePreset> read = DrillCasePreset.fromJson(DrillCasePreset.toJson(presets));
    assertEquals(1, read.size());
    assertEquals("R, U \"and\" away;", read.get(0).getName());
    assertEquals(Arrays.asList("pll_t"), new ArrayList<String>(read.get(0).getCases()));
  }

  @Test
  public void testUnreadableStoredValue() {
    assertTrue(DrillCasePreset.fromJson(null).isEmpty());
    assertTrue(DrillCasePreset.fromJson("").isEmpty());
    assertTrue(DrillCasePreset.fromJson("not json at all").isEmpty());
    assertTrue(DrillCasePreset.fromJson("[{\"name\":\"no cases\"}]").isEmpty());
  }

  @Test
  public void testHoldsIgnoresOrder() {
    DrillCasePreset preset = new DrillCasePreset("x", Arrays.asList("oll_1", "oll_2"));
    assertTrue(preset.holds(Arrays.asList("oll_2", "oll_1")));
    assertFalse(preset.holds(Arrays.asList("oll_1")));
    assertFalse(preset.holds(Arrays.asList("oll_1", "oll_2", "oll_3")));
  }

  @Test
  public void testMatching() {
    List<DrillCasePreset> presets = new ArrayList<DrillCasePreset>();
    presets.add(new DrillCasePreset("a", Arrays.asList("oll_1")));
    presets.add(new DrillCasePreset("b", Arrays.asList("oll_2")));

    assertEquals("b", DrillCasePreset.matching(presets, Arrays.asList("oll_2")).getName());
    assertNull(DrillCasePreset.matching(presets, Arrays.asList("oll_3")));
    assertNull(DrillCasePreset.matching(presets, new ArrayList<String>()));
  }
}
