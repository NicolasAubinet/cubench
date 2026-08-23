package com.cube.nanotimer.coach;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Everything a coach is told about a solver, and the only thing that leaves the device.
 *
 * <p>It is numbers and vocabulary codes, and it is that on purpose. No user-authored text rides in
 * it at any version: solve type names, custom step names and drill names are all written by the
 * user, and a payload carrying them would be a prompt-injection channel straight into whatever
 * reads this. The same shape is what lets a plan be checked afterwards, since a claim citing a
 * number that is not in here was invented.
 *
 * <p>What is here has already been through the floors and the outlier rule: a figure too thin to
 * stand is absent rather than caveated, because nothing can be careful about a number it never saw.
 * Windows are counts of solves and drills rather than dates, so the payload says how much it looked
 * at without saying when the user was cubing.
 *
 * <p>Versioned from day one because old clients will be sending old payloads for years, and read
 * back through the same class it is written by, so the two sides cannot drift.
 */
public class CoachPayload {

  /** The newest payload this app writes and can read back. Version 2 added the two-look count and
   * the known set; a version 1 payload is still read, and is simply missing them. */
  public static final int VERSION = 3;

  private final int schemaVersion;
  private final String puzzle;
  private final String method;
  private final int familyWindow;
  private final int caseWindow;
  private final int drillWindow;
  private final Integer twoLookCount;
  private final StepFigure solve;
  private final List<StepFigure> families;
  private final List<StepFigure> parts;
  private final List<StepFigure> cases;
  private final List<StepFigure> drillCases;
  private final List<CaseComparison> comparisons;
  private final List<String> knownCases;
  private final List<String> learningCases;
  private final List<String> toLearnCases;

  /**
   * @param solve the whole solve's figures, or null when the window holds too few to quote
   * @param familyWindow how many solves the step figures were read from, {@code caseWindow} the
   *     same for the case figures, which need a longer look to say anything
   * @param drillWindow how many recorded drills the drill figures were read from
   * @param twoLookCount how many of those solves took the last layer in more than one algorithm,
   *     null in a payload written before it was counted — which is not the same as none
   * @param knownCases the cases the solver has been shown to execute in one algorithm unaided,
   *     {@code learningCases} the ones they have not. A case in neither has too little behind it to
   *     say so, which is its own answer and is never the same as not knowing it.
   * @param toLearnCases the subset of {@code learningCases} never once put in with a single
   *     algorithm, so a plan can say learn where it would otherwise say drill
   */
  public CoachPayload(int schemaVersion, String puzzle, String method, int familyWindow,
      int caseWindow, int drillWindow, Integer twoLookCount, StepFigure solve,
      List<StepFigure> families, List<StepFigure> parts, List<StepFigure> cases,
      List<StepFigure> drillCases, List<CaseComparison> comparisons, List<String> knownCases,
      List<String> learningCases, List<String> toLearnCases) {
    this.schemaVersion = schemaVersion;
    this.puzzle = puzzle;
    this.method = method;
    this.familyWindow = familyWindow;
    this.caseWindow = caseWindow;
    this.drillWindow = drillWindow;
    this.twoLookCount = twoLookCount;
    this.solve = solve;
    this.families = unmodifiable(families);
    this.parts = unmodifiable(parts);
    this.cases = unmodifiable(cases);
    this.drillCases = unmodifiable(drillCases);
    this.comparisons = comparisons == null
        ? Collections.<CaseComparison>emptyList()
        : Collections.unmodifiableList(new ArrayList<CaseComparison>(comparisons));
    this.knownCases = codes(knownCases);
    this.learningCases = codes(learningCases);
    this.toLearnCases = codes(toLearnCases);
  }

  private static List<String> codes(List<String> codes) {
    return codes == null ? Collections.<String>emptyList()
        : Collections.unmodifiableList(new ArrayList<String>(codes));
  }

  private static List<StepFigure> unmodifiable(List<StepFigure> figures) {
    return figures == null
        ? Collections.<StepFigure>emptyList()
        : Collections.unmodifiableList(new ArrayList<StepFigure>(figures));
  }

  public int getSchemaVersion() {
    return schemaVersion;
  }

  /** The puzzle in the vocabulary a drill spec uses, "3x3". */
  public String getPuzzle() {
    return puzzle;
  }

  /** The method the solves were read as, "cfop". */
  public String getMethod() {
    return method;
  }

  public int getFamilyWindow() {
    return familyWindow;
  }

  public int getCaseWindow() {
    return caseWindow;
  }

  public int getDrillWindow() {
    return drillWindow;
  }

  /**
   * How many of the family window's solves needed more than one algorithm for the last layer's
   * orientation, which is what a two-look OLL is. A technique rather than a speed, and the one level
   * difference in the splits that is real, so it is counted rather than read off a mean.
   *
   * <p>Null where the payload was written before this was counted, which no reader may take for a
   * count of none: a stored plan re-read that way would say the solver two-looks nothing.
   */
  public Integer getTwoLookCount() {
    return twoLookCount;
  }

