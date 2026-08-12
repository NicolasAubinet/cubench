package com.cube.nanotimer.gui;

import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.App;
import com.cube.nanotimer.R;
import com.cube.nanotimer.gui.widget.LastLayerCaseView;
import com.cube.nanotimer.gui.widget.dialog.CaseAlgorithmsDialog;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.smartcube.step.LastLayerDiagram;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.YesNoListener;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.helper.TimeColorScale;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.util.view.DrillSplitBarView;
import com.cube.nanotimer.vo.drill.DrillCaseAttempt;
import com.cube.nanotimer.vo.drill.DrillCaseRep;

import java.util.ArrayList;
import java.util.List;

/**
 * One case, and every attempt at it in the window the stats screen was left on: what the figures a
 * case is judged by are actually made of.
 *
 * <p>It is here that a figure can be argued with. A mean pulled up by a rep where the cube was
 * knocked, or where the user stopped half way through, is not a measurement of the case, and this
 * is the only screen where such a rep can still be reached once the drill it belongs to is over.
 *
 * <p><b>A rep thrown out is flagged, not deleted.</b> It leaves every figure at once and stays on
 * the list, struck through, so it can be put back by the tap that would otherwise ask about the
 * case. The stored row survives too: a rep pruned by hand is itself a signal, and a reader looking
 * for times far outside the usual wants the ones that were. It is gone from the list the next time
 * the screen is opened, having been disowned rather than hidden.
 *
 * <p>Attempts that were never measurements are shown but not counted: one given up on and one where
 * the algorithm was looked up both say something about the case, and neither is a time.
 */
public class DrillCaseStatsActivity extends NanoTimerActivity {

  public static final String EXTRA_CASE = "drillStatsCase";
  /** Which {@link DrillStatsWindow} was being read, by name, so that opening a case keeps it. */
  public static final String EXTRA_WINDOW = "drillStatsWindow";

  private static final int[] VALUE_IDS = {R.id.tvDrillAttemptRecognition,
      R.id.tvDrillAttemptExecution, R.id.tvDrillAttemptTotal};

  /** A thrown-out line: still legible, and plainly not one of the attempts being read. */
  private static final float DELETED_ALPHA = 0.45f;

  /** The write goes away and is not waited on: the screen has already moved without it. */
  private static final DataCallback<Void> IGNORED = new DataCallback<Void>() {
    @Override
    public void onData(Void data) {
    }
  };

  private String caseCode;
  private DrillStatsWindow window;
  private LinearLayout rows;
  private final List<DrillCaseAttempt> attempts = new ArrayList<DrillCaseAttempt>();
  private final TimeColorScale[] scales = new TimeColorScale[VALUE_IDS.length];

