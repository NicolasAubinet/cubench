package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;

import com.cube.nanotimer.vo.SolveStep;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class SolveStepsFormatTest {

  @Test
  public void formatsStepsAndTheirPartsInDbColumnOrder() {
    List<SolveStep> none = Collections.emptyList();
    SolveStep pair = new SolveStep(1, "pair_rf", 550, 1480, none);
    List<SolveStep> steps = Arrays.asList(
        new SolveStep(0, "cross", 790, 1520, none),
        new SolveStep(1, "f2l", 0, 0, Collections.singletonList(pair)));
    assertEquals("0:cross:790:1520 1:f2l:0:0 1.0:pair_rf:550:1480", SolveStepsFormat.format(steps));
  }

  @Test
  public void emptyStepsFormatToAnEmptyString() {
    assertEquals("", SolveStepsFormat.format(Collections.<SolveStep>emptyList()));
  }
}
