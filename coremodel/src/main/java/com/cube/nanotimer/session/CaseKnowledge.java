package com.cube.nanotimer.session;

import java.util.ArrayList;
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
 * <p><b>One unaided solve is enough to be known, and two lapses running are enough to stop being
 * it.</b> A rare OLL turns up once in sixty solves, so waiting for a case to come up several times
 * before saying anything left most of a set with nothing said about it for months: putting a case in
 * with one algorithm in a real solve is already about as strong as evidence gets, so the first time
 * it happens the case is known.
 *
 * <p>Coming back down is not symmetrical, deliberately. <b>A single two-look against an unaided
 * record is forgiven</b>, because an algorithm fired in reverse or a case missed once is a slip
 * rather than knowledge lost, and demoting on it made a case with twenty clean executions behind it
 * ask to be reviewed. <b>Two in a row is a pattern</b> and demotes. Consecutive occurrences rather
 * than a window of time, so a case seen twice in six months still reads.
 *
 * <p>The cost of <em>that</em>, in turn: a case slipped on every other time it comes up goes on
 * reading as known, since no two of its lapses are ever adjacent.
 *
 * <p><b>A case not known now is one of two things, and they want opposite advice.</b> One never yet
 * put in with a single algorithm has not been learnt at all, and telling its solver to drill it is
 * noise; one that has been, and was taken in two looks since, is learnt and slipping. The newest
 * occurrence still decides whether a case is known; what separates {@link Status#TO_LEARN} from
 * {@link Status#NEEDS_REVIEW} is whether any occurrence before it was {@link Evidence#UNAIDED}.
 *
 * <p>Both of the statuses below describe rather than accuse.
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

    /** Taken in two looks the last two times it came up, having gone in with one before that. */
    NEEDS_REVIEW("needs_review"),

    /** Taken in two looks, or shown, every time it has ever come up: never yet done in one. */
    TO_LEARN("to_learn");

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

  /**
   * Which reading of the occurrences the stored statuses were worked out under.
   *
   * <p>The statuses live in a table that is a cache of the history and nothing more, and it is
   * refreshed a case at a time as that case comes up again. So <b>changing the rule below does not
   * change what is already stored</b>: a case that has not been solved since would go on showing
   * what the old rule said, for months in the case of a rare OLL, and there is no schema change to
   * hang an upgrade off. Bumping this is what tells the app to throw the table away once and read it
   * back. <b>Bump it for any change to {@link #read}.</b>
   */
  public static final int RULE_VERSION = 4;

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
   * Where a case stands, read from the occurrences that said anything.
   *
   * @param occurrences oldest first
   * @return the status, or null where nothing has been seen that says either way
   */
  public static Status read(List<Evidence> occurrences) {
    if (occurrences == null) {
      return null;
    }
    List<Evidence> said = new ArrayList<Evidence>(); // newest first, the silent ones dropped
    for (int i = occurrences.size() - 1; i >= 0; i--) {
      if (occurrences.get(i) != Evidence.SILENT) {
        said.add(occurrences.get(i));
      }
    }
    if (said.isEmpty()) {
      return null;
    }
    if (said.get(0) == Evidence.UNAIDED) {
      return Status.KNOWN;
    }
    if (!said.contains(Evidence.UNAIDED)) {
      return Status.TO_LEARN; // never once put in with a single algorithm
    }
    return said.get(1) == Evidence.HELPED ? Status.NEEDS_REVIEW : Status.KNOWN;
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
