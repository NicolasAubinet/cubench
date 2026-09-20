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
 * one satisfies both. Measured over the recorded blind solves, 26 of the 27 settle their frame from
 * their pieces alone: exactly one frame of 24 puts the shot cycles of both types at the owner's
 * {@code UF}/{@code UFR}, it is the grip they were recorded with wherever they carry one, and they
 * are named the same whatever frame is handed in. The 27th read only its edges, and is the whole
 * subject of the note below.
 *
 * <p>It reads no gyro, so yaw drift, an unpinned session anchor and a cube held off square cannot
 * reach it. The one way left to be wrong is a buffer declared that the solver does not use, which is
 * why {@link #of} moves off the frame it is given only as far as the pieces force it: to their one
 * fit where they pin one, and no further than the nearest of the few they leave where the frame
 * given is not among them.
 *
 * <p><b>Where one buffer is all the solve read.</b> A solve that stops before its second piece type,
 * an edges-only session or a 3BLD abandoned part way, leaves the two frames an edge slot admits
 * (three for a corner slot), and they differ by a turn about the buffer's own axis. Nothing in any
 * cube state tells them apart: the whole reading rotates with the frame and comes out just as
 * consistent either way, so a wrong one is invisible and every name comes out mirrored. That is the
 * solve of 2026-09-19, reported by its solver.
 *
 * <p><b>What answers there is the orientation the solver declares.</b> A cube writes its state
 * against its own centres, which no face turn moves, and this app fixes that reported frame as white
 * up and green front. So "I hold white up, red front" is not a hint but an equation: it names one of
 * the 24 and no other, with nothing measured, nothing inferred and nothing to drift. It is asked for
 * once and kept in the settings, and it is what {@code frame} carries for a solve being read as it
 * is turned. A solve whose pieces settle their own frame never consults it, so a declaration left at
 * a hold the solver has since changed can only reach the solves that had no answer at all.
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
   *     orientation the solver declares they hold it in, or the grip a stored solve was recorded
   *     through, which is what decides the way up of a solve that only read one piece type
   */
  static int of(int[] shotFrom, int[] declared, int frame) {
    List<Integer> fits = fits(shotFrom, declared);
    if (fits.size() == 1) {
      return fits.get(0); // the pieces pin it, and nothing declared can move them
    }
    if (fits.isEmpty() || fits.contains(frame)) {
      return frame; // they settled nothing, or the frame given is one of the ways they left
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
   * where a solve read one piece type and no more <em>and</em> the frame given is not one of the
   * ways up it leaves, which is a solver whose declared buffer or declared hold no longer matches
   * what they do: the pieces still know which way up the cube is, so what they know is kept and the
   * declaration decides the rest.
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
