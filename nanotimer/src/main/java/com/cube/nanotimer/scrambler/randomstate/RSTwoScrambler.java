package com.cube.nanotimer.scrambler.randomstate;

import android.content.Context;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.vo.TwoCubeState;

import java.util.Random;

public class RSTwoScrambler implements RSScrambler {

  /** The length of every official WCA 2x2 scramble, shared by the random-move fallback. */
  public static final int SCRAMBLE_LENGTH = 11;

  private TwoSolver twoSolver = new TwoSolver();

  /**
   * A scramble of exactly the configured length, like the official ones.
   *
   * <p>The state is drawn first and uniformly, so the scramble is as random as a shortest one
   * would be. It is just written out in more moves than it strictly needs, which is what keeps an
   * easy state from announcing itself as a four-move scramble.
   */
  @Override
  public String[] getNewScramble(ScrambleConfig config) {
    int length = (config != null && config.getMaxLength() > 0) ? config.getMaxLength() : SCRAMBLE_LENGTH;
    String[] generator;
    do { // a state with no generator of exactly that length is answered with another state
      generator = twoSolver.getGenerator(getRandomState(), length);
    } while (generator == null && !twoSolver.isStopped());
    return Utils.invertMoves(generator);
  }

  @Override
  public void prepareGenTables(Context context) {
  }

  @Override
  public void genTables() {
    TwoSolver.genTables();
  }

  @Override
  public void stop() {
    twoSolver.stop();
  }

  private TwoCubeState getRandomState() {
    TwoCubeState cubeState;
    Random r = Utils.getRandom();

    byte[] state;

    cubeState = new TwoCubeState();

    state = new byte[7];
    IndexConvertor.unpackPermutation(r.nextInt(TwoSolver.N_PERM), state);
    cubeState.permutations = state;

    state = new byte[7];
    IndexConvertor.unpackOrientation(r.nextInt(TwoSolver.N_ORIENT), state, (byte) 3);
    cubeState.orientations = state;

    return cubeState;
  }

}
