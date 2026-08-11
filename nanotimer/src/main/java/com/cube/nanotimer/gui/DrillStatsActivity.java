package com.cube.nanotimer.gui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cube.nanotimer.App;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.gui.widget.DrillStatsTable;
import com.cube.nanotimer.gui.widget.SegmentedControl;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.vo.drill.DrillCaseStats;

import java.util.ArrayList;
import java.util.List;

/**
 * What the drills have added up to: every case that has been practised, what it costs, and which of
 * them is worth the next session.
 *
 * <p>A drill's own summary answers "how did that go"; this answers "what should I work on", and the
 * two questions want different lines. There a case dealt four times is four attempts, here it is one
 * case with four reps behind it. Tapping a case opens the attempts themselves, which is where a
 * figure that looks wrong is checked and where a rep that was never a measurement is thrown out.
 *
 * <p><b>Only the reps that measured something are here.</b> A drill run in the chill mode records
 * nothing at all, and of what is recorded, a rep given up on, one where the algorithm was looked up
 * and one already pruned are all left out. What is left is what the user can claim to know.
 *
 * <p>The window and the family are remembered between visits. Someone who drills OLL comes back to
 * OLL, and picking both again every time would be two taps of tax on the screen's one purpose.
 */
public class DrillStatsActivity extends NanoTimerActivity implements DrillStatsTable.Listener {

  private static final String KEY_FAMILY = "stats_family";
  private static final String KEY_WINDOW = "stats_window";

  private static final int FAMILY_PLL_SEGMENT = 0;
  private static final String FAMILY_PLL = "pll_";
  private static final String FAMILY_OLL = "oll_";

