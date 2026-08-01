package com.cube.nanotimer.vo;

import java.util.Arrays;

public class ThreeCubeState implements CubeState {
  public byte[] cornerPermutations = new byte[8];
  public byte[] cornerOrientations = new byte[8];
  public byte[] edgePermutations = new byte[12];
  public byte[] edgeOrientations = new byte[12];

  public ThreeCubeState() {
  }

  public ThreeCubeState(ThreeCubeState cubeState) {
    System.arraycopy(cubeState.cornerPermutations, 0, this.cornerPermutations, 0, 8);
    System.arraycopy(cubeState.cornerOrientations, 0, this.cornerOrientations, 0, 8);
    System.arraycopy(cubeState.edgePermutations, 0, this.edgePermutations, 0, 12);
    System.arraycopy(cubeState.edgeOrientations, 0, this.edgeOrientations, 0, 12);
  }

  public boolean isSolved() {
    for (int i = 0; i < cornerPermutations.length; i++) {
      if (cornerPermutations[i] != i || cornerOrientations[i] != 0) {
        return false;
      }
    }
    for (int i = 0; i < edgePermutations.length; i++) {
      if (edgePermutations[i] != i || edgeOrientations[i] != 0) {
        return false;
      }
    }
    return true;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Corner permutations: ").append(Arrays.toString(cornerPermutations)).append("\n");
    sb.append("Corner orientations: ").append(Arrays.toString(cornerOrientations)).append("\n");
    sb.append("Edge permutations: ").append(Arrays.toString(edgePermutations)).append("\n");
    sb.append("Edge orientations: ").append(Arrays.toString(edgeOrientations));
    return sb.toString();
  }
}
