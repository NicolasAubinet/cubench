package com.cube.nanotimer.smartcube.step;

import com.cube.nanotimer.smartcube.model.CubeMove;
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

  private static final int[][] PIECES = new int[Cubies.EDGES.length + Cubies.CORNERS.length][];

  static {
    System.arraycopy(Cubies.EDGES, 0, PIECES, 0, Cubies.EDGES.length);
    System.arraycopy(Cubies.CORNERS, 0, PIECES, Cubies.EDGES.length, Cubies.CORNERS.length);
  }

  /** One algorithm, dated where it landed and named for what it put home. */
  private static final class Landing {
    final long timestampMs;
    final int type;
    final String name;

    Landing(long timestampMs, int type, String name) {
      this.timestampMs = timestampMs;
      this.type = type;
      this.name = name;
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

  private String landed; // the state at the last landing, with the drift taken out
  private Long memoMs;
  private Long solvedMs;
  private long lastTimestampMs;
  private boolean parity; // the scramble was an odd permutation, so one algorithm swaps two of each
  private boolean parityFound;

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
    List<String>[] gained = gained(facelets, frame);
    boolean parityLanding = parity && !parityFound && touched == PARITY_CYCLE
        && !gained[EDGES].isEmpty() && !gained[CORNERS].isEmpty();
    if (touched != CYCLE && touched != FLIP && !parityLanding) {
      return;
    }
    parityFound |= parityLanding;
    landings.add(new Landing(timestampMs, typeOf(gained, parityLanding), nameOf(gained)));
    landed = withoutDrift(facelets, frame);
  }

  private static int typeOf(List<String>[] gained, boolean parityLanding) {
    if (parityLanding) {
      return PARITY_TYPE;
    }
    if (!gained[EDGES].isEmpty()) {
      return EDGES;
    }
    return gained[CORNERS].isEmpty() ? NO_GAIN : CORNERS;
  }

  /** The pieces put home, by the faces they belong on, so the display can colour them. */
  private static String nameOf(List<String>[] gained) {
    List<String> all = new ArrayList<>(gained[EDGES]);
    all.addAll(gained[CORNERS]);
    return all.isEmpty() ? UNDO : String.join("+", all);
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
  private List<String>[] gained(String facelets, int frame) {
    List<String>[] gained = new List[] {new ArrayList<String>(), new ArrayList<String>()};
    for (int i = 0; i < PIECES.length; i++) {
      if (Cubies.inPlace(facelets, PIECES[i], frame) && !Cubies.inPlace(landed, PIECES[i])) {
        gained[i < Cubies.EDGES.length ? EDGES : CORNERS].add(Cubies.nameOf(PIECES[i]));
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
   * its own: undoing a mistake is part of solving that piece type, not a piece type of its own.
   */
  private List<Run> runs() {
    List<Run> runs = new ArrayList<>();
    for (Landing landing : landings) {
      String name = landing.type == PARITY_TYPE ? PARITY
          : landing.type == NO_GAIN ? null : TYPE_NAMES[landing.type];
      Run last = runs.isEmpty() ? null : runs.get(runs.size() - 1);
      if (name == null && last != null) {
        last.landings.add(landing); // an undo carries on with whatever was being solved
        continue;
      }
      if (last == null || !last.name.equals(name)) {
        last = new Run(name == null ? EXECUTION : name);
        runs.add(last);
      }
      last.landings.add(landing);
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
