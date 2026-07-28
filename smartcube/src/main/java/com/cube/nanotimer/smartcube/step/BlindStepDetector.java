package com.cube.nanotimer.smartcube.step;

import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import com.cube.nanotimer.smartcube.model.CubeState;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a blindfolded solve as memorisation and then the algorithms it was executed in: which
 * pieces each one put home, grouped into the piece types they belong to.
 *
 * <p><b>Memorisation</b> is the one step read off a move rather than off the state — the solver taps
 * to start, memorises without touching the cube, and the first turn is the moment the two meet. The
 * solve is therefore anchored at the tap; anchored at the first move, as every other detector is, the
 * memo would be zero by construction.
 *
 * <p><b>Everything after it is read off where the algorithms land.</b> An algorithm takes half the
 * cube apart and puts it back, so mid-way through one the state means nothing — the counts swing on
 * nearly every move. What is unmistakable is the landing: the cube arrives at almost exactly the
 * state it was at the last landing, differing only in the small cycle the algorithm targeted. A
 * commutator is a three-cycle; a flip or a twist moves two pieces; a parity swaps two of each. So a
 * landing is a state {@value #CYCLE} or {@value #FLIP} pieces from the last one — nothing a blind
 * solver runs touches more, and a state that reads more than that is not a landing but the middle
 * of something.
 *
 * <p>Two details earn their place, both because a real solve showed what happens without them.
 *
 * <p><b>"Almost" is measured against every way the cube can sit.</b> Blind is full of slices, and a
 * slice turns the core: relative to the centres the state is written against, it is everything else
 * that moved. The closest of the 24 rotations is taken, and the landing is carried forward with that
 * drift taken out — otherwise the next algorithm is compared against a frame the solve has left.
 *
 * <p><b>A landing need not have gained anything.</b> A solver who spots a mistake undoes the
 * algorithm and does another, and the undo is every bit as much a three-cycle. Demanding progress
 * makes it invisible, and then the state it is compared against is one the solve has abandoned — on
 * the recorded solve that left the whole rest of it unread. What the algorithm was worth is a
 * question for its net effect afterwards, not for whether it happened.
 *
 * <p><b>An algorithm is a flip or a twist when its net effect leaves every piece it touched in the
 * slot that piece belongs to</b> — turned where it stands rather than cycled anywhere. Read off the
 * effect rather than off how many pieces moved, which is what a real solve demanded: two flips and a
 * twist all came out as commutators, and a three-corner twist moves three pieces exactly as a
 * commutator does. The buffer is named among them, since nothing was shot and a buffer turned in
 * place is a memo item like any other.
 *
 * <p><b>It is read across algorithms too.</b> A flip is often executed as two commutators, and the
 * first of them can only take its pieces apart — so it gains nothing, and only the pair turns
 * anything. A landing that gained nothing therefore joins the one after it when the two together
 * turn pieces where they stand, and the flip reads as the one memo item it was.
 */
public final class BlindStepDetector implements StepDetector {

  private static final int EDGES = 0, CORNERS = 1;
  /** A landing that gained pieces of both types: only a parity does that. */
  private static final int PARITY_TYPE = 2;
  /** A landing that gained nothing — an algorithm undone, or one that only moved the buffer on. */
  private static final int NO_GAIN = -1;

  private static final String MEMO = "memo";
  private static final String PARITY = "parity";
  private static final String UNDO = "undo";
  private static final String[] TYPE_NAMES = {"edges", "corners"};

  /** What a solve whose algorithms never read as any piece type's is left calling its turning. */
  private static final String EXECUTION = "execution";

  /** Pieces an algorithm moves: a three-cycle, a pair flipped or twisted, a parity's two of each. */
  private static final int CYCLE = 3, FLIP = 2, PARITY_CYCLE = 4;

  private static final int[][] PIECES = Cubies.PIECES;

  /** One algorithm, dated where it landed and named for what it put home. */
  private static final class Landing {
    final long timestampMs;
    final int type;
    final String name;
    final String before;

    Landing(long timestampMs, int type, String name, String before) {
      this.timestampMs = timestampMs;
      this.type = type;
      this.name = name;
      this.before = before;
    }
  }

  /** Consecutive algorithms that worked on the same piece type: one step of the solve. */
  private static final class Run {
    final String name;
    final List<Landing> landings = new ArrayList<>();

    Run(String name) {
      this.name = name;
    }

    long completedMs() {
      return landings.get(landings.size() - 1).timestampMs;
    }
  }

  private final List<Landing> landings = new ArrayList<>();

  private BlindTargets targets = new BlindTargets(BlindTargets.UNKNOWN_FRAME);
  private String landed; // the state at the last landing, with the drift taken out
  private Long memoMs;
  private Long solvedMs;
  private long lastTimestampMs;
  private boolean parity; // the scramble was an odd permutation, so one algorithm swaps two of each
  private boolean parityFound;

  /**
   * The whole-cube rotation the solver made picking the cube up, which the gyro already tracks for
   * every solve — the scramble is turned green in front and a blind solve is turned in whatever
   * grip the solver memorised in, and nothing rotates it after that.
   *
   * <p>Its <em>inverse</em> is the frame: the rotation carries the solver's front onto the face the
   * cube reports it as, and names have to be spelled the other way round.
   */
  public void setPickupRotation(CubeRotation pickup) {
    setHoldingFrame(pickup == null ? BlindTargets.UNKNOWN_FRAME
        : FaceletRotations.inverse(FaceletRotations.of(pickup)));
  }

  /**
   * The frame the solver is holding the cube in, which is not the one it reports in. Names are
   * spelled in it and the buffer is found through it; left unknown they fall back to the reported
   * frame.
   */
  void setHoldingFrame(int rotation) {
    targets = new BlindTargets(rotation);
  }

  @Override
  public void reset(CubeState startState, long startTimestampMs) {
    landings.clear();
    memoMs = null;
    solvedMs = null;
    parityFound = false;
    lastTimestampMs = startTimestampMs;
    landed = startState.getFacelets();
    parity = Cubies.isOddPermutation(landed);
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
    if (memoMs != null && solvedMs == null) {
      readLanding(state.getFacelets(), lastTimestampMs);
      // Read whether or not the state was a landing: a solve can come out on turning that reads as
      // no algorithm at all, and it has still come out.
      if (isSolved(state.getFacelets())) {
        solvedMs = lastTimestampMs;
      }
    }
    return boundaries();
  }

  private void readLanding(String facelets, long timestampMs) {
    int frame = closestFrame(facelets);
    int touched = touched(facelets, frame);
    List<Integer>[] gained = gained(facelets, frame);
    boolean parityLanding = parity && !parityFound && touched == PARITY_CYCLE
        && !gained[EDGES].isEmpty() && !gained[CORNERS].isEmpty();
    if (touched != CYCLE && touched != FLIP && !parityLanding) {
      return;
    }
    parityFound |= parityLanding;
    List<Integer> all = new ArrayList<>(gained[EDGES]);
    all.addAll(gained[CORNERS]);
    String steady = withoutDrift(facelets, frame);
    if (!readOrientation(steady, timestampMs) && !readUndo(steady, timestampMs)) {
      // Only a cycle was shot: a flip or a twist turns its pieces where they stand, a parity neither.
      String name = targets.name(landed, steady, moved(landed, steady), all, touched == CYCLE);
      landings.add(new Landing(timestampMs, typeOf(gained, parityLanding), name, landed));
    }
    landed = steady;
  }

  /**
   * A flip or a twist, if that is what landed here — on its own, or together with the algorithms
   * before it that gained nothing, which is how a flip done as two commutators reads: each half
   * takes its pieces apart, and only the pair puts them back turned.
   */
  private boolean readOrientation(String steady, long timestampMs) {
    String from = landed;
    for (int joined = 0; ; joined++) {
      List<Integer> turned = turnedInPlace(from, steady);
      if (turned != null && !turned.isEmpty()) {
        for (int i = 0; i < joined; i++) {
          landings.remove(landings.size() - 1); // the halves are the one algorithm they compose
        }
        int type = Cubies.isEdge(turned.get(0)) ? EDGES : CORNERS;
        landings.add(new Landing(timestampMs, type, targets.turnedName(from, turned), from));
        return true;
      }
      int previous = landings.size() - 1 - joined;
      if (previous < 0 || landings.get(previous).type != NO_GAIN) {
        return false; // one that put something home is an algorithm of its own, never half a turn
      }
      from = landings.get(previous).before;
    }
  }

  /** An algorithm that puts the cube back where the one before it found it: a mistake taken back. */
  private boolean readUndo(String steady, long timestampMs) {
    if (landings.isEmpty() || !steady.equals(landings.get(landings.size() - 1).before)) {
      return false;
    }
    landings.add(new Landing(timestampMs, NO_GAIN, UNDO, landed));
    return true;
  }

  /**
   * The pieces the two states differ in, when every one of them sits in the slot it belongs to on
   * both sides — turned where it stands rather than cycled anywhere. Null if any of them moved.
   */
  private static List<Integer> turnedInPlace(String before, String after) {
    List<Integer> turned = new ArrayList<>();
    for (int slot : moved(before, after)) {
      if (Cubies.homeSlotOf(before, slot) != slot || Cubies.homeSlotOf(after, slot) != slot) {
        return null;
      }
      turned.add(slot);
    }
    return turned;
  }

  /** The pieces that read differently either side of an algorithm, however they differ. */
  private static List<Integer> moved(String before, String after) {
    List<Integer> moved = new ArrayList<>();
    for (int slot = 0; slot < PIECES.length; slot++) {
      for (int facelet : PIECES[slot]) {
        if (before.charAt(facelet) != after.charAt(facelet)) {
          moved.add(slot);
          break;
        }
      }
    }
    return moved;
  }

  private static int typeOf(List<Integer>[] gained, boolean parityLanding) {
    if (parityLanding) {
      return PARITY_TYPE;
    }
    if (!gained[EDGES].isEmpty()) {
      return EDGES;
    }
    return gained[CORNERS].isEmpty() ? NO_GAIN : CORNERS;
  }

  /** The rotation under which the state differs from the last landing in the fewest pieces. */
  private int closestFrame(String facelets) {
    int closest = FaceletRotations.IDENTITY;
    int fewest = Integer.MAX_VALUE;
    for (int rotation = 0; rotation < FaceletRotations.COUNT; rotation++) {
      int differing = touched(facelets, rotation);
      if (differing < fewest) {
        fewest = differing;
        closest = rotation;
      }
    }
    return closest;
  }

  private int touched(String facelets, int frame) {
    int differing = 0;
    for (int[] piece : PIECES) {
      for (int facelet : piece) {
        if (landed.charAt(facelet) != facelets.charAt(FaceletRotations.apply(frame, facelet))) {
          differing++;
          break;
        }
      }
    }
    return differing;
  }

  /** The pieces home now that were not at the last landing, split by type. */
  @SuppressWarnings("unchecked")
  private List<Integer>[] gained(String facelets, int frame) {
    List<Integer>[] gained = new List[] {new ArrayList<Integer>(), new ArrayList<Integer>()};
    for (int i = 0; i < PIECES.length; i++) {
      if (Cubies.inPlace(facelets, PIECES[i], frame) && !Cubies.inPlace(landed, PIECES[i])) {
        gained[Cubies.isEdge(i) ? EDGES : CORNERS].add(i);
      }
    }
    return gained;
  }

  /** Every piece home in some one way of holding the cube, which is what solved means. */
  private static boolean isSolved(String facelets) {
    for (int rotation = 0; rotation < FaceletRotations.COUNT; rotation++) {
      boolean home = true;
      for (int[] piece : PIECES) {
        if (!Cubies.inPlace(facelets, piece, rotation)) {
          home = false;
          break;
        }
      }
      if (home) {
        return true;
      }
    }
    return false;
  }

  /** The state as it would read with the drift taken out, so the next landing compares like for like. */
  private static String withoutDrift(String facelets, int frame) {
    char[] steady = new char[facelets.length()];
    for (int facelet = 0; facelet < steady.length; facelet++) {
      steady[facelet] = facelets.charAt(FaceletRotations.apply(frame, facelet));
    }
    return new String(steady);
  }

  /**
   * The solve as steps: memorisation, then a step per stretch of algorithms that worked on the same
   * piece type, with the parity — when there was one — standing apart wherever it was done.
   *
   * <p>An algorithm that gained nothing belongs to the stretch it interrupted rather than to one of
   * its own: undoing a mistake is part of solving that piece type, not a piece type of its own. One
   * that comes before any stretch has begun — a cycle broken into on the very first algorithm, or a
   * mistake made straight away — belongs to the stretch it <em>precedes</em>, for the same reason.
   * Left to stand alone it would open the solve with a step that is not a piece type at all.
   */
  private List<Run> runs() {
    List<Run> runs = new ArrayList<>();
    List<Landing> beforeAnyRun = new ArrayList<>();
    for (Landing landing : landings) {
      String name = landing.type == PARITY_TYPE ? PARITY
          : landing.type == NO_GAIN ? null : TYPE_NAMES[landing.type];
      if (name == null) {
        if (runs.isEmpty()) {
          beforeAnyRun.add(landing);
        } else {
          runs.get(runs.size() - 1).landings.add(landing);
        }
        continue;
      }
      Run last = runs.isEmpty() ? null : runs.get(runs.size() - 1);
      if (last == null || !last.name.equals(name)) {
        last = new Run(name);
        runs.add(last);
      }
      last.landings.addAll(beforeAnyRun); // whatever preceded the first stretch opens it
      beforeAnyRun.clear();
      last.landings.add(landing);
    }
    if (!beforeAnyRun.isEmpty()) {
      // Nothing was ever put home: turning that reads as no piece type is all this solve has.
      Run execution = new Run(EXECUTION);
      execution.landings.addAll(beforeAnyRun);
      runs.add(execution);
    }
    return runs;
  }

  private void layout(List<String> names, List<Long> times) {
    names.add(MEMO);
    times.add(memoMs);
    for (Run run : runs()) {
      names.add(run.name);
      times.add(run.completedMs());
    }
    if (memoMs != null && solvedMs == null) {
      // It stopped before the cube came out: the turning past the last landing reached nothing, and
      // is left for the display to draw as the tail it is.
      names.add(EXECUTION);
      times.add(null);
    }
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

  /** Each algorithm of a step is a part of it, the way each pair of an F2L is. */
  @Override
  public int subStepCount(int step) {
    List<Run> runs = runs();
    int run = step - 1;
    return run >= 0 && run < runs.size() ? runs.get(run).landings.size() : 0;
  }

  @Override
  public String subStepName(int step, int subStep) {
    return runs().get(step - 1).landings.get(subStep).name;
  }

  @Override
  public Long getSubStepTimestampMs(int step, int subStep) {
    List<Run> runs = runs();
    int run = step - 1;
    if (run < 0 || run >= runs.size() || subStep >= runs.get(run).landings.size()) {
      return null;
    }
    return runs.get(run).landings.get(subStep).timestampMs;
  }

  @Override
  public boolean isComplete() {
    return solvedMs != null;
  }

  /**
   * The cube was memorised before it was turned, and it was executed as algorithms, one piece type
   * at a time: the stretches read as one type and then the other, with at most a parity between or
   * after them.
   *
   * <p>A solve that never came out is not asked to prove it — a prefix in order is a legitimate
   * partial match. One that did, and whose turning read as no algorithm at all, is refused: the cube
   * falling solved all at once is what a sighted solve done on a blind solve type looks like, and it
   * has no algorithms to show for itself.
   */
  @Override
  public boolean matchesMethod() {
    if (memoMs == null) {
      return false;
    }
    if (solvedMs == null) {
      return true;
    }
    List<String> types = new ArrayList<>();
    for (Run run : runs()) {
      if (!PARITY.equals(run.name) && !types.contains(run.name)) {
        types.add(run.name);
      }
    }
    // One stretch per type, and no type coming back after the other has started: interleaving them
    // is not how a blind solve is executed, and is how a sighted one looks.
    int stretches = runs().size() - (parityFound ? 1 : 0);
    return !types.isEmpty() && types.size() == stretches;
  }
}
