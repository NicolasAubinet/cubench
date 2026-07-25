package com.cube.nanotimer.cube;

import com.cube.nanotimer.smartcube.model.CubeOrientation;
import com.cube.nanotimer.smartcube.model.CubeRotation;
import com.cube.nanotimer.smartcube.model.StillnessTracker.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Turns the cube's still-grip windows into the whole-cube rotations the solver made.
 *
 * <p>Grips are read from stillness windows only: a still reading is wobble-free, so a single
 * window sitting a clean rotation away from the current grip <em>is</em> a rotation — the gyro
 * alone detects it, with no confirmation by later moves. Moves only decide what gets written
 * down: a rotation is recorded at the first move made in the new grip, so a tilt that comes and
 * goes with no move in it relabels nothing and vanishes, which is exactly a peek.
 *
 * <p>The standard position is the grip held <em>longest</em> across the scramble's still windows.
 * Scrambling is when the solver provably holds the cube the way the scramble reads, but not every
 * instant of it — a {@code B} executed at an angle can own a still window — and dominance by held
 * time lets the brief tilts lose. The opening rotation runs from there to the last still grip held
 * before the first solve move: the settle at the end of inspection, measured clean instead of
 * sampled mid-turn at the first move.
 */
public final class RotationTracker {

  /**
   * Two readings within this are the same held grip; beyond it the cube has genuinely moved.
   * Gates both the stale-window veto and the moves-in-transit count, and is as generous as the
   * rotation match tolerance: move-time wobble passes, a moved-on grip does not.
   */
  private static final double GRIP_AGREEMENT_DEGREES = 35;

  /**
   * How near a lattice orientation a <em>yaw</em> regrip must sit to be taken as a real one. Hands
   * are precise about the vertical: measured across captures, once gravity has taken each grip's
   * tilt out, a real yaw lands within a few degrees, while a twist of the wrist and back sat 25°
   * off. Erring tight is safe — a rejected reading leaves the reference frozen, so a real rotation
   * is caught at the next clean window, while accepting a peek poisons the frame for good.
   */
  private static final double GRIP_COMMIT_DEGREES = 20;

  private final List<Rotation> rotations = new ArrayList<Rotation>();
  private final List<Window> scrambleWindows = new ArrayList<Window>();
  private Long scrambleCompleteWallMs;
  private CubeOrientation reference;
  private long consumedWindowStartMs;
  private int movesInTransit; // moves made since the cube last read near the reference grip
  private Window pendingFlip; // a grip on a new face, waiting for a second window to confirm it
  private long pendingFlipMoveMs;

  /** Feed the latest still window seen while following the scramble. */
  public void anchor(Window rawWindow) {
    if (rawWindow == null || scrambleCompleteWallMs != null) {
      return;
    }
    Window window = upright(rawWindow);
    int last = scrambleWindows.size() - 1;
    if (last >= 0 && scrambleWindows.get(last).getStartMs() == window.getStartMs()) {
      scrambleWindows.set(last, window); // the same window, extended
    } else {
      scrambleWindows.add(window);
    }
  }

  /** Forget the scramble grips seen so far: the cube was set down and picked up afresh. */
  public void restartAnchor() {
    scrambleWindows.clear();
  }

  /** Call when the scramble is fully followed: still windows after this are the solver's grips. */
  public void scrambleComplete(long wallMs) {
    if (scrambleCompleteWallMs == null) {
      scrambleCompleteWallMs = wallMs;
    }
  }

