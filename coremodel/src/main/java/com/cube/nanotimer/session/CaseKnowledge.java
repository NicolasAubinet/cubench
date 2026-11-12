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
 * <p><b>What counts as an occurrence.</b> A solve that was handed the case, and a drill rep of it.
 * A solve says {@link Evidence#UNAIDED} where its last layer step went in one algorithm and
 * {@link Evidence#HELPED} where it took more.
 *
 * <p><b>Drill reps count as a run, never one at a time.</b> A drill never names the case, so a clean
 * rep was recognised, but out of a pool the solver picked and with no solve around it. That is
 * weaker than a solve, so it takes {@link #RUN_TO_KNOW} clean reps in a row to call a case known, or
 * {@link #RUN_TO_KNOW_LARGE_POOL} from a pool of at least {@link #LARGE_POOL} cases, where picking
 * the case out of many is most of the work. A rep with the algorithm looked up or on screen starts
 * the run again and says nothing else: the panel is also opened to compare algorithms, so only a
 * solve moves a case down. A restarted rep breaks the run the same way; a skipped one never reaches
 * the rule at all.
 *
 * <p><b>A learning drill's clear is its own evidence.</b> That drill decides with rules of its own
 * (a reset takes one dot back, a rep on another algorithm empties them), and its reps may not even
 * be recorded, so the clear itself is stored and read here as a drilled run.
 *
 * <p><b>Known from drills alone gets no grace.</b> The single lapse forgiven above is forgiven only
 * where a solve has put the case in alone since the lapse before it. A case drills made known and
 * then taken in two looks in its next solve has shown what drills cannot: it needs review.
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

    /** Done in one look at some point: known now, or known and slipping since. */
    public boolean isLearned() {
      return this == KNOWN || this == NEEDS_REVIEW;
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
  public static final int RULE_VERSION = 8;

  /** Clean drill reps in a row that make a case known. */
  public static final int RUN_TO_KNOW = 5;

  /** The same, for reps dealt out of a pool of at least {@link #LARGE_POOL} cases. */
  public static final int RUN_TO_KNOW_LARGE_POOL = 3;

  /** The fewest cases a drill deals from for its reps to count as a large pool. */
  public static final int LARGE_POOL = 10;

  /** What one occurrence of a case says about whether the solver knows it. */
  public enum Evidence {

    /** A solve whose last layer step went in one algorithm. */
    UNAIDED,

    /** A solve whose last layer step took more than one algorithm. */
    HELPED,

    /** A clean drill rep, dealt out of a small pool. */
    CLEAN_REP,

    /** A clean drill rep, dealt out of at least {@link #LARGE_POOL} cases. */
    CLEAN_REP_LARGE_POOL,

    /** A drill rep turned with the algorithm looked up or on screen, or restarted. */
    LOOKED_UP,

    /** A learning drill cleared the case: its run of reps turned without looking hit the target. */
    CLEARED,
  }

  /** What first made a case learned, stored beside its status. */
  public enum Source {
    SOLVE("solve"), DRILL("drill"), LEARNING_DRILL("learning_drill");

    private final String code;

    Source(String code) {
      this.code = code;
    }

    /** Stored under this, never under the enum's own name or its position. */
    public String code() {
      return code;
    }
  }

  /** What the occurrences come to once drill reps are read as runs. */
  private enum Said {
    SOLVED, DRILLED, LAPSED
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
   *     how many times it came up: a whole run of drill reps is one of them
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
   * Where a case stands, read from its occurrences.
   *
   * @param occurrences oldest first
   * @return the status, or null where nothing has been seen that says either way
   */
  public static Status read(List<Evidence> occurrences) {
    List<Said> said = said(occurrences);
    if (said.isEmpty()) {
      return null;
    }
    if (said.get(0) != Said.LAPSED) {
      return Status.KNOWN;
    }
    if (!said.contains(Said.SOLVED) && !said.contains(Said.DRILLED)) {
      return Status.TO_LEARN; // never once put in with a single algorithm
    }
    for (int i = 1; i < said.size() && said.get(i) != Said.LAPSED; i++) {
      if (said.get(i) == Said.SOLVED) {
        return Status.KNOWN; // one lapse against a solve-proven record is a slip
      }
    }
    return Status.NEEDS_REVIEW;
  }

  /** How many occurrences said anything, a whole run of drill reps counting as one. */
  public static int evidenceCount(List<Evidence> occurrences) {
    return said(occurrences).size();
  }

  /**
   * The occurrence that first made the case learned, or -1 for one never done in one algorithm. A
   * drill run is placed at the rep that completed it.
   *
   * @param occurrences oldest first
   */
  public static int firstLearned(List<Evidence> occurrences) {
    List<Integer> at = new ArrayList<Integer>();
    List<Said> said = said(occurrences, at);
    for (int i = said.size() - 1; i >= 0; i--) {
      if (said.get(i) != Said.LAPSED) {
        return at.get(i);
      }
    }
    return -1;
  }

  /** Where an occurrence {@link #firstLearned} points at came from. */
  public static Source sourceOf(Evidence occurrence) {
    return occurrence == Evidence.UNAIDED ? Source.SOLVE
        : occurrence == Evidence.CLEARED ? Source.LEARNING_DRILL : Source.DRILL;
  }

  private static List<Said> said(List<Evidence> occurrences) {
    return said(occurrences, new ArrayList<Integer>());
  }

  /**
   * Newest first. A run counts once, and a lapse in a solve breaks it as a lookup does. A learning
   * drill's clear stands for the run its reps make, so the two never count twice.
   *
   * @param at filled alongside, newest first: which occurrence each entry was said at
   */
  private static List<Said> said(List<Evidence> occurrences, List<Integer> at) {
    List<Said> said = new ArrayList<Said>();
    if (occurrences == null) {
      return said;
    }
    int run = 0;
    int largeRun = 0;
    boolean counted = false;
    for (int i = 0; i < occurrences.size(); i++) {
      Evidence occurrence = occurrences.get(i);
      if (occurrence == Evidence.UNAIDED) {
        said.add(0, Said.SOLVED);
        at.add(0, i);
      } else if (occurrence == Evidence.HELPED || occurrence == Evidence.LOOKED_UP) {
        if (occurrence == Evidence.HELPED) {
          said.add(0, Said.LAPSED);
          at.add(0, i);
        }
        run = 0;
        largeRun = 0;
        counted = false;
      } else if (occurrence == Evidence.CLEARED) {
        if (!counted) {
          said.add(0, Said.DRILLED);
          at.add(0, i);
          counted = true;
        }
      } else {
        run++;
        largeRun = occurrence == Evidence.CLEAN_REP_LARGE_POOL ? largeRun + 1 : 0;
        if (!counted && (run >= RUN_TO_KNOW || largeRun >= RUN_TO_KNOW_LARGE_POOL)) {
          said.add(0, Said.DRILLED);
          at.add(0, i);
          counted = true;
        }
      }
    }
    return said;
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
