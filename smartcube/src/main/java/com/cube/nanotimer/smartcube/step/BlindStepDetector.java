package com.cube.nanotimer.smartcube.step;

import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Splits a blindfolded solve into memorisation, the two piece types in whichever order they were
 * solved, and the parity that fixes what an odd permutation leaves over. The solver taps to start,
 * memorises without touching the cube, and the first turn is the moment the two meet — so memo is
 * the one step read off a move rather than off the state.
 *
 * <p>Everything after it is read off <b>counts</b>: how many corners and how many edges are solved.
 * That is what makes one detector enough for every blind method — 3-style, M2 and Old Pochmann
 * differ in how a piece gets solved, not in when the count falls — and it needs no buffer, no letter
 * scheme and no memo to compare against.
 *
 * <p>Three things separate this from the sighted detectors.
 *
 * <p><b>The solve is anchored at the tap, not at the first move.</b> Everywhere else the first move
 * both starts the clock and opens the breakdown, which would make memo zero by construction.
 *
 * <p><b>Where a phase ends is decided by the scramble, not watched for.</b> An odd permutation
 * cannot be cycled away, so it leaves a pair of each type swapped for one last algorithm; an even
 * one leaves nothing. Read off the start state, that says outright whether a phase ends on two
 * pieces or on none — where waiting to see a pair standing would mean reading the counts through
 * the very algorithm that swaps it back, and inventing a parity out of any moment an algorithm
 * happened to pass through on its way.
 *
 * <p><b>Progress is the best the solve has reached, not the count of the moment.</b> An algorithm
 * disturbs half the cube while it runs and the state arrives after every quarter turn, so the
 * instantaneous counts swing wildly inside one. Between algorithms progress really is monotone —
 * nothing already solved gets disturbed, net — so the running minimum is both the honest reading and
 * a monotone one, which leaves a milestone with no way to be dated twice.
 */
public final class BlindStepDetector implements StepDetector {

  private static final int EDGES = 0, CORNERS = 1, TYPES = 2;

  /** Names in step order: memorisation, then whichever piece types and parity the solve had. */
  private static final String MEMO = "memo";
  private static final String PARITY = "parity";
  private static final String[] TYPE_NAMES = {"edges", "corners"};

  /** What a solve that never told the piece types apart is left calling its one turning step. */
  private static final String EXECUTION = "execution";

  /** The pair an odd permutation leaves over, which the parity algorithm swaps back. */
  private static final int PARITY_REMAINDER = 2;

  private final int[] best = new int[TYPES];
  private final int[] unsolved = new int[TYPES];
  private final Long[] cyclesMs = new Long[TYPES]; // the count first fell to the parity remainder
  private final Long[] fullyMs = new Long[TYPES]; // ...and to none left at all
  private final int[] othersLeftAtCycles = new int[TYPES];

  private Long memoMs;
  private Long solvedMs;
  private long lastTimestampMs;
  private boolean parity; // the scramble was an odd permutation, so a pair of each is left over

  @Override
  public void reset(CubeState startState, long startTimestampMs) {
    Arrays.fill(cyclesMs, null);
    Arrays.fill(fullyMs, null);
    Arrays.fill(othersLeftAtCycles, 0);
    best[EDGES] = Cubies.EDGES.length;
    best[CORNERS] = Cubies.CORNERS.length;
    memoMs = null;
    solvedMs = null;
    lastTimestampMs = startTimestampMs;
    parity = Cubies.isOddPermutation(startState.getFacelets());
    evaluate(startState.getFacelets(), startTimestampMs);
  }

  @Override
  public List<StepBoundaryEvent> onState(CubeState state, CubeMove lastMove) {
    if (lastMove != null) {
      lastTimestampMs = lastMove.getCubeTimestampMs();
      if (memoMs == null) {
        memoMs = lastTimestampMs; // memorising ends the instant the cube is first turned
      }
    }
    // Past the solved state nothing is read. A blind solver cannot see they are done and may turn on
    // thinking an orientation is still out; those turns are not the solve, and must not unfinish it.
    if (solvedMs == null) {
      evaluate(state.getFacelets(), lastTimestampMs);
    }
    return boundaries();
  }

  private void evaluate(String facelets, long timestampMs) {
    int rotation = frameOf(facelets);
    unsolved[EDGES] = unsolvedIn(facelets, rotation, Cubies.EDGES);
    unsolved[CORNERS] = unsolvedIn(facelets, rotation, Cubies.CORNERS);
    for (int type = 0; type < TYPES; type++) {
      best[type] = Math.min(best[type], unsolved[type]);
    }
    for (int type = 0; type < TYPES; type++) {
      if (best[type] <= remainder() && cyclesMs[type] == null) {
        cyclesMs[type] = timestampMs;
        othersLeftAtCycles[type] = best[1 - type];
      }
      if (best[type] == 0 && fullyMs[type] == null) {
        fullyMs[type] = timestampMs;
      }
    }
    // A cube solved before a single turn is one waiting to be scrambled, not a solve already over.
    if (memoMs != null && best[EDGES] == 0 && best[CORNERS] == 0) {
      solvedMs = timestampMs;
    }
  }

  /** What a phase of cycles leaves behind: nothing, or the pair only a parity algorithm can swap. */
  private int remainder() {
    return parity ? PARITY_REMAINDER : 0;
  }