  @SuppressWarnings("unchecked") // the only thing this screen ever retains is its own attempts
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.drill_case_stats);
    setTitle(R.string.drill_case_stats_title);

    caseCode = getIntent().getStringExtra(EXTRA_CASE);
    window = DrillStatsWindow.of(getIntent().getStringExtra(EXTRA_WINDOW));
    rows = findViewById(R.id.llDrillCaseStatsRows);

    ((LastLayerCaseView) findViewById(R.id.vDrillCaseStatsChart))
        .setDiagram(LastLayerDiagram.forCase(caseCode));
    ((TextView) findViewById(R.id.tvDrillCaseStatsName))
        .setText(Utils.toSmartCubeCaseHeadline(this, caseCode));
    ((TextView) findViewById(R.id.tvDrillCaseStatsWindow)).setText(window.getLongLabelId());

    // The attempts survive a rotation rather than being read again, because reading them again
    // would lose the struck lines: a rep thrown out is gone from the query at once, and this screen
    // is the only place it can be put back from.
    List<DrillCaseAttempt> kept = (List<DrillCaseAttempt>) getLastCustomNonConfigurationInstance();
    if (kept == null) {
      load();
    } else {
      attempts.addAll(kept);
      show();
    }
  }

  @Override
  public Object onRetainCustomNonConfigurationInstance() {
    return new ArrayList<DrillCaseAttempt>(attempts);
  }

  private void load() {
    App.INSTANCE.getService().getDrillCaseAttempts(caseCode, window.since(),
        new DataCallback<List<DrillCaseAttempt>>() {
          @Override
          public void onData(final List<DrillCaseAttempt> data) {
            runOnUiThread(new Runnable() {
              @Override
              public void run() {
                attempts.clear();
                attempts.addAll(data);
                show();
              }
            });
          }
        });
  }

  private void show() {
    boolean anything = !attempts.isEmpty();
    findViewById(R.id.llDrillCaseStatsSection).setVisibility(anything ? View.VISIBLE : View.GONE);
    TextView empty = findViewById(R.id.tvDrillCaseStatsEmpty);
    empty.setVisibility(anything ? View.GONE : View.VISIBLE);
    if (!anything) {
      findViewById(R.id.llDrillSummaryCells).setVisibility(View.GONE);
      empty.setText(R.string.drill_case_stats_empty);
      return;
    }

    LayoutInflater inflater = LayoutInflater.from(this);
    rows.removeAllViews();
    for (final DrillCaseAttempt attempt : attempts) {
      final View line = inflater.inflate(R.layout.drill_case_attempt_row, rows, false);
      line.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          // A thrown-out line is put back by the same tap that would otherwise ask about the case.
          // Undoing is not destructive, so unlike throwing one out it asks nothing first.
          if (attempt.getRep().isDeleted()) {
            setDeleted(attempt, false);
          } else {
            DialogUtils.showFragment(DrillCaseStatsActivity.this,
                CaseAlgorithmsDialog.newInstance(caseCode));
          }
        }
      });
      line.setOnLongClickListener(new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
          askToDelete(attempt);
          return true;
        }
      });
      line.setTag(attempt);
      rows.addView(line);
    }
    refresh();
  }

  /** Everything the figures are made of: the attempts that measured the case and were kept. */
  private List<DrillCaseAttempt> counted() {
    List<DrillCaseAttempt> counted = new ArrayList<DrillCaseAttempt>();
    for (DrillCaseAttempt attempt : attempts) {
      if (attempt.isCounted()) {
        counted.add(attempt);
      }
    }
    return counted;
  }

  /**
   * Draws the figures and the lines against the attempts that still count. Called again whenever
   * one stops counting: a rep thrown out for being wild was also the end of the gradient every
   * other line was coloured against.
   */
  private void refresh() {
    List<DrillCaseAttempt> counted = counted();
    buildScales(counted);
    showFigures(counted);
    for (int i = 0; i < rows.getChildCount(); i++) {
      View line = rows.getChildAt(i);
      fill(line, (DrillCaseAttempt) line.getTag());
    }
  }

  private void buildScales(List<DrillCaseAttempt> counted) {
    for (int column = 0; column < VALUE_IDS.length; column++) {
      List<Long> times = new ArrayList<Long>();
      for (DrillCaseAttempt attempt : counted) {
        times.add(value(attempt.getRep(), column));
      }
      scales[column] = new TimeColorScale(this);
      scales[column].setTimes(times, false);
    }
  }

  /** The three cells, in the shape every drill screen reports its figures in. */
  private void showFigures(List<DrillCaseAttempt> counted) {
    boolean timed = !counted.isEmpty();
    findViewById(R.id.llDrillSummaryCells).setVisibility(timed ? View.VISIBLE : View.GONE);
    TextView empty = findViewById(R.id.tvDrillCaseStatsEmpty);
    // Every attempt was a skip, a look-up or was thrown out: there are lines to read and no figures.
    empty.setVisibility(timed ? View.GONE : View.VISIBLE);
    if (!timed) {
      empty.setText(R.string.drill_summary_nothing_timed);
      return;
    }

    long total = 0;
    long recognition = 0;
    long best = Long.MAX_VALUE;
    long worst = 0;
    for (DrillCaseAttempt attempt : counted) {
      DrillCaseRep rep = attempt.getRep();
      total += rep.getTotalMs();
      recognition += rep.getRecognitionMs();
      best = Math.min(best, rep.getTotalMs());
      worst = Math.max(worst, rep.getTotalMs());
    }
    int reps = counted.size();

    setCell(R.id.tvDrillCellKeyOne, R.id.tvDrillCellValueOne, R.id.tvDrillCellSubOne,
        getString(R.string.drill_summary_cell_reps), String.valueOf(reps),
        // Only where they differ: "12 of 12" would be arithmetic dressed as a fact.
        reps == attempts.size() ? "" : getString(R.string.drill_summary_cell_of, attempts.size()));

    ((TextView) findViewById(R.id.tvDrillCellKeyTwo)).setText(R.string.drill_summary_cell_mean);
    ((TextView) findViewById(R.id.tvDrillMeanRecognition))
        .setText(FormatterService.INSTANCE.formatSolveTime(recognition / reps));
    ((TextView) findViewById(R.id.tvDrillMeanExecution))
        .setText(FormatterService.INSTANCE.formatSolveTime((total - recognition) / reps));

    setCell(R.id.tvDrillCellKeyThree, R.id.tvDrillCellValueThree, R.id.tvDrillCellSubThree,
        getString(R.string.drill_summary_cell_best),
        FormatterService.INSTANCE.formatSolveTime(best),
        getString(R.string.drill_case_stats_cell_worst,
            FormatterService.INSTANCE.formatSolveTime(worst)));
  }

  private void fill(View line, DrillCaseAttempt attempt) {
    DrillCaseRep rep = attempt.getRep();
    boolean gone = rep.isDeleted();
    line.setAlpha(gone ? DELETED_ALPHA : 1f);

    TextView when = line.findViewById(R.id.tvDrillAttemptWhen);
    when.setText(when(attempt));
    strikeThrough(when, gone);

    for (int column = 0; column < VALUE_IDS.length; column++) {
      TextView value = line.findViewById(VALUE_IDS[column]);
      strikeThrough(value, gone);
      // An attempt given up on has no time worth printing, which is also why it is coloured by
      // nothing. A looked-up or thrown-out one keeps its figures and stands outside the gradient.
      if (rep.isAbandoned()) {
        value.setText(R.string.drill_case_no_time);
        value.setTextColor(color(R.color.secondary_text));
      } else {
        long time = value(rep, column);
        value.setText(FormatterService.INSTANCE.formatSolveTime(time));
        value.setTextColor(attempt.isCounted() ? scales[column].colorFor(time, false)
            : color(R.color.secondary_text));
      }
    }

    DrillSplitBarView bar = line.findViewById(R.id.vDrillAttemptBar);
    bar.setVisibility(rep.isAbandoned() ? View.INVISIBLE : View.VISIBLE);
    bar.setSplit(rep.getRecognitionMs(), rep.getExecutionMs());

    TextView note = line.findViewById(R.id.tvDrillAttemptNote);
    String text = note(rep);
    note.setText(text);
    note.setVisibility(text == null ? View.GONE : View.VISIBLE);
  }

  /** Asks before throwing an attempt out, naming which one so a line held by accident is not lost. */
  private void askToDelete(final DrillCaseAttempt attempt) {
    if (attempt.getRep().isDeleted()) {
      setDeleted(attempt, false); // a hold on a struck line puts it back, as a tap does
      return;
    }
    // Carries its unit here where it stands in a sentence, unlike in the table where the column
    // heading says what the figure is. An attempt with no time says so instead, and takes no unit.
    String time = attempt.getRep().isAbandoned() ? getString(R.string.drill_rep_skipped)
        : getString(R.string.drill_case_seconds,
            FormatterService.INSTANCE.formatSolveTime(attempt.getRep().getTotalMs()));
    String message = getString(R.string.drill_case_stats_remove_message, when(attempt), time);
    DialogUtils.showDestructiveConfirmDialog(this, R.string.drill_case_remove_title, message,
        R.string.drill_case_remove, R.string.cancel, new YesNoListener() {
          @Override
          public void onYes() {
            setDeleted(attempt, true);
          }
        });
  }

  /** Throws an attempt out of every figure, or puts it back, on the screen and in the database. */
  private void setDeleted(DrillCaseAttempt attempt, boolean gone) {
    attempt.getRep().setDeleted(gone);
    App.INSTANCE.getService().setDrillCaseRepDeleted(attempt.getDrillId(),
        attempt.getRep().getPosition(), gone, IGNORED);
    refresh();
  }

  /**
   * Which attempt a line is. The moment alone does not say: a drill's reps are all dated by the
   * drill, so twenty of them read as the same minute, and its number is what tells them apart.
   */
  private String when(DrillCaseAttempt attempt) {
    return getString(R.string.drill_case_stats_when,
        FormatterService.INSTANCE.formatSessionStart(attempt.getTimestamp()),
        attempt.getRep().getPosition() + 1);
  }

  /** What has to be said about an attempt before its figures are read, or null when nothing has. */
  private String note(DrillCaseRep rep) {
    List<String> parts = new ArrayList<String>();
    // First, because it is the one that says the rest of the line is not being counted. It carries
    // the way back too: nothing else on screen says a struck line can be put back.
    if (rep.isDeleted()) {
      parts.add(getString(R.string.drill_case_removed));
    }
    if (rep.isAbandoned()) {
      parts.add(getString(R.string.drill_rep_skipped));
    }
    // A time reached by looking the algorithm up is real but is not a measure of knowing the case.
    if (rep.wasRevealed()) {
      parts.add(getString(R.string.drill_case_revealed));
    }
    // A time reached on the third go is not a clean one, whatever the clock says.
    if (rep.getResetCount() > 0) {
      parts.add(getResources().getQuantityString(R.plurals.drill_case_stats_resets,
          rep.getResetCount(), rep.getResetCount()));
    }
    if (parts.isEmpty()) {
      return null;
    }
    String separator = getString(R.string.drill_case_note_separator);
    StringBuilder sb = new StringBuilder();
    for (String part : parts) {
      sb.append(sb.length() == 0 ? "" : separator).append(part);
    }
    return sb.toString();
  }

  private static long value(DrillCaseRep rep, int column) {
    switch (column) {
      case 0:
        return rep.getRecognitionMs();
      case 1:
        return rep.getExecutionMs();
      default:
        return rep.getTotalMs();
    }
  }

  private void setCell(int keyId, int valueId, int subId, String key, String value, String sub) {
    ((TextView) findViewById(keyId)).setText(key);
    ((TextView) findViewById(valueId)).setText(value);
    ((TextView) findViewById(subId)).setText(sub);
  }

  private void strikeThrough(TextView view, boolean struck) {
    view.setPaintFlags(struck ? view.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
        : view.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
  }

  private int color(int colorResId) {
    return ContextCompat.getColor(this, colorResId);
  }
}
