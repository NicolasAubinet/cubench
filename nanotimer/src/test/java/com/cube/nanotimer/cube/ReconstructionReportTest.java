package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.cube.nanotimer.vo.BlindMethod;
import com.cube.nanotimer.vo.CubeMethod;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONObject;
import org.junit.Test;

/** A report's smart-cube section is the stored fields verbatim, so a report replays as a fixture. */
public class ReconstructionReportTest {

  @Test
  public void theSmartcubeSectionKeepsTheStoredFieldsAsTheyAre() throws Exception {
    Map<String, String> fields = new LinkedHashMap<String, String>();
    fields.put("cube", "GAN i3");
    fields.put("method", "roux");
    fields.put("moves", RecordedSolveReplayTest.MOVES);
    fields.put("stopped_step", "2");

    JSONObject smartcube = new JSONObject(ReconstructionReport.smartcube(fields).toString());

    assertEquals(4, smartcube.length());
    assertEquals("GAN i3", smartcube.getString("cube"));
    assertEquals(RecordedSolveReplayTest.MOVES, smartcube.getString("moves"));
    assertEquals("2", smartcube.getString("stopped_step"));
  }

  @Test
  public void aReportsMovesReplayWithoutHandEditing() throws Exception {
    Map<String, String> fields = new LinkedHashMap<String, String>();
    fields.put("moves", RecordedSolveReplayTest.MOVES);
    JSONObject smartcube = new JSONObject(ReconstructionReport.smartcube(fields).toString());

    StoredSolveReplay.Result result = StoredSolveReplay.reinterpret(
        RecordedSolveReplayTest.SCRAMBLE, smartcube.getString("moves"), CubeMethod.ROUX,
        BlindMethod.THREE_STYLE);

    assertNotNull(result);
    assertEquals(CubeMethod.ROUX, result.getMethod());
  }

  @Test
  public void timesAreUtcIso() {
    assertEquals("2026-10-30T09:00:00Z", ReconstructionReport.iso(1_793_350_800_000L));
  }
}
