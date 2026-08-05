package com.cube.nanotimer.smartcube.drill;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.Face;
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

  private final DrillSpec spec;
  private final Random random;
  private final Map<String, Long> weights;
  private final List<String> cases = new ArrayList<String>();
  private final List<String> unknownCases = new ArrayList<String>();
  private final List<DrillRep> reps = new ArrayList<DrillRep>();
  private final List<String> pass = new ArrayList<String>();

  private CubieCube cube = new CubieCube();
  private String currentCase;
  private String currentScramble;
  private int moveCount;
  private long firstMoveMs;
  private long lastMoveMs;
  private long caseShownAtMs = NOT_SHOWN;
  private int resetCount;

  public DrillSession(DrillSpec spec, Random random) {
    this(spec, random, null);
  }

  /**
   * @param weights what each case is costing, for a spec drawing by weight. A case with no weight
   *     still comes up, since nothing known about it is not a reason to never practise it.
   */
  public DrillSession(DrillSpec spec, Random random, Map<String, Long> weights) {
    this.spec = spec;
    this.random = random;
    this.weights = weights;
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
    currentScramble = LastLayerScrambles.forCase(currentCase, random);
    cube = new CubieCube();
    applyScramble(cube, currentScramble);
    moveCount = 0;
    firstMoveMs = 0;
    lastMoveMs = 0;
    caseShownAtMs = NOT_SHOWN;
    resetCount = 0;
    return true;
  }

  /**
   * The case is now in front of the user, as of a host timestamp on the same clock the moves carry.
   * This is where recognition runs from, and until it is called the rep has not begun and turns are
   * not counted against it.
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
   * Puts the case back as it was and starts the rep over, for one botched by a slip rather than by
   * not knowing it. Costs no rep: the case comes round again here and now, where skipping it would
   * spend it and move on.
   *
   * <p>The same scramble, not another of the same case: a redo is of the position that went wrong,
   * and a fresh alignment would be a different one to look at. The rep it eventually finishes
   * carries how many times it was restarted, so a time reached on the third go is not read as a
   * clean one.
   *
   * <p>Like a case just dealt, the rep is not running until {@link #markCaseShown} says the user can
   * see it again.
   */
  public void resetRep() {
    if (currentCase == null) {
      return;
    }
    cube = new CubieCube();
    applyScramble(cube, currentScramble);
    moveCount = 0;
    firstMoveMs = 0;
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
    long at = move.getCubeTimestampMs();
    if (moveCount == 0) {
      firstMoveMs = at;
    }
    moveCount++;
    lastMoveMs = at;
    cube.applyMove(move.getFace(), move.isPrime());
    return cube.isSolved() ? complete(false) : null;
  }

  /** Gives up on the case in front of the user, spending the rep. Null when there is none. */
  public DrillRep abandon() {
    return currentCase == null ? null : complete(true);
  }

  private DrillRep complete(boolean abandoned) {
    long recognition = moveCount > 0 ? Math.max(0, firstMoveMs - caseShownAtMs) : 0;
    long execution = moveCount > 0 ? Math.max(0, lastMoveMs - firstMoveMs) : 0;
    DrillRep rep = new DrillRep(currentCase, currentScramble, recognition, execution, moveCount,
        resetCount, abandoned);
    reps.add(rep);
    currentCase = null;
    caseShownAtMs = NOT_SHOWN;
    return rep;
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

  /** Face turns only, which is all {@link LastLayerScrambles} writes. */
  private static void applyScramble(CubieCube cube, String scramble) {
    for (String token : scramble.trim().split("\\s+")) {
      if (token.isEmpty()) {
        continue;
      }
      Face face = Face.valueOf(token.substring(0, 1));
      boolean prime = token.endsWith("'");
      int quarters = token.indexOf('2') >= 0 ? 2 : 1;
      for (int quarter = 0; quarter < quarters; quarter++) {
        cube.applyMove(face, prime);
      }
    }
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
