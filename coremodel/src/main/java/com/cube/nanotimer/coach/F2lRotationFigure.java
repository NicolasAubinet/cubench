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
 * about 410 ms over a plain gap per rotation, no loss in the next pair's recognition. By where the
 * slot sat in the grip (2026-11-06), basic pairs were rotated on 36% in front right, 63% in front
 * left, 62% in back right and 74% in back left.
 *
 * <p>Since payload 14 the basic pairs are also split by where the slot sat as the pair began, front
 * or back ({@link PairRotation#getSlot}), and each case is counted per slot: a front-slot rotation
 * is undone by algorithms every solver learns early, a back-slot one by advanced ones.
 */
public final class F2lRotationFigure {

  private final int solves;
  private final int pairs;
  private final int rotations;
  private final int rotatedPairs;
  private final int basicPairs;
  private final int basicRotatedPairs;
  private final Held held;
  private final Long excessMs;
  private final List<Case> cases;

  F2lRotationFigure(int solves, int pairs, int rotations, int rotatedPairs, int basicPairs,
      int basicRotatedPairs, Held held, Long excessMs, List<Case> cases) {
    this.solves = solves;
    this.pairs = pairs;
    this.rotations = rotations;
    this.rotatedPairs = rotatedPairs;
    this.basicPairs = basicPairs;
    this.basicRotatedPairs = basicRotatedPairs;
    this.held = held;
    this.excessMs = excessMs;
    this.cases = Collections.unmodifiableList(new ArrayList<Case>(cases));
  }

  /**
   * The figure over these pairs, or null under {@code solveFloor} gyro solves.
   *
   * @param gapFloor how many gaps with a rotation the excess needs behind it, else it is not sent
   * @param caseFloor how many pairs a case needs in a slot before it is quoted
   */
  static F2lRotationFigure of(List<PairRotation> pairs, int solveFloor, int gapFloor,
      int caseFloor) {
    Set<Long> solveIds = new LinkedHashSet<Long>();
    int rotations = 0;
    int rotated = 0;
    int basic = 0;
    int basicRotated = 0;
    int[] held = new int[4]; // front pairs, front rotated, back pairs, back rotated
    PairRotation.Gaps start = PairRotation.Gaps.NONE;
    PairRotation.Gaps inside = PairRotation.Gaps.NONE;
    Map<String, Case> byCase = new LinkedHashMap<String, Case>();
    for (PairRotation pair : pairs) {
      solveIds.add(Long.valueOf(pair.getSolveId()));
      boolean turned = pair.getRotations() > 0;
      rotations += pair.getRotations();
      rotated += turned ? 1 : 0;
      if (pair.isBasic()) {
        basic++;
        basicRotated += turned ? 1 : 0;
        int side = pair.isFront() ? 0 : pair.isBack() ? 2 : -1;
        if (side >= 0) {
          held[side]++;
          held[side + 1] += turned ? 1 : 0;
        }
      }
      start = start.plus(pair.getStart());
      inside = inside.plus(pair.getInside());
      if (pair.getCaseCode() != null && pair.getSlot() != null) {
        String key = pair.getCaseCode() + "_" + pair.getSlot();
        Case tally = byCase.get(key);
        byCase.put(key, new Case(pair.getCaseCode(), pair.getSlot(),
            (tally == null ? 0 : tally.count) + 1, (tally == null ? 0 : tally.rotated)
                + (turned ? 1 : 0)));
      }
    }
    if (solveIds.size() < solveFloor) {
      return null;
    }
    List<Case> cases = new ArrayList<Case>();
    for (Case tally : byCase.values()) {
      if (tally.count >= caseFloor) {
        cases.add(tally);
      }
    }
    Collections.sort(cases, new Comparator<Case>() {
      @Override
      public int compare(Case a, Case b) { // costliest first: most rotations, then most often rotated
        int byCount = Integer.compare(b.rotated, a.rotated);
        return byCount != 0 ? byCount
            : Long.compare((long) b.rotated * a.count, (long) a.rotated * b.count);
      }
    });
    return new F2lRotationFigure(solveIds.size(), pairs.size(), rotations, rotated, basic,
        basicRotated, new Held(held[0], held[1], held[2], held[3]),
        excess(start, inside, gapFloor), cases);
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

  /** The basic pairs by where their slot sat, or null in a payload older than 14. */
  public Held getHeld() {
    return held;
  }

  /**
   * How much longer a gap with a rotation takes than one without in the same place, in ms, or null
   * where too few rotations were made to say.
   */
  public Long getExcessMs() {
    return excessMs;
  }

  /** Each case and slot above the floor, the most rotated on first. */
  public List<Case> getCases() {
    return cases;
  }

  Case getCase(String code) {
    for (Case one : cases) {
      if (one.key().equals(code) || one.slot == null && one.code.equals(code)) {
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
    if (held != null) {
      Integer count = held.field(name);
      if (count != null) {
        return Double.valueOf(count.intValue());
      }
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
    if (held != null) {
      for (String name : Held.NAMES) {
        json.put(name, held.field(name).intValue());
      }
    }
    if (excessMs != null) {
      json.put("excess_ms", excessMs.longValue());
    }
    JSONArray array = new JSONArray();
    for (Case one : cases) {
      JSONObject item = new JSONObject();
      item.put("code", one.code);
      if (one.slot != null) {
        item.put("slot", one.slot);
      }
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
      cases.add(new Case(item.getString("code"), item.optString("slot", null),
          item.getInt("count"), item.getInt("rotated")));
    }
    Held held = json.has(Held.NAMES[0]) ? new Held(json.getInt(Held.NAMES[0]),
        json.getInt(Held.NAMES[1]), json.getInt(Held.NAMES[2]), json.getInt(Held.NAMES[3])) : null;
    return new F2lRotationFigure(json.getInt("solves"), json.getInt("pairs"),
        json.getInt("rotations"), json.getInt("rotated_pairs"), json.getInt("basic_pairs"),
        json.getInt("basic_rotated_pairs"), held,
        json.has("excess_ms") ? Long.valueOf(json.getLong("excess_ms")) : null, cases);
  }

  /**
   * The basic pairs whose slot sat in front as they began, and those whose slot sat at the back,
   * each with how many had a rotation. A pair the gyro could not place is in neither.
   */
  public static final class Held {

    static final String[] NAMES =
        {"front_pairs", "front_rotated_pairs", "back_pairs", "back_rotated_pairs"};

    private final int frontPairs;
    private final int frontRotatedPairs;
    private final int backPairs;
    private final int backRotatedPairs;

    Held(int frontPairs, int frontRotatedPairs, int backPairs, int backRotatedPairs) {
      this.frontPairs = frontPairs;
      this.frontRotatedPairs = frontRotatedPairs;
      this.backPairs = backPairs;
      this.backRotatedPairs = backRotatedPairs;
    }

    public int getFrontPairs() {
      return frontPairs;
    }

    public int getFrontRotatedPairs() {
      return frontRotatedPairs;
    }

    public int getBackPairs() {
      return backPairs;
    }

    public int getBackRotatedPairs() {
      return backRotatedPairs;
    }

    Integer field(String name) {
      int[] values = {frontPairs, frontRotatedPairs, backPairs, backRotatedPairs};
      for (int i = 0; i < NAMES.length; i++) {
        if (NAMES[i].equals(name)) {
          return Integer.valueOf(values[i]);
        }
      }
      return null;
    }
  }

  /**
   * One case in one slot: how many of its pairs there were, and how many of them had a rotation.
   * A payload older than 14 counts each case over every slot, with no slot named.
   */
  public static final class Case {

    private final String code;
    private final String slot;
    private final int count;
    private final int rotated;

    Case(String code, String slot, int count, int rotated) {
      this.code = code;
      this.slot = slot;
      this.count = count;
      this.rotated = rotated;
    }

    public String getCode() {
      return code;
    }

    /** Where the slot sat in the grip ({@link PairRotation#FRONT_RIGHT}...), null before 14. */
    public String getSlot() {
      return slot;
    }

    public boolean isFront() {
      return PairRotation.FRONT_RIGHT.equals(slot) || PairRotation.FRONT_LEFT.equals(slot);
    }

    public boolean isBack() {
      return PairRotation.BACK_RIGHT.equals(slot) || PairRotation.BACK_LEFT.equals(slot);
    }

    /** The case dealt into its slot, {@code pair_27_fl}, as a drill names it; the code before 14. */
    public String key() {
      return slot == null ? code : code + "_" + slot;
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
