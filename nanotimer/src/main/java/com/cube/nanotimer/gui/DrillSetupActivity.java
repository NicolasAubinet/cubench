package com.cube.nanotimer.gui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.cube.SmartCubeManager;
import com.cube.nanotimer.gui.widget.CrossFaceSwatches;
import com.cube.nanotimer.gui.widget.SegmentedControl;
import com.cube.nanotimer.scrambler.cross.CrossFace;
import com.cube.nanotimer.smartcube.drill.DrillSpec;
import com.cube.nanotimer.smartcube.step.LastLayerScrambles;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Picking a drill: what to practise, how many reps of it, and whether the reps count.
 *
 * <p>This is the free "choose what to work on" list, and it is the only way a drill starts here.
 * What the app used to run was one hard-coded set of every PLL, which was enough to build a runner
 * against and is not a way to practise anything in particular.
 *
 * <p><b>The mode is decided here because it cannot be decided later.</b> A set drilled loosely and
 * then claimed as a result at the end would make the drill history worth less than no history, so
 * the choice is made before the first case and is fixed for the whole drill. Stopping and starting
 * another is how it changes.
 *
 * <p>What hangs below the three shared controls depends on the practice. A cross drill is the only
 * one with a colour to pick, and it picks it from the cross solver's own swatches rather than a
 * second set of colours that could drift from them.
 */
public class DrillSetupActivity extends NanoTimerActivity {

  private static final int PRACTICE_PLL = 0;
  private static final int PRACTICE_OLL = 1;
  private static final int PRACTICE_CROSS = 2;

  private static final int[] REP_COUNTS = {10, 20, 30, 50};
  private static final int DEFAULT_PLANNING_SECONDS = 15;

  private static final String KEY_PRACTICE = "practice";
  private static final String KEY_REPS = "reps";
  private static final String KEY_RECORDING = "recording";
  private static final String KEY_CROSS_FACE = "cross_face";
  private static final String KEY_LAYER_FACE = "layer_face";
  private static final String KEY_PLANNING_ON = "planning_on";
  private static final String KEY_PLANNING_SECONDS = "planning_seconds";

  private SegmentedControl practice;
  private SegmentedControl reps;
  private SegmentedControl mode;
  private CrossFaceSwatches crossFaces;
  private CheckBox cbPlanning;
  private EditText etPlanningSeconds;
  private View crossOptions;
  private View planningSeconds;
  private TextView tvPracticeHint;
  private TextView tvModeHint;
  private TextView tvFaceLabel;

