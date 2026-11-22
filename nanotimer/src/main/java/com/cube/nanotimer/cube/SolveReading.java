package com.cube.nanotimer.cube;

import com.cube.nanotimer.coach.CrossTurn;
import com.cube.nanotimer.coach.PairRotation;
import com.cube.nanotimer.session.MethodStatistics;
import com.cube.nanotimer.smartcube.step.AlgorithmExecution;
import com.cube.nanotimer.smartcube.step.AlgorithmForm;
import com.cube.nanotimer.smartcube.step.CaseAlgorithms;
import com.cube.nanotimer.vo.CubeMethod;
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
   * version 4 makes stall_ms all the time not turning and adds stop_ms, the long pauses in it,
   * version 5 the F2L rotations of a gyro solve, version 6 reads them again now that the solves
   * come with their gyro tracks (version 5 rows hold none), version 7 stops counting a last layer
   * false start as one of the step's algorithms, version 8 drops pauses under 250 ms, version 9
   * where each pair's slot sat in the grip, version 10 the cross against its optimal, version 11
   * the pause before the final AUF, version 12 times a PLL without its final AUF, version 13 reads
   * the halves of any listed algorithm as one, whatever the pause, version 14 a Roux solve's CMLL,
   * version 15 marks each pair turned with moves undone or stopped inside.
   */
  public static final int VERSION = 15;

  static final String RESTART_PART_SUFFIX = "restart";

  /** The F2L step, whose parts are read a pair at a time where a last layer step is read whole. */
  public static final String F2L = "f2l";

  private final List<Execution> executions;
  private final List<Turning> turnings;
  private final boolean whole;
  private final List<PairRotation> rotations;
  private final CrossTurn cross;
  private final Long finalAufMs;
  /** Read as Roux, its steps alone; a CFOP row carries no mark, so rows from before still read. */
  private boolean roux;

  /**
   * @param turnings each step as it was turned, in solving order
   * @param whole whether every step of the solve was read, so its turnings add up to the solve;
   *     a solve stopped inside a step leaves that step out
   */
  public SolveReading(List<Execution> executions, List<Turning> turnings, boolean whole) {
    this(executions, turnings, whole, Collections.<PairRotation>emptyList());
  }

  /** @param rotations each F2L pair's rotations, empty for a solve with no gyro track */
  public SolveReading(List<Execution> executions, List<Turning> turnings, boolean whole,
      List<PairRotation> rotations) {
    this(executions, turnings, whole, rotations, null);
  }

  /** @param cross the cross against its optimal, null where none was read */
  public SolveReading(List<Execution> executions, List<Turning> turnings, boolean whole,
      List<PairRotation> rotations, CrossTurn cross) {
    this(executions, turnings, whole, rotations, cross, null);
  }

  /** @param finalAufMs the pause before the final AUF ({@link #getFinalAufMs}) */
  public SolveReading(List<Execution> executions, List<Turning> turnings, boolean whole,
      List<PairRotation> rotations, CrossTurn cross, Long finalAufMs) {
    this.finalAufMs = finalAufMs;
    this.cross = cross;
    this.executions = Collections.unmodifiableList(new ArrayList<Execution>(executions));
    this.turnings = Collections.unmodifiableList(new ArrayList<Turning>(turnings));
    this.whole = whole;
    this.rotations = Collections.unmodifiableList(new ArrayList<PairRotation>(rotations));
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

  /** Each F2L pair's rotations ({@link F2lRotations}), empty where the solve has no gyro track. */
  public List<PairRotation> getRotations() {
    return rotations;
  }

  /** The cross against the fewest moves it could take ({@link CrossMoves}), or null. */
  public CrossTurn getCross() {
    return cross;
  }

  /**
   * The pause before the final AUF ({@link FinalAuf}): -1 for a PLL with no AUF after it, null where
   * no PLL algorithm was read.
   */
  public Long getFinalAufMs() {
    return finalAufMs;
  }

  /**
   * Whether this was read as the method the solve is read as now. Changing a solve type's method
   * reads its solves again but leaves their kept rows, which then belong to the other method.
   */
  public boolean isReadAs(CubeMethod method) {
    return roux == (method == CubeMethod.ROUX);
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
    private boolean undone;
    private boolean stopped;

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

    /** An F2L pair where side turns were messed up and taken back ({@link #UNDONE_TURNS}). */
    public boolean isUndone() {
      return undone;
    }

    /** An F2L pair with a stop inside its turning, past its recognition. */
    public boolean isStopped() {
      return stopped;
    }

    Execution marked(boolean undone, boolean stopped) {
      this.undone = undone;
      this.stopped = stopped;
      return this;
    }
  }

  /** Side quarter turns taken back that make a pair's moves a mess-up rather than a regrip. */
  static final int UNDONE_TURNS = 2;

  /**
   * Reads a solve, which means solving its scramble again. The expensive path, and the one a kept
   * reading exists to skip.
   *
   * @return its executions and steps in solving order: a Roux solve's CMLL is its one execution,
   *     and a solve that is neither a readable CFOP nor Roux one has none
   */
  public static SolveReading read(SolveTime solve) {
    if (SolveTypeMethod.of(solve.getSolveType()) == CubeMethod.ROUX) {
      return readRoux(CaseExecutions.replayAs(solve, CubeMethod.ROUX));
    }
    final List<Execution> executions = new ArrayList<Execution>();
    final List<Turning> turnings = new ArrayList<Turning>();
    final CaseExecutions.Replay replay = CaseExecutions.replay(solve);
    final long[] stepStart = {0};
    boolean whole = CaseExecutions.readSteps(replay, new CaseExecutions.StepReader() {
      @Override
      public void read(SolveStep step, SolveSolution.Step turned) {
        if (!SolveBreakdown.UNFINISHED_STEP.equals(step.getName())) { // after a stop inside PLL
          turnings.add(new Turning(step.getName(), step.getTotalMs(),
              turned.getMoveCount(), turned.getStops(), turned.getPauseMs(),
              turned.getStopMs()));
        }
        if (F2L.equals(step.getName())) {
          long stopMs = 2 * SolveSolution.turnMsOf(SolveSolution.timedSolution(replay.getMoves()));
          readPairs(step, turned, executions, stepStart[0], stopMs);
        } else {
          readLastLayer(step, turned, executions);
        }
        stepStart[0] += step.getTotalMs();
      }
    });
    List<PairRotation> rotations = !whole || solve.getSmartcubeGyroTrack() == null
        ? Collections.<PairRotation>emptyList()
        : F2lRotations.read(solve.getId(), replay.getMoves(), replay.getSteps(),
            GyroTrackFormat.parse(solve.getSmartcubeGyroTrack()));
    CrossTurn cross = whole ? readCross(solve, replay) : null;
    Long finalAuf = whole ? readFinalAuf(solve, replay) : null;
    return new SolveReading(executions, turnings, whole, rotations, cross, finalAuf);
  }

  /** A Roux solve: its steps' turning and its CMLL, kept so it is replayed once. */
  private static SolveReading readRoux(CaseExecutions.Replay replay) {
    final List<Execution> executions = new ArrayList<Execution>();
    final List<Turning> turnings = new ArrayList<Turning>();
    boolean whole = CaseExecutions.readSteps(replay, new CaseExecutions.StepReader() {
      @Override
      public void read(SolveStep step, SolveSolution.Step turned) {
        if (!SolveBreakdown.UNFINISHED_STEP.equals(step.getName())) {
          turnings.add(new Turning(step.getName(), step.getTotalMs(),
              turned.getMoveCount(), turned.getStops(), turned.getPauseMs(),
              turned.getStopMs()));
        }
        readLastLayer(step, turned, executions); // only the CMLL names a case
      }
    });
    SolveReading reading = new SolveReading(executions, turnings, whole);
    reading.roux = true;
    return reading;
  }

  private static Long readFinalAuf(SolveTime solve, CaseExecutions.Replay replay) {
    try {
      return FinalAuf.read(replay.getMoves(), replay.getSteps(),
          CrossMoves.face(solve.getScramble(), replay.getMoves(), replay.getSteps()));
    } catch (RuntimeException e) {
      return null; // a scramble that cannot be replayed: the rest of the reading stands
    }
  }

  private static CrossTurn readCross(SolveTime solve, CaseExecutions.Replay replay) {
    try {
      return CrossMoves.read(solve.getId(), solve.getScramble(), replay.getMoves(),
          replay.getSteps());
    } catch (RuntimeException e) {
      return null; // a scramble the cross solver cannot read: the rest of the reading stands
    }
  }

  /**
   * @param startMs when the step began in the solve, which the pause spans are timed from
   * @param stopMs how long a pause has to be to count as a stop: twice the solve's turn time
   */
  private static void readPairs(SolveStep step, SolveSolution.Step turned,
      List<Execution> executions, long startMs, long stopMs) {
    List<SolveStep> parts = step.getSubSteps();
    long partStart = startMs;
    for (int i = 0; i < parts.size(); i++) {
      long timeMs = parts.get(i).getTotalMs();
      long turningFrom = partStart + parts.get(i).getRecognitionMs();
      partStart += timeMs;
      if (i >= turned.getGroups().size()) {
        executions.add(unread(F2L, timeMs)); // no moves to set against
        continue;
      }
      String moves = turned.getPartMoves(i);
      if (moves.isEmpty()) {
        continue; // a skip: nothing turned, so nothing over the usual either
      }
      CaseExecutions.Pair pair = CaseExecutions.pairOf(parts.get(i), moves);
      boolean stopped = false;
      for (long[] span : turned.getPauseSpans()) {
        stopped |= span[0] >= turningFrom && span[0] < partStart && span[1] - span[0] > stopMs;
      }
      executions.add((pair == null ? unread(F2L, timeMs)
          : new Execution(F2L, pair.getCaseCode(), timeMs,
              pair.getExecution().getMoves(), pair.getExecution().getUsualMoves(),
              pair.isListed(), 1)).marked(undoneTurns(moves) >= UNDONE_TURNS, stopped));
    }
  }

  /** Side-face quarter turns a later turn of the same face cancelled; U/D and rotations aside. */
  static int undoneTurns(String moves) {
    List<String> faces = new ArrayList<String>();
    List<int[]> turns = new ArrayList<int[]>(); // net quarter turns, quarter turns spent
    int undone = 0;
    for (String move : moves.trim().split("\\s+")) {
      if (move.isEmpty()) {
        continue;
      }
      String face = move.replaceAll("[2']", "");
      int amount = move.contains("2") ? 2 : move.endsWith("'") ? 3 : 1;
      int top = faces.size() - 1;
      if (top >= 0 && faces.get(top).equals(face)) {
        int[] sofar = turns.get(top);
        int net = (sofar[0] + amount) % 4;
        int spent = sofar[1] + Math.min(amount, 4 - amount);
        if (net == 0) {
          faces.remove(top);
          turns.remove(top);
          if (!face.matches("[UDudExyz]")) {
            undone += spent;
          }
        } else {
          turns.set(top, new int[] {net, spent});
        }
      } else {
        faces.add(face);
        turns.add(new int[] {amount, Math.min(amount, 4 - amount)});
      }
    }
    return undone;
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
        caseCode, step.getCaseMs(), read.getMoves(), read.getUsualMoves(), !read.isUnusual(),
        Math.max(1, algorithmsOf(step))));
  }

  /** The parts that were algorithms: a false start ("ollrestart", "pllrestart") is not one. */
  private static int algorithmsOf(SolveStep step) {
    int algorithms = 0;
    for (SolveStep part : step.getSubSteps()) {
      if (part.getName() == null || !part.getName().endsWith(RESTART_PART_SUFFIX)) {
        algorithms++;
      }
    }
    return algorithms;
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
        if (execution.undone) {
          json.put("undone", true);
        }
        if (execution.stopped) {
          json.put("stopped", true);
        }
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
      if (!reading.rotations.isEmpty()) {
        JSONArray rotations = new JSONArray();
        for (PairRotation pair : reading.rotations) {
          JSONObject one = new JSONObject();
          if (pair.getCaseCode() != null) {
            one.put("case", pair.getCaseCode());
          }
          one.put("basic", pair.isBasic());
          if (pair.getSlot() != null) {
            one.put("slot", pair.getSlot());
          }
          one.put("rotations", pair.getRotations());
          one.put("start", gaps(pair.getStart()));
          one.put("inside", gaps(pair.getInside()));
          rotations.put(one);
        }
        json.put("rotations", rotations);
      }
      if (reading.cross != null) {
        json.put("cross", new JSONArray().put(reading.cross.getOptimalMoves())
            .put(reading.cross.getTurnedMoves()).put(reading.cross.getSlotsWithCross()));
      }
      if (reading.finalAufMs != null) {
        json.put("final_auf", reading.finalAufMs.longValue());
      }
      if (reading.roux) {
        json.put("method", CubeMethod.ROUX.getCode());
      }
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
    return parse(text, 0);
  }

  /** As above, for the solve the reading was kept for, whose id its pairs' rotations carry. */
  public static SolveReading parse(String text, long solveId) {
    List<Execution> executions = new ArrayList<Execution>();
    List<Turning> turnings = new ArrayList<Turning>();
    try {
      JSONObject reading = new JSONObject(text);
      JSONArray array = reading.getJSONArray("executions");
      for (int i = 0; i < array.length(); i++) {
        JSONObject json = array.getJSONObject(i);
        executions.add(new Execution(json.getString("family"), json.optString("case", null),
            json.getLong("time_ms"), json.getInt("moves"), json.getInt("usual_moves"),
            json.getBoolean("listed"), json.getInt("algorithms"))
            .marked(json.optBoolean("undone"), json.optBoolean("stopped")));
      }
      array = reading.getJSONArray("steps");
      for (int i = 0; i < array.length(); i++) {
        JSONObject json = array.getJSONObject(i);
        turnings.add(new Turning(json.getString("code"), json.getLong("time_ms"),
            json.getInt("moves"), json.getInt("stalls"), json.getLong("stall_ms"),
            json.getLong("stop_ms")));
      }
      List<PairRotation> rotations = new ArrayList<PairRotation>();
      array = reading.optJSONArray("rotations");
      for (int i = 0; array != null && i < array.length(); i++) {
        JSONObject json = array.getJSONObject(i);
        rotations.add(new PairRotation(solveId, json.optString("case", null),
            json.getBoolean("basic"), json.optString("slot", null), json.getInt("rotations"),
            gaps(json.getJSONArray("start")), gaps(json.getJSONArray("inside"))));
      }
      JSONArray cross = reading.optJSONArray("cross");
      SolveReading parsed = new SolveReading(executions, turnings, reading.getBoolean("whole"),
          rotations, cross == null ? null
              : new CrossTurn(solveId, cross.getInt(0), cross.getInt(1), cross.getInt(2)),
          reading.has("final_auf") ? Long.valueOf(reading.getLong("final_auf")) : null);
      parsed.roux = CubeMethod.ROUX.getCode().equals(reading.optString("method", null));
      return parsed;
    } catch (JSONException e) {
      return null;
    }
  }

  private static JSONArray gaps(PairRotation.Gaps gaps) {
    return new JSONArray().put(gaps.getRotated()).put(gaps.getRotatedMs()).put(gaps.getPlain())
        .put(gaps.getPlainMs());
  }

  private static PairRotation.Gaps gaps(JSONArray json) throws JSONException {
    return new PairRotation.Gaps(json.getInt(0), json.getLong(1), json.getInt(2), json.getLong(3));
  }

}