  /**
   * The rotation the solve is being turned in. A slice is reported as its two face turns — the core
   * moved, so relative to the centres the state is written against it is everything else that
   * turned — and blind spends whole phases in slices. The frame is read back as the rotation that
   * accounts for the most solved pieces, which needs no tracking of the drift itself: the pieces the
   * solver has finished are the evidence of where their frame is.
   */
  private static int frameOf(String facelets) {
    int frame = FaceletRotations.IDENTITY;
    int mostSolved = -1;
    for (int rotation = 0; rotation < FaceletRotations.COUNT; rotation++) {
      int solved = Cubies.EDGES.length - unsolvedIn(facelets, rotation, Cubies.EDGES)
          + Cubies.CORNERS.length - unsolvedIn(facelets, rotation, Cubies.CORNERS);
      if (solved > mostSolved) {
        mostSolved = solved;
        frame = rotation;
      }
    }
    return frame;
  }

  private static int unsolvedIn(String facelets, int rotation, int[][] pieces) {
    int count = 0;
    for (int[] piece : pieces) {
      if (!Cubies.inPlace(facelets, piece, rotation)) {
        count++;
      }
    }
    return count;
  }

  /**
   * The solve as steps, in the order they happened: memorisation, the piece type that was finished
   * first, the other one, and the parity wherever it was done.
   *
   * <p>Parity is the stretch in which a type's leftover pair goes back where it belongs, and it can
   * fall in two places. Done last — the common case — both types are sitting on their pair when the
   * second one finishes, and one algorithm closes both, so it follows them. Done in between, the
   * first type is whole before the second is started, and it sits between the two. It is emitted
   * only when it happened: an even permutation has none, and inventing a step of no time would say
   * the solve had one.
   */
  private void layout(List<String> names, List<Long> times) {
    names.add(MEMO);
    times.add(memoMs);
    if (cyclesMs[EDGES] == null && cyclesMs[CORNERS] == null) {
      names.add(EXECUTION); // neither type was finished: nothing yet says which was being solved
      times.add(null);
      return;
    }
    int first = firstFinished();
    int second = 1 - first;
    names.add(TYPE_NAMES[first]);
    times.add(cyclesMs[first]);

    Long firstWhole = parity ? fullyMs[first] : null;
    boolean betweenTheTypes = firstWhole != null && cyclesMs[second] != null
        && firstWhole > cyclesMs[first] && firstWhole <= cyclesMs[second];
    if (betweenTheTypes) {
      names.add(PARITY);
      times.add(firstWhole);
    }
    names.add(TYPE_NAMES[second]);
    times.add(cyclesMs[second]);

    Long whole = parity ? latest(fullyMs[first], fullyMs[second]) : null;
    if (!betweenTheTypes && whole != null && cyclesMs[second] != null && whole > cyclesMs[second]) {
      names.add(PARITY);
      times.add(whole);
    }
  }

  private int firstFinished() {
    if (cyclesMs[EDGES] == null) {
      return CORNERS;
    }
    if (cyclesMs[CORNERS] == null) {
      return EDGES;
    }
    return cyclesMs[CORNERS] < cyclesMs[EDGES] ? CORNERS : EDGES;
  }

  private static Long latest(Long first, Long second) {
    if (first == null || second == null) {
      return first == null ? second : first;
    }
    return Math.max(first, second);
  }

  private List<Long> stepTimes() {
    List<Long> times = new ArrayList<>();
    layout(new ArrayList<String>(), times);
    return times;
  }

  private List<StepBoundaryEvent> boundaries() {
    List<Long> times = stepTimes();
    List<StepBoundaryEvent> events = new ArrayList<>();
    for (int step = 0; step < times.size(); step++) {
      if (times.get(step) != null) {
        events.add(new StepBoundaryEvent(step, times.get(step)));
      }
    }
    return events;
  }

  @Override
  public int stepCount() {
    return stepTimes().size();
  }

  @Override
  public String stepName(int index) {
    List<String> names = new ArrayList<>();
    layout(names, new ArrayList<Long>());
    return names.get(index);
  }

  @Override
  public Long getStepTimestampMs(int index) {
    return stepTimes().get(index);
  }

  /** A blind solver has no case to square up: every turn they make is one they memorised. */
  @Override
  public boolean isAlignmentMove(int step, CubeMove move) {
    return false;
  }

  @Override
  public int subStepCount(int step) {
    return 0;
  }

  @Override
  public String subStepName(int step, int subStep) {
    throw new IndexOutOfBoundsException("Blind steps have no parts yet");
  }

  @Override
  public Long getSubStepTimestampMs(int step, int subStep) {
    return null;
  }

  @Override
  public boolean isComplete() {
    return solvedMs != null;
  }

  /**
   * The cube was memorised before it was turned, and the piece types were solved <b>one after the
   * other</b> — when the first was finished, the other still had more than its leftover pair out.
   *
   * <p>That second clause is what a sighted solve done on a blind solve type fails, and it needs no
   * threshold to say so: a last layer usually finishes its corners and its edges on the same turn,
   * so whichever type is checked, the other one is already home. It is only asked of a solve that
   * got far enough to answer it. One that stopped inside its first piece type has shown nothing to
   * contradict, and a prefix in order is a legitimate partial match.
   *
   * <p><b>What it does not catch:</b> a sighted solve whose last layer permutes the edges alone (a
   * U, Z or H perm) really does finish its corners first, with three edges still out, and passes.
   * Separating that from a blind solve means measuring <em>how much</em> of the second type was
   * still out, and every threshold that would do it is a guess at where a phase becomes real. Left
   * open rather than guessed at: this detector runs only where the solve type says blind, so the
   * case is a solve entered against the wrong type, not a solve being misread.
   */
  @Override
  public boolean matchesMethod() {
    if (memoMs == null) {
      return false;
    }
    int first = firstFinished();
    if (cyclesMs[first] == null) {
      return true;
    }
    return othersLeftAtCycles[first] > PARITY_REMAINDER;
  }
}
