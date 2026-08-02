package com.cube.nanotimer.smartcube.step;

import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Detects the CFOP milestones (Cross, F2L, OLL, PLL) on the live facelet stream. Each one is a
 * predicate on the net state, so any solving order reaches it and a skipped step simply completes
 * with the previous one. A step is dated at its first completion and never retracted: later steps
 * routinely disturb earlier ones for a few moves (an F2L insertion lifts a cross edge out and back).
 *
 * <p>The steps that are built in parts carry sub-steps — F2L its 4 slots, OLL its edge and corner
 * orientation, PLL its corner and edge permutation — so the pauses <em>between</em> the parts are
 * counted as recognition rather than disappearing into the step's execution. Sub-steps complete in
 * whatever order the solver works in, and are dated by the run they were in when the step completed
 * (a slot broken and rebuilt counts from the rebuild). A one-look OLL or PLL completes both of its
 * sub-steps on the same move, leaving one of them zero.
 *
 * <p>The cross face is auto-detected. All six candidates are tracked, and F2L completion confirms
 * which one the solve was actually built on — so a cross that happens to be complete on some other
 * face (in the scramble, or in passing) is discarded rather than mistaken for the real one.
 */
public final class CFOPStepDetector implements StepDetector {

  public static final int CROSS = 0;
  public static final int F2L = 1;
  public static final int OLL = 2;
  public static final int PLL = 3;

  private static final String[] STEP_NAMES = {"cross", "f2l", "oll", "pll"};

  /**
   * PLL has no parts. A two-look PLL permutes the corners first, but in a one-look algorithm the
   * corners often fall into place a move or two before the edges anyway — so the split cannot tell
   * the two apart from the cube alone, and a one-look solve would be reported as a two-look. A
   * two-look PLL is simply a longer execution.
   */
  /** Step codes, localized when displayed. F2L's four entries only carry the count: each pair is
   * coded by the slot it sits in ({@link #SLOT_CODES}), but shown by the order it was built. */
  private static final String[][] SUB_STEP_NAMES = {
    {},
    {"", "", "", ""},
    {"edges", "corners"},
    {},
  };

  /** Where each step's sub-goals start in the flat sub-goal arrays. */
  private static final int[] SUB_STEP_OFFSET = {0, 0, 4, 6};
  private static final int SUB_GOAL_COUNT = 6;

  /** The 4 F2L slots of each cross face: a first-layer corner and the middle edge beside it. */
  private static final int[][] SLOT_CORNERS = new int[6][4];
  private static final int[][] SLOT_EDGES = new int[6][4];

  /** Each slot's code, carrying the two faces it sits between ("pair_rf") so it can be told apart. */
  private static final String[][] SLOT_CODES = new String[6][4];

  static {
    for (int face = 0; face < 6; face++) {
      int slot = 0;
      for (int corner = 0; corner < Cubies.CORNERS.length; corner++) {
        if (!Cubies.touches(Cubies.CORNERS[corner], face)) {
          continue;
        }
        char[] sides = sideColours(Cubies.CORNERS[corner], face);
        SLOT_CORNERS[face][slot] = corner;
        SLOT_EDGES[face][slot] = edgeBetween(sides[0], sides[1]);
        SLOT_CODES[face][slot] = ("pair_" + sides[0] + sides[1]).toLowerCase(Locale.US);
        slot++;
      }
    }
  }

  private final Long[][] times = new Long[6][STEP_NAMES.length]; // [cross face][step]
  private final Long[][] subGoalTimes = new Long[6][SUB_GOAL_COUNT]; // [cross face][sub-goal]
  private final String[][] states = new String[6][STEP_NAMES.length]; // [cross face][step]
  private final Long[] reported = new Long[STEP_NAMES.length];

  private Integer crossFace; // provisional until F2L confirms it
  private boolean confirmed;
  private long lastTimestampMs;
  private long solveStartMs;

  @Override
  public void reset(CubeState startState, long startTimestampMs) {
    for (int face = 0; face < 6; face++) {
      Arrays.fill(times[face], null);
      Arrays.fill(subGoalTimes[face], null);
      Arrays.fill(states[face], null);
    }
    Arrays.fill(reported, null);
    crossFace = null;
    confirmed = false;
    lastTimestampMs = startTimestampMs;
    solveStartMs = startTimestampMs;
    evaluate(startState.getFacelets(), startTimestampMs);
    for (int step = 0; step < STEP_NAMES.length; step++) {
      reported[step] = getStepTimestampMs(step);
    }
  }

  @Override
  public List<StepBoundaryEvent> onState(CubeState state, CubeMove lastMove) {
    if (lastMove != null) {
      lastTimestampMs = lastMove.getCubeTimestampMs();
    }
    evaluate(state.getFacelets(), lastTimestampMs);

    List<StepBoundaryEvent> events = new ArrayList<>();
    for (int step = 0; step < STEP_NAMES.length; step++) {
      Long time = getStepTimestampMs(step);
      if (time != null && !time.equals(reported[step])) {
        events.add(new StepBoundaryEvent(step, time));
      }
      reported[step] = time;
    }
    return events;
  }

