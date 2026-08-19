package com.cube.nanotimer.vo;

import java.util.Random;

public class ScrambleTypes {

  public static final ScrambleType DEFAULT = new ScrambleType(ScrambleType.DEFAULT_NAME) {
  };

  public static final ScrambleType[] THREE_BY_THREE = new ScrambleType[] {
    DEFAULT,
    new ScrambleType("f2l") {
      @Override
      protected byte[] getFixedEdgePermutationIndices() {
        return new byte[] { 8, 9, 10, 11 };
      }

      @Override
      protected byte[] getFixedEdgeOrientationIndices() {
        return new byte[] { 8, 9, 10, 11 };
      }
    },
    new ScrambleType("last_layer") {
      @Override
      protected byte[] getFixedCornerPermutationIndices() {
        return new byte[] { 4, 5, 6, 7 };
      }

      @Override
      protected byte[] getFixedCornerOrientationIndices() {
        return new byte[] { 4, 5, 6, 7 };
      }

      @Override
      protected byte[] getFixedEdgePermutationIndices() {
        return new byte[] { 0, 1, 2, 3, 8, 9, 10, 11 };
      }

      @Override
      protected byte[] getFixedEdgeOrientationIndices() {
        return new byte[] { 0, 1, 2, 3, 8, 9, 10, 11 };
      }
    },
    new ScrambleType("pll") {
      @Override
      protected byte[] getFixedCornerPermutationIndices() {
        return new byte[] { 4, 5, 6, 7 };
      }

      @Override
      protected byte[] getFixedCornerOrientationIndices() {
        return new byte[] { 0, 1, 2, 3, 4, 5, 6, 7 };
      }

      @Override
      protected byte[] getFixedEdgePermutationIndices() {
        return new byte[] { 0, 1, 2, 3, 8, 9, 10, 11 };
      }

      @Override
      protected byte[] getFixedEdgeOrientationIndices() {
        return new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 };
      }
    },
    new ScrambleType("corners") {
      @Override
      protected byte[] getFixedEdgePermutationIndices() {
        return new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 };
      }

      @Override
      protected byte[] getFixedEdgeOrientationIndices() {
        return new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 };
      }
    },
    new ScrambleType("edges") {
      @Override
      protected byte[] getFixedCornerOrientationIndices() {
        return new byte[] { 0, 1, 2, 3, 4, 5, 6, 7 };
      }

      @Override
      protected byte[] getFixedCornerPermutationIndices() {
        return new byte[] { 0, 1, 2, 3, 4, 5, 6, 7 };
      }
    },
    new ScrambleType("roux_second_block") {
      // the first block stays solved: the DBL and DFL corners, and the BL, FL and DL edges
      @Override
      protected byte[] getFixedCornerPermutationIndices() {
        return new byte[] { 4, 5 };
      }

      @Override
      protected byte[] getFixedCornerOrientationIndices() {
        return new byte[] { 4, 5 };
      }

      @Override
      protected byte[] getFixedEdgePermutationIndices() {
        return new byte[] { 2, 3, 11 };
      }

      @Override
      protected byte[] getFixedEdgeOrientationIndices() {
        return new byte[] { 2, 3, 11 };
      }
    },
    new ScrambleType("roux_last_10_pieces") {
      @Override
      protected byte[] getFixedCornerPermutationIndices() {
        return new byte[] { 4, 5, 6, 7 };
      }

      @Override
      protected byte[] getFixedCornerOrientationIndices() {
        return new byte[] { 4, 5, 6, 7 };
      }

      @Override
      protected byte[] getFixedEdgePermutationIndices() {
        return new byte[] { 0, 1, 2, 3, 9, 11 };
      }

      @Override
      protected byte[] getFixedEdgeOrientationIndices() {
        return new byte[] { 0, 1, 2, 3, 9, 11 };
      }

      @Override
      public String[] finalizeScramble(String[] scramble) {
        // add final "m" move so that the centers are not always aligned with the blocks
        int locResult = new Random().nextInt(4);

        if (locResult > 0) {
          String additionalMove = "";
          switch (locResult) {
            case 1:
              additionalMove = "m";
              break;
            case 2:
              additionalMove = "m'";
              break;
            case 3:
              additionalMove = "m2";
              break;
          }

          scramble = addMoveToScramble(scramble, additionalMove);
        }

        return scramble;
      }
    },
    new ScrambleType("roux_last_6_edges") {
      @Override
      protected byte[] getFixedCornerPermutationIndices() {
        return new byte[] { 0, 1, 2, 3, 4, 5, 6, 7 };
      }

      @Override
      protected byte[] getFixedCornerOrientationIndices() {
        return new byte[] { 0, 1, 2, 3, 4, 5, 6, 7 };
      }

      @Override
      protected byte[] getFixedEdgePermutationIndices() {
        return new byte[] { 0, 1, 2, 3, 9, 11 };
      }

      @Override
      protected byte[] getFixedEdgeOrientationIndices() {
        return new byte[] { 0, 1, 2, 3, 9, 11 };
      }
    },
    new ScrambleType("parity") {
      @Override
      protected boolean mustHaveParity() {
        return true;
      }
    },
  };

}
