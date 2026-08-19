package com.cube.nanotimer.gui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cube.nanotimer.App;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.cube.CaseExecutions;
import com.cube.nanotimer.gui.widget.LastLayerCaseView;
import com.cube.nanotimer.gui.widget.SegmentedControl;
import com.cube.nanotimer.gui.widget.dialog.CaseAlgorithmsDialog;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.session.CaseKnowledge;
import com.cube.nanotimer.smartcube.step.LastLayerCaseAlgorithms;
import com.cube.nanotimer.smartcube.step.LastLayerDiagram;
import com.cube.nanotimer.smartcube.step.LastLayerScrambles;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.vo.CaseHistory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which cases the solver puts in with one algorithm and nothing shown, and what they turn for each.
 *
 * <p>Every case of the family is listed, not only the ones with something behind them, because the
 * list is also the answer to "how far through the set am I": a screen showing twelve rows cannot say
 * that nine cases have never come up. The ones that are known come first and the ones with no
 * evidence last, so the top of the list is what has been learnt and the bottom is what is left.
 *
 * <p><b>The moves are the solver's own.</b> They are cut out of their solves rather than stored, so
 * a case shows nothing until it has been answered with a cube on, and what it shows is the usual
 * answer rather than the last one: one odd solve should not rename an algorithm they have been
 * turning for months. Where the answer is one of the listed algorithms it is written the way the
 * table writes it, which is the way the case is drawn; where it is none of them it stands as turned.
 * An execution hardly anybody turns is chipped as unusual, and where it is also longer than what
 * people do turn the row says by how many moves, which is the part the solver can act on.
 *
 * <p><b>Whether a case goes in unaided is said by the heading it sits under, not on every line.</b>
 * Most of a family has nothing said about it, and repeating that down fifty rows would drown the one
 * thing each row is there for. The last group is the cases nothing has been seen of at all, and it
 * is called that rather than unlearnt: silence is not evidence. Tapping a case opens the algorithms
 * it can be solved with, which is where the solver says which one is theirs.
 */
public class KnownAlgorithmsActivity extends NanoTimerActivity {

  /**
   * How many solves are read for the moves. Each is replayed, so this is the cost of the screen; it
   * is deep enough for a case that turns up once in sixty solves to have been answered twice.
   */
  private static final int SOLVES_READ = 120;

  private static final String KEY_FAMILY = "known_algorithms_family";
  private static final int FAMILY_PLL_SEGMENT = 0;
  private static final String FAMILY_PLL = "pll_";
  private static final String FAMILY_OLL = "oll_";

  /**
   * The headings, in the order they are walked: what has been learnt, then what is being learnt,
   * then what there is nothing to say about yet.
   */
  private static final CaseKnowledge.Status[] GROUPS =
      {CaseKnowledge.Status.KNOWN, CaseKnowledge.Status.LEARNING, null};

  private SegmentedControl family;
  private LinearLayout rows;
  private TextView count;
  private TextView empty;
  private TextView hint;

