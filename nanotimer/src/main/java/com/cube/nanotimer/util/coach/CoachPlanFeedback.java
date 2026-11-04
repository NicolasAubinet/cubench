package com.cube.nanotimer.util.coach;

import com.cube.nanotimer.coach.CoachPlan;
import com.cube.nanotimer.coach.FocusArea;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * What a beta member said about one plan, and the reading signals the feedback report scores it
 * by. Kept on the device as a draft until the stars are given, since the service takes nothing
 * without them; then sent whole, and sent whole again after any change.
 *
 * <p>The draft holds when the plan was first opened, which never leaves the device: the body
 * carries only the time from that opening to the rating.
 */
public final class CoachPlanFeedback {

  /** The answer to "Is this right about your solving?" on one focus. */
  public enum Answer {
    YES, NOT_QUITE;

    String code() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  /** Why a focus was "not quite" right. */
  public enum Reason {
    WRONG_ABOUT_SOLVES, ALREADY_DO, NOT_CLEAR, TOO_HARD, TOO_BORING, NOT_WORTH;

    String code() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  /** The answer to "Did you work on last week's plan?", asked on the plan after it. */
  public enum LastWeek {
    MOSTLY, A_BIT, NOT_REALLY;

    String code() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  /** One focus's answer. */
  public static final class FocusAnswer {
    private Answer answer;
    private final Set<Reason> reasons = EnumSet.noneOf(Reason.class);
    private String text;

    public Answer getAnswer() {
      return answer;
    }

    public Set<Reason> getReasons() {
      return Collections.unmodifiableSet(reasons);
    }

    public String getText() {
      return text;
    }
  }

  /** Texts are cut here, well inside the service's 16 KB for a whole body. */
  static final int MAX_TEXT = 2000;

  private int stars;
  private String text;
  private LastWeek lastWeek;
  private String lastWeekText;
  private long firstOpenedAt;
  private long ratedAfterMs = -1;
  private final Map<Integer, FocusAnswer> focuses = new TreeMap<Integer, FocusAnswer>();
  private final Set<Integer> focusesSeen = new TreeSet<Integer>();
  private final Map<Integer, Integer> drillReps = new TreeMap<Integer, Integer>();

  /** 1 to 5, 0 before the plan is rated. */
  public int getStars() {
    return stars;
  }

  public boolean isRated() {
    return stars > 0;
  }

  /**
   * Rates the plan. The first rating also fixes how long after the first opening it came.
   *
   * @throws IllegalArgumentException outside 1 to 5
   */
  public void rate(int stars, long now) {
    if (stars < 1 || stars > 5) {
      throw new IllegalArgumentException("Stars are 1 to 5: " + stars);
    }
    if (this.stars == 0 && firstOpenedAt > 0) {
      ratedAfterMs = Math.max(0, now - firstOpenedAt);
    }
    this.stars = stars;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = clean(text);
  }

  public LastWeek getLastWeek() {
    return lastWeek;
  }

  public void setLastWeek(LastWeek lastWeek) {
    this.lastWeek = lastWeek;
    if (lastWeek == LastWeek.MOSTLY) {
      lastWeekText = null;
    }
  }

  /** What got in the way of last week's plan, kept only with "a bit" or "not really". */
  public String getLastWeekText() {
    return lastWeekText;
  }

  public void setLastWeekText(String text) {
    lastWeekText = clean(text);
  }

  /** Picks the one thing off with a focus, in place of any picked before. */
  public void pickReason(int focusIndex, Reason reason) {
    FocusAnswer focus = focus(focusIndex);
    focus.reasons.clear();
    focus.reasons.add(reason);
  }

  /** Keeps the first opening only. */
  public void opened(long now) {
    if (firstOpenedAt == 0) {
      firstOpenedAt = now;
    }
  }

  /** The focus at this index in the plan was scrolled into view. */
  public boolean seen(int focusIndex) {
    return focusesSeen.add(focusIndex);
  }

  /** The drill reps run on this focus's drill since the plan arrived. */
  public void setDrillReps(int focusIndex, int reps) {
    drillReps.put(focusIndex, reps);
  }

  /** The focus's answer, null when it has none. */
  public FocusAnswer getFocus(int focusIndex) {
    return focuses.get(focusIndex);
  }

  /** Sets a focus's answer. A "yes" drops the reasons and text a "not quite" had. */
  public void answer(int focusIndex, Answer answer) {
    FocusAnswer focus = focus(focusIndex);
    focus.answer = answer;
    if (answer == Answer.YES) {
      focus.reasons.clear();
      focus.text = null;
    }
  }

  public void toggleReason(int focusIndex, Reason reason) {
    FocusAnswer focus = focus(focusIndex);
    if (!focus.reasons.remove(reason)) {
      focus.reasons.add(reason);
    }
  }

  public void setFocusText(int focusIndex, String text) {
    focus(focusIndex).text = clean(text);
  }

  private FocusAnswer focus(int focusIndex) {
    FocusAnswer focus = focuses.get(focusIndex);
    if (focus == null) {
      focus = new FocusAnswer();
      focuses.put(focusIndex, focus);
    }
    return focus;
  }

  private static String clean(String text) {
    if (text == null) {
      return null;
    }
    String trimmed = text.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    return trimmed.length() > MAX_TEXT ? trimmed.substring(0, MAX_TEXT) : trimmed;
  }

