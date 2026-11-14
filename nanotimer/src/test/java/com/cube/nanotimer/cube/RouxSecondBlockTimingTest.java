package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.cube.nanotimer.vo.BlindMethod;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveStep;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;

/** Roux's second block has no recognition: the wait before it is a pause inside it, as in a cross. */
public class RouxSecondBlockTimingTest {

  /** As the report stored them: the first block ended at 30798, SB took 2608 + 22149. */
  private static final long FB_END_MS = 30_798;
  private static final long SB_TOTAL_MS = 2_608 + 22_149;

  @Test
  public void theWaitBeforeTheSecondBlockIsTurningTime() throws Exception {
    List<SolveStep> steps = reread().getSteps();

    assertEquals("sb", steps.get(1).getName());
    assertEquals(0, steps.get(1).getRecognitionMs());
    assertEquals(SB_TOTAL_MS, steps.get(1).getExecutionMs());
  }

  @Test
  public void theOtherStepsKeepTheirRecognition() throws Exception {
    List<SolveStep> steps = reread().getSteps();

    assertEquals(1_986, steps.get(2).getRecognitionMs()); // CMLL, as recorded
    assertEquals(7_565, steps.get(3).getRecognitionMs()); // LSE
  }

  @Test
  public void theWaitShowsAsTheSecondBlocksFirstPause() throws Exception {
    StoredSolveReplay.Result result = reread();
    SolveSolution solution = SolveSolution.from(result.getMoves(), result.getSteps(),
        CubeMethod.ROUX);

    long[] first = solution.getSteps().get(1).getPauseSpans().get(0);
    assertEquals(FB_END_MS, first[0]);
  }

  @Test
  public void anotherMethodReadingTheSameStepsCountsNoOpeningPause() throws Exception {
    StoredSolveReplay.Result result = reread();
    SolveSolution solution = SolveSolution.from(result.getMoves(), result.getSteps(), null);

    for (long[] pause : solution.getSteps().get(1).getPauseSpans()) {
      assertFalse(pause[0] == FB_END_MS);
    }
  }

  private static StoredSolveReplay.Result reread() throws Exception {
    JSONObject report = RouxFramesTest.report();
    StoredSolveReplay.Result result = StoredSolveReplay.reinterpret(
        report.getJSONObject("solve").getString("scramble"),
        report.getJSONObject("smartcube").getString("moves"), CubeMethod.ROUX,
        BlindMethod.THREE_STYLE);
    assertNotNull(result);
    return result;
  }
}
