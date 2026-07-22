package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.cube.nanotimer.vo.SolveStep;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class SolveStepsFormatTest {

  private static final List<SolveStep> NONE = Collections.emptyList();

  // A realistic CFOP breakdown: cross, F2L with four pairs, OLL with two looks, PLL.
  private static final String CFOP = "0:cross:0:2449 1:f2l:3279:15495 1.0:pair_fl:1294:2158"
      + " 1.1:pair_lb:622:4711 1.2:pair_br:1247:4891 1.3:pair_rf:116:3735 2:oll:2092:2934"
      + " 2.0:edges:1264:755 2.1:corners:828:2179 3:pll:1416:1711";

  @Test
  public void formatsStepsAndTheirPartsInDbColumnOrder() {
    SolveStep pair = new SolveStep(1, "pair_rf", 550, 1480, NONE);
    List<SolveStep> steps = Arrays.asList(
        new SolveStep(0, "cross", 790, 1520, NONE),
        new SolveStep(1, "f2l", 0, 0, Collections.singletonList(pair)));
    assertEquals("0:cross:790:1520 1:f2l:0:0 1.0:pair_rf:550:1480", SolveStepsFormat.format(steps));
  }

  @Test
  public void emptyStepsFormatToAnEmptyString() {
    assertEquals("", SolveStepsFormat.format(Collections.<SolveStep>emptyList()));
  }

  @Test
  public void nullAndBlankParseToAnEmptyList() {
    assertTrue(SolveStepsFormat.parse(null).isEmpty());
    assertTrue(SolveStepsFormat.parse("").isEmpty());
    assertTrue(SolveStepsFormat.parse("   ").isEmpty());
  }

  @Test
  public void fullBreakdownSurvivesTheRoundTripByteForByte() {
    assertEquals(CFOP, SolveStepsFormat.format(SolveStepsFormat.parse(CFOP)));
  }

  @Test
  public void parseRebuildsTheNestedStructure() {
    List<SolveStep> steps = SolveStepsFormat.parse(CFOP);
    assertEquals(4, steps.size());
    assertEquals("cross", steps.get(0).getName());
    assertEquals(0, steps.get(0).getSubSteps().size());
    SolveStep f2l = steps.get(1);
    assertEquals(1, f2l.getStepIndex());
    assertEquals(3279, f2l.getRecognitionMs());
    assertEquals(15495, f2l.getExecutionMs());
    assertEquals(4, f2l.getSubSteps().size());
    assertEquals("pair_lb", f2l.getSubSteps().get(1).getName());
    assertEquals(622, f2l.getSubSteps().get(1).getRecognitionMs());
    assertEquals(4711, f2l.getSubSteps().get(1).getExecutionMs());
    assertEquals(2, steps.get(2).getSubSteps().size());
    assertEquals("pll", steps.get(3).getName());
  }

  @Test
  public void objectRoundTripPreservesEveryField() {
    List<SolveStep> original = Arrays.asList(
        new SolveStep(0, "cross", 0, 2449, NONE),
        new SolveStep(2, "oll", 2092, 2934, Arrays.asList( // gap in indexes is legitimate
            new SolveStep(2, "edges", 1264, 755, NONE),
            new SolveStep(2, "corners", 828, 2179, NONE))));
    List<SolveStep> reparsed = SolveStepsFormat.parse(SolveStepsFormat.format(original));
    assertEquals(original.size(), reparsed.size());
    for (int i = 0; i < original.size(); i++) {
      assertStepEquals(original.get(i), reparsed.get(i));
    }
  }

  @Test
  public void extraWhitespaceBetweenTokensIsTolerated() {
    List<SolveStep> steps = SolveStepsFormat.parse("  0:cross:10:20   1:f2l:30:40  ");
    assertEquals(2, steps.size());
    assertEquals("f2l", steps.get(1).getName());
  }

  @Test
  public void zeroTimesAndLargeTimesAreCarried() {
    String stored = "0:cross:0:0 1:f2l:9999999999:9999999999";
    assertEquals(stored, SolveStepsFormat.format(SolveStepsFormat.parse(stored)));
  }

  @Test
  public void malformedTokensAreRejected() {
    assertParseFails("0:cross:10");                       // too few fields
    assertParseFails("0:cross:10:20:30");                 // too many fields
    assertParseFails("0:cross:10:x");                     // non-numeric time
    assertParseFails("x:cross:10:20");                    // non-numeric index
    assertParseFails("0:cross:-1:20");                    // negative recognition
    assertParseFails("0:cross:10:-20");                   // negative execution
    assertParseFails("-1:cross:10:20");                   // negative index
    assertParseFails("0::10:20");                         // empty name
    assertParseFails("garbage");                          // not a token at all
    assertParseFails("0:cross:10:20 junk");               // one bad token spoils the record
  }

  @Test
  public void partsMustFollowTheirStepInOrder() {
    assertParseFails("0.0:pair_fl:10:20");                    // part before any step
    assertParseFails("0:cross:10:20 1.0:pair_fl:10:20");      // part of a step not being read
    assertParseFails("0:f2l:10:20 0.1:pair_fl:10:20");        // first part must be .0
    assertParseFails("0:f2l:10:20 0.0:a:1:2 0.2:b:1:2");      // gap in part numbering
    assertParseFails("0:f2l:10:20 0.0:a:1:2 0.0:b:1:2");      // duplicate part number
    assertParseFails("0:f2l:10:20 0.x:pair_fl:10:20");        // non-numeric part number
  }

  @Test
  public void stepIndexesMustIncrease() {
    assertParseFails("1:cross:10:20 1:f2l:10:20");        // duplicate
    assertParseFails("1:cross:10:20 0:f2l:10:20");        // decreasing
  }

  @Test
  public void nonContiguousStepIndexesAreAccepted() {
    // The format carries whatever the DB holds; it is not its place to renumber rows.
    List<SolveStep> steps = SolveStepsFormat.parse("0:cross:10:20 2:oll:30:40 5:pll:50:60");
    assertEquals(3, steps.size());
    assertEquals(5, steps.get(2).getStepIndex());
  }

  @Test
  public void unserializableNamesFailAtFormatTimeNotAtParseTime() {
    assertFormatFails(new SolveStep(0, "has space", 1, 2, NONE));
    assertFormatFails(new SolveStep(0, "has:colon", 1, 2, NONE));
    assertFormatFails(new SolveStep(0, "", 1, 2, NONE));
    assertFormatFails(new SolveStep(0, null, 1, 2, NONE));
  }

  private void assertStepEquals(SolveStep expected, SolveStep actual) {
    assertEquals(expected.getStepIndex(), actual.getStepIndex());
    assertEquals(expected.getName(), actual.getName());
    assertEquals(expected.getRecognitionMs(), actual.getRecognitionMs());
    assertEquals(expected.getExecutionMs(), actual.getExecutionMs());
    assertEquals(expected.getSubSteps().size(), actual.getSubSteps().size());
    for (int i = 0; i < expected.getSubSteps().size(); i++) {
      assertStepEquals(expected.getSubSteps().get(i), actual.getSubSteps().get(i));
    }
  }

  private void assertParseFails(String stored) {
    try {
      SolveStepsFormat.parse(stored);
      fail("Expected parse to reject: \"" + stored + "\"");
    } catch (IllegalArgumentException expected) {
    }
  }

  private void assertFormatFails(SolveStep step) {
    try {
      SolveStepsFormat.format(new ArrayList<SolveStep>(Collections.singletonList(step)));
      fail("Expected format to reject step named \"" + step.getName() + "\"");
    } catch (IllegalArgumentException expected) {
    }
  }
}
