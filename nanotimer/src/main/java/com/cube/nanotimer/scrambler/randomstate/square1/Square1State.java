package com.cube.nanotimer.scrambler.randomstate.square1;

import com.cube.nanotimer.scrambler.randomstate.WalterIndexMapping;

import java.io.Serializable;

public class Square1State implements Serializable {
  // The value res/raw/square1_shapes.dat was written with, so the table no longer depends on the class shape.
  private static final long serialVersionUID = 5972047542828316112L;

  public byte[] permutation;

  public Square1State(byte[] permutation) {
    this.permutation = permutation;
  }

  public boolean isTwistable() {
    return this.permutation[1] != this.permutation[2] &&
      this.permutation[7] != this.permutation[8] &&
      this.permutation[13] != this.permutation[14] &&
      this.permutation[19] != this.permutation[20];
  }

  public Square1State multiply(Square1State move) {
    byte [] permutation = new byte[24];

    for (int i = 0; i < permutation.length; i++) {
      permutation[i] = this.permutation[move.permutation[i]];
    }

    return new Square1State(permutation);
  }

  public int getShapeIndex() {
    byte[] cuts = new byte[24];
    for (int i = 0; i < cuts.length; i++) {
      cuts[i] = 0;
    }

    for (int i = 0; i < 12; i++) {
      int next = (i + 1) % 12;
      if (this.permutation[i] != this.permutation[next]) {
        cuts[i] = 1;
      }
    }

    for (int i = 0; i < 12; i++) {
      int next = (i + 1) % 12;
      if (this.permutation[12 + i] != this.permutation[12 + next]) {
        cuts[12 + i] = 1;
      }
    }

    return WalterIndexMapping.orientationToIndex(cuts, 2);
  }

  public byte[] getPiecesPermutation() {
    byte[] permutation = new byte[16];
    int nextSlot = 0;

    for (int i = 0; i < 12; i++) {
      int next = (i + 1) % 12;
      if (this.permutation[i] != this.permutation[next]) {
        permutation[nextSlot++] = this.permutation[i];
      }
    }

    for (int i = 0; i < 12; i++) {
      int next = 12 + (i + 1) % 12;
      if (this.permutation[12 + i] != this.permutation[next]) {
        permutation[nextSlot++] = this.permutation[12 + i];
      }
    }

    return permutation;
  }

  public Square1CubeState toCubeState() {
    int[] cornerIndices = { 0, 3, 6, 9, 12, 15, 18, 21 };

    byte[] cornersPermutation = new byte[8];
    for (int i = 0; i < cornersPermutation.length; i++) {
      cornersPermutation[i] = this.permutation[cornerIndices[i]];
    }

    int[] edgeIndices = { 1, 4, 7, 10, 13, 16, 19, 22 };

    byte[] edgesPermutation = new byte[8];
    for (int i = 0; i < edgesPermutation.length; i++) {
      edgesPermutation[i] = (byte) (this.permutation[edgeIndices[i]] - 8);
    }

    return new Square1CubeState(cornersPermutation, edgesPermutation);
  }

  public static Square1State id;

  static {
    id = new Square1State(new byte[] {
      0,  8,  1,  1,  9,  2,  2, 10,  3,  3, 11,  0,
      4, 12,  5,  5, 13,  6,  6, 14,  7,  7, 15,  4,
    });
  }
}
