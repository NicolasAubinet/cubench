package com.cube.nanotimer.util.coach;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.json.JSONObject;
import org.junit.Test;

public class CoachPlanReportTest {

  private static final String ID = "3F2504E0-4F89-11D3-9A0C-0305E82C3301";

  @Test
  public void bodyCarriesTheContractFields() throws Exception {
    JSONObject body = new JSONObject(
        CoachPlanReport.body(CoachPlanReport.Reason.HARMFUL, "  too fast  ", "2.3.0", ID));
    assertEquals(1, body.getInt("report_version"));
    assertEquals(ID.toLowerCase(), body.getString("report_id"));
    assertEquals("harmful", body.getString("reason"));
    assertEquals("too fast", body.getString("comment"));
    assertEquals("2.3.0", body.getJSONObject("app").getString("version_name"));
  }

  @Test
  public void longNoteIsCutAndMissingNoteIsEmpty() throws Exception {
    StringBuilder longNote = new StringBuilder();
    for (int i = 0; i < CoachPlanReport.MAX_COMMENT_CHARS + 50; i++) {
      longNote.append('x');
    }
    JSONObject cut = new JSONObject(CoachPlanReport.body(CoachPlanReport.Reason.OTHER,
        longNote.toString(), "2.3.0", ID));
    assertEquals(CoachPlanReport.MAX_COMMENT_CHARS, cut.getString("comment").length());
    JSONObject none = new JSONObject(
        CoachPlanReport.body(CoachPlanReport.Reason.OFFENSIVE, null, null, ID));
    assertEquals("", none.getString("comment"));
    assertEquals("offensive", none.getString("reason"));
  }

  @Test
  public void eachReportGetsItsOwnId() throws Exception {
    String a = new JSONObject(CoachPlanReport.body(CoachPlanReport.Reason.OTHER, "", "1"))
        .getString("report_id");
    String b = new JSONObject(CoachPlanReport.body(CoachPlanReport.Reason.OTHER, "", "1"))
        .getString("report_id");
    assertNotEquals(a, b);
    assertEquals(a.toLowerCase(), a);
  }
}
