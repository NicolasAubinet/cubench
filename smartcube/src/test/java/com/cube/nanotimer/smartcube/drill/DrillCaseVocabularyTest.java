package com.cube.nanotimer.smartcube.drill;

import com.cube.nanotimer.coach.CoachPayloadBuilder;
import com.cube.nanotimer.drill.DrillSpec;
import com.cube.nanotimer.step.LastLayerAlgorithms;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Every case a payload may quote can be dealt, which is a card's drill button working rather than
 * doing nothing.
 *
 * <p>A card and its button fail separately. Every figure a card cites can resolve and the case it
 * names can be one the payload really sent, while the drill behind the button asks for a case this
 * app has no scramble for, and neither {@code CoachPlan.uncited} nor {@code unknownCodes} reads the
 * case list inside a drill spec. The two ends have to be pinned to each other instead: what
 * {@link CoachPayloadBuilder#CASE_FAMILIES} lets out is what {@link DrillSession} has to take in.
 *
 * <p>The codes are built from {@link LastLayerAlgorithms} rather than from the scramble table, so a
 * case added to the algorithms and to nothing else fails here instead of reaching a user as a button
 * with nothing behind it. It has to live in this module because the scrambles do, which is also why
 * no plan writer can ever own this check.
 */
@RunWith(JUnit4.class)
public class DrillCaseVocabularyTest {

  /** Fixed, so a failure is a case that cannot be dealt rather than a draw that went badly. */
  private static final long SEED = 7;

  @Test
  public void testEveryCaseTheAllowlistCanEmitCanBeDealt() {
    List<String> cases = allowedCases();
    Assert.assertEquals(57 + 21, cases.size());

    DrillSession session = new DrillSession(specFor(cases), new Random(SEED));
    Assert.assertEquals("cases the drill runner has no scramble for",
        Collections.<String>emptyList(), session.getUnknownCases());

    Set<String> dealt = new HashSet<String>();
    for (int rep = 0; rep < cases.size(); rep++) {
      Assert.assertTrue("the drill ran out after " + rep + " reps", session.nextRep());
      Assert.assertNotNull("no scramble for " + session.getCurrentCase(),
          session.getCurrentScramble());
      dealt.add(session.getCurrentCase());
    }
    Assert.assertEquals(new HashSet<String>(cases), dealt);
  }

  /** A code from a family the allowlist keeps out is dropped, not dealt as if it named a case. */
  @Test
  public void testACodeThatNamesNoCaseIsNotDealt() {
    List<String> codes = Arrays.asList("pair_rf", "ollalg_45", "alg_gb", "cmll_orient", "lse_eo");
    DrillSession session = new DrillSession(specFor(codes), new Random(SEED));

    Assert.assertEquals(codes, session.getUnknownCases());
    Assert.assertFalse("a drill of nothing but unknown cases must not run", session.isRunnable());
    for (String code : codes) {
      Assert.assertFalse(code + " is quotable as a case",
          CoachPayloadBuilder.CASE_FAMILIES.contains(family(code)));
    }
  }

  /** Every last-layer case under the families the payload may quote, named as a solve names one. */
  private static List<String> allowedCases() {
    List<String> cases = new ArrayList<String>();
    for (String[] row : LastLayerAlgorithms.ORIENTATIONS) {
      cases.add("oll_" + row[0]);
    }
    for (String[] row : LastLayerAlgorithms.PERMUTATIONS) {
      cases.add("pll_" + row[0]);
    }
    for (String code : cases) {
      Assert.assertTrue(code + " is not from a family the payload may quote",
          CoachPayloadBuilder.CASE_FAMILIES.contains(family(code)));
    }
    return cases;
  }

  private static String family(String code) {
    return code.substring(0, code.indexOf('_'));
  }

  private static DrillSpec specFor(List<String> cases) {
    return new DrillSpec("vocabulary", DrillSpec.Type.CASE_EXECUTION, DrillSpec.Delivery.VIRTUAL,
        cases, DrillSpec.Selection.ROUND_ROBIN, cases.size(), 0, "every case");
  }
}
