package com.cube.nanotimer.scrambler.randomstate;

import com.cube.nanotimer.vo.TwoCubeState;
import junit.framework.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Arrays;

@RunWith(JUnit4.class)
public class TwoSolverTest {

  @Test
  public void testVeryEasy() {
    TwoCubeState cubeState = new TwoCubeState();
    //  F
    cubeState.permutations = new byte[] { 0, 4, 1, 3, 5, 2, 6 };
    cubeState.orientations = new byte[] { 0, 2, 1, 0, 1, 2, 0 };

    TwoSolver solver = new TwoSolver();
    String[] scramble = solver.getSolution(cubeState);
    System.out.println("Scramble: " + Arrays.toString(scramble));
    Assert.assertTrue(scramble.length > 0);
  }

  @Test
  public void testEasy() {
    TwoCubeState cubeState = new TwoCubeState();
    // F U2
    cubeState.permutations = new byte[] { 1, 3, 0, 4, 5, 2, 6 };
    cubeState.orientations = new byte[] { 1, 0, 0, 2, 1, 2, 0 };

    TwoSolver solver = new TwoSolver();
    String[] scramble = solver.getSolution(cubeState);
    System.out.println("Scramble: " + Arrays.toString(scramble));
    Assert.assertTrue(scramble.length > 0);
    // Found:
  }

  @Test
  public void testNormal() {
    TwoCubeState cubeState = new TwoCubeState();
    // U F2 R U2 R' F R F' U' R2
    cubeState.permutations = new byte[] { 4, 2, 0, 1, 3, 6, 5 };
    cubeState.orientations = new byte[] { 0, 2, 2, 1, 0, 2, 2 };

    TwoSolver solver = new TwoSolver();
    String[] scramble = solver.getSolution(cubeState);
    System.out.println("Scramble: " + Arrays.toString(scramble));
    Assert.assertTrue(scramble.length > 0);
    // Found:
  }

  @Test
  public void testCubeStateMoves() {
    TwoCubeState state = new TwoCubeState();
    byte[] state7 = new byte[7];
    IndexConvertor.unpackPermutation(0, state7);
    state.permutations = state7;
    state7 = new byte[7];
    IndexConvertor.unpackOrientation(0, state7, (byte) 3);
    state.orientations = state7;

    applyMove(state, TwoSolver.Move.F); // F2
    applyMove(state, TwoSolver.Move.F);
    applyMove(state, TwoSolver.Move.U); // U
    applyMove(state, TwoSolver.Move.R); // R
    applyMove(state, TwoSolver.Move.F); // F
    applyMove(state, TwoSolver.Move.R); // R2
    applyMove(state, TwoSolver.Move.R);
    applyMove(state, TwoSolver.Move.U); // U
    Assert.assertTrue(Arrays.equals(new byte[] { 2, 0, 1, 5, 6, 3, 4 }, state.permutations));
    Assert.assertTrue(Arrays.equals(new byte[] { 2, 2, 1, 0, 2, 1, 1 }, state.orientations));
  }

  @Test
  public void testTransit() {
    TwoSolver.genTables();

    // Permutation
    short[][] transitPerm = TwoSolver.transitPerm;
    for (int i = 0; i < transitPerm.length; i++) {
      for (int j = 0; j < transitPerm[i].length; j++) {
        short val = transitPerm[i][j];
        Assert.assertTrue(val >= 0 && val < TwoSolver.N_PERM);
      }
    }

    Assert.assertEquals(IndexConvertor.packPermutation(new byte[] { 1, 2, 3, 0, 4, 5, 6 }), transitPerm[0][0]); // U
    Assert.assertEquals(IndexConvertor.packPermutation(new byte[] { 0, 1, 5, 2, 4, 6, 3 }), transitPerm[0][1]); // R
    Assert.assertEquals(IndexConvertor.packPermutation(new byte[] { 0, 4, 1, 3, 5, 2, 6 }), transitPerm[0][2]); // F
    Assert.assertEquals(0, transitPerm[IndexConvertor.packPermutation(new byte[] { 0, 2, 5, 3, 1, 4, 6 })][2]); // F', F
    Assert.assertEquals(IndexConvertor.packPermutation(new byte[] { 6, 5, 4, 1, 2, 3, 0 }),
        transitPerm[IndexConvertor.packPermutation(new byte[] { 1, 6, 5, 4, 2, 3, 0 })][0]); // after R U F2 R, U

    // Orientation
    short[][] transitOrient = TwoSolver.transitOrient;
    for (int i = 0; i < transitOrient.length; i++) {
      for (int j = 0; j < transitOrient[i].length; j++) {
        short val = transitOrient[i][j];
        Assert.assertTrue(val >= 0 && val < TwoSolver.N_ORIENT);
      }
    }
    Assert.assertEquals(0, transitOrient[0][0]);
    Assert.assertEquals(IndexConvertor.packOrientation(new byte[] { 0, 0, 2, 1, 0, 1, 2 }, 3), transitOrient[0][1]); // R
    Assert.assertEquals(IndexConvertor.packOrientation(new byte[] { 0, 2, 1, 0, 1, 2, 0 }, 3), transitOrient[0][2]); // F
    Assert.assertEquals(0, transitOrient[IndexConvertor.packOrientation(new byte[] { 0, 2, 1, 0, 1, 2, 0 }, 3)][2]); // F', F
    Assert.assertEquals(IndexConvertor.packOrientation(new byte[] { 1, 1, 1, 0, 1, 0, 2 }, 3),
        transitOrient[IndexConvertor.packOrientation(new byte[] { 0, 1, 1, 1, 1, 0, 2 }, 3)][0]); // after R U F2 R, U

  }