  private void evaluate(String facelets, long timestampMs) {
    boolean solved = Cubies.SOLVED.equals(facelets);
    for (int face = 0; face < 6; face++) {
      boolean cross = crossDone(facelets, face);
      boolean f2l = cross && slotsDone(facelets, face); // the 4 slots are the whole first two layers
      boolean edgesOriented = lastLayerOriented(facelets, face, Cubies.EDGE_POSITIONS);
      boolean cornersOriented = lastLayerOriented(facelets, face, Cubies.CORNER_POSITIONS);

      boolean oll = f2l && edgesOriented && cornersOriented;
      for (int slot = 0; slot < 4; slot++) {
        markSubGoal(face, slot, slotDone(facelets, face, slot), timestampMs);
      }
      // A sub-goal is only watched inside its own step: orientation once the layers are there,
      // permutation once they are oriented. Otherwise one satisfied by chance earlier in the
      // solve would take the credit for a part the solver has not done yet.
      if (f2l) {
        markSubGoal(face, 4, edgesOriented, timestampMs);
        markSubGoal(face, 5, cornersOriented, timestampMs);
      }

      markStep(face, CROSS, cross, facelets, timestampMs);
      markStep(face, F2L, f2l, facelets, timestampMs);
      markStep(face, OLL, oll, facelets, timestampMs);
      markStep(face, PLL, solved, facelets, timestampMs);
    }
    updateCrossFace();
  }

  /** Dated at its first completion, like a step: the parts that follow disturb it in passing. */
  private void markSubGoal(int face, int subGoal, boolean done, long timestampMs) {
    if (done && subGoalTimes[face][subGoal] == null) {
      subGoalTimes[face][subGoal] = timestampMs;
    }
  }

  /** The state is kept with the time: it is what the step after this one has to solve. */
  private void markStep(int face, int step, boolean done, String facelets, long timestampMs) {
    if (done && times[face][step] == null) {
      times[face][step] = timestampMs;
      states[face][step] = facelets;
    }
  }

  private void updateCrossFace() {
    if (confirmed) {
      return;
    }
    // F2L confirms the cross face. When several faces reach it in the same state — a solve whose
    // last layer skips, so F2L completes at the solved state and every face looks done at once —
    // the real cross is the one built first, so break the tie on the earliest cross.
    int confirmedFace = -1;
    for (int face = 0; face < 6; face++) {
      if (times[face][F2L] != null
          && (confirmedFace == -1 || times[face][CROSS] < times[confirmedFace][CROSS])) {
        confirmedFace = face;
      }
    }
    if (confirmedFace != -1) {
      crossFace = confirmedFace;
      confirmed = true;
      return;
    }
    if (crossFace != null && times[crossFace][CROSS] != null) {
      return;
    }
    crossFace = null;
    for (int face = 0; face < 6; face++) {
      if (times[face][CROSS] != null) {
        crossFace = face;
        return;
      }
    }
  }

  /** The 4 edges of the cross face are in place. */
  private static boolean crossDone(String facelets, int face) {
    for (int[] edge : Cubies.EDGES) {
      if (Cubies.touches(edge, face) && !Cubies.inPlace(facelets, edge)) {
        return false;
      }
    }
    return true;
  }

  /** One F2L slot: its first-layer corner and the middle edge beside it are both in place. */
  private static boolean slotDone(String facelets, int face, int slot) {
    return Cubies.inPlace(facelets, Cubies.CORNERS[SLOT_CORNERS[face][slot]])
        && Cubies.inPlace(facelets, Cubies.EDGES[SLOT_EDGES[face][slot]]);
  }

  private static boolean slotsDone(String facelets, int face) {
    for (int slot = 0; slot < 4; slot++) {
      if (!slotDone(facelets, face, slot)) {
        return false;
      }
    }
    return true;
  }

  /** The given positions of the last-layer face all show its colour: those pieces are oriented. */
  private static boolean lastLayerOriented(String facelets, int face, int[] positions) {
    int opposite = Cubies.opposite(face);
    char colour = Cubies.FACES.charAt(opposite);
    for (int position : positions) {
      if (facelets.charAt(opposite * 9 + position) != colour) {
        return false;
      }
    }
    return true;
  }

  /** The two colours of a corner other than the given face's. */
  private static char[] sideColours(int[] corner, int face) {
    char[] sides = new char[2];
    int found = 0;
    for (int facelet : corner) {
      char colour = Cubies.SOLVED.charAt(facelet);
      if (colour != Cubies.FACES.charAt(face)) {
        sides[found++] = colour;
      }
    }
    return sides;
  }

  private static int edgeBetween(char first, char second) {
    for (int edge = 0; edge < Cubies.EDGES.length; edge++) {
      char a = Cubies.SOLVED.charAt(Cubies.EDGES[edge][0]);
      char b = Cubies.SOLVED.charAt(Cubies.EDGES[edge][1]);
      if ((a == first && b == second) || (a == second && b == first)) {
        return edge;
      }
    }
    throw new IllegalStateException("No edge between " + first + " and " + second);
  }