  /**
   * The body the service keeps, naming each focus by what it is about as well as its place, so
   * the report can read it without the plan beside it.
   *
   * @throws IllegalStateException before the plan is rated, which the service would refuse
   */
  public String toBody(CoachPlan plan) {
    if (!isRated()) {
      throw new IllegalStateException("A plan is sent its feedback once it has its stars");
    }
    try {
      JSONObject body = common().put("stars", stars);
      List<FocusArea> areas = plan.getFocus();
      JSONArray answers = new JSONArray();
      for (Map.Entry<Integer, FocusAnswer> entry : focuses.entrySet()) {
        JSONObject answer = focusJson(entry.getKey(), entry.getValue());
        int index = entry.getKey();
        if (index >= 0 && index < areas.size()) {
          answer.put("reason", areas.get(index).getReasonCode())
              .put("codes", new JSONArray(areas.get(index).getCodes()));
        }
        answers.put(answer);
      }
      body.put("focuses", answers);
      JSONObject signals = new JSONObject()
          .put("focus_count", areas.size())
          .put("focuses_seen", new JSONArray(focusesSeen))
          .put("drill_reps", repsJson());
      if (ratedAfterMs >= 0) {
        signals.put("rated_after_ms", ratedAfterMs);
      }
      return body.put("signals", signals).toString();
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
  }

  /** The draft as kept on the device. */
  public String toDraft() {
    try {
      JSONArray answers = new JSONArray();
      for (Map.Entry<Integer, FocusAnswer> entry : focuses.entrySet()) {
        answers.put(focusJson(entry.getKey(), entry.getValue()));
      }
      return common()
          .put("stars", stars)
          .put("first_opened_at", firstOpenedAt)
          .put("rated_after_ms", ratedAfterMs)
          .put("focuses", answers)
          .put("focuses_seen", new JSONArray(focusesSeen))
          .put("drill_reps", repsJson())
          .toString();
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
  }

  /** A draft {@link #toDraft} wrote. Anything unreadable is a fresh draft, not a crash. */
  public static CoachPlanFeedback fromDraft(String draft) {
    CoachPlanFeedback feedback = new CoachPlanFeedback();
    if (draft == null) {
      return feedback;
    }
    try {
      JSONObject json = new JSONObject(draft);
      int stars = json.optInt("stars", 0);
      feedback.stars = stars >= 1 && stars <= 5 ? stars : 0;
      feedback.text = clean(json.optString("text", null));
      feedback.lastWeek = valueOf(LastWeek.class, json.optString("last_week", null));
      feedback.lastWeekText = clean(json.optString("last_week_text", null));
      feedback.firstOpenedAt = json.optLong("first_opened_at", 0);
      feedback.ratedAfterMs = json.optLong("rated_after_ms", -1);
      JSONArray answers = json.optJSONArray("focuses");
      for (int i = 0; answers != null && i < answers.length(); i++) {
        JSONObject entry = answers.getJSONObject(i);
        FocusAnswer focus = feedback.focus(entry.getInt("index"));
        focus.answer = valueOf(Answer.class, entry.optString("answer", null));
        focus.text = clean(entry.optString("text", null));
        JSONArray reasons = entry.optJSONArray("reasons");
        for (int j = 0; reasons != null && j < reasons.length(); j++) {
          Reason reason = valueOf(Reason.class, reasons.getString(j));
          if (reason != null) {
            focus.reasons.add(reason);
          }
        }
      }
      JSONArray seen = json.optJSONArray("focuses_seen");
      for (int i = 0; seen != null && i < seen.length(); i++) {
        feedback.focusesSeen.add(seen.getInt(i));
      }
      JSONObject reps = json.optJSONObject("drill_reps");
      if (reps != null) {
        for (Iterator<String> keys = reps.keys(); keys.hasNext(); ) {
          String key = keys.next();
          feedback.drillReps.put(Integer.parseInt(key), reps.getInt(key));
        }
      }
      return feedback;
    } catch (JSONException | NumberFormatException e) {
      return new CoachPlanFeedback();
    }
  }

  private JSONObject common() throws JSONException {
    JSONObject json = new JSONObject();
    if (text != null) {
      json.put("text", text);
    }
    if (lastWeek != null) {
      json.put("last_week", lastWeek.code());
    }
    if (lastWeekText != null) {
      json.put("last_week_text", lastWeekText);
    }
    return json;
  }

  private static JSONObject focusJson(int index, FocusAnswer focus) throws JSONException {
    JSONObject json = new JSONObject().put("index", index);
    if (focus.answer != null) {
      json.put("answer", focus.answer.code());
    }
    List<String> reasons = new ArrayList<String>();
    for (Reason reason : focus.reasons) {
      reasons.add(reason.code());
    }
    json.put("reasons", new JSONArray(reasons));
    if (focus.text != null) {
      json.put("text", focus.text);
    }
    return json;
  }

  private JSONObject repsJson() throws JSONException {
    JSONObject reps = new JSONObject();
    for (Map.Entry<Integer, Integer> entry : drillReps.entrySet()) {
      reps.put(String.valueOf(entry.getKey()), entry.getValue().intValue());
    }
    return reps;
  }

  private static <E extends Enum<E>> E valueOf(Class<E> type, String code) {
    if (code == null) {
      return null;
    }
    for (E value : type.getEnumConstants()) {
      if (value.name().toLowerCase(Locale.ROOT).equals(code)) {
        return value;
      }
    }
    return null;
  }
}
