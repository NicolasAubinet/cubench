package com.cube.nanotimer.cube;

import com.cube.nanotimer.cube.SolveMovesFormat.Move;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import com.cube.nanotimer.smartcube.step.AlgorithmSlots;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveStep;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What the solver actually did, rebuilt from the stored moves and the step durations: the moves of
 * each step, split again at its parts. Nothing about the split is persisted — a move belongs to the
 * step whose window its offset falls in, which is how the analyzer assigned it in the first place.
 *
 * <p>Counts are in half turns, the metric speedcubers quote: the cube reports {@code R2} as two
 * quarter turns, so consecutive turns of the same face in the same direction fold back into one.
 *
 * <p>Moves that undid each other are marked rather than dropped, and go on counting towards the
 * move count and the turn rate: the hands turned them and the time they cost is real, so a display
 * shows them spent instead of pretending they never happened. The marking runs on the displayed
 * tokens, after the slices, the wides and the half turns have been folded, so {@code M M'} and
 * {@code r r'} fall out of it for free. Only whole cancellations count: {@code R2 R'} is left
 * standing, since half of a move is not something that can be crossed out. Which of them a given
 * row may draw is {@link #cancelledIn}'s business.
 *
 * <p><b>A blind solve is spelled from the grip it was picked up in, and shows no rotations.</b> The
 * solver never turns the cube, so the frame can only be the grip plus what the slices and the wides
 * rock the core by, and a rotation token in one of these solves is the frame accounting leaking
 * rather than turning that happened. Both halves of that are the one leak: the grip is read at the
 * first move <em>outside a slice pair</em>, so a solve opening on a slice has no frame read at its
 * first moves at all, the grip goes unwritten, and it surfaces several moves later as a regrip
 * nobody made. The 2026-08-23 solve spelled its opening {@code M'} as {@code S'} and its twin four
 * moves on as {@code M'} for exactly that reason.
 *
 * <p>{@link RotationTracker#getRotations} writes the grip down before it compares any frame against
 * it, so a solve recorded since carries no such phantom. This rule stays for the ones recorded
 * before, and because a blind solve's rotation tokens are noise however they got there.
 *
 * <p><b>A dropped token can take a wide with it.</b> The solver peeks at the cube and tips it back,
 * and where one half of that swing lands beside a face turn the gyro reads it as that face being
 * wide — it cannot tell the two apart, since a peek and a wide move the core identically. The half
 * that finds no face becomes a rotation token, so the peek reaches here written down twice: once as
 * a token this drops and once as a wide it would believe. Believing it leaves the frame a quarter
 * turn out for the rest of the solve. See {@link #peekedWides}.
 *
 * <p><b>Where the gyro leaves a reading open, the algorithm's own name settles it.</b> A slice it
 * reported no spin for is left as two faces, a pair it did report one for is folded, a wide it
 * invented is believed and a real one it wrote off as a peek is dropped — and every one of those
 * leaves the frame a quarter turn out for the rest of the solve. What the frame has to answer to is
 * that a spelled algorithm shifts exactly the slots the detector named it for. See
 * {@link BlindSpelling}.
 *
 * <p><b>A cube with no gyro has its blind slices read from timing, one algorithm at a time.</b> Its
 * stream carries no rotation token at all, so a pair without a spin is not a two-hander the gyro
 * vouched for: the opposite pairs inside {@link Slices#WINDOW_MS} are taken as slices. Timing alone
 * mispairs, though (a slow {@code M2} arriving as {@code R'}, {@code L R'}, {@code L} folds to one
 * {@code M'}), and one slice too many or too few leaves every later letter a quarter turn out. So
 * an algorithm's pairs fold only in a reading whose spins leave the core where it was, or a half
 * turn round as an {@code M2} target does; with none, that algorithm keeps its faces, which leave
 * the frame alone. The frame is then never a quarter turn out at an algorithm's end, whatever was
 * misread inside it. With no steps there are no algorithms to hold the folds to, and nothing folds.
 *
 * <p>The readings are tried in a fixed order: every pair as timed, then every pair with a slow
 * slice's gap allowed, then either with one pair left as the two-hander it may have been. Among
 * those last both kinds can fit, and the algorithm's name says which to take first: one that swaps
 * the buffer with a single edge is an {@code M2} target, whose slices leave the core a half turn
 * round, and any other puts it back.
 * See {@link #returningPairs}.
 *
 * <p><b>No other solve may be read this way.</b> Nothing guards the stored grip against a first
 * move that is <em>wide</em>, whose swing the gyro has already reported, and the scripted wide
 * drill stores a grip its own ground truth disowns. Spelling a sighted solve from the grip turns
 * every letter of it a quarter turn.
 *
 * <p><b>A slice turned slowly is still one slice.</b> Its halves can come too far apart for the gyro
 * to vouch for it, and the core's spin is then written between them as a regrip. A blind solver does
 * not regrip mid-slice by exactly that slice's spin, so the three read as the slice (an M2 seen
 * split over 343 ms).
 */
public final class SolveSolution {

  /** How far apart a straddled slice's halves may be: the one seen split was 343 ms. */
  private static final long STRADDLED_SLICE_MS = 1000;

  /** What an algorithm's slices may leave the core turned by: an {@code M2} target's worth. */
  private static final List<String> AXIS_HALF_TURNS = Arrays.asList("x2", "y2", "z2");

  /** Between the parts of a step, wherever they are shown as one run of moves. */
  public static final String GROUP_SEPARATOR = " · ";

  // How long a turn takes, read per solve (see turnMsOf) within these bounds; the cube reports a
  // turn when it ends. The default is for a solve too short to read it from.
  private static final long MIN_TURN_MS = 80;
  private static final long MAX_TURN_MS = 200;
  private static final long DEFAULT_TURN_MS = 150;
  private static final int MIN_TURN_GAPS = 20;

  /** A pause longer than this many turns is a stop: the solver froze rather than turned slowly. */
  private static final int STOP_TURNS = 2;

  /** Shorter gaps are the uneven rhythm of turning, not a pause. */
  private static final long MIN_PAUSE_MS = 250;

  private final List<Step> steps;
  private final int moveCount;
  private final int partCount;
  private final long turningMs;

  private SolveSolution(List<Step> steps, int moveCount, int partCount, long turningMs) {
    this.steps = Collections.unmodifiableList(steps);
    this.moveCount = moveCount;
    this.partCount = partCount;
    this.turningMs = turningMs;
  }

  /**
   * Empty when the solve carries no moves, so callers can hide the section on one check.
   *
   * <p>The step durations are the whole measure here: they run back to back from the solve start, so
   * a move belongs to the step whose window its offset falls in, and their total is the solve. Pass
   * the steps a solve is <em>displayed</em> through — tails included — or the last of the moves will
   * fall outside every window.
   */
  public static SolveSolution from(String storedMoves, List<SolveStep> solveSteps) {
    return from(storedMoves, solveSteps, null);
  }

  /**
   * The same, for a solve whose method was read. Only blind makes any difference, and only because
   * of what it promises: see {@link #inSolversFrame}. Null where no method fitted.
   */
  public static SolveSolution from(String storedMoves, List<SolveStep> solveSteps,
      CubeMethod method) {
    List<Move> stored = SolveMovesFormat.parse(storedMoves);
    CubeRotation grip = method == CubeMethod.BLIND ? gripOf(storedMoves) : null;
    BlindChoices choices = grip == null ? null : blindChoices(storedMoves, stored, grip, solveSteps);
    List<Move> moves = inSolversFrame(stored, null, grip, choices, splitsOf(solveSteps));
    if (grip == null) { // a blind solve shows no rotations to fold
      moves = RotationRoundTrips.fold(moves);
    }
    if (moves.isEmpty() || solveSteps == null || solveSteps.isEmpty()) {
      return new SolveSolution(new ArrayList<Step>(), 0, 0, 0);
    }
    List<Step> steps = stepsOf(moves, solveSteps);
    int total = 0;
    long turningMs = 0;
    int parts = 0;
    for (int i = 0; i < solveSteps.size(); i++) {
      total += steps.get(i).getMoveCount();
      if (solveSteps.get(i).getExecutionMs() > 0) { // a step that turned nothing has none: see getTps
        turningMs += solveSteps.get(i).getTotalMs();
      }
      parts += solveSteps.get(i).getSubSteps().size();
    }
    markCancelled(steps);
    return new SolveSolution(steps, total, parts, turningMs);
  }

  /** The moves of each step, split again at its parts — the shape a reconstruction is shown in. */
  static List<Step> stepsOf(List<Move> moves, List<SolveStep> solveSteps) {
    List<Step> steps = new ArrayList<Step>();
    long turnMs = turnMsOf(moves);
    int taken = 0;
    long boundaryMs = 0;
    for (int i = 0; i < solveSteps.size(); i++) {
      SolveStep solveStep = solveSteps.get(i);
      long stepStartMs = boundaryMs;
      boundaryMs += solveStep.getTotalMs();
      // A step ends on the move that completed it and so owns that move — except a step that turned
      // nothing, which owns none. Memorisation ends the moment the cube is first turned, and that
      // turn is the first of the solving: shown under the memo it reads as a move made blind.
      int end = solveStep.getExecutionMs() > 0 ? endOf(moves, taken, boundaryMs) : taken;
      steps.add(new Step(i, solveStep.getName(),
          groupsFor(moves, taken, end, solveStep.getSubSteps(), stepStartMs),
          pausesOf(moves, taken, end, solveStep, stepStartMs, turnMs), STOP_TURNS * turnMs));
      taken = end;
    }
    return steps;
  }

  /**
   * How long one turn takes this solver: the fastest quarter of the gaps between their turns.
   * A stumble only adds slow gaps, so it barely moves this. Gaps under the floor are turns made
   * at once.
   */
  public static long turnMsOf(List<Move> moves) {
    List<Long> gaps = new ArrayList<Long>();
    Move previous = null;
    for (Move move : moves) {
      if (SolveMovesFormat.isRotation(move.getNotation())) {
        continue;
      }
      if (previous != null && move.getOffsetMs() - previous.getOffsetMs() >= MIN_TURN_MS) {
        gaps.add(move.getOffsetMs() - previous.getOffsetMs());
      }
      previous = move;
    }
    if (gaps.size() < MIN_TURN_GAPS) {
      return DEFAULT_TURN_MS;
    }
    Collections.sort(gaps);
    return Math.min(MAX_TURN_MS, gaps.get(gaps.size() / 4));
  }

  /**
   * The time inside a step's execution when the solver was not turning. Each turn takes
   * {@code turnMs} up to its timestamp, and a rotation twice that before the turn it is stamped
   * with; what no turn covers for at least {@link #MIN_PAUSE_MS} is a pause. The wait before a
   * part's first turn is its recognition, so only the gaps after it are read.
   *
   * @return each pause as {from, to}, in milliseconds since the solve started
   */
  private static List<long[]> pausesOf(List<Move> moves, int from, int to, SolveStep step,
      long stepStartMs, long turnMs) {
    List<long[]> pauses = new ArrayList<long[]>();
    List<SolveStep> parts = step.getSubSteps();
    if (parts.isEmpty()) {
      addPauses(moves, from, to, stepStartMs + step.getRecognitionMs(), turnMs, pauses);
      return pauses;
    }
    long boundaryMs = stepStartMs;
    int taken = from;
    for (SolveStep part : parts) {
      long executionStartMs = boundaryMs + part.getRecognitionMs();
      boundaryMs += part.getTotalMs();
      int end = Math.min(endOf(moves, taken, boundaryMs), to);
      addPauses(moves, taken, end, executionStartMs, turnMs, pauses);
      taken = end;
    }
    return pauses;
  }

  private static void addPauses(List<Move> moves, int from, int to, long executionStartMs,
      long turnMs, List<long[]> pauses) {
    Long turnedUntil = null; // null until the part's first turn
    boolean rotated = false;
    for (int i = from; i < to; i++) {
      Move move = moves.get(i);
      if (move.getOffsetMs() < executionStartMs) {
        continue;
      }
      if (SolveMovesFormat.isRotation(move.getNotation())) {
        rotated = true;
        continue;
      }
      long busyFrom = move.getOffsetMs() - turnMs - (rotated ? 2 * turnMs : 0);
      if (turnedUntil != null && busyFrom - turnedUntil >= MIN_PAUSE_MS) {
        pauses.add(new long[] {turnedUntil, busyFrom});
      }
      turnedUntil = move.getOffsetMs();
      rotated = false;
    }
  }

  /** The stored stream spelled under one set of choices, for a reading that is weighing them. */
  static List<Move> spell(List<Move> stored, CubeRotation grip, BlindChoices choices,
      List<SolveStep> solveSteps) {
    return inSolversFrame(stored, null, grip, choices, splitsOf(solveSteps));
  }

  /**
   * The stored stream in the solver's frame with its timing intact — what an animated replay plays.
   *
   * <p>Quarter turns are left unfolded here, unlike the displayed reconstruction: the cube reports a
   * half turn as two, and replaying them at the offsets they arrived at renders one flick as a flick
   * and two deliberate turns as two. Folding first would throw away the timing that tells them
   * apart. Rotation tokens are kept, since the whole point of a replay is to turn with the solver.
   */
  public static List<Move> timedSolution(String storedMoves) {
    return inSolversFrame(SolveMovesFormat.parse(storedMoves), null, null, null, null);
  }

  /**
   * A blind solve's timed stream, spelled as its reconstruction is: the grip it was picked up in at
   * offset 0, then no rotation token at all. The solver never turned the cube, so neither does a
   * replay of it; the tokens a regrip leaves in the stream are the frame accounting, not turning.
   *
   * @param solveSteps the steps the reconstruction was split by, which settle the readings the gyro
   *     left open; null to go by the stream alone
   * @param framesOut  receives this spelling's frame over time, as {@link #framesOf} gives a sighted
   *     one's, for lining a gyro track up against it
   */
  public static List<Move> timedBlindSolution(String storedMoves, List<SolveStep> solveSteps,
      List<FrameAt> framesOut) {
    List<Move> stored = SolveMovesFormat.parse(storedMoves);
    CubeRotation grip = gripOf(storedMoves);
    return inSolversFrame(stored, framesOut, grip,
        blindChoices(storedMoves, stored, grip, solveSteps), splitsOf(solveSteps));
  }

  private static BlindChoices blindChoices(String storedMoves, List<Move> stored, CubeRotation grip,
      List<SolveStep> solveSteps) {
    BlindChoices choices = BlindChoices.of(peekedWides(stored));
    if (solveSteps != null) {
      choices = withUnsensedSlicesFolded(stored, choices, solveSteps);
    }
    // The name check holds a spelling to a name, only worth anything where the two share a frame:
    // a solve recorded before its grip was kept is left with the reading its stream gives.
    if (solveSteps != null && SolveMovesFormat.pickupOf(storedMoves) != null) {
      choices = BlindSpelling.arbitrate(stored, grip, choices, solveSteps);
    }
    return choices;
  }

  // A stream with no rotation token had no gyro behind it, so a pair's missing spin says nothing.
  private static BlindChoices withUnsensedSlicesFolded(List<Move> stored, BlindChoices choices,
      List<SolveStep> solveSteps) {
    for (Move move : stored) {
      if (SolveMovesFormat.isRotation(move.getNotation())) {
        return choices;
      }
    }
    int from = 0;
    for (Algorithm algorithm : algorithmsOf(solveSteps)) {
      int to = from;
      while (to < stored.size() && stored.get(to).getOffsetMs() <= algorithm.endMs) {
        to++;
      }
      for (int pair : returningPairs(stored, from, to, algorithm.edgeSwap)) {
        choices = choices.withPairInverted(stored.get(pair).getOffsetMs());
      }
      from = to;
    }
    return choices;
  }

  /** One algorithm's pairs to fold, by the index of the near half; none where no reading fits. */
  private static List<Integer> returningPairs(List<Move> stored, int from, int to,
      boolean edgeSwap) {
    List<List<Integer>> readings = new ArrayList<List<Integer>>();
    readings.add(pairsWithin(stored, from, to, Slices.WINDOW_MS));
    readings.add(pairsWithin(stored, from, to, STRADDLED_SLICE_MS));
    for (int i = 0; i < 2; i++) { // then each of the two with one pair left out
      for (int left = 0; readings.get(i).size() > 1 && left < readings.get(i).size(); left++) {
        List<Integer> without = new ArrayList<Integer>(readings.get(i));
        without.remove(left);
        readings.add(without);
      }
    }
    for (int i = 0; i < 2; i++) {
      CubeRotation net = netSpin(stored, readings.get(i));
      if (net.isIdentity() || AXIS_HALF_TURNS.contains(net.getNotation())) {
        return readings.get(i);
      }
    }
    // With a pair left out both can fit: an M2 target turns the core half round, the rest do not.
    for (boolean halfTurn : new boolean[] {edgeSwap, !edgeSwap}) {
      for (int i = 2; i < readings.size(); i++) {
        CubeRotation net = netSpin(stored, readings.get(i));
        if (halfTurn ? AXIS_HALF_TURNS.contains(net.getNotation()) : net.isIdentity()) {
          return readings.get(i);
        }
      }
    }
    return Collections.<Integer>emptyList();
  }

  private static String[] sliceAt(List<Move> stored, int pair) {
    return Slices.forPair(stored.get(pair).getNotation(), stored.get(pair + 1).getNotation());
  }

  /** The opposite-face pairs in {@code stored[from, to)} whose halves are within {@code gapMs}. */
  private static List<Integer> pairsWithin(List<Move> stored, int from, int to, long gapMs) {
    List<Integer> pairs = new ArrayList<Integer>();
    for (int i = from; i + 1 < to; i++) {
      Move near = stored.get(i);
      Move far = stored.get(i + 1);
      if (far.getOffsetMs() - near.getOffsetMs() <= gapMs
          && Slices.forPair(near.getNotation(), far.getNotation()) != null) {
        pairs.add(i);
        i++;
      }
    }
    return pairs;
  }

  /** What the given pairs, read as slices, turn the core by between them. */
  private static CubeRotation netSpin(List<Move> stored, List<Integer> pairs) {
    CubeRotation net = CubeRotation.byNotation("");
    for (int pair : pairs) {
      // The spins are in the cube's own axes, so the later one goes on the right.
      net = CubeRotation.byNotation(sliceAt(stored, pair)[1]).then(net);
    }
    return net;
  }

  /** Every part of each step, then what the parts of that step left over. */
  private static List<Algorithm> algorithmsOf(List<SolveStep> solveSteps) {
    List<Algorithm> algorithms = new ArrayList<Algorithm>();
    long boundaryMs = 0;
    for (SolveStep step : solveSteps) {
      long partMs = boundaryMs;
      boundaryMs += step.getTotalMs();
      if (step.getExecutionMs() <= 0) {
        continue; // it turned nothing, so its moves are the next step's
      }
      for (SolveStep part : step.getSubSteps()) {
        partMs += part.getTotalMs();
        algorithms.add(new Algorithm(partMs, isEdgeSwap(part.getName())));
      }
      algorithms.add(new Algorithm(boundaryMs, false));
    }
    algorithms.add(new Algorithm(Long.MAX_VALUE, false));
    return algorithms;
  }

  /** A buffer and one edge target, nothing marked: the shape of an M2 target's name. */
  private static boolean isEdgeSwap(String name) {
    String[] pieces = AlgorithmSlots.pieces(name);
    return pieces.length == 2 && name.indexOf(':') < 0
        && pieces[0].length() == 2 && pieces[1].length() == 2;
  }

  /** Where one algorithm's moves end, and whether its name is an edge swap's. */
  private static final class Algorithm {

    private final long endMs;
    private final boolean edgeSwap;

    private Algorithm(long endMs, boolean edgeSwap) {
      this.endMs = endMs;
      this.edgeSwap = edgeSwap;
    }
  }

  /**
   * Every moment the reconstruction's frame changed, and what it changed to.
   *
   * <p>⚠️ <b>This is not the same as walking the rotation tokens of {@link #timedSolution}.</b> A
   * slice or a wide rocks the core, which turns the frame, but emits no rotation token — the spin is
   * the same physical event as the move and showing it would be wrong. So the emitted tokens
   * under-count the frame, badly on a Roux solve where the M slices never stop. Anything that has to
   * line a gyro reading up against what the reconstruction believes must read the frame from here.
   */
  public static List<FrameAt> framesOf(String storedMoves) {
    List<FrameAt> frames = new ArrayList<FrameAt>();
    inSolversFrame(SolveMovesFormat.parse(storedMoves), frames, null, null, null);
    return frames;
  }

  /** The frame the reconstruction has the cube in from {@code offsetMs} until the next entry. */
  public static final class FrameAt {

    private final long offsetMs;
    private final CubeRotation frame;

    private FrameAt(long offsetMs, CubeRotation frame) {
      this.offsetMs = offsetMs;
      this.frame = frame;
    }

    public long getOffsetMs() {
      return offsetMs;
    }

    public CubeRotation getFrame() {
      return frame;
    }
  }

  /**
   * Rewrites the stored stream the way the solver would have written it.
   *
   * <p>The cube names its faces in its own frame, which never moves: it reports {@code B} for the
   * same piece of plastic however the cube is being held. Read literally next to a rotation that is
   * wrong — turn the cube and {@code B} is no longer where a reader's {@code B} is. So each face
   * letter is carried through the rotations made before it, and each rotation is itself re-expressed
   * from the frame the solver was already in, leaving a sequence that can just be followed.
   *
   * <p>The stored stream stays raw for exactly this reason: the frame is rebuilt on the way out, so
   * a correction here fixes solves already recorded.
   *
   * <p>A wide next to the opposite face turned the same way is the slice ({@code r R'} is
   * {@code M'}), however far apart the two were, since the wide's spin is the slice's. Only the
   * algorithm boundaries stop it: merged across one, the far half would be shown in the wrong one.
   *
   * @param heldIn the one grip the solve was turned in, or null where it cannot be known. See the
   *     class javadoc for what only a blind solve can promise.
   * @param splits where each algorithm ends ({@link #splitsOf}), or null where none are known
   */
  private static List<Move> inSolversFrame(List<Move> stored, List<FrameAt> framesOut,
      CubeRotation heldIn, BlindChoices choices, List<Long> splits) {
    CubeRotation frame = heldIn != null ? heldIn : CubeRotation.byNotation("");
    List<Move> rewritten = new ArrayList<Move>(stored.size());
    if (heldIn != null && !heldIn.getNotation().isEmpty()) {
      rewritten.add(new Move(heldIn.getNotation(), 0)); // shown, so it follows from the scramble
      record(framesOut, 0, frame);
    }
    for (int i = 0; i < stored.size(); i++) {
      Move move = stored.get(i);
      String notation = move.getNotation();
      if (SolveMovesFormat.isRotation(notation)) {
        Move face = wideFace(stored, i);
        if (face != null && (choices == null || choices.believesWide(move.getOffsetMs()))) {
          // The solver did one wide move, named in their frame. As with a slice the spin is the
          // move itself rather than a grip change: not shown, but it still turns the frame.
          CubeRotation spin = CubeRotation.byNotation(notation).seenFrom(frame);
          String wide = Wides.forFaceAndSpin(relabelFace(frame, face.getNotation()),
              spin.getNotation());
          if (wide != null) {
            int far = sliceAfterWide(stored, i, splits);
            if (far > i) { // r R' is M': the same spin, so the frame turns the same either way
              wide = Slices.forPair(relabelFace(frame, face.getNotation()),
                  relabelFace(frame, stored.get(far).getNotation()))[0];
            }
            rewritten.add(new Move(wide, face.getOffsetMs()));
            frame = frame.then(spin);
            record(framesOut, face.getOffsetMs(), frame);
            i = far > i ? far : i + 1; // the face, and any far half, are spoken for
            continue;
          }
        }
        // One reorientation is stored as tokens sharing an offset; relabelled one at a time its
        // spelling would be misread as being about moved axes, so it is reassembled first.
        StringBuilder composite = new StringBuilder(notation);
        while (i + 1 < stored.size() && stored.get(i + 1).getOffsetMs() == move.getOffsetMs()
            && SolveMovesFormat.isRotation(stored.get(i + 1).getNotation())) {
          composite.append(' ').append(stored.get(++i).getNotation());
        }
        CubeRotation rotation = CubeRotation.byNotation(composite.toString());
        if (rotation == null || heldIn != null) {
          continue; // held in one grip: nothing here is turning the solver did
        }
        CubeRotation seen = rotation.seenFrom(frame);
        rewritten.add(new Move(seen.getNotation(), move.getOffsetMs()));
        frame = frame.then(seen); // the solver-frame rotation: then() composes in that frame
        record(framesOut, move.getOffsetMs(), frame);
      } else {
        boolean sensed = sliceCoreSpin(stored, i) != null
            && (choices == null || choices.foldsSlice(move.getOffsetMs()));
        int straddled = sensed || heldIn == null ? -1 : straddledSlice(stored, i);
        if (straddled > i && choices != null && !choices.foldsSlice(move.getOffsetMs())) {
          straddled = -1;
        }
        int far = sensed ? i + 1 : straddled > i ? straddled : foldedFar(stored, i, choices);
        if (far == i) {
          far = wideAfterFace(stored, i, choices, splits); // R' r is M' too
        }
        if (far > i) {
          // An opposite-face pair the gyro vouches for, or one the name check does: the solver did
          // one M/E/S, named in their frame. The spin is the same physical event as the move, not a
          // grip change, so it is not shown — but it still turns the frame, because the core really
          // did rock and every later face the cube reports is measured from there.
          Move next = stored.get(far);
          String spin = Slices.forPair(notation, next.getNotation())[1];
          String[] slice = Slices.forPair(
              relabelFace(frame, notation), relabelFace(frame, next.getNotation()));
          rewritten.add(new Move(slice[0], move.getOffsetMs()));
          frame = frame.then(CubeRotation.byNotation(spin).seenFrom(frame));
          record(framesOut, move.getOffsetMs(), frame);
          i = sensed ? far + 1 : far; // the gyro's own spin token is spoken for too
        } else {
          rewritten.add(new Move(frame.mapFace(notation.charAt(0)) + notation.substring(1),
              move.getOffsetMs()));
        }
      }
    }
    return rewritten;
  }

  /**
   * The wide spins that never happened: each one is the far half of a peek whose near half is a
   * rotation token this reading drops anyway.
   *
   * <p>A slice is corroborated by the pair of faces the cube reports, but a wide is a lone face the
   * gyro alone promotes, and <b>the gyro cannot tell a wide from a swing that came back</b> — the
   * two are the same reading either side of one face turn. What tells them apart is what happens
   * afterwards: a wide leaves the core a quarter turn round, a peek gives it back. So a wide's spin
   * and a rotation token that cancel each other out, with only the solve's own turning between them,
   * are one peek that the frame accounting wrote down as two events.
   *
   * <p>Cancelling is asked of the composed frame rather than of the two notations: they are a
   * quarter turn apart in the cube's own axes, so a slice between them re-expresses the second, and
   * the 2026-08-23 solve's pair reads {@code y} and {@code z} for exactly that reason.
   *
   * <p>Only a blind solve may be read this way. A solver who is looking at the cube turns it on
   * purpose, and a rotation that a wide happens to undo is two moves they meant.
   */
  static Set<Long> peekedWides(List<Move> stored) {
    List<FrameStep> steps = frameSteps(stored);
    List<Peek> peeks = new ArrayList<Peek>();
    for (int i = 0; i < steps.size(); i++) {
      for (int j = i + 1; j < steps.size(); j++) {
        if (steps.get(i).slice || steps.get(j).slice
            || steps.get(i).wide && steps.get(j).wide // two real wides may cancel
            || !cancels(steps, i, j)) {
          continue;
        }
        peeks.add(new Peek(i, j, steps.get(j).atMs - steps.get(i).atMs));
      }
    }
    // Nearest first, which does two things: a token that has a partner of its own is taken by it
    // before anything further off can claim it, and a nested peek settles before the one round it.
    Collections.sort(peeks, Comparator.comparingLong(peek -> peek.apartMs));
    Set<Long> peeked = new HashSet<Long>();
    boolean[] spent = new boolean[steps.size()];
    for (Peek peek : peeks) {
      if (spent[peek.from] || spent[peek.to] || !settledBetween(steps, spent, peek)) {
        continue;
      }
      spent[peek.from] = true;
      spent[peek.to] = true;
      FrameStep wide = steps.get(peek.from).wide ? steps.get(peek.from)
          : steps.get(peek.to).wide ? steps.get(peek.to) : null;
      if (wide != null) { // two rotation tokens taking each other back: no wide is in question
        peeked.add(wide.wideOffsetMs);
      }
    }
    return peeked;
  }

  /** Two frame changes that come to nothing, and how far apart the solve made them. */
  private static final class Peek {

    private final int from;
    private final int to;
    private final long apartMs;

    private Peek(int from, int to, long apartMs) {
      this.from = from;
      this.to = to;
      this.apartMs = apartMs;
    }
  }

  /**
   * Whether everything between a peek's two halves is either a slice or a peek already settled.
   *
   * <p>The correction is the accounting's <em>next</em> word on the matter, so a token or a wide
   * standing unaccounted for between the two is a chance to notice that was taken and did not
   * correct: whatever those two are, they are not one peek. Slices do not count, since no frame is
   * read inside a pair, and neither does a peek nested in this one, which the solver really can
   * make and the 2026-08-23 solve's twin capture does. Nesting needs no case of its own: the
   * shorter pair is always reached first, so by the time the longer one is asked its insides are
   * already spent.
   */
  private static boolean settledBetween(List<FrameStep> steps, boolean[] spent, Peek peek) {
    for (int k = peek.from + 1; k < peek.to; k++) {
      if (!steps.get(k).slice && !spent[k]) {
        return false;
      }
    }
    return true;
  }

  /** Whether steps {@code i} and {@code j} leave the frame where it stood, everything between kept. */
  private static boolean cancels(List<FrameStep> steps, int i, int j) {
    CubeRotation between = CubeRotation.byNotation("");
    for (int k = i + 1; k < j; k++) {
      between = steps.get(k).rotation.then(between); // composes in the cube's axes, so on the right
    }
    CubeRotation taken = steps.get(i).rotation.to(CubeRotation.byNotation(""));
    return between.to(between.then(taken)).getNotation().equals(steps.get(j).rotation.getNotation());
  }

  /** Everything in the stored stream that turns the frame, in order. */
  private static List<FrameStep> frameSteps(List<Move> stored) {
    List<FrameStep> steps = new ArrayList<FrameStep>();
    for (int i = 0; i < stored.size(); i++) {
      Move move = stored.get(i);
      String notation = move.getNotation();
      if (!SolveMovesFormat.isRotation(notation)) {
        Move spin = sliceCoreSpin(stored, i);
        int straddled = spin == null ? straddledSlice(stored, i) : -1;
        if (spin != null || straddled > i) {
          String turned = spin != null ? spin.getNotation() : stored.get(i + 1).getNotation();
          steps.add(FrameStep.slice(CubeRotation.byNotation(turned), move.getOffsetMs()));
          i = spin != null ? i + 2 : straddled;
        }
        continue;
      }
      Move face = wideFace(stored, i);
      // Whether the two name a wide does not depend on the frame, so it is asked without one: a
      // rotation landing a millisecond before a face it cannot be the spin of folds nowhere here
      // either, and counting it would put this walk out of step with the one that shows the moves.
      if (face != null && Wides.forFaceAndSpin(face.getNotation(), notation) != null) {
        steps.add(FrameStep.wide(CubeRotation.byNotation(notation), move.getOffsetMs()));
        i++; // the face is spoken for
        continue;
      }
      StringBuilder composite = new StringBuilder(notation);
      while (i + 1 < stored.size() && stored.get(i + 1).getOffsetMs() == move.getOffsetMs()
          && SolveMovesFormat.isRotation(stored.get(i + 1).getNotation())) {
        composite.append(' ').append(stored.get(++i).getNotation());
      }
      CubeRotation rotation = CubeRotation.byNotation(composite.toString());
      if (rotation != null) {
        steps.add(FrameStep.token(rotation, move.getOffsetMs()));
      }
    }
    return steps;
  }

  /**
   * One frame change: a slice's spin, a wide's spin, or a rotation token nobody vouches for.
   *
   * <p>Which of the three it is gets its own field rather than being read off the offset. A wide's
   * spin is dated a millisecond ahead of its face, so a solve whose first move is wide carries that
   * spin at {@code -1}: a real offset, as the drill capture shows, and not a spare value.
   */
  private static final class FrameStep {

    private final CubeRotation rotation;
    private final long atMs;
    private final long wideOffsetMs;
    private final boolean wide;
    private final boolean slice;

    private static FrameStep slice(CubeRotation rotation, long atMs) {
      return new FrameStep(rotation, atMs, 0, false, true);
    }

    private static FrameStep wide(CubeRotation rotation, long atMs) {
      return new FrameStep(rotation, atMs, atMs, true, false);
    }

    private static FrameStep token(CubeRotation rotation, long atMs) {
      return new FrameStep(rotation, atMs, 0, false, false);
    }

    private FrameStep(CubeRotation rotation, long atMs, long wideOffsetMs, boolean wide,
        boolean slice) {
      this.rotation = rotation;
      this.atMs = atMs;
      this.wideOffsetMs = wideOffsetMs;
      this.wide = wide;
      this.slice = slice;
    }
  }

  /** The grip the solve was picked up in, or none where the stream carries none to read. */
  private static CubeRotation gripOf(String storedMoves) {
    String pickup = SolveMovesFormat.pickupOf(storedMoves);
    CubeRotation grip = pickup == null ? null : CubeRotation.byNotation(pickup);
    return grip == null ? CubeRotation.byNotation("") : grip;
  }

  private static void record(List<FrameAt> framesOut, long offsetMs, CubeRotation frame) {
    if (framesOut != null) {
      framesOut.add(new FrameAt(offsetMs, frame));
    }
  }

  /**
   * When {@code stored[i]} and {@code stored[i+1]} are a slice-shaped pair immediately followed by
   * the exact whole-cube spin the core makes during that slice, returns that spin; otherwise null.
   *
   * <p>The spin is what tells a real slice from a two-handed pair of the same two faces: a slice
   * turns the middle layer and rocks the core (the gyro reports it), a two-handed pair leaves the
   * core still. Folding is faithful only with the spin present, because {@code pair · spin = slice}
   * exactly — fold without it and the reconstruction no longer solves. So a cube with no gyro emits
   * no spin, nothing folds, and the raw faces stand: always replayable, just not as tidy.
   */
  static Move sliceCoreSpin(List<Move> stored, int i) {
    if (i + 2 >= stored.size()) {
      return null;
    }
    // No time limit: the gyro's spin makes the pair that slice exactly, however long it took.
    String[] slice = Slices.forPair(stored.get(i).getNotation(), stored.get(i + 1).getNotation());
    Move spin = stored.get(i + 2);
    if (slice == null || !spin.getNotation().equals(slice[1])) {
      return null;
    }
    boolean lone = i + 3 >= stored.size()
        || !SolveMovesFormat.isRotation(stored.get(i + 3).getNotation())
        || stored.get(i + 3).getOffsetMs() != spin.getOffsetMs();
    return lone ? spin : null; // part of a bigger reorientation: leave it to the rotation path
  }

  /** The far face of a slow slice whose core spin was logged between its halves, or -1. */
  static int straddledSlice(List<Move> stored, int i) {
    if (i + 2 >= stored.size()) {
      return -1;
    }
    Move near = stored.get(i);
    Move spin = stored.get(i + 1);
    Move far = stored.get(i + 2);
    String[] slice = Slices.forPair(near.getNotation(), far.getNotation());
    return slice != null && spin.getNotation().equals(slice[1])
        && far.getOffsetMs() - near.getOffsetMs() <= STRADDLED_SLICE_MS ? i + 2 : -1;
  }

  /**
   * The face a wide's core spin belongs to, when {@code stored[i]} is one. The signature is the
   * offset: a wide's spin undercuts its face by a millisecond, where a regrip before the same face
   * shares its offset. No gyro means no spin, so nothing folds and the lone far face stands.
   *
   * <p>Solves recorded before wides were read keep the long spelling: the readings that tell a wide
   * from a regrip are not stored, so only the dating carries the answer forward.
   */
  static Move wideFace(List<Move> stored, int i) {
    if (i + 1 >= stored.size()) {
      return null;
    }
    Move face = stored.get(i + 1);
    return !SolveMovesFormat.isRotation(face.getNotation())
        && face.getOffsetMs() == stored.get(i).getOffsetMs() + 1
        ? face
        : null;
  }

  /**
   * The opposite face that makes the wide whose spin is {@code stored[spin]} a slice
   * ({@code r R'}), or -1. A face the gyro paired with its own neighbour is left to that pair.
   */
  private static int sliceAfterWide(List<Move> stored, int spin, List<Long> splits) {
    int far = spin + 2;
    if (far >= stored.size() || SolveMovesFormat.isRotation(stored.get(far).getNotation())
        || sliceCoreSpin(stored, far) != null) {
      return -1;
    }
    return makesSlice(stored.get(spin), stored.get(spin + 1), stored.get(far), splits) ? far : -1;
  }

  /** The wide's face when {@code stored[i]} and the believed wide after it are a slice, or -1. */
  private static int wideAfterFace(List<Move> stored, int i, BlindChoices choices,
      List<Long> splits) {
    if (i + 2 >= stored.size() || !SolveMovesFormat.isRotation(stored.get(i + 1).getNotation())
        || wideFace(stored, i + 1) == null
        || choices != null && !choices.believesWide(stored.get(i + 1).getOffsetMs())) {
      return -1;
    }
    return makesSlice(stored.get(i + 1), stored.get(i + 2), stored.get(i), splits) ? i + 2 : -1;
  }

  /** Whether a wide and an opposite face are one slice turned in one algorithm. */
  private static boolean makesSlice(Move spin, Move wideFace, Move face, List<Long> splits) {
    String[] slice = Slices.forPair(wideFace.getNotation(), face.getNotation());
    return slice != null && slice[1].equals(spin.getNotation())
        && !splitBetween(splits, Math.min(wideFace.getOffsetMs(), face.getOffsetMs()),
            Math.max(wideFace.getOffsetMs(), face.getOffsetMs()));
  }

  /** Where each algorithm's moves end, as the breakdown splits them; null for no steps. */
  private static List<Long> splitsOf(List<SolveStep> solveSteps) {
    if (solveSteps == null) {
      return null;
    }
    List<Long> splits = new ArrayList<Long>();
    for (Algorithm algorithm : algorithmsOf(solveSteps)) {
      splits.add(algorithm.endMs);
    }
    return splits;
  }

  /** Whether an algorithm ends at or after {@code fromMs} and before {@code toMs}. */
  private static boolean splitBetween(List<Long> splits, long fromMs, long toMs) {
    if (splits != null) {
      for (long split : splits) {
        if (fromMs <= split && split < toMs) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * The far half of a pair {@link BlindSpelling} settled was one slice, or {@code i} where the
   * reading has no such answer. The gyro said nothing about this one — that is why it is in
   * question — so what stands for its spin is the pair's own, which is the same turn either way.
   */
  private static int foldedFar(List<Move> stored, int i, BlindChoices choices) {
    if (choices == null || !choices.foldsPair(stored.get(i).getOffsetMs())) {
      return i;
    }
    int far = nextFace(stored, i + 1);
    return far > i
        && Slices.forPair(stored.get(i).getNotation(), stored.get(far).getNotation()) != null
        ? far : i;
  }

  /** The next move that turns a face, the rotation tokens between skipped, or -1 where none does. */
  static int nextFace(List<Move> stored, int from) {
    for (int i = from; i < stored.size(); i++) {
      if (!SolveMovesFormat.isRotation(stored.get(i).getNotation())) {
        return i;
      }
    }
    return -1;
  }

  private static String relabelFace(CubeRotation frame, String notation) {
    return frame.mapFace(notation.charAt(0)) + notation.substring(1);
  }

  /**
   * A step's moves, split into one group per part — the pairs of an F2L, the looks of an OLL. The
   * groups stay aligned with the parts, empty ones included, so each part can be shown its own
   * count; anything the parts did not account for trails behind them.
   */
  private static List<List<Token>> groupsFor(List<Move> moves, int from, int to,
      List<SolveStep> parts, long stepStartMs) {
    List<List<Token>> groups = new ArrayList<List<Token>>();
    if (parts.isEmpty()) {
      groups.add(toHalfTurns(moves.subList(from, to)));
      return groups;
    }
    long boundaryMs = stepStartMs;
    int taken = from;
    for (SolveStep part : parts) {
      boundaryMs += part.getTotalMs();
      int end = endOf(moves, taken, boundaryMs);
      groups.add(toHalfTurns(moves.subList(taken, end)));
      taken = end;
    }
    if (to > taken) {
      groups.add(toHalfTurns(moves.subList(taken, to)));
    }
    return groups;
  }

  private static int endOf(List<Move> moves, int from, long boundaryMs) {
    int end = from;
    while (end < moves.size() && moves.get(end).getOffsetMs() <= boundaryMs) {
      end++;
    }
    return end;
  }

  private static List<Token> toHalfTurns(List<Move> moves) {
    List<Token> tokens = new ArrayList<Token>();
    for (int i = 0; i < moves.size(); i++) {
      String notation = moves.get(i).getNotation();
      boolean isDouble = i + 1 < moves.size() && moves.get(i + 1).getNotation().equals(notation)
          && notation.indexOf(' ') < 0; // "y z2" twice is not a half turn of anything
      tokens.add(new Token(isDouble ? notation.substring(0, 1) + "2" : notation));
      if (isDouble) {
        i++;
      }
    }
    return tokens;
  }

  /** One move as it is displayed, after the slices, the wides and the half turns have been folded. */
  public static final class Token {

    private final String notation;
    private Token partner;

    private Token(String notation) {
      this.notation = notation;
    }

    public String getNotation() {
      return notation;
    }
  }

  /**
   * The moves to strike out of {@code groups}: the cancelled ones whose partner is shown beside
   * them. A strike says these moves and the ones between them came to nothing, so a pair split
   * between two rows must not be drawn — the half in each row would be claiming that of moves it
   * never touched. The pair is still struck wherever both halves are shown together, which for two
   * parts of a step is the step's own row.
   */
  public static Set<Token> cancelledIn(List<List<Token>> groups) {
    Set<Token> shown = new HashSet<Token>();
    for (List<Token> group : groups) {
      shown.addAll(group);
    }
    Set<Token> cancelled = new HashSet<Token>();
    for (Token token : shown) {
      if (token.partner != null && shown.contains(token.partner)) {
        cancelled.add(token);
      }
    }
    return cancelled;
  }

  /**
   * Pairs the moves that undid each other, over the whole solution at once.
   *
   * <p>Push each token; when it inverts the one on top, the pair is spent, so the two are married
   * and the one below comes back into reach. Nesting needs no case of its own: {@code R U F F' U' R'}
   * collapses from the inside out. Nothing is moved or removed, which is what lets this run across
   * the step and part boundaries the moves were already split at — where a pair lands only decides
   * which rows can draw it, not whether it happened.
   */
  private static void markCancelled(List<Step> steps) {
    List<Token> stack = new ArrayList<Token>();
    for (Step step : steps) {
      for (List<Token> group : step.getGroups()) {
        for (Token token : group) {
          int top = stack.size() - 1;
          if (top >= 0 && inverts(stack.get(top).getNotation(), token.getNotation())) {
            token.partner = stack.remove(top);
            token.partner.partner = token;
          } else {
            stack.add(token);
          }
        }
      }
    }
  }

  /**
   * Whether {@code b} undoes {@code a}. A reorientation spelled as several rotations at once is
   * never anyone's inverse: it would have to be met by its own mirror image, spelled backwards.
   */
  private static boolean inverts(String a, String b) {
    if (a.indexOf(' ') >= 0) {
      return false;
    }
    String inverse = a.endsWith("2") ? a
        : (a.endsWith("'") ? a.substring(0, a.length() - 1) : a + "'");
    return inverse.equals(b);
  }

  public List<Step> getSteps() {
    return steps;
  }

  public boolean isEmpty() {
    return steps.isEmpty();
  }

  public int getMoveCount() {
    return moveCount;
  }

  /** How many parts the steps were built from — for a blind solve, how many algorithms it took. */
  public int getPartCount() {
    return partCount;
  }

  /**
   * Turns per second over the time actually spent turning, rather than over the whole solve. On a
   * sighted solve the two are the same thing, since every step turns something. On a blind one they
   * are not: memorising is most of the solve and moves nothing, and nothing stops the timer at the
   * solved state, so dividing by the recorded time would report a rate the hands never went at.
   *
   * <p>A step counts as turning when it has <em>execution</em>, not merely a move: memorisation ends
   * on the first turn and so owns it, but that turn is the first of the solving and belongs to the
   * rate — which is what dividing by execution alone gives. The same test drops a solve's trailing
   * stare, whether it was spent stuck or taking a blindfold off. 0 when nothing turned.
   */
  public double getTps() {
    return turningMs > 0 ? moveCount * 1000d / turningMs : 0;
  }

  public static final class Step {

    private final int index;
    private final String name;
    private final List<List<Token>> groups;
    private final int moveCount;
    private final List<long[]> pauses;
    private final long pauseMs;
    private final int stops;
    private final long stopMs;

    Step(int index, String name, List<List<Token>> groups, List<long[]> pauses, long stopLineMs) {
      this.index = index;
      this.name = name;
      this.pauses = Collections.unmodifiableList(pauses);
      long pauseMs = 0;
      int stops = 0;
      long stopMs = 0;
      for (long[] pause : pauses) {
        pauseMs += pause[1] - pause[0];
        if (pause[1] - pause[0] > stopLineMs) {
          stops++;
          stopMs += pause[1] - pause[0];
        }
      }
      this.pauseMs = pauseMs;
      this.stops = stops;
      this.stopMs = stopMs;
      List<List<Token>> parts = new ArrayList<List<Token>>(groups.size());
      for (List<Token> group : groups) {
        parts.add(Collections.unmodifiableList(group));
      }
      this.groups = Collections.unmodifiableList(parts);
      this.moveCount = countMoves(groups);
    }

    private static int countMoves(List<List<Token>> groups) {
      int count = 0;
      for (List<Token> group : groups) {
        count += countGroup(group);
      }
      return count;
    }

    /** Rotations are shown but never counted: turning the whole cube solves nothing. */
    private static int countGroup(List<Token> group) {
      int count = 0;
      for (Token token : group) {
        if (!SolveMovesFormat.isRotation(token.getNotation())) {
          count++;
        }
      }
      return count;
    }

    private static String join(List<Token> group) {
      StringBuilder sb = new StringBuilder();
      for (Token token : group) {
        if (sb.length() > 0) {
          sb.append(' ');
        }
        sb.append(token.getNotation());
      }
      return sb.toString();
    }

    /** The moves of one part, by its position in the step — 0 for a part built with none. */
    public int getPartMoveCount(int part) {
      return part < groups.size() ? countGroup(groups.get(part)) : 0;
    }

    /** The moves of one part, by its position in the step — empty for a part built with none. */
    public String getPartMoves(int part) {
      return part < groups.size() ? join(groups.get(part)) : "";
    }

    public int getIndex() {
      return index;
    }

    public String getName() {
      return name;
    }

    public int getMoveCount() {
      return moveCount;
    }

    /**
     * When the execution was not turning, between two turns of one part ({@link #pausesOf}). Each
     * one {from, to}, in milliseconds since the solve started.
     */
    public List<long[]> getPauseSpans() {
      return pauses;
    }

    /** What those pauses came to. */
    public long getPauseMs() {
      return pauseMs;
    }

    /** How many of those pauses were long enough to be a stop rather than slow turning. */
    public int getStops() {
      return stops;
    }

    /** What those stops came to. */
    public long getStopMs() {
      return stopMs;
    }

    /**
     * The moves of each part, in order, for a display that has to tell them apart move by move.
     * {@link #getMoves} is the same thing as text, for one that does not.
     */
    public List<List<Token>> getGroups() {
      return groups;
    }

    /** The parts joined for display, separated so the slot and look boundaries stay readable. */
    public String getMoves() {
      StringBuilder sb = new StringBuilder();
      for (List<Token> group : groups) {
        if (group.isEmpty()) { // a part built with no move of its own would show as a stray separator
          continue;
        }
        if (sb.length() > 0) {
          sb.append(GROUP_SEPARATOR);
        }
        sb.append(join(group));
      }
      return sb.toString();
    }
  }
}