  /** The face the cross was built on, or null before any cross completes. */
  public Face getCrossFace() {
    return crossFace == null ? null : Cubies.faceAt(crossFace);
  }

  @Override
  public int stepCount() {
    return STEP_NAMES.length;
  }

  /**
   * The step's code, carrying which case it was — {@code "pll_jb"}, {@code "oll_21"} — the way an F2L
   * pair carries its slot. A case is named by the state the step <em>started</em> from, so it is the
   * milestone before it that says which one it was: F2L hands over an OLL, OLL hands over a PLL.
   *
   * <p>Plain {@code "oll"} or {@code "pll"} when there is no case to name yet, or none to name at all:
   * the step has not been reached, or the solve was not one whose last layer can be read.
   */
  @Override
  public String stepName(int index) {
    if (crossFace == null) {
      return STEP_NAMES[index];
    }
    if (index == OLL) {
      return named(OLL, LastLayerCases.orientation(states[crossFace][F2L], crossFace));
    }
    if (index == PLL) {
      return named(PLL, LastLayerCases.permutation(states[crossFace][OLL], crossFace));
    }
    return STEP_NAMES[index];
  }

  private static String named(int step, String lastLayerCase) {
    return lastLayerCase == null ? STEP_NAMES[step] : STEP_NAMES[step] + "_" + lastLayerCase;
  }

  @Override
  public Long getStepTimestampMs(int index) {
    return crossFace == null ? null : times[crossFace][index];
  }

  /**
   * A last-layer turn opening OLL or PLL is an AUF: the solver is squaring the case up to read it,
   * not solving it yet.
   *
   * <p>During F2L the same turn is usually the pair going in — {@code U R U' R'} opens on one — so
   * the face alone proves nothing there and it only counts as looking when the solver stopped again
   * after it. That is the difference the solve actually shows: a U turned straight out of was the
   * insertion starting, a U sat on was the solver still hunting the pair.
   *
   * <p>The cross is left out. Its edges start in the last layer too, but it is entered from
   * inspection rather than from reading a case, so the same reasoning does not carry over.
   */
  @Override
  public boolean isAlignmentMove(int step, CubeMove move, boolean pausedAfter) {
    if (crossFace == null || (step != F2L && step != OLL && step != PLL)) {
      return false;
    }
    if (step == F2L && !pausedAfter) {
      return false;
    }
    return move.getFace() == Cubies.faceAt(Cubies.opposite(crossFace));
  }

  @Override
  public int subStepCount(int step) {
    return SUB_STEP_NAMES[step].length;
  }

  @Override
  public String subStepName(int step, int subStep) {
    if (step != F2L) {
      return SUB_STEP_NAMES[step][subStep];
    }
    return crossFace == null ? "pair" : SLOT_CODES[crossFace][subStep];
  }

  @Override
  public Long getSubStepTimestampMs(int step, int subStep) {
    return crossFace == null ? null : subGoalTimes[crossFace][SUB_STEP_OFFSET[step] + subStep];
  }

  @Override
  public boolean isComplete() {
    return times[0][PLL] != null;
  }

  /**
   * A CFOP solve builds the cross first, then the F2L pairs on top of it — so the cross completes
   * strictly before F2L. A Roux solve builds its blocks first and leaves the last cross edges for
   * the end, so its cross completes together with F2L (the slots were already done); the same holds
   * for freestyle, where nothing assembles until the finish. Any step may be a skip and still match:
   * a skipped OLL/PLL only affects the later boundaries, never cross-before-F2L. The one case where
   * the cross does not precede F2L yet the solve is still CFOP is a scramble that already left the
   * first two layers solved (an OLL/PLL drill) — there both are skips, done at the solve start.
   */
  @Override
  public boolean matchesMethod() {
    Long cross = getStepTimestampMs(CROSS);
    if (cross == null) {
      return false;
    }
    Long f2l = getStepTimestampMs(F2L);
    if (f2l == null) {
      return matchesOnFirstPair(cross);
    }
    return cross < f2l || f2l == solveStartMs;
  }

  /**
   * The same check on a solve that stopped inside F2L: the cross was built before the first pair went
   * in. Roux is still rejected — mid-block it has no cross at all — and so is ZZ, whose cross only
   * completes around the second pair (EOLine leaves two of its edges out). Not strict, unlike the
   * complete check: a keyhole insertion finishes the cross <em>with</em> the pair rather than before
   * it. A lone cross proves nothing (every method builds one eventually), so it never matches.
   *
   * <p>The cross face is still provisional here — only F2L confirms it — so this rests on a guess no
   * later step will correct. It takes an accidental cross <em>and</em> one of that same face's slots
   * to mislead it, which is why the guess is worth trusting this far and no further.
   */
  private boolean matchesOnFirstPair(long crossMs) {
    Long firstPair = null;
    for (int subStep = 0; subStep < subStepCount(F2L); subStep++) {
      Long pairMs = getSubStepTimestampMs(F2L, subStep);
      if (pairMs != null && (firstPair == null || pairMs < firstPair)) {
        firstPair = pairMs;
      }
    }
    return firstPair != null && crossMs <= firstPair;
  }
}
