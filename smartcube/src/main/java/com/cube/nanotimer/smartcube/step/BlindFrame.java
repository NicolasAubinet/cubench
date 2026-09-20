package com.cube.nanotimer.smartcube.step;

import java.util.ArrayList;
import java.util.List;

/**
 * The frame a blind solve was held in, read off the pieces it shot from rather than off the gyro.
 *
 * <p><b>Why not the gyro.</b> The grip is one snapped reading taken over the last two seconds before
 * the first move, and a blind memorisation does not hold still: measured on the solve of 2026-09-09,
 * the cube's up face flipped between {@code U}, {@code F} and {@code B} six times in the last ten
 * seconds, tilting to 40-47° each way, so {@code upFace} was snapping across the 45° line
 * throughout. Moving the end of that window by 250 ms changed the answer from {@code y} to
 * {@code y x'}, and the window is closed on the host clock while the first move is dated on the
 * cube's, which are fitted to each other only within a couple of seconds. The grip was therefore a
 * coin flip, and a wrong one is invisible: every name comes out a rotation from where the solver
 * held the cube, and a rotated frame still yields a perfectly self-consistent buffer, so nothing
 * downstream disowns it. This was the fourth recurrence of the same symptom.
 *
 * <p><b>What answers instead.</b> A blind solver shoots from the same piece over and over, and which
 * piece that is, is a fact about them rather than about a solve. The detector reads the slot each
 * algorithm was shot from off cube states alone, and one buffer slot per type pins the frame: a
 * corner slot leaves three of the 24 rotations standing and an edge slot two, and no rotation but
 * one satisfies both. Measured over all 26 recorded blind solves, exactly one frame of 24 puts the
 * shot cycles of both types at the owner's {@code UF}/{@code UFR}, and it is the grip they were
 * recorded with wherever they carry one.
 *
 * <p>It reads no gyro, so yaw drift, an unpinned session anchor and a cube held off square cannot
 * reach it. The one way left to be wrong is a buffer declared that the solver does not use.
 *
 * <p><b>Where one buffer is all the solve read.</b> A solve that stops before its second piece type
 * — an edges-only session, or a 3BLD abandoned part way — leaves the two frames an edge slot admits
 * (three for a corner slot), and they differ by a turn about the buffer's own axis. Nothing in any
 * cube state tells them apart: the whole reading rotates with the frame and comes out just as
 * consistent either way, so a wrong one is invisible and every name comes out mirrored. That is the
 * solve of 2026-09-19, reported by its solver.
 *
 * <p><b>What answers is the solver's own habit.</b> A grip is a fact about them in the same way a
 * buffer is: the scramble is followed in one orientation and the cube is picked up out of it the
 * same way each time. Measured, all 19 recorded solves that read both piece types settle on
 * {@code y}, and the solver says so himself. So the frame a completed solve settled on is kept, and
 * a solve that can only settle half of one is read through it. This is not a second rule for
 * part-solves: it is the answer their own whole solves already gave, which is why an edges-only
 * solve comes out reading like any other.
 *
 * <p><b>Only where no solve has taught it yet</b> does the turning get a say: a cube is held with
 * the faces the fingers reach up and down, and front and back are awkward. Counted in reported
 * quarter turns over each recorded solve's edges alone — the shape this is for — the front-back axis
 * is the quieter one in 18 of 20, by 1.22x at the tightest. Better than the coin flip it replaces
 * and no more than that, which is why the habit is asked first and this second.
 */
final class BlindFrame {

  /** The faces a frame names front and back, as {@link FaceletRotations} indexes them. */
  private static final int FRONT = 2, BACK = 5;

  private BlindFrame() {
  }