  /** The cross a cross drill builds, and the layer a case drill finishes on. Two choices, since a
   * cross colour and a last layer colour are opposite faces for most solvers. */
  private CrossFace crossFace;
  private CrossFace layerFace;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.drill_setup);
    setTitle(R.string.drill_setup_title);

    crossOptions = findViewById(R.id.llDrillCrossOptions);
    planningSeconds = findViewById(R.id.llDrillPlanningSeconds);
    tvPracticeHint = findViewById(R.id.tvDrillPracticeHint);
    tvFaceLabel = findViewById(R.id.tvDrillFaceLabel);
    tvModeHint = findViewById(R.id.tvDrillModeHint);
    cbPlanning = findViewById(R.id.cbDrillPlanning);
    etPlanningSeconds = findViewById(R.id.etDrillPlanningSeconds);

    practice = new SegmentedControl(this, (LinearLayout) findViewById(R.id.llDrillPractice),
        new String[] {getString(R.string.drill_practice_pll), getString(R.string.drill_practice_oll),
            getString(R.string.drill_practice_cross)},
        new SegmentedControl.Listener() {
          @Override
          public void onSegmentPicked(int index) {
            Options.INSTANCE.setDrillChoice(KEY_PRACTICE, index);
            refreshPractice();
          }
        });

    String[] repLabels = new String[REP_COUNTS.length];
    for (int i = 0; i < REP_COUNTS.length; i++) {
      repLabels[i] = String.valueOf(REP_COUNTS[i]);
    }
    reps = new SegmentedControl(this, (LinearLayout) findViewById(R.id.llDrillReps), repLabels,
        new SegmentedControl.Listener() {
          @Override
          public void onSegmentPicked(int index) {
            Options.INSTANCE.setDrillChoice(KEY_REPS, index);
          }
        });

    mode = new SegmentedControl(this, (LinearLayout) findViewById(R.id.llDrillMode),
        new String[] {getString(R.string.drill_mode_casual),
            getString(R.string.drill_mode_recording)},
        new SegmentedControl.Listener() {
          @Override
          public void onSegmentPicked(int index) {
            Options.INSTANCE.setDrillChoice(KEY_RECORDING, index);
            refreshModeHint();
          }
        });

    // The cross solver's own default is where the cross starts, and then keeps its own: a colour
    // drilled to learn it is often not the colour that solver is set to. The last layer defaults to
    // the opposite face, which is where a solver who builds that cross finishes.
    int defaultCross = Options.INSTANCE.getCrossFaceIndex(CrossFace.D.ordinal());
    crossFace = CrossFace.values()[Options.INSTANCE.getDrillChoice(KEY_CROSS_FACE, defaultCross)];
    layerFace = CrossFace.values()[Options.INSTANCE.getDrillChoice(KEY_LAYER_FACE,
        CrossFace.values()[defaultCross].opposite().ordinal())];
    crossFaces = new CrossFaceSwatches(this, (LinearLayout) findViewById(R.id.llDrillCrossSwatches),
        new CrossFaceSwatches.Listener() {
          @Override
          public void onFacePicked(CrossFace picked) {
            if (isCrossDrill()) {
              crossFace = picked;
              Options.INSTANCE.setDrillChoice(KEY_CROSS_FACE, picked.ordinal());
            } else {
              layerFace = picked;
              Options.INSTANCE.setDrillChoice(KEY_LAYER_FACE, picked.ordinal());
            }
            crossFaces.setSelection(picked, null);
          }
        });

    cbPlanning.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton button, boolean checked) {
        Options.INSTANCE.setDrillChoice(KEY_PLANNING_ON, checked ? 1 : 0);
        planningSeconds.setVisibility(checked ? View.VISIBLE : View.GONE);
      }
    });

    findViewById(R.id.btDrillStart).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        start();
      }
    });

    practice.setSelection(Options.INSTANCE.getDrillChoice(KEY_PRACTICE, PRACTICE_PLL));
    reps.setSelection(Options.INSTANCE.getDrillChoice(KEY_REPS, 1));
    mode.setSelection(Options.INSTANCE.getDrillChoice(KEY_RECORDING, 1));
    cbPlanning.setChecked(Options.INSTANCE.getDrillChoice(KEY_PLANNING_ON, 0) == 1);
    etPlanningSeconds.setText(String.valueOf(
        Options.INSTANCE.getDrillChoice(KEY_PLANNING_SECONDS, DEFAULT_PLANNING_SECONDS)));
    refreshPractice();
    refreshModeHint();
  }

  @Override
  protected void onResume() {
    super.onResume();
    // Said here rather than only on the drill screen: being turned back at the door having picked
    // everything is worse than being told at the door.
    findViewById(R.id.tvDrillNoCube).setVisibility(
        SmartCubeManager.INSTANCE.isConnected() ? View.GONE : View.VISIBLE);
  }

  private void refreshPractice() {
    int picked = practice.getSelection();
    boolean cross = picked == PRACTICE_CROSS;
    crossOptions.setVisibility(cross ? View.VISIBLE : View.GONE);
    planningSeconds.setVisibility(cross && cbPlanning.isChecked() ? View.VISIBLE : View.GONE);
    int hint;
    if (cross) {
      hint = R.string.drill_practice_hint_cross;
    } else if (picked == PRACTICE_OLL) {
      hint = R.string.drill_practice_hint_oll;
    } else {
      hint = R.string.drill_practice_hint_pll;
    }
    tvPracticeHint.setText(hint);
    tvFaceLabel.setText(cross ? R.string.drill_cross_colour : R.string.drill_layer_colour);
    crossFaces.setSelection(cross ? crossFace : layerFace, null);
  }

  private boolean isCrossDrill() {
    return practice.getSelection() == PRACTICE_CROSS;
  }

  private void refreshModeHint() {
    tvModeHint.setText(isRecording() ? R.string.drill_mode_hint_recording
        : R.string.drill_mode_hint_casual);
  }

  private boolean isRecording() {
    return mode.getSelection() == 1;
  }

  private void start() {
    int repCount = REP_COUNTS[reps.getSelection()];
    Intent intent;
    if (practice.getSelection() == PRACTICE_CROSS) {
      int seconds = typedPlanningSeconds();
      Options.INSTANCE.setDrillChoice(KEY_PLANNING_SECONDS, seconds);
      intent = new Intent(this, CrossDrillActivity.class);
      intent.putExtra(CrossDrillActivity.EXTRA_SPEC, DrillSpec
          .cross("local-cross-" + crossFace.name().toLowerCase(Locale.ROOT), crossFace.name(),
              repCount, cbPlanning.isChecked() ? seconds * 1000L : 0,
              getString(R.string.drill_cross_title))
          .toJson());
    } else {
      String family = practice.getSelection() == PRACTICE_OLL ? "oll_" : "pll_";
      intent = new Intent(this, DrillActivity.class);
      intent.putExtra(DrillActivity.EXTRA_LAYER_FACE, layerFace.name());
      intent.putExtra(DrillActivity.EXTRA_SPEC, new DrillSpec("local-" + family + "all",
          DrillSpec.Type.CASE_EXECUTION, DrillSpec.Delivery.VIRTUAL, casesOf(family),
          DrillSpec.Selection.ROUND_ROBIN, repCount, 0,
          getString(practice.getSelection() == PRACTICE_OLL ? R.string.drill_practice_oll
              : R.string.drill_practice_pll)).toJson());
    }
    intent.putExtra(DrillScreenActivity.EXTRA_RECORDING, isRecording());
    startActivity(intent);
  }

  /** Whatever is in the field, kept whether the limit is on or not so it survives being toggled. */
  private int typedPlanningSeconds() {
    try {
      return Math.max(1, Integer.parseInt(etPlanningSeconds.getText().toString().trim()));
    } catch (NumberFormatException e) {
      return DEFAULT_PLANNING_SECONDS;
    }
  }

  private static List<String> casesOf(String family) {
    List<String> cases = new ArrayList<String>();
    for (String code : LastLayerScrambles.cases()) {
      if (code.startsWith(family)) {
        cases.add(code);
      }
    }
    return cases;
  }
}
