package com.cube.nanotimer.drill;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * One drill: something to practise, a way of putting it in front of the user, and something to beat.
 * For most types that something is a set of named cases; a cross is a whole scramble that no
 * vocabulary names, so it carries a face instead of a list, and a look-ahead rep is two pairs dealt
 * together, which no case names either.
 *
 * <p>This is the contract between the app and whatever prescribes a drill, so it is written to be
 * read by an app that has never heard of the sender and by a sender that has never heard of this
 * app. It carries no reasoning: why these cases were picked belongs to whoever picked them, and a
 * drill has to stand on its own because the app writes its own.
 *
 * <p>The version is the drill's own, not the version of any message it arrived in, because a drill
 * the user wrote here never arrived in one and still has to be readable after an update. A drill
 * from a newer version is refused rather than guessed at, and so is a type or a delivery this app
 * does not know, since running the wrong drill is worse than running none. An unknown <em>case</em>
 * is different and is only dropped (the session reports them), so a prescription written
 * against a larger vocabulary still runs for the part this app understands.
 *
 * <p>A spec that was parsed keeps the text it was parsed from, which is what should be stored:
 * fields a later version adds survive a round trip that way, and are lost by rebuilding the text
 * from the fields this version happens to know.
 */
public final class DrillSpec {

  /** The newest spec this app can run. 5 added the learning drill, 6 the look-ahead drill. */
  public static final int VERSION = 6;

  /** How the id of every drill the coach prescribes begins. */
  public static final String COACH_ID_PREFIX = "coach-";

  /**
   * The version a drill with no per-case targets and no retiring is written at. A drill that needs
   * nothing newer says so, so an app that only reads 2 still runs every drill that did not need 3.
   */
  private static final int VERSION_2 = 2;

  /** The version a drill with per-case targets or retiring, and no working set, is written at. */
  private static final int VERSION_3 = 3;

  /** The version a drill with a working set, and no learning, is written at. */
  private static final int VERSION_4 = 4;

  /** The version a learning drill is written at. */
  private static final int VERSION_5 = 5;

  /** The version a look-ahead drill is written at, so an app that cannot deal one refuses it. */
  private static final int VERSION_6 = 6;

  /**
   * What a rep is timed and judged on. The two case types are the same reps and only move the
   * target; the cross is a different question altogether, which is why it takes a version with it.
   */
  public enum Type {
    /** The turning, from the first move of a rep to the last. */
    CASE_EXECUTION,
    /** The looking, from the case appearing to the first move. */
    CASE_RECOGNITION,
    /**
     * A whole scramble's cross on one face, judged on how many moves it took against the fewest
     * there were. Named by no case, so it carries a face and a scramble rather than a case list.
     */
    CROSS,
    /**
     * One F2L pair handed as a named case ({@code pair_a12}), the cross and the other slots in;
     * done when the pair is home. Timed on the whole rep.
     */
    F2L_PAIR,
    /**
     * Two random F2L pairs out, the cross and the other two slots in; done when both are home.
     * Trains the pause between the pairs, so it is judged from the case appearing to the second
     * pair's first turn. Dealt from no case list, and with no targets yet.
     */
    F2L_LOOKAHEAD;

    public String code() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  /** How the case is put in front of the user. */
  public enum Delivery {
    /** The virtual cube holds the case and the smart cube is only the input. */
    VIRTUAL;

    public String code() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  /** How the reps are spread over the case set. */
  public enum Selection {
    /** Every case in turn, the order redrawn each pass. */
    ROUND_ROBIN,
    /** Drawn by what each case is costing, which the app knows and the sender does not. */
    WEIGHTED;

    public String code() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  private static final String DEFAULT_PUZZLE = "3x3";
  private static final String DEFAULT_METHOD = "cfop";

  /** The faces a cross may be asked for, which are the letters every solver here already uses. */
  private static final List<String> FACES =
      Collections.unmodifiableList(Arrays.asList("U", "D", "R", "L", "F", "B"));

