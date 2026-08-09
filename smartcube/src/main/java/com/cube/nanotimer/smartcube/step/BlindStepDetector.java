package com.cube.nanotimer.smartcube.step;

import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import com.cube.nanotimer.smartcube.model.CubeState;
import java.util.ArrayList;
import java.util.Collections;
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
 * commutator does.
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

  /**
   * One algorithm, dated where it landed and named for the cycle it shot — which it can only be once
   * the piece it was shot from is known, and that is sometimes a later algorithm's doing.
   */
  private static final class Landing {
    final long timestampMs;
    final int type;
    final String before;
    final String after; // where it landed, kept only for a cycle that may still be renamed
    final List<Integer> pieces;
    final List<Integer> gained; // what it put home, which says which of its name's pieces are solved
    final List<Integer> moved; // every piece it shifted, named or not: who to blame for one left out
    BlindTargets.Named named;
    int buffer;

    /** An algorithm with nothing left to settle: an undo, or a flip or a twist. */
    Landing(long timestampMs, int type, BlindTargets.Named named, String before,
        List<Integer> gained, List<Integer> moved) {
      this(timestampMs, type, named, before, null, null, BlindTargets.NO_BUFFER, gained, moved);
    }

    Landing(long timestampMs, int type, BlindTargets.Named named, String before, String after,
        List<Integer> pieces, int buffer, List<Integer> gained, List<Integer> moved) {
      this.timestampMs = timestampMs;
      this.type = type;
      this.named = named;
      this.before = before;
      this.after = after;
      this.pieces = pieces;
      this.buffer = buffer;
      this.gained = gained;
      this.moved = moved;
    }

    /**
     * What became of each piece this algorithm's name says it shot at, in the order they are said.
     * Home is this algorithm's own doing; wrong is the whole solve's verdict, and which algorithm
     * carries it is {@link #blamedOn} to decide.
     */
    List<PieceMark> marks(List<Integer> blamed) {
      List<PieceMark> marks = new ArrayList<>(named.slots.size());
      for (int slot : named.slots) {
        marks.add(blamed.contains(slot) ? PieceMark.WRONG
            : gained.contains(slot) ? PieceMark.HOME : PieceMark.TOUCHED);
      }
      return marks;
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
  private int buffer; // the piece the solve is shooting from, for as long as it stays out
  private int lastBuffer; // and the last one it shot from, kept after that one came home
  // The last piece each type was shot from. A parity swaps a pair of each and is said from both, so
  // one running buffer is not enough: by then it holds whichever type was solved last.
  private final int[] typeBuffer = new int[] {BlindTargets.NO_BUFFER, BlindTargets.NO_BUFFER};
  private String landed; // the state at the last landing, with the drift taken out
  private String stopped; // the state the solve was left in, which says what went wrong with it
  private int unread; // moves made since the last landing, which are moves nothing was read from
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
   * spelled in it; left unknown they fall back to the reported frame.
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
    buffer = BlindTargets.NO_BUFFER;
    lastBuffer = BlindTargets.NO_BUFFER;
    typeBuffer[EDGES] = BlindTargets.NO_BUFFER;
    typeBuffer[CORNERS] = BlindTargets.NO_BUFFER;
    lastTimestampMs = startTimestampMs;
    landed = startState.getFacelets();
    stopped = landed;
    unread = 0;
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
      stopped = state.getFacelets();
      if (readLanding(state.getFacelets(), lastTimestampMs)) {
        unread = 0;
      } else if (lastMove != null) {
        unread++;
      }
      // Read whether or not the state was a landing: a solve can come out on turning that reads as
      // no algorithm at all, and it has still come out.
      if (isSolved(state.getFacelets())) {
        solvedMs = lastTimestampMs;
      }
    }
    return boundaries();
  }

  private boolean readLanding(String facelets, long timestampMs) {
    int frame = closestFrame(facelets);
    int touched = touched(facelets, frame);
    List<Integer>[] gained = gained(facelets, frame);
    boolean parityLanding = parity && !parityFound && touched == PARITY_CYCLE
        && !gained[EDGES].isEmpty() && !gained[CORNERS].isEmpty();
    if (touched != CYCLE && touched != FLIP && !parityLanding) {
      return false;
    }
    parityFound |= parityLanding;
    List<Integer> all = new ArrayList<>(gained[EDGES]);
    all.addAll(gained[CORNERS]);
    String steady = withoutDrift(facelets, frame);
    // Undo first: it is the stricter claim of the two, the cube standing exactly where the previous
    // algorithm found it. A misfire taken straight back leaves pieces turned where they stand and
    // would otherwise read as the flip it undid.
    if (!readUndo(steady, timestampMs, all) && !readOrientation(steady, timestampMs, all)) {
      // Only a cycle was shot: a flip or a twist turns its pieces where they stand, a parity neither.
      boolean shot = touched == CYCLE;
      List<Integer> moved = moved(landed, steady);
      // A cycle is said as all three of its pieces; anything else, as what it put home.
      List<Integer> named = shot || all.isEmpty() ? moved : all;
      int shotFrom = shot ? bufferOf(moved, all) : BlindTargets.NO_BUFFER;
      // A parity is said as the pieces it swapped, not the ones it solved: a corner it swaps into a
      // slot it is still twisted in was swapped all the same, and is half of what was memorised.
      BlindTargets.Named name = parityLanding
          ? targets.swapName(ofType(moved, CORNERS), ofType(moved, EDGES),
              typeBuffer[CORNERS], typeBuffer[EDGES])
          : targets.name(landed, steady, shotFrom, named);
      landings.add(new Landing(timestampMs, typeOf(gained, parityLanding), name, landed,
          shot ? steady : null, named, shotFrom, all, moved));
      if (shot && shotFrom != BlindTargets.NO_BUFFER) {
        nameWhatWaitedForIt(shotFrom);
        // The buffer stays the buffer until an algorithm brings it home; then another is picked up.
        buffer = all.contains(shotFrom) ? BlindTargets.NO_BUFFER : shotFrom;
        lastBuffer = shotFrom;
        typeBuffer[Cubies.isEdge(shotFrom) ? EDGES : CORNERS] = shotFrom;
      }
    }
    landed = steady;
    return true;
  }

  /**
   * Which piece an algorithm was shot from, told by the cube rather than configured, since a solver
   * who floats their buffer has no fixed piece to be told about.
   *
   * <p><b>A buffer stays the buffer until an algorithm brings it home</b>, so one that moves the
   * piece the solve is already shooting from was shot from it. Failing that, <b>a shot leaves the
   * buffer holding a piece that is not its own</b>: a cycle that put all but one of its pieces home
   * was shot from the one it left out. And failing that too — a closing cycle leaves none out, a
   * break-in two — it is the piece the solve was shooting from before.
   *
   * <p>Both fallbacks were a real solve's doing. Left-out ahead of continuity named an algorithm
   * that breaks into a new cycle and closes the old one after its <em>target</em>, printing the
   * cycle rotated by one; with neither, the cycle cannot be walked at all and the pieces are said
   * in the order the cube stores them, which reads as the buffer landing anywhere in the name.
   */
  private int bufferOf(List<Integer> moved, List<Integer> gained) {
    if (moved.contains(buffer)) {
      return buffer;
    }
    int left = BlindTargets.NO_BUFFER;
    int count = 0;
    for (int slot : moved) {
      if (!gained.contains(slot)) {
        left = slot;
        count++;
      }
    }
    // A buffer of another type says nothing here: an algorithm moves pieces of one type only.
    return count == 1 ? left : moved.contains(lastBuffer) ? lastBuffer : BlindTargets.NO_BUFFER;
  }

  /**
   * The algorithms before this one that could not tell which piece they were shot from, now that it
   * has been named. The first algorithm of a piece type is what needs this: breaking a cycle leaves
   * two pieces it could have been shot from, and the one after it settles which — a buffer only
   * stops being the buffer once something has brought it home.
   */
  private void nameWhatWaitedForIt(int shotFrom) {
    for (int i = landings.size() - 2; i >= 0; i--) {
      Landing landing = landings.get(i);
      if (landing.after == null) {
        continue; // nothing was shot here, so nothing here chose a buffer either
      }
      if (landing.buffer != BlindTargets.NO_BUFFER || !landing.pieces.contains(shotFrom)) {
        return;
      }
      landing.buffer = shotFrom;
      landing.named = targets.name(landing.before, landing.after, shotFrom, landing.pieces);
    }
  }

  /**
   * A flip or a twist, if that is what landed here — on its own, or together with the algorithms
   * before it that gained nothing, which is how a flip done as two commutators reads: each half
   * takes its pieces apart, and only the pair puts them back turned.
   *
   * <p>What it put home is still the last landing's gain even where halves are joined: the halves
   * gained nothing, so a piece the pair turned is home now exactly when this landing gained it.
   */
  private boolean readOrientation(String steady, long timestampMs, List<Integer> gained) {
    String from = landed;
    for (int joined = 0; ; joined++) {
      List<Integer> turned = turnedInPlace(from, steady);
      if (turned != null && !turned.isEmpty()) {
        for (int i = 0; i < joined; i++) {
          landings.remove(landings.size() - 1); // the halves are the one algorithm they compose
        }
        int type = Cubies.isEdge(turned.get(0)) ? EDGES : CORNERS;
        landings.add(new Landing(timestampMs, type, targets.turnedName(from, turned), from, gained,
            turned));
        return true;
      }
      int previous = landings.size() - 1 - joined;
      if (previous < 0 || landings.get(previous).type != NO_GAIN) {
        return false; // one that put something home is an algorithm of its own, never half a turn
      }
      if (UNDO.equals(landings.get(previous).named.name)) {
        // A mistake taken back is a finished statement, not half a turn: joining across one read a
        // misfire and the shot that replaced it as a single flip of the pair they both aimed at.
        return false;
      }
      from = landings.get(previous).before;
    }
  }

  /** An algorithm that puts the cube back where the one before it found it: a mistake taken back. */
  private boolean readUndo(String steady, long timestampMs, List<Integer> gained) {
    if (landings.isEmpty() || !steady.equals(landings.get(landings.size() - 1).before)) {
      return false;
    }
    landings.add(new Landing(timestampMs, NO_GAIN,
        new BlindTargets.Named(UNDO, Collections.<Integer>emptyList()), landed, gained,
        moved(landed, steady)));
    return true;
  }

  /**
   * The pieces the two states differ in, when every one of them is still the same piece afterwards
   * — turned where it stands rather than cycled anywhere. Null if any of them moved.
   *
   * <p>Same piece, <b>not</b> piece at home. Requiring home was the rule until a solve with a parity
   * flipped its buffer: on an odd solve the buffer holds a foreign piece right up until the parity,
   * so a flip of the buffer and one other edge had one slot away from home and read as no flip at
   * all. It was then named for the single piece it happened to solve, and a flip of one piece is not
   * a thing that exists.
   */
  private static List<Integer> turnedInPlace(String before, String after) {
    List<Integer> turned = new ArrayList<>();
    for (int slot : moved(before, after)) {
      if (Cubies.homeSlotOf(before, slot) != Cubies.homeSlotOf(after, slot)) {
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

  /** The edges among some pieces, or the corners: {@code type} is {@link #EDGES} or {@link #CORNERS}. */
  private static List<Integer> ofType(List<Integer> slots, int type) {
    List<Integer> ofType = new ArrayList<>();
    for (int slot : slots) {
      if ((Cubies.isEdge(slot) ? EDGES : CORNERS) == type) {
        ofType.add(slot);
      }
    }
    return ofType;
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
  public boolean isAlignmentMove(int step, CubeMove move, boolean pausedAfter) {
    return false;
  }

  /** Each algorithm of a step is a part of it, the way each pair of an F2L is. */
  @Override
  public int subStepCount(int step) {
    List<Run> runs = runs();
    int run = step - 1;
    return run >= 0 && run < runs.size() ? runs.get(run).landings.size() : 0;
  }

  /** An algorithm's name: the cycle it shot, spelled in the grip the solver held the cube in. */
  @Override
  public String subStepName(int step, int subStep) {
    return runs().get(step - 1).landings.get(subStep).named.name;
  }

  /** What became of each piece an algorithm names: what it put home, and what it lost the solve on. */
  @Override
  public List<PieceMark> subStepPieceMarks(int step, int subStep) {
    Landing landing = runs().get(step - 1).landings.get(subStep);
    return landing.marks(blamedOn(landing));
  }

  /**
   * The pieces this algorithm is answerable for, which it is only when <b>what the cube was left
   * with is exactly what this algorithm said it would fix</b>, and nothing has touched those pieces
   * since. That is what an algorithm shot to the wrong sticker leaves: the same three pieces it
   * named, cycled among themselves, still out.
   *
   * <p>Anything looser blames algorithms that did nothing wrong. A solve stopped part way through a
   * cycle has pieces out because the cycle is still open, and the last algorithm of it was going
   * right; a parity never done leaves two of each swapped, and no algorithm did that either. Both
   * leave pieces out that no single algorithm ever claimed, so both are left to the verdict line,
   * which says the shape without pointing at anyone.
   *
   * <p>The piece an algorithm was shot from is not marked even so: a cycle leaves its buffer holding
   * whatever came back from its last target, so the buffer is where the leftover sits rather than
   * something the algorithm missed.
   *
   * <p>Nothing is blamed on anything where the reading did not run to the last move: past that point
   * the unread turning could have broken any of it. That is what {@link #getLostReading()} says.
   */
  private List<Integer> blamedOn(Landing landing) {
    List<Integer> left = leftOut();
    if (left.size() != landing.named.slots.size() || !landing.named.slots.containsAll(left)) {
      return Collections.emptyList();
    }
    List<Integer> blamed = new ArrayList<>();
    for (int slot : left) {
      if (lastToMove(slot) != landing) {
        return Collections.emptyList(); // something later had it, so this is not where it was lost
      }
      if (slot != landing.buffer) {
        blamed.add(slot);
      }
    }
    return blamed;
  }

  /** The pieces not home when the solve stopped, or none where there is nothing honest to read. */
  private List<Integer> leftOut() {
    List<Integer> left = new ArrayList<>();
    if (solvedMs != null || unread > 0) {
      return left;
    }
    for (int slot = 0; slot < PIECES.length; slot++) {
      if (!Cubies.inPlace(landed, PIECES[slot])) {
        left.add(slot);
      }
    }
    return left;
  }

  private Landing lastToMove(int slot) {
    for (int i = landings.size() - 1; i >= 0; i--) {
      if (landings.get(i).moved.contains(slot)) {
        return landings.get(i);
      }
    }
    return null;
  }

  /** A parity is one algorithm and a step of its own: collapsed, it loses both its name and its
   * place in the count. */
  @Override
  public boolean keepsLonePart() {
    return true;
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
   * What the cube was left in, read off the state at the last move the solve is credited with: what
   * a solver who came out wrong has to pick the cube up and work out for themselves.
   *
   * <p>Turning past solved is not part of it, the same turning {@link #onState} declines to read: a
   * solve that came out and was then turned on came out.
   */
  @Override
  public BlindResidual getResidual() {
    return BlindResidual.of(stopped, targets);
  }

  /**
   * Whether the parity was the one the scramble asked for, told from the scramble's permutation and
   * the state the solve was left in rather than from how the solve read.
   *
   * <p>Only ever said of a cube left on a two-and-two swap. That swap is an odd permutation, so the
   * cube is exactly one parity algorithm from solvable and nothing built out of three-cycles could
   * have reached it or could leave it. An even scramble never called for one, so a cube left on one
   * was put there by an algorithm the memo did not have. An odd scramble did call for one, and where
   * none was read that swap is the parity itself, still owed.
   *
   * <p>The second of those is the only half that leans on the reading, and it is held back once the
   * turning outran it: past that point the parity could have been done in the moves nothing was read
   * from. The first leans on the scramble and the final state alone, which a lost reading does not
   * touch.
   */
  @Override
  public ParityCheck getParityCheck() {
    BlindResidual residual = getResidual();
    if (residual == null || residual.getShape() != BlindResidual.Shape.PARITY) {
      return null;
    }
    if (!parity) {
      return ParityCheck.NEEDLESS;
    }
    return !parityFound && unread == 0 ? ParityCheck.SKIPPED : null;
  }

  /**
   * Where the reading stopped, when turning carried on past the last algorithm it could read. A
   * solve that came out has none of this: the turning after it is the blindfold coming off.
   */
  @Override
  public LostReading getLostReading() {
    if (solvedMs != null || unread == 0 || landings.isEmpty()) {
      return null;
    }
    return new LostReading(landings.get(landings.size() - 1).named.name, unread);
  }

  /**
   * The cube was memorised before it was turned, and the turning read as algorithms: a stretch of
   * them put pieces of one type home, and that is the whole of what is asked.
   *
   * <p><b>The order the types were solved in is not.</b> It used to be — one stretch per type, no
   * type coming back after another had started — on the grounds that interleaving is what a sighted
   * solve done on a blind solve type looks like. It is not what that looks like: a sighted solve
   * lands nothing this detector can read, so it arrives here with no stretches at all and is refused
   * on that. What the order rule actually caught was blind solves that were read correctly and then
   * thrown away for having done a flip late, and a solver owes their memo no particular order.
   *
   * <p>A solve that never came out is not asked to prove any of it — whatever was read of it is a
   * legitimate partial match.
   */
  @Override
  public boolean matchesMethod() {
    if (memoMs == null) {
      return false;
    }
    if (solvedMs == null) {
      return true;
    }
    // Turning that put nothing home is not an algorithm, and a solve made only of it has nothing to
    // show for itself: the cube falling solved all at once is what a sighted solve looks like here.
    for (Run run : runs()) {
      if (!EXECUTION.equals(run.name)) {
        return true;
      }
    }
    return false;
  }
}
