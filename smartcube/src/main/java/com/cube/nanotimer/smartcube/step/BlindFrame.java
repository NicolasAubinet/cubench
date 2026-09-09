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
 * one satisfies both. Measured over all 25 recorded blind solves, exactly one frame of 24 puts their
 * shot cycles at the owner's {@code UF}/{@code UFR}, and it is the grip they were recorded with
 * wherever they carry one.
 *
 * <p>It reads no gyro, so yaw drift, an unpinned session anchor and a cube held off square cannot
 * reach it. The one way left to be wrong is a buffer declared that the solver does not use, which is
 * why {@link #of} moves off the frame it is given only when the answer is <em>unique</em>.
 */
final class BlindFrame {

  private BlindFrame() {
  }

  /**
   * The holding frame that names the solve's buffers as the solver's own, or {@code frame} where the
   * solve does not say.
   *
   * @param shotFrom the slot each piece type was shot from, in the frame the cube reports, or
   *     {@link BlindTargets#NO_BUFFER} for a type nothing settled
   * @param declared the slot the solver shoots that type from, or {@link BlindTargets#NO_BUFFER}
   * @param frame the frame to keep where the buffers leave more than one way to hold the cube: the
   *     gyro's answer, which still decides the yaw of a solve that only read one piece type
   */
  static int of(int[] shotFrom, int[] declared, int frame) {
    List<Integer> fits = fits(shotFrom, declared);
    if (fits.isEmpty() || fits.contains(frame)) {
      return frame;
    }
    if (fits.size() == 1) {
      return fits.get(0);
    }
    return frame == BlindTargets.UNKNOWN_FRAME ? frame : closestTo(fits, frame);
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
