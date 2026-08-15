package com.cube.nanotimer.session;

import java.util.List;

/**
 * Whether the solver can execute a case in one algorithm without being shown it, per case, worked
 * out from what they have actually done rather than from a list they filled in. A hand-kept list of
 * 78 rows goes stale the moment it is written; this one cannot.
 *
 * <p><b>It is not a speed.</b> How fast a case runs is its own figure and has its own home, and
 * keeping the two apart is what makes <em>"known but slow"</em> sayable at all — which is the most
 * actionable of the three things a slow case can mean. Slow because it is unlearned, slow because it
 * is learned but not recognised under pressure, and slow because it is deliberately taken in two
 * looks are three diagnoses with three different fixes.
 *
 * <p><b>What counts as an occurrence.</b> A solve that was handed the case and a drill rep of it,
 * each saying one thing: whether it went in one algorithm, unaided. A last layer step recorded in
 * two or more algorithms did not; a drill rep whose algorithm had to be shown did not. A rep that
 * was abandoned or restarted says neither — it says the rep was fumbled, and the fact here is
 * deliberately not about fluency.
 *
 * <p><b>Absence is never evidence.</b> A rare OLL turns up once in sixty solves, so a case under
 * {@link #FLOOR} occurrences has no status at all rather than a bad one, and nothing downstream may
 * read that silence as "does not know it". The only honest thing to say about such a case is to
 * drill it and find out.
 */
public class CaseKnowledge {

  /** Where a case stands, or nothing at all when too little has been seen of it. */
  public enum Status {

    /** Executed in one algorithm, unaided, and recently enough for that still to be true. */
    KNOWN("known"),

    /** Reached for more than one algorithm, or had to be shown one. */
    LEARNING("learning");

    private final String code;

    Status(String code) {
      this.code = code;
    }

    /** Stored and sent under this, never under the enum's own name or its position. */
    public String code() {
      return code;
    }

    public static Status forCode(String code) {
      for (Status status : values()) {
        if (status.code.equals(code)) {
          return status;
        }
      }
      return null;
    }
  }

  /** How many of a case's most recent occurrences the status is read from. A case learned in March
   * is not held against the solver, and one since dropped does not stay green. */
  public static final int WINDOW = 10;

  /** Below this many occurrences in that window there is no status. */
  public static final int FLOOR = 3;

  /** How many occurrences in a row must disagree before the status flips, so it does not flap on a
   * single bad solve. */
  private static final int FLIP_AFTER = 2;

  private final String caseSet;
  private final String caseCode;
  private final Status status;
  private final int evidenceCount;
  private final long lastSeenMs;

  /**
   * @param caseSet the family the case belongs to, "oll" — kept apart from the code so a COLL or a
   *     CMLL later costs a row and not a migration
   * @param evidenceCount how many occurrences the status was read from, which is at most
   *     {@link #WINDOW}
   */
  public CaseKnowledge(String caseSet, String caseCode, Status status, int evidenceCount,
      long lastSeenMs) {
    this.caseSet = caseSet;
    this.caseCode = caseCode;
    this.status = status;
    this.evidenceCount = evidenceCount;
    this.lastSeenMs = lastSeenMs;
  }

  /**
   * Where a case stands over its last occurrences.
   *
   * <p>It starts at {@link Status#LEARNING} and is not seeded from the oldest occurrence, so
   * {@link Status#KNOWN} is only ever reached by evidence of the case going in unaided — never by
   * the window happening to open on a good solve.
   *
   * @param occurrences oldest first, each true where the case went in one algorithm unaided
   * @return the status, or null when there is too little to say
   */
  public static Status read(List<Boolean> occurrences) {
    if (occurrences == null || occurrences.size() < FLOOR) {
      return null;
    }
    Status status = Status.LEARNING;
    int against = 0;
    for (Boolean occurrence : occurrences) {
      Status says = occurrence.booleanValue() ? Status.KNOWN : Status.LEARNING;
      against = says == status ? 0 : against + 1;
      if (against >= FLIP_AFTER) {
        status = says;
        against = 0;
      }
    }
    return status;
  }

  /** The code a solve and a drill both record the case under, "oll_53". */
  public String getCode() {
    return caseSet + "_" + caseCode;
  }

  public String getCaseSet() {
    return caseSet;
  }

  public String getCaseCode() {
    return caseCode;
  }

  public Status getStatus() {
    return status;
  }

  public int getEvidenceCount() {
    return evidenceCount;
  }

  /** When the case last came up, in a solve or in a drill. */
  public long getLastSeenMs() {
    return lastSeenMs;
  }
}
