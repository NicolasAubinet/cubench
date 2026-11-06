package com.cube.nanotimer.coach;

/**
 * How one F2L pair of a gyro solve was turned for rotations: how many the cube made, and how long
 * the gaps between face turns took with a rotation in them and without.
 *
 * <p>Read from the gyro and never from the reconstruction's rotation tokens, which unfolded wide
 * moves and frame corrections fill with rotations nobody made. Only a cube with a gyro can say this
 * at all, so a solve without one has no pair here rather than pairs of none.
 *
 * <p>The gaps are kept apart by where they fall, since the first gap of a pair holds its recognition
 * and is slow with or without a rotation: a rotation is set against the gaps of its own position.
 * Gaps over {@link #LONG_GAP_MS} are left out of both sides as lost looks, not rotations.
 */
public final class PairRotation {

  /** A gap this long is a lost look, whatever the cube did during it. */
  public static final long LONG_GAP_MS = 3000;

  /** Where a pair's slot sat in the grip, spelt as a drill's slot code spells it. */
  public static final String FRONT_RIGHT = "fr";
  public static final String FRONT_LEFT = "fl";
  public static final String BACK_RIGHT = "br";
  public static final String BACK_LEFT = "bl";

  private final long solveId;
  private final String caseCode;
  private final boolean basic;
  private final String slot;
  private final int rotations;
  private final Gaps start;
  private final Gaps inside;

  /**
   * @param caseCode the case the pair was handed, {@code pair_27}, or null where none was named
   * @param basic whether that is a basic case, both pieces in the last layer or their own slot
   * @param rotations how many the cube made during the pair, its first gap included
   * @param start the pair's first gap, from the turn before it
   * @param inside the gaps between the pair's own turns
   */
  public PairRotation(long solveId, String caseCode, boolean basic, int rotations, Gaps start,
      Gaps inside) {
    this(solveId, caseCode, basic, null, rotations, start, inside);
  }

  /**
   * As above, with where the pair's slot sat in the grip as the pair began: {@link #FRONT_RIGHT}
   * and the others, or null where the gyro could not say.
   */
  public PairRotation(long solveId, String caseCode, boolean basic, String slot, int rotations,
      Gaps start, Gaps inside) {
    this.solveId = solveId;
    this.caseCode = caseCode;
    this.basic = basic;
    this.slot = slot;
    this.rotations = rotations;
    this.start = start;
    this.inside = inside;
  }

  public long getSolveId() {
    return solveId;
  }

  public String getCaseCode() {
    return caseCode;
  }

  public boolean isBasic() {
    return basic;
  }

  /** Where the slot sat in the grip as the pair began, or null where the gyro could not say. */
  public String getSlot() {
    return slot;
  }

  public boolean isFront() {
    return FRONT_RIGHT.equals(slot) || FRONT_LEFT.equals(slot);
  }

  public boolean isBack() {
    return BACK_RIGHT.equals(slot) || BACK_LEFT.equals(slot);
  }

  public int getRotations() {
    return rotations;
  }

  public Gaps getStart() {
    return start;
  }

  public Gaps getInside() {
    return inside;
  }

  /** Gaps between face turns, those with a rotation in them and those without, counted and summed. */
  public static final class Gaps {

    public static final Gaps NONE = new Gaps(0, 0, 0, 0);

    private final int rotated;
    private final long rotatedMs;
    private final int plain;
    private final long plainMs;

    public Gaps(int rotated, long rotatedMs, int plain, long plainMs) {
      this.rotated = rotated;
      this.rotatedMs = rotatedMs;
      this.plain = plain;
      this.plainMs = plainMs;
    }

    public int getRotated() {
      return rotated;
    }

    public long getRotatedMs() {
      return rotatedMs;
    }

    public int getPlain() {
      return plain;
    }

    public long getPlainMs() {
      return plainMs;
    }

    /** These and {@code other} together. */
    public Gaps plus(Gaps other) {
      return new Gaps(rotated + other.rotated, rotatedMs + other.rotatedMs, plain + other.plain,
          plainMs + other.plainMs);
    }

    /** This with one more gap, left out where it is a lost look. */
    public Gaps with(long gapMs, boolean rotation) {
      if (gapMs > LONG_GAP_MS) {
        return this;
      }
      return rotation ? new Gaps(rotated + 1, rotatedMs + gapMs, plain, plainMs)
          : new Gaps(rotated, rotatedMs, plain + 1, plainMs + gapMs);
    }
  }
}
