package com.cube.nanotimer.smartcube.step;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which algorithms Cubench smart-cube users turn per case, as the coach service publishes them:
 * the share of a case's sharers who turn each one, in percent. Downloaded or bundled, the source
 * does not matter here.
 *
 * <p><b>Case by case.</b> Only a case the snapshot holds is ordered by it, and only from
 * {@link #CASE_FLOOR} sharers (the service publishes none below that; checked again here). Every
 * other case keeps the vote table's order and shows no figure, so every number shown is our own.
 *
 * <p><b>Only what solves the case is shown.</b> A row is drawn only if its moves solve the case,
 * so a bad snapshot can drop a row but never put a wrong algorithm, or anything but notation, on
 * screen. A case left with no row falls back to the vote table.
 *
 * <p>Equal shares keep the vote table's order. Nothing is ranked by length.
 */
public final class UsageFigures {

  /** The sharers a case needs before its figures replace the vote table's order. */
  public static final int CASE_FLOOR = 30;

  public static final UsageFigures NONE = new UsageFigures(Collections.<String, Case>emptyMap());

  private final Map<String, Case> cases;
  private final Map<String, List<CaseAlgorithms.Listed>> shown =
      new ConcurrentHashMap<String, List<CaseAlgorithms.Listed>>();

  /** @param cases per case code ({@code pll_t}, {@code oll_27}, {@code pair_12}) */
  public UsageFigures(Map<String, Case> cases) {
    Map<String, Case> kept = new LinkedHashMap<String, Case>();
    for (Map.Entry<String, Case> entry : cases.entrySet()) {
      if (entry.getValue().users >= CASE_FLOOR && !entry.getValue().shares.isEmpty()) {
        kept.put(entry.getKey(), entry.getValue());
      }
    }
    this.cases = Collections.unmodifiableMap(kept);
  }

  /** How many cases are ordered by these figures. */
  public int size() {
    return cases.size();
  }

  /** The case's algorithms by share, or null where the vote table still decides. */
  List<CaseAlgorithms.Listed> shown(String caseCode, List<CaseAlgorithms.Listed> byVotes) {
    Case figures = cases.get(caseCode);
    if (figures == null) {
      return null;
    }
    List<CaseAlgorithms.Listed> cached = shown.get(caseCode);
    if (cached == null) {
      cached = order(caseCode, figures, byVotes);
      shown.put(caseCode, cached);
    }
    return cached.isEmpty() ? null : cached;
  }

  private static List<CaseAlgorithms.Listed> order(String caseCode, Case figures,
      final List<CaseAlgorithms.Listed> byVotes) {
    List<CaseAlgorithms.Listed> rows = new ArrayList<CaseAlgorithms.Listed>();
    for (Share share : figures.shares) {
      if (share.moves == null || share.moves.trim().isEmpty() || share.percent < 0
          || isShown(caseCode, rows, share.moves)
          || !CaseAlgorithms.solves(caseCode, share.moves)) {
        continue;
      }
      rows.add(new CaseAlgorithms.Listed(share.moves, false,
          CaseAlgorithms.emptySlotOf(caseCode, share.moves), share.percent));
    }
    final Map<CaseAlgorithms.Listed, Integer> voteRank =
        new LinkedHashMap<CaseAlgorithms.Listed, Integer>();
    for (CaseAlgorithms.Listed row : rows) {
      voteRank.put(row, rankIn(caseCode, byVotes, row.getMoves()));
    }
    Collections.sort(rows, new Comparator<CaseAlgorithms.Listed>() {
      @Override
      public int compare(CaseAlgorithms.Listed one, CaseAlgorithms.Listed other) {
        int byShare = Float.compare(other.getShare(), one.getShare());
        return byShare != 0 ? byShare : voteRank.get(one) - voteRank.get(other);
      }
    });
    return Collections.unmodifiableList(rows);
  }

  private static boolean isShown(String caseCode, List<CaseAlgorithms.Listed> rows,
      String moves) {
    return rankIn(caseCode, rows, moves) < Integer.MAX_VALUE;
  }

  private static int rankIn(String caseCode, List<CaseAlgorithms.Listed> rows, String moves) {
    for (int i = 0; i < rows.size(); i++) {
      if (CaseAlgorithms.sameTurning(caseCode, rows.get(i).getMoves(), moves)) {
        return i;
      }
    }
    return Integer.MAX_VALUE;
  }

  /** One case's published figures. */
  public static final class Case {

    private final int users;
    private final List<Share> shares;

    /** @param users the case's counted sharers, the share's denominator */
    public Case(int users, List<Share> shares) {
      this.users = users;
      this.shares = Collections.unmodifiableList(new ArrayList<Share>(shares));
    }
  }

  /** One algorithm and the percentage of the case's sharers who turn it. */
  public static final class Share {

    private final String moves;
    private final float percent;

    public Share(String moves, float percent) {
      this.moves = moves;
      this.percent = percent;
    }
  }
}
