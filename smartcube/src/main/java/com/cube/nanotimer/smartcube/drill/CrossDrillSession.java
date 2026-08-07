package com.cube.nanotimer.smartcube.drill;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.step.FaceTurns;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A cross drill being run: a whole scramble stands on the virtual cube, the smart cube is the input,
 * and the rep is over the moment the cross is there.
 *
 * <p><b>Not {@link CubieCube#isSolved}.</b> That is what ends a last layer rep, and it is the wrong
 * question here: only four edges are being asked for and the other sixteen pieces are meant to be
 * left where the scramble put them. So the check is those four edges home and the right way up, and
 * everything the case drills carries over unchanged, the frame included: the cube reports its turns
 * against its own centres, and home is measured against those same centres.
 *
 * <p><b>A wrong cross cannot end itself, so it is announced.</b> There is no state that says "this
 * user believes they are finished", and waiting for one that never comes would leave a rep running
 * for good. {@link #declareFinished} is that announcement, and it produces a rep either way: a cross
 * that was not there is a result, not a rep that did not happen.
 *
 * <p>The scramble comes from outside. Generating one is the app's business and there is a good
 * generator over there already; what belongs here is only what a scramble is turned into.
 */
public final class CrossDrillSession {

  private static final long NOT_SHOWN = -1;

  /**
   * The four edges each face's cross is made of, as {@link CubieCube}'s own slot numbers (UR UF UL
   * UB DR DF DL DB FR FL BL BR). A face's cross edges are the four that face's quarter turn moves.
   */
  private static final int[][] CROSS_EDGES = {
    {0, 1, 2, 3},   // U: UR UF UL UB
    {4, 5, 6, 7},   // D: DR DF DL DB
    {2, 6, 9, 10},  // L: UL DL FL BL
    {0, 4, 8, 11},  // R: UR DR FR BR
    {1, 5, 8, 9},   // F: UF DF FR FL
    {3, 7, 10, 11}, // B: UB DB BL BR
  };

  private static final String FACE_ORDER = "UDLRFB";

  private final DrillSpec spec;
  private final int[] crossEdges;
  private final List<CrossDrillRep> reps = new ArrayList<CrossDrillRep>();

  private CubieCube cube = new CubieCube();
  private String currentScramble;
  private boolean running;
  private int moveCount;
  private long firstMoveMs;
  private long lastMoveMs;
  private long shownAtMs = NOT_SHOWN;
  private int optimalLength;
  private boolean planningExpired;

  public CrossDrillSession(DrillSpec spec) {
    if (spec.getType() != DrillSpec.Type.CROSS) {
      throw new IllegalArgumentException("Not a cross drill: " + spec.getType());
    }
    this.spec = spec;
    this.crossEdges = CROSS_EDGES[FACE_ORDER.indexOf(spec.getCrossFace())];
  }

  public DrillSpec getSpec() {
    return spec;
  }

  /** The face the cross goes on, as its letter. */
  public String getFace() {
    return spec.getCrossFace();
  }

  /**
   * Puts a scramble on the virtual cube, or returns false when the drill is over. Like a case drill,
   * the rep does not start here but when the scramble reaches the user's eyes
   * ({@link #markCaseShown}).
   *
   * <p>Calling this again before a rep has finished simply replaces the scramble, which is what a
   * caller handed one whose cross was already built should do.
   */
  public boolean nextRep(String scramble) {
    if (isFinished()) {
      running = false;
      return false;
    }
    cube = new CubieCube();
    FaceTurns.apply(cube, scramble);
    currentScramble = scramble;
    running = true;
    moveCount = 0;
    firstMoveMs = 0;
    lastMoveMs = 0;
    shownAtMs = NOT_SHOWN;
    optimalLength = 0;
    planningExpired = false;
    return true;
  }

  /** The scramble is now in front of the user, which is where the planning time runs from. */
  public void markCaseShown(long hostMs) {
    shownAtMs = hostMs;
  }

  /** The planning limit ran out before the user turned anything. */
  public void markPlanningExpired() {
    planningExpired = true;
  }

  /**
   * The fewest moves this cross could have taken, once the search has found it. Handed in rather
   * than worked out here: the solver lives with the scrambler, and it runs while the user looks.
   *
   * <p>It can land after the rep it belongs to, since a cross solved in two moves takes less time
   * than a table being built, so a rep already finished takes it too. The caller is what says which
   * scramble an answer is for; nothing arrives here that belongs to another one.
   */
  public void setOptimalLength(int optimalLength) {
    this.optimalLength = optimalLength;
    if (!running && !reps.isEmpty()) {
      reps.get(reps.size() - 1).setOptimalLength(optimalLength);
    }
  }

  /**
   * Feeds one turn to the virtual cube. Turns made before the scramble was shown are dropped rather
   * than queued, so the two cubes stay in step.
   *
   * @return the finished rep if that turn built the cross, null while it is still going
   */
  public CrossDrillRep onMove(CubeMove move) {
    if (!running || shownAtMs == NOT_SHOWN) {
      return null;
    }
    long at = move.getCubeTimestampMs();
    if (moveCount == 0) {
      firstMoveMs = at;
    }
    moveCount++;
    lastMoveMs = at;
    cube.applyMove(move.getFace(), move.isPrime());
    return isCrossBuilt() ? complete() : null;
  }

  /**
   * The user says they are finished. Ends the rep on whatever is really there, which for a cross
   * that was built is the same rep {@link #onMove} would have ended and for one that was not is the
   * only way the rep could ever end.
   *
   * @return the rep, or null when there is none running
   */
  public CrossDrillRep declareFinished() {
    return running ? complete() : null;
  }

  /**
   * Turns the cube without timing anything, for the rep that is over: the user is trying the short
   * way they were shown, or seeing what their own extra move did, and the picture has to follow
   * their hands or there is nothing to see. Ignored while a rep is running, which is what
   * {@link #onMove} is for.
   */
  public void explore(CubeMove move) {
    if (!running) {
      cube.applyMove(move.getFace(), move.isPrime());
    }
  }

  /**
   * Puts the cube back to the scramble the rep started from, so the solution can be tried on it.
   *
   * <p>Only between reps, and that is the whole point of the restriction: the cube goes grey on the
   * first turn of a rep so that the cross is built from what was read, and a rewind mid-rep would
   * hand back the look the drill just took away.
   */
  public void resetToStart() {
    if (running) {
      return;
    }
    cube = new CubieCube();
    FaceTurns.apply(cube, currentScramble);
  }

  /** Whether the four edges of the drilled face are home and the right way up. */
  public boolean isCrossBuilt() {
    int[] cp = new int[8];
    int[] co = new int[8];
    int[] ep = new int[12];
    int[] eo = new int[12];
    cube.toPermutation(cp, co, ep, eo);
    for (int slot : crossEdges) {
      if (ep[slot] != slot || eo[slot] != 0) {
        return false;
      }
    }
    return true;
  }

  /**
   * The four edges this cross is made of, by the slot each one belongs in, for a screen that shows
   * those four and greys the rest.
   *
   * <p><b>The pieces, not where they are.</b> Which is a fact about the drawn cube rather than about
   * this class: the player owns one object per piece and moves it to wherever the piece sits, so a
   * mask names pieces and travels with them. Naming the occupied slots instead lights four
   * arbitrary edges, and only looks right on a solved cube, where the two readings agree.
   */
  public int[] getCrossEdges() {
    return crossEdges.clone();
  }

  private CrossDrillRep complete() {
    boolean built = isCrossBuilt();
    long planning = moveCount > 0 ? Math.max(0, firstMoveMs - shownAtMs) : 0;
    long execution = moveCount > 0 ? Math.max(0, lastMoveMs - firstMoveMs) : 0;
    CrossDrillRep rep = new CrossDrillRep(getFace(), currentScramble, planning, execution, moveCount,
        optimalLength, built, planningExpired);
    reps.add(rep);
    running = false;
    shownAtMs = NOT_SHOWN;
    return rep;
  }

  /** The virtual cube as it stands, for drawing. */
  public String getFacelets() {
    return cube.toFaceCube();
  }

  /** What the virtual cube was set up with. */
  public String getCurrentScramble() {
    return currentScramble;
  }

  /** Quarter turns made in the rep so far. */
  public int getMoveCount() {
    return moveCount;
  }

  /** Whether a scramble is in front of the user, waiting to be turned or already being turned. */
  public boolean isRunning() {
    return running;
  }

  public List<CrossDrillRep> getReps() {
    return Collections.unmodifiableList(reps);
  }

  public boolean isFinished() {
    return reps.size() >= spec.getReps();
  }
}
