package com.cube.nanotimer.gui;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.cube.SmartCubeChip;
import com.cube.nanotimer.cube.SmartCubeManager;
import com.cube.nanotimer.gui.widget.DrillFacePicture;
import com.cube.nanotimer.gui.widget.DrillHelpDialog;
import com.cube.nanotimer.gui.widget.LastLayerCaseView;
import com.cube.nanotimer.gui.widget.SegmentedControl;
import com.cube.nanotimer.gui.widget.SmartCubeConnectDialog;
import com.cube.nanotimer.gui.widget.StepCardBackground;
import com.cube.nanotimer.gui.widget.dialog.DrillCasesDialog;
import com.cube.nanotimer.gui.widget.preferences.LastLayerColourDialog;
import com.cube.nanotimer.gui.widget.preferences.NumberEntryDialog;
import com.cube.nanotimer.scrambler.cross.CrossFace;
import com.cube.nanotimer.drill.DrillSpec;
import com.cube.nanotimer.smartcube.model.CubeConnection;
import com.cube.nanotimer.smartcube.model.CubeConnectionListener;
import com.cube.nanotimer.smartcube.step.LastLayerDiagram;
import com.cube.nanotimer.smartcube.step.LastLayerScrambles;
import com.cube.nanotimer.step.F2lPairCases;
import com.cube.nanotimer.util.DrillCasePreset;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.helper.GUIUtils;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.util.view.SmartCubeMarkLabel;
import com.cube.nanotimer.util.view.StepPalette;

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
 * <p><b>Recording is decided here because it cannot be decided later.</b> A set drilled loosely and
 * then claimed as a result at the end would make the drill history worth less than no history, so
 * the choice is made before the first case and is fixed for the whole drill. Stopping and starting
 * another is how it changes.
 *
 * <p>What hangs below the three shared controls depends on the practice. The colour every drill
 * deals onto is the last layer colour setting, offered here through that setting's own picker.
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
  private static final int PRACTICE_F2L = 3;

  /** The tabs in the order drawn. The value is what is stored, so a tab can move. */
  private static final int[] PRACTICE_ORDER =
      {PRACTICE_PLL, PRACTICE_OLL, PRACTICE_F2L, PRACTICE_CROSS};

  /** Which F2L pairs a drill deals, in the order the segments are drawn. */
  private static final int F2L_BASIC = 0;
  private static final int F2L_ADVANCED = 1;
  private static final int F2L_BOTH = 2;

  private static final int[] REP_COUNTS = {10, 20, 30, 50};

  /** The last segment of the reps row: as many reps as there are cases, so each comes up once. */
  private static final int REPS_ALL = REP_COUNTS.length;
  /** Which segment a drill starts on, before the user has ever picked one. */
  private static final int DEFAULT_REP_CHOICE = 1;
  private static final int DEFAULT_PLANNING_SECONDS = 15;
  private static final int MIN_PLANNING_SECONDS = 1;
  private static final int MAX_PLANNING_SECONDS = 600;

  /** The case strip's slots; past this many cases the last slot counts the rest instead. */
  private static final int STRIP_SLOTS = 6;
  private static final int REPS_CELL_DP = 34;
  /** The step colour's strength in a selected tile and the cases chip. */
  private static final int TILE_ALPHA = 0x2E;
  private static final int CHIP_ALPHA = 0x29;
  /** The step colour of each practice, in StepPalette's codes. */
  private static final String[] PRACTICE_CODES = {"pll", "oll", "cross", "f2l"};

  private static final String KEY_PRACTICE = "practice";
  private static final String KEY_REPS = "reps";
  private static final String KEY_RECORDING = "recording";
  private static final String KEY_PLANNING_ON = "planning_on";
  private static final String KEY_PLANNING_SECONDS = "planning_seconds";
  private static final String KEY_F2L_CASES = "f2l_cases";

  private static final String FAMILY_PLL = "pll_";
  private static final String FAMILY_OLL = "oll_";

  private final List<View> practiceTiles = new ArrayList<View>();
  private int practiceIndex;
  private StepPalette palette;
  private SegmentedControl reps;
  private SegmentedControl f2lCases;
  private Switch swRecord;
  private SmartCubeChip smartCubeChip;
  private Switch swPlanning;
  private TextView tvPlanningSeconds;
  private int planningSecondsValue;
  private View crossOptions;
  private View planningSeconds;
  private TextView tvPracticeHint;
  private TextView tvCasesCount;
  private LinearLayout llPicked;
  private View casesRow;
  private Button btStart;
  private int[] startPadding;

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
    llPicked = findViewById(R.id.llDrillCasesPicked);
    btStart = findViewById(R.id.btDrillStart);
    startPadding = new int[] {btStart.getPaddingLeft(), btStart.getPaddingTop(),
        btStart.getPaddingRight(), btStart.getPaddingBottom()};
    palette = StepPalette.cfop(this);
    planningSeconds = findViewById(R.id.llDrillPlanningSeconds);
    tvPracticeHint = findViewById(R.id.tvDrillPracticeHint);
    final View layerChip = findViewById(R.id.vColourChip);
    LastLayerColourDialog.paintChip(layerChip);
    findViewById(R.id.llDrillLayerColour).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        LastLayerColourDialog.pick(DrillSetupActivity.this, new Runnable() {
          @Override
          public void run() {
            LastLayerColourDialog.paintChip(layerChip);
            refreshPractice(); // the cross picture is drawn in the face opposite this one
          }
        });
      }
    });
    swPlanning = findViewById(R.id.swDrillPlanning);
    swRecord = findViewById(R.id.swDrillRecord);
    tvPlanningSeconds = findViewById(R.id.tvDrillPlanningSeconds);
    tvPlanningSeconds.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        pickPlanningSeconds();
      }
    });

    buildPracticeTiles();

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
    reps.setCompact(REPS_CELL_DP);

    f2lCases = new SegmentedControl(this, (LinearLayout) findViewById(R.id.llDrillF2lCases),
        new String[] {getString(R.string.drill_f2l_basic), getString(R.string.drill_f2l_advanced),
            getString(R.string.drill_f2l_both)},
        new SegmentedControl.Listener() {
          @Override
          public void onSegmentPicked(int index) {
            Options.INSTANCE.setDrillChoice(KEY_F2L_CASES, index);
          }
        });
    f2lCases.setSelection(Options.INSTANCE.getDrillChoice(KEY_F2L_CASES, F2L_BOTH));

    swRecord.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton button, boolean checked) {
        Options.INSTANCE.setDrillChoice(KEY_RECORDING, checked ? 1 : 0);
      }
    });
    findViewById(R.id.llDrillRecordRow).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        swRecord.toggle();
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

    btStart.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (SmartCubeManager.INSTANCE.isConnected()) {
          start();
        } else {
          openSmartCubeConnect();
        }
      }
    });

    practiceIndex = positionOf(Options.INSTANCE.getDrillChoice(KEY_PRACTICE, PRACTICE_PLL));
    swRecord.setChecked(Options.INSTANCE.getDrillChoice(KEY_RECORDING, 1) == 1);
    swPlanning.setChecked(Options.INSTANCE.getDrillChoice(KEY_PLANNING_ON, 0) == 1);
    setPlanningSeconds(
        Options.INSTANCE.getDrillChoice(KEY_PLANNING_SECONDS, DEFAULT_PLANNING_SECONDS));
    refreshPractice();
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
    if (item.getItemId() == R.id.itKnownAlgorithms) {
      startActivity(new Intent(this, KnownAlgorithmsActivity.class));
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
    // everything is worse than being told at the door. Start becomes the way to connect one.
    boolean connected = SmartCubeManager.INSTANCE.isConnected();
    if (connected) {
      btStart.setText(R.string.drill_start);
      btStart.setBackgroundResource(R.drawable.hero_start_button);
      btStart.setTextColor(ContextCompat.getColorStateList(this, R.color.on_accent_ink));
    } else {
      btStart.setText(SmartCubeMarkLabel.of(this, getString(R.string.drill_connect_cube), 22));
      btStart.setBackgroundResource(R.drawable.connect_cube_button);
      btStart.setTextColor(ContextCompat.getColor(this, R.color.white));
    }
    // The outline stops above the 3dp kept for Start's shadow, so the label centres on the outline.
    btStart.setPadding(startPadding[0], startPadding[1], startPadding[2],
        startPadding[3] + (connected ? 0 : dp(3)));
  }

  private void buildPracticeTiles() {
    LinearLayout row = findViewById(R.id.llDrillPractice);
    String[] names = {getString(R.string.drill_practice_pll), getString(R.string.drill_practice_oll),
        getString(R.string.drill_practice_f2l), getString(R.string.drill_practice_cross)};
    int[] icons = {R.drawable.ic_practice_pll, R.drawable.ic_practice_oll,
        R.drawable.ic_practice_f2l, R.drawable.ic_practice_cross};
    for (int i = 0; i < PRACTICE_ORDER.length; i++) {
      final int index = i;
      View tile = getLayoutInflater().inflate(R.layout.drill_practice_tile, row, false);
      ((ImageView) tile.findViewById(R.id.ivPracticeIcon)).setImageResource(icons[i]);
      ((TextView) tile.findViewById(R.id.tvPracticeName)).setText(names[i]);
      if (i > 0) {
        ((LinearLayout.LayoutParams) tile.getLayoutParams()).leftMargin = dp(8);
      }
      tile.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          if (practiceIndex != index) {
            practiceIndex = index;
            Options.INSTANCE.setDrillChoice(KEY_PRACTICE, PRACTICE_ORDER[index]);
            refreshPractice();
          }
        }
      });
      practiceTiles.add(tile);
      row.addView(tile);
    }
  }

  /** The picked tile is lit in its own colour; the others keep theirs, receded, as a preview. */
  private void paintPracticeTiles() {
    for (int i = 0; i < practiceTiles.size(); i++) {
      View tile = practiceTiles.get(i);
      int colour = practiceColour(PRACTICE_ORDER[i]);
      boolean on = i == practiceIndex;
      GradientDrawable ground = new GradientDrawable();
      ground.setCornerRadius(dp(14));
      if (on) {
        ground.setColor(withAlpha(colour, TILE_ALPHA));
        ground.setStroke(Math.round(1.5f * getResources().getDisplayMetrics().density), colour);
      } else {
        ground.setColor(ContextCompat.getColor(this, R.color.hero_inset));
      }
      tile.setBackground(new RippleDrawable(
          ColorStateList.valueOf(withAlpha(colour, 0x55)), ground, null));
      tile.setSelected(on);
      ImageView icon = tile.findViewById(R.id.ivPracticeIcon);
      icon.setColorFilter(colour, PorterDuff.Mode.SRC_IN);
      icon.setAlpha(on ? 1f : 0.7f);
      TextView name = tile.findViewById(R.id.tvPracticeName);
      name.setTextColor(ContextCompat.getColor(this, on ? R.color.white : R.color.secondary_text));
      GUIUtils.setWeight(name, on ? Typeface.BOLD : Typeface.NORMAL);
    }
  }

  /** The card, lit from the top in the practice's colour, and every coloured mark under it. */
  private void paintPracticeColour(int colour) {
    StepCardBackground.paint(findViewById(R.id.llDrillHero), colour);

    int[] icons = {R.id.ivDrillRepsIcon, R.id.ivDrillRecordIcon, R.id.ivDrillPlanningIcon,
        R.id.ivDrillLayerIcon};
    for (int id : icons) {
      ((ImageView) findViewById(id)).setColorFilter(colour, PorterDuff.Mode.SRC_IN);
    }
    reps.setAccent(colour);
    f2lCases.setAccent(colour);
    ((TextView) findViewById(R.id.tvDrillCasesFamily)).setTextColor(colour);
    ((TextView) findViewById(R.id.tvDrillPictureLabel)).setTextColor(colour);
    TextView choose = findViewById(R.id.tvDrillCasesChoose);
    choose.setTextColor(colour);
    GradientDrawable chip = new GradientDrawable();
    chip.setCornerRadius(dp(12));
    chip.setColor(withAlpha(colour, CHIP_ALPHA));
    choose.setBackground(chip);
  }

  /** A drill with no case set shows its practice as a face: the cross, or an open F2L slot. */
  private void showPicture(int practiceValue) {
    boolean cross = practiceValue == PRACTICE_CROSS;
    int blank = ContextCompat.getColor(this, R.color.case_blank);
    int ink;
    int[] stickers;
    if (cross) {
      ink = ContextCompat.getColor(this,
          Utils.getFaceColorRes(layerFace().opposite().name().charAt(0)));
      stickers = new int[] {blank, ink, blank, ink, ink, ink, blank, ink, blank};
    } else {
      ink = ContextCompat.getColor(this, Utils.getFaceColorRes('F'));
      stickers = new int[] {blank, blank, blank, ink, ink, blank, ink, ink, blank};
    }
    ((DrillFacePicture) findViewById(R.id.vDrillPicture)).setStickers(stickers);
    ((TextView) findViewById(R.id.tvDrillPictureLabel)).setText(cross
        ? R.string.drill_practice_cross : R.string.drill_practice_f2l);
    ((TextView) findViewById(R.id.tvDrillPictureTitle)).setText(cross
        ? R.string.drill_title_cross : R.string.drill_title_f2l);
  }

  private int practiceColour(int practiceValue) {
    return palette.colorFor(PRACTICE_CODES[practiceValue]);
  }

  private static int withAlpha(int colour, int alpha) {
    return (colour & 0x00FFFFFF) | (alpha << 24);
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private void refreshPractice() {
    int picked = chosenPractice();
    boolean cross = picked == PRACTICE_CROSS;
    boolean f2l = picked == PRACTICE_F2L;
    paintPracticeTiles();
    paintPracticeColour(practiceColour(picked));
    crossOptions.setVisibility(cross ? View.VISIBLE : View.GONE);
    findViewById(R.id.llDrillF2lCases).setVisibility(f2l ? View.VISIBLE : View.GONE);
    findViewById(R.id.llDrillPicture).setVisibility(cross || f2l ? View.VISIBLE : View.GONE);
    if (cross || f2l) {
      showPicture(picked);
    }
    planningSeconds.setVisibility(cross && swPlanning.isChecked() ? View.VISIBLE : View.GONE);
    int hint;
    if (cross) {
      hint = R.string.drill_practice_hint_cross;
    } else if (f2l) {
      hint = R.string.drill_practice_hint_f2l;
    } else if (picked == PRACTICE_OLL) {
      hint = R.string.drill_practice_hint_oll;
    } else {
      hint = R.string.drill_practice_hint_pll;
    }
    tvPracticeHint.setText(hint);
    // Getting through the set is an answer for a case drill; a cross rep is a fresh scramble, so
    // there is no set to get through and the choice is taken away rather than left to mean nothing.
    // F2L pairs are dealt by how often they come up, so neither is there a set to get through.
    boolean noSet = cross || f2l;
    reps.setSegmentVisible(REPS_ALL, !noSet);
    int chosenReps = Options.INSTANCE.getDrillChoice(KEY_REPS, DEFAULT_REP_CHOICE);
    reps.setSelection(noSet && chosenReps == REPS_ALL ? DEFAULT_REP_CHOICE : chosenReps);
    casesRow.setVisibility(noSet ? View.GONE : View.VISIBLE);
    // A pair's time depends on the slot and grip it was dealt in, and no screen reads one back.
    findViewById(R.id.llDrillRecord).setVisibility(f2l ? View.GONE : View.VISIBLE);
    if (!noSet) {
      ((TextView) findViewById(R.id.tvDrillCasesFamily)).setText(getString(
          R.string.drill_cases_family, getString(picked == PRACTICE_OLL
              ? R.string.drill_practice_oll : R.string.drill_practice_pll)));
      refreshCasesCount();
    }
  }

  @Override
  public void onDrillCasesPicked(String family) {
    refreshCasesCount();
  }

  /**
   * What the drill will deal, which is what the user is about to spend the session on. Everything
   * picked reads as one line: "57 of 57 picked" is a sum where "All 57 cases" is an answer. The
   * strip under it draws the first few cases either way, as the card's picture of the drill.
   */
  private void refreshCasesCount() {
    List<String> all = casesOf(family());
    List<String> picked = pickedCases();
    boolean allPicked = picked.size() == all.size();

    // A preset the pick stands at names it better than any count of it can, the whole family
    // included: someone who has named all 57 meant that name.
    DrillCasePreset preset = DrillCasePreset.matching(
        Options.INSTANCE.getDrillCasePresets(family()), picked);
    if (preset != null) {
      tvCasesCount.setText(presetWithFamilyColour(preset.getName(), picked.size()));
    } else if (allPicked) {
      tvCasesCount.setText(getString(R.string.drill_cases_all_count, all.size()));
    } else {
      tvCasesCount.setText(countWithFamilyColour(picked.size(), all.size()));
    }
    llPicked.setVisibility(picked.isEmpty() ? View.GONE : View.VISIBLE);
    llPicked.removeAllViews();
    boolean overflow = picked.size() > STRIP_SLOTS;
    int charts = overflow ? STRIP_SLOTS - 1 : picked.size();
    // Every slot is laid out, empty ones invisible, so a short pick keeps the same cell width.
    for (int i = 0; i < STRIP_SLOTS; i++) {
      View cell = getLayoutInflater().inflate(R.layout.drill_setup_case, llPicked, false);
      LastLayerCaseView chart = cell.findViewById(R.id.vDrillCaseChart);
      if (i < charts) {
        chart.setDiagram(LastLayerDiagram.forCase(picked.get(i)));
        ((TextView) cell.findViewById(R.id.tvDrillCaseName)).setText(shortName(picked.get(i)));
      } else if (overflow) {
        chart.setVisibility(View.INVISIBLE);
        TextView more = cell.findViewById(R.id.tvDrillCaseMore);
        more.setVisibility(View.VISIBLE);
        more.setText(getString(R.string.drill_cases_more, picked.size() - charts));
      } else {
        cell.setVisibility(View.INVISIBLE);
      }
      llPicked.addView(cell);
    }
  }

  /** A case as a cuber writes it under its chart: Rb, 21. */
  private static String shortName(String code) {
    String name = code.substring(code.indexOf('_') + 1);
    return name.isEmpty() ? name : name.substring(0, 1).toUpperCase(Locale.US) + name.substring(1);
  }

  private CharSequence countWithFamilyColour(int picked, int total) {
    String count = String.valueOf(picked);
    String text = getString(R.string.drill_cases_count, picked, total);
    SpannableString spanned = new SpannableString(text);
    int at = text.indexOf(count);
    if (at >= 0) {
      spanned.setSpan(new ForegroundColorSpan(familyColour()), at, at + count.length(),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    return spanned;
  }

  private CharSequence presetWithFamilyColour(String name, int picked) {
    SpannableString spanned = new SpannableString(getResources()
        .getQuantityString(R.plurals.drill_preset_and_count, picked, name, picked));
    spanned.setSpan(new ForegroundColorSpan(familyColour()), 0, name.length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    return spanned;
  }

  private int familyColour() {
    return StepPalette.cfop(this).colorFor(family());
  }

  private String family() {
    return chosenPractice() == PRACTICE_OLL ? FAMILY_OLL : FAMILY_PLL;
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

  /** The face a case drill deals its last layer onto, which is the last layer colour setting's. */
  public static CrossFace layerFace() {
    return CrossFace.valueOf(Options.INSTANCE.getLastLayerFace());
  }

  /**
   * A drill of named cases, for a screen that has already worked out which ones are worth drilling
   * and should not make the reader pick them again.
   *
   * <p><b>No caller yet.</b> The Analysis hub's Cases tab called this and no longer does: composing
   * a practice set out of a finding is the plan saying what to do next, so it belongs behind the
   * subscription rather than on a free tab. This is kept as the way such a drill is launched once
   * there is a plan to launch it from, since how it is launched was itself a ruling: the cases ride
   * in the spec.
   *
   * <p>The cases ride in the spec rather than through the saved picks: what someone chose to drill
   * is theirs, and a screen elsewhere in the app must not quietly rewrite it on its way past.
   *
   * <p><b>It runs casual, whatever the setup screen was last left on.</b> The rep count and the
   * layer face are that screen's standing answers and are inherited; recording is not, because it
   * is the one of the three that writes something down. A drill offered off a finding is a thing to
   * try, not a set of reps the reader sat down to count.
   */
  public static Intent drillOf(Context context, List<String> cases, String title) {
    Intent intent = new Intent(context, DrillActivity.class);
    intent.putExtra(DrillActivity.EXTRA_LAYER_FACE, layerFace().name());
    // Never recorded: a drill somebody was sent to is not a set of reps they sat down to count.
    intent.putExtra(DrillScreenActivity.EXTRA_RECORDING, false);
    intent.putExtra(DrillActivity.EXTRA_SPEC, new DrillSpec("local-picked",
        DrillSpec.Type.CASE_EXECUTION, DrillSpec.Delivery.VIRTUAL, cases,
        DrillSpec.Selection.ROUND_ROBIN, repCount(cases.size()), 0, title).toJson());
    return intent;
  }

  /**
   * How many reps a drill started from elsewhere runs: the reader's own standing choice, the same
   * one this screen opens on. Dealing each case once would be a four-rep session, which is not a
   * practice of anything; how long they like to drill for is theirs, as the face is.
   */
  private static int repCount(int cases) {
    int chosen = Options.INSTANCE.getDrillChoice(KEY_REPS, DEFAULT_REP_CHOICE);
    return chosen == REPS_ALL ? cases : REP_COUNTS[chosen];
  }

  /** The practice the selected tab stands for. */
  private int chosenPractice() {
    return PRACTICE_ORDER[practiceIndex];
  }

  private static int positionOf(int practiceValue) {
    for (int i = 0; i < PRACTICE_ORDER.length; i++) {
      if (PRACTICE_ORDER[i] == practiceValue) {
        return i;
      }
    }
    return 0;
  }

  private boolean isRecording() {
    return chosenPractice() != PRACTICE_F2L && swRecord.isChecked();
  }

  private void start() {
    Intent intent;
    if (chosenPractice() == PRACTICE_CROSS) {
      CrossFace crossFace = layerFace().opposite(); // the cross is built under the last layer
      int repCount = REP_COUNTS[reps.getSelection()];
      intent = new Intent(this, CrossDrillActivity.class);
      intent.putExtra(CrossDrillActivity.EXTRA_SPEC, DrillSpec
          .cross("local-cross-" + crossFace.name().toLowerCase(Locale.ROOT), crossFace.name(),
              repCount, swPlanning.isChecked() ? planningSecondsValue * 1000L : 0,
              getString(R.string.drill_cross_title))
          .toJson());
    } else if (chosenPractice() == PRACTICE_F2L) {
      intent = new Intent(this, DrillActivity.class);
      intent.putExtra(DrillActivity.EXTRA_LAYER_FACE, layerFace().name());
      intent.putExtra(DrillActivity.EXTRA_SPEC, new DrillSpec("local-f2l",
          DrillSpec.Type.F2L_PAIR, DrillSpec.Delivery.VIRTUAL, pickedPairs(),
          DrillSpec.Selection.WEIGHTED, REP_COUNTS[reps.getSelection()], 0,
          getString(R.string.drill_practice_f2l)).toJson());
    } else {
      List<String> cases = pickedCases();
      if (cases.isEmpty()) {
        DialogUtils.showInfoMessage(this, R.string.drill_cases_empty);
        return;
      }
      int repCount = reps.getSelection() == REPS_ALL ? cases.size()
          : REP_COUNTS[reps.getSelection()];
      intent = new Intent(this, DrillActivity.class);
      intent.putExtra(DrillActivity.EXTRA_LAYER_FACE, layerFace().name());
      intent.putExtra(DrillActivity.EXTRA_SPEC, new DrillSpec("local-" + family() + "picked",
          DrillSpec.Type.CASE_EXECUTION, DrillSpec.Delivery.VIRTUAL, cases,
          DrillSpec.Selection.ROUND_ROBIN, repCount, 0,
          getString(chosenPractice() == PRACTICE_OLL ? R.string.drill_practice_oll
              : R.string.drill_practice_pll)).toJson());
    }
    intent.putExtra(DrillScreenActivity.EXTRA_RECORDING, isRecording());
    startActivity(intent);
  }

  private List<String> pickedPairs() {
    switch (f2lCases.getSelection()) {
      case F2L_BASIC:
        return F2lPairCases.basicCodes();
      case F2L_ADVANCED:
        return F2lPairCases.stuckCodes();
      default:
        return F2lPairCases.codes();
    }
  }

  /** The settings screen's number dialog, so a limit is picked here the way any other one is. */
  private void pickPlanningSeconds() {
    NumberEntryDialog.pick(this, R.string.drill_planning_limit, MIN_PLANNING_SECONDS,
        MAX_PLANNING_SECONDS, 1, R.array.presets_planning_seconds, R.string.seconds,
        planningSecondsValue, new NumberEntryDialog.Listener() {
          @Override
          public void onNumberPicked(int value) {
            setPlanningSeconds(value);
          }
        });
  }

  /** The limit is kept whether it is switched on or not, so it survives being toggled. */
  private void setPlanningSeconds(int seconds) {
    planningSecondsValue = seconds;
    Options.INSTANCE.setDrillChoice(KEY_PLANNING_SECONDS, seconds);
    tvPlanningSeconds.setText(String.valueOf(seconds));
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