  private SegmentedControl family;
  private SegmentedControl window;
  private DrillStatsTable table;
  /**
   * Which load the screen is waiting for. Every query runs on a thread of its own, so a window with
   * a long history can answer after the short one picked next and paint its figures under the wrong
   * lit segment, with nothing on screen to say so.
   */
  private int loadId;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.drill_stats);
    setTitle(R.string.drill_stats_title);

    family = new SegmentedControl(this, (LinearLayout) findViewById(R.id.llDrillStatsFamily),
        new String[] {getString(R.string.drill_practice_pll),
            getString(R.string.drill_practice_oll)},
        new SegmentedControl.Listener() {
          @Override
          public void onSegmentPicked(int index) {
            Options.INSTANCE.setDrillChoice(KEY_FAMILY, index);
            load();
          }
        });

    DrillStatsWindow[] windows = DrillStatsWindow.values();
    String[] labels = new String[windows.length];
    for (int i = 0; i < windows.length; i++) {
      labels[i] = getString(windows[i].getLabelId());
    }
    window = new SegmentedControl(this, (LinearLayout) findViewById(R.id.llDrillStatsWindow),
        labels, new SegmentedControl.Listener() {
          @Override
          public void onSegmentPicked(int index) {
            Options.INSTANCE.setDrillChoice(KEY_WINDOW, index);
            load();
          }
        });

    // Both clamped, since a stored choice outlives the row of segments it was made in, and a
    // selection outside the row lights no segment at all.
    family.setSelection(clamped(Options.INSTANCE.getDrillChoice(KEY_FAMILY, FAMILY_PLL_SEGMENT), 2));
    window.setSelection(clamped(
        Options.INSTANCE.getDrillChoice(KEY_WINDOW, DrillStatsWindow.ALL.ordinal()),
        windows.length));
    table = new DrillStatsTable(this, this);
  }

  /** Read again on the way back, since a rep thrown out on a case's own screen changes these. */
  @Override
  protected void onResume() {
    super.onResume();
    load();
  }

  @Override
  public void onCasePicked(String caseCode) {
    Intent intent = new Intent(this, DrillCaseStatsActivity.class);
    intent.putExtra(DrillCaseStatsActivity.EXTRA_CASE, caseCode);
    intent.putExtra(DrillCaseStatsActivity.EXTRA_WINDOW, window().name());
    startActivity(intent);
  }

  private void load() {
    final int id = ++loadId;
    App.INSTANCE.getService().getDrillCaseStats(window().since(),
        new DataCallback<List<DrillCaseStats>>() {
          @Override
          public void onData(final List<DrillCaseStats> data) {
            runOnUiThread(new Runnable() {
              @Override
              public void run() {
                if (id == loadId) {
                  show(ofFamily(data));
                }
              }
            });
          }
        });
  }

  /** The window's cases of the family being read. The query answers for both at once. */
  private List<DrillCaseStats> ofFamily(List<DrillCaseStats> all) {
    String prefix = family.getSelection() == FAMILY_PLL_SEGMENT ? FAMILY_PLL : FAMILY_OLL;
    List<DrillCaseStats> picked = new ArrayList<DrillCaseStats>();
    for (DrillCaseStats caseStats : all) {
      if (caseStats.getCaseCode() != null && caseStats.getCaseCode().startsWith(prefix)) {
        picked.add(caseStats);
      }
    }
    return picked;
  }

  private void show(List<DrillCaseStats> caseStats) {
    boolean anything = !caseStats.isEmpty();
    findViewById(R.id.llDrillSummaryCells).setVisibility(anything ? View.VISIBLE : View.GONE);
    findViewById(R.id.llDrillStatsSection).setVisibility(anything ? View.VISIBLE : View.GONE);
    TextView empty = findViewById(R.id.tvDrillStatsEmpty);
    empty.setVisibility(anything ? View.GONE : View.VISIBLE);
    if (!anything) {
      // Two different nothings, and the difference is what the reader has to do about it. The
      // second question is only asked where a window could be hiding a history, and it costs a
      // query, so the answer lands on the line rather than being waited for.
      if (window().since() == 0) {
        empty.setText(R.string.drill_stats_empty);
      } else {
        empty.setText(R.string.drill_stats_empty_window);
        sayWhetherAnythingWasEverDrilled(empty, loadId);
      }
      return;
    }
    showFigures(caseStats);
    table.setStats(caseStats);
  }

  /** Whether a window with nothing in it is an empty window or an empty history. */
  private void sayWhetherAnythingWasEverDrilled(final TextView empty, final int id) {
    App.INSTANCE.getService().getDrillCaseStats(0, new DataCallback<List<DrillCaseStats>>() {
      @Override
      public void onData(final List<DrillCaseStats> data) {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            // Of the family being read: someone who has only ever drilled PLL is not being told to
            // go and record a drill, they are being told they have never drilled an OLL.
            if (id == loadId && ofFamily(data).isEmpty()) {
              empty.setText(R.string.drill_stats_empty);
            }
          }
        });
      }
    });
  }

  /**
   * The cells over the table, from every rep in the window rather than from the cases: a mean of
   * per-case means would weigh a case seen twice as heavily as one seen forty times.
   */
  private void showFigures(List<DrillCaseStats> caseStats) {
    int reps = 0;
    long total = 0;
    long recognition = 0;
    long best = Long.MAX_VALUE;
    String bestCase = null;
    for (DrillCaseStats stats : caseStats) {
      reps += stats.getCount();
      total += stats.getTotalMs();
      recognition += stats.getRecognitionMs();
      if (stats.getBestMs() < best) {
        best = stats.getBestMs();
        bestCase = stats.getCaseCode();
      }
    }

    setCell(R.id.tvDrillCellKeyOne, R.id.tvDrillCellValueOne, R.id.tvDrillCellSubOne,
        getString(R.string.drill_summary_cell_reps), String.valueOf(reps),
        getResources().getQuantityString(R.plurals.drill_stats_cell_cases, caseStats.size(),
            caseStats.size()));

    ((TextView) findViewById(R.id.tvDrillCellKeyTwo)).setText(R.string.drill_summary_cell_mean);
    ((TextView) findViewById(R.id.tvDrillMeanRecognition))
        .setText(FormatterService.INSTANCE.formatSolveTime(recognition / reps));
    ((TextView) findViewById(R.id.tvDrillMeanExecution))
        .setText(FormatterService.INSTANCE.formatSolveTime((total - recognition) / reps));

    setCell(R.id.tvDrillCellKeyThree, R.id.tvDrillCellValueThree, R.id.tvDrillCellSubThree,
        getString(R.string.drill_summary_cell_best),
        FormatterService.INSTANCE.formatSolveTime(best),
        Utils.toSmartCubeCaseHeadline(this, bestCase));
  }

  private void setCell(int keyId, int valueId, int subId, String key, String value, String sub) {
    ((TextView) findViewById(keyId)).setText(key);
    ((TextView) findViewById(valueId)).setText(value);
    ((TextView) findViewById(subId)).setText(sub);
  }

  private static int clamped(int selection, int segments) {
    return Math.min(segments - 1, Math.max(0, selection));
  }

  private DrillStatsWindow window() {
    return DrillStatsWindow.values()[window.getSelection()];
  }
}
