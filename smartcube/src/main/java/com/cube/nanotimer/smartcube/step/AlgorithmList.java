package com.cube.nanotimer.smartcube.step;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * One case's algorithms the way both tables hand them out, an F2L pair's and a last layer case's
 * alike: one row per algorithm, most voted first, and the few at the front the case is listed with.
 *
 * <p><b>One row per algorithm, not per spelling.</b> A table writes one algorithm several ways: a
 * wide where another row writes a slice, a mirror behind the rotation that puts it on the other hand.
 * Its vote then arrives split across its spellings and understates it, so the rows are folded first,
 * by the same test an execution is matched with, and each keeps the votes of all its spellings.
 *
 * <p><b>Listed means holding most of the votes.</b> Rows are taken most voted first until they hold
 * {@link #COVERED_VOTES} percent of the case's votes. A case one algorithm holds 85% of lists that one
 * alone, and a case the world is split many ways on lists every way it is split. An execution that
 * is none of the listed rows is the one worth pointing out. A case nobody voted on lists every row,
 * having nothing to rank them by.
 *
 * <p><b>It cannot say an execution is bad, and must not be made to.</b> The tables hold the
 * algorithms a case is usually taught with, not every algorithm there is, so an execution off the
 * list is one few people turn, not one nobody should.
 */
final class AlgorithmList {

  /** The share of a case's votes its listed algorithms hold between them, in percent. */
  static final int COVERED_VOTES = 80;

  /**
   * How far ahead the most voted algorithm has to be before it is called the recommended one: half
   * again the next one's votes. A case the world is split on has no recommendation to make.
   */
  static final float CLEAR_LEAD = 1.5f;

  private final List<Row> rows;
  private final int listed;
  private final int votes;
  private final AlgorithmForm.Drawn drawn;
  private final List<List<String>> forms = new ArrayList<List<String>>();

  /**
   * @param tableRows each {moves, votes, the slot it needs empty or ""}, spelled for the cube held
   *     the way the case is drawn
   */
  AlgorithmList(List<String[]> tableRows, AlgorithmForm.Drawn drawn) {
    this.drawn = drawn;
    List<Row> folded = new ArrayList<Row>();
    List<List<String>> foldedForms = new ArrayList<List<String>>();
    int total = 0;
    for (String[] tableRow : tableRows) {
      int held = Integer.parseInt(tableRow[1]);
      total += held;
      int at = AlgorithmForm.indexOfTurning(foldedForms, tableRow[0], drawn);
      if (at < 0) {
        folded.add(new Row(tableRow[0], held, tableRow[2].isEmpty() ? null : tableRow[2]));
        foldedForms.add(AlgorithmForm.comparable(tableRow[0]));
      } else {
        folded.get(at).votes += held;
      }
    }
    Collections.sort(folded, new Comparator<Row>() {
      @Override
      public int compare(Row one, Row other) {
        return other.votes - one.votes; // stable, so equal votes keep the table's order
      }
    });
    this.rows = Collections.unmodifiableList(folded);
    this.votes = total;
    for (Row row : folded) {
      row.share = total == 0 ? 0 : Math.round(row.votes * 100f / total);
      forms.add(AlgorithmForm.comparable(row.moves));
    }
    this.listed = listedCount(folded, total);
  }

  private static int listedCount(List<Row> rows, int votes) {
    if (votes == 0) {
      return rows.size();
    }
    int held = 0;
    for (int i = 0; i < rows.size(); i++) {
      held += rows.get(i).votes;
      if (held * 100 >= COVERED_VOTES * votes) {
        return i + 1;
      }
    }
    return rows.size();
  }

  /** Every algorithm of the case, most voted first. */
  List<Row> all() {
    return rows;
  }

  /** The algorithms the case is listed with, most voted first. */
  List<Row> listed() {
    return rows.subList(0, listed);
  }

  /** Whether the most voted algorithm is far enough ahead of the next listed one to recommend. */
  boolean leadIsClear() {
    return listed > 1 && votes > 0 && rows.get(0).votes >= rows.get(1).votes * CLEAR_LEAD;
  }

  /** Which algorithm the moves are, listed or not, or null for none of them. */
  Row matching(String moves) {
    int at = AlgorithmForm.indexOfTurning(forms, moves, drawn);
    return at < 0 ? null : rows.get(at);
  }

  /**
   * Whether an execution is off the list, and how long it is beside the shortest listed algorithm.
   * Notation nothing can read is not off the list, it is unknown.
   */
  AlgorithmExecution read(String moves) {
    if (moves == null || AlgorithmForm.key(moves) == null) {
      return new AlgorithmExecution(false, 0, 0);
    }
    int at = AlgorithmForm.indexOfTurning(forms, moves, drawn);
    boolean unusual = !rows.isEmpty() && (at < 0 || at >= listed);
    return new AlgorithmExecution(unusual, AlgorithmForm.lengthAsDrawn(moves, drawn), shortest());
  }

  /** The shortest listed algorithm, which is what a long execution is measured against. */
  private int shortest() {
    int shortest = 0;
    for (int i = 0; i < listed; i++) {
      int length = forms.get(i).size();
      if (length > 0 && (shortest == 0 || length < shortest)) {
        shortest = length;
      }
    }
    return shortest;
  }

  /** One algorithm and every spelling of it the table holds, as the most voted one spells it. */
  static final class Row {

    private final String moves;
    private final String emptySlot;
    private int votes;
    private int share;

    private Row(String moves, int votes, String emptySlot) {
      this.moves = moves;
      this.votes = votes;
      this.emptySlot = emptySlot;
    }

    String getMoves() {
      return moves;
    }

    /** The share of its case's votes this algorithm holds, in percent. */
    int getShare() {
      return share;
    }

    /** The slot ("fl", "br") an F2L algorithm needs empty, or null. */
    String getEmptySlot() {
      return emptySlot;
    }
  }
}
