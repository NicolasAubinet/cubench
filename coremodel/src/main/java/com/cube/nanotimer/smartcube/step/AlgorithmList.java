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
 * wide where another row writes a slice, a rotation naming the same faces differently. Its vote then
 * arrives split across its spellings and understates it, so rows that turn the very same face turns
 * are folded first, and each keeps the votes of all its spellings.
 *
 * <p><b>A mirror is not folded.</b> Turned with the other hand it is another algorithm to a solver,
 * so it keeps its own row and votes. An execution of a mirror the table has no row for still matches
 * the row it mirrors, which {@link AlgorithmForm#indexOfTurning} sees to.
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
  /** Per spelling only: the rows shown, and the forms an execution is not unusual for. */
  private final List<Row> shown;
  private final List<List<String>> listedForms;

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
      Spelling spelling = new Spelling(tableRow[0], tableRow[2].isEmpty() ? null : tableRow[2]);
      List<String> form = AlgorithmForm.comparable(tableRow[0]);
      int at = form.isEmpty() ? -1 : foldedForms.indexOf(form);
      if (at < 0) {
        folded.add(new Row(spelling, held));
        foldedForms.add(form);
      } else {
        folded.get(at).votes += held;
        folded.get(at).spellings.add(spelling);
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
      forms.add(AlgorithmForm.comparable(row.getMoves()));
    }
    this.listed = listedCount(folded, total);
    this.shown = null;
    this.listedForms = null;
  }

  /**
   * One row per spelling, for a table that counts each slot's votes apart (the F2L table). A
   * spelling is ranked, recommended and shown by its own front-right votes, since two spellings of
   * one algorithm are not the same to the hands. Spellings still fold into one algorithm for what
   * an execution is and whether it is unusual: an algorithm is listed when its spellings hold the
   * front of any one slot's votes, so a back slot's usual insertion is not flagged.
   *
   * @param tableRows each {moves, front-right votes, the slot it needs empty or "", front-left,
   *     back-left and back-right votes}, spelled for the pair going into front right
   */
  static AlgorithmList bySpelling(List<String[]> tableRows, AlgorithmForm.Drawn drawn) {
    return new AlgorithmList(tableRows, drawn, true);
  }

  private AlgorithmList(List<String[]> tableRows, AlgorithmForm.Drawn drawn, boolean bySpelling) {
    this.drawn = drawn;
    List<Row> spelled = new ArrayList<Row>();
    int total = 0;
    for (String[] tableRow : tableRows) {
      Row row = new Row(new Spelling(tableRow[0], tableRow[2].isEmpty() ? null : tableRow[2]),
          Integer.parseInt(tableRow[1]));
      for (int slot = 1; slot < SLOTS; slot++) {
        row.slotVotes[slot] = tableRow.length > slot + 2 ? Integer.parseInt(tableRow[slot + 2]) : 0;
      }
      row.slotVotes[0] = row.votes;
      total += row.votes;
      spelled.add(row);
    }
    Collections.sort(spelled, new Comparator<Row>() {
      @Override
      public int compare(Row one, Row other) {
        return other.votes != one.votes ? other.votes - one.votes
            : other.otherVotes() - one.otherVotes(); // stable: ties keep the table's order
      }
    });
    this.rows = Collections.unmodifiableList(spelled);
    this.votes = total;
    for (Row row : spelled) {
      row.share = total == 0 ? 0 : Math.round(row.votes * 100f / total);
      forms.add(AlgorithmForm.comparable(row.getMoves()));
    }
    for (int i = 0; i < spelled.size(); i++) {
      for (int j = 0; j < spelled.size(); j++) {
        if (!forms.get(i).isEmpty() && forms.get(i).equals(forms.get(j))) {
          spelled.get(i).siblings.add(spelled.get(j));
        }
      }
    }
    List<List<String>> frontRight = listedFolds(spelled, 0);
    List<List<String>> listedFolds = new ArrayList<List<String>>();
    for (int slot = 0; slot < SLOTS; slot++) {
      for (List<String> form : listedFolds(spelled, slot)) {
        addForm(listedFolds, form);
      }
    }
    List<Row> front = new ArrayList<Row>();
    for (int i = 0; i < spelled.size(); i++) {
      Row row = spelled.get(i);
      if (front.isEmpty() || (frontRight.contains(forms.get(i)) && row.share >= SHOWN_SHARE)) {
        front.add(row);
      }
    }
    this.shown = Collections.unmodifiableList(front);
    this.listedForms = Collections.unmodifiableList(listedFolds);
    this.listed = front.size();
  }

  /**
   * The share of a case's front-right votes a spelling needs to be shown, in percent. A full vote
   * list has a long tail of spellings a few people voted for, which a share of the votes would
   * otherwise bring onto the case's sheet; they still read as the algorithm they spell.
   */
  static final int SHOWN_SHARE = 5;

  /** The four slots a table may count votes for: front right, front left, back left, back right. */
  private static final int SLOTS = 4;

  private static boolean addForm(List<List<String>> forms, List<String> form) {
    if (form.isEmpty() || forms.contains(form)) {
      return false;
    }
    return forms.add(form);
  }

  /** The algorithms holding the front of one slot's votes, their spellings' votes together. */
  private List<List<String>> listedFolds(List<Row> spelled, int slot) {
    final List<List<String>> folds = new ArrayList<List<String>>();
    final List<Integer> held = new ArrayList<Integer>();
    int slotTotal = 0;
    for (int i = 0; i < spelled.size(); i++) {
      int slotVotes = spelled.get(i).slotVotes[slot];
      slotTotal += slotVotes;
      List<String> form = forms.get(i);
      if (slotVotes == 0 || form.isEmpty()) {
        continue;
      }
      int at = folds.indexOf(form);
      if (at < 0) {
        folds.add(form);
        held.add(slotVotes);
      } else {
        held.set(at, held.get(at) + slotVotes);
      }
    }
    List<Integer> order = new ArrayList<Integer>();
    for (int i = 0; i < folds.size(); i++) {
      order.add(i);
    }
    Collections.sort(order, new Comparator<Integer>() {
      @Override
      public int compare(Integer one, Integer other) {
        return held.get(other) - held.get(one);
      }
    });
    List<List<String>> listedFolds = new ArrayList<List<String>>();
    int covered = 0;
    for (int at : order) {
      if (covered * 100 >= COVERED_VOTES * slotTotal) {
        break;
      }
      listedFolds.add(folds.get(at));
      covered += held.get(at);
    }
    return listedFolds;
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
    return shown != null ? shown : rows.subList(0, listed);
  }

  /** Whether the most voted algorithm is far enough ahead of the next listed one to recommend. */
  boolean leadIsClear() {
    List<Row> listed = listed();
    return listed.size() > 1 && votes > 0
        && listed.get(0).votes >= listed.get(1).votes * CLEAR_LEAD;
  }

  /**
   * Which algorithm the moves are, listed or not, or null for none of them, written the way the
   * table spells it for the hand it was turned in.
   */
  Row matching(String moves) {
    int at = AlgorithmForm.indexOfTurning(forms, moves, drawn);
    return at < 0 ? null : asTurned(rows.get(at), moves);
  }

  /**
   * The row spelled the way the moves were turned, where one of its spellings was turned that way:
   * the spelling naming the very faces the execution named first, and otherwise one that is the same
   * turning from some other grip.
   *
   * <p><b>A row's spellings are not interchangeable to the solver.</b> They are one algorithm and so
   * one row, but the table writes one of them from the other hand and another from another grip, and
   * handing back the row's own spelling for either writes a solver's R moves on L. Nothing about the
   * execution makes that reading wrong, and everything about the solver's hands does.
   */
  private Row asTurned(Row row, String moves) {
    if (!row.siblings.isEmpty()) {
      return asTurnedSibling(row, moves);
    }
    if (row.spellings.size() == 1) {
      return row;
    }
    List<String> turned = AlgorithmForm.comparable(moves);
    String key = AlgorithmForm.keyAsDrawn(moves, drawn);
    Spelling sameFaces = null;
    Spelling sameTurning = null;
    for (Spelling spelling : row.spellings) {
      if (!turned.isEmpty() && turned.equals(AlgorithmForm.comparable(spelling.moves))
          && (sameFaces == null || rotations(spelling.moves) < rotations(sameFaces.moves))) {
        sameFaces = spelling;
      }
      if (sameTurning == null && key != null
          && key.equals(AlgorithmForm.keyAsDrawn(spelling.moves, drawn))) {
        sameTurning = spelling;
      }
    }
    Spelling spelled = sameFaces != null ? sameFaces : sameTurning;
    return spelled == null ? row : row.spelled(spelled);
  }

  /** As {@link #asTurned}, where each spelling is its own row with its own votes. */
  private Row asTurnedSibling(Row row, String moves) {
    List<String> turned = AlgorithmForm.comparable(moves);
    String key = AlgorithmForm.keyAsDrawn(moves, drawn);
    Row sameFaces = null;
    Row sameTurning = null;
    for (Row sibling : row.siblings) {
      String spelling = sibling.getMoves();
      if (!turned.isEmpty() && turned.equals(AlgorithmForm.comparable(spelling))
          && (sameFaces == null || rotations(spelling) < rotations(sameFaces.getMoves()))) {
        sameFaces = sibling;
      }
      if (sameTurning == null && key != null
          && key.equals(AlgorithmForm.keyAsDrawn(spelling, drawn))) {
        sameTurning = sibling;
      }
    }
    return sameFaces != null ? sameFaces : sameTurning != null ? sameTurning : row;
  }

  /**
   * How many times a spelling turns the cube. Of two spellings of one turning, the one that turns
   * the cube less writes more of it on the faces it is: a solver who turned R is shown R.
   */
  private static int rotations(String moves) {
    int rotations = 0;
    for (String token : moves.trim().split("\\s+")) {
      if (!token.isEmpty() && "xyz".indexOf(token.charAt(0)) >= 0) {
        rotations++;
      }
    }
    return rotations;
  }

  /**
   * Whether an execution is off the list, and how long it is beside the shortest listed algorithm.
   * Notation nothing can read is not off the list, it is unknown.
   */
  AlgorithmExecution read(String moves) {
    if (moves == null || AlgorithmForm.key(moves) == null) {
      return new AlgorithmExecution(false, 0, 0);
    }
    if (listedForms != null) {
      boolean unusual = !rows.isEmpty()
          && AlgorithmForm.indexOfTurning(listedForms, moves, drawn) < 0;
      return new AlgorithmExecution(unusual, AlgorithmForm.lengthAsDrawn(moves, drawn), shortest());
    }
    int at = AlgorithmForm.indexOfTurning(forms, moves, drawn);
    // The other hand's version of a listed algorithm is not unusual, even with its own row unlisted.
    boolean unusual = !rows.isEmpty() && (at < 0 || at >= listed)
        && AlgorithmForm.indexOfTurning(forms.subList(0, listed), moves, drawn) < 0;
    return new AlgorithmExecution(unusual, AlgorithmForm.lengthAsDrawn(moves, drawn), shortest());
  }

  /**
   * The shortest algorithm shown, which is what a long execution is measured against: not one only
   * another slot lists, which a few voters can bring in.
   */
  private int shortest() {
    int shortest = 0;
    for (int i = 0; i < rows.size(); i++) {
      List<String> form = forms.get(i);
      boolean counted = shown != null ? shown.contains(rows.get(i)) : i < listed;
      if (counted && !form.isEmpty() && (shortest == 0 || form.size() < shortest)) {
        shortest = form.size();
      }
    }
    return shortest;
  }

  /** One algorithm and every spelling of it the table holds, as the most voted one spells it. */
  static final class Row {

    private final List<Spelling> spellings = new ArrayList<Spelling>();
    /** Per spelling only: every row spelling the same algorithm, this one included. */
    private final List<Row> siblings = new ArrayList<Row>();
    private final int[] slotVotes = new int[SLOTS];
    private int votes;
    private int share;

    private Row(Spelling spelling, int votes) {
      this.spellings.add(spelling);
      this.votes = votes;
    }

    String getMoves() {
      return spellings.get(0).moves;
    }

    /** The share of its case's votes this algorithm holds, in percent. */
    int getShare() {
      return share;
    }

    /** The slot ("fl", "br") an F2L algorithm needs empty, or null. */
    String getEmptySlot() {
      return spellings.get(0).emptySlot;
    }

    /** Its votes in the slots other than front right, for a table that counts them. */
    int otherVotes() {
      return slotVotes[1] + slotVotes[2] + slotVotes[3];
    }

    /**
     * The same algorithm written as one of its other spellings: see {@link AlgorithmList#asTurned}.
     */
    private Row spelled(Spelling spelling) {
      Row row = new Row(spelling, votes);
      row.share = share;
      return row;
    }
  }

  /** One of the ways the table writes an algorithm, with what that way of writing it needs. */
  private static final class Spelling {

    private final String moves;
    private final String emptySlot;

    private Spelling(String moves, String emptySlot) {
      this.moves = moves;
      this.emptySlot = emptySlot;
    }
  }
}
