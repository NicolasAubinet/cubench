package com.cube.nanotimer.smartcube.drill;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.Face;
import com.cube.nanotimer.smartcube.step.FaceTurns;
import com.cube.nanotimer.smartcube.step.LastLayerScrambles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A drill being run: the virtual cube holds the case, the smart cube is only the input, and the
 * user's own cube ends up scrambled, which is expected and needs nothing done about it.
 *
 * <p><b>Why this needs nothing from the gyro.</b> A cube reports its turns against its own centres,
 * so feeding them straight to a virtual cube keeps that cube equal to the physical one whatever the
 * user does with it. Turning the whole cube round reports nothing and changes nothing, and the next
 * turn arrives as whichever face it physically was. A slice is the one move that carries the centres
 * with it, and it is still no exception: {@code M} arriving as {@code R} and {@code L'} is not a
 * misreading but the truth relative to the centres that moved. Solved relative to your own centres
 * is what solved means, so {@link CubieCube#isSolved} is the whole of the check and the frame never
 * comes into it. What the frame is needed for is naming <em>where</em> in a solve something happened,
 * and a drill never asks.
 *
 * <p>What that does leave is the user's grip, and it is a matter of what is <em>shown</em>, never of
 * what is checked here: a case drawn on a cube standing square while the user holds theirs some
 * other way has them turning the face they meant on a face they cannot see. The screen answers it by
 * following the grip off the gyro; nothing in this class needs to know either way.
 */
public final class DrillSession {

  private static final long NOT_SHOWN = -1;

  /** Where the faces sit in a facelet string, which is the order {@link CubieCube} writes one in. */
  private static final String FACELET_ORDER = "URFDLB";

  private final DrillSpec spec;
  private final Random random;
  private final Map<String, Long> weights;
  /** The face the last layer is dealt onto, which is the colour the user solves it on. */
  private final String layerFace;
  private final List<String> cases = new ArrayList<String>();
  private final List<String> unknownCases = new ArrayList<String>();
  private final List<DrillRep> reps = new ArrayList<DrillRep>();
  private final List<String> pass = new ArrayList<String>();

  private CubieCube cube = new CubieCube();
  private String currentCase;
  private String currentScramble;
  private final List<CubeMove> moves = new ArrayList<CubeMove>();
  private long firstMoveMs;
  /** The first turn that was not an AUF, which is where the algorithm really started. */
  private long algStartMs;
  private long lastMoveMs;
  /** Host wall-clock, converted to the cube's clock when it is read. See {@link #markCaseShown}. */
  private long caseShownAtMs = NOT_SHOWN;
  /** Host clock minus cube clock, from the last move that carried both. */
  private long clockSkewMs;
  private int resetCount;
  private boolean revealed;

  public DrillSession(DrillSpec spec, Random random) {
    this(spec, random, null, "U");
  }

  public DrillSession(DrillSpec spec, Random random, Map<String, Long> weights) {
    this(spec, random, weights, "U");
  }

  /**
   * @param weights what each case is costing, for a spec drawing by weight. A case with no weight
   *     still comes up, since nothing known about it is not a reason to never practise it.
   * @param layerFace the face to deal the last layer onto. Not the sender's business and not on the
   *     spec: which colour a solver finishes on is theirs, and a drill prescribed from outside has
   *     no way of knowing it.
   */
  public DrillSession(DrillSpec spec, Random random, Map<String, Long> weights, String layerFace) {
    if (spec.getType() == DrillSpec.Type.CROSS) {
      throw new IllegalArgumentException("A cross drill is run by CrossDrillSession");
    }
    this.spec = spec;
    this.random = random;
    this.weights = weights;
    this.layerFace = layerFace == null ? "U" : layerFace;
    List<String> known = LastLayerScrambles.cases();
    for (String code : spec.getCases()) {
      (known.contains(code) ? cases : unknownCases).add(code);
    }
  }

  public DrillSpec getSpec() {
    return spec;
  }

  /** The cases asked for that this app has no scramble for, dropped rather than run wrong. */
  public List<String> getUnknownCases() {
    return Collections.unmodifiableList(unknownCases);
  }

  /** False when every case asked for was dropped, which leaves nothing to run. */
  public boolean isRunnable() {
    return !cases.isEmpty();
  }

  /**
   * Puts the next case on the virtual cube, or returns false when the drill is over. The rep does
   * not start here: it starts when the case reaches the user's eyes, which only the caller knows
   * and says with {@link #markCaseShown}.
   */
  public boolean nextRep() {
    if (isFinished()) {
      currentCase = null;
      return false;
    }
    currentCase = pick();
    currentScramble = LayerRotation.toFace(
        LastLayerScrambles.forCase(currentCase, random), layerFace);
    cube = new CubieCube();
    FaceTurns.apply(cube, currentScramble);
    moves.clear();
    firstMoveMs = 0;
    algStartMs = 0;
    lastMoveMs = 0;
    caseShownAtMs = NOT_SHOWN;
    resetCount = 0;
    revealed = false;
    return true;
  }

  /**
   * The case is now in front of the user, as of a host wall-clock timestamp. This is where
   * recognition runs from, and until it is called the rep has not begun and turns are not counted
   * against it.
   *
   * <p><b>The two clocks are not the same one.</b> A move is stamped on the cube's own clock, which
   * is fitted to host time when the cube connects and only re-fitted once it has drifted seconds;
   * subtracting one from the other took whatever the fit was out by out of the user's recognition
   * and gave it to their execution, which is how a rep came back as 0.00 to recognise and four
   * seconds to turn. The moves carry both stamps often enough to keep the difference, so this one
   * is converted rather than compared.
   *
   * <p>Set apart from {@link #nextRep} because the two are not the same moment. A case is chosen
   * before it can be drawn, and a screen may hold the one just finished for a beat so that solving
   * it reads as an ending rather than as a jump; measuring from the previous rep's last move, which
   * is what this used to do, charged every one of those beats to the user's recognition.
   */
  public void markCaseShown(long hostMs) {
    caseShownAtMs = hostMs;
  }

  /**
   * The user looked up the algorithm for the case in front of them. Nothing about the rep changes:
   * it is still theirs to finish and still timed, since stopping the clock would only teach them to
   * look things up. What it costs is the claim that the time says they know the case, and the rep
   * carries that so nothing reading the drill back has to guess.
   */
  public void markRevealed() {
    if (currentCase != null) {
      revealed = true;
    }
  }

  /**
   * Puts the case back as it was and starts the rep over, for one botched by a slip rather than by
   * not knowing it. Costs no rep: the case comes round again here and now, where skipping it would
   * spend it and move on.
   *
   * <p>The same scramble, not another of the same case: a redo is of the position that went wrong,
   * and a fresh alignment would be a different one to look at. The rep it eventually finishes
   * carries how many times it was restarted, and is no measurement of the case: recognition is
   * timed again off a position the user has already read and begun on, which is a couple of tenths
   * whatever they know. The figures leave such a rep out, and {@link #showAgain} deals the case once
   * more so the drill is not left with nothing for it.
   *
   * <p>Like a case just dealt, the rep is not running until {@link #markCaseShown} says the user can
   * see it again.
   */
  public void resetRep() {
    if (currentCase == null) {
      return;
    }
    cube = new CubieCube();
    FaceTurns.apply(cube, currentScramble);
    moves.clear();
    firstMoveMs = 0;
    algStartMs = 0;
    lastMoveMs = 0;
    caseShownAtMs = NOT_SHOWN;
    resetCount++;
  }

  /**
   * Feeds one turn to the virtual cube. Turns made before the case was shown are dropped rather
   * than queued: the cube on screen missed them too, so the two stay in step, and a rep cannot be
   * timed against a case the user could not yet see.
   *
   * @return the finished rep if that turn solved the case, null while it is still going
   */
  public DrillRep onMove(CubeMove move) {
    if (currentCase == null || caseShownAtMs == NOT_SHOWN) {
      return null;
    }
    if (move.getHostTimestampMs() != null) {
      clockSkewMs = move.getHostTimestampMs() - move.getCubeTimestampMs();
    }
    long at = move.getCubeTimestampMs();
    if (at < caseShownOnCubeClock()) {
      // Turned before this case was up and delivered late: the cube still has to have it, or the
      // two fall out of step, but it is the previous case's turn and cannot start this rep, and it
      // is not one of the turns the rep is made of.
      cube.applyMove(move.getFace(), move.isPrime());
      return isCaseDone() ? complete(false) : null;
    }
    if (moves.isEmpty()) {
      firstMoveMs = at;
    }
    if (algStartMs == 0 && move.getFace() != Face.valueOf(layerFace)) {
      algStartMs = at;
    }
    moves.add(move);
    lastMoveMs = at;
    cube.applyMove(move.getFace(), move.isPrime());
    return isCaseDone() ? complete(false) : null;
  }

  /**
   * <b>An OLL is done when the layer is one colour, not when the cube is solved.</b> Orienting is
   * all an OLL claims to do, and every good algorithm for a case leaves the layer permuted its own
   * way; a scramble here is one particular algorithm inverted, so ending on a solved cube would ask
   * the user to reproduce that algorithm rather than the one they know, and any other correct
   * algorithm would orient the face and then never finish. A PLL restores the permutation, so for
   * one of those a solved cube is exactly the question.
   */
  private boolean isCaseDone() {
    if (currentCase != null && currentCase.startsWith("oll_")) {
      return isLayerOriented();
    }
    return cube.isSolved();
  }

  /** Whether the nine stickers of the layer's face all show the same colour. */
  private boolean isLayerOriented() {
    String facelets = cube.toFaceCube();
    int start = FACELET_ORDER.indexOf(layerFace) * 9;
    for (int i = 1; i < 9; i++) {
      if (facelets.charAt(start + i) != facelets.charAt(start)) {
        return false;
      }
    }
    return true;
  }

  /** Gives up on the case in front of the user, spending the rep. Null when there is none. */
  public DrillRep abandon() {
    return currentCase == null ? null : complete(true);
  }

  /**
   * A turn of the layer's own face before the algorithm only squared the case up to be read, so it
   * is looking rather than solving; the one that closes an algorithm falls inside the execution by
   * construction. A rep of nothing but those has no algorithm to have started, and is split at its
   * first turn as before.
   */
  private DrillRep complete(boolean abandoned) {
    long algStart = algStartMs > 0 ? algStartMs : firstMoveMs;
    // On the cube's clock, not the host's: the turns are stamped on that one, and the offsets the
    // rep is stored with are measured from this.
    long shownAt = caseShownOnCubeClock();
    long recognition = moves.isEmpty() ? 0 : Math.max(0, algStart - shownAt);
    long execution = moves.isEmpty() ? 0 : Math.max(0, lastMoveMs - algStart);
    DrillRep rep = new DrillRep(currentCase, currentScramble,
        new ArrayList<CubeMove>(moves), shownAt, recognition, execution, moves.size(),
        resetCount, revealed, abandoned);
    reps.add(rep);
    if (resetCount > 0 || revealed) {
      showAgain(currentCase);
    }
    currentCase = null;
    caseShownAtMs = NOT_SHOWN;
    return rep;
  }

  /** When the case went up, said in the clock the moves are stamped on. */
  private long caseShownOnCubeClock() {
    return caseShownAtMs - clockSkewMs;
  }

  /**
   * Deals a case again later in this pass, for one whose rep measured nothing: restarted, or solved
   * with the algorithm on screen. Spending a case on a rep the figures will not read leaves the
   * drill with nothing to say about it, and on a short drill it never comes round again.
   *
   * <p>Never as the next case, which is what {@link #resetRep} already offers and what nobody needs
   * twice. Nothing is done where the pass is spent, since the next one deals every case anyway, and
   * nothing for a drill drawing by weight, which can deal any case at any time.
   *
   * <p><b>And nothing where the drill has no rep to spare.</b> The extra showing is a rep like any
   * other and comes out of the same count, so on a drill of one rep per case it was paid for by the
   * case at the end of the pass, which the user had picked and then never saw. A case still owed
   * its first turn outranks a second turn for one that has already had it.
   */
  private void showAgain(String code) {
    if (!pass.isEmpty() && spec.getReps() - reps.size() > pass.size()) {
      pass.add(random.nextInt(pass.size()), code);
    }
  }

  /** Every case in turn, the order redrawn each pass so that the next one cannot be guessed. */
  private String pick() {
    if (spec.getSelection() == DrillSpec.Selection.WEIGHTED) {
      return weighted();
    }
    if (pass.isEmpty()) {
      pass.addAll(cases);
      Collections.shuffle(pass, random);
    }
    return pass.remove(pass.size() - 1);
  }

  private String weighted() {
    long total = 0;
    for (String code : cases) {
      total += weightOf(code);
    }
    long draw = (long) (random.nextDouble() * total);
    for (String code : cases) {
      draw -= weightOf(code);
      if (draw < 0) {
        return code;
      }
    }
    return cases.get(cases.size() - 1);
  }

  private long weightOf(String code) {
    Long weight = weights == null ? null : weights.get(code);
    return weight == null || weight <= 0 ? 1 : weight;
  }

  /** The case in front of the user, or null between reps. */
  public String getCurrentCase() {
    return currentCase;
  }

  /** What the virtual cube was set up with. */
  public String getCurrentScramble() {
    return currentScramble;
  }

  /** The virtual cube as it stands, for drawing. */
  public String getFacelets() {
    return cube.toFaceCube();
  }

  public List<DrillRep> getReps() {
    return Collections.unmodifiableList(reps);
  }

  public boolean isFinished() {
    return !isRunnable() || reps.size() >= spec.getReps();
  }
}
