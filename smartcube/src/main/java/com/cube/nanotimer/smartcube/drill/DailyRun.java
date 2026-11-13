package com.cube.nanotimer.smartcube.drill;

import com.cube.nanotimer.drill.DrillSpec;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How the coach's daily drill deals its cases: block by block in solve order, never mixed, each
 * block's cases in turn. A case needing review is dealt until it is clean {@link #REVIEW_RUN}
 * times running, at most its planned reps; any other case gets its planned reps (owner,
 * 2026-11-13). Read from the service's answer to {@code POST /v1/daily}.
 */
public final class DailyRun implements DrillSession.Dealer {

  /** Clean reps in a row that bring a case needing review back to Known. */
  public static final int REVIEW_RUN = 3;

  /** Why a case is in today's drill, as the service names it. */
  public static final String REVIEW = "review";
  public static final String SLIP = "slip";
  public static final String PAUSE = "pause";
  public static final String DUE = "due";
  public static final String FOCUS = "focus";
  public static final String COST = "cost";

  /** One case of the day: why it is here, the figure saying so, and its planned reps. */
  public static final class Entry {
    private final String code;
    private final String reason;
    private final Integer days;
    private final String slipKind;
    private final Long recognitionMs;
    private final int reps;

    Entry(String code, String reason, Integer days, String slipKind, Long recognitionMs,
        int reps) {
      this.code = code;
      this.reason = reason;
      this.days = days;
      this.slipKind = slipKind;
      this.recognitionMs = recognitionMs;
      this.reps = Math.max(1, reps);
    }

    public String getCode() {
      return code;
    }

    public String getReason() {
      return reason;
    }

    /** Days since the slip for a review or a slip, since last seen for a due case; or null. */
    public Integer getDays() {
      return days;
    }

    public String getSlipKind() {
      return slipKind;
    }

    public Long getRecognitionMs() {
      return recognitionMs;
    }

    public int getReps() {
      return reps;
    }

    public boolean isReview() {
      return REVIEW.equals(reason);
    }
  }

  /** One family's cases, in the order the service ranked them. */
  public static final class Block {
    private final String family;
    private final List<Entry> entries;

    Block(String family, List<Entry> entries) {
      this.family = family;
      this.entries = Collections.unmodifiableList(entries);
    }

    public String getFamily() {
      return family;
    }

    public List<Entry> getEntries() {
      return entries;
    }
  }

  private final List<Block> blocks;
  private final Map<String, Integer> dealt = new HashMap<String, Integer>();
  private final Map<String, Integer> cleanRun = new HashMap<String, Integer>();
  private final Map<String, Integer> cleanCount = new HashMap<String, Integer>();
  private final Set<String> slipped = new LinkedHashSet<String>();
  private String lastDealt;

  public DailyRun(List<Block> blocks) {
    this.blocks = Collections.unmodifiableList(new ArrayList<Block>(blocks));
  }

  /** The service's answer, its blocks as sent; empty for text that is no answer. */
  public static List<Block> parse(String reply) {
    List<Block> blocks = new ArrayList<Block>();
    if (reply == null) {
      return blocks;
    }
    try {
      JSONArray array = new JSONObject(reply).getJSONArray("blocks");
      for (int i = 0; i < array.length(); i++) {
        JSONObject block = array.getJSONObject(i);
        JSONArray cases = block.getJSONArray("cases");
        List<Entry> entries = new ArrayList<Entry>();
        for (int j = 0; j < cases.length(); j++) {
          JSONObject one = cases.getJSONObject(j);
          entries.add(new Entry(one.getString("code"), one.optString("reason", COST),
              one.has("days") ? Integer.valueOf(one.getInt("days")) : null,
              one.has("slip_kind") ? one.getString("slip_kind") : null,
              one.has("recognition_ms") ? Long.valueOf(one.getLong("recognition_ms")) : null,
              one.optInt("reps", 2)));
        }
        if (!entries.isEmpty()) {
          blocks.add(new Block(block.getString("family"), entries));
        }
      }
    } catch (JSONException e) {
      return new ArrayList<Block>();
    }
    return blocks;
  }

  /** The blocks with only the cases this app can deal, and any block left empty dropped. */
  public static List<Block> dealable(List<Block> blocks, Collection<String> dealable) {
    List<Block> kept = new ArrayList<Block>();
    for (Block block : blocks) {
      List<Entry> entries = new ArrayList<Entry>();
      for (Entry entry : block.entries) {
        if (dealable.contains(entry.code)) {
          entries.add(entry);
        }
      }
      if (!entries.isEmpty()) {
        kept.add(new Block(block.family, entries));
      }
    }
    return kept;
  }