  /**
   * Sample the latest still window at a solve move. The first window begun after the scramble
   * completed resolves the opening; each later window a clean rotation away from the current grip
   * records that rotation, timestamped at this move — the first move made in the new grip.
   *
   * <p>{@code now} is the instantaneous reading at the move: a later window only counts while the
   * cube is still near it. A slow rotation can ease through a briefly-steady angle and mint a
   * window mid-way — committing that once relabelled a whole test solve — but by the move the cube
   * has moved well past it, whereas a real grip stays within hand wobble of its window.
   *
   * <p>The opening is exempt: its window is the last grip held before the solve, and a solver
   * already turning at the first move has not stopped having started there.
   */
  public void onMove(Window rawWindow, CubeOrientation rawNow, long moveTimestampMs) {
    if (rawWindow == null || scrambleCompleteWallMs == null) {
      return;
    }
    Window window = upright(rawWindow);
    CubeOrientation now = rawNow == null ? null : CubeRotation.upright(rawNow);
    // The committing move itself always reads far from the old grip — it is the first move made
    // in the new one — so the half-turn gate judges by the moves before it.
    int movesBeforeThis = movesInTransit;
    if (reference != null && now != null) {
      if (reference.angleToDegrees(now) <= GRIP_AGREEMENT_DEGREES) {
        movesInTransit = 0; // the cube still reads near the claimed grip: any change starts here
      } else {
        movesInTransit++;
      }
    }
    if (reference == null) {
      if (window.getStartMs() <= scrambleCompleteWallMs) {
        return; // the latest still grip is still the scramble's: no inspection settle seen yet
      }
      CubeOrientation scrambleGrip = dominantScrambleGrip();
      if (scrambleGrip == null) {
        return; // no scramble grip was ever seen: a guessed opening would be worse than none
      }
      CubeRotation opening = opening(scrambleGrip, window.getOrientation());
      if (!opening.isIdentity()) {
        rotations.add(new Rotation(opening.getNotation(), moveTimestampMs));
      }
      commitGrip(window);
      return;
    }
    if (now != null && window.getOrientation().angleToDegrees(now) > GRIP_AGREEMENT_DEGREES) {
      return; // the cube has moved on since that window: a transient, not the grip
    }
    if (window.getStartMs() <= consumedWindowStartMs) {
      return; // no new still grip since the last commit
    }
    // Turning the cube onto another face takes a whole regrip and carries far more yaw slop than a
    // regrip about the vertical does — 26° on a real solve, against the 0-3° a yaw lands within —
    // so the tolerance would refuse every flip. What rejects a mere look there is the corroboration
    // below, not an angle; gravity has already made the face itself exact.
    boolean flip = isFlip(window);
    CubeOrientation delta = reference.deltaTo(window.getOrientation());
    CubeRotation rotation = flip
        ? CubeRotation.closest(delta)
        : CubeRotation.nearest(delta, GRIP_COMMIT_DEGREES);
    if (rotation == null || rotation.isIdentity()) {
      // An ambiguous tilt, or noise. The reference stays frozen between commits so that a grip
      // creeping slowly back from a rotation still adds up to the rotation back.
      pendingFlip = null; // and a flip the cube came back from was a look at the bottom, not a turn
      return;
    }
    long recordAtMs = moveTimestampMs;
    if (flip) {
      // Turning the cube right over mid-solve is rare and deliberate; tilting it far enough to see
      // the bottom, moves and all, happens constantly and reads identically in a single window.
      // Gravity tells them apart as soon as a second window says the new face is still on top.
      if (!confirmsPendingFlip(window)) {
        if (pendingFlip == null || pendingFlip.getStartMs() != window.getStartMs()) {
          pendingFlip = window;
          pendingFlipMoveMs = moveTimestampMs;
        }
        return;
      }
      recordAtMs = pendingFlipMoveMs; // it belongs to the move it was first held at
    }
    if (rotation.isHalfTurn() && movesBeforeThis > 0) {
      // A half turn surfacing while face turns were being made is the gyro's yaw snapping —
      // seen twice on one real solve, both 180°, neither a regrip: nobody flips the cube over
      // mid-burst. Re-zero on the new reading without recording anything; the letters come from
      // the cube's own move stream, so the solver's frame is unchanged — only the gyro's belief
      // moved. A real y2 is made in a pause, reaching here with no moves in transit.
      // Gravity cannot sharpen this: the gyro rides the core, so a slice turns its up face while
      // the cube sits still — judging by up face reported an M2's rock as a z2 mid-PLL.
      commitGrip(window);
      movesInTransit = 0;
      return;
    }
    rotations.add(new Rotation(rotation.getNotation(), recordAtMs));
    commitGrip(window);
    movesInTransit = 0;
  }

