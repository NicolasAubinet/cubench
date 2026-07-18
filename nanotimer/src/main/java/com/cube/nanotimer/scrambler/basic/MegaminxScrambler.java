package com.cube.nanotimer.scrambler.basic;

import com.cube.nanotimer.scrambler.Scrambler;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MegaminxScrambler implements Scrambler {

  private final int MOVES_PER_LINE = 10;
  private final int LINES_COUNT = 7;

  private String[] mainMoves = {"R", "D"};
  private String endMove = "U";

  @Override
  public String[] getNewScramble() {
    List<String> scramble = new ArrayList<String>();
    Random rand = new Random();
    for (int i = 0; i < LINES_COUNT; i++) {
      for (int j = 0; j < MOVES_PER_LINE; j++) {
        StringBuilder move = new StringBuilder();
        move.append(mainMoves[j % mainMoves.length]);
        if (rand.nextBoolean()) {
          move.append("++ ");
        } else {
          move.append("-- ");
        }
        scramble.add(move.toString());
      }

      StringBuilder move = new StringBuilder();
      move.append(endMove);
      if (rand.nextBoolean()) {
        move.append("'");
      } else {
        move.append(" ");
      }
      scramble.add(move.toString());

      if (i < LINES_COUNT - 1) {
        scramble.add("\n");
      }
    }
    return scramble.toArray(new String[0]);
  }

}
