package com.cube.nanotimer.scrambler.randomstate;

import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.ScrambleType;

public class RandomStateGenEvent {

  public enum State { PREPARING, GENERATING, IDLE, STOPPING, GENERATED }
  public enum GenerationLaunch { AUTO, PLUGGED }

  private State state;
  private CubeType cubeType;
  private ScrambleType scrambleType;
  private GenerationLaunch generationLaunch;
  private int curScramble;
  private int totToGen;

  public RandomStateGenEvent(State state, GenerationLaunch generationLaunch, int curScramble, int totToGen) {
    this(state, null, null, generationLaunch, curScramble, totToGen);
  }

  public RandomStateGenEvent(State state, CubeType cubeType, ScrambleType scrambleType, GenerationLaunch generationLaunch, int curScramble, int totToGen) {
    this.state = state;
    this.cubeType = cubeType;
    this.scrambleType = scrambleType;
    this.generationLaunch = generationLaunch;
    this.curScramble = curScramble;
    this.totToGen = totToGen;
  }

  public State getState() {
    return state;
  }

  public CubeType getCubeType() {
    return cubeType;
  }

  public ScrambleType getScrambleType() {
    return scrambleType;
  }

  public GenerationLaunch getGenerationLaunch() {
    return generationLaunch;
  }

  public String getCubeTypeName() {
    return cubeType == null ? "" : cubeType.getName();
  }

  public int getCurScramble() {
    return curScramble;
  }

  public int getTotalToGenerate() {
    return totToGen;
  }

}
