package com.cube.nanotimer.smartcube.step;

import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Detects the Roux milestones (first block, second block, CMLL, last six edges) on the live facelet
 * stream. Like the CFOP detector these are net-state predicates, so any building order reaches them
 * and the blocks may be built on any pair of faces — all 24 orientations are tracked, and the second
 * block confirms which one the solve was really built on.
 *
 * <p>Two things make Roux harder to read off the state than CFOP.
 *
 * <p><b>The frame moves.</b> Roux solves its last six edges with M moves, and a smart cube reports
 * an M as {@code R} then {@code L'} — the core turned, so relative to the centres the state is
 * written against, it is the rest of the cube that moved. The solver's blocks sit still in their
 * hands while walking a quarter turn around the state. Every milestone is therefore read in the
 * rotation the blocks are currently sitting in ({@link FaceletRotations}), which is the one the
 * solver sees; a milestone with the blocks half-turned, between the {@code R} and the {@code L'},
 * is simply not read until the pair lands.
 *
 * <p><b>CMLL ends on no fixed state.</b> Its corners are solved relative to the blocks but the U
 * layer is left wherever it lies, so the milestone is "solved after some AUF" rather than an exact
 * placement — and it is split into orienting and permuting, which a two-look solver does as two
 * steps and a one-look solver reaches on the same move, leaving one of them at zero.
 */
public final class RouxStepDetector implements StepDetector {

  public static final int FB = 0;
  public static final int SB = 1;
  public static final int CMLL = 2;
  public static final int LSE = 3;

  private static final String[] STEP_NAMES = {"fb", "sb", "cmll", "lse"};

  /** Step codes, localized when displayed. The blocks are built in one go and carry no parts. */
  private static final String[][] SUB_STEP_NAMES = {
    {},
    {},
    {"cmll_orient", "cmll_permute"},
    {"lse_eo", "lse_ulur", "lse_l4e"},
  };

  /** Where each step's sub-goals start in the flat sub-goal arrays. */
  private static final int[] SUB_STEP_OFFSET = {0, 0, 0, 2};
  private static final int SUB_GOAL_COUNT = 5;

  private static final int ORIENTED = 0, PERMUTED = 1, EO = 2, UL_UR = 3, L4E = 4;

  private static final int ORIENTATIONS = FaceletRotations.COUNT;

  /** The two blocks' facelets, home positions, per orientation: [orientation][block][facelet]. */
  private static final int[][][] BLOCKS = new int[ORIENTATIONS][2][];

  /** The four last-layer corner slots in the order a U turn moves them through. */
  private static final int[][][] CORNER_SLOTS = new int[ORIENTATIONS][][];

  /** The six last edges, each written with its up- or down-facing facelet first. */
  private static final int[][][] EDGE_SLOTS = new int[ORIENTATIONS][][];

  /** The two edges of the down face that Roux leaves for the very end, and CFOP starts with. */
  private static final int[][] LAST_DOWN_EDGES = new int[ORIENTATIONS][];

  /** The rotations that leave this orientation's blocks where they are: the M-move drift. */
  private static final int[][] DRIFTS = new int[ORIENTATIONS][];

  private static final char[] UP_COLOUR = new char[ORIENTATIONS];
  private static final char[] DOWN_COLOUR = new char[ORIENTATIONS];

  static {
    int[][] firstBlock = {
      Cubies.CORNERS[Cubies.DLF], Cubies.CORNERS[Cubies.DBL],
      Cubies.EDGES[Cubies.DL], Cubies.EDGES[Cubies.FL], Cubies.EDGES[Cubies.BL],
    };
    int[][] secondBlock = {
      Cubies.CORNERS[Cubies.DFR], Cubies.CORNERS[Cubies.DRB],
      Cubies.EDGES[Cubies.DR], Cubies.EDGES[Cubies.FR], Cubies.EDGES[Cubies.BR],
    };
    int[][] lastEdges = {
      Cubies.EDGES[Cubies.UL], Cubies.EDGES[Cubies.UR], Cubies.EDGES[Cubies.UF],
      Cubies.EDGES[Cubies.UB], Cubies.EDGES[Cubies.DF], Cubies.EDGES[Cubies.DB],
    };
    for (int orientation = 0; orientation < ORIENTATIONS; orientation++) {
      BLOCKS[orientation][0] = mapAll(orientation, firstBlock);
      BLOCKS[orientation][1] = mapAll(orientation, secondBlock);
      CORNER_SLOTS[orientation] = map(orientation, Arrays.copyOf(Cubies.CORNERS, 4));
      EDGE_SLOTS[orientation] = map(orientation, lastEdges);
      LAST_DOWN_EDGES[orientation] =
          mapAll(orientation, new int[][] {Cubies.EDGES[Cubies.DF], Cubies.EDGES[Cubies.DB]});
      int left = FaceletRotations.face(orientation, Cubies.L);
      int right = FaceletRotations.face(orientation, Cubies.R);
      DRIFTS[orientation] = drifts(left, right);
      UP_COLOUR[orientation] = Cubies.FACES.charAt(FaceletRotations.face(orientation, Cubies.U));
      DOWN_COLOUR[orientation] = Cubies.FACES.charAt(FaceletRotations.face(orientation, Cubies.D));
    }
  }

  private final Long[][] times = new Long[ORIENTATIONS][STEP_NAMES.length];
  private final Long[][] subGoalTimes = new Long[ORIENTATIONS][SUB_GOAL_COUNT];
  private final Long[] reported = new Long[STEP_NAMES.length];

  /** Whether the down face's last two edges were still out when the blocks were finished. */
  private final Boolean[] lastEdgesOutAtSecondBlock = new Boolean[ORIENTATIONS];

  private final int[] liveDrift = new int[ORIENTATIONS];

  private Integer orientation; // provisional until the second block confirms it
  private boolean confirmed;
  private long lastTimestampMs;
  private long solveStartMs;

  @Override
  public void reset(CubeState startState, long startTimestampMs) {
    for (int candidate = 0; candidate < ORIENTATIONS; candidate++) {
      Arrays.fill(times[candidate], null);
      Arrays.fill(subGoalTimes[candidate], null);
      lastEdgesOutAtSecondBlock[candidate] = null;
      liveDrift[candidate] = FaceletRotations.IDENTITY;
    }
    Arrays.fill(reported, null);
    orientation = null;
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
    for (int candidate = 0; candidate < ORIENTATIONS; candidate++) {
      int drift = -1;
      boolean firstBlock = false;
      for (int rotation : DRIFTS[candidate]) {
        if (!inPlace(facelets, BLOCKS[candidate][0], rotation)) {
          continue;
        }
        firstBlock = true;
        if (inPlace(facelets, BLOCKS[candidate][1], rotation)) {
          drift = rotation;
          break;
        }
      }
      markStep(candidate, FB, firstBlock, timestampMs);
      if (drift < 0) {
        continue;
      }
      liveDrift[candidate] = drift;
      markStep(candidate, SB, true, timestampMs);
      if (lastEdgesOutAtSecondBlock[candidate] == null) {
        lastEdgesOutAtSecondBlock[candidate] =
            !inPlace(facelets, LAST_DOWN_EDGES[candidate], drift);
      }

      // A sub-goal is only watched inside its own step, so one satisfied by chance earlier in the
      // solve does not take the credit for a part the solver has not done yet.
      boolean oriented = cornersOriented(facelets, candidate, drift);
      boolean permuted = cornersPermuted(facelets, candidate, drift);
      markSubGoal(candidate, ORIENTED, oriented, timestampMs);
      markSubGoal(candidate, PERMUTED, permuted, timestampMs);
      boolean cmll = oriented && permuted;
      markStep(candidate, CMLL, cmll, timestampMs);

      if (cmll) {
        boolean edgesOriented = edgesOriented(facelets, candidate, drift);
        markSubGoal(candidate, EO, edgesOriented, timestampMs);
        if (edgesOriented) {
          markSubGoal(candidate, UL_UR, sideEdgesPlaced(facelets, candidate, drift), timestampMs);
        }
      }
      markStep(candidate, LSE, solved, timestampMs);
      markSubGoal(candidate, L4E, solved, timestampMs);
    }
    updateOrientation();
  }

  /** Dated at its first completion, like a step: the parts that follow disturb it in passing. */
  private void markSubGoal(int candidate, int subGoal, boolean done, long timestampMs) {
    if (done && subGoalTimes[candidate][subGoal] == null) {
      subGoalTimes[candidate][subGoal] = timestampMs;
    }
  }

  private void markStep(int candidate, int step, boolean done, long timestampMs) {
    if (done && times[candidate][step] == null) {
      times[candidate][step] = timestampMs;
    }
  }

  /**
   * The second block confirms the orientation, the way F2L confirms CFOP's cross face. Every
   * orientation reaches both blocks on a solved cube, so when several land in the same state the
   * real one is the one whose first block was built earliest.
   */
  private void updateOrientation() {
    if (confirmed) {
      return;
    }
    int best = -1;
    for (int candidate = 0; candidate < ORIENTATIONS; candidate++) {
      if (times[candidate][SB] != null
          && (best == -1 || times[candidate][FB] < times[best][FB])) {
        best = candidate;
      }
    }
    if (best != -1) {
      orientation = best;
      confirmed = true;
      return;
    }
    if (orientation != null && times[orientation][FB] != null) {
      return;
    }
    orientation = null;
    for (int candidate = 0; candidate < ORIENTATIONS; candidate++) {
      if (times[candidate][FB] != null) {
        orientation = candidate;
        return;
      }
    }
  }

  /**
   * Whether the pieces are home, read in the given rotation: the piece belonging at facelet
   * {@code home} shows its own colour, wherever that rotation has carried it.
   */
  private static boolean inPlace(String facelets, int[] homes, int rotation) {
    for (int home : homes) {
      if (facelets.charAt(FaceletRotations.apply(rotation, home)) != Cubies.SOLVED.charAt(home)) {
        return false;
      }
    }
    return true;
  }

  /** Every last-layer corner shows the up colour on top: the corners are oriented. */
  private static boolean cornersOriented(String facelets, int candidate, int drift) {
    for (int[] slot : CORNER_SLOTS[candidate]) {
      if (facelets.charAt(FaceletRotations.apply(drift, slot[0])) != Cubies.SOLVED.charAt(slot[0])) {
        return false;
      }
    }
    return true;
  }

  /**
   * The corners are permuted when one AUF would place them all — the U layer is left wherever the
   * last algorithm ended, so the slots are read as a cycle rather than as fixed positions. Twists are
   * ignored here: orienting them is the other half of the step, and a solver may do it either way
   * round.
   */
  private static boolean cornersPermuted(String facelets, int candidate, int drift) {
    return Cubies.placingTurns(facelets, CORNER_SLOTS[candidate], drift) != 0;
  }

  /**
   * The six remaining edges are oriented when each shows an up or down colour on the up or down
   * face — the state from which the rest of the step needs only M and U turns.
   */
  private static boolean edgesOriented(String facelets, int candidate, int drift) {
    for (int[] slot : EDGE_SLOTS[candidate]) {
      char colour = facelets.charAt(FaceletRotations.apply(drift, slot[0]));
      if (colour != UP_COLOUR[candidate] && colour != DOWN_COLOUR[candidate]) {
        return false;
      }
    }
    return true;
  }

  /** The two edges beside the blocks are home, leaving only the middle slice to permute. */
  private static boolean sideEdgesPlaced(String facelets, int candidate, int drift) {
    return inPlace(facelets, EDGE_SLOTS[candidate][0], drift)
        && inPlace(facelets, EDGE_SLOTS[candidate][1], drift);
  }

  private static int[][] map(int rotation, int[][] pieces) {
    int[][] mapped = new int[pieces.length][];
    for (int piece = 0; piece < pieces.length; piece++) {
      mapped[piece] = new int[pieces[piece].length];
      for (int facelet = 0; facelet < pieces[piece].length; facelet++) {
        mapped[piece][facelet] = FaceletRotations.apply(rotation, pieces[piece][facelet]);
      }
    }
    return mapped;
  }

  private static int[] mapAll(int rotation, int[][] pieces) {
    int[][] mapped = map(rotation, pieces);
    int size = 0;
    for (int[] piece : mapped) {
      size += piece.length;
    }
    int[] flat = new int[size];
    int next = 0;
    for (int[] piece : mapped) {
      System.arraycopy(piece, 0, flat, next, piece.length);
      next += piece.length;
    }
    return flat;
  }

  private static int[] drifts(int left, int right) {
    int[] aboutLeft = FaceletRotations.about(left);
    int[] found = new int[4];
    int count = 0;
    for (int rotation : aboutLeft) {
      if (FaceletRotations.face(rotation, right) == right) {
        found[count++] = rotation;
      }
    }
    return Arrays.copyOf(found, count);
  }

  /** The pair of faces the blocks were built on, or null before either block completes. */
  public Face getLeftFace() {
    return orientation == null ? null : Cubies.faceAt(FaceletRotations.face(orientation, Cubies.L));
  }

  public Face getDownFace() {
    return orientation == null ? null : Cubies.faceAt(FaceletRotations.face(orientation, Cubies.D));
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
    return orientation == null ? null : times[orientation][index];
  }

  /**
   * A last-layer turn opening CMLL is an AUF: the solver is squaring the case up to read it. The
   * blocks have no such move, and the last six edges are solved with the same U turns they are
   * built from, so there the turn does belong to the step.
   */
  @Override
  public boolean isAlignmentMove(int step, CubeMove move, boolean pausedAfter) {
    if (orientation == null || step != CMLL) {
      return false;
    }
    int up = FaceletRotations.face(liveDrift[orientation],
        FaceletRotations.face(orientation, Cubies.U));
    return move.getFace() == Cubies.faceAt(up);
  }

  @Override
  public int subStepCount(int step) {
    return SUB_STEP_NAMES[step].length;
  }

  @Override
  public String subStepName(int step, int subStep) {
    return SUB_STEP_NAMES[step][subStep];
  }

  @Override
  public Long getSubStepTimestampMs(int step, int subStep) {
    return orientation == null
        ? null : subGoalTimes[orientation][SUB_STEP_OFFSET[step] + subStep];
  }

  @Override
  public boolean isComplete() {
    return times[FaceletRotations.IDENTITY][LSE] != null;
  }

  /**
   * A Roux solve builds a block, then the block beside it, then the last-layer corners, and only
   * then the six edges through the middle. What tells it apart from a method that builds a cross
   * first is not that order — CFOP's first two layers contain both blocks too, and reach them in
   * some order — but what is missing when they are done: Roux leaves the two down-face edges of the
   * middle slice for the very end, where every cross-first method has them in from the start. So the
   * blocks must have been finished with those edges still out.
   *
   * <p>A block alone proves nothing, since every method assembles one eventually. Blocks already
   * built at the solve start are a drill on the steps that follow: nothing was built, so there is
   * nothing to tell the methods apart by, and the check does not apply.
   */
  @Override
  public boolean matchesMethod() {
    Long firstBlock = getStepTimestampMs(FB);
    Long secondBlock = getStepTimestampMs(SB);
    if (firstBlock == null || secondBlock == null) {
      return false;
    }
    if (firstBlock > secondBlock) {
      return false;
    }
    if (secondBlock > solveStartMs) {
      if (firstBlock.equals(secondBlock) || !Boolean.TRUE.equals(lastEdgesOutAtSecondBlock[orientation])) {
        return false;
      }
    }
    Long corners = getStepTimestampMs(CMLL);
    if (corners == null) {
      return true;
    }
    Long edges = getStepTimestampMs(LSE);
    return edges == null || corners <= edges;
  }
}