  private final int specVersion;
  private final String id;
  private final String puzzle;
  private final String method;
  private final Type type;
  private final Delivery delivery;
  private final List<String> cases;
  private final Selection selection;
  private final int reps;
  private final long targetMs;
  private final Map<String, Long> caseTargetsMs;
  private final int winsToRetire;
  private final int minShowings;
  private final String label;
  private final String crossFace;
  private final long planningMs;
  private final int workingSet;
  private final boolean learning;
  private final Map<String, String> caseAlgorithms;
  private final String source;

  /** A drill this app wrote itself, at the current version. */
  public DrillSpec(String id, Type type, Delivery delivery, List<String> cases,
      Selection selection, int reps, long targetMs, String label) {
    this(id, type, delivery, cases, selection, reps, targetMs,
        Collections.<String, Long>emptyMap(), label);
  }

  /**
   * A drill over a set whose cases are not one ask: beating the family mean on Gb and on T are
   * different targets. {@code targetMs} stays the fallback for a case the map does not name.
   */
  public DrillSpec(String id, Type type, Delivery delivery, List<String> cases,
      Selection selection, int reps, long targetMs, Map<String, Long> caseTargetsMs,
      String label) {
    this(id, type, delivery, cases, selection, reps, targetMs, caseTargetsMs, 0, 0, label);
  }

  /**
   * A drill that runs each case until it has beaten its target {@code winsToRetire} times, and
   * not before {@code minShowings} clean reps of it; {@code reps} is then only the ceiling. 0 wins
   * for a drill that runs its reps out, which is every drill the user picks by hand.
   */
  public DrillSpec(String id, Type type, Delivery delivery, List<String> cases,
      Selection selection, int reps, long targetMs, Map<String, Long> caseTargetsMs,
      int winsToRetire, int minShowings, String label) {
    this(caseTargetsMs.isEmpty() && winsToRetire == 0 ? VERSION_2 : VERSION_3, id, DEFAULT_PUZZLE,
        DEFAULT_METHOD, type, delivery, cases, selection, reps, targetMs, caseTargetsMs,
        winsToRetire, minShowings, label, null, 0, 0, false,
        Collections.<String, String>emptyMap(), null);
  }

  /**
   * The same drill dealing from only {@code size} of its cases at a time, the next one in line
   * coming in as one retires. For a set too large to clear in one drill; 0 deals from them all.
   */
  public DrillSpec withWorkingSet(int size) {
    return new DrillSpec(size > 0 ? Math.max(VERSION_4, specVersion) : specVersion, id, puzzle,
        method, type, delivery, cases, selection, reps, targetMs, caseTargetsMs, winsToRetire,
        minShowings, label, crossFace, planningMs, size, learning, caseAlgorithms, null);
  }

  /**
   * A learning drill: each case's algorithm is chosen and practised on the cube first, and a case
   * retires after {@code winsToRetire} clean reps turned without it, whatever the time.
   * {@code algorithms} names the algorithm to show per case; a case it leaves out shows the usual one.
   */
  public static DrillSpec learning(String id, Type type, List<String> cases, Selection selection,
      int reps, int winsToRetire, Map<String, String> algorithms, String label) {
    return new DrillSpec(VERSION_5, id, DEFAULT_PUZZLE, DEFAULT_METHOD, type, Delivery.VIRTUAL,
        cases, selection, reps, 0, Collections.<String, Long>emptyMap(), winsToRetire,
        winsToRetire, label, null, 0, 0, true, algorithms, null);
  }

  /**
   * A cross drill this app wrote itself: a face to build the cross on, and how long the user may
   * look at it before the cube goes grey. Zero for as long as they like.
   */
  public static DrillSpec cross(String id, String crossFace, int reps, long planningMs,
      String label) {
    return new DrillSpec(VERSION_2, id, DEFAULT_PUZZLE, DEFAULT_METHOD, Type.CROSS,
        Delivery.VIRTUAL, Collections.<String>emptyList(), Selection.ROUND_ROBIN, reps, 0,
        Collections.<String, Long>emptyMap(), 0, 0, label, crossFace, planningMs, 0, false,
        Collections.<String, String>emptyMap(), null);
  }

