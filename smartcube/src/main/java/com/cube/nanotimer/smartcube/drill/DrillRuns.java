package com.cube.nanotimer.smartcube.drill;

import com.cube.nanotimer.drill.DrillSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * What earlier runs of a coach card's speed drill left behind, so the next run moves on rather than
 * opening on the same cases all week (owner, 2026-11-21). A run opens on {@link #REVIEWS} cases
 * beaten before, those that took the most reps first, then the cases a run dealt without beating,
 * then ones never dealt; once every case is beaten, it deals them in a random order, drawn from what
 * is saved so the plan card shows the cases the run will open on. The working set brings in the
 * next in line as one is beaten, as it does within a run.
 *
 * <p>A plan card's runs are kept for one set of cases and targets only: a new plan writes new
 * targets, and its first run starts clean. A standing practice set ({@link #PRACTICE_PREFIX}) is
 * fetched again every day with targets following the solver's means, so its runs are kept by its
 * id alone; a case it no longer deals is passed over, one new to it comes as never dealt. Saved as a line of text, {@code <hash>;<case>:<reps>,...}, with {@link #MISSED}
 * for a case dealt and not beaten.
 */
public final class DrillRuns {

  /** Cases beaten in an earlier run that each run opens on. */
  static final int REVIEWS = 2;

  /** A case its latest run dealt and did not beat. */
  static final int MISSED = -1;

  /** How the coach names its standing practice sets, {@code coach-practice-f2l}. */
  static final String PRACTICE_PREFIX = "coach-practice-";

  private final String specHash;
  /** Reps each case took to be beaten in the latest run that dealt it, or {@link #MISSED}. */
  private final Map<String, Integer> cases;

  private DrillRuns(String specHash, Map<String, Integer> cases) {
    this.specHash = specHash;
    this.cases = cases;
  }

  /** Whether a drill keeps its runs: a coach speed drill dealing a few of its cases at a time. */
  public static boolean applies(DrillSpec spec) {
    return spec.isFromCoach() && !spec.isLearning() && spec.retiresCases()
        && spec.getWorkingSet() > 0;
  }

  /** The runs saved as {@code saved} if they were of this spec, or none at all. */
  public static DrillRuns of(DrillSpec spec, String saved) {
    String hash = hashOf(spec);
    Map<String, Integer> cases = new LinkedHashMap<String, Integer>();
    int split = saved == null ? -1 : saved.indexOf(';');
    if (split > 0 && saved.substring(0, split).equals(hash)) {
      for (String entry : saved.substring(split + 1).split(",")) {
        int colon = entry.lastIndexOf(':');
        if (colon > 0) {
          try {
            cases.put(entry.substring(0, colon), Integer.valueOf(entry.substring(colon + 1)));
          } catch (NumberFormatException e) {
            // an entry that does not read is a case never dealt
          }
        }
      }
    }
    return new DrillRuns(hash, cases);
  }

  /** The cases and their targets, which a new plan rewrites; not the JSON, whose key order varies. */
  static String hashOf(DrillSpec spec) {
    if (spec.getId() != null && spec.getId().startsWith(PRACTICE_PREFIX)) {
      return Integer.toHexString(spec.getId().hashCode());
    }
    StringBuilder key = new StringBuilder();
    for (String code : spec.getCases()) {
      key.append(code).append('=').append(spec.getTargetMs(code)).append(',');
    }
    return Integer.toHexString(key.toString().hashCode());
  }

  /** These runs with one more on top, made of {@code reps}: every case it dealt takes its result. */
  public DrillRuns after(DrillSpec spec, List<DrillRep> reps) {
    Map<String, Integer> dealt = new LinkedHashMap<String, Integer>();
    for (DrillRep rep : reps) {
      if (!rep.isPractice()) {
        Integer count = dealt.get(rep.getCaseCode());
        dealt.put(rep.getCaseCode(), count == null ? 1 : count + 1);
      }
    }
    CaseTally tally = CaseTally.of(spec, reps);
    Map<String, Integer> next = new LinkedHashMap<String, Integer>(cases);
    for (Map.Entry<String, Integer> one : dealt.entrySet()) {
      next.put(one.getKey(), tally.isCleared(one.getKey()) ? one.getValue() : MISSED);
    }
    return new DrillRuns(specHash, next);
  }

  /** {@code cases} in the order a new run takes them, the first of them its working set. */
  public List<String> order(List<String> cases) {
    List<String> missed = new ArrayList<String>();
    List<String> fresh = new ArrayList<String>();
    final List<String> beaten = new ArrayList<String>();
    for (String code : cases) {
      Integer result = this.cases.get(code);
      (result == null ? fresh : result == MISSED ? missed : beaten).add(code);
    }
    if (missed.isEmpty() && fresh.isEmpty()) {
      Collections.shuffle(beaten, new Random(toText().hashCode()));
      return beaten;
    }
    Collections.sort(beaten, new Comparator<String>() { // stable, so ties keep the spec's order
      @Override
      public int compare(String a, String b) {
        return Integer.compare(DrillRuns.this.cases.get(b), DrillRuns.this.cases.get(a));
      }
    });
    List<String> run = new ArrayList<String>(beaten.subList(0, Math.min(REVIEWS, beaten.size())));
    run.addAll(missed);
    run.addAll(fresh);
    run.addAll(beaten.subList(run.size() - missed.size() - fresh.size(), beaten.size()));
    return run;
  }

  public String toText() {
    StringBuilder text = new StringBuilder(specHash).append(';');
    for (Map.Entry<String, Integer> one : cases.entrySet()) {
      if (text.charAt(text.length() - 1) != ';') {
        text.append(',');
      }
      text.append(one.getKey()).append(':').append(one.getValue());
    }
    return text.toString();
  }
}
