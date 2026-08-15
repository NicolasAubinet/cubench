package com.cube.nanotimer.gui.widget;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cube.nanotimer.R;
import com.cube.nanotimer.coach.CoachPayload;
import com.cube.nanotimer.coach.FocusArea;
import com.cube.nanotimer.coach.StoredCoachPlan;
import com.cube.nanotimer.drill.DrillSpec;
import com.cube.nanotimer.session.MethodStatistics;
import com.cube.nanotimer.gui.CrossDrillActivity;
import com.cube.nanotimer.gui.DrillActivity;
import com.cube.nanotimer.gui.DrillScreenActivity;
import com.cube.nanotimer.gui.DrillSetupActivity;
import com.cube.nanotimer.smartcube.step.LastLayerDiagram;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.helper.Utils;

/**
 * A plan drawn as cards, whoever wrote it.
 *
 * <p>It renders the plan's codes and the payload's figures, and never the plan's own citations. A
 * claim is checked against the payload elsewhere ({@link StoredCoachPlan#uncited}); here the numbers
 * on screen are the user's own, so a coach that invented one has nothing to print with it. What a
 * coach adds that the device cannot is the prose, and that is the one thing taken from the plan.
 *
 * <p>A reason this version has never heard of still draws: its title is generic, its text is
 * whatever was written, and its drill still launches. An app on Play's release cycle will be older
 * than whatever is sending to it, and a card that refused would take the whole plan down.
 */
public class CoachPlanView {

  private final Activity activity;
  private final LinearLayout container;
  private final LayoutInflater inflater;

  public CoachPlanView(Activity activity, LinearLayout container) {
    this.activity = activity;
    this.container = container;
    this.inflater = LayoutInflater.from(activity);
  }

  public void setPlan(StoredCoachPlan stored) {
    container.removeAllViews();
    if (stored == null) {
      return;
    }
    for (FocusArea area : stored.getPlan().getFocus()) {
      container.addView(areaCard(area, stored.getPayload()));
    }
  }

  private View areaCard(FocusArea area, CoachPayload payload) {
    View card = inflater.inflate(R.layout.coach_focus_area, container, false);
    ((TextView) card.findViewById(R.id.tvCoachAreaTitle)).setText(title(area));

    String text = area.getText() != null ? area.getText() : observation(area, payload);
    TextView tvText = card.findViewById(R.id.tvCoachAreaText);
    tvText.setText(text);
    tvText.setVisibility(text == null ? View.GONE : View.VISIBLE);

    LinearLayout cases = card.findViewById(R.id.llCoachAreaCases);
    for (String code : area.getCodes()) {
      if (payload.value("cases." + code + ".mean_ms") != null) {
        cases.addView(caseRow(cases, area, payload, code));
      }
    }

    bindDrill(card, area);
    return card;
  }

  /**
   * A plan holds one area per family per kind of problem, since a drill cannot deal OLLs and PLLs
   * together, so the two that a solver has both of arrive as two cards saying the same words. The
   * family is what tells them apart, and it is put in the title rather than left to the charts.
   */
  private String title(FocusArea area) {
    if (area.getReason() == null) {
      return activity.getString(R.string.coach_reason_unknown);
    }
    switch (area.getReason()) {
      case SLOW_CASE:
        return familyTitle(area, R.string.coach_reason_slow_case);
      case SLOW_UNDER_PRESSURE:
        return familyTitle(area, R.string.coach_reason_slow_under_pressure);
      case RECOGNITION_HEAVY:
        return activity.getString(R.string.coach_reason_recognition_heavy);
      case TWO_LOOK_OLL:
        return activity.getString(R.string.coach_reason_two_look_oll);
      default:
        // Not the consistency note: it ranks across the whole history and names no family of its own.
        return activity.getString(R.string.coach_reason_inconsistent_case);
    }
  }

  private String familyTitle(FocusArea area, int reason) {
    if (area.getCodes().isEmpty()) {
      return activity.getString(reason);
    }
    String family = MethodStatistics.familyOf(area.getCodes().get(0));
    return activity.getString(R.string.coach_area_family,
        Utils.toSmartCubeStepLocalizedName(activity, family, 0), activity.getString(reason));
  }

  /**
   * The sentence an area with no cases under it is made of, worked out from the payload. A case area
   * says what it means with its rows and gets none.
   */
  private String observation(FocusArea area, CoachPayload payload) {
    if (area.getReason() == FocusArea.Reason.RECOGNITION_HEAVY && !area.getCodes().isEmpty()) {
      String code = area.getCodes().get(0);
      Double recognition = payload.value("families." + code + ".recognition_ms");
      Double mean = payload.value("families." + code + ".mean_ms");
      if (recognition == null || mean == null) {
        return null;
      }
      return activity.getString(R.string.coach_recognition_body,
          Utils.toSmartCubeStepLocalizedName(activity, code, 0), time(recognition), time(mean));
    }
    if (area.getReason() == FocusArea.Reason.TWO_LOOK_OLL) {
      Double edges = payload.value("parts.edges.count");
      Double corners = payload.value("parts.corners.count");
      Double solves = payload.value("windows.families");
      if (edges == null || corners == null || solves == null) {
        return null;
      }
      return activity.getString(R.string.coach_two_look_body,
          (int) Math.min(edges.doubleValue(), corners.doubleValue()), solves.intValue());
    }
    return null;
  }