  /** A look-ahead drill this app wrote itself, which runs its reps out. */
  public static DrillSpec lookAhead(String id, int reps, String label) {
    return new DrillSpec(VERSION_6, id, DEFAULT_PUZZLE, DEFAULT_METHOD, Type.F2L_LOOKAHEAD,
        Delivery.VIRTUAL, Collections.<String>emptyList(), Selection.ROUND_ROBIN, reps, 0,
        Collections.<String, Long>emptyMap(), 0, 0, label, null, 0, 0, false,
        Collections.<String, String>emptyMap(), null);
  }

  private DrillSpec(int specVersion, String id, String puzzle, String method, Type type,
      Delivery delivery, List<String> cases, Selection selection, int reps, long targetMs,
      Map<String, Long> caseTargetsMs, int winsToRetire, int minShowings, String label,
      String crossFace, long planningMs, int workingSet, boolean learning,
      Map<String, String> caseAlgorithms, String source) {
    if (type == null || delivery == null || selection == null) {
      throw new IllegalArgumentException("A drill needs a type, a delivery and a selection");
    }
    // The face is to a cross drill what the case list is to every other type: without it there is
    // nothing to practise, and it is the one thing a cross drill cannot take from a list of cases.
    if (type == Type.CROSS) {
      if (crossFace == null || FACES.indexOf(crossFace) < 0) {
        throw new IllegalArgumentException("A cross drill needs a face, not " + crossFace);
      }
    } else if (type != Type.F2L_LOOKAHEAD && (cases == null || cases.isEmpty())) {
      throw new IllegalArgumentException("A drill needs at least one case");
    }
    if (reps <= 0) {
      throw new IllegalArgumentException("A drill needs at least one rep, not " + reps);
    }
    this.specVersion = specVersion;
    this.id = id;
    this.puzzle = puzzle;
    this.method = method;
    this.type = type;
    this.delivery = delivery;
    this.cases = Collections.unmodifiableList(
        new ArrayList<String>(cases == null ? Collections.<String>emptyList() : cases));
    this.selection = selection;
    this.reps = reps;
    this.targetMs = targetMs;
    Map<String, Long> targets = new LinkedHashMap<String, Long>();
    for (Map.Entry<String, Long> target : caseTargetsMs.entrySet()) {
      // None is what 0 already means for the set-wide target, so it is no entry rather than a zero.
      if (target.getValue() != null && target.getValue().longValue() > 0) {
        targets.put(target.getKey(), target.getValue());
      }
    }
    this.caseTargetsMs = Collections.unmodifiableMap(targets);
    if (winsToRetire < 0 || minShowings < 0) {
      throw new IllegalArgumentException("Retiring counts cannot be negative");
    }
    if (workingSet < 0) {
      throw new IllegalArgumentException("A working set cannot be negative");
    }
    if (learning && (winsToRetire == 0 || !hasCases(type))) {
      throw new IllegalArgumentException("A learning drill retires its cases, and a "
          + type.code() + " drill has none");
    }
    // A learning drill retires on reps turned without looking, so a case needs no time to beat.
    if (winsToRetire > 0 && !learning) {
      // A case with nothing to beat could never retire, so the drill would only ever hit its ceiling.
      if (!hasCases(type)) {
        throw new IllegalArgumentException("A " + type.code() + " drill does not retire cases");
      }
      for (String code : this.cases) {
        if (getTargetMs(code) <= 0) {
          throw new IllegalArgumentException("A retiring drill needs a target for " + code);
        }
      }
    }
    this.winsToRetire = winsToRetire;
    this.minShowings = minShowings;
    this.label = label;
    this.crossFace = crossFace;
    this.planningMs = planningMs;
    this.workingSet = workingSet;
    this.learning = learning;
    Map<String, String> algorithms = new LinkedHashMap<String, String>();
    for (Map.Entry<String, String> algorithm : caseAlgorithms.entrySet()) {
      if (algorithm.getValue() != null && !algorithm.getValue().trim().isEmpty()) {
        algorithms.put(algorithm.getKey(), algorithm.getValue().trim());
      }
    }
    this.caseAlgorithms = Collections.unmodifiableMap(algorithms);
    this.source = source;
  }