  /**
   * The grip the solve starts in. Gravity gives the up face outright, leaving the four grips that
   * differ in yaw, and the scramble grip picks between those: only the yaw is still assumed.
   */
  private static CubeRotation opening(CubeOrientation scrambleGrip, CubeOrientation solveGrip) {
    // No tolerance on the yaw: there is no "no rotation yet" here — the start grip is the opening.
    CubeRotation fromScramble = CubeRotation.closest(scrambleGrip.deltaTo(solveGrip));
    CubeRotation measured = fromScramble.nearestOf(
        CubeRotation.withFaceUp(CubeRotation.upFace(solveGrip)));
    return measured == null ? fromScramble : measured;
  }

  /** The same window with the grip's tilt taken out: gravity removes it without a reference. */
  private static Window upright(Window window) {
    return new Window(CubeRotation.upright(window.getOrientation()),
        window.getStartMs(), window.getStartMs() + window.getDurationMs());
  }

  /** Whether this grip puts a different face on top than the one the frame is measured from. */
  private boolean isFlip(Window window) {
    return CubeRotation.upFace(reference) != CubeRotation.upFace(window.getOrientation());
  }

  /** True when a second, later window agrees the pending flip is where the cube has stayed. */
  private boolean confirmsPendingFlip(Window window) {
    return pendingFlip != null && pendingFlip.getStartMs() != window.getStartMs()
        && isSameGrip(pendingFlip.getOrientation(), window.getOrientation());
  }

  private void commitGrip(Window window) {
    reference = window.getOrientation();
    consumedWindowStartMs = window.getStartMs();
    pendingFlip = null;
  }

  /** The grip held longest while scrambling, at its freshest reading; brief tilts lose to it. */
  private CubeOrientation dominantScrambleGrip() {
    List<CubeOrientation> reps = new ArrayList<CubeOrientation>();
    List<CubeOrientation> freshest = new ArrayList<CubeOrientation>();
    List<Long> heldMs = new ArrayList<Long>();
    for (Window window : scrambleWindows) {
      int cluster = 0;
      while (cluster < reps.size() && !isSameGrip(reps.get(cluster), window.getOrientation())) {
        cluster++;
      }
      if (cluster == reps.size()) {
        reps.add(window.getOrientation());
        freshest.add(window.getOrientation());
        heldMs.add(0L);
      }
      freshest.set(cluster, window.getOrientation());
      heldMs.set(cluster, heldMs.get(cluster) + window.getDurationMs());
    }
    int best = -1;
    for (int i = 0; i < reps.size(); i++) {
      if (best < 0 || heldMs.get(i) > heldMs.get(best)) {
        best = i;
      }
    }
    return best < 0 ? null : freshest.get(best);
  }

  private static boolean isSameGrip(CubeOrientation a, CubeOrientation b) {
    CubeRotation between = CubeRotation.nearest(a.deltaTo(b));
    return between != null && between.isIdentity();
  }

  public List<Rotation> getRotations() {
    return Collections.unmodifiableList(rotations);
  }

  /** True once a scramble grip has been seen, so an opening can be resolved. */
  public boolean isAnchored() {
    return !scrambleWindows.isEmpty();
  }

  public void reset() {
    rotations.clear();
    scrambleWindows.clear();
    scrambleCompleteWallMs = null;
    reference = null;
    consumedWindowStartMs = 0;
    movesInTransit = 0;
    pendingFlip = null;
  }

  /** A whole-cube rotation, at the moment of the move it preceded. */
  public static final class Rotation {

    private final String notation;
    private final long timestampMs;

    Rotation(String notation, long timestampMs) {
      this.notation = notation;
      this.timestampMs = timestampMs;
    }

    /** One or two tokens, e.g. {@code "y"} or {@code "y x'"}. */
    public String getNotation() {
      return notation;
    }

    public long getTimestampMs() {
      return timestampMs;
    }
  }
}