  private View caseRow(LinearLayout parent, FocusArea area, CoachPayload payload, String code) {
    View row = inflater.inflate(R.layout.coach_case_row, parent, false);

    LastLayerDiagram diagram = LastLayerDiagram.forCase(code);
    LastLayerCaseView chart = row.findViewById(R.id.vCoachCaseChart);
    chart.setDiagram(diagram);
    chart.setVisibility(diagram == null ? View.GONE : View.VISIBLE);

    ((TextView) row.findViewById(R.id.tvCoachCaseName))
        .setText(Utils.toSmartCubeCaseHeadline(activity, code));
    fill(row, R.id.tvCoachCaseFigures, figures(area, payload, code));
    fill(row, R.id.tvCoachCaseCost, cost(area, payload, code));
    return row;
  }

  /** What the case costs, said the way its reason wants it said. */
  private String figures(FocusArea area, CoachPayload payload, String code) {
    if (area.getReason() == FocusArea.Reason.SLOW_UNDER_PRESSURE) {
      Double drilled = payload.value("drill_cases." + code + ".mean_ms");
      Double gap = payload.value("comparisons." + code + ".gap_ms");
      if (drilled != null && gap != null) {
        return activity.getString(R.string.coach_case_gap, time(drilled), time(gap));
      }
    }
    Double mean = payload.value("cases." + code + ".mean_ms");
    Double family = payload.value("cases." + code + ".family_mean_ms");
    return mean == null || family == null ? null
        : activity.getString(R.string.coach_case_mean, time(mean), time(family));
  }

  /** The second line: how often, and what that has added up to. */
  private String cost(FocusArea area, CoachPayload payload, String code) {
    if (area.getReason() == FocusArea.Reason.INCONSISTENT_CASE) {
      Double rate = payload.value("cases." + code + ".rejection_rate");
      return rate == null ? null : activity.getString(R.string.coach_case_rejected,
          (int) Math.round(rate.doubleValue() * 100));
    }
    Double count = payload.value("cases." + code + ".count");
    Double lost = payload.value("cases." + code + ".time_lost_ms");
    return count == null || lost == null ? null
        : activity.getString(R.string.coach_case_cost, count.intValue(), time(lost));
  }

  private static void fill(View row, int id, String text) {
    TextView view = row.findViewById(id);
    view.setText(text);
    view.setVisibility(text == null ? View.GONE : View.VISIBLE);
  }

  /**
   * A drill written by a version newer than this app is one area that cannot be practised, not a
   * plan that cannot be read, so the spec is parsed here rather than when the plan was.
   */
  private void bindDrill(View card, FocusArea area) {
    Button button = card.findViewById(R.id.buCoachAreaDrill);
    if (area.getDrill() == null) {
      return;
    }
    final DrillSpec spec;
    try {
      spec = DrillSpec.fromJson(area.getDrill());
    } catch (IllegalArgumentException e) {
      return;
    }
    button.setVisibility(View.VISIBLE);
    // A cross drill is named by no case at all, so counting its case list would label it "these cases".
    button.setText(spec.getType() == DrillSpec.Type.CROSS ? R.string.drill_cross_title
        : spec.getCases().size() == 1 ? R.string.coach_drill_case : R.string.coach_drill_cases);
    button.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        launch(spec);
      }
    });
  }

  /**
   * Straight into the runner rather than through the setup screen: the plan already chose the cases
   * and the count, and the only thing left to ask is which way up the cube is held, which is the
   * user's own standing answer. Recorded, since a prescribed drill's reps are what the next plan
   * reads its solve-against-drill gap from.
   */
  private void launch(DrillSpec spec) {
    Intent intent;
    if (spec.getType() == DrillSpec.Type.CROSS) {
      intent = new Intent(activity, CrossDrillActivity.class);
      intent.putExtra(CrossDrillActivity.EXTRA_SPEC, spec.toJson());
    } else {
      intent = new Intent(activity, DrillActivity.class);
      intent.putExtra(DrillActivity.EXTRA_SPEC, spec.toJson());
      intent.putExtra(DrillActivity.EXTRA_LAYER_FACE, DrillSetupActivity.layerFace().name());
    }
    intent.putExtra(DrillScreenActivity.EXTRA_RECORDING, true);
    activity.startActivity(intent);
  }

  private String time(Double ms) {
    return FormatterService.INSTANCE.formatSolveTime(Long.valueOf(Math.round(ms.doubleValue())));
  }
}