  /**
   * @throws IllegalArgumentException if the text is not a drill, or is one this app is too old to
   *     run
   */
  public static DrillSpec fromJson(String json) {
    try {
      JSONObject object = new JSONObject(json);
      int version = object.getInt("spec_version");
      if (version > VERSION) {
        throw new IllegalArgumentException(
            "Drill spec version " + version + " is newer than this app reads (" + VERSION + ")");
      }
      return new DrillSpec(version,
          object.optString("id", null),
          object.optString("puzzle", DEFAULT_PUZZLE),
          object.optString("method", DEFAULT_METHOD),
          value(Type.class, object.optString("type", null), "drill type"),
          value(Delivery.class, object.optString("delivery", null), "delivery"),
          codes(object.optJSONArray("cases")),
          value(Selection.class, object.optString("selection", Selection.ROUND_ROBIN.code()),
              "selection"),
          object.getInt("reps"),
          object.optLong("target_ms", 0),
          targets(object.optJSONObject("case_targets_ms")),
          object.optInt("retire_after_wins", 0),
          object.optInt("min_showings", 0),
          object.optString("label", null),
          object.optString("cross_face", null),
          object.optLong("planning_ms", 0),
          object.optInt("working_set", 0),
          object.optBoolean("learn", false),
          algorithms(object.optJSONObject("case_algorithms")),
          json);
    } catch (JSONException e) {
      throw new IllegalArgumentException("Not a drill spec: " + e.getMessage(), e);
    }
  }

  /**
   * The text to store for a spec that was received, and a written form for one that was not. A
   * received spec hands back what it was given, so that fields this version does not know are still
   * there for a version that does.
   */
  public String toJson() {
    if (source != null) {
      return source;
    }
    try {
      JSONObject object = new JSONObject();
      object.put("spec_version", specVersion);
      if (id != null) {
        object.put("id", id);
      }
      object.put("puzzle", puzzle);
      object.put("method", method);
      object.put("type", type.code());
      object.put("delivery", delivery.code());
      object.put("cases", new JSONArray(cases));
      object.put("selection", selection.code());
      object.put("reps", reps);
      if (crossFace != null) {
        object.put("cross_face", crossFace);
      }
      if (planningMs > 0) {
        object.put("planning_ms", planningMs);
      }
      if (targetMs > 0) {
        object.put("target_ms", targetMs);
      }
      if (!caseTargetsMs.isEmpty()) {
        object.put("case_targets_ms", new JSONObject(caseTargetsMs));
      }
      if (winsToRetire > 0) {
        object.put("retire_after_wins", winsToRetire);
        object.put("min_showings", minShowings);
      }
      if (workingSet > 0) {
        object.put("working_set", workingSet);
      }
      if (learning) {
        object.put("learn", true);
        if (!caseAlgorithms.isEmpty()) {
          object.put("case_algorithms", new JSONObject(caseAlgorithms));
        }
      }
      if (label != null) {
        object.put("label", label);
      }
      return object.toString();
    } catch (JSONException e) {
      throw new IllegalStateException("Cannot write drill spec", e);
    }
  }

  /** Whether a type deals named cases, which retiring and learning are counted on. */
  private static boolean hasCases(Type type) {
    return type != Type.CROSS && type != Type.F2L_LOOKAHEAD;
  }

  /** Empty rather than fatal when absent: whether a type may go without cases is the type's rule. */
  private static List<String> codes(JSONArray array) throws JSONException {
    List<String> codes = new ArrayList<String>();
    if (array == null) {
      return codes;
    }
    for (int i = 0; i < array.length(); i++) {
      codes.add(array.getString(i));
    }
    return codes;
  }

