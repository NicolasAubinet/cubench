package com.cube.nanotimer.smartcube.step;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.Face;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The algorithms one last layer step took, which is normally one. A solver who misreads the case
 * executes an algorithm that leaves the layer some other way and has a second one to do — so every
 * return to the states the step runs between ends an algorithm, and each is named by the case it
 * would have solved rather than the one it was given. That is what tells a misread case from a
 * slow one, and it is also how a two-look OLL and a two-look PLL read.
 *
 * <p>An alignment turn ends nothing: it never leaves those states. Nor does a run that came back
 * to where it started — an algorithm begun and taken back names no case, so those moves stay with
 * the algorithm that follows rather than becoming one of their own.
 *
 * <p>A single algorithm that passed back through one of those states on its way would read as two,
 * which is the one thing that could invent a look the solver never took. {@code OneLookOllTest}
 * holds the standard algorithms to that, since OLL is where those states are the easier to hit: an
 * OLL runs between states that only need the first two layers, a PLL between states that need them
 * oriented as well.
 *
 * <p>Some listed algorithms do pass through one: OLL 41 and 42 are a Sune or an Antisune followed
 * by {@code F R U R' U' F'}, OLL 3 and 4 two such halves around a turn of the layer. Parts that
 * are the halves of an algorithm listed for the case handed over are read as that one algorithm,
 * however long the pause between them: a listed algorithm is known, whatever it is made of (owner,
 * 2026-11-17). Which algorithms have halves is read off the table itself, by turning each one.
 */
final class AlgorithmTrack {

  /** Stands where an algorithm's name would, for the moves that got back to where they started. */
  static final String RESTART = "restart";

  /** The face the table's algorithms are turned with the first two layers on. */
  private static final int TABLE_CROSS_FACE = Cubies.FACES.indexOf('D');

  /** Whether the step permutes the layer (a PLL) or orients it (an OLL). */
  private final boolean permutes;
  /** Off while the listed algorithms are read into {@link Composites}, which this reads. */
  private final boolean joins;

  private final List<String> cases = new ArrayList<>();
  private final List<Long> times = new ArrayList<>();
  /** When each algorithm first left the states the step runs between, its first real turn. */
  private final List<Long> starts = new ArrayList<>();
  private String handedState;
  private String landedState; // what the algorithm being turned started from
  private boolean leftTheCase;
  private long leftAtMs;
  private boolean finished;

  AlgorithmTrack(boolean permutes) {
    this(permutes, true);
  }

  private AlgorithmTrack(boolean permutes, boolean joins) {
    this.permutes = permutes;
    this.joins = joins;
  }

  void reset() {
    cases.clear();
    times.clear();
    starts.clear();
    handedState = null;
    landedState = null;
    leftTheCase = false;
    finished = false;
  }

  /**
   * @param handed the state the step was handed, or null before the step before it completed
   * @param resting whether the cube is at one of the states this step runs between
   */
  void track(String handed, boolean resting, String facelets, long timestampMs, int crossFace) {
    if (finished || handed == null) {
      return;
    }
    if (landedState == null) {
      handedState = handed;
      landedState = handed;
      finished = done(landedState, crossFace); // the step was already solved on arrival
    }
    if (finished) {
      return;
    }
    if (!resting) {
      if (!leftTheCase) {
        leftAtMs = timestampMs;
      }
      leftTheCase = true;
      return;
    }
    if (!leftTheCase) {
      return;
    }
    String executed = permutes
        ? LastLayerCases.permutationAlgorithm(landedState, facelets, crossFace)
        : LastLayerCases.orientationAlgorithm(landedState, facelets, crossFace);
    if (executed == null) {
      return; // nothing nameable landed on: keep waiting
    }
    if (LastLayerCases.SKIP.equals(executed)) {
      // Back at the case it left. Cut the scrapped moves off here so the algorithm that follows
      // is measured from this point rather than from where the fumble started.
      cases.add(RESTART);
      times.add(Long.valueOf(timestampMs));
      starts.add(Long.valueOf(leftAtMs));
      landedState = facelets;
      leftTheCase = false;
      return; // the case still stands, so the step cannot be done
    }
    cases.add(executed);
    times.add(Long.valueOf(timestampMs));
    starts.add(Long.valueOf(leftAtMs));
    landedState = facelets;
    leftTheCase = false;
    // Landed on nothing left to do: whatever is turned after it belongs to the step that follows.
    finished = done(facelets, crossFace);
    if (finished && joins) {
      joinListed(crossFace);
    }
  }

  /** Reads the halves of a listed algorithm as the one algorithm they are. */
  private void joinListed(int crossFace) {
    String handedCase = handedCase(crossFace);
    if (cases.size() < 2 || !isListed(permutes, handedCase, cases)) {
      return;
    }
    long last = times.get(times.size() - 1);
    long first = starts.get(0);
    cases.clear();
    times.clear();
    starts.clear();
    cases.add(handedCase);
    times.add(Long.valueOf(last));
    starts.add(Long.valueOf(first));
  }

  /** The case the step was handed, by the name its algorithms go under. */
  String handedCase(int crossFace) {
    return permutes ? LastLayerCases.permutation(handedState, crossFace)
        : LastLayerCases.orientation(handedState, crossFace);
  }

  /** The algorithms the step took, leaving out the moves that came back to where they started. */
  List<String> algorithms() {
    List<String> algorithms = new ArrayList<>(cases);
    algorithms.removeAll(Collections.singleton(RESTART));
    return algorithms;
  }

  /** Whether these parts, in order, are the halves of an algorithm listed for the case. */
  static boolean isListed(boolean permutes, String handedCase, List<String> parts) {
    Set<List<String>> halves = Composites.BY_CASE.get((permutes ? "pll_" : "oll_") + handedCase);
    return halves != null && halves.contains(parts);
  }

  /** At one of the states a step runs between: the first two layers, and oriented for a PLL. */
  static boolean resting(String facelets, int crossFace, boolean permutes) {
    return firstTwoLayers(facelets, crossFace) && (!permutes
        || (Cubies.lastLayerOriented(facelets, crossFace, Cubies.EDGE_POSITIONS)
            && Cubies.lastLayerOriented(facelets, crossFace, Cubies.CORNER_POSITIONS)));
  }

  /** Every piece off the last layer in place. */
  static boolean firstTwoLayers(String facelets, int crossFace) {
    int lastLayer = Cubies.opposite(crossFace);
    for (int[] piece : Cubies.PIECES) {
      if (!Cubies.touches(piece, lastLayer) && !Cubies.inPlace(facelets, piece)) {
        return false;
      }
    }
    return true;
  }

  /**
   * The parts each listed algorithm reads as, where that is more than one, by case: every algorithm
   * a solver is shown for the case, turned from the case it solves. A low-voted spelling is left
   * out, since several are a plain two-look (OLL 35 as {@code f R U R' U' f'} then a Sune). Read
   * once, on first use.
   */
  private static final class Composites {

    static final Map<String, Set<List<String>>> BY_CASE = read();

    private static Map<String, Set<List<String>>> read() {
      Map<String, Set<List<String>>> byCase = new HashMap<>();
      Set<String> codes = new LinkedHashSet<>();
      for (String[] row : LastLayerCaseAlgorithms.rows()) {
        codes.add(row[0]);
      }
      for (String code : codes) {
        boolean permutes = code.startsWith("pll_");
        if (!permutes && !code.startsWith("oll_")) {
          continue;
        }
        for (LastLayerCaseAlgorithms.Algorithm listed : LastLayerCaseAlgorithms.listed(code)) {
          read(byCase, permutes, listed.getMoves());
        }
      }
      return byCase;
    }

    private static void read(Map<String, Set<List<String>>> byCase, boolean permutes,
        String algorithm) {
      List<String> turns;
      try {
        turns = AlgorithmForm.of(algorithm);
      } catch (IllegalArgumentException e) {
        return;
      }
      CubieCube cube = new CubieCube();
      for (int i = turns.size() - 1; i >= 0; i--) {
        turn(cube, turns.get(i), true);
      }
      String handed = cube.toFaceCube();
      AlgorithmTrack track = new AlgorithmTrack(permutes, false);
      track.track(handed, true, handed, 0, TABLE_CROSS_FACE);
      for (String turn : turns) {
        String quarter = turn.endsWith("'") ? turn.substring(0, 1) + "'" : turn.substring(0, 1);
        for (int q = turn.endsWith("2") ? 2 : 1; q > 0; q--) {
          turn(cube, quarter, false);
          String facelets = cube.toFaceCube();
          track.track(handed, resting(facelets, TABLE_CROSS_FACE, permutes), facelets, 0,
              TABLE_CROSS_FACE);
        }
      }
      List<String> parts = track.algorithms();
      if (track.isFinished() && parts.size() > 1) {
        String key = (permutes ? "pll_" : "oll_") + track.handedCase(TABLE_CROSS_FACE);
        Set<List<String>> halves = byCase.get(key);
        if (halves == null) {
          halves = new HashSet<>();
          byCase.put(key, halves);
        }
        halves.add(parts);
      }
    }

    /** One face turn, or its inverse. */
    private static void turn(CubieCube cube, String turn, boolean inverted) {
      Face face = Face.valueOf(turn.substring(0, 1));
      boolean prime = turn.endsWith("'") != inverted;
      for (int q = turn.endsWith("2") ? 2 : 1; q > 0; q--) {
        cube.applyMove(face, prime);
      }
    }
  }

  private boolean done(String facelets, int crossFace) {
    String remaining = permutes ? LastLayerCases.permutation(facelets, crossFace)
        : LastLayerCases.orientation(facelets, crossFace);
    return LastLayerCases.SKIP.equals(remaining);
  }

  /** Whether the step was seen through to nothing left to do. */
  boolean isFinished() {
    return finished;
  }

  int count() {
    return cases.size();
  }

  String caseAt(int index) {
    return cases.get(index);
  }

  Long timeAt(int index) {
    return times.get(index);
  }
}
