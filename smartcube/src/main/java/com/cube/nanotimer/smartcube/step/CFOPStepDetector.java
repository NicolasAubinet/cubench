package com.cube.nanotimer.smartcube.step;

import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.Face;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Detects the CFOP milestones (Cross, F2L, OLL, PLL) on the live facelet stream. Each one is a
 * predicate on the net state, so any solving order reaches it and a skipped step simply completes
 * with the previous one. A step is dated at its first completion and never retracted: later steps
 * routinely disturb earlier ones for a few moves (an F2L insertion lifts a cross edge out and back).
 *
 * <p>The cross face is auto-detected. All six candidates are tracked, and F2L completion confirms
 * which one the solve was actually built on — so a cross that happens to be complete on some other
 * face (in the scramble, or in passing) is discarded rather than mistaken for the real one.
 */
public final class CFOPStepDetector implements StepDetector {

  public static final int CROSS = 0;
  public static final int F2L = 1;
  public static final int OLL = 2;
  public static final int PLL = 3;

  private static final String[] STEP_NAMES = {"Cross", "F2L", "OLL", "PLL"};

  private static final String SOLVED = CubeState.SOLVED_FACELETS;
  private static final String FACES = "URFDLB";

  /** Corner facelet indices, one triple per corner (URF, UFL, ...), as in {@code CubieCube}. */
  private static final int[][] CORNERS = {
    {8, 9, 20}, {6, 18, 38}, {0, 36, 47}, {2, 45, 11},
    {29, 26, 15}, {27, 44, 24}, {33, 53, 42}, {35, 17, 51},
  };

  /** Edge facelet indices, one pair per edge (UR, UF, ...), as in {@code CubieCube}. */
  private static final int[][] EDGES = {
    {5, 10}, {7, 19}, {3, 37}, {1, 46}, {32, 16}, {28, 25},
    {30, 43}, {34, 52}, {23, 12}, {21, 41}, {50, 39}, {48, 14},
  };

  private final Long[][] times = new Long[6][STEP_NAMES.length]; // [cross face][step]
  private final Long[] reported = new Long[STEP_NAMES.length];

  private Integer crossFace; // provisional until F2L confirms it
  private boolean confirmed;
  private long lastTimestampMs;

  @Override
  public void reset(CubeState startState, long startTimestampMs) {
    for (Long[] faceTimes : times) {
      Arrays.fill(faceTimes, null);
    }
    Arrays.fill(reported, null);
    crossFace = null;
    confirmed = false;
    lastTimestampMs = startTimestampMs;
    evaluate(startState.getFacelets(), startTimestampMs);
    for (int step = 0; step < STEP_NAMES.length; step++) {
      reported[step] = getStepTimestampMs(step);
    }
  }

  @Override
  public List<StepBoundaryEvent> onState(CubeState state, CubeMove lastMove) {
    if (lastMove != null) {
      lastTimestampMs = lastMove.getCubeTimestampMs();
    }
    evaluate(state.getFacelets(), lastTimestampMs);

    List<StepBoundaryEvent> events = new ArrayList<>();
    for (int step = 0; step < STEP_NAMES.length; step++) {
      Long time = getStepTimestampMs(step);
      if (time != null && !time.equals(reported[step])) {
        events.add(new StepBoundaryEvent(step, time));
      }
      reported[step] = time;
    }
    return events;
  }

  private void evaluate(String facelets, long timestampMs) {
    boolean solved = SOLVED.equals(facelets);
    for (int face = 0; face < 6; face++) {
      boolean cross = crossDone(facelets, face);
      boolean f2l = cross && f2lDone(facelets, face);
      boolean oll = f2l && lastLayerOriented(facelets, face);
      mark(face, CROSS, cross, timestampMs);
      mark(face, F2L, f2l, timestampMs);
      mark(face, OLL, oll, timestampMs);
      mark(face, PLL, solved, timestampMs);
    }
    updateCrossFace();
  }

  private void mark(int face, int step, boolean done, long timestampMs) {
    if (done && times[face][step] == null) {
      times[face][step] = timestampMs;
    }
  }

  private void updateCrossFace() {
    if (confirmed) {
      return;
    }
    for (int face = 0; face < 6; face++) {
      if (times[face][F2L] != null) {
        crossFace = face;
        confirmed = true;
        return;
      }
    }
    if (crossFace != null && times[crossFace][CROSS] != null) {
      return;
    }
    crossFace = null;
    for (int face = 0; face < 6; face++) {
      if (times[face][CROSS] != null) {
        crossFace = face;
        return;
      }
    }
  }

  /** The 4 edges of the cross face are in place. */
  private static boolean crossDone(String facelets, int face) {
    for (int[] edge : EDGES) {
      if (touches(edge, face) && !inPlace(facelets, edge)) {
        return false;
      }
    }
    return true;
  }

  /** Cross plus the 4 first-layer corners and the 4 middle-layer edges: the first two layers. */
  private static boolean f2lDone(String facelets, int face) {
    int opposite = opposite(face);
    for (int[] corner : CORNERS) {
      if (touches(corner, face) && !inPlace(facelets, corner)) {
        return false;
      }
    }
    for (int[] edge : EDGES) {
      if (!touches(edge, face) && !touches(edge, opposite) && !inPlace(facelets, edge)) {
        return false;
      }
    }
    return true;
  }

  /** The last-layer face shows a single colour; its pieces may still be permuted wrong. */
  private static boolean lastLayerOriented(String facelets, int face) {
    int opposite = opposite(face);
    char colour = FACES.charAt(opposite);
    for (int i = opposite * 9; i < opposite * 9 + 9; i++) {
      if (facelets.charAt(i) != colour) {
        return false;
      }
    }
    return true;
  }

  private static boolean touches(int[] piece, int face) {
    for (int facelet : piece) {
      if (SOLVED.charAt(facelet) == FACES.charAt(face)) {
        return true;
      }
    }
    return false;
  }

  private static boolean inPlace(String facelets, int[] piece) {
    for (int facelet : piece) {
      if (facelets.charAt(facelet) != SOLVED.charAt(facelet)) {
        return false;
      }
    }
    return true;
  }

  private static int opposite(int face) {
    return (face + 3) % 6;
  }

  /** The face the cross was built on, or null before any cross completes. */
  public Face getCrossFace() {
    return crossFace == null ? null : Face.valueOf(String.valueOf(FACES.charAt(crossFace)));
  }

  @Override
  public int stepCount() {
    return STEP_NAMES.length;
  }

  @Override
  public String stepName(int index) {
    return STEP_NAMES[index];
  }

  @Override
  public Long getStepTimestampMs(int index) {
    return crossFace == null ? null : times[crossFace][index];
  }

  @Override
  public boolean isComplete() {
    return times[0][PLL] != null;
  }
}