  /** The drill that runs the day: every case in order, as many reps as it could take. */
  public static DrillSpec spec(List<Block> blocks, String label) {
    List<String> cases = new ArrayList<String>();
    int reps = 0;
    for (Block block : blocks) {
      for (Entry entry : block.entries) {
        cases.add(entry.code);
        reps += entry.reps;
      }
    }
    return new DrillSpec(DrillSpec.DAILY_ID, DrillSpec.Type.CASE_EXECUTION,
        DrillSpec.Delivery.VIRTUAL, cases, DrillSpec.Selection.ROUND_ROBIN, Math.max(1, reps), 0,
        label);
  }

  public List<Block> getBlocks() {
    return blocks;
  }

  /**
   * The next case: the first block with a case still due, its cases in turn after the one last
   * dealt. Null once every case is done. Reads only what {@link #record} kept, so asking twice
   * gives the same answer.
   */
  @Override
  public String next() {
    for (Block block : blocks) {
      List<Entry> open = new ArrayList<Entry>();
      for (Entry entry : block.entries) {
        if (!isDone(entry)) {
          open.add(entry);
        }
      }
      if (open.isEmpty()) {
        continue;
      }
      int after = -1;
      for (int i = 0; i < block.entries.size(); i++) {
        if (block.entries.get(i).code.equals(lastDealt)) {
          after = i;
        }
      }
      for (int step = 1; step <= block.entries.size(); step++) {
        Entry entry = block.entries.get((after + step + block.entries.size())
            % block.entries.size());
        if (!isDone(entry)) {
          return entry.code;
        }
      }
    }
    return null;
  }

  /** A finished rep of the day, which moves its case on. */
  public void record(DrillRep rep) {
    String code = rep.getCaseCode();
    if (entry(code) == null || rep.isPractice()) {
      return;
    }
    lastDealt = code;
    dealt.put(code, count(dealt, code) + 1);
    if (CaseTally.isClean(rep)) {
      cleanRun.put(code, count(cleanRun, code) + 1);
      cleanCount.put(code, count(cleanCount, code) + 1);
    } else if (rep.getResetCount() > 0 || rep.wasRevealed()) {
      cleanRun.put(code, 0); // a skip says nothing, as in CaseKnowledge
      slipped.add(code);
    }
  }

  public boolean isDone(Entry entry) {
    int done = count(dealt, entry.code);
    if (entry.isReview() && count(cleanRun, entry.code) >= REVIEW_RUN) {
      return true;
    }
    return done >= entry.reps;
  }

  /** The block a case belongs to, or null. */
  public Block blockOf(String code) {
    for (Block block : blocks) {
      for (Entry entry : block.entries) {
        if (entry.code.equals(code)) {
          return block;
        }
      }
    }
    return null;
  }

  public Entry entry(String code) {
    Block block = blockOf(code);
    if (block == null) {
      return null;
    }
    for (Entry entry : block.entries) {
      if (entry.code.equals(code)) {
        return entry;
      }
    }
    return null;
  }

  /** Whether dealing this case opens another block than the last rep's. */
  public boolean opensBlock(String code) {
    return lastDealt != null && code != null && blockOf(code) != blockOf(lastDealt);
  }

  /** Reps of the block done so far, the one being dealt included. */
  public int repInBlock(Block block) {
    int done = 0;
    for (Entry entry : block.entries) {
      done += count(dealt, entry.code);
    }
    return done + 1;
  }

  /**
   * Reps the block will take as it stands: those done, and the fewest each open case still needs,
   * a review case the clean ones its run lacks.
   */
  public int repsInBlock(Block block) {
    int total = 0;
    for (Entry entry : block.entries) {
      int done = count(dealt, entry.code);
      int left = isDone(entry) ? 0 : entry.isReview()
          ? Math.min(REVIEW_RUN - count(cleanRun, entry.code), entry.reps - done)
          : entry.reps - done;
      total += done + left;
    }
    return total;
  }

  /** Review cases that ran clean {@link #REVIEW_RUN} times: back to Known. */
  public List<String> getBackToKnown() {
    List<String> back = new ArrayList<String>();
    for (Block block : blocks) {
      for (Entry entry : block.entries) {
        if (entry.isReview() && count(cleanRun, entry.code) >= REVIEW_RUN) {
          back.add(entry.code);
        }
      }
    }
    return back;
  }

  /** Cases with a rep looked up or restarted today, so they come back tomorrow. */
  public List<String> getSlipped() {
    List<String> still = new ArrayList<String>();
    for (String code : slipped) {
      if (!getBackToKnown().contains(code)) {
        still.add(code);
      }
    }
    return still;
  }

  /** Cases dealt at least once. */
  public int getCasesDealt() {
    return dealt.size();
  }

  public int getCleanReps() {
    int clean = 0;
    for (Integer one : cleanCount.values()) {
      clean += one.intValue();
    }
    return clean;
  }

  public int getRepsDealt() {
    int reps = 0;
    for (Integer one : dealt.values()) {
      reps += one.intValue();
    }
    return reps;
  }

  private static int count(Map<String, Integer> counts, String code) {
    Integer count = counts.get(code);
    return count == null ? 0 : count.intValue();
  }
}
