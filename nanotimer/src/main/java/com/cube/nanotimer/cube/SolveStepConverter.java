package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.step.StepTime;
import com.cube.nanotimer.vo.PieceMark;
import com.cube.nanotimer.vo.SolveStep;
import java.util.ArrayList;
import java.util.List;

/** Converts the smart cube analyzer's step times into the datamodel breakdown the app stores and shows. */
public final class SolveStepConverter {

  private SolveStepConverter() {
  }

  public static List<SolveStep> toSolveSteps(List<StepTime> stepTimes) {
    List<SolveStep> steps = new ArrayList<>(stepTimes.size());
    for (StepTime step : stepTimes) {
      steps.add(toSolveStep(step));
    }
    return steps;
  }

  private static SolveStep toSolveStep(StepTime step) {
    List<SolveStep> subSteps = new ArrayList<>(step.getSubSteps().size());
    for (StepTime subStep : step.getSubSteps()) {
      subSteps.add(toSolveStep(subStep));
    }
    return new SolveStep(step.getStepIndex(), step.getStepName(),
        step.getRecognitionMs(), step.getExecutionMs(), subSteps, step.isComplete(),
        toPieceMarks(step.getPieceMarks()));
  }

  /** The same marks in the datamodel's own terms, which is the seam this class exists to cross. */
  private static List<PieceMark> toPieceMarks(
      List<com.cube.nanotimer.smartcube.step.PieceMark> marks) {
    List<PieceMark> converted = new ArrayList<>(marks.size());
    for (com.cube.nanotimer.smartcube.step.PieceMark mark : marks) {
      converted.add(PieceMark.valueOf(mark.name()));
    }
    return converted;
  }
}
