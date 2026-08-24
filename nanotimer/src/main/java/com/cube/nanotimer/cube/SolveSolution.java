package com.cube.nanotimer.cube;

import com.cube.nanotimer.cube.SolveMovesFormat.Move;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveStep;
import java.util.ArrayList;
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
 * <p><b>No other solve may be read this way.</b> Nothing guards the stored grip against a first
 * move that is <em>wide</em>, whose swing the gyro has already reported, and the scripted wide
 * drill stores a grip its own ground truth disowns. Spelling a sighted solve from the grip turns
 * every letter of it a quarter turn.
 */
public final class SolveSolution {

  /** Between the parts of a step, wherever they are shown as one run of moves. */
  public static final String GROUP_SEPARATOR = " · ";

  /**
   * How far apart the two halves of a peek may land before a wide stops being in question. A tip
   * and its tip back happen inside one algorithm: the 2026-08-23 capture's two are 333 ms and
   * 789 ms across, the second only that far because a slice pair stood between the swing and the
   * move the frame was next read at. Two rotation tokens are paired at any distance, since taking
   * each other back is all they can do and leaving them loose is what lets one claim a wide.
   */
  private static final long PEEK_WINDOW_MS = 2000;

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
    List<Move> moves = inSolversFrame(SolveMovesFormat.parse(storedMoves), null,
        method == CubeMethod.BLIND ? gripOf(storedMoves) : null);
    if (moves.isEmpty() || solveSteps == null || solveSteps.isEmpty()) {
      return new SolveSolution(new ArrayList<Step>(), 0, 0, 0);
    }
    List<Step> steps = new ArrayList<Step>();
    int total = 0;
    int taken = 0;
    long boundaryMs = 0;
    long turningMs = 0;
    int parts = 0;
    for (int i = 0; i < solveSteps.size(); i++) {
      SolveStep solveStep = solveSteps.get(i);
      long stepStartMs = boundaryMs;
      boundaryMs += solveStep.getTotalMs();
      // A step ends on the move that completed it and so owns that move — except a step that turned
      // nothing, which owns none. Memorisation ends the moment the cube is first turned, and that
      // turn is the first of the solving: shown under the memo it reads as a move made blind.
      int end = solveStep.getExecutionMs() > 0 ? endOf(moves, taken, boundaryMs) : taken;
      Step step = new Step(i, solveStep.getName(),
          groupsFor(moves, taken, end, solveStep.getSubSteps(), stepStartMs));
      steps.add(step);
      total += step.getMoveCount();
      if (solveStep.getExecutionMs() > 0) { // a step that turned nothing has none: see getTps
        turningMs += solveStep.getTotalMs();
      }
      parts += solveStep.getSubSteps().size();
      taken = end;
    }
    markCancelled(steps);
    return new SolveSolution(steps, total, parts, turningMs);
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
    return inSolversFrame(SolveMovesFormat.parse(storedMoves), null, null);
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
    inSolversFrame(SolveMovesFormat.parse(storedMoves), frames, null);
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
   * @param heldIn the one grip the solve was turned in, or null where it cannot be known. See the
   *     class javadoc for what only a blind solve can promise.
   */
  private static List<Move> inSolversFrame(List<Move> stored, List<FrameAt> framesOut,
      CubeRotation heldIn) {
    CubeRotation frame = heldIn != null ? heldIn : CubeRotation.byNotation("");
    Set<Long> peeked = heldIn != null ? peekedWides(stored) : Collections.<Long>emptySet();
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
        if (face != null && !peeked.contains(move.getOffsetMs())) {
          // The solver did one wide move, named in their frame. As with a slice the spin is the
          // move itself rather than a grip change: not shown, but it still turns the frame.
          CubeRotation spin = CubeRotation.byNotation(notation).seenFrom(frame);
          String wide = Wides.forFaceAndSpin(relabelFace(frame, face.getNotation()),
              spin.getNotation());
          if (wide != null) {
            rewritten.add(new Move(wide, face.getOffsetMs()));
            frame = frame.then(spin);
            record(framesOut, face.getOffsetMs(), frame);
            i++; // the face is spoken for: it is half of the move just written
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
        Move spin = sliceCoreSpin(stored, i);
        if (spin != null) {
          // A sensed opposite-face pair with the core-spin the gyro reports for a slice: the
          // solver did one M/E/S, named in their frame. The spin is the same physical event, not a
          // grip change, so it is not shown — but it still turns the frame, because the core really
          // did rock and every later face the cube reports is measured from there.
          Move next = stored.get(i + 1);
          String[] slice = Slices.forPair(
              relabelFace(frame, notation), relabelFace(frame, next.getNotation()));
          rewritten.add(new Move(slice[0], move.getOffsetMs()));
          frame = frame.then(CubeRotation.byNotation(spin.getNotation()).seenFrom(frame));
          record(framesOut, move.getOffsetMs(), frame);
          i += 2;
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
  private static Set<Long> peekedWides(List<Move> stored) {
    List<FrameStep> steps = frameSteps(stored);
    List<Peek> peeks = new ArrayList<Peek>();
    for (int i = 0; i < steps.size(); i++) {
      for (int j = i + 1; j < steps.size(); j++) {
        if (steps.get(i).slice || steps.get(j).slice
            || steps.get(i).isWide() && steps.get(j).isWide() // two real wides may cancel
            || !cancels(steps, i, j)) {
          continue;
        }
        long apartMs = steps.get(j).atMs - steps.get(i).atMs;
        if ((steps.get(i).isWide() || steps.get(j).isWide()) && apartMs > PEEK_WINDOW_MS) {
          continue; // too far apart to be one peek, and a wide is not given away on a coincidence
        }
        peeks.add(new Peek(i, j, apartMs));
      }
    }
    // Nearest first: the two halves of one peek are seconds apart at most, and taking them in that
    // order stops a token that has a partner of its own from claiming a wide further off.
    Collections.sort(peeks, Comparator.comparingLong(peek -> peek.apartMs));
    Set<Long> peeked = new HashSet<Long>();
    boolean[] spent = new boolean[steps.size()];
    for (Peek peek : peeks) {
      if (spent[peek.from] || spent[peek.to]) {
        continue;
      }
      spent[peek.from] = true;
      spent[peek.to] = true;
      long wide = Math.max(steps.get(peek.from).wideOffsetMs, steps.get(peek.to).wideOffsetMs);
      if (wide >= 0) { // two rotation tokens taking each other back: no wide is in question
        peeked.add(wide);
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
        if (spin != null) {
          steps.add(new FrameStep(CubeRotation.byNotation(spin.getNotation()),
              move.getOffsetMs(), -1, true));
          i += 2;
        }
        continue;
      }
      Move face = wideFace(stored, i);
      // Whether the two name a wide does not depend on the frame, so it is asked without one: a
      // rotation landing a millisecond before a face it cannot be the spin of folds nowhere here
      // either, and counting it would put this walk out of step with the one that shows the moves.
      if (face != null && Wides.forFaceAndSpin(face.getNotation(), notation) != null) {
        steps.add(new FrameStep(CubeRotation.byNotation(notation), move.getOffsetMs(),
            move.getOffsetMs(), false));
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
        steps.add(new FrameStep(rotation, move.getOffsetMs(), -1, false));
      }
    }
    return steps;
  }

  /** One frame change: a slice's spin, a wide's spin, or a rotation token nobody vouches for. */
  private static final class FrameStep {

    private final CubeRotation rotation;
    private final long atMs;
    private final long wideOffsetMs;
    private final boolean slice;

    private FrameStep(CubeRotation rotation, long atMs, long wideOffsetMs, boolean slice) {
      this.rotation = rotation;
      this.atMs = atMs;
      this.wideOffsetMs = wideOffsetMs;
      this.slice = slice;
    }

    private boolean isWide() {
      return wideOffsetMs >= 0;
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
  private static Move sliceCoreSpin(List<Move> stored, int i) {
    if (i + 2 >= stored.size()) {
      return null;
    }
    String[] slice = slicePair(stored.get(i), stored.get(i + 1));
    Move spin = stored.get(i + 2);
    if (slice == null || !spin.getNotation().equals(slice[1])) {
      return null;
    }
    boolean lone = i + 3 >= stored.size()
        || !SolveMovesFormat.isRotation(stored.get(i + 3).getNotation())
        || stored.get(i + 3).getOffsetMs() != spin.getOffsetMs();
    return lone ? spin : null; // part of a bigger reorientation: leave it to the rotation path
  }

  /**
   * The face a wide's core spin belongs to, when {@code stored[i]} is one. The signature is the
   * offset: a wide's spin undercuts its face by a millisecond, where a regrip before the same face
   * shares its offset. No gyro means no spin, so nothing folds and the lone far face stands.
   *
   * <p>Solves recorded before wides were read keep the long spelling: the readings that tell a wide
   * from a regrip are not stored, so only the dating carries the answer forward.
   */
  private static Move wideFace(List<Move> stored, int i) {
    if (i + 1 >= stored.size()) {
      return null;
    }
    Move face = stored.get(i + 1);
    return !SolveMovesFormat.isRotation(face.getNotation())
        && face.getOffsetMs() == stored.get(i).getOffsetMs() + 1
        ? face
        : null;
  }

  /** The slice and spin for two moves close enough together to be one, or null. */
  private static String[] slicePair(Move a, Move b) {
    return b.getOffsetMs() - a.getOffsetMs() > Slices.WINDOW_MS
        ? null
        : Slices.forPair(a.getNotation(), b.getNotation());
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

    Step(int index, String name, List<List<Token>> groups) {
      this.index = index;
      this.name = name;
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
