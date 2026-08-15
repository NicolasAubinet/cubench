package com.cube.nanotimer.coach;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * One thing to work on: what it is about, the numbers that say so, and something to practise it
 * with.
 *
 * <p>The reason is a code rather than a sentence, which is what lets the free plan exist at all: the
 * device has no model to write with, so it names the shape of the problem and the screen says it in
 * the user's language. A coach may add its narration in {@link #getText}, and the code stays, so the
 * two plans can be read against each other.
 *
 * <p>A reason this version has never heard of is kept rather than refused. The reader of a plan is
 * the app, which ships on its own release cycle, so the sender will outrun it: a kind of advice
 * added after an app was published has to arrive as an area that still shows what it was told
 * ({@link #getText}) and still launches its drill, instead of taking the whole plan down with it.
 *
 * <p>The drill rides as the text of a spec rather than a parsed one. A drill written by a newer
 * version than this app knows is then one area that cannot be launched instead of a plan that cannot
 * be read.
 */
public class FocusArea {

  /** The shape of the problem, which is as much as a plan written without a model can name. */
  public enum Reason {
    /** A case that runs well over what the rest of its family costs. */
    SLOW_CASE,
    /** A case that is quick when drilled and slow in a solve: known, and not recognised under pressure. */
    SLOW_UNDER_PRESSURE,
    /** A step spending more of itself on finding the answer than on turning. */
    RECOGNITION_HEAVY,
    /** A last layer being taken in two looks where one would do. */
    TWO_LOOK_OLL,
    /** A case whose occurrences are thrown out often enough to be the complaint itself. */
    INCONSISTENT_CASE,
    /** A step taking more of the solve than the method's published baseline says it should. */
    SLOW_STEP;

    public String code() {
      return name().toLowerCase(Locale.ROOT);
    }

    /** Null for a reason this version does not know, which is the sender being newer, not wrong. */
    static Reason of(String code) {
      for (Reason reason : values()) {
        if (reason.code().equals(code)) {
          return reason;
        }
      }
      return null;
    }
  }

  private final String reasonCode;
  private final Reason reason;
  private final List<String> codes;
  private final List<Evidence> evidence;
  private final String text;
  private final String drill;

  /**
   * @param codes the steps or cases this is about, in the vocabulary the payload speaks
   * @param text the narration, null for a plan written without a model
   * @param drill the spec to practise it with as its JSON text, null when there is nothing to drill
   */
  public FocusArea(Reason reason, List<String> codes, List<Evidence> evidence, String text,
      String drill) {
    this(reason.code(), codes, evidence, text, drill);
  }

  /** As above, for a reason read off a plan, which may be one this version has never heard of. */
  public FocusArea(String reasonCode, List<String> codes, List<Evidence> evidence, String text,
      String drill) {
    this.reasonCode = reasonCode;
    this.reason = Reason.of(reasonCode);
    this.codes = Collections.unmodifiableList(new ArrayList<String>(codes));
    this.evidence = Collections.unmodifiableList(new ArrayList<Evidence>(evidence));
    this.text = text;
    this.drill = drill;
  }

  /** Null when the plan names a kind of advice this version does not know: show {@link #getText}. */
  public Reason getReason() {
    return reason;
  }

  /** What the plan called it, known here or not. */
  public String getReasonCode() {
    return reasonCode;
  }

  public List<String> getCodes() {
    return codes;
  }

  public List<Evidence> getEvidence() {
    return evidence;
  }

  /** What a coach said about it, or null when nothing wrote any. */
  public String getText() {
    return text;
  }

  /** The drill spec's JSON text, or null. Parse it when it is launched, not when the plan is read. */
  public String getDrill() {
    return drill;
  }

  JSONObject toJson() throws JSONException {
    JSONObject json = new JSONObject();
    json.put("reason", reasonCode);
    json.put("codes", new JSONArray(codes));
    JSONArray cited = new JSONArray();
    for (Evidence one : evidence) {
      cited.put(one.toJson());
    }
    json.put("evidence", cited);
    if (text != null) {
      json.put("text", text);
    }
    if (drill != null) {
      json.put("drill", new JSONObject(drill));
    }
    return json;
  }

  static FocusArea fromJson(JSONObject json) throws JSONException {
    List<String> codes = new ArrayList<String>();
    JSONArray about = json.optJSONArray("codes");
    for (int i = 0; about != null && i < about.length(); i++) {
      codes.add(about.getString(i));
    }
    List<Evidence> evidence = new ArrayList<Evidence>();
    JSONArray cited = json.optJSONArray("evidence");
    for (int i = 0; cited != null && i < cited.length(); i++) {
      evidence.add(Evidence.fromJson(cited.getJSONObject(i)));
    }
    JSONObject drill = json.optJSONObject("drill");
    return new FocusArea(json.getString("reason"), codes, evidence,
        json.has("text") ? json.getString("text") : null, drill == null ? null : drill.toString());
  }
}
