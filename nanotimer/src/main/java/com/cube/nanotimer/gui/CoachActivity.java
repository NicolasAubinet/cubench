package com.cube.nanotimer.gui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cube.nanotimer.App;
import com.cube.nanotimer.R;
import com.cube.nanotimer.coach.CoachPayload;
import com.cube.nanotimer.coach.CoachPayloadBuilder;
import com.cube.nanotimer.coach.CoachPlan;
import com.cube.nanotimer.coach.StepShares;
import com.cube.nanotimer.coach.StoredCoachPlan;
import com.cube.nanotimer.cube.SolveTypeMethod;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.gui.widget.CoachPlanView;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.vo.SolveType;

import java.text.DateFormat;
import java.util.Date;

/**
 * What the week is worth spending on: a few things to work on, the figures behind each, and a drill
 * for the ones there is something to practise.
 *
 * <p>Nothing here writes a plan. One is kept beside the figures it was read from, so it draws again
 * without being worked out again, and the screen renders it without knowing who wrote it.
 */
public class CoachActivity extends NanoTimerActivity {

  private SolveType solveType;
  /** How much history there is, which decides what the screen says when there is no plan. */
  private int solveCount;
  private CoachPlanView planView;
  private TextView tvWritten;
  private LinearLayout llEmpty;
  private LinearLayout llBasis;
  private LinearLayout llBasisRows;
  private LinearLayout llSplits;
  private LinearLayout llSplitRows;
  private TextView tvUncited;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.coach_screen);
    setTitle(R.string.coach_title);

    solveType = (SolveType) getIntent().getSerializableExtra("solveType");
    ((TextView) findViewById(R.id.tvCoachSolveType))
        .setText(Utils.toSolveTypeLocalizedName(this, solveType.getName()));

    tvWritten = findViewById(R.id.tvCoachWritten);
    tvUncited = findViewById(R.id.tvCoachUncited);
    llEmpty = findViewById(R.id.llCoachEmpty);
    llBasis = findViewById(R.id.llCoachBasis);
    llBasisRows = findViewById(R.id.llCoachBasisRows);
    llSplits = findViewById(R.id.llCoachSplits);
    llSplitRows = findViewById(R.id.llCoachSplitRows);
    planView = new CoachPlanView(this, (LinearLayout) findViewById(R.id.llCoachAreas));

    if (!sayIfUnreadable()) {
      load();
    }
  }

  /**
   * Whether this solve type is one the coach cannot read at all, said out loud rather than left to
   * a count that will never be reached. Both answers are facts about the history and not settings:
   * a smart cube only ever drives a 3x3, and the vocabulary a plan is written in is CFOP's, so a
   * Roux sub-step read as a case would be named and drilled as one.
   */
  private boolean sayIfUnreadable() {
    TextView title = findViewById(R.id.tvCoachEmptyTitle);
    TextView text = findViewById(R.id.tvCoachEmpty);
    CubeMethod method = SolveTypeMethod.of(solveType);
    if (solveType.getCubeTypeId() != CubeType.THREE_BY_THREE.getId()) {
      title.setText(R.string.coach_only_3x3_title);
      text.setText(R.string.coach_only_3x3);
    } else if (method != CubeMethod.CFOP) {
      title.setText(R.string.coach_only_cfop_title);
      text.setText(getString(R.string.coach_only_cfop, getString(SolveTypeMethod.nameOf(method))));
    } else {
      return false;
    }
    // The plan kept for this solve type is left where it is: it reads again if the method goes back.
    title.setVisibility(View.VISIBLE);
    llEmpty.setVisibility(View.VISIBLE);
    return true;
  }

  /**
   * How much history there is, then the plan last written from it. Chained rather than run side by
   * side: the count decides what the screen says about the plan, so painting on whichever answered
   * first would show the wrong one of the two for as long as the other took.
   */
  private void load() {
    App.INSTANCE.getService().getCoachSolveCount(solveType, SolveTypeMethod.of(solveType),
        new DataCallback<Integer>() {
          @Override
          public void onData(final Integer solves) {
            App.INSTANCE.getService().getCoachPlan(solveType, CoachPlan.Source.HEURISTIC,
                new DataCallback<StoredCoachPlan>() {
                  @Override
                  public void onData(final StoredCoachPlan stored) {
                    runOnUiThread(new Runnable() {
                      @Override
                      public void run() {
                        solveCount = solves.intValue();
                        show(stored);
                      }
                    });
                  }
                });
          }
        });
  }

  /**
   * Four states, and the first of them is the reason the count is loaded at all: too little history
   * to read, nothing read yet, read and there was nothing thick enough to say, and a plan.
   */
  private void show(StoredCoachPlan stored) {
    boolean readable = solveCount >= CoachPayloadBuilder.PLAN_FLOOR;

    planView.setPlan(stored);
    boolean anything = stored != null && !stored.getPlan().getFocus().isEmpty();
    llEmpty.setVisibility(anything ? View.GONE : View.VISIBLE);
    if (!anything) {
      showEmpty(stored, readable);
    }

    // A plan is shown whenever there is one, even where the history has since fallen under the
    // floor: it was a true reading when it was written, and the date on it says as of when.
    tvWritten.setVisibility(stored == null ? View.GONE : View.VISIBLE);
    if (stored != null) {
      tvWritten.setText(getString(R.string.coach_written,
          DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(stored.getWrittenAt()))));
      showUncited(stored);
      showSplits(stored.getPayload());
      showBasis(stored.getPayload());
    }
    llBasis.setVisibility(stored == null ? View.GONE : View.VISIBLE);
    if (stored == null) {
      llSplits.setVisibility(View.GONE);
    }
  }

  /** What to say instead of a plan, which is a different thing in each of the three cases. */
  private void showEmpty(StoredCoachPlan stored, boolean readable) {
    TextView title = findViewById(R.id.tvCoachEmptyTitle);
    TextView text = findViewById(R.id.tvCoachEmpty);
    if (!readable) {
      title.setVisibility(View.VISIBLE);
      title.setText(R.string.coach_too_few_title);
      text.setText(getString(R.string.coach_too_few, CoachPayloadBuilder.PLAN_FLOOR, solveCount));
      return;
    }
    // Never read and read-but-thin are two different states, and only the second one is a finding.
    title.setVisibility(stored == null ? View.GONE : View.VISIBLE);
    title.setText(R.string.coach_empty_title);
    text.setText(stored == null ? R.string.coach_never_read : R.string.coach_empty);
  }

  /** Here for the plans this app did not write, which is every one of them. */
  private void showUncited(StoredCoachPlan stored) {
    // A figure nobody sent and a case nobody has are the same complaint to a reader, so one line.
    int invented = stored.uncited().size() + stored.unknownCodes().size();
    tvUncited.setVisibility(invented == 0 ? View.GONE : View.VISIBLE);
    tvUncited.setText(getString(R.string.coach_uncited, invented));
  }

  /**
   * Where the time goes, shown whether or not any step was far enough out to earn a card. Steps in
   * proportion is a reading, and a solver who never sees the figures cannot tell that from silence.
   */
  private void showSplits(CoachPayload payload) {
    llSplitRows.removeAllViews();
    StepShares shares = StepShares.of(payload);
    llSplits.setVisibility(shares.isEmpty() ? View.GONE : View.VISIBLE);
    if (shares.isEmpty()) {
      return;
    }
    for (String family : shares.families()) {
      splitRow(family, getString(R.string.coach_split_value,
          percent(shares.actual(family)), percent(shares.expected(family))));
    }
  }

  private void splitRow(String family, String value) {
    View row = LayoutInflater.from(this).inflate(R.layout.coach_basis_row, llSplitRows, false);
    ((TextView) row.findViewById(R.id.tvCoachBasisLabel))
        .setText(Utils.toSmartCubeStepLocalizedName(this, family, 0));
    ((TextView) row.findViewById(R.id.tvCoachBasisValue)).setText(value);
    llSplitRows.addView(row);
  }

  private String percent(Double share) {
    return getString(R.string.coach_percent,
        Integer.valueOf((int) Math.round(share.doubleValue() * 100)));
  }

  /** How much history is behind the plan, so a thin one reads as young rather than as broken. */
  private void showBasis(CoachPayload payload) {
    llBasisRows.removeAllViews();
    basisRow(R.string.coach_basis_solves, payload.getFamilyWindow());
    basisRow(R.string.coach_basis_cases, payload.getCaseWindow());
    basisRow(R.string.coach_basis_named, payload.getCases().size());
    basisRow(R.string.coach_basis_known, payload.getKnownCases().size());
    basisRow(R.string.coach_basis_drills, payload.getDrillWindow());
  }

  private void basisRow(int label, int value) {
    View row = LayoutInflater.from(this).inflate(R.layout.coach_basis_row, llBasisRows, false);
    ((TextView) row.findViewById(R.id.tvCoachBasisLabel)).setText(label);
    ((TextView) row.findViewById(R.id.tvCoachBasisValue)).setText(String.valueOf(value));
    llBasisRows.addView(row);
  }
}