  /** The whole solve, or null when there were too few to quote one. */
  public StepFigure getSolve() {
    return solve;
  }

  /** The steps of the method, in solving order. */
  public List<StepFigure> getFamilies() {
    return families;
  }

  /** The codes a step was built in parts under, which are not steps of the method themselves. */
  public List<StepFigure> getParts() {
    return parts;
  }

  /** The named cases, worst cost first. */
  public List<StepFigure> getCases() {
    return cases;
  }

  /** The same cases as they run in a drill, which is a different question from how a solve asks them. */
  public List<StepFigure> getDrillCases() {
    return drillCases;
  }

  /** The cases where solve and drill are far enough apart, on samples thick enough, to mean something. */
  public List<CaseComparison> getComparisons() {
    return comparisons;
  }

  /**
   * The cases the solver has been shown to execute in one algorithm on their own. Codes only: it is
   * a status and not a figure, and how fast each of them runs is already here as a case.
   */
  public List<String> getKnownCases() {
    return knownCases;
  }

  /**
   * The cases they reach for a second algorithm on, or have had to be shown. Every other case is
   * absent because too little has been seen of it, never because it is not known.
   */
  public List<String> getLearningCases() {
    return learningCases;
  }

  /**
   * The cases of {@link #getLearningCases} that have never once gone in with a single algorithm, so
   * they have not been learnt rather than slipped. Empty in a payload written before they were told
   * apart, which is not the same as every learning case having been learnt once.
   */
  public List<String> getToLearnCases() {
    return toLearnCases;
  }

  /**
   * The figure at a path, or null when the payload holds nothing there. Paths are how a plan cites
   * what it leans on: {@code solve.mean_ms}, {@code families.pll.recognition_ms},
   * {@code cases.pll_gb.time_lost_ms}, {@code comparisons.pll_gb.gap_ms}, {@code windows.cases},
   * {@code two_look_count}.
   */
  public Double value(String path) {
    String[] segments = path.split("\\.");
    if (segments.length == 1) {
      return "two_look_count".equals(segments[0]) && twoLookCount != null
          ? Double.valueOf(twoLookCount.intValue()) : null;
    }
    if (segments.length == 2 && "solve".equals(segments[0])) {
      return solve == null ? null : field(solve, segments[1]);
    }
    if (segments.length == 2 && "windows".equals(segments[0])) {
      return window(segments[1]);
    }
    if (segments.length != 3) {
      return null;
    }
    if ("comparisons".equals(segments[0])) {
      CaseComparison comparison = comparison(segments[1]);
      return comparison == null ? null : field(comparison, segments[2]);
    }
    StepFigure figure = figure(list(segments[0]), segments[1]);
    return figure == null ? null : field(figure, segments[2]);
  }

  private Double window(String name) {
    if ("families".equals(name)) {
      return Double.valueOf(familyWindow);
    }
    if ("cases".equals(name)) {
      return Double.valueOf(caseWindow);
    }
    return "drills".equals(name) ? Double.valueOf(drillWindow) : null;
  }

  private List<StepFigure> list(String name) {
    if ("families".equals(name)) {
      return families;
    }
    if ("parts".equals(name)) {
      return parts;
    }
    if ("cases".equals(name)) {
      return cases;
    }
    return "drill_cases".equals(name) ? drillCases : Collections.<StepFigure>emptyList();
  }

  private static StepFigure figure(List<StepFigure> figures, String code) {
    for (StepFigure figure : figures) {
      if (figure.getCode().equals(code)) {
        return figure;
      }
    }
    return null;
  }

  private CaseComparison comparison(String code) {
    for (CaseComparison comparison : comparisons) {
      if (comparison.getCode().equals(code)) {
        return comparison;
      }
    }
    return null;
  }

  private static Double field(StepFigure figure, String name) {
    if ("count".equals(name)) {
      return Double.valueOf(figure.getCount());
    }
    if ("mean_ms".equals(name)) {
      return Double.valueOf(figure.getMeanMs());
    }
    if ("recognition_ms".equals(name)) {
      return figure.getRecognitionMs() == null ? null
          : Double.valueOf(figure.getRecognitionMs().longValue());
    }
    if ("std_dev_ms".equals(name)) {
      return Double.valueOf(figure.getStdDevMs());
    }
    if ("best_ms".equals(name)) {
      return Double.valueOf(figure.getBestMs());
    }
    if ("rejection_rate".equals(name)) {
      return Double.valueOf(figure.getRejectionRate());
    }
    if ("family_mean_ms".equals(name)) {
      return figure.getFamilyMeanMs() == null ? null
          : Double.valueOf(figure.getFamilyMeanMs().longValue());
    }
    if ("time_lost_ms".equals(name)) {
      return figure.getTimeLostMs() == null ? null
          : Double.valueOf(figure.getTimeLostMs().longValue());
    }
    if ("skip_rate".equals(name)) {
      return figure.getSkipRate() == null ? null : figure.getSkipRate();
    }
    return null;
  }

