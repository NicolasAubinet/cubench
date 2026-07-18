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

  private static final String SOLVED = CubeState.SOLVED_FACELETS;
  private static final String FACES = "URFDLB";

  /** Facelet offsets within a face: corners at the four points, edges between them. */
  private static final int[] CORNER_POSITIONS = {0, 2, 6, 8};
  private static final int[] EDGE_POSITIONS = {1, 3, 5, 7};

  /** Corner facelet indices, one triple per corner (URF, UFL, ...), as in {@code CubieCube}. */
  private static final int[][] CORNERS = {
    {8, 9, 20}, {6, 18, 38}, {0, 36, 47}, {2, 45, 11},
    {29, 26, 15}, {27, 44, 24}, {33, 53, 42}, {35, 17, 51},
  };

  /** Edge facelet indices, one pair per edge (UR, UF, ...), as in {@code CubieCube}. */
  private static final int[][] EDGES = {
    {5, 10}, {7, 19}, {3, 37}, {1, 46}, {32, 16}, {28, 25},
    {30, 43}, {34, 52}, {23, 12}, {21, 41}, {50, 39}, {48, 14},
  };

  /** The 4 F2L slots of each cross face: a first-layer corner and the middle edge beside it. */
  private static final int[][] SLOT_CORNERS = new int[6][4];
  private static final int[][] SLOT_EDGES = new int[6][4];

  /** Each slot's code, carrying the two faces it sits between ("pair_rf") so it can be told apart. */
  private static final String[][] SLOT_CODES = new String[6][4];

  static {
    for (int face = 0; face < 6; face++) {
      int slot = 0;
      for (int corner = 0; corner < CORNERS.length; corner++) {
        if (!touches(CORNERS[corner], face)) {
          continue;
        }
        char[] sides = sideColours(CORNERS[corner], face);
        SLOT_CORNERS[face][slot] = corner;
        SLOT_EDGES[face][slot] = edgeBetween(sides[0], sides[1]);
        SLOT_CODES[face][slot] = ("pair_" + sides[0] + sides[1]).toLowerCase(Locale.US);
        slot++;
      }
    }
  }

  private final Long[][] times = new Long[6][STEP_NAMES.length]; // [cross face][step]
  private final Long[][] subGoalTimes = new Long[6][SUB_GOAL_COUNT]; // [cross face][sub-goal]
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
    boolean solved = SOLVED.equals(facelets);
    for (int face = 0; face < 6; face++) {
      boolean cross = crossDone(facelets, face);
      boolean f2l = cross && slotsDone(facelets, face); // the 4 slots are the whole first two layers
      boolean edgesOriented = lastLayerOriented(facelets, face, EDGE_POSITIONS);
      boolean cornersOriented = lastLayerOriented(facelets, face, CORNER_POSITIONS);

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

      markStep(face, CROSS, cross, timestampMs);
      markStep(face, F2L, f2l, timestampMs);
      markStep(face, OLL, oll, timestampMs);
      markStep(face, PLL, solved, timestampMs);
    }
    updateCrossFace();
  }

  /** Dated at its first completion, like a step: the parts that follow disturb it in passing. */
  private void markSubGoal(int face, int subGoal, boolean done, long timestampMs) {
    if (done && subGoalTimes[face][subGoal] == null) {
      subGoalTimes[face][subGoal] = timestampMs;
    }
  }

  private void markStep(int face, int step, boolean done, long timestampMs) {
    if (done && times[face][step] == null) {
      times[face][step] = timestampMs;
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
    for (int[] edge : EDGES) {
      if (touches(edge, face) && !inPlace(facelets, edge)) {
        return false;
      }
    }
    return true;
  }

  /** One F2L slot: its first-layer corner and the middle edge beside it are both in place. */
  private static boolean slotDone(String facelets, int face, int slot) {
    return inPlace(facelets, CORNERS[SLOT_CORNERS[face][slot]])
        && inPlace(facelets, EDGES[SLOT_EDGES[face][slot]]);
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
    int opposite = opposite(face);
    char colour = FACES.charAt(opposite);
    for (int position : positions) {
      if (facelets.charAt(opposite * 9 + position) != colour) {
        return false;
      }
    }
    return true;
  }

  private static boolean touches(int[] piece, int face) {
    for (int facelet : piece) {
      if (SOLVED.charAt(facelet) == FACES.charAt(face)) {
        return true;
      }
    }
    return false;
  }

  private static boolean inPlace(String facelets, int[] piece) {
    for (int facelet : piece) {
      if (facelets.charAt(facelet) != SOLVED.charAt(facelet)) {
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
      char colour = SOLVED.charAt(facelet);
      if (colour != FACES.charAt(face)) {
        sides[found++] = colour;
      }
    }
    return sides;
  }

  private static int edgeBetween(char first, char second) {
    for (int edge = 0; edge < EDGES.length; edge++) {
      char a = SOLVED.charAt(EDGES[edge][0]);
      char b = SOLVED.charAt(EDGES[edge][1]);
      if ((a == first && b == second) || (a == second && b == first)) {
        return edge;
      }
    }
    throw new IllegalStateException("No edge between " + first + " and " + second);
  }

  private static int opposite(int face) {
    return (face + 3) % 6;
  }

  /** The face the cross was built on, or null before any cross completes. */
  public Face getCrossFace() {
    return crossFace == null ? null : Face.valueOf(String.valueOf(FACES.charAt(crossFace)));
  }

  @Override
  public int stepCount() {
    return STEP_NAMES.length;
  }

  @Override
  public String stepName(int index) {
    return STEP_NAMES[index];
  }

  @Override
  public Long getStepTimestampMs(int index) {
    return crossFace == null ? null : times[crossFace][index];
  }

  /**
   * A last-layer turn opening OLL or PLL is an AUF: the solver is squaring the case up to read it,
   * not solving it yet. Cross and F2L have no such move — their last-layer turns do build the step.
   */
  @Override
  public boolean isAlignmentMove(int step, CubeMove move) {
    if (crossFace == null || (step != OLL && step != PLL)) {
      return false;
    }
    return move.getFace() == Face.valueOf(String.valueOf(FACES.charAt(opposite(crossFace))));
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
    Long f2l = getStepTimestampMs(F2L);
    if (cross == null || f2l == null) {
      return false;
    }
    return cross < f2l || f2l == solveStartMs;
  }
}
