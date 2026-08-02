package com.cube.nanotimer.util.helper;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class UtilsTest {

  /**
   * The pieces the breakdown marks are read back out of the name the detector wrote, so the two have
   * to agree on how one is put together: pieces joined with a dash, the two swaps of a parity with a
   * plus between them, and a flip or a twist saying what it did in front of a colon.
   */
  @Test
  public void readsThePiecesOutOfEveryShapeOfBlindName() {
    assertArrayEquals(new String[] {"UF", "DB", "BR"}, Utils.getSmartCubeNamedPieces("UF-DB-BR"));
    assertArrayEquals(new String[] {"UF", "FL"}, Utils.getSmartCubeNamedPieces("flip:UF-FL"));
    assertArrayEquals(new String[] {"LUB", "LDF", "BDL"},
        Utils.getSmartCubeNamedPieces("twist:LUB-LDF-BDL"));
    assertArrayEquals(new String[] {"UFR", "UBL", "UF", "UR"},
        Utils.getSmartCubeNamedPieces("UFR-UBL + UF-UR"));
  }
}
