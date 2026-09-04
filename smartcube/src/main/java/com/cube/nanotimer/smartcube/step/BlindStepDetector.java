package com.cube.nanotimer.smartcube.step;

import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import com.cube.nanotimer.smartcube.model.CubeState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
 * <p><b>A parity is read by the shape it leaves, not by what it put home.</b> It exchanges two
 * corners and two edges, and nothing else a blind solver runs does that. Asking it to bring one of
 * each type home was a real solve's undoing: a memo slip left two edges out, the parity swapped that
 * pair and so gained no edge, and it went unread — taking the rest of the solve with it, as turning
 * nothing could be read from.
 *
 * <p><b>A landing need not have gained anything.</b> A solver who spots a mistake undoes the
 * algorithm and does another, and the undo is every bit as much a three-cycle. Demanding progress
 * makes it invisible, and then the state it is compared against is one the solve has abandoned — on
 * the recorded solve that left the whole rest of it unread. What the algorithm was worth is a
 * question for its net effect afterwards, not for whether it happened.
 *
 * <p><b>A landing is provisional until the solve lands <em>from</em> it, and where an algorithm
 * could have ended in more than one place all of them are held.</b> An algorithm can pass through a
 * landing on its way to its own: a twelve-turn M-slice three-cycle stands a clean three-cycle from
 * where it began after eight of those turns. Committed there, the four edges the rest of it moves
 * are no algorithm at all, every state after is compared against one the solve never really stood
 * in, and nothing lands again. A whole 3-style solve arrived reading as one algorithm that way.
 *
 * <p>So each state that lands from the last committed one is kept as another way that same
 * algorithm may have ended. Choosing between them at the time — taking the later reading as soon as
 * it appears — trades one failure for its mirror, since a state part way through the next algorithm
 * can stand a cycle from the landing before last, and preferring it there throws away a landing that
 * was right.
 *
 * <p><b>So nothing is chosen at the time at all: the solve is read every way at once, and the
 * reading that makes most sense of it wins.</b> Settling an algorithm is a guess like any other, and
 * a reading that settles one and a reading that carries straight past the same state are both kept
 * until the solve says which was right. What settles it is how much of the solve each has read —
 * more algorithms is a better reading — and among readings that have read as much, the one whose
 * algorithms landed <em>earliest</em>, an algorithm ending where it first could have rather than at
 * a later place the cube passed back through.
 *
 * <p>⚠️ <b>Settling at the time was measured on face turns alone, and that is exactly where it
 * holds.</b> A slice rocks the core, so the state after one is read against all 24 rotations, and
 * the state one slice into the next algorithm stands a clean three-cycle from the last landing far
 * more often than any face turn does. Settle there and the reading follows a shadow of the real one,
 * a slice out of phase, for the rest of the solve — the algorithm already read is renamed off a
 * different buffer, and the piece type after it never lands at all. Over two thousand generated
 * slice solves, settling at the time read less of six of them than the reading it replaced; reading
 * every way at once reads all two thousand as well or better, and still reads more of 55 of the
 * three thousand face-turn ones.
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

  /** Ways one algorithm may have ended that are worth holding at once. Measured never above two. */
  private static final int MOST_TAILS = 6;

  /**
   * Readings of the whole solve held at once. Two is enough for every generated solve, face turns
   * and slices alike; four is room for the real ones, which are made of misfires and undos as well.
   */
  private static final int MOST_READINGS = 4;

  /**
   * Most of the solve made sense of first, and among equals the one whose algorithms landed
   * earliest — an algorithm ended where it first could have, not at a later place the cube passed
   * back through. Without that second rule, turning that comes to nothing and leaves the cube where
   * it stood reads as the algorithm ending again, later, with nothing left unread.
   */
  private static final Comparator<Reading> BEST_FIRST = new Comparator<Reading>() {
    @Override
    public int compare(Reading a, Reading b) {
      if (a.algorithms() != b.algorithms()) {
        return b.algorithms() - a.algorithms();
      }
      List<Long> landedA = a.landedMs();
      List<Long> landedB = b.landedMs();
      for (int i = 0; i < Math.min(landedA.size(), landedB.size()); i++) {
        if (!landedA.get(i).equals(landedB.get(i))) {
          return landedA.get(i) < landedB.get(i) ? -1 : 1;
        }
      }
      return a.unread - b.unread;
    }
  };

  /** The pieces of one type a parity swaps. */
  private static final int SWAPPED_PAIR = 2;

  private static final int[][] PIECES = Cubies.PIECES;

  /**
   * One algorithm, dated where it landed and named for the cycle it shot — which it can only be once
   * the piece it was shot from is known, and that is sometimes a later algorithm's doing.
   */
  private static final class Landing {
    final long timestampMs;
    final int type;
    final String before;
    final String after; // where it landed, which is where the solve stood one algorithm on
    final boolean shot; // whether a cycle was shot, and so may still be renamed
    final List<Integer> pieces;
    final List<Integer> gained; // what it put home, which says which of its name's pieces are solved
    BlindTargets.Named named;
    int buffer;

    /** An algorithm with nothing left to settle: an undo, or a flip or a twist. */
    Landing(long timestampMs, int type, BlindTargets.Named named, String before, String after,
        List<Integer> gained) {
      this(timestampMs, type, named, before, after, false, null, BlindTargets.NO_BUFFER, gained);
    }

    Landing(long timestampMs, int type, BlindTargets.Named named, String before, String after,
        boolean shot, List<Integer> pieces, int buffer, List<Integer> gained) {
      this.timestampMs = timestampMs;
      this.type = type;
      this.named = named;
      this.before = before;
      this.after = after;
      this.shot = shot;
      this.pieces = pieces;
      this.buffer = buffer;
      this.gained = gained;
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

  /**
   * One way to read the solve so far: the landings it has settled on, the ways the algorithm now
   * running may already have ended, and the moves it has made nothing of.
   *
   * <p>The states carry the drift taken out against the landing before them, which is why they are
   * kept per reading rather than shared: a reading that commits a different landing normalises
   * everything after it differently.
   */
  private static final class Reading {
    final List<String> chain = new ArrayList<>();
    final List<Long> chainMs = new ArrayList<>();
    final List<String> tails = new ArrayList<>();
    final List<Long> tailMs = new ArrayList<>();
    int unread; // moves made since the last landing, which are moves nothing was read from
    boolean parityFound;

    Reading() {
    }

    Reading(Reading from) {
      chain.addAll(from.chain);
      chainMs.addAll(from.chainMs);
      tails.addAll(from.tails);
      tailMs.addAll(from.tailMs);
      unread = from.unread;
      parityFound = from.parityFound;
    }

    /** How much of the solve this reading has made sense of, which is what readings compete on. */
    int algorithms() {
      return chain.size() + (tails.isEmpty() ? 0 : 1);
    }

    /** When each algorithm this reading has read landed, the one still open included. */
    List<Long> landedMs() {
      List<Long> times = new ArrayList<>(chainMs);
      if (!tailMs.isEmpty()) {
        times.add(tailMs.get(0));
      }
      return times;
    }

    /** What tells two readings apart, and all a reading of one is built out of. */
    String key() {
      return chain + "|" + chainMs + "|" + tails + "|" + tailMs;
    }
  }

  private final List<Landing> landings = new ArrayList<>();

  private BlindTargets targets = new BlindTargets(BlindTargets.UNKNOWN_FRAME);
  private int buffer; // the piece the solve is shooting from, for as long as it stays out
  private int lastBuffer; // and the last one it shot from, kept after that one came home
  // The last piece each type was shot from. A parity swaps a pair of each and is said from both, so
  // one running buffer is not enough: by then it holds whichever type was solved last.
  private final int[] typeBuffer = new int[] {BlindTargets.NO_BUFFER, BlindTargets.NO_BUFFER};
  private String start; // the state the cube was memorised from, which the chain is read against
  // The readings still in the running, best first. See the class note on why there is more than one.
  private final List<Reading> readings = new ArrayList<>();
  private String landed; // the state at the last landing, with the drift taken out
  private String stopped; // the state the solve was left in, which says what went wrong with it
  private String builtFrom; // the reading the landings stand for, so they are built once
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
    readings.clear();
    readings.add(new Reading());
    builtFrom = null;
    memoMs = null;
    solvedMs = null;
    lastTimestampMs = startTimestampMs;
    start = startState.getFacelets();
    stopped = start;
    parity = Cubies.isOddPermutation(start);
    rebuild();
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
      readLanding(state.getFacelets(), lastTimestampMs, lastMove != null);
      rebuild();
      // Read whether or not the state was a landing: a solve can come out on turning that reads as
      // no algorithm at all, and it has still come out.
      if (isSolved(state.getFacelets())) {
        solvedMs = lastTimestampMs;
      }
    }
    return boundaries();
  }

  /** One algorithm on from a state: a cycle, a pair turned where they stand, or a parity. */
  private boolean lands(String base, String facelets, boolean parityRead) {
    int frame = closestFrame(base, facelets);
    int touched = touched(base, facelets, frame);
    return touched == CYCLE || touched == FLIP
        || (parity && !parityRead && touched == PARITY_CYCLE
            && exchangesTwoOfEach(base, withoutDrift(facelets, frame)));
  }

  /**
   * Every reading carried one state on. A reading that can settle its algorithm here and one that
   * carries straight past this state are both kept: which of them was right is not knowable here.
   */
  private void readLanding(String facelets, long timestampMs, boolean turned) {
    List<Reading> grown = new ArrayList<>();
    for (Reading reading : readings) {
      settled(reading, facelets, timestampMs, grown);
      anotherEnding(reading, facelets, timestampMs, grown);
      if (turned) {
        Reading passing = new Reading(reading);
        passing.unread++;
        grown.add(passing); // this state was the middle of something: nothing is read from it
      }
    }
    keepTheBest(grown);
  }

  /** The reading that takes this state as the solve having moved on from one of its open endings. */
  private void settled(Reading reading, String facelets, long timestampMs, List<Reading> grown) {
    for (int tail = 0; tail < reading.tails.size(); tail++) {
      if (!lands(reading.tails.get(tail), facelets, reading.parityFound)) {
        continue;
      }
      Reading settled = new Reading(reading);
      settled.parityFound |= isParity(committed(settled), settled.tails.get(tail));
      settled.chain.add(settled.tails.get(tail));
      settled.chainMs.add(settled.tailMs.get(tail));
      settled.tails.clear();
      settled.tailMs.clear();
      settled.unread = 0;
      addTail(settled, facelets, timestampMs);
      grown.add(settled);
      return; // the earliest ending it moved on from; a later one is that one passed through
    }
  }

  /** The reading that takes this state as another way the algorithm now running may have ended. */
  private void anotherEnding(Reading reading, String facelets, long timestampMs,
      List<Reading> grown) {
    if (repeatsATail(reading, facelets)
        || !lands(committed(reading), facelets, reading.parityFound)) {
      return;
    }
    Reading another = new Reading(reading);
    another.unread = 0;
    addTail(another, facelets, timestampMs);
    grown.add(another);
  }

  /**
   * The readings worth carrying on, best first by {@link #BEST_FIRST}. Duplicates go — two readings
   * built out of the same landings are the same reading, however they arrived at them.
   *
   * <p>The best reading here is not necessarily the right one, and that is what keeping the rest is
   * for: one that settles an algorithm early is ahead of one that carries on, right up until the
   * one that carried on settles two more.
   *
   * <p>Nothing at all grows where a state arrives with no move behind it and no reading can place
   * it. The readings then stand: they are the solve so far, and a state that says nothing about
   * them is no reason to have none.
   */
  private void keepTheBest(List<Reading> grown) {
    if (grown.isEmpty()) {
      return;
    }
    Collections.sort(grown, BEST_FIRST);
    List<String> seen = new ArrayList<>();
    readings.clear();
    for (Reading reading : grown) {
      if (readings.size() >= MOST_READINGS) {
        return;
      }
      if (!seen.contains(reading.key())) {
        seen.add(reading.key());
        readings.add(reading);
      }
    }
  }

  /** Whether a landing is the parity: it exchanges two of each, which nothing else does. */
  private boolean isParity(String before, String after) {
    return parity && touched(before, after, FaceletRotations.IDENTITY) == PARITY_CYCLE
        && exchangesTwoOfEach(before, after);
  }

  /** The last landing a reading has settled on, which every candidate tail is read against. */
  private String committed(Reading reading) {
    return reading.chain.isEmpty() ? start : reading.chain.get(reading.chain.size() - 1);
  }

  /**
   * Whether the cube is back at somewhere this algorithm was already read as ending. Turning that
   * comes to nothing is not another reading of it, and must not pass for one.
   */
  private boolean repeatsATail(Reading reading, String facelets) {
    for (String tail : reading.tails) {
      if (touched(tail, facelets, closestFrame(tail, facelets)) == 0) {
        return true;
      }
    }
    return false;
  }

  /** Keeps a state as somewhere the algorithm now running may have ended. */
  private void addTail(Reading reading, String facelets, long timestampMs) {
    String base = committed(reading);
    reading.tails.add(withoutDrift(facelets, closestFrame(base, facelets)));
    reading.tailMs.add(timestampMs);
    if (reading.tails.size() > MOST_TAILS) {
      reading.tails.remove(reading.tails.size() - 1); // the earliest ones are worth keeping
      reading.tailMs.remove(reading.tailMs.size() - 1);
    }
  }

  /**
   * The reading on show, read afresh: every landing named against the one before it. Skipped where
   * the reading on show has not moved, which most moves of a solve do not move it.
   */
  private void rebuild() {
    Reading best = readings.isEmpty() ? new Reading() : readings.get(0);
    if (best.key().equals(builtFrom)) {
      return;
    }
    builtFrom = best.key();
    landings.clear();
    parityFound = false;
    buffer = BlindTargets.NO_BUFFER;
    lastBuffer = BlindTargets.NO_BUFFER;
    typeBuffer[EDGES] = BlindTargets.NO_BUFFER;
    typeBuffer[CORNERS] = BlindTargets.NO_BUFFER;
    landed = start;
    for (int i = 0; i < best.chain.size(); i++) {
      readAlgorithm(best.chain.get(i), best.chainMs.get(i));
    }
    if (!best.tails.isEmpty()) {
      readAlgorithm(best.tails.get(0), best.tailMs.get(0));
    }
    nameWhatNothingSettled();
  }

  /**
   * The algorithms nothing ever settled a buffer for, read off what they landed: <b>a shot sends
   * the buffer's piece home</b>, so one that put exactly one piece home was shot from the slot that
   * was holding it, and the algorithms after it follow from that one.
   *
   * <p>Asked last and only of what is left, because it infers where {@link #nameWhatWaitedForIt}
   * knows: an algorithm that misfired put its one piece home from somewhere that was not the buffer,
   * and wherever a later algorithm of the type settles the question that reading is the true one.
   * What is left is a piece type whose every algorithm hid it — the 2026-09-04 solve's corners are a
   * cycle broken and then closed, one leaving two pieces out and the other none, so neither is the
   * one-piece-left-out an ordinary shot is read from, and they were said from whichever corner the
   * cube happened to store first.
   */
  private void nameWhatNothingSettled() {
    int[] settled = {BlindTargets.NO_BUFFER, BlindTargets.NO_BUFFER};
    for (Landing landing : landings) {
      if (!landing.shot) {
        continue;
      }
      int type = Cubies.isEdge(landing.pieces.get(0)) ? EDGES : CORNERS;
      if (landing.buffer != BlindTargets.NO_BUFFER) {
        settled[type] = landing.buffer;
        continue;
      }
      int shotFrom = landing.pieces.contains(settled[type]) ? settled[type]
          : senderOfTheOnePieceItLanded(landing);
      if (shotFrom == BlindTargets.NO_BUFFER) {
        continue;
      }
      landing.buffer = shotFrom;
      landing.named = targets.name(landing.before, landing.after, shotFrom, landing.pieces);
      settled[type] = shotFrom;
    }
  }

  /**
   * The slot that was holding the one piece this algorithm put home, or nothing where it put a
   * different number home. Nothing either where one of the pieces was already sitting in its own
   * slot: that is a cycle closed, and an algorithm that breaks out of one lands its <em>second</em>
   * target, so what came home was sent by the slot it broke into rather than by the buffer.
   */
  private int senderOfTheOnePieceItLanded(Landing landing) {
    if (landing.gained.size() != 1) {
      return BlindTargets.NO_BUFFER;
    }
    int sender = BlindTargets.NO_BUFFER;
    for (int slot : landing.pieces) {
      int home = Cubies.homeSlotOf(landing.before, slot);
      if (home == slot) {
        return BlindTargets.NO_BUFFER;
      }
      if (home == landing.gained.get(0)) {
        sender = slot;
      }
    }
    return sender;
  }

  /** How many moves the reading on show has made nothing of. */
  private int unread() {
    return readings.isEmpty() ? 0 : readings.get(0).unread;
  }

  /** One landing of the chain, read against the one before it, which is what {@code landed} holds. */
  private void readAlgorithm(String steady, long timestampMs) {
    int touched = touched(landed, steady, FaceletRotations.IDENTITY);
    List<Integer>[] gained = gained(landed, steady, FaceletRotations.IDENTITY);
    boolean parityLanding = parity && !parityFound && touched == PARITY_CYCLE
        && exchangesTwoOfEach(landed, steady);
    parityFound |= parityLanding;
    List<Integer> all = new ArrayList<>(gained[EDGES]);
    all.addAll(gained[CORNERS]);
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
      landings.add(new Landing(timestampMs, typeOf(gained, parityLanding), name, landed, steady,
          shot, named, shotFrom, all));
      if (shot && shotFrom != BlindTargets.NO_BUFFER) {
        nameWhatWaitedForIt(shotFrom);
        // The buffer stays the buffer until an algorithm brings it home; then another is picked up.
        buffer = all.contains(shotFrom) ? BlindTargets.NO_BUFFER : shotFrom;
        lastBuffer = shotFrom;
        typeBuffer[Cubies.isEdge(shotFrom) ? EDGES : CORNERS] = shotFrom;
      }
    }
    landed = steady;
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
      if (!landing.shot) {
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
        landings.add(new Landing(timestampMs, type, targets.turnedName(from, turned,
            typeBuffer[type]), from, steady, gained));
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
        new BlindTargets.Named(UNDO, Collections.<Integer>emptyList()), landed, steady, gained));
    return true;
  }

  /** Whether the algorithm exchanged two corners and two edges, which is what a parity does. */
  private static boolean exchangesTwoOfEach(String before, String after) {
    List<Integer> moved = moved(before, after);
    List<Integer> edges = ofType(moved, EDGES);
    List<Integer> corners = ofType(moved, CORNERS);
    return edges.size() == SWAPPED_PAIR && corners.size() == SWAPPED_PAIR
        && exchanged(before, after, edges) && exchanged(before, after, corners);
  }

  /** Whether the two slots came out holding each other's piece. */
  private static boolean exchanged(String before, String after, List<Integer> pair) {
    return Cubies.homeSlotOf(before, pair.get(0)) == Cubies.homeSlotOf(after, pair.get(1))
        && Cubies.homeSlotOf(before, pair.get(1)) == Cubies.homeSlotOf(after, pair.get(0));
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

  /**
   * The rotation under which the state differs from a landing in the fewest pieces. Counting stops
   * at the best rotation so far, which most of the 24 pass within a piece or two — the search is
   * made once per open ending per move, so it is the one place here worth counting less.
   */
  private static int closestFrame(String base, String facelets) {
    int closest = FaceletRotations.IDENTITY;
    int fewest = Integer.MAX_VALUE;
    for (int rotation = 0; rotation < FaceletRotations.COUNT; rotation++) {
      int differing = touched(base, facelets, rotation, fewest);
      if (differing < fewest) {
        fewest = differing;
        closest = rotation;
      }
    }
    return closest;
  }

  private static int touched(String base, String facelets, int frame) {
    return touched(base, facelets, frame, PIECES.length);
  }

  /** @param most where the count stops, the caller having no use for anything above it */
  private static int touched(String base, String facelets, int frame, int most) {
    int differing = 0;
    for (int[] piece : PIECES) {
      for (int facelet : piece) {
        if (base.charAt(facelet) != facelets.charAt(FaceletRotations.apply(frame, facelet))) {
          if (++differing >= most) {
            return differing;
          }
          break;
        }
      }
    }
    return differing;
  }

  /** The pieces home now that were not at the landing before, split by type. */
  @SuppressWarnings("unchecked")
  private static List<Integer>[] gained(String base, String facelets, int frame) {
    List<Integer>[] gained = new List[] {new ArrayList<Integer>(), new ArrayList<Integer>()};
    for (int i = 0; i < PIECES.length; i++) {
      if (Cubies.inPlace(facelets, PIECES[i], frame) && !Cubies.inPlace(base, PIECES[i])) {
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
   * The stretch an algorithm that gained nothing opens, or null where it belongs to the one it is
   * standing in. Nothing came home to say which piece type it was about, so the pieces it turned
   * say it instead: a mistake taken back turns the ones the stretch it sits in is made of, while an
   * algorithm that turned the other type's is that type's first, however little it put home.
   */
  private static String opensAStretch(Landing landing, Run running) {
    List<Integer> moved = moved(landing.before, landing.after);
    boolean edges = !ofType(moved, EDGES).isEmpty();
    boolean corners = !ofType(moved, CORNERS).isEmpty();
    if (edges == corners) {
      return null; // both types, or neither: the turning says nothing about which stretch it is
    }
    String name = TYPE_NAMES[edges ? EDGES : CORNERS];
    return running != null && name.equals(running.name) ? null : name;
  }

  /**
   * The solve as steps: memorisation, then a step per stretch of algorithms that worked on the same
   * piece type, with the parity — when there was one — standing apart wherever it was done.
   *
   * <p><b>An algorithm that gained nothing is placed by the pieces it turned</b>, since nothing came
   * home to place it by. A mistake taken back turns the pieces of the stretch it interrupted and
   * stays in it — undoing a mistake is part of solving that piece type, not a piece type of its own
   * — while one that turned the other type's pieces opens that type, whatever it was worth.
   *
   * <p>Which it has to be, because <b>the algorithm that opens a piece type routinely gains
   * nothing</b>: breaking into a new cycle parks the buffer's own piece and takes a fresh one in,
   * putting nothing home by definition. Placed by the stretch it followed instead, a solve whose
   * corners open on a break-in read that algorithm — a corner commutator, spelled in corner stickers
   * — as the last of the edges, and charged the edges its three seconds.
   *
   * <p>A landing whose turning says nothing — the pieces of both types, or of neither — falls back
   * to the stretch it is standing in, or to the one it <em>precedes</em> where none has begun. Left
   * to stand alone it would open the solve with a step that is not a piece type at all.
   */
  private List<Run> runs() {
    List<Run> runs = new ArrayList<>();
    List<Landing> beforeAnyRun = new ArrayList<>();
    for (Landing landing : landings) {
      Run running = runs.isEmpty() ? null : runs.get(runs.size() - 1);
      String name = landing.type == PARITY_TYPE ? PARITY
          : landing.type == NO_GAIN ? opensAStretch(landing, running) : TYPE_NAMES[landing.type];
      if (name == null) {
        if (running == null) {
          beforeAnyRun.add(landing);
        } else {
          running.landings.add(landing);
        }
        continue;
      }
      if (running == null || !running.name.equals(name)) {
        running = new Run(name);
        runs.add(running);
      }
      running.landings.addAll(beforeAnyRun); // whatever preceded the first stretch opens it
      beforeAnyRun.clear();
      running.landings.add(landing);
    }
    if (!beforeAnyRun.isEmpty()) {
      // Turning that reads as no piece type is all this solve has, and none of it named one.
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
   * What the cube wanted of an algorithm that carries red: the piece sitting in the buffer shot
   * home, and then the piece waiting at that target shot home after it. Read off the state the
   * algorithm found rather than off a memo nothing here has, which is the same thing wherever the
   * memo was being followed.
   *
   * <p><b>Only the blamed algorithm is asked</b>, and for the reason the ones after it carry no red:
   * past the first mistake the cube has moved on, so what it wants there is no longer what the
   * solver memorised. A break-in is silent too, the buffer holding its own piece leaving the next
   * cycle the solver's to open wherever they please.
   */
  @Override
  public String subStepWantedName(int step, int subStep) {
    Landing landing = runs().get(step - 1).landings.get(subStep);
    if (landing.buffer == BlindTargets.NO_BUFFER || blamedOn(landing).isEmpty()) {
      return null;
    }
    return targets.wantedName(landing.before, landing.buffer);
  }

  /**
   * The pieces this algorithm is answerable for, of which a solve that came out has none.
   *
   * <p><b>An algorithm executed the other way round is answerable for what it should have put
   * home.</b> That is proved rather than guessed: reverse this one algorithm, leave the rest of the
   * solve as it was ({@link #wouldHaveSolvedItReversed}), and if the cube comes out this is where it
   * was lost, of which {@link #whatItShouldHavePutHome} says how much. Proved outranks everything
   * below: the algorithms after a misfire take pieces out executing the memo faithfully on a cube
   * the misfire had already moved on, and reddening them points at the wrong one.
   *
   * <p><b>Otherwise an algorithm answers for a target it shot at and never brought home</b>
   * ({@link #shotsThatNeverLanded}), all of them where there are several: nothing proved says which
   * was the mistake and which the memo carried out on a cube that had moved on. Or, where it shot
   * at nothing because the cycle had closed, for having broken into a piece that was already home
   * ({@link #brokeIntoASolvedPiece}).
   *
   * <p>Neither blames an algorithm that did nothing wrong. A cycle left open and a parity never done
   * put pieces out that no shot ever claimed and that no reversal would fix: both are the verdict
   * line's to explain, which says the shape without pointing at anyone.
   */
  private List<Integer> blamedOn(Landing landing) {
    if (solvedMs != null) {
      return Collections.emptyList();
    }
    Landing reversed = lostByReversal();
    if (reversed != null) {
      return reversed == landing
          ? whatItShouldHavePutHome(landing) : Collections.<Integer>emptyList();
    }
    List<Integer> brokeIn = brokeIntoASolvedPiece(landing);
    return brokeIn.isEmpty() ? shotsThatNeverLanded(landing) : brokeIn;
  }

  /**
   * The one break-in worth blaming: the piece it took back out was already home, and no parity was
   * owed that would have put it back. A cycle to break into can be opened at any piece still out,
   * and choosing one that was not is a target read off the wrong letter.
   *
   * <p><b>The parity is why this is not simply "the target was home".</b> A parity ends by swapping
   * the buffer with one other piece, so a solver who breaks into the piece the parity is going to
   * swap breaks into a solved slot on purpose and comes out. The permutation says whether one is
   * still owed at that moment, which the state before the algorithm answers on its own: every
   * three-cycle leaves it as it found it, so it is odd exactly while the parity is outstanding.
   *
   * <p>Only the target is blamed. The piece it displaced had to be parked somewhere, and where is
   * no more the solver's choice here than it is on any other break-in.
   */
  private List<Integer> brokeIntoASolvedPiece(Landing landing) {
    List<Integer> blamed = new ArrayList<>();
    if (!landing.shot || landing.buffer == BlindTargets.NO_BUFFER || wasTakenBack(landing)
        || !brokeIntoANewCycle(landing) || Cubies.isOddPermutation(landing.before)) {
      return blamed;
    }
    int target = brokeInto(landing);
    if (target >= 0 && Cubies.inPlace(landing.before, PIECES[target])) {
      blamed.add(target);
    }
    return blamed;
  }

  /**
   * The slot this algorithm broke into, read off the cube: the one it <em>parked the buffer's own
   * piece in</em>, which is the whole of what breaking in is. Not the second piece of the name,
   * which is the target only while the name could be walked as a cycle at all — where it could not,
   * the pieces are said in the order the cube stores them and the second of them is nobody in
   * particular.
   *
   * <p>Parked <b>by this algorithm</b>: the buffer's piece has to have arrived there, not merely to
   * be sitting there. Every shot leaves it lying somewhere, so asked without that, every algorithm
   * of the solve reads as a break-in.
   */
  private int brokeInto(Landing landing) {
    for (int slot = 0; slot < PIECES.length; slot++) {
      if (slot != landing.buffer && Cubies.homeSlotOf(landing.after, slot) == landing.buffer
          && Cubies.homeSlotOf(landing.before, slot) != landing.buffer) {
        return slot;
      }
    }
    return -1;
  }

  /**
   * The targets this algorithm shot at that it did not put home and that nothing put right after it:
   * a target is where a piece was meant to arrive, so one that never arrived is a shot that missed.
   *
   * <p><b>Except a break-in</b>, which is the algorithm made once a cycle has closed: with its own
   * piece in the buffer there is nothing left to shoot, so all it can do is put that piece into a
   * new cycle and take a fresh one in. Neither slot it moved a piece into was a target it missed:
   * one holds the buffer's piece, and the piece that one displaced had to be parked in the other.
   * Which piece it broke into is a question of its own, and {@link #brokeIntoASolvedPiece} asks it.
   * And except an algorithm the solver took back, whose effect on the cube is gone.
   *
   * <p><b>And except the break-in an algorithm makes while it is still shooting.</b> A cycle can
   * close on the first of the two targets — the piece waiting there is the buffer's own — and then
   * the algorithm lands that one target and spends its second piece opening a fresh cycle, at
   * whichever piece of the type the solver pleases. That second slot comes out holding the buffer's
   * piece and so never comes home, which is what a break-in is and not a shot that missed; the cube
   * says as much on the wanted line, which marks what it owed there {@code breakin:}. Reddening it
   * anyway is the 2026-08-26 solve's complaint, where the algorithm was right and a flip the solver
   * never did was what lost the cube.
   *
   * <p>Only a cycle is asked, and only one whose buffer settled: a parity swaps and a flip turns, so
   * neither has a target to have missed, and a cycle its solver never memorised would otherwise be
   * laid at the door of the parity before it. Nothing is asked past the reading either, which is
   * what {@link #leftOut} answers with nothing.
   */
  private List<Integer> shotsThatNeverLanded(Landing landing) {
    List<Integer> blamed = new ArrayList<>();
    if (!landing.shot || landing.buffer == BlindTargets.NO_BUFFER || wasTakenBack(landing)
        || brokeIntoANewCycle(landing)) {
      return blamed;
    }
    List<Integer> left = leftOut();
    int brokeInto = brokeInto(landing);
    for (int slot : landing.named.slots) {
      if (slot != landing.buffer && slot != brokeInto && left.contains(slot)
          && !landing.gained.contains(slot)) {
        blamed.add(slot);
      }
    }
    return blamed;
  }

  /** Whether this algorithm found the buffer holding its own piece, which is a cycle closed. */
  private boolean brokeIntoANewCycle(Landing landing) {
    return Cubies.homeSlotOf(landing.before, landing.buffer) == landing.buffer;
  }

  /** Whether the algorithm right after this one put the cube back where this one found it. */
  private boolean wasTakenBack(Landing landing) {
    int next = landings.indexOf(landing) + 1;
    return next > 0 && next < landings.size() && UNDO.equals(landings.get(next).named.name);
  }

  /** The first algorithm the cube would have come out without, or null where there is none. */
  private Landing lostByReversal() {
    for (Landing landing : landings) {
      if (wouldHaveSolvedItReversed(landing)) {
        return landing;
      }
    }
    return null;
  }

  /**
   * The pieces of an algorithm's name that executing it the right way round would have brought home,
   * read off that corrected cube: the mirror of the ones it did put home. Which settles the piece it
   * was shot from without a rule of its own, a buffer being landed only by a cycle that closes.
   */
  private List<Integer> whatItShouldHavePutHome(Landing landing) {
    String corrected = executedTheOtherWayRound(landing);
    List<Integer> blamed = new ArrayList<>();
    for (int slot : landing.named.slots) {
      if (Cubies.inPlace(corrected, PIECES[slot])) {
        blamed.add(slot);
      }
    }
    return blamed;
  }

  /**
   * Whether the cube would have come out had this one algorithm been executed the other way round,
   * everything after it held identical — the turning nothing was read from included, being turning
   * all the same.
   */
  private boolean wouldHaveSolvedItReversed(Landing landing) {
    return isSolved(Cubies.applyMotion(Cubies.motionBetween(landing.after, stopped),
        executedTheOtherWayRound(landing)));
  }

  /** The state this algorithm would have left had it been executed the other way round. */
  private String executedTheOtherWayRound(Landing landing) {
    return Cubies.applyMotion(Cubies.motionBetween(landing.after, landing.before), landing.before);
  }

  /** The pieces not home when the solve stopped, or none where there is nothing honest to read. */
  private List<Integer> leftOut() {
    List<Integer> left = new ArrayList<>();
    if (solvedMs != null || unread() > 0) {
      return left;
    }
    for (int slot = 0; slot < PIECES.length; slot++) {
      if (!Cubies.inPlace(landed, PIECES[slot])) {
        left.add(slot);
      }
    }
    return left;
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
    return BlindResidual.of(stopped, targets, typeBuffer[EDGES], typeBuffer[CORNERS]);
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
    return !parityFound && unread() == 0 ? ParityCheck.SKIPPED : null;
  }

  /**
   * Where the reading stopped, when turning carried on past the last algorithm it could read. A
   * solve that came out has none of this: the turning after it is the blindfold coming off.
   */
  @Override
  public LostReading getLostReading() {
    if (solvedMs != null || unread() == 0 || landings.isEmpty()) {
      return null;
    }
    return new LostReading(landings.get(landings.size() - 1).named.name, unread());
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
    // Asked of the landings rather than of the steps they are laid out as, which name a piece type
    // off the pieces an algorithm turned whether or not it put any of them home.
    for (Landing landing : landings) {
      if (landing.type != NO_GAIN) {
        return true;
      }
    }
    return false;
  }
}
