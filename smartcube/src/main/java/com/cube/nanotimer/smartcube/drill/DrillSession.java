package com.cube.nanotimer.smartcube.drill;

import com.cube.nanotimer.drill.DrillSpec;

import com.cube.nanotimer.smartcube.cube.CubeTwin;
import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import com.cube.nanotimer.smartcube.step.CaseAlgorithms;
import com.cube.nanotimer.smartcube.step.F2LPairScrambles;
import com.cube.nanotimer.smartcube.step.FaceTurns;
import com.cube.nanotimer.smartcube.step.LastLayerScrambles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
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

  /** Most copies of one case a weighted pass holds, so a heavy case cannot crowd out the rest. */
  private static final int MAX_COPIES = 3;
  /** Cases left below which cleared ones are mixed back in. */
  private static final int MIN_MIX = 3;

  /**
   * The case code every look-ahead rep carries: its two pairs are composed, so no case names them,
   * and the rep is recorded under the drill's own type.
   */
  public static final String LOOKAHEAD_CASE = DrillSpec.Type.F2L_LOOKAHEAD.code();

  /** Whether a rep's code is a look-ahead rep's, which names no case and has no algorithms. */
  public static boolean isLookAheadCase(String code) {
    return LOOKAHEAD_CASE.equals(code);
  }

  /** Deals tried before a look-ahead rep takes one with a piece stuck in the other slot. */
  private static final int MAX_LOOKAHEAD_DEALS = 50;

  /** The cross and the four slots with the cross on D, each slot as its corner and its edge. */
  private static final String[] CROSS_EDGES = {"DF", "DR", "DB", "DL"};
  private static final String[][] SLOTS = {{"DFR", "FR"}, {"DLF", "FL"}, {"DBL", "BL"},
      {"DRB", "BR"}};

  private final DrillSpec spec;
  private final Random random;
  private final Map<String, Long> weights;
  /** The face the last layer is dealt onto, which is the colour the user solves it on. */
  private final String layerFace;
  private final List<String> cases = new ArrayList<String>();
  private final List<String> unknownCases = new ArrayList<String>();
  private final List<DrillRep> reps = new ArrayList<DrillRep>();
  private final List<String> pass = new ArrayList<String>();
  /** The case last dealt by the drill, which the next deal avoids where it can. */
  private String lastDealt;
  /** A case left untouched for practice, dealt first when the drill resumes. */
  private String dealNext;
  private final Map<String, String> taught = new HashMap<String, String>();

  /** An F2L pair drill, whose case is a pair and whose rep ends when that pair is in. */
  private final boolean pairs;
  /** The current pair's corner and edge by the faces of their slot, as dealt. */
  private String pairCorner;
  private String pairEdge;
  /** A look-ahead drill, whose rep is two pairs and ends when both are in. */
  private final boolean lookAhead;
  /** Every slot as {corner, edge}, the two dealt ones first. Null for any other drill. */
  private String[][] lookAheadSlots;
  /** When the first of the two pairs came in, and the turn after it, on the cube's clock. */
  private long firstPairInMs = NOT_SHOWN;
  private long pairTwoStartMs = NOT_SHOWN;
  private int[] shownEdges;
  private int[] shownCorners;

  private CubieCube cube = new CubieCube();
  /** The user's own cube, followed only so that a turn it never reported can be recovered. */
  private final CubeTwin twin = new CubeTwin();
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
  /** The case being practised, dealt over and over outside the drill's reps. */
  private String practiceCase;
  /** An algorithm being tried for it, which deals it at that algorithm's angle; null for any. */
  private String practiceAlgorithm;
  /**
   * Turns made after a practice rep ended and before the next one is shown, kept for that one: the
   * same case comes next, so a fast start is already on it. Null outside that gap.
   */
  private List<CarriedTurn> carried;
  /** Whether the rep up follows straight on from a practice rep, owning the turns made between. */
  private boolean followsOn;

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
    this.pairs = spec.getType() == DrillSpec.Type.F2L_PAIR;
    this.lookAhead = spec.getType() == DrillSpec.Type.F2L_LOOKAHEAD;
    if (lookAhead) {
      cases.add(LOOKAHEAD_CASE); // dealt from no list, so any the spec names are not this drill's
    } else {
      List<String> known = pairs ? F2LPairScrambles.cases() : LastLayerScrambles.cases();
      for (String code : spec.getCases()) {
        (known.contains(code) ? cases : unknownCases).add(code);
      }
    }
    if (spec.getWorkingSet() > 0 && weights != null) {
      // Heaviest first, so the working set opens on what is costing most; ties keep the spec's order.
      Collections.sort(cases, new Comparator<String>() {
        @Override
        public int compare(String a, String b) {
          return Long.compare(weightOf(b), weightOf(a));
        }
      });
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
    if (practiceCase != null) {
      deal(practiceCase);
      return true;
    }
    if (isFinished()) {
      currentCase = null;
      return false;
    }
    deal(pick());
    return true;
  }

  /**
   * Practises one case with an algorithm for it, dealt again each time it is solved until
   * {@link #endPractice}. It is dealt at the algorithm's own angle, so the algorithm is run at once
   * with its leading turn of the cube dropped: a last layer case is turned to face it, and an F2L
   * pair is put in the slot that turn would have brought to front right. A case already up and not
   * yet turned is put back rather than spent, since leaving it was not an answer about it.
   *
   * @param algorithm as listed, leading turn of the cube included; null deals the case at any angle
   */
  public void startPractice(String code, String algorithm) {
    if (currentCase != null && moves.isEmpty() && practiceCase == null) {
      dealNext = currentCase;
    }
    practiceCase = code;
    practiceAlgorithm = algorithm;
    carried = null; // another case, or another angle: nothing turned before was meant for it
    deal(code);
  }

  /** The algorithm the practised case is dealt for from its next deal on; null for any angle. */
  public void setPracticeAlgorithm(String algorithm) {
    practiceAlgorithm = algorithm;
  }

  /** Back to the drill, whose next case the caller deals with {@link #nextRep}. */
  public void endPractice() {
    practiceCase = null;
    practiceAlgorithm = null;
    carried = null;
    currentCase = null;
    caseShownAtMs = NOT_SHOWN;
  }

  /** The case being practised, or null while the drill is running. */
  public String getPracticeCase() {
    return practiceCase;
  }

  private void deal(String code) {
    currentCase = code;
    if (lookAhead) {
      dealTwoPairs();
    } else if (pairs) {
      // A random slot, so the pair is not always read in the same place. Practice spins it by the
      // algorithm's leading y, so the moves after it run with no rotation.
      String atItsAngle = practiceCase != null && practiceAlgorithm != null
          ? F2LPairScrambles.forAlgorithm(practiceAlgorithm, random) : null;
      int spins = atItsAngle != null ? LastLayerScrambles.leadingYQuarters(practiceAlgorithm)
          : practiceCase != null ? 0 : random.nextInt(4);
      currentScramble = LayerRotation.toFace(LayerRotation.spun(atItsAngle != null ? atItsAngle
          : F2LPairScrambles.forCase(currentCase, random), spins), layerFace);
      pairCorner = LayerRotation.piece("DFR", spins, layerFace);
      pairEdge = LayerRotation.piece("FR", spins, layerFace);
    } else {
      String atItsAngle = practiceCase != null && practiceAlgorithm != null
          ? LastLayerScrambles.forAlgorithm(
              LastLayerScrambles.withoutLeadingY(practiceAlgorithm)) : null;
      currentScramble = LayerRotation.toFace(atItsAngle != null ? atItsAngle
          : LastLayerScrambles.forCase(currentCase, random), layerFace);
    }
    cube = new CubieCube();
    FaceTurns.apply(cube, currentScramble);
    if (pairs || lookAhead) {
      readShownPieces();
    }
    moves.clear();
    firstMoveMs = 0;
    algStartMs = 0;
    lastMoveMs = 0;
    firstPairInMs = NOT_SHOWN;
    pairTwoStartMs = NOT_SHOWN;
    caseShownAtMs = NOT_SHOWN;
    resetCount = 0;
    revealed = false;
  }

  /**
   * Two basic pairs in two random slots: one case's scramble, then a second case undone on top in
   * another slot. The second undo can carry the first pair's top-layer pieces, so a deal that leaves
   * a piece in the other pair's slot, or a slot already in, is dealt again.
   */
  private void dealTwoPairs() {
    List<String> basic = F2LPairScrambles.basicCases();
    for (int attempt = 1; ; attempt++) {
      int first = random.nextInt(4);
      int apart = 1 + random.nextInt(3);
      String firstCase = basic.get(random.nextInt(basic.size()));
      String secondCase = basic.get(random.nextInt(basic.size()));
      String both = F2LPairScrambles.forCase(firstCase, random) + " "
          + LayerRotation.spun(F2LPairScrambles.undoing(secondCase, random), apart);
      currentScramble = LayerRotation.toFace(LayerRotation.spun(both, first), layerFace);
      List<Integer> order = new ArrayList<Integer>();
      order.add(first);
      order.add((first + apart) % 4);
      for (int quarters = 0; quarters < 4; quarters++) {
        if (!order.contains(quarters)) {
          order.add(quarters);
        }
      }
      lookAheadSlots = new String[4][];
      for (int i = 0; i < 4; i++) {
        lookAheadSlots[i] = new String[] {LayerRotation.piece("DFR", order.get(i), layerFace),
            LayerRotation.piece("FR", order.get(i), layerFace)};
      }
      cube = new CubieCube();
      FaceTurns.apply(cube, currentScramble);
      if (isFairDeal() || attempt >= MAX_LOOKAHEAD_DEALS) {
        return;
      }
    }
  }

  /** Both dealt slots out, neither holding a piece of the other, and the rest of F2L in. */
  private boolean isFairDeal() {
    int[] cp = new int[8];
    int[] co = new int[8];
    int[] ep = new int[12];
    int[] eo = new int[12];
    cube.toPermutation(cp, co, ep, eo);
    for (int i = 0; i < 4; i++) {
      int corner = F2LPairScrambles.cornerIndex(lookAheadSlots[i][0]);
      int edge = F2LPairScrambles.edgeIndex(lookAheadSlots[i][1]);
      boolean in = cp[corner] == corner && co[corner] == 0 && ep[edge] == edge && eo[edge] == 0;
      if (in != (i >= 2)) {
        return false;
      }
    }
    for (int i = 0; i < 2; i++) {
      int otherCorner = F2LPairScrambles.cornerIndex(lookAheadSlots[1 - i][0]);
      int otherEdge = F2LPairScrambles.edgeIndex(lookAheadSlots[1 - i][1]);
      if (cp[otherCorner] == F2LPairScrambles.cornerIndex(lookAheadSlots[i][0])
          || ep[otherEdge] == F2LPairScrambles.edgeIndex(lookAheadSlots[i][1])) {
        return false;
      }
    }
    for (String cross : CROSS_EDGES) {
      int edge = F2LPairScrambles.edgeIndex(LayerRotation.piece(cross, 0, layerFace));
      if (ep[edge] != edge || eo[edge] != 0) {
        return false;
      }
    }
    return true;
  }

  /**
   * How many of the two dealt pairs are in, counting only while the cross and the other two slots
   * are: a pair put in by breaking another slot is not one done.
   */
  private int lookAheadPairsIn(CubieCube state) {
    String facelets = state.toFaceCube();
    char crossFace = LayerRotation.piece("D", 0, layerFace).charAt(0);
    for (int i = 2; i < 4; i++) {
      if (!F2LPairScrambles.isPairIn(facelets, crossFace, lookAheadSlots[i][0],
          lookAheadSlots[i][1])) {
        return 0;
      }
    }
    int in = 0;
    for (int i = 0; i < 2; i++) {
      if (F2LPairScrambles.isPairIn(facelets, crossFace, lookAheadSlots[i][0],
          lookAheadSlots[i][1])) {
        in++;
      }
    }
    return in;
  }

  /** Marks the first pair in as of a turn's time; the first moment counts, whatever comes after. */
  private void notePairsIn(long atMs) {
    if (lookAhead && firstPairInMs == NOT_SHOWN && !moves.isEmpty()
        && lookAheadPairsIn(cube) > 0) {
      firstPairInMs = atMs;
    }
  }

  /**
   * Whether one of a look-ahead rep's two pairs has come in, which is where its pause starts. False
   * for any other drill.
   */
  public boolean isFirstPairIn() {
    return firstPairInMs != NOT_SHOWN;
  }

  /**
   * The algorithm a learning drill is teaching for a case, which a rep must be turned with to count.
   * The screen says which, since the user's own pick in the case popup wins. Null for any.
   */
  public void setTaughtAlgorithm(String code, String algorithm) {
    if (algorithm == null) {
      taught.remove(code);
    } else {
      taught.put(code, algorithm);
    }
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
    showCase(hostMs);
  }

  /**
   * {@link #markCaseShown}, returning the turns made since the last practice rep ended, which
   * the case up has been given: the caller has to give them to whatever else follows the case.
   * Empty unless this rep follows straight on from a practice rep of the same case.
   */
  public Shown showCase(long hostMs) {
    caseShownAtMs = hostMs;
    List<CarriedTurn> kept = carried != null && practiceCase != null && currentCase != null
        ? carried : null;
    carried = null;
    followsOn = kept != null;
    if (!followsOn) {
      // Turns count from here, so what the cube did before this is not something to recover: the
      // next state it sends is what anything it misses from now on is measured against.
      twin.reset();
      return Shown.NONE;
    }
    // The twin is kept: it has followed the cube through the gap, so a turn missed in it is known.
    for (CarriedTurn turn : kept) {
      if (turn.getMove() != null) {
        addTurn(turn.getMove());
      } else {
        cube = cube.multiply(turn.getMissed());
      }
    }
    return new Shown(kept, isCaseDone() ? complete(false) : null);
  }

  /** Forgets the turns kept for the next practice rep, for a case dealt afresh rather than next. */
  public void dropCarriedTurns() {
    carried = null;
  }

  /** Whether turns made since a practice rep ended are being kept for the next one. */
  public boolean hasCarriedTurns() {
    return carried != null && !carried.isEmpty();
  }

  /** Whether this is the gap after a practice rep, whose turns are kept for the next one. */
  public boolean isCarrying() {
    return carried != null;
  }

  /** A turn made between two practice reps: reported, or recovered from a state the cube sent. */
  public static final class CarriedTurn {

    private final CubeMove move;
    private final CubieCube missed;

    CarriedTurn(CubeMove move, CubieCube missed) {
      this.move = move;
      this.missed = missed;
    }

    /** The turn as reported, or null for one recovered from a state. */
    public CubeMove getMove() {
      return move;
    }

    /** The turns recovered from a state, or null for a reported one. */
    public CubieCube getMissed() {
      return missed;
    }
  }

  /** What showing a case gave it from the gap before it, and the rep that finished, if any. */
  public static final class Shown {

    private static final Shown NONE = new Shown(Collections.<CarriedTurn>emptyList(), null);

    private final List<CarriedTurn> turns;
    private final DrillRep rep;

    private Shown(List<CarriedTurn> turns, DrillRep rep) {
      this.turns = turns;
      this.rep = rep;
    }

    public List<CarriedTurn> getTurns() {
      return Collections.unmodifiableList(turns);
    }

    /** The rep those turns finished, or null if it is still going. */
    public DrillRep getRep() {
      return rep;
    }
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

  /** The turns made on the case up so far, for a screen that starts following them part way. */
  public List<CubeMove> getMovesSoFar() {
    return Collections.unmodifiableList(moves);
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
    firstPairInMs = NOT_SHOWN;
    pairTwoStartMs = NOT_SHOWN;
    caseShownAtMs = NOT_SHOWN;
    resetCount++;
  }

  /**
   * Feeds one turn to the virtual cube. Turns made before the case was shown are dropped rather
   * than queued: the cube on screen missed them too, so the two stay in step, and a rep cannot be
   * timed against a case the user could not yet see.
   *
   * <p><b>Except in practice</b>, where the next case is the same one at the same angle and a fast
   * user is already turning it before it is up. Those turns are kept for it (see
   * {@link #showCase}); dropping them left the case a few turns behind the user's hands.
   *
   * @return the finished rep if that turn solved the case, null while it is still going
   */
  public DrillRep onMove(CubeMove move) {
    // Before the drop: the twin follows the cube itself, so a turn this rep has no use for is
    // still not one the cube is later found to have "missed".
    twin.onMove(move);
    if (move.getHostTimestampMs() != null) {
      clockSkewMs = move.getHostTimestampMs() - move.getCubeTimestampMs();
    }
    if (currentCase == null && carried != null) {
      carried.add(new CarriedTurn(move, null));
      return null;
    }
    if (currentCase == null || caseShownAtMs == NOT_SHOWN) {
      return null;
    }
    if (isFromBeforeTheCase(move)) {
      return null; // the previous case's turn, delivered late: like one delivered on time, dropped
    }
    return addTurn(move);
  }

  /** One of the rep's own turns, counted and timed. */
  private DrillRep addTurn(CubeMove move) {
    long at = move.getCubeTimestampMs();
    if (at < caseShownOnCubeClock()) {
      // Only a rep following on: started before it was shown, on a case the user already knew
      caseShownAtMs = at + clockSkewMs;
    }
    if (moves.isEmpty()) {
      firstMoveMs = at;
    }
    if (algStartMs == 0 && move.getFace() != Face.valueOf(layerFace)) {
      algStartMs = at;
    }
    if (firstPairInMs != NOT_SHOWN && pairTwoStartMs == NOT_SHOWN) {
      pairTwoStartMs = at; // the first turn after a pair came in ends the pause
    }
    moves.add(move);
    lastMoveMs = at;
    cube.applyMove(move.getFace(), move.isPrime());
    notePairsIn(at);
    return isCaseDone() ? complete(false) : null;
  }

  /**
   * The state the cube says it is really in, which is the truth wherever the two disagree.
   *
   * <p>Reported turns are the fast path and not a complete one: a QiYi drops a state change during
   * fast slices, and one turn lost that way leaves the drawn cube wrong for the rest of the rep,
   * with the case never reading as done however well it was solved. The turns nobody saw are handed
   * to the case here instead, which needs no notation for them and no idea of which they were.
   *
   * <p>Only while a case is in front of the user: a turn made before it was shown is dropped by
   * {@link #onMove}, and one recovered here has to be dropped the same way or the two cubes part
   * company over it.
   *
   * <p>What the rep cannot have is the turns themselves. A recovered one has no notation and no
   * timestamp, so it counts towards nothing and the rep it ends is timed to the last turn that was
   * really reported. A rep a turn short is a smaller price than one that could not end at all.
   *
   * @return what that cost, which is nothing at all unless something really was missed
   */
  public Correction onState(CubeState state) {
    CubieCube missed = twin.missing(state);
    if (missed != null && currentCase == null && carried != null) {
      carried.add(new CarriedTurn(null, missed)); // missed in the gap, so the next rep's
      return Correction.NONE;
    }
    if (missed == null || currentCase == null || caseShownAtMs == NOT_SHOWN) {
      return Correction.NONE;
    }
    cube = cube.multiply(missed);
    notePairsIn(lastMoveMs); // a missed turn has no time, so the last reported one stands in
    return new Correction(true, isCaseDone() ? complete(false) : null, missed);
  }

  /**
   * What a reported state put right. Both halves can land at once: the drawn cube is behind the
   * one in the user's hands, and the turns it was behind by are the ones that finished the case.
   */
  public static final class Correction {

    private static final Correction NONE = new Correction(false, null, null);

    private final boolean repaired;
    private final DrillRep rep;
    private final CubieCube missed;

    private Correction(boolean repaired, DrillRep rep, CubieCube missed) {
      this.repaired = repaired;
      this.rep = rep;
      this.missed = missed;
    }

    /** The turns the cube missed, as a state to apply on top of the turns it did report. */
    public CubieCube getMissed() {
      return missed;
    }

    /** Whether the case has moved under the drawing, and so has to be drawn again. */
    public boolean isRepaired() {
      return repaired;
    }

    /** The rep the missed turns finished, or null if the case is still going. */
    public DrillRep getRep() {
      return rep;
    }
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
    return isCaseDone(cube);
  }

  private boolean isCaseDone(CubieCube state) {
    if (lookAhead) {
      return lookAheadPairsIn(state) == 2;
    }
    if (pairs) {
      return F2LPairScrambles.isPairIn(state.toFaceCube(),
          LayerRotation.piece("D", 0, layerFace).charAt(0), pairCorner, pairEdge);
    }
    if (currentCase != null && currentCase.startsWith("oll_")) {
      return isLayerOriented(state);
    }
    return state.isSolved();
  }

  /**
   * Whether these outer turns ({@code "R"}, {@code "D'"}, {@code "F2"}), made on the case as it was
   * dealt, finish it, allowing a last turn of the layer. Tells a screen which angles an algorithm can
   * be started from, since a case is dealt at any turn of its layer.
   */
  public boolean wouldFinish(List<String> turns) {
    if (currentCase == null) {
      return false;
    }
    CubieCube state = new CubieCube();
    FaceTurns.apply(state, currentScramble);
    for (String turn : turns) {
      Face face = Face.valueOf(turn.substring(0, 1));
      String modifier = turn.substring(1);
      int quarters = modifier.startsWith("2") ? 2 : 1;
      for (int i = 0; i < quarters; i++) {
        state.applyMove(face, modifier.startsWith("'"));
      }
    }
    Face layer = Face.valueOf(layerFace);
    for (int i = 0; i < 4; i++) {
      if (isCaseDone(state)) {
        return true;
      }
      state.applyMove(layer, false);
    }
    return false;
  }

  /**
   * The pieces a pair's rep is drawn with: the cross, the slots already in, and the pair, wherever
   * its pieces are. The rest is grey, so the last layer says nothing and the slot to fill reads as
   * the one hole; a stuck case shows the slot its piece is trapped in grey around that piece.
   */
  private void readShownPieces() {
    int[] cp = new int[8];
    int[] co = new int[8];
    int[] ep = new int[12];
    int[] eo = new int[12];
    cube.toPermutation(cp, co, ep, eo);
    List<Integer> edges = new ArrayList<Integer>();
    List<Integer> corners = new ArrayList<Integer>();
    for (String cross : CROSS_EDGES) {
      edges.add(F2LPairScrambles.edgeIndex(LayerRotation.piece(cross, 0, layerFace)));
    }
    for (String[] slot : SLOTS) {
      int corner = F2LPairScrambles.cornerIndex(LayerRotation.piece(slot[0], 0, layerFace));
      int edge = F2LPairScrambles.edgeIndex(LayerRotation.piece(slot[1], 0, layerFace));
      if (cp[corner] == corner && co[corner] == 0 && ep[edge] == edge && eo[edge] == 0) {
        corners.add(corner);
        edges.add(edge);
      }
    }
    if (lookAhead) {
      for (int i = 0; i < 2; i++) {
        corners.add(F2LPairScrambles.cornerIndex(lookAheadSlots[i][0]));
        edges.add(F2LPairScrambles.edgeIndex(lookAheadSlots[i][1]));
      }
    } else {
      corners.add(F2LPairScrambles.cornerIndex(pairCorner));
      edges.add(F2LPairScrambles.edgeIndex(pairEdge));
    }
    shownEdges = toArray(edges);
    shownCorners = toArray(corners);
  }

  private static int[] toArray(List<Integer> values) {
    int[] array = new int[values.size()];
    for (int i = 0; i < array.length; i++) {
      array[i] = values.get(i);
    }
    return array;
  }

  /** The edges a pair's rep colours, by the slot each belongs in. Null for any other drill. */
  public int[] getShownEdges() {
    return shownEdges == null ? null : shownEdges.clone();
  }

  /** The corners a pair's rep colours, likewise. */
  public int[] getShownCorners() {
    return shownCorners == null ? null : shownCorners.clone();
  }

  /** Whether the nine stickers of the layer's face all show the same colour. */
  private boolean isLayerOriented(CubieCube state) {
    String facelets = state.toFaceCube();
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
   * Leaves the case in front of the user to look its algorithms up, spending the rep as a looked-up
   * skip. Unlike a skip it comes round again later, as a case looked up mid-rep does. Null when
   * there is none.
   */
  public DrillRep lookUp() {
    String code = currentCase;
    if (code == null || practiceCase != null) {
      return null;
    }
    revealed = true;
    DrillRep rep = complete(true);
    if (!spec.retiresCases()) {
      showAgain(code);
    }
    return rep;
  }

  /**
   * A turn of the layer's own face before the algorithm only squared the case up to be read, so it
   * is looking rather than solving; the one that closes an algorithm falls inside the execution by
   * construction. A rep of nothing but those has no algorithm to have started, and is split at its
   * first turn as before.
   */
  private DrillRep complete(boolean abandoned) {
    // A look-ahead rep reads a turn of the layer as part of a pair, as a solve's F2L does.
    long algStart = algStartMs > 0 && !lookAhead ? algStartMs : firstMoveMs;
    // On the cube's clock, not the host's: the turns are stamped on that one, and the offsets the
    // rep is stored with are measured from this.
    long shownAt = caseShownOnCubeClock();
    long recognition = moves.isEmpty() ? 0 : Math.max(0, algStart - shownAt);
    long execution = moves.isEmpty() ? 0 : Math.max(0, lastMoveMs - algStart);
    boolean practice = practiceCase != null;
    String taughtAlgorithm = taught.get(currentCase);
    boolean offTaught = spec.isLearning() && !practice && !abandoned && taughtAlgorithm != null
        && !CaseAlgorithms.sameTurning(currentCase, DrillRep.notation(moves), taughtAlgorithm);
    Long pairOne = null;
    Long pause = null;
    if (lookAhead && firstPairInMs != NOT_SHOWN) {
      pairOne = Math.max(0, firstPairInMs - firstMoveMs);
      // Both pairs in on one turn leave no pause; given up on before the next turn, it never ended.
      long pauseEnd = pairTwoStartMs != NOT_SHOWN ? pairTwoStartMs
          : abandoned ? NOT_SHOWN : firstPairInMs;
      if (pauseEnd != NOT_SHOWN) {
        pause = Math.max(0, pauseEnd - firstPairInMs);
      }
    }
    DrillRep rep = new DrillRep(currentCase, currentScramble,
        new ArrayList<CubeMove>(moves), shownAt, recognition, execution, moves.size(),
        resetCount, revealed, abandoned, practice, offTaught, pairOne, pause);
    // A practice rep is none of the drill's: it neither spends one nor counts towards retiring.
    if (!practice) {
      reps.add(rep);
    }
    // A retiring drill deals nothing again: a case stays in until it has earned its way out.
    if (!practice && !spec.retiresCases() && !abandoned && (resetCount > 0 || revealed)) {
      // Not for one that was skipped: giving up on a case is an answer about it, and the rep spends
      // it whether or not it had been restarted or looked up first.
      showAgain(currentCase);
    }
    currentCase = null;
    caseShownAtMs = NOT_SHOWN;
    // The same case comes next in practice, so what is turned before it is up is already on it
    carried = practice ? new ArrayList<CarriedTurn>() : null;
    return rep;
  }

  /** What the reps so far have counted towards each case leaving the drill. */
  public CaseTally getTally() {
    return CaseTally.of(spec, reps);
  }

  /** Whether a case has beaten its target often enough to leave the drill. */
  public boolean isRetired(String code) {
    return getTally().isCleared(code);
  }

  /** The cases this app can deal, in the order the spec named them. */
  public List<String> getCases() {
    return Collections.unmodifiableList(cases);
  }

  /**
   * The cases still being dealt, in the order the spec named them, or by weight for a drill with a
   * working set, which deals from only the first of them that have not retired.
   */
  public List<String> getActiveCases() {
    CaseTally tally = getTally();
    int limit = spec.getWorkingSet() > 0 ? spec.getWorkingSet() : Integer.MAX_VALUE;
    List<String> active = new ArrayList<String>();
    for (String code : cases) {
      if (active.size() < limit && !tally.isCleared(code)) {
        active.add(code);
      }
    }
    return active;
  }

  /**
   * Whether a turn was made before the case up was shown, though delivered after: the end of the
   * previous case, or a start made too soon. It belongs to no rep, nor to anything following one.
   * Never for a practice rep following on from the last, which owns every turn since that ended.
   */
  public boolean isFromBeforeTheCase(CubeMove move) {
    if (currentCase == null || caseShownAtMs == NOT_SHOWN) {
      return true;
    }
    if (followsOn) {
      return false;
    }
    long skew = move.getHostTimestampMs() != null
        ? move.getHostTimestampMs() - move.getCubeTimestampMs() : clockSkewMs;
    return move.getCubeTimestampMs() < caseShownAtMs - skew;
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
   * twice. Nothing is done where the pass is spent, since the next one deals every case anyway.
   *
   * <p><b>And nothing where the drill has no rep to spare.</b> The extra showing is a rep like any
   * other and comes out of the same count, so on a drill of one rep per case it was paid for by the
   * case at the end of the pass, which the user had picked and then never saw. A case still owed
   * its first turn outranks a second turn for one that has already had it.
   */
  private void showAgain(String code) {
    if (!pass.isEmpty() && spec.getReps() - reps.size() > pass.size()) {
      pass.add(code); // the pass is drawn from at random, and never repeats the case just dealt
    }
  }

  /**
   * Every case in turn, each pass drawn in a fresh order so the next one cannot be guessed. A
   * weighted drill puts up to {@link #MAX_COPIES} of a heavy case in each pass, so no case goes
   * unseen for long. Where fewer than {@link #MIN_MIX} cases are left, cleared ones are mixed back in
   * now and then, so the last case is not known to be coming.
   */
  private String pick() {
    List<String> active = getActiveCases();
    String next = dealNext;
    dealNext = null;
    if (next == null || !active.contains(next)) {
      next = mixedIn(active);
    }
    if (next == null) {
      pass.retainAll(active); // a case can have cleared since the pass was drawn
      boolean fresh = pass.isEmpty();
      if (fresh) {
        fillPass(active);
      }
      // Across two passes only where enough cases are left that it does not become a pattern
      next = drawFromPass(fresh && active.size() < MIN_MIX ? null : lastDealt);
    }
    lastDealt = next;
    return next;
  }

  /** A cleared case to deal instead, one time in (cases left + 1), or null. */
  private String mixedIn(List<String> active) {
    if (active.size() >= MIN_MIX) {
      return null;
    }
    CaseTally tally = getTally();
    List<String> cleared = new ArrayList<String>();
    for (String code : cases) {
      if (tally.isCleared(code) && !code.equals(lastDealt)) {
        cleared.add(code);
      }
    }
    if (cleared.isEmpty() || random.nextInt(active.size() + 1) != 0) {
      return null;
    }
    return cleared.get(random.nextInt(cleared.size()));
  }

  private void fillPass(List<String> active) {
    long lightest = Long.MAX_VALUE;
    for (String code : active) {
      lightest = Math.min(lightest, weightOf(code));
    }
    boolean weighted = spec.getSelection() == DrillSpec.Selection.WEIGHTED;
    for (String code : active) {
      long copies = weighted ? Math.round((double) weightOf(code) / lightest) : 1;
      for (long i = 0; i < Math.max(1, Math.min(MAX_COPIES, copies)); i++) {
        pass.add(code);
      }
    }
  }

  /** A random case of the pass, other than {@code avoid} where the pass holds another. */
  private String drawFromPass(String avoid) {
    List<Integer> allowed = new ArrayList<Integer>();
    for (int i = 0; i < pass.size(); i++) {
      if (!pass.get(i).equals(avoid)) {
        allowed.add(i);
      }
    }
    if (allowed.isEmpty()) {
      allowed.add(0); // every copy left is the case to avoid
    }
    // No draw for a single choice, so a one-case drill's scrambles keep their random sequence
    int index = allowed.size() == 1 ? allowed.get(0) : allowed.get(random.nextInt(allowed.size()));
    return pass.remove(index);
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
    return !isRunnable() || reps.size() >= spec.getReps() || getActiveCases().isEmpty();
  }
}
