package com.cube.nanotimer.gui;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.cube.SmartCubeChip;
import com.cube.nanotimer.cube.SmartCubeManager;
import com.cube.nanotimer.gui.widget.CrossFaceSwatches;
import com.cube.nanotimer.gui.widget.DrillHelpDialog;
import com.cube.nanotimer.gui.widget.LastLayerCaseView;
import com.cube.nanotimer.gui.widget.SegmentedControl;
import com.cube.nanotimer.gui.widget.SmartCubeConnectDialog;
import com.cube.nanotimer.gui.widget.dialog.DrillCasesDialog;
import com.cube.nanotimer.scrambler.cross.CrossFace;
import com.cube.nanotimer.smartcube.drill.DrillSpec;
import com.cube.nanotimer.smartcube.model.CubeConnection;
import com.cube.nanotimer.smartcube.model.CubeConnectionListener;
import com.cube.nanotimer.smartcube.step.LastLayerDiagram;
import com.cube.nanotimer.smartcube.step.LastLayerScrambles;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.helper.GUIUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
 *
 * <p><b>A case drill runs the cases the user picked, not the family.</b> Knowing eleven of the 57
 * OLLs is the normal state of learning them, and a drill that deals the other 46 spends the session
 * on cases there is nothing to practise yet.
 */
