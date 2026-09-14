package com.cube.nanotimer.scrambler.randomstate;

import com.cube.nanotimer.vo.TwoCubeState;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TwoSolver {

  // Cubies numbering (DBL is considered as solved):
  //
  //     U          D
  // #########  #########
  // # 0 # 3 #  #   # 6 #
  // #########  #########
  // # 1 # 2 #  # 4 # 5 #
  // #########  #########

  enum Move {
    U("U", new byte[] { 1, 2, 3, 0, 4, 5, 6 }, new byte[] { 0, 0, 0, 0, 0, 0, 0 }),
    UP("U'", new byte[] { }, new byte[] { }),
    U2("U2", new byte[] { }, new byte[] { }),
    R("R", new byte[] { 0, 1, 5, 2, 4, 6, 3 }, new byte[] { 0, 0, 2, 1, 0, 1, 2 }),
    RP("R'", new byte[] { }, new byte[] { }),
    R2("R2", new byte[] { }, new byte[] { }),
    F("F", new byte[] { 0, 4, 1, 3, 5, 2, 6 }, new byte[] { 0, 2, 1, 0, 1, 2, 0 }),
    FP("F'", new byte[] { }, new byte[] { }),
    F2("F2", new byte[] { }, new byte[] { });

    Move(String name, byte[] corPerm, byte[] corOrient) {
      this.name = name;
      this.corPerm = corPerm;
      this.corOrient = corOrient;
    }

    String name;
    byte[] corPerm;
    byte[] corOrient;
  }

  public static final int N_PERM = 5040;
  public static final int N_ORIENT = 729;

  private static final int SEARCH_TIME_MIN = 100; // time in ms during which to search for a better solution
  private static final int DEFAULT_MAX_SOLUTION_LENGTH = 11;

  private List<Byte> solution;
  private List<Byte> bestSolution;
  private long searchStartTs;
  private int maxSolutionLength;

  private boolean mustStop = false;
  private final Random random = new Random();

  private static Move[] moves;
  private static Move[] allMoves;
  private static byte[] slices;

  // Transition tables
  static short[][] transitPerm;
  static short[][] transitOrient;

  // Pruning tables
  static byte[] pruningPerm;
  static byte[] pruningOrient;

  static {
    // Moves
    moves = new Move[] {
        Move.U, Move.R, Move.F
    };

    allMoves = new Move[] {
        Move.U, Move.U2, Move.UP,
        Move.R, Move.R2, Move.RP,
        Move.F, Move.F2, Move.FP,
    };

    // Slices
    slices = new byte[allMoves.length];
    for (int i = 0; i < allMoves.length; i++) {
      slices[i] = (byte) (i / 3);
    }
  }

  private boolean search(int perm, int orient, int depth, byte lastMove) throws InterruptedException {
    boolean foundSolution = false;
    if (depth == 0) {
      if (orient == 0 && perm == 0) {
        if (bestSolution == null || solution.size() < bestSolution.size()) {
          bestSolution = new ArrayList<Byte>(solution.size());
          for (Byte m : solution) {
            bestSolution.add(m);
          }
        }
        foundSolution = true;
      }
      return foundSolution;
    }
    if (bestSolution != null && System.currentTimeMillis() > searchStartTs + SEARCH_TIME_MIN) {
      return false;
    }
    if (mustStop) {
      throw new InterruptedException("Scramble interruption requested.");
    }

    if (pruningPerm[perm] <= depth && pruningOrient[orient] <= depth) {
      int curSolutionSize = solution.size();
      for (byte i = 0; i < moves.length; i++) {
        if (lastMove >= 0 && i == slices[lastMove]) { // same face twice in a row
          continue;
        }
        int corPerm = perm;
        int corOri = orient;
        for (int j = 0; j < 3; j++) {
          corPerm = transitPerm[corPerm][i];
          corOri = transitOrient[corOri][i];
          byte nextMove = (byte) (i * 3 + j);
          solution.add(nextMove);
          foundSolution |= search(corPerm, corOri, depth - 1, nextMove);
          solution.remove(curSolutionSize);
        }
      }
    }
    return foundSolution;
  }

  /**
   * A sequence of exactly {@code length} moves that solves this state, or null where the search
   * finds none.
   *
   * <p>Longer than it needs to be, on purpose. Inverting a shortest solution gives a shortest
   * scramble, and one that comes out four moves long is a giveaway before inspection has even
   * started. Every official WCA 2x2 scramble is eleven moves for that reason (450,851 of them in
   * the results export, without one exception), so a scramble built on this reads like one.
   *
   * <p>Faces and turns are tried in a random order at each step. The deterministic search behind
   * the official scrambles leans on whichever it tries first, enough to be measurable: an official
   * 2x2 scramble opens on F 5% of the time rather than a third of it.
   */
  public String[] getGenerator(TwoCubeState cubeState, int length) {
    solution = new ArrayList<Byte>(length);
    int cornerPermutation = IndexConvertor.packPermutation(cubeState.permutations);
    int cornerOrientation = IndexConvertor.packOrientation(cubeState.orientations, 3);

    try {
      if (!searchExactly(cornerPermutation, cornerOrientation, length, (byte) -1)) {
        return null;
      }
    } catch (InterruptedException e) {
      return null; // user requested stop
    }

    String[] moveNames = new String[solution.size()];
    for (int i = 0; i < moveNames.length; i++) {
      moveNames[i] = allMoves[solution.get(i)].name;
    }
    return moveNames;
  }

  /** Depth-first for a solution of exactly this depth, stopping at the first one found. */
  private boolean searchExactly(int perm, int orient, int depth, byte lastMove) throws InterruptedException {
    if (depth == 0) {
      return perm == 0 && orient == 0;
    }
    if (mustStop) {
      throw new InterruptedException("Scramble interruption requested.");
    }
    if (pruningPerm[perm] > depth || pruningOrient[orient] > depth) {
      return false;
    }

    int faceOffset = random.nextInt(moves.length);
    for (int f = 0; f < moves.length; f++) {
      byte i = (byte) ((f + faceOffset) % moves.length);
      if (lastMove >= 0 && i == slices[lastMove]) { // same face twice in a row
        continue;
      }
      int perm1 = transitPerm[perm][i], orient1 = transitOrient[orient][i];
      int perm2 = transitPerm[perm1][i], orient2 = transitOrient[orient1][i];
      int perm3 = transitPerm[perm2][i], orient3 = transitOrient[orient2][i];

      int turnOffset = random.nextInt(3);
      for (int t = 0; t < 3; t++) {
        int j = (t + turnOffset) % 3;
        int nextPerm = (j == 0) ? perm1 : (j == 1) ? perm2 : perm3;
        int nextOrient = (j == 0) ? orient1 : (j == 1) ? orient2 : orient3;
        byte nextMove = (byte) (i * 3 + j);
        solution.add(nextMove);
        if (searchExactly(nextPerm, nextOrient, depth - 1, nextMove)) {
          return true;
        }
        solution.remove(solution.size() - 1);
      }
    }
    return false;
  }

  public String[] getSolution(TwoCubeState cubeState) {
    return getSolution(cubeState, null);
  }

  public String[] getSolution(TwoCubeState cubeState, ScrambleConfig config) {
//    if (transitPerm == null) { // (now generated from ScramblerService)
//      genTables();
//    }
    if (config != null && config.getMaxLength() > 0) {
      maxSolutionLength = config.getMaxLength();
    } else {
      maxSolutionLength = DEFAULT_MAX_SOLUTION_LENGTH;
    }
    searchStartTs = System.currentTimeMillis();

    solution = new ArrayList<Byte>();
    bestSolution = null;

    int cornerPermutation = IndexConvertor.packPermutation(cubeState.permutations);
    int cornerOrientation = IndexConvertor.packOrientation(cubeState.orientations, 3);

    try {
      for (int i = 0; bestSolution == null || System.currentTimeMillis() < searchStartTs + SEARCH_TIME_MIN || bestSolution.size() > maxSolutionLength; i++) {
        if (search(cornerPermutation, cornerOrientation, i, (byte) -1)) {
          solution = new ArrayList<Byte>();
        }
      }
    } catch (InterruptedException e) {
      // ignore, user requested stop
    }

    String[] solution = null;
    if (bestSolution != null) {
      solution = new String[bestSolution.size()];
      int i = 0;
      for (Byte m : bestSolution) {
        solution[i++] = allMoves[m].name;
      }
    }
//    Log.i("[NanoTimer]", "solution time: " + (System.currentTimeMillis() - searchStartTs));

    return solution;
  }

  public static void genTables() {
    if (transitPerm != null) {
      return;
    }
    byte[] state7 = new byte[7];

    transitPerm = new short[N_PERM][moves.length];
    for (int i = 0; i < transitPerm.length; i++) {
      IndexConvertor.unpackPermutation(i, state7);
      for (int j = 0; j < moves.length; j++) {
        transitPerm[i][j] = (short) IndexConvertor.packPermMult(state7, moves[j].corPerm);
      }
    }

    transitOrient = new short[N_ORIENT][moves.length];
    for (int i = 0; i < transitOrient.length; i++) {
      IndexConvertor.unpackOrientation(i, state7, (byte) 3);
      for (int j = 0; j < moves.length; j++) {
        transitOrient[i][j] = (short) IndexConvertor.packOrientMult(state7, moves[j].corPerm, moves[j].corOrient, 3);
      }
    }

    pruningPerm = new byte[N_PERM];
    genPruning(pruningPerm, transitPerm);
    pruningOrient = new byte[N_ORIENT];
    genPruning(pruningOrient, transitOrient);
  }

  private static void genPruning(byte[] pruningTable, short[][] transit) {
    for (int i = 0; i < transit.length; i++) {
      pruningTable[i] = -1;
    }
    pruningTable[0] = 0;
    int done = 1;
    byte distance = 0;
    while (done < pruningTable.length) {
      for (int i = 0; i < transit.length; i++) {
        if (pruningTable[i] == distance) {
          for (int j = 0; j < moves.length; j++) {
            int res = i;
            for (int k = 0; k < 3; k++) {
              res = transit[res][j];
              if (pruningTable[res] < 0) {
                pruningTable[res] = (byte) (distance + 1);
                done++;
              }
            }
          }
        }
      }
      distance++;
    }
  }

  public void stop() {
    mustStop = true;
  }

  /** Whether a null answer means the user asked to stop, rather than a search coming up empty. */
  public boolean isStopped() {
    return mustStop;
  }

}
