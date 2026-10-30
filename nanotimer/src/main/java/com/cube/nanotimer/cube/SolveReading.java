package com.cube.nanotimer.cube;

import com.cube.nanotimer.session.MethodStatistics;
import com.cube.nanotimer.smartcube.step.AlgorithmExecution;
import com.cube.nanotimer.smartcube.step.AlgorithmForm;
import com.cube.nanotimer.smartcube.step.CaseAlgorithms;
import com.cube.nanotimer.vo.SolveStep;
import com.cube.nanotimer.vo.SolveTime;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What one solve turned for each of its cases and each of its steps, read out of the solve and
 * cheap to keep.
 *
 * <p>Reading it is not cheap: nothing stores a case's moves, so the scramble has to be solved again
 * and every execution set against the algorithms its case is taught with. Measured over 148 solves,
 * the replay is a fifth of that and the matching the other four fifths, and the same window is read
 * again on every Analysis open and every plan. The answer for a solve that has happened cannot
 * change, so it is read once and kept ({@code SolveReadingStore}).
 *
 * <p><b>Versioned, because the reading can change and the rows cannot.</b> A change to how a solve
 * is stepped, to the algorithm lists, or to what counts as one of them makes every stored row a lie,
 * and rows written by another version are ignored rather than trusted. Bump {@link #VERSION} for any
 * of those.
 *
 * <p>What is kept is deliberately not one screen's answer. Both readers want the same executions
 * and fold them differently, so the fold stays with them: a last layer step keeps whether it was
 * one of the usual algorithms <em>and</em> how many algorithms it took, rather than the one figure
 * {@link AlgorithmFiguresReader} makes of the two.
 */
public final class SolveReading {

  /**
   * Bump on any change to how a solve is read: the stepping, the algorithm lists, or what counts
   * as one of them. Rows written by another version are read again rather than believed. Version 2
   * added each step's turning, version 3 reads the standard OLL 41 and 42 as one algorithm,
   * version 4 makes stall_ms all the time not turning and adds stop_ms, the long pauses in it.
   */
  public static final int VERSION = 4;

  /** The F2L step, whose parts are read a pair at a time where a last layer step is read whole. */
  public static final String F2L = "f2l";

  private final List<Execution> executions;
  private final List<Turning> turnings;
  private final boolean whole;

  /**
   * @param turnings each step as it was turned, in solving order
   * @param whole whether every step of the solve was read, so its turnings add up to the solve;
   *     a solve stopped inside a step leaves that step out
   */
  public SolveReading(List<Execution> executions, List<Turning> turnings, boolean whole) {
    this.executions = Collections.unmodifiableList(new ArrayList<Execution>(executions));
    this.turnings = Collections.unmodifiableList(new ArrayList<Turning>(turnings));
    this.whole = whole;
  }

  /** Executions alone, with no step read. */
  public SolveReading(List<Execution> executions) {
    this(executions, Collections.<Turning>emptyList(), false);
  }

  public List<Execution> getExecutions() {
    return executions;
  }

  public List<Turning> getTurnings() {
    return turnings;
  }

  public boolean isWhole() {
    return whole;
  }

  public boolean isEmpty() {
    return executions.isEmpty() && turnings.isEmpty();
  }

  /**
   * How one step of a solve was turned, whatever case it was: how many moves, how long the solver
   * was not turning between them, and how many of those pauses were stops and what they came to
   * ({@link SolveSolution.Step#getPauseMs}, {@link SolveSolution.Step#getStops}).
   */
  public static final class Turning {

    private final String code;
    private final long timeMs;
    private final int moves;
    private final int stalls;
    private final long stallMs;
    private final long stopMs;

    /**
     * @param code the step as the breakdown names it, {@code cross}, {@code f2l}, {@code pll_ja} or
     *     {@code oll_skip}, so a skip can be told from a step that was turned
     */
    public Turning(String code, long timeMs, int moves, int stalls, long stallMs, long stopMs) {
      this.code = code;
      this.timeMs = timeMs;
      this.moves = moves;
      this.stalls = stalls;
      this.stallMs = stallMs;
      this.stopMs = stopMs;
    }

    public String getCode() {
      return code;
    }

    public long getTimeMs() {
      return timeMs;
    }

    public int getMoves() {
      return moves;
    }

    public int getStalls() {
      return stalls;
    }

    public long getStallMs() {
      return stallMs;
    }

    public long getStopMs() {
      return stopMs;
    }
  }

  /** One case execution of a solve. */
  public static final class Execution {

    private final String family;
    private final String caseCode;
    private final long timeMs;
    private final int moves;
    private final int usualMoves;
    private final boolean listed;
    private final int algorithms;

    /**
     * @param family the step it belongs to, {@code f2l} / {@code oll} / {@code pll}
     * @param caseCode the case turned, or null for an F2L pair whose placement names no case, whose
     *     piece was stuck elsewhere, or whose moves no algorithm can be told from: it still counts
     *     as a pair that happened, which is why it is kept rather than dropped
     * @param moves how many were turned, {@code usualMoves} how many the shortest listed algorithm
     *     takes, both 0 where there was nothing to compare
     * @param listed whether it went in with one of the case's listed algorithms
     * @param algorithms how many algorithms the step took, 1 for a pair and 2 for a two-looked last
     *     layer; kept apart from {@code listed} because only some readers count a two-look against
     *     the solver
     */
    public Execution(String family, String caseCode, long timeMs, int moves, int usualMoves,
        boolean listed, int algorithms) {
      this.family = family;
      this.caseCode = caseCode;
      this.timeMs = timeMs;
      this.moves = moves;
      this.usualMoves = usualMoves;
      this.listed = listed;
      this.algorithms = algorithms;
    }

    public String getFamily() {
      return family;
    }

    public String getCaseCode() {
      return caseCode;
    }

    public long getTimeMs() {
      return timeMs;
    }

    public int getMoves() {
      return moves;
    }

    public int getUsualMoves() {
      return usualMoves;
    }

    public boolean isListed() {
      return listed;
    }

    public int getAlgorithms() {
      return algorithms;
    }

    /** Whether there is anything to set against the case's algorithms at all. */
    public boolean isRead() {
      return caseCode != null && usualMoves > 0;
    }
  }

  /**
   * Reads a solve, which means solving its scramble again. The expensive path, and the one a kept
   * reading exists to skip.
   *
   * @return its executions and steps in solving order, empty for a solve that is not a readable
   *     CFOP one
   */
  public static SolveReading read(SolveTime solve) {
    final List<Execution> executions = new ArrayList<Execution>();
    final List<Turning> turnings = new ArrayList<Turning>();
    boolean whole = CaseExecutions.readSteps(solve, new CaseExecutions.StepReader() {
      @Override
      public void read(SolveStep step, SolveSolution.Step turned) {
        if (!SolveBreakdown.UNFINISHED_STEP.equals(step.getName())) { // after a stop inside PLL
          turnings.add(new Turning(step.getName(), step.getTotalMs(),
              turned.getMoveCount(), turned.getStops(), turned.getPauseMs(),
              turned.getStopMs()));
        }
        if (F2L.equals(step.getName())) {
          readPairs(step, turned, executions);
        } else {
          readLastLayer(step, turned, executions);
        }
      }
    });
    return new SolveReading(executions, turnings, whole);
  }

  private static void readPairs(SolveStep step, SolveSolution.Step turned,
      List<Execution> executions) {
    List<SolveStep> parts = step.getSubSteps();
    for (int i = 0; i < parts.size(); i++) {
      long timeMs = parts.get(i).getTotalMs();
      if (i >= turned.getGroups().size()) {
        executions.add(unread(F2L, timeMs)); // no moves to set against
        continue;
      }
      String moves = turned.getPartMoves(i);
      if (moves.isEmpty()) {
        continue; // a skip: nothing turned, so nothing over the usual either
      }
      CaseExecutions.Pair pair = CaseExecutions.pairOf(parts.get(i), moves);
      executions.add(pair == null ? unread(F2L, timeMs)
          : new Execution(F2L, pair.getCaseCode(), timeMs,
              pair.getExecution().getMoves(), pair.getExecution().getUsualMoves(),
              pair.isListed(), 1));
    }
  }

  private static void readLastLayer(SolveStep step, SolveSolution.Step turned,
      List<Execution> executions) {
    String caseCode = CaseExecutions.caseOfStep(step.getName());
    if (caseCode == null) {
      return;
    }
    // Every algorithm the step took, run together, so a two-look can be measured against the one.
    StringBuilder moves = new StringBuilder();
    for (int i = 0; i < turned.getGroups().size(); i++) {
      moves.append(' ').append(turned.getPartMoves(i));
    }
    String turnedMoves = moves.toString().trim();
    if (turnedMoves.isEmpty() || AlgorithmForm.key(turnedMoves) == null) {
      return; // nothing turned, or nothing that can be read: an older execution may say more
    }
    AlgorithmExecution read = CaseAlgorithms.read(caseCode, turnedMoves);
    executions.add(new Execution(MethodStatistics.familyOf(caseCode),
        caseCode, step.getTotalMs(), read.getMoves(), read.getUsualMoves(), !read.isUnusual(),
        Math.max(1, step.getSubSteps().size())));
  }

  /** A pair that happened but can be set against nothing: it still counts as a pair. */
  private static Execution unread(String family, long timeMs) {
    return new Execution(family, null, timeMs, 0, 0, false, 1);
  }

  public static String toJson(SolveReading reading) {
    try {
      JSONArray executions = new JSONArray();
      for (Execution execution : reading.executions) {
        JSONObject json = new JSONObject();
        json.put("family", execution.family);
        if (execution.caseCode != null) {
          json.put("case", execution.caseCode);
        }
        json.put("time_ms", execution.timeMs);
        json.put("moves", execution.moves);
        json.put("usual_moves", execution.usualMoves);
        json.put("listed", execution.listed);
        json.put("algorithms", execution.algorithms);
        executions.put(json);
      }
      JSONArray steps = new JSONArray();
      for (Turning turning : reading.turnings) {
        JSONObject json = new JSONObject();
        json.put("code", turning.code);
        json.put("time_ms", turning.timeMs);
        json.put("moves", turning.moves);
        json.put("stalls", turning.stalls);
        json.put("stall_ms", turning.stallMs);
        json.put("stop_ms", turning.stopMs);
        steps.put(json);
      }
      JSONObject json = new JSONObject();
      json.put("executions", executions);
      json.put("steps", steps);
      json.put("whole", reading.whole);
      return json.toString();
    } catch (JSONException e) {
      throw new IllegalStateException("Cannot write a solve reading", e);
    }
  }

  /**
   * @return what was kept, or null where the text cannot be read, which is answered by reading the
   *     solve again rather than by failing: a reading is a cache and never the only copy
   */
  public static SolveReading parse(String text) {
    List<Execution> executions = new ArrayList<Execution>();
    List<Turning> turnings = new ArrayList<Turning>();
    try {
      JSONObject reading = new JSONObject(text);
      JSONArray array = reading.getJSONArray("executions");
      for (int i = 0; i < array.length(); i++) {
        JSONObject json = array.getJSONObject(i);
        executions.add(new Execution(json.getString("family"), json.optString("case", null),
            json.getLong("time_ms"), json.getInt("moves"), json.getInt("usual_moves"),
            json.getBoolean("listed"), json.getInt("algorithms")));
      }
      array = reading.getJSONArray("steps");
      for (int i = 0; i < array.length(); i++) {
        JSONObject json = array.getJSONObject(i);
        turnings.add(new Turning(json.getString("code"), json.getLong("time_ms"),
            json.getInt("moves"), json.getInt("stalls"), json.getLong("stall_ms"),
            json.getLong("stop_ms")));
      }
      return new SolveReading(executions, turnings, reading.getBoolean("whole"));
    } catch (JSONException e) {
      return null;
    }
  }

}