  private final Map<String, CaseKnowledge.Status> statuses =
      new LinkedHashMap<String, CaseKnowledge.Status>();
  /** What each case is shown as, worked out once off the main thread rather than as it is drawn. */
  private Map<String, Shown> turned = new LinkedHashMap<String, Shown>();
  private boolean read;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.known_algorithms);
    setTitle(R.string.known_algorithms_title);

    rows = findViewById(R.id.llKnownAlgorithmsRows);
    count = findViewById(R.id.tvKnownAlgorithmsCount);
    empty = findViewById(R.id.tvKnownAlgorithmsEmpty);
    hint = findViewById(R.id.tvKnownAlgorithmsHint);

    family = new SegmentedControl(this, (LinearLayout) findViewById(R.id.llKnownAlgorithmsFamily),
        new String[] {getString(R.string.drill_practice_pll),
            getString(R.string.drill_practice_oll)},
        new SegmentedControl.Listener() {
          @Override
          public void onSegmentPicked(int index) {
            Options.INSTANCE.setDrillChoice(KEY_FAMILY, index);
            show();
          }
        });
    family.setSelection(
        Math.max(0, Math.min(1, Options.INSTANCE.getDrillChoice(KEY_FAMILY, FAMILY_PLL_SEGMENT))));
  }

  /** Read again on the way back: the algorithm a case is filed under can have changed meanwhile. */
  @Override
  protected void onResume() {
    super.onResume();
    load();
  }

  private void load() {
    App.INSTANCE.getService().getCaseHistory(SOLVES_READ, new DataCallback<CaseHistory>() {
      @Override
      public void onData(CaseHistory history) {
        // Cutting the solves up and naming what they turned is the slow half of this screen, and is
        // deliberately still on the service's thread: a family is up to 57 rows to draw.
        final Map<String, Shown> shown = named(CaseExecutions.readFrom(history.getSolves()));
        final List<CaseKnowledge> cases = history.getCases();
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            statuses.clear();
            for (CaseKnowledge known : cases) {
              statuses.put(known.getCode(), known.getStatus());
            }
            turned = shown;
            read = true;
            show();
          }
        });
      }
    });
  }

  /** Draws the family that is showing, in the order the reader wants to walk it. */
  private void show() {
    rows.removeAllViews();
    String prefix = family.getSelection() == FAMILY_PLL_SEGMENT ? FAMILY_PLL : FAMILY_OLL;
    int size = 0;
    int known = 0;
    for (CaseKnowledge.Status status : GROUPS) {
      List<String> cases = grouped(prefix, status);
      size += cases.size();
      if (status == CaseKnowledge.Status.KNOWN) {
        known = cases.size();
      }
      draw(status, cases);
    }
    count.setText(getString(R.string.known_algorithms_count, known, size));
    boolean nothing = read && turned.isEmpty() && statuses.isEmpty();
    empty.setText(read ? getString(R.string.known_algorithms_empty)
        : getString(R.string.known_algorithms_reading));
    empty.setVisibility(!read || nothing ? View.VISIBLE : View.GONE);
    rows.setVisibility(!read || nothing ? View.GONE : View.VISIBLE);
    hint.setVisibility(!read || nothing ? View.GONE : View.VISIBLE);
  }

  /** One heading and the cases under it, or nothing at all where a group is empty. */
  private void draw(CaseKnowledge.Status status, List<String> cases) {
    if (cases.isEmpty()) {
      return;
    }
    LayoutInflater inflater = LayoutInflater.from(this);
    TextView heading = (TextView) inflater.inflate(R.layout.known_algorithms_section, rows, false);
    heading.setText(status == CaseKnowledge.Status.KNOWN ? R.string.known_algorithms_status_known
        : status == CaseKnowledge.Status.LEARNING ? R.string.known_algorithms_status_learning
        : R.string.known_algorithms_status_unseen);
    rows.addView(heading);

    ViewGroup group = (ViewGroup) inflater.inflate(R.layout.known_algorithms_group, rows, false);
    for (final String code : cases) {
      View row = inflater.inflate(R.layout.known_algorithms_row, group, false);
      row.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          DialogUtils.showFragment(KnownAlgorithmsActivity.this,
              CaseAlgorithmsDialog.newInstance(code));
        }
      });
      fill(row, code);
      group.addView(row);
    }
    rows.addView(group);
  }

  /** One group's cases, in the order the cases are listed everywhere else in the app. */
  private List<String> grouped(String prefix, CaseKnowledge.Status status) {
    List<String> grouped = new ArrayList<String>();
    for (String code : LastLayerScrambles.cases()) {
      if (code.startsWith(prefix) && statuses.get(code) == status) {
        grouped.add(code);
      }
    }
    return grouped;
  }

  private void fill(View row, String code) {
    ((LastLayerCaseView) row.findViewById(R.id.vKnownAlgorithmsChart))
        .setDiagram(LastLayerDiagram.forCase(code));
    ((TextView) row.findViewById(R.id.tvKnownAlgorithmsName))
        .setText(Utils.toSmartCubeCaseHeadline(this, code));
    Shown shown = turned.get(code);
    ((TextView) row.findViewById(R.id.tvKnownAlgorithmsMoves))
        .setText(shown == null ? getString(R.string.known_algorithms_no_moves) : shown.moves);

    boolean unusual = shown != null && shown.execution.isUnusual();
    row.findViewById(R.id.tvKnownAlgorithmsUnusual)
        .setVisibility(unusual ? View.VISIBLE : View.GONE);
    TextView longer = row.findViewById(R.id.tvKnownAlgorithmsLonger);
    boolean says = unusual && shown.execution.isLonger();
    if (says) {
      longer.setText(getString(R.string.case_algorithm_longer, shown.execution.getMoves(),
          shown.execution.getUsualMoves()));
    }
    longer.setVisibility(says ? View.VISIBLE : View.GONE);
  }

  /**
   * What each case is shown as, and how what is turned stands beside the algorithms people use.
   * Neither rule lives here: both are the ones the case dialog reads its own execution with, so the
   * two screens cannot end up disagreeing about the same solve.
   */
  private static Map<String, Shown> named(Map<String, String> executions) {
    Map<String, Shown> named = new LinkedHashMap<String, Shown>();
    for (Map.Entry<String, String> execution : executions.entrySet()) {
      named.put(execution.getKey(), new Shown(
          CaseExecutions.asAlgorithm(execution.getKey(), execution.getValue()),
          LastLayerCaseAlgorithms.read(execution.getKey(), execution.getValue())));
    }
    return named;
  }

  /** One case's row, as far as it can be worked out before there is a view to put it in. */
  private static final class Shown {

    private final String moves;
    private final LastLayerCaseAlgorithms.Execution execution;

    Shown(String moves, LastLayerCaseAlgorithms.Execution execution) {
      this.moves = moves;
      this.execution = execution;
    }
  }
}