  private static Map<String, Long> targets(JSONObject object) throws JSONException {
    Map<String, Long> targets = new LinkedHashMap<String, Long>();
    if (object == null) {
      return targets;
    }
    Iterator<String> codes = object.keys();
    while (codes.hasNext()) {
      String code = codes.next();
      targets.put(code, Long.valueOf(object.getLong(code)));
    }
    return targets;
  }

  private static Map<String, String> algorithms(JSONObject object) throws JSONException {
    Map<String, String> algorithms = new LinkedHashMap<String, String>();
    if (object == null) {
      return algorithms;
    }
    Iterator<String> codes = object.keys();
    while (codes.hasNext()) {
      String code = codes.next();
      algorithms.put(code, object.getString(code));
    }
    return algorithms;
  }

  private static <T extends Enum<T>> T value(Class<T> type, String code, String what) {
    if (code == null || code.isEmpty()) {
      throw new IllegalArgumentException("A drill needs a " + what);
    }
    try {
      return Enum.valueOf(type, code.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unknown " + what + ": " + code);
    }
  }

  public int getSpecVersion() {
    return specVersion;
  }

  /** The sender's handle for this drill, for reporting reps back against. */
  public String getId() {
    return id;
  }

  /** Whether the coach prescribed this drill, which every id it sends starts by saying. */
  public boolean isFromCoach() {
    return id != null && id.startsWith(COACH_ID_PREFIX);
  }

  public String getPuzzle() {
    return puzzle;
  }

  public String getMethod() {
    return method;
  }

  public Type getType() {
    return type;
  }

  public Delivery getDelivery() {
    return delivery;
  }

  /** The cases as a solve records them, {@code oll_21} or {@code pll_ga}, unfiltered. */
  public List<String> getCases() {
    return cases;
  }

  public Selection getSelection() {
    return selection;
  }

  /** Reps over the whole set, not per case. The ceiling, for a drill that retires its cases. */
  public int getReps() {
    return reps;
  }

  /**
   * What a rep is meant to come in under, on whichever half {@link #getType} names, or on the whole
   * rep for a drill that retires its cases. 0 for none. The set-wide figure: a case with a target of
   * its own answers to {@link #getTargetMs(String)} instead.
   */
  public long getTargetMs() {
    return targetMs;
  }

  /** What a rep of this case is meant to come in under, falling back to the set-wide target. */
  public long getTargetMs(String caseCode) {
    Long target = caseTargetsMs.get(caseCode);
    return target == null ? targetMs : target.longValue();
  }

  /** The cases with a target of their own, which is what moved the version to 3. Often empty. */
  public Map<String, Long> getCaseTargetsMs() {
    return caseTargetsMs;
  }

  /** Wins against its target that retire a case, or 0 for a drill that runs its reps out. */
  public int getWinsToRetire() {
    return winsToRetire;
  }

  /** Clean reps a case must have had before it may retire, so one lucky first rep does not do it. */
  public int getMinShowings() {
    return minShowings;
  }

  public boolean retiresCases() {
    return winsToRetire > 0;
  }

  /**
   * Whether this drill teaches its cases rather than speeding them up: the algorithm is practised
   * before the drill, and a case retires on reps turned without it rather than on a time.
   */
  public boolean isLearning() {
    return learning;
  }

  /** The algorithm a learning drill shows for a case, or null for the case's usual one. */
  public String getCaseAlgorithm(String caseCode) {
    return caseAlgorithms.get(caseCode);
  }

  /** How many cases are dealt from at once, or 0 for all of them. */
  public int getWorkingSet() {
    return workingSet;
  }

  /** A short name to show. Whoever wrote it is responsible for its language. */
  public String getLabel() {
    return label;
  }

  /** The face a cross drill's cross goes on, as its letter. Null for every other type. */
  public String getCrossFace() {
    return crossFace;
  }

  /**
   * How long the user may look at a cross before the cube goes grey and they have to build it from
   * what they read. 0 for as long as they like, which is how the full cross is learned.
   */
  public long getPlanningMs() {
    return planningMs;
  }
}
