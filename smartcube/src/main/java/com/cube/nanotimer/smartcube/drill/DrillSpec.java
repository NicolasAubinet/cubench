package com.cube.nanotimer.smartcube.drill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * One drill: a set of cases, a way of putting them in front of the user, and something to beat.
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
 * is different and is only dropped ({@link DrillSession#getUnknownCases}), so a prescription written
 * against a larger vocabulary still runs for the part this app understands.
 *
 * <p>A spec that was parsed keeps the text it was parsed from, which is what should be stored:
 * fields a later version adds survive a round trip that way, and are lost by rebuilding the text
 * from the fields this version happens to know.
 */
public final class DrillSpec {

  /** The newest spec this app can run. */
  public static final int VERSION = 1;

  /** What a rep is timed and judged on. Both are the same reps; only the target moves. */
  public enum Type {
    /** The turning, from the first move of a rep to the last. */
    CASE_EXECUTION,
    /** The looking, from the case appearing to the first move. */
    CASE_RECOGNITION;

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
  private final String label;
  private final String source;

  /** A drill this app wrote itself, at the current version. */
  public DrillSpec(String id, Type type, Delivery delivery, List<String> cases,
      Selection selection, int reps, long targetMs, String label) {
    this(VERSION, id, DEFAULT_PUZZLE, DEFAULT_METHOD, type, delivery, cases, selection, reps,
        targetMs, label, null);
  }

  private DrillSpec(int specVersion, String id, String puzzle, String method, Type type,
      Delivery delivery, List<String> cases, Selection selection, int reps, long targetMs,
      String label, String source) {
    if (type == null || delivery == null || selection == null) {
      throw new IllegalArgumentException("A drill needs a type, a delivery and a selection");
    }
    if (cases == null || cases.isEmpty()) {
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
    this.cases = Collections.unmodifiableList(new ArrayList<String>(cases));
    this.selection = selection;
    this.reps = reps;
    this.targetMs = targetMs;
    this.label = label;
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
          object.optString("label", null),
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
      if (targetMs > 0) {
        object.put("target_ms", targetMs);
      }
      if (label != null) {
        object.put("label", label);
      }
      return object.toString();
    } catch (JSONException e) {
      throw new IllegalStateException("Cannot write drill spec", e);
    }
  }

  private static List<String> codes(JSONArray array) throws JSONException {
    if (array == null) {
      throw new JSONException("no cases");
    }
    List<String> codes = new ArrayList<String>();
    for (int i = 0; i < array.length(); i++) {
      codes.add(array.getString(i));
    }
    return codes;
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

  /** The sender's handle for this drill, for reporting reps back against. Opaque here. */
  public String getId() {
    return id;
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

  /** Reps over the whole set, not per case. */
  public int getReps() {
    return reps;
  }

  /** What a rep is meant to come in under, on whichever half {@link #getType} names. 0 for none. */
  public long getTargetMs() {
    return targetMs;
  }

  /** A short name to show. Whoever wrote it is responsible for its language. */
  public String getLabel() {
    return label;
  }
}
