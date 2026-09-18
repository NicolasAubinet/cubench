package com.cube.nanotimer.vo;

import static org.junit.Assert.assertEquals;

import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.Face;
import com.cube.nanotimer.smartcube.step.AlgorithmForm;
import java.util.List;
import org.junit.Test;

public class ScrambleTypeSliceMergeTest {

  private static final class Merging extends ScrambleType {
    Merging() {
      super("test");
    }

    String[] merge(String last, String slice) {
      return addMoveToScramble(new String[] {"U", last}, slice);
    }
  }

  /** A slice folded into the move before it must leave the cube where the two moves did. */
  @Test
  public void aMergedSliceIsTheSameTurning() {
    Merging type = new Merging();
    for (String last : new String[] {"R", "R'", "R2", "L", "L'", "L2", "F"}) {
      for (String slice : new String[] {"M", "M'", "M2"}) {
        String[] merged = type.merge(last, slice);
        assertEquals(last + " " + slice + " -> " + String.join(" ", merged),
            state("U", last, slice), state(merged));
      }
    }
  }

  private static String state(String... scramble) {
    CubieCube cube = new CubieCube();
    for (List<String> turns : AlgorithmForm.perToken(scramble)) {
      for (String turn : turns) {
        Face face = Face.valueOf(turn.substring(0, 1));
        int quarters = turn.endsWith("'") ? 3 : turn.endsWith("2") ? 2 : 1;
        for (int i = 0; i < quarters; i++) {
          cube.applyMove(face, false);
        }
      }
    }
    return cube.toFaceCube();
  }
}