public class DrillSetupActivity extends NanoTimerActivity
    implements DrillCasesDialog.Listener, CubeConnectionListener {

  private static final int PRACTICE_PLL = 0;
  private static final int PRACTICE_OLL = 1;
  private static final int PRACTICE_CROSS = 2;

  private static final int[] REP_COUNTS = {10, 20, 30, 50};

  /** The last segment of the reps row: as many reps as there are cases, so each comes up once. */
  private static final int REPS_ALL = REP_COUNTS.length;
  /** Which segment a drill starts on, before the user has ever picked one. */
  private static final int DEFAULT_REP_CHOICE = 1;
  private static final int DEFAULT_PLANNING_SECONDS = 15;

  /** How many of the picked cases the row draws before it starts counting them instead. */
  private static final int PICKED_SHOWN = 6;
  private static final int PICKED_SIZE_DP = 32;

  private static final String KEY_PRACTICE = "practice";
  private static final String KEY_REPS = "reps";
  private static final String KEY_RECORDING = "recording";
  private static final String KEY_CROSS_FACE = "cross_face";
  private static final String KEY_LAYER_FACE = "layer_face";
  private static final String KEY_PLANNING_ON = "planning_on";
  private static final String KEY_PLANNING_SECONDS = "planning_seconds";

  private static final String FAMILY_PLL = "pll_";
  private static final String FAMILY_OLL = "oll_";

  private SegmentedControl practice;
  private SegmentedControl reps;
  private SegmentedControl mode;
  private CrossFaceSwatches crossFaces;
  private SmartCubeChip smartCubeChip;
  private Switch swPlanning;
  private EditText etPlanningSeconds;
  private View crossOptions;
  private View planningSeconds;
  private TextView tvPracticeHint;
  private TextView tvCasesCount;
  private TextView tvPickedLabel;
  private LinearLayout llPicked;
  private View casesRow;
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

    // Before the bar asks for it: onCreateOptionsMenu binds the chip this builds.
    smartCubeChip = new SmartCubeChip(this, this::openSmartCubeConnect);

    crossOptions = findViewById(R.id.llDrillCrossOptions);
    casesRow = findViewById(R.id.llDrillCases);
    tvCasesCount = findViewById(R.id.tvDrillCasesCount);
    tvPickedLabel = findViewById(R.id.tvDrillCasesDrilling);
    llPicked = findViewById(R.id.llDrillCasesPicked);
    planningSeconds = findViewById(R.id.llDrillPlanningSeconds);
    tvPracticeHint = findViewById(R.id.tvDrillPracticeHint);
    tvFaceLabel = findViewById(R.id.tvDrillFaceLabel);
    tvModeHint = findViewById(R.id.tvDrillModeHint);
    swPlanning = findViewById(R.id.swDrillPlanning);
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

    String[] repLabels = new String[REP_COUNTS.length + 1];
    for (int i = 0; i < REP_COUNTS.length; i++) {
      repLabels[i] = String.valueOf(REP_COUNTS[i]);
    }
    repLabels[REPS_ALL] = getString(R.string.drill_reps_all);
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

    swPlanning.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton button, boolean checked) {
        Options.INSTANCE.setDrillChoice(KEY_PLANNING_ON, checked ? 1 : 0);
        planningSeconds.setVisibility(checked ? View.VISIBLE : View.GONE);
      }
    });

    findViewById(R.id.llDrillCasesPick).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        DrillCasesDialog.newInstance(family()).show(getSupportFragmentManager(), "drillCases");
      }
    });

    findViewById(R.id.btDrillStart).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        start();
      }
    });

    practice.setSelection(Options.INSTANCE.getDrillChoice(KEY_PRACTICE, PRACTICE_PLL));
    mode.setSelection(Options.INSTANCE.getDrillChoice(KEY_RECORDING, 1));
    swPlanning.setChecked(Options.INSTANCE.getDrillChoice(KEY_PLANNING_ON, 0) == 1);
    etPlanningSeconds.setText(String.valueOf(
        Options.INSTANCE.getDrillChoice(KEY_PLANNING_SECONDS, DEFAULT_PLANNING_SECONDS)));
    refreshPractice();
    refreshModeHint();
  }

  /**
   * The cube chip and the two explanations that used to stand as grey paragraphs between the last
   * control and Start. The chip stays whether or not a cube is connected: this screen is only
   * reached to set up a drill, which needs one, and it is where the asking happens.
   */
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.drill_setup_menu, menu);
    MenuItem item = menu.findItem(R.id.itSmartCube);
    smartCubeChip.bind(item != null ? item.getActionView() : null);
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == R.id.itDrillStats) {
      startActivity(new Intent(this, DrillStatsActivity.class));
      return true;
    }
    if (item.getItemId() == R.id.itDrillHelp) {
      DialogUtils.showFragment(this, new DrillHelpDialog());
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  private void openSmartCubeConnect() {
    DialogUtils.showFragment(this, new SmartCubeConnectDialog());
  }

  @Override
  protected void onResume() {
    super.onResume();
    smartCubeChip.start();
    SmartCubeManager.INSTANCE.addConnectionListener(this); // replays the connection at once
  }

  @Override
  protected void onPause() {
    super.onPause();
    smartCubeChip.stop();
    SmartCubeManager.INSTANCE.removeConnectionListener(this);
  }

  /** A cube connected from the chip here, so the line asking for one goes without leaving. */
  @Override
  public void onConnection(CubeConnection connection) {
    // Said here rather than only on the drill screen: being turned back at the door having picked
    // everything is worse than being told at the door.
    findViewById(R.id.tvDrillNoCube).setVisibility(
        SmartCubeManager.INSTANCE.isConnected() ? View.GONE : View.VISIBLE);
  }

  private void refreshPractice() {
    int picked = practice.getSelection();
    boolean cross = picked == PRACTICE_CROSS;
    crossOptions.setVisibility(cross ? View.VISIBLE : View.GONE);
    planningSeconds.setVisibility(cross && swPlanning.isChecked() ? View.VISIBLE : View.GONE);
    int hint;
    if (cross) {
      hint = R.string.drill_practice_hint_cross;
    } else if (picked == PRACTICE_OLL) {
      hint = R.string.drill_practice_hint_oll;
    } else {
      hint = R.string.drill_practice_hint_pll;
    }
    tvPracticeHint.setText(hint);
    // Getting through the set is an answer for a case drill; a cross rep is a fresh scramble, so
    // there is no set to get through and the choice is taken away rather than left to mean nothing.
    reps.setSegmentVisible(REPS_ALL, !cross);
    int chosenReps = Options.INSTANCE.getDrillChoice(KEY_REPS, DEFAULT_REP_CHOICE);
    reps.setSelection(cross && chosenReps == REPS_ALL ? DEFAULT_REP_CHOICE : chosenReps);
    casesRow.setVisibility(cross ? View.GONE : View.VISIBLE);
    if (!cross) {
      refreshCasesCount();
    }
    tvFaceLabel.setText(cross ? R.string.drill_cross_colour : R.string.drill_layer_colour);
    crossFaces.setSelection(cross ? crossFace : layerFace, null);
  }

  @Override
  public void onDrillCasesPicked(String family) {
    refreshCasesCount();
  }

  /**
   * What the drill will deal, which is what the user is about to spend the session on. Everything
   * picked reads as one line: "57 of 57 picked" is a sum where "All 57 cases" is an answer, and the
   * strip under it would then be the whole family drawn out for nothing.
   */
  private void refreshCasesCount() {
    List<String> all = casesOf(family());
    List<String> picked = pickedCases();
    boolean allPicked = picked.size() == all.size();

    if (allPicked) {
      tvCasesCount.setText(getString(R.string.drill_cases_all_count, all.size()));
    } else {
      tvCasesCount.setText(countWithFamilyColour(picked.size(), all.size()));
    }
    // Nothing to draw for the whole family, which the line above already answers, nor for none.
    boolean strip = !allPicked && !picked.isEmpty();
    tvPickedLabel.setVisibility(strip ? View.VISIBLE : View.GONE);
    llPicked.setVisibility(strip ? View.VISIBLE : View.GONE);
    llPicked.removeAllViews();
    if (!strip) {
      return;
    }
    for (int i = 0; i < Math.min(PICKED_SHOWN, picked.size()); i++) {
      LastLayerCaseView chart = new LastLayerCaseView(this);
      chart.setDiagram(LastLayerDiagram.forCase(picked.get(i)));
      chart.setLayoutParams(stripCell());
      llPicked.addView(chart);
    }
    if (picked.size() > PICKED_SHOWN) {
      TextView more = GUIUtils.newTextView(this);
      more.setText(getString(R.string.drill_cases_more, picked.size() - PICKED_SHOWN));
      more.setTextSize(11);
      more.setTextColor(ContextCompat.getColor(this, R.color.secondary_text));
      more.setGravity(Gravity.CENTER);
      more.setBackgroundResource(R.drawable.case_more);
      more.setLayoutParams(stripCell());
      llPicked.addView(more);
    }
  }

  private LinearLayout.LayoutParams stripCell() {
    int size = (int) (PICKED_SIZE_DP * getResources().getDisplayMetrics().density);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
    params.rightMargin = getResources().getDimensionPixelSize(R.dimen.space_xs);
    return params;
  }

  private CharSequence countWithFamilyColour(int picked, int total) {
    String count = String.valueOf(picked);
    String text = getString(R.string.drill_cases_count, picked, total);
    SpannableString spanned = new SpannableString(text);
    int at = text.indexOf(count);
    if (at >= 0) {
      spanned.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this,
              FAMILY_OLL.equals(family()) ? R.color.step_oll : R.color.step_pll)),
          at, at + count.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    return spanned;
  }

  private String family() {
    return practice.getSelection() == PRACTICE_OLL ? FAMILY_OLL : FAMILY_PLL;
  }

  /** The cases the drill will deal: what was picked, or the whole family if nothing ever was. */
  private List<String> pickedCases() {
    List<String> all = casesOf(family());
    Set<String> chosen = Options.INSTANCE.getDrillCases(family());
    if (chosen == null) {
      return all;
    }
    List<String> picked = new ArrayList<String>();
    for (String code : all) {
      if (chosen.contains(code)) {
        picked.add(code);
      }
    }
    return picked;
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
    Intent intent;
    if (practice.getSelection() == PRACTICE_CROSS) {
      int repCount = REP_COUNTS[reps.getSelection()];
      int seconds = typedPlanningSeconds();
      Options.INSTANCE.setDrillChoice(KEY_PLANNING_SECONDS, seconds);
      intent = new Intent(this, CrossDrillActivity.class);
      intent.putExtra(CrossDrillActivity.EXTRA_SPEC, DrillSpec
          .cross("local-cross-" + crossFace.name().toLowerCase(Locale.ROOT), crossFace.name(),
              repCount, swPlanning.isChecked() ? seconds * 1000L : 0,
              getString(R.string.drill_cross_title))
          .toJson());
    } else {
      List<String> cases = pickedCases();
      if (cases.isEmpty()) {
        DialogUtils.showInfoMessage(this, R.string.drill_cases_empty);
        return;
      }
      int repCount = reps.getSelection() == REPS_ALL ? cases.size()
          : REP_COUNTS[reps.getSelection()];
      intent = new Intent(this, DrillActivity.class);
      intent.putExtra(DrillActivity.EXTRA_LAYER_FACE, layerFace.name());
      intent.putExtra(DrillActivity.EXTRA_SPEC, new DrillSpec("local-" + family() + "picked",
          DrillSpec.Type.CASE_EXECUTION, DrillSpec.Delivery.VIRTUAL, cases,
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