  @Test
  public void testPruning() {
    TwoSolver.genTables();

    // Permutation
    byte[] pruningPerm = TwoSolver.pruningPerm;
    for (int i = 0; i < pruningPerm.length; i++) {
      Assert.assertTrue(pruningPerm[i] >= 0);
    }
    Assert.assertEquals(0, pruningPerm[0]);
    Assert.assertEquals(1, pruningPerm[IndexConvertor.packPermutation(new byte[] { 0, 1, 5, 2, 4, 6, 3 })]); // R
    Assert.assertEquals(1, pruningPerm[IndexConvertor.packPermutation(new byte[] { 0, 4, 1, 3, 5, 2, 6 })]); // F
    Assert.assertEquals(2, pruningPerm[IndexConvertor.packPermutation(new byte[] { 0, 4, 2, 1, 5, 6, 3 })]); // F R
    Assert.assertEquals(3, pruningPerm[IndexConvertor.packPermutation(new byte[] { 4, 2, 1, 0, 5, 6, 3 })]); // F R U
    Assert.assertEquals(3, pruningPerm[IndexConvertor.packPermutation(new byte[] { 2, 1, 0, 4, 5, 6, 3 })]); // F R U2

    // Orientation
    byte[] pruningOrient = TwoSolver.pruningOrient;
    for (int i = 0; i < pruningOrient.length; i++) {
      Assert.assertTrue(pruningOrient[i] >= 0);
    }
    Assert.assertEquals(0, pruningOrient[0]);
    Assert.assertEquals(1, pruningOrient[IndexConvertor.packOrientation(new byte[] { 0, 0, 2, 1, 0, 1, 2 }, 3)]); // R
    Assert.assertEquals(1, pruningOrient[IndexConvertor.packOrientation(new byte[] { 0, 2, 1, 0, 1, 2, 0 }, 3)]); // F
    Assert.assertEquals(2, pruningOrient[IndexConvertor.packOrientation(new byte[] { 0, 2, 1, 2, 1, 1, 2 }, 3)]); // F R
    Assert.assertEquals(3, pruningOrient[IndexConvertor.packOrientation(new byte[] { 2, 1, 2, 0, 1, 1, 2 }, 3)]); // F R U
  }

  @Test
  public void testTableSizes() {
    TwoSolver.genTables();
    System.out.println("transitPerm: " + getSize(TwoSolver.transitPerm));
    System.out.println("transitOrient: " + getSize(TwoSolver.transitOrient));
    System.out.println("pruningPerm: " + getSize(TwoSolver.pruningPerm));
    System.out.println("pruningOrient: " + getSize(TwoSolver.pruningOrient));
  }

  /** The whole point of the generator: exactly the asked length, and it really does solve. */
  @Test
  public void testGeneratorIsExactlyAsLongAsAsked() {
    TwoSolver.genTables();
    TwoSolver solver = new TwoSolver();
    java.util.Random random = new java.util.Random(7);
    for (int i = 0; i < 200; i++) {
      TwoCubeState cubeState = new TwoCubeState();
      cubeState.permutations = new byte[7];
      IndexConvertor.unpackPermutation(random.nextInt(TwoSolver.N_PERM), cubeState.permutations);
      cubeState.orientations = new byte[7];
      IndexConvertor.unpackOrientation(random.nextInt(TwoSolver.N_ORIENT), cubeState.orientations, (byte) 3);

      String[] generator = solver.getGenerator(cubeState, RSTwoScrambler.SCRAMBLE_LENGTH);
      Assert.assertNotNull("No generator of the scramble length for state " + i, generator);
      Assert.assertEquals(RSTwoScrambler.SCRAMBLE_LENGTH, generator.length);

      for (String name : generator) {
        applyNamedMove(cubeState, name);
      }
      Assert.assertTrue("Generator left the cube unsolved: " + Arrays.toString(generator),
          isSolved(cubeState));
    }
  }

  /** Only the three base moves carry a permutation, so a half turn is that move applied twice. */
  private void applyNamedMove(TwoCubeState state, String name) {
    TwoSolver.Move base = TwoSolver.Move.valueOf(name.substring(0, 1));
    int turns = name.endsWith("2") ? 2 : name.endsWith("'") ? 3 : 1;
    for (int i = 0; i < turns; i++) {
      applyMove(state, base);
    }
  }

  private boolean isSolved(TwoCubeState state) {
    return IndexConvertor.packPermutation(state.permutations) == 0
        && IndexConvertor.packOrientation(state.orientations, 3) == 0;
  }

  private void applyMove(TwoCubeState state, TwoSolver.Move move) {
    state.permutations = StateTables.getPermResult(state.permutations, move.corPerm);
    state.orientations = StateTables.getOrientResult(state.orientations, move.corPerm, move.corOrient, 3);
  }

  private int getSize(Object obj) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(obj);
      oos.close();
      return baos.size();
    } catch (IOException e) {
      e.printStackTrace();
      Assert.fail();
    }
    return -1;
  }

}
