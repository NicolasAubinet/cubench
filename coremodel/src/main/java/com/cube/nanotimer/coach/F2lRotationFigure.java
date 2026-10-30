package com.cube.nanotimer.coach;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How often a solver rotates the cube during F2L, and what a rotation costs them, read from the
 * gyro solves of the F2L move window ({@link PairRotation}).
 *
 * <p>Counts only, never a rate: a rate is two of these divided, and a derived field could be cited
 * without either part clearing its floor. The one exception is {@link #getExcessMs}, a difference of
 * means over gaps no other field counts.
 *
 * <p>Measured on the owner's 249 solves (2026-10-30): 0.78 rotations a pair, 61% of pairs with one,
 * about 410 ms over a plain gap per rotation, no loss in the next pair's recognition.
 */
public final class F2lRotationFigure {

  private final int solves;
  private final int pairs;
  private final int rotations;
  private final int rotatedPairs;
  private final int basicPairs;
  private final int basicRotatedPairs;
  private final Long excessMs;
  private final List<Case> cases;

  F2lRotationFigure(int solves, int pairs, int rotations, int rotatedPairs, int basicPairs,
      int basicRotatedPairs, Long excessMs, List<Case> cases) {
    this.solves = solves;
    this.pairs = pairs;
    this.rotations = rotations;
    this.rotatedPairs = rotatedPairs;
    this.basicPairs = basicPairs;
    this.basicRotatedPairs = basicRotatedPairs;
    this.excessMs = excessMs;
    this.cases = Collections.unmodifiableList(new ArrayList<Case>(cases));
  }

  /**
   * The figure over these pairs, or null under {@code solveFloor} gyro solves.
   *
   * @param gapFloor how many gaps with a rotation the excess needs behind it, else it is not sent
   * @param caseFloor how many pairs a case needs before it is quoted
   */
  static F2lRotationFigure of(List<PairRotation> pairs, int solveFloor, int gapFloor,
      int caseFloor) {
    Set<Long> solveIds = new LinkedHashSet<Long>();
    int rotations = 0;
    int rotated = 0;
    int basic = 0;
    int basicRotated = 0;
    PairRotation.Gaps start = PairRotation.Gaps.NONE;
    PairRotation.Gaps inside = PairRotation.Gaps.NONE;
    Map<String, int[]> byCase = new LinkedHashMap<String, int[]>();
    for (PairRotation pair : pairs) {
      solveIds.add(Long.valueOf(pair.getSolveId()));
      boolean turned = pair.getRotations() > 0;
      rotations += pair.getRotations();
      rotated += turned ? 1 : 0;
      if (pair.isBasic()) {
        basic++;
        basicRotated += turned ? 1 : 0;
      }
      start = start.plus(pair.getStart());
      inside = inside.plus(pair.getInside());
      if (pair.getCaseCode() != null) {
        int[] tally = byCase.get(pair.getCaseCode());
        if (tally == null) {
          tally = new int[2];
          byCase.put(pair.getCaseCode(), tally);
        }
        tally[0]++;
        tally[1] += turned ? 1 : 0;
      }
    }
    if (solveIds.size() < solveFloor) {
      return null;
    }
    List<Case> cases = new ArrayList<Case>();
    for (Map.Entry<String, int[]> entry : byCase.entrySet()) {
      if (entry.getValue()[0] >= caseFloor) {
        cases.add(new Case(entry.getKey(), entry.getValue()[0], entry.getValue()[1]));
      }
    }
    Collections.sort(cases, new Comparator<Case>() {
      @Override
      public int compare(Case a, Case b) { // most often rotated on first
        return Long.compare((long) b.rotated * a.count, (long) a.rotated * b.count);
      }
    });
    return new F2lRotationFigure(solveIds.size(), pairs.size(), rotations, rotated, basic,
        basicRotated, excess(start, inside, gapFloor), cases);
  }

  /** Each position's rotated gaps over its plain ones, weighted by how many rotations each holds. */
  private static Long excess(PairRotation.Gaps start, PairRotation.Gaps inside, int gapFloor) {
    double sum = 0;
    int weight = 0;
    for (PairRotation.Gaps gaps : new PairRotation.Gaps[] {start, inside}) {
      if (gaps.getRotated() == 0 || gaps.getPlain() == 0) {
        continue;
      }
      double over = (double) gaps.getRotatedMs() / gaps.getRotated()
          - (double) gaps.getPlainMs() / gaps.getPlain();
      sum += over * gaps.getRotated();
      weight += gaps.getRotated();
    }
    return weight < gapFloor ? null : Long.valueOf(Math.round(sum / weight));
  }

  /** How many gyro solves the pairs came from. */
  public int getSolves() {
    return solves;
  }

  public int getPairs() {
    return pairs;
  }

  /** Every rotation counted, a {@code y2} as one. */
  public int getRotations() {
    return rotations;
  }

  /** The pairs with at least one rotation. */
  public int getRotatedPairs() {
    return rotatedPairs;
  }

  /** The pairs of a basic case, the ones a rotationless algorithm can be learnt for. */
  public int getBasicPairs() {
    return basicPairs;
  }

  public int getBasicRotatedPairs() {
    return basicRotatedPairs;
  }

  /**
   * How much longer a gap with a rotation takes than one without in the same place, in ms, or null
   * where too few rotations were made to say.
   */
  public Long getExcessMs() {
    return excessMs;
  }

  /** Each case above the floor, the most often rotated on first. */
  public List<Case> getCases() {
    return cases;
  }

  Case getCase(String code) {
    for (Case one : cases) {
      if (one.code.equals(code)) {
        return one;
      }
    }
    return null;
  }

  Double field(String name) {
    if ("solves".equals(name)) {
      return Double.valueOf(solves);
    }
    if ("pairs".equals(name)) {
      return Double.valueOf(pairs);
    }
    if ("rotations".equals(name)) {
      return Double.valueOf(rotations);
    }
    if ("rotated_pairs".equals(name)) {
      return Double.valueOf(rotatedPairs);
    }
    if ("basic_pairs".equals(name)) {
      return Double.valueOf(basicPairs);
    }
    if ("basic_rotated_pairs".equals(name)) {
      return Double.valueOf(basicRotatedPairs);
    }
    return "excess_ms".equals(name) && excessMs != null
        ? Double.valueOf(excessMs.longValue()) : null;
  }

  JSONObject toJson() throws JSONException {
    JSONObject json = new JSONObject();
    json.put("solves", solves);
    json.put("pairs", pairs);
    json.put("rotations", rotations);
    json.put("rotated_pairs", rotatedPairs);
    json.put("basic_pairs", basicPairs);
    json.put("basic_rotated_pairs", basicRotatedPairs);
    if (excessMs != null) {
      json.put("excess_ms", excessMs.longValue());
    }
    JSONArray array = new JSONArray();
    for (Case one : cases) {
      JSONObject item = new JSONObject();
      item.put("code", one.code);
      item.put("count", one.count);
      item.put("rotated", one.rotated);
      array.put(item);
    }
    json.put("cases", array);
    return json;
  }

  static F2lRotationFigure fromJson(JSONObject json) throws JSONException {
    List<Case> cases = new ArrayList<Case>();
    JSONArray array = json.optJSONArray("cases");
    for (int i = 0; array != null && i < array.length(); i++) {
      JSONObject item = array.getJSONObject(i);
      cases.add(new Case(item.getString("code"), item.getInt("count"), item.getInt("rotated")));
    }
    return new F2lRotationFigure(json.getInt("solves"), json.getInt("pairs"),
        json.getInt("rotations"), json.getInt("rotated_pairs"), json.getInt("basic_pairs"),
        json.getInt("basic_rotated_pairs"),
        json.has("excess_ms") ? Long.valueOf(json.getLong("excess_ms")) : null, cases);
  }

  /** One case: how many of its pairs there were, and how many of them had a rotation. */
  public static final class Case {

    private final String code;
    private final int count;
    private final int rotated;

    Case(String code, int count, int rotated) {
      this.code = code;
      this.count = count;
      this.rotated = rotated;
    }

    public String getCode() {
      return code;
    }

    public int getCount() {
      return count;
    }

    public int getRotated() {
      return rotated;
    }

    Double field(String name) {
      if ("count".equals(name)) {
        return Double.valueOf(count);
      }
      return "rotated".equals(name) ? Double.valueOf(rotated) : null;
    }
  }
}
