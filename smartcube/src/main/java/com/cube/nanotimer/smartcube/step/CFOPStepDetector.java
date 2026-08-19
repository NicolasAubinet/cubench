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
 * <p>The steps that are built in parts carry sub-steps — F2L its 4 slots, OLL and PLL the algorithms
 * they took — so the pauses <em>between</em> the parts are counted as recognition rather than
 * disappearing into the step's execution. The slots complete in whatever order the solver works in,
 * and are dated by the run they were in when F2L completed (a slot broken and rebuilt counts from
 * the rebuild). A last layer step read right is one algorithm, and two looks are two.
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

  /** Kept apart from the "oll_" and "pll_" a step's own case is coded under: one is the case the
   * solver was given, the other the algorithm they answered it with, and averaging them together
   * says nothing. The two have a family each for the same reason, and OLL's is not "alg_" only
   * because that code is already written in stored history as PLL's. */
  private static final String PERMUTATION_ALGORITHM_PREFIX = "alg_";
  private static final String ORIENTATION_ALGORITHM_PREFIX = "ollalg_";

  /** The 4 slots F2L is built in. Each is coded by where it sits ({@link #SLOT_CODES}) and shown by
   * the order it was built, so there are no names to write here. */
  private static final int SLOT_COUNT = 4;

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
  private final Long[][] slotTimes = new Long[6][SLOT_COUNT]; // [cross face][F2L slot]
  private final String[][] states = new String[6][STEP_NAMES.length]; // [cross face][step]
  private final Long[] reported = new Long[STEP_NAMES.length];

  /** The first two layers are there, per face: what an OLL runs between. */
  private final boolean[] firstTwoLayers = new boolean[6];

  /** Everything but the last layer's permutation is done, per face: what a PLL runs between. */
  private final boolean[] permutationOnly = new boolean[6];

  private final AlgorithmTrack orientation = new AlgorithmTrack(false);
  private final AlgorithmTrack permutation = new AlgorithmTrack(true);

  private Integer crossFace; // provisional until F2L confirms it
  private boolean confirmed;
  private long lastTimestampMs;
  private long solveStartMs;

  @Override
  public void reset(CubeState startState, long startTimestampMs) {
    for (int face = 0; face < 6; face++) {
      Arrays.fill(times[face], null);
      Arrays.fill(slotTimes[face], null);
      Arrays.fill(states[face], null);
    }
    Arrays.fill(reported, null);
    orientation.reset();
    permutation.reset();
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
      firstTwoLayers[face] = f2l;
      permutationOnly[face] = oll;
      for (int slot = 0; slot < SLOT_COUNT; slot++) {
        markSlot(face, slot, slotDone(facelets, face, slot), timestampMs);
      }

      markStep(face, CROSS, cross, facelets, timestampMs);
      markStep(face, F2L, f2l, facelets, timestampMs);
      markStep(face, OLL, oll, facelets, timestampMs);
      markStep(face, PLL, solved, facelets, timestampMs);
    }
    updateCrossFace();
    if (crossFace != null) {
      // The cross face is settled by the time either watch opens: F2L confirms it, and neither the
      // F2L that hands over an OLL nor the OLL that hands over a PLL can complete before it does.
      orientation.track(states[crossFace][F2L], firstTwoLayers[crossFace], facelets, timestampMs,
          crossFace);
      permutation.track(states[crossFace][OLL], permutationOnly[crossFace], facelets, timestampMs,
          crossFace);
    }
  }

  /** Dated at its first completion, like a step: the pairs that follow disturb it in passing. */
  private void markSlot(int face, int slot, boolean done, long timestampMs) {
    if (done && slotTimes[face][slot] == null) {
      slotTimes[face][slot] = timestampMs;
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
    if (step == F2L) {
      return SLOT_COUNT;
    }
    AlgorithmTrack track = trackOf(step);
    return track == null ? 0 : track.count();
  }

  /** A last layer part is coded by the algorithm that was run ("alg_jb", "ollalg_21"), the way a
   * pair is by its slot. */
  @Override
  public String subStepName(int step, int subStep) {
    if (step == OLL) {
      return ORIENTATION_ALGORITHM_PREFIX + orientation.caseAt(subStep);
    }
    if (step == PLL) {
      return PERMUTATION_ALGORITHM_PREFIX + permutation.caseAt(subStep);
    }
    return crossFace == null ? "pair" : SLOT_CODES[crossFace][subStep];
  }

  @Override
  public Long getSubStepTimestampMs(int step, int subStep) {
    AlgorithmTrack track = trackOf(step);
    if (track != null) {
      return track.timeAt(subStep);
    }
    return crossFace == null ? null : slotTimes[crossFace][subStep];
  }

  /** The algorithms a step is read in parts of, or null for a step that is not read that way. */
  private AlgorithmTrack trackOf(int step) {
    if (step == OLL) {
      return orientation;
    }
    return step == PLL ? permutation : null;
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

  /**
   * The algorithms one last layer step took, which is normally one. A solver who misreads the case
   * executes an algorithm that leaves the layer some other way and has a second one to do — so every
   * return to the states the step runs between ends an algorithm, and each is named by the case it
   * would have solved rather than the one it was given. That is what tells a misread case from a
   * slow one, and it is also how a two-look OLL and a two-look PLL read.
   *
   * <p>An alignment turn ends nothing: it never leaves those states. Nor does a run that came back
   * to where it started — an algorithm begun and taken back names no case, so those moves stay with
   * the algorithm that follows rather than becoming one of their own.
   *
   * <p>A single algorithm that passed back through one of those states on its way would read as two,
   * which is the one thing that could invent a look the solver never took. {@code OneLookOllTest}
   * holds the standard algorithms to that, since OLL is where those states are the easier to hit: an
   * OLL runs between states that only need the first two layers, a PLL between states that need them
   * oriented as well.
   */
  private static final class AlgorithmTrack {

    /** Whether the step permutes the layer (a PLL) or orients it (an OLL). */
    private final boolean permutes;

    private final List<String> cases = new ArrayList<>();
    private final List<Long> times = new ArrayList<>();
    private String landedState; // what the algorithm being turned started from
    private boolean leftTheCase;
    private boolean finished;

    AlgorithmTrack(boolean permutes) {
      this.permutes = permutes;
    }

    void reset() {
      cases.clear();
      times.clear();
      landedState = null;
      leftTheCase = false;
      finished = false;
    }

    /**
     * @param handed the state the step was handed, or null before the step before it completed
     * @param resting whether the cube is at one of the states this step runs between
     */
    void track(String handed, boolean resting, String facelets, long timestampMs, int crossFace) {
      if (finished || handed == null) {
        return;
      }
      if (landedState == null) {
        landedState = handed;
        finished = done(landedState, crossFace); // the step was already solved on arrival
      }
      if (finished) {
        return;
      }
      if (!resting) {
        leftTheCase = true;
        return;
      }
      if (!leftTheCase) {
        return;
      }
      String executed = permutes
          ? LastLayerCases.permutationAlgorithm(landedState, facelets, crossFace)
          : LastLayerCases.orientationAlgorithm(landedState, facelets, crossFace);
      if (executed == null || LastLayerCases.SKIP.equals(executed)) {
        return;
      }
      cases.add(executed);
      times.add(Long.valueOf(timestampMs));
      landedState = facelets;
      leftTheCase = false;
      // Landed on nothing left to do: whatever is turned after it belongs to the step that follows.
      finished = done(facelets, crossFace);
    }

    private boolean done(String facelets, int crossFace) {
      String remaining = permutes ? LastLayerCases.permutation(facelets, crossFace)
          : LastLayerCases.orientation(facelets, crossFace);
      return LastLayerCases.SKIP.equals(remaining);
    }

    int count() {
      return cases.size();
    }

    String caseAt(int index) {
      return cases.get(index);
    }

    Long timeAt(int index) {
      return times.get(index);
    }
  }
}
