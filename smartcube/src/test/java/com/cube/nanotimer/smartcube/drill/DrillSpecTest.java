package com.cube.nanotimer.smartcube.drill;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
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