  private static Double field(CaseComparison comparison, String name) {
    if ("solve_count".equals(name)) {
      return Double.valueOf(comparison.getSolveCount());
    }
    if ("solve_mean_ms".equals(name)) {
      return Double.valueOf(comparison.getSolveMeanMs());
    }
    if ("drill_count".equals(name)) {
      return Double.valueOf(comparison.getDrillCount());
    }
    if ("drill_mean_ms".equals(name)) {
      return Double.valueOf(comparison.getDrillMeanMs());
    }
    return "gap_ms".equals(name) ? Double.valueOf(comparison.getGapMs()) : null;
  }

  /** Is there enough here to say anything at all? */
  public boolean isEmpty() {
    return solve == null && families.isEmpty() && cases.isEmpty() && drillCases.isEmpty();
  }

  public String toJson() {
    try {
      JSONObject json = new JSONObject();
      json.put("schema_version", schemaVersion);
      json.put("puzzle", puzzle);
      json.put("method", method);
      JSONObject windows = new JSONObject();
      windows.put("families", familyWindow);
      windows.put("cases", caseWindow);
      windows.put("drills", drillWindow);
      json.put("windows", windows);
      if (twoLookCount != null) {
        json.put("two_look_count", twoLookCount);
      }
      if (solve != null) {
        json.put("solve", solve.toJson());
      }
      json.put("families", figures(families));
      json.put("parts", figures(parts));
      json.put("cases", figures(cases));
      json.put("drill_cases", figures(drillCases));
      JSONArray gaps = new JSONArray();
      for (CaseComparison comparison : comparisons) {
        gaps.put(comparison.toJson());
      }
      json.put("comparisons", gaps);
      json.put("known_cases", new JSONArray(knownCases));
      json.put("learning_cases", new JSONArray(learningCases));
      json.put("to_learn_cases", new JSONArray(toLearnCases));
      return json.toString();
    } catch (JSONException e) {
      throw new IllegalStateException("Cannot write coach payload", e);
    }
  }

  private static JSONArray figures(List<StepFigure> figures) throws JSONException {
    JSONArray array = new JSONArray();
    for (StepFigure figure : figures) {
      array.put(figure.toJson());
    }
    return array;
  }

  /**
   * @throws IllegalArgumentException if the text is not a payload, or is one this version is too old
   *     to read, which is refused rather than guessed at
   */
  public static CoachPayload parse(String text) {
    try {
      JSONObject json = new JSONObject(text);
      int version = json.getInt("schema_version");
      if (version > VERSION) {
        throw new IllegalArgumentException(
            "Coach payload version " + version + ", this reader knows " + VERSION);
      }
      JSONObject windows = json.optJSONObject("windows");
      return new CoachPayload(version, json.getString("puzzle"), json.getString("method"),
          windows == null ? 0 : windows.optInt("families"),
          windows == null ? 0 : windows.optInt("cases"),
          windows == null ? 0 : windows.optInt("drills"),
          json.has("two_look_count") ? Integer.valueOf(json.getInt("two_look_count")) : null,
          json.has("solve") ? StepFigure.fromJson(json.getJSONObject("solve")) : null,
          figures(json, "families"), figures(json, "parts"), figures(json, "cases"),
          figures(json, "drill_cases"), comparisons(json), codes(json, "known_cases"),
          codes(json, "learning_cases"), codes(json, "to_learn_cases"));
    } catch (JSONException e) {
      throw new IllegalArgumentException("Not a coach payload: " + e.getMessage(), e);
    }
  }

  private static List<StepFigure> figures(JSONObject json, String field) throws JSONException {
    List<StepFigure> figures = new ArrayList<StepFigure>();
    JSONArray array = json.optJSONArray(field);
    for (int i = 0; array != null && i < array.length(); i++) {
      figures.add(StepFigure.fromJson(array.getJSONObject(i)));
    }
    return figures;
  }

  private static List<String> codes(JSONObject json, String field) throws JSONException {
    List<String> codes = new ArrayList<String>();
    JSONArray array = json.optJSONArray(field);
    for (int i = 0; array != null && i < array.length(); i++) {
      codes.add(array.getString(i));
    }
    return codes;
  }

  private static List<CaseComparison> comparisons(JSONObject json) throws JSONException {
    List<CaseComparison> comparisons = new ArrayList<CaseComparison>();
    JSONArray array = json.optJSONArray("comparisons");
    for (int i = 0; array != null && i < array.length(); i++) {
      comparisons.add(CaseComparison.fromJson(array.getJSONObject(i)));
    }
    return comparisons;
  }
}