  /**
   * The holding frame that names the solve's buffers as the solver's own, or {@code frame} where the
   * solve does not say.
   *
   * @param shotFrom the slot each piece type was shot from, in the frame the cube reports, or
   *     {@link BlindTargets#NO_BUFFER} for a type nothing settled
   * @param declared the slot the solver shoots that type from, or {@link BlindTargets#NO_BUFFER}
   * @param habit the frame this solver's completed solves settled on, or
   *     {@link BlindTargets#UNKNOWN_FRAME} where none has
   * @param frame the gyro's answer, kept where nothing else has an opinion
   * @param turns how often each reported face was turned, URFDLB
   */
  static int of(int[] shotFrom, int[] declared, int habit, int frame, int[] turns) {
    List<Integer> fits = fits(shotFrom, declared);
    if (fits.isEmpty()) {
      return frame;
    }
    if (fits.size() == 1) {
      return fits.get(0);
    }
    if (fits.contains(habit)) {
      return habit;
    }
    int quietest = quietestFront(fits, turns);
    if (quietest != BlindTargets.UNKNOWN_FRAME) {
      return quietest;
    }
    if (fits.contains(frame)) {
      return frame;
    }
    return frame == BlindTargets.UNKNOWN_FRAME ? frame : closestTo(fits, frame);
  }

  /**
   * The fit that puts the quietest axis at front and back, or {@link BlindTargets#UNKNOWN_FRAME}
   * where two of them were turned exactly alike and the turning cannot choose.
   */
  private static int quietestFront(List<Integer> fits, int[] turns) {
    int best = BlindTargets.UNKNOWN_FRAME;
    int quietest = Integer.MAX_VALUE;
    boolean alone = false;
    for (int fit : fits) {
      int turned =
          turns[FaceletRotations.face(fit, FRONT)] + turns[FaceletRotations.face(fit, BACK)];
      if (turned < quietest) {
        quietest = turned;
        best = fit;
        alone = true;
      } else if (turned == quietest) {
        alone = false;
      }
    }
    return alone ? best : BlindTargets.UNKNOWN_FRAME;
  }

  /**
   * Whether the pieces alone leave one way to hold the cube, which is what makes a solve's own grip
   * worth learning from: anything less is a fallback, and a habit taught by one would only confirm
   * itself.
   */
  static boolean settles(int[] shotFrom, int[] declared) {
    return fits(shotFrom, declared).size() == 1;
  }

  /** Every way of holding the cube that puts each settled buffer at the declared one. */
  private static List<Integer> fits(int[] shotFrom, int[] declared) {
    List<Integer> fits = new ArrayList<Integer>();
    for (int frame = 0; frame < FaceletRotations.COUNT; frame++) {
      boolean asked = false;
      boolean holds = true;
      for (int type = 0; type < shotFrom.length && holds; type++) {
        if (shotFrom[type] == BlindTargets.NO_BUFFER || declared[type] == BlindTargets.NO_BUFFER) {
          continue;
        }
        asked = true;
        holds = heldSlotOf(frame, shotFrom[type]) == declared[type];
      }
      if (asked && holds) {
        fits.add(frame);
      }
    }
    return fits;
  }

  /** Where the slot the cube reports sits in a frame, as {@code BlindTargets} spells it. */
  private static int heldSlotOf(int frame, int slot) {
    return Cubies.slotOf(
        FaceletRotations.apply(FaceletRotations.inverse(frame), Cubies.PIECES[slot][0]));
  }

  /**
   * The fit nearest the frame given, counted in faces left where that frame puts them. Reached only
   * by a solve that read one piece type and no more, where the buffer says which way up the cube is
   * without saying which way round: the gyro is wrong about one of those and is still the only thing
   * with an opinion on the other.
   */
  private static int closestTo(List<Integer> fits, int frame) {
    int best = fits.get(0);
    int bestAgreed = -1;
    for (int fit : fits) {
      int agreed = 0;
      for (int face = 0; face < 6; face++) {
        if (FaceletRotations.face(fit, face) == FaceletRotations.face(frame, face)) {
          agreed++;
        }
      }
      if (agreed > bestAgreed) {
        bestAgreed = agreed;
        best = fit;
      }
    }
    return best;
  }
}
