package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveStep;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;
import org.json.JSONObject;
import org.junit.Test;

/** A Roux solve read after the first block by the method's move set rather than the gyro. */
public class RouxFramesTest {

  /** Reported 2026-11-14: a MoYu V11 whose gyro swung and came back around every r and M. */
  private static final String REPORT = "/reports/a23413e6-8ae2-47bf-b46a-45674579c284.json";

  @Test
  public void theReportedSecondBlockReadsAsRUAndM() throws Exception {
    SolveSolution solution = reported(CubeMethod.ROUX);

    String sb = solution.getSteps().get(1).getMoves();
    for (String token : sb.replace(SolveSolution.GROUP_SEPARATOR, " ").trim().split(" +")) {
      assertTrue(sb, "URrM".indexOf(token.charAt(0)) >= 0);
    }
  }

  @Test
  public void theReportedSolveStillSolvesAsShown() throws Exception {
    JSONObject report = report();
    StringBuilder shown = new StringBuilder();
    for (SolveSolution.Step step : reported(CubeMethod.ROUX).getSteps()) {
      shown.append(step.getMoves().replace(SolveSolution.GROUP_SEPARATOR, " ")).append(' ');
    }

    assertTrue(shown.toString(), DisplayedSolutionReplay.solves(
        report.getJSONObject("solve").getString("scramble"), shown.toString()));
  }

  @Test
  public void cmllAndLastSixEdgesAreUnchanged() throws Exception {
    List<SolveSolution.Step> steps = reported(CubeMethod.ROUX).getSteps();

    assertEquals("M' R U R' U R U' R' U R U2 R'", steps.get(2).getMoves());
    assertEquals("U2 M' U M M' U2 M' U2 U M U M'" + SolveSolution.GROUP_SEPARATOR
        + "M2 U M' U2 M' U M2 U'" + SolveSolution.GROUP_SEPARATOR + "M2", steps.get(3).getMoves());
  }

  @Test
  public void otherMethodsKeepTheGyrosFrame() throws Exception {
    assertTrue(reported(null).getSteps().get(1).getMoves().contains("B"));
  }

  private static SolveSolution reported(CubeMethod method) throws Exception {
    JSONObject smartcube = report().getJSONObject("smartcube");
    List<SolveStep> steps = SolveStepsFormat.parse(smartcube.getString("steps"));
    return SolveSolution.from(smartcube.getString("moves"), steps, method);
  }

  static JSONObject report() throws Exception {
    try (InputStream in = RouxFramesTest.class.getResourceAsStream(REPORT);
        Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
      return new JSONObject(scanner.next());
    }
  }
}
