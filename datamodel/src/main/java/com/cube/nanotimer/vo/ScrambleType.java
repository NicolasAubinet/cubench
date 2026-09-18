package com.cube.nanotimer.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public abstract class ScrambleType implements Serializable, NameHolder {

  public static final String DEFAULT_NAME = "default";

  private String name;

  public ScrambleType(String name) {
    this.name = name;
  }

  public ThreeCubeState getRandomState() {
    ThreeCubeState cubeState;

    do {
      cubeState = new ThreeCubeState();
      cubeState.cornerPermutations = getRandomPermutation(getFixedCornerPermutationIndices(), 8);
      cubeState.edgePermutations = getRandomPermutation(getFixedEdgePermutationIndices(), 12);
      cubeState.cornerOrientations = getRandomOrientation(getFixedCornerOrientationIndices(), 8, 3);
      cubeState.edgeOrientations = getRandomOrientation(getFixedEdgeOrientationIndices(), 12, 2);
    } while (hasParity(cubeState.cornerPermutations) != hasParity(cubeState.edgePermutations)
        || (mustHaveParity() && !hasParity(cubeState.cornerPermutations))
        || cubeState.isSolved()); // a solved state would give an empty scramble (skip cases)

    return cubeState;
  }

  public String[] finalizeScramble(String[] scramble) {
    return scramble;
  }

  public static boolean hasParity(byte[] perm) {
    int inversion = 0;
    for (int i = 0; i < perm.length; i++) {
      for (int j = i + 1; j < perm.length; j++) {
        if (perm[i] > perm[j]) {
          inversion++;
        }
      }
    }
    return (inversion % 2 != 0);
  }

  @Override
  public String getName() {
    return name;
  }

  public boolean isDefault() {
    return name.equals(DEFAULT_NAME);
  }

  protected byte[] getFixedCornerPermutationIndices() {
    return new byte[0];
  }

  protected byte[] getFixedCornerOrientationIndices() {
    return new byte[0];
  }

  protected byte[] getFixedEdgePermutationIndices() {
    return new byte[0];
  }

  protected byte[] getFixedEdgeOrientationIndices() {
    return new byte[0];
  }

  /**
   * Whether the scramble keeps the D side solved and leaves a last layer on U, so that it can be
   * turned onto the solver's own last layer colour by relabelling its faces.
   */
  public boolean hasLastLayer() {
    return false;
  }

  /**
   * Whether the solve is over only once the whole cube is solved, which is what makes being stopped
   * on an unsolved cube worth a penalty.
   *
   * <p>True of every scramble that leaves a piece set which <em>is</em> the end of the solve: a last
   * layer, a PLL, the corners or the edges alone, the last of a Roux solve. False of the ones that
   * leave an intermediate block, where stopping with the rest of the cube scrambled is the point of
   * the drill rather than a mistake.
   *
   * <p>Not the same split as {@link #hasLastLayer()}, which cuts across this one in both directions:
   * F2L leaves a last layer and does not end solved, corners-only ends solved and leaves none.
   */
  public boolean endsSolved() {
    return true;
  }

  /**
   * The method a solve from this scramble is necessarily done with, or null where the scramble says
   * nothing and the solve type or the preference decides.
   *
   * <p>Only the states that belong to one method answer: a Roux block or its last pieces cannot be
   * solved by anything else, so reading them as the preferred method would fit a detector to a solve
   * it was never going to match. A last layer or an F2L is common to several methods and stays
   * silent.
   */
  public CubeMethod getMethod() {
    return null;
  }

  protected boolean mustHaveParity() {
    return false;
  }

  private byte[] getRandomPermutation(byte[] fixedIndices, int size) {
    List<Byte> randomPermutation = new ArrayList<>();
    for (byte i = 0; i < size; i++) {
      if (!containsIndex(fixedIndices, i)) {
        randomPermutation.add(i);
      }
    }
    Collections.shuffle(randomPermutation, new Random());

    Arrays.sort(fixedIndices);
    for (byte fixedIndex : fixedIndices) {
      randomPermutation.add(fixedIndex, fixedIndex);
    }

    byte[] randomPermutationArray = new byte[size];
    for (int i = 0; i < size; i++) {
      randomPermutationArray[i] = randomPermutation.get(i);
    }
    return randomPermutationArray;
  }

  protected String[] addMoveToScramble(String[] scramble, String move) {
    // add to scramble (and try to merge it with the last scramble move, like "R M'" becomes "r")
    String lastMove = scramble[scramble.length - 1];
    if (lastMove.equals("R")) {
      if (move.equals("M'")) {
        scramble[scramble.length - 1] = "r";
      } else if (move.equals("M2")) {
        scramble[scramble.length - 1] = "r";
        scramble = appendToArray(scramble, "M'");
      } else {
        scramble = appendToArray(scramble, move);
      }
    } else if (lastMove.equals("R'")) {
      if (move.equals("M")) {
        scramble[scramble.length - 1] = "r'";
      } else if (move.equals("M2")) {
        scramble[scramble.length - 1] = "r'";
        scramble = appendToArray(scramble, "M");
      } else {
        scramble = appendToArray(scramble, move);
      }
    } else if (lastMove.equals("R2")) {
      if (move.equals("M'")) {
        scramble[scramble.length - 1] = "R";
        scramble = appendToArray(scramble, "r");
      } else if (move.equals("M")) {
        scramble[scramble.length - 1] = "R'";
        scramble = appendToArray(scramble, "r'");
      } else if (move.equals("M2")) {
        scramble[scramble.length - 1] = "r2";
      } else {
        scramble = appendToArray(scramble, move);
      }
    } else if (lastMove.equals("L")) {
      if (move.equals("M")) {
        scramble[scramble.length - 1] = "l";
      } else if (move.equals("M2")) {
        scramble[scramble.length - 1] = "l";
        scramble = appendToArray(scramble, "M");
      } else {
        scramble = appendToArray(scramble, move);
      }
    } else if (lastMove.equals("L'")) {
      if (move.equals("M'")) {
        scramble[scramble.length - 1] = "l'";
      } else if (move.equals("M2")) {
        scramble[scramble.length - 1] = "l'";
        scramble = appendToArray(scramble, "M'");
      } else {
        scramble = appendToArray(scramble, move);
      }
    } else if (lastMove.equals("L2")) {
      if (move.equals("M'")) {
        scramble[scramble.length - 1] = "L'";
        scramble = appendToArray(scramble, "l'");
      } else if (move.equals("M")) {
        scramble[scramble.length - 1] = "L";
        scramble = appendToArray(scramble, "l");
      } else if (move.equals("M2")) {
        scramble[scramble.length - 1] = "l2";
      } else {
        scramble = appendToArray(scramble, move);
      }
    } else {
      scramble = appendToArray(scramble, move);
    }
    return scramble;
  }

  private String[] appendToArray(String[] scramble, String toAppend) {
    String[] finalizedScramble = new String[scramble.length + 1];
    System.arraycopy(scramble, 0, finalizedScramble, 0, scramble.length);
    finalizedScramble[finalizedScramble.length - 1] = toAppend;
    return finalizedScramble;
  }

  private byte[] getRandomOrientation(byte[] fixedIndices, int size, int nDifferentValues) {
    byte[] randomOrientation = new byte[size];
    Random random = new Random();

    int parity;
    do {
      parity = 0;
      for (byte i = 0; i < randomOrientation.length; i++) {
        byte orientation;
        if (containsIndex(fixedIndices, i)) {
          orientation = 0;
        } else {
          orientation = (byte) random.nextInt(nDifferentValues);
        }
        randomOrientation[i] = orientation;
        parity += orientation;
      }
    } while (parity % nDifferentValues != 0);

    return randomOrientation;
  }

  private boolean containsIndex(byte[] indexes, byte index) {
    for (byte currentIndex : indexes) {
      if (currentIndex == index) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ScrambleType)) return false;

    ScrambleType that = (ScrambleType) o;

    return name.equals(that.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

}
