package com.cube.nanotimer.drill;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class DrillSpecTest {

  private static final String G_PERMS = "{"
      + "\"spec_version\": 1,"
      + "\"id\": \"gperms-01\","
      + "\"puzzle\": \"3x3\","
      + "\"method\": \"cfop\","
      + "\"type\": \"case_execution\","
      + "\"delivery\": \"virtual\","
      + "\"cases\": [\"pll_ga\", \"pll_gb\"],"
      + "\"selection\": \"weighted\","
      + "\"reps\": 20,"
      + "\"target_ms\": 1900,"
      + "\"label\": \"G perms\""
      + "}";

  @Test
  public void readsEveryField() {
    DrillSpec spec = DrillSpec.fromJson(G_PERMS);
    assertEquals(1, spec.getSpecVersion());
    assertEquals("gperms-01", spec.getId());
    assertEquals("3x3", spec.getPuzzle());
    assertEquals("cfop", spec.getMethod());
    assertEquals(DrillSpec.Type.CASE_EXECUTION, spec.getType());
    assertEquals(DrillSpec.Delivery.VIRTUAL, spec.getDelivery());
    assertEquals(Arrays.asList("pll_ga", "pll_gb"), spec.getCases());
    assertEquals(DrillSpec.Selection.WEIGHTED, spec.getSelection());
    assertEquals(20, spec.getReps());
    assertEquals(1900, spec.getTargetMs());
    assertEquals("G perms", spec.getLabel());
  }

  @Test
  public void fillsInWhatWasLeftOut() {
    DrillSpec spec = DrillSpec.fromJson("{\"spec_version\": 1, \"type\": \"case_recognition\","
        + " \"delivery\": \"virtual\", \"cases\": [\"oll_21\"], \"reps\": 5}");
    assertEquals("3x3", spec.getPuzzle());
    assertEquals("cfop", spec.getMethod());
    assertEquals(DrillSpec.Selection.ROUND_ROBIN, spec.getSelection());
    assertEquals(0, spec.getTargetMs());
    assertNull(spec.getId());
    assertNull(spec.getLabel());
  }

  /**
   * The point of storing the text rather than the fields: a drill written against a later version
   * has to come back out whole, including the parts this version had no use for.
   */
  @Test
  public void handsBackTheTextItWasGiven() {
    String withMore = "{\"spec_version\": 1, \"type\": \"case_execution\", \"delivery\":"
        + " \"virtual\", \"cases\": [\"pll_t\"], \"reps\": 3, \"rest_between_reps_ms\": 800}";
    assertEquals(withMore, DrillSpec.fromJson(withMore).toJson());
  }

  @Test
  public void writesOneItAuthoredItself() {
    DrillSpec written = new DrillSpec("mine", DrillSpec.Type.CASE_RECOGNITION,
        DrillSpec.Delivery.VIRTUAL, Arrays.asList("pll_h", "pll_z"),
        DrillSpec.Selection.ROUND_ROBIN, 12, 800, "Slice perms");
    DrillSpec read = DrillSpec.fromJson(written.toJson());
    assertEquals("mine", read.getId());
    assertEquals(DrillSpec.Type.CASE_RECOGNITION, read.getType());
    assertEquals(Arrays.asList("pll_h", "pll_z"), read.getCases());
    assertEquals(12, read.getReps());
    assertEquals(800, read.getTargetMs());
    assertEquals("Slice perms", read.getLabel());
  }

  /** A mixed set, where beating the family mean on Gb and on T are not the same ask. */
  @Test
  public void readsATargetPerCaseAndFallsBackForTheRest() {
    DrillSpec spec = DrillSpec.fromJson("{\"spec_version\": 3, \"type\": \"case_execution\","
        + " \"delivery\": \"virtual\", \"cases\": [\"pll_gb\", \"pll_t\", \"pll_ja\"],"
        + " \"reps\": 12, \"target_ms\": 2000,"
        + " \"case_targets_ms\": {\"pll_gb\": 2600, \"pll_t\": 1400}}");
    assertEquals(3, spec.getSpecVersion());
    assertEquals(2600, spec.getTargetMs("pll_gb"));
    assertEquals(1400, spec.getTargetMs("pll_t"));
    assertEquals("a case the map leaves out", 2000, spec.getTargetMs("pll_ja"));
    assertEquals(2000, spec.getTargetMs());
  }

  /** An app that reads only 2 still runs every drill that did not need 3. */
  @Test
  public void writesTheOldestVersionItsFieldsNeed() {
    DrillSpec plain = new DrillSpec("mine", DrillSpec.Type.CASE_EXECUTION,
        DrillSpec.Delivery.VIRTUAL, Arrays.asList("pll_t"), DrillSpec.Selection.ROUND_ROBIN, 5,
        1500, "T");
    assertEquals(2, DrillSpec.fromJson(plain.toJson()).getSpecVersion());
    assertEquals(2, DrillSpec.fromJson(DrillSpec.cross("x", "D", 3, 0, null).toJson())
        .getSpecVersion());

    Map<String, Long> targets = new LinkedHashMap<String, Long>();
    targets.put("pll_gb", Long.valueOf(2600));
    targets.put("pll_t", Long.valueOf(0)); // no target, not a target of nothing
    DrillSpec mixed = new DrillSpec("mine", DrillSpec.Type.CASE_EXECUTION,
        DrillSpec.Delivery.VIRTUAL, Arrays.asList("pll_gb", "pll_t"), DrillSpec.Selection.WEIGHTED,
        10, 2000, targets, "G and T");
    DrillSpec read = DrillSpec.fromJson(mixed.toJson());
    assertEquals(3, read.getSpecVersion());
    assertEquals(2600, read.getTargetMs("pll_gb"));
    assertEquals(2000, read.getTargetMs("pll_t"));
    assertEquals(1, read.getCaseTargetsMs().size());
  }

  /** A drill that runs each case until it beats its target needs 3, and keeps its counts. */
  @Test
  public void aRetiringDrillIsWrittenAtThreeAndReadsBack() {
    Map<String, Long> targets = new LinkedHashMap<String, Long>();
    targets.put("pll_gb", Long.valueOf(2600));
    DrillSpec spec = new DrillSpec("coach", DrillSpec.Type.CASE_EXECUTION,
        DrillSpec.Delivery.VIRTUAL, Arrays.asList("pll_gb", "pll_t"), DrillSpec.Selection.ROUND_ROBIN,
        40, 2000, targets, 3, 5, "G and T");
    DrillSpec read = DrillSpec.fromJson(spec.toJson());
    assertEquals(3, read.getSpecVersion());
    assertTrue(read.retiresCases());
    assertEquals(3, read.getWinsToRetire());
    assertEquals(5, read.getMinShowings());
    assertEquals(40, read.getReps());

    DrillSpec plain = new DrillSpec("mine", DrillSpec.Type.CASE_EXECUTION,
        DrillSpec.Delivery.VIRTUAL, Arrays.asList("pll_t"), DrillSpec.Selection.ROUND_ROBIN, 5, 0,
        "T");
    assertFalse("a hand-picked drill runs its reps out", DrillSpec.fromJson(plain.toJson())
        .retiresCases());
  }

  /** A case with nothing to beat could never leave, so the drill is refused rather than run. */
  @Test(expected = IllegalArgumentException.class)
  public void aRetiringDrillNeedsATargetForEveryCase() {
    DrillSpec.fromJson("{\"spec_version\": 3, \"type\": \"case_execution\","
        + " \"delivery\": \"virtual\", \"cases\": [\"pll_gb\", \"pll_t\"], \"reps\": 40,"
        + " \"case_targets_ms\": {\"pll_gb\": 2600},"
        + " \"retire_after_wins\": 3, \"min_showings\": 5}");
  }

  /** A drill from a version this app does not know is refused, since running it wrong is worse. */
  @Test
  public void refusesAVersionItCannotRead() {
    refuses("{\"spec_version\": 99, \"type\": \"case_execution\", \"delivery\": \"virtual\","
        + " \"cases\": [\"pll_t\"], \"reps\": 3}", "newer");
  }

  @Test
  public void refusesATypeItDoesNotKnow() {
    refuses("{\"spec_version\": 1, \"type\": \"full_solve\", \"delivery\": \"virtual\","
        + " \"cases\": [\"pll_t\"], \"reps\": 3}", "full_solve");
  }

  @Test
  public void refusesADrillWithNothingToDo() {
    refuses("{\"spec_version\": 1, \"type\": \"case_execution\", \"delivery\": \"virtual\","
        + " \"cases\": [], \"reps\": 3}", "case");
    refuses("{\"spec_version\": 1, \"type\": \"case_execution\", \"delivery\": \"virtual\","
        + " \"cases\": [\"pll_t\"], \"reps\": 0}", "rep");
  }

  /**
   * The one type with no cases. A cross case is a whole scramble that no vocabulary names, so what
   * would be a case list is a face and a looking limit instead, which is what moved the version.
   */
  @Test
  public void readsACrossDrill() {
    DrillSpec spec = DrillSpec.fromJson("{\"spec_version\": 2, \"id\": \"cross-01\","
        + " \"type\": \"cross\", \"delivery\": \"virtual\", \"reps\": 12,"
        + " \"cross_face\": \"D\", \"planning_ms\": 15000}");
    assertEquals(DrillSpec.Type.CROSS, spec.getType());
    assertEquals("D", spec.getCrossFace());
    assertEquals(15000, spec.getPlanningMs());
    assertTrue(spec.getCases().isEmpty());
  }

  @Test
  public void writesACrossDrillItAuthoredItself() {
    DrillSpec read = DrillSpec.fromJson(DrillSpec.cross("mine", "R", 8, 0, "Red cross").toJson());
    assertEquals(DrillSpec.Type.CROSS, read.getType());
    assertEquals("R", read.getCrossFace());
    assertEquals(8, read.getReps());
    assertEquals("no limit is no field", 0, read.getPlanningMs());
    assertEquals("Red cross", read.getLabel());
  }

  @Test
  public void refusesACrossDrillWithNoFace() {
    refuses("{\"spec_version\": 2, \"type\": \"cross\", \"delivery\": \"virtual\","
        + " \"reps\": 3}", "face");
    refuses("{\"spec_version\": 2, \"type\": \"cross\", \"delivery\": \"virtual\","
        + " \"reps\": 3, \"cross_face\": \"M\"}", "face");
  }

  @Test
  public void aLearningDrillNeedsNoTargetAndKeepsItsAlgorithms() {
    Map<String, String> algorithms = new HashMap<String, String>();
    algorithms.put("oll_26", "R U2 R' U' R U' R'");
    DrillSpec spec = DrillSpec.learning("coach-learn", DrillSpec.Type.CASE_EXECUTION,
        Arrays.asList("oll_26", "oll_27"), DrillSpec.Selection.ROUND_ROBIN, 40, 3, algorithms,
        null);
    DrillSpec read = DrillSpec.fromJson(spec.toJson());
    assertEquals("still readable by an app at 5", 5, read.getSpecVersion());
    assertTrue(read.isLearning());
    assertEquals("R U2 R' U' R U' R'", read.getCaseAlgorithm("oll_26"));
    assertNull("left out is the usual one", read.getCaseAlgorithm("oll_27"));
    assertEquals(3, read.getWinsToRetire());
  }

  @Test
  public void aDrillWithoutLearningStaysReadableByAnOlderApp() {
    DrillSpec spec = new DrillSpec("coach-f2l", DrillSpec.Type.F2L_PAIR,
        DrillSpec.Delivery.VIRTUAL, Arrays.asList("pair_a1"), DrillSpec.Selection.WEIGHTED, 40,
        900, Collections.<String, Long>emptyMap(), 3, 5, null).withWorkingSet(5);
    assertEquals(4, DrillSpec.fromJson(spec.toJson()).getSpecVersion());
    assertFalse(spec.isLearning());
  }

  @Test
  public void refusesALearningDrillThatCannotRetire() {
    refuses("{\"spec_version\": 5, \"type\": \"case_execution\", \"delivery\": \"virtual\","
        + " \"cases\": [\"oll_26\"], \"reps\": 10, \"learn\": true}", "learning");
  }

  @Test
  public void readsALookAheadDrillWithNoCases() {
    DrillSpec spec = DrillSpec.fromJson("{\"spec_version\": 6, \"id\": \"coach-lookahead\","
        + " \"type\": \"f2l_lookahead\", \"delivery\": \"virtual\", \"reps\": 12}");
    assertEquals(DrillSpec.Type.F2L_LOOKAHEAD, spec.getType());
    assertTrue(spec.getCases().isEmpty());
    assertEquals(12, spec.getReps());
    assertFalse(spec.retiresCases());
  }

  /** Written at 6, so an app at 5, which cannot deal two pairs, refuses it rather than misrun it. */
  @Test
  public void writesALookAheadDrillAtSix() {
    DrillSpec read = DrillSpec.fromJson(DrillSpec.lookAhead("mine", 10, "Look-ahead").toJson());
    assertEquals(6, read.getSpecVersion());
    assertEquals(DrillSpec.Type.F2L_LOOKAHEAD, read.getType());
    assertEquals(10, read.getReps());
    assertEquals("Look-ahead", read.getLabel());
    assertEquals(0, read.getTargetMs());
  }

  @Test
  public void refusesALookAheadDrillThatRetiresOrLearns() {
    refuses("{\"spec_version\": 6, \"type\": \"f2l_lookahead\", \"delivery\": \"virtual\","
        + " \"reps\": 10, \"target_ms\": 3000, \"retire_after_wins\": 3}", "retire");
    refuses("{\"spec_version\": 6, \"type\": \"f2l_lookahead\", \"delivery\": \"virtual\","
        + " \"reps\": 10, \"retire_after_wins\": 3, \"learn\": true}", "learning");
  }

  /** Written at 7, so an app at 6, which deals pairs only into front right, refuses it. */
  @Test
  public void writesABackSlotLearningDrillAtSeven() {
    DrillSpec spec = DrillSpec.learning("coach-f2l_rotations-f2l", DrillSpec.Type.F2L_BACK_SLOT,
        Arrays.asList("pair_4", "pair_17"), DrillSpec.Selection.ROUND_ROBIN, 100, 5,
        Collections.<String, String>emptyMap(), null);
    DrillSpec read = DrillSpec.fromJson(spec.toJson());
    assertEquals(7, read.getSpecVersion());
    assertEquals(DrillSpec.Type.F2L_BACK_SLOT, read.getType());
    assertEquals(Arrays.asList("pair_4", "pair_17"), read.getCases());
    assertTrue(read.isLearning());
  }

  @Test
  public void refusesTextThatIsNotADrill() {
    refuses("not json at all", "");
    refuses("{\"reps\": 3}", "");
  }

  private static void refuses(String json, String saying) {
    try {
      DrillSpec.fromJson(json);
      fail("Should have refused: " + json);
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage(), e.getMessage().contains(saying));
    }
  }
}
