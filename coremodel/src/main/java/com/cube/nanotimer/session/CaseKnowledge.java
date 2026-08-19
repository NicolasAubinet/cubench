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
 * <p><b>The most recent occurrence decides, and one is enough.</b> A rare OLL turns up once in
 * sixty solves, so waiting for a case to come up several times before saying anything left most of
 * a set with nothing said about it for months. Putting a case in with one algorithm in a real solve
 * is already about as strong as evidence gets, so the first time it happens the case is known; and a
 * case taken in two looks since is not known now, whatever it did before.
 *
 * <p>The cost of that is accepted deliberately: <b>one lapse reads as {@link Status#LEARNING}</b>
 * even on a case the solver knows and merely failed to recognise in time. It is the honest reading
 * of what they last did, and LEARNING is a description rather than an accusation.
 *
 * <p><b>What counts as an occurrence.</b> A solve that was handed the case, and a drill rep of it
 * that had to be shown. A solve says {@link Evidence#UNAIDED} where its last layer step went in one
 * algorithm and {@link Evidence#HELPED} where it took more; a rep whose algorithm was revealed says
 * HELPED, since asking is the answer. <b>A clean drill rep says nothing</b>: a drill hands the
 * solver the case with no recognition to survive and no fatigue behind it, so letting one call a
 * case known would collapse the "known but slow" distinction this whole fact exists to make.
 *
 * <p><b>Absence is still never evidence.</b> A case with nothing behind it has no status at all
 * rather than a bad one, and nothing downstream may read that silence as "does not know it".
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

  /** What one occurrence of a case says about whether the solver knows it. */
  public enum Evidence {

    /** A solve whose last layer step went in one algorithm with nothing shown. */
    UNAIDED,

    /** A solve that took more than one algorithm, or a drill rep whose algorithm had to be shown. */
    HELPED,

    /** A clean drill rep, or a rep fumbled and restarted: neither says anything either way. */
    SILENT,
  }

  private final String caseSet;
  private final String caseCode;
  private final Status status;
  private final int evidenceCount;
  private final long lastSeenMs;

  /**
   * @param caseSet the family the case belongs to, "oll" — kept apart from the code so a COLL or a
   *     CMLL later costs a row and not a migration
   * @param evidenceCount how many occurrences of the case said anything either way, which is not
   *     how many times it came up: a clean drill rep is not one of them
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
   * Where a case stands: what the most recent occurrence that said anything said.
   *
   * @param occurrences oldest first
   * @return the status, or null where nothing has been seen that says either way
   */
  public static Status read(List<Evidence> occurrences) {
    if (occurrences == null) {
      return null;
    }
    for (int i = occurrences.size() - 1; i >= 0; i--) { // newest first: the last word wins
      if (occurrences.get(i) == Evidence.UNAIDED) {
        return Status.KNOWN;
      }
      if (occurrences.get(i) == Evidence.HELPED) {
        return Status.LEARNING;
      }
    }
    return null;
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
