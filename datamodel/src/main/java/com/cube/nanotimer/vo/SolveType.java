package com.cube.nanotimer.vo;

import java.io.Serializable;
import java.util.Arrays;

public class SolveType implements Serializable, NameHolder {

  private int id;
  private String name;
  private int cubeTypeId;
  private SolveTypeStep[] steps = new SolveTypeStep[0];
  private ScrambleType scrambleType;
  private boolean blind = false;
  private boolean inspection = true;
  private CubeMethod method; // null to follow the preferred method rather than name one here
  private TimerQuickAction quickAction; // null to follow the default rather than freeze a copy of it

  public SolveType(String name, boolean blind, ScrambleType scrambleType, int cubeTypeId) {
    this.name = name;
    this.blind = blind;
    this.scrambleType = scrambleType;
    this.cubeTypeId = cubeTypeId;
    this.inspection = !blind;
  }

  public SolveType(int id, String name, boolean blind, ScrambleType scrambleType, int cubeTypeId) {
    this(name, blind, scrambleType, cubeTypeId);
    this.id = id;
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  @Override
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getCubeTypeId() {
    return cubeTypeId;
  }

  public ScrambleType getScrambleType() {
    return scrambleType;
  }

  public boolean isBlind() {
    return blind;
  }

  /** Whether the timer inspects before this solve type's solves. Always false for a blind one. */
  public boolean hasInspection() {
    return inspection && !blind;
  }

  public void setInspection(boolean inspection) {
    this.inspection = inspection;
  }

  /**
   * The method this solve type overrides the preferred one with, or null to follow it. Null is the
   * normal answer: the preference is the one place the method is kept, and a type only names its own
   * when it is solved differently from the rest.
   *
   * <p>This is the stored answer, not the method the solves are read as — that one is never null and
   * has to account for the preference and for the blind flag, which no module below the app can see.
   */
  public CubeMethod getMethodOverride() {
    return method;
  }

  public void setMethod(CubeMethod method) {
    this.method = method;
  }

  /** The action this solve type puts in the timer's action bar, the default included. */
  public TimerQuickAction getQuickAction() {
    return quickAction != null ? quickAction : TimerQuickAction.getDefault(blind);
  }

  /**
   * The action this solve type overrides the default one with, or null to follow it. Null is the
   * normal answer, and what a type that was never asked the question holds: the default then lives
   * in {@link TimerQuickAction#getDefault} alone, and moving it moves every type that follows it.
   */
  public TimerQuickAction getQuickActionOverride() {
    return quickAction;
  }

  public void setQuickAction(TimerQuickAction quickAction) {
    this.quickAction = quickAction;
  }

  public SolveTypeStep[] getSteps() {
    return steps;
  }

  public void setSteps(SolveTypeStep[] steps) {
    this.steps = steps;
  }

  public boolean hasSteps() {
    return steps != null && steps.length > 0;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SolveType)) return false;

    SolveType solveType = (SolveType) o;

    if (id != solveType.id) return false;
    if (cubeTypeId != solveType.cubeTypeId) return false;
    if (blind != solveType.blind) return false;
    if (inspection != solveType.inspection) return false;
    if (method != solveType.method) return false;
    if (!name.equals(solveType.name)) return false;
    if (quickAction != solveType.quickAction) return false;
    // Probably incorrect - comparing Object[] arrays with Arrays.equals
    if (!Arrays.equals(steps, solveType.steps)) return false;
    return scrambleType == solveType.scrambleType;
  }

  @Override
  public int hashCode() {
    int result = id;
    result = 31 * result + name.hashCode();
    result = 31 * result + cubeTypeId;
    result = 31 * result + (steps != null ? Arrays.hashCode(steps) : 0);
    result = 31 * result + (scrambleType != null ? scrambleType.hashCode() : 0);
    result = 31 * result + (blind ? 1 : 0);
    result = 31 * result + (inspection ? 1 : 0);
    result = 31 * result + (method != null ? method.hashCode() : 0);
    result = 31 * result + (quickAction != null ? quickAction.hashCode() : 0);
    return result;
  }
}
