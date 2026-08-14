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

  /** The newest payload this app writes and can read back. */
  public static final int VERSION = 1;

  private final int schemaVersion;
  private final String puzzle;
  private final String method;
  private final int familyWindow;
  private final int caseWindow;
  private final int drillWindow;
  private final StepFigure solve;
  private final List<StepFigure> families;
  private final List<StepFigure> parts;
  private final List<StepFigure> cases;
  private final List<StepFigure> drillCases;
  private final List<CaseComparison> comparisons;

  /**
   * @param solve the whole solve's figures, or null when the window holds too few to quote
   * @param familyWindow how many solves the step figures were read from, {@code caseWindow} the
   *     same for the case figures, which need a longer look to say anything
   * @param drillWindow how many recorded drills the drill figures were read from
   */
  public CoachPayload(int schemaVersion, String puzzle, String method, int familyWindow,
      int caseWindow, int drillWindow, StepFigure solve, List<StepFigure> families,
      List<StepFigure> parts, List<StepFigure> cases, List<StepFigure> drillCases,
      List<CaseComparison> comparisons) {
    this.schemaVersion = schemaVersion;
    this.puzzle = puzzle;
    this.method = method;
    this.familyWindow = familyWindow;
    this.caseWindow = caseWindow;
    this.drillWindow = drillWindow;
    this.solve = solve;
    this.families = unmodifiable(families);
    this.parts = unmodifiable(parts);
    this.cases = unmodifiable(cases);
    this.drillCases = unmodifiable(drillCases);
    this.comparisons = comparisons == null
        ? Collections.<CaseComparison>emptyList()
        : Collections.unmodifiableList(new ArrayList<CaseComparison>(comparisons));
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
          json.has("solve") ? StepFigure.fromJson(json.getJSONObject("solve")) : null,
          figures(json, "families"), figures(json, "parts"), figures(json, "cases"),
          figures(json, "drill_cases"), comparisons(json));
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

  private static List<CaseComparison> comparisons(JSONObject json) throws JSONException {
    List<CaseComparison> comparisons = new ArrayList<CaseComparison>();
    JSONArray array = json.optJSONArray("comparisons");
    for (int i = 0; array != null && i < array.length(); i++) {
      comparisons.add(CaseComparison.fromJson(array.getJSONObject(i)));
    }
    return comparisons;
  }
}
