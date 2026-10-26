package com.cube.nanotimer.gui.widget;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.text.BreakIterator;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.FragmentActivity;

import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.coach.CoachPayload;
import com.cube.nanotimer.coach.Evidence;
import com.cube.nanotimer.coach.FocusArea;
import com.cube.nanotimer.coach.StepShares;
import com.cube.nanotimer.coach.StoredCoachPlan;
import com.cube.nanotimer.drill.DrillSpec;
import com.cube.nanotimer.vo.SolveType;
import com.cube.nanotimer.session.MethodStatistics;
import com.cube.nanotimer.gui.CrossDrillActivity;
import com.cube.nanotimer.gui.DrillActivity;
import com.cube.nanotimer.gui.DrillScreenActivity;
import com.cube.nanotimer.gui.DrillSetupActivity;
import com.cube.nanotimer.gui.widget.dialog.CaseAlgorithmsDialog;
import com.cube.nanotimer.gui.widget.dialog.DrillCasesDialog;
import com.cube.nanotimer.smartcube.step.CaseAlgorithms;
import com.cube.nanotimer.smartcube.step.LastLayerDiagram;
import com.cube.nanotimer.util.FormatterService;
import com.cube.nanotimer.util.coach.CoachPractice;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.util.view.CenteredImageSpan;
import com.cube.nanotimer.util.view.StepPalette;

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

  /** What the teaser draws: the families the service sends sets for, at a full set's size. */
  private static final String[] TEASER_FAMILIES = {"oll", "pll"};
  private static final int TEASER_CASES = 5;
  /** The kinds of focus the preview draws blank, with the step each is coloured by. */
  private static final FocusArea.Reason[] PREVIEW_REASONS = {FocusArea.Reason.SLOW_CASE,
      FocusArea.Reason.RECOGNITION_HEAVY};
  private static final String[] PREVIEW_FAMILIES = {"pll", "oll"};
  private static final int SCALE = 1000;
  private static final int LEARNING_ROW = 5;
  /** The Choose chip's fill, as the drill setup screen's. */
  private static final int CHOOSE_CHIP_ALPHA = 0x29;

  private final Activity activity;
  private final LinearLayout container;
  private final LayoutInflater inflater;
  /** Whose plan this is, for a drill that reads the solver's own recent solves. */
  private final SolveType solveType;

  public CoachPlanView(Activity activity, LinearLayout container, SolveType solveType) {
    this.activity = activity;
    this.solveType = solveType;
    this.container = container;
    this.inflater = LayoutInflater.from(activity);
  }

  public void setPlan(StoredCoachPlan stored) {
    container.removeAllViews();
    if (stored == null) {
      return;
    }
    List<String> undealable = stored.getPlan().undealableDrills(stored.getPayload());
    List<FocusArea> focus = stored.getPlan().getFocus();
    for (int i = 0; i < focus.size(); i++) {
      container.addView(areaCard(i + 1, focus.get(i), stored.getPayload(), undealable));
    }
  }

  /**
   * The plan's own card: when it was read, what went well, the headline, and a chip per focus that
   * scrolls to its card. Call after {@link #setPlan}, which draws the cards the chips scroll to.
   */
  public void setHero(View hero, StoredCoachPlan stored) {
    ((TextView) hero.findViewById(R.id.tvAnalysisPlanDate)).setText(activity.getString(
        R.string.coach_written,
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(stored.getWrittenAt()))));
    String[] said = splitWin(stored.getPlan().getHeadline());
    fill(hero, R.id.tvAnalysisPlanWin, said[0]);
    fill(hero, R.id.tvAnalysisPlanHeadline, said[1]);
    LinearLayout chips = hero.findViewById(R.id.llAnalysisPlanChips);
    chips.removeAllViews();
    List<FocusArea> focus = stored.getPlan().getFocus();
    for (int i = 0; i < focus.size(); i++) {
      chips.addView(chip(chips, i, focus.get(i), stored.getPayload()));
    }
    // A single focus is the card right under it, so a chip would point at nothing new.
    hero.findViewById(R.id.hsAnalysisPlanChips)
        .setVisibility(focus.size() > 1 ? View.VISIBLE : View.GONE);
  }

  /**
   * A headline written to open on what went well is two sentences, the first being that, so it is
   * split off. A one-sentence headline, which every plan before that rule has, keeps it whole.
   *
   * @return what went well or null, then the rest or null
   */
  static String[] splitWin(String headline) {
    if (headline == null || headline.trim().isEmpty()) {
      return new String[] {null, null};
    }
    String said = headline.trim();
    BreakIterator sentences = BreakIterator.getSentenceInstance(Locale.ENGLISH);
    sentences.setText(said);
    int end = sentences.next();
    if (end == BreakIterator.DONE || end >= said.length()) {
      return new String[] {null, said};
    }
    return new String[] {said.substring(0, end).trim(), said.substring(end).trim()};
  }

  /**
   * The plan an install without the coach would get, drawn blank: the plan's card with no headline
   * and one card per kind of focus, each with its real title and step and none of its figures.
   */
  public void setPreview(View preview) {
    LinearLayout chips = preview.findViewById(R.id.llCoachPreviewChips);
    LinearLayout areas = preview.findViewById(R.id.llCoachPreviewAreas);
    chips.removeAllViews();
    areas.removeAllViews();
    for (int i = 0; i < PREVIEW_REASONS.length; i++) {
      int colour = stepColour(PREVIEW_FAMILIES[i]);
      View chip = inflater.inflate(R.layout.coach_hero_chip, chips, false);
      TextView rank = chip.findViewById(R.id.tvCoachChipRank);
      rank.setText(String.valueOf(i + 1));
      tint(rank, colour);
      TextView name = chip.findViewById(R.id.tvCoachChipName);
      name.setText(Utils.toSmartCubeStepLocalizedName(activity, PREVIEW_FAMILIES[i], 0));
      chip.setClickable(false);
      chips.addView(chip);
      areas.addView(blankCard(areas, i + 1, PREVIEW_REASONS[i], PREVIEW_FAMILIES[i], colour));
    }
  }

  private View blankCard(LinearLayout parent, int rank, FocusArea.Reason reason, String family,
      int colour) {
    // The card's own margin goes to the frame, so the light is drawn over the card alone.
    View card = inflater.inflate(R.layout.coach_focus_area, parent, false);
    ShimmerLayout frame = new ShimmerLayout(activity);
    frame.setLayoutParams(card.getLayoutParams());
    card.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.WRAP_CONTENT));
    header(card, rank, family, colour);
    PlanCardLight.apply(card, R.drawable.coach_focus_card, colour);
    FocusArea area = new FocusArea(reason.code(), Collections.<String>emptyList(),
        Collections.<Evidence>emptyList(), null, null);
    ((TextView) card.findViewById(R.id.tvCoachAreaTitle)).setText(title(area));

    card.findViewById(R.id.llCoachAreaFigure).setVisibility(View.VISIBLE);
    blank(card.findViewById(R.id.tvCoachAreaNumber), 84, 34, 0);
    card.findViewById(R.id.tvCoachAreaNumber).getBackground()
        .setTint(ColorUtils.setAlphaComponent(colour, 0x38));
    ((TextView) card.findViewById(R.id.tvCoachAreaUnit)).setText(
        reason == FocusArea.Reason.SLOW_CASE ? activity.getString(R.string.coach_fig_seconds_lost)
            : activity.getString(R.string.coach_fig_spent_looking,
                Utils.toSmartCubeStepLocalizedName(activity, family, 0)));
    blank(card.findViewById(R.id.tvCoachAreaSub), 120, 10, 6);

    LinearLayout bars = card.findViewById(R.id.llCoachAreaBars);
    bars.setVisibility(View.VISIBLE);
    if (reason == FocusArea.Reason.SLOW_CASE) {
      bars.addView(blankBar(bars, 1, colour, true));
      bars.addView(blankBar(bars, 0.64, colour, false));
    } else {
      View split = split(bars, new CoachFocusFigure.Bar[] {
          new CoachFocusFigure.Bar("", 0.55, "", false),
          new CoachFocusFigure.Bar("", 0.45, "", true)}, colour);
      ((View) split.findViewById(R.id.tvCoachSplitFirst).getParent()).setVisibility(View.GONE);
      bars.addView(split);
    }
    TextView text = card.findViewById(R.id.tvCoachAreaText);
    text.setVisibility(View.VISIBLE);
    blank(text, LinearLayout.LayoutParams.MATCH_PARENT, 12, 14);

    Button drill = card.findViewById(R.id.buCoachAreaDrill);
    drill.setVisibility(View.VISIBLE);
    drill.setBackgroundResource(R.drawable.accent_ghost_button);
    drill.setTextColor(ContextCompat.getColor(activity, R.color.lightblue));
    drill.setText(withMark(drill, R.drawable.ic_coach_lock, R.string.coach_drill_cases));
    drill.setAlpha(0.6f);
    drill.setClickable(false);
    frame.addView(card);
    return frame;
  }

  private View blankBar(LinearLayout parent, double share, int colour, boolean own) {
    View row = bar(parent, new CoachFocusFigure.Bar("", share, "", own), 1, colour);
    blank(row.findViewById(R.id.tvCoachBarLabel), 64, 10, 0);
    return row;
  }

  /** A line of text with nothing in it, drawn as a rounded bar of the given size in dp. */
  private void blank(View view, int width, int height, int top) {
    float density = activity.getResources().getDisplayMetrics().density;
    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
    params.width = width < 0 ? width : Math.round(width * density);
    params.height = Math.round(height * density);
    params.topMargin = Math.round(top * density);
    view.setLayoutParams(params);
    if (view instanceof TextView) {
      ((TextView) view).setText(null);
    }
    view.setBackgroundResource(R.drawable.coach_skeleton);
    view.getBackground().mutate();
  }

  private View chip(LinearLayout parent, final int index, FocusArea area, CoachPayload payload) {
    View chip = inflater.inflate(R.layout.coach_hero_chip, parent, false);
    TextView rank = chip.findViewById(R.id.tvCoachChipRank);
    rank.setText(String.valueOf(index + 1));
    tint(rank, colour(area));
    ((TextView) chip.findViewById(R.id.tvCoachChipName)).setText(shortName(area, payload));
    chip.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (index < container.getChildCount()) {
          scrollTo(container.getChildAt(index));
        }
      }
    });
    return chip;
  }

  /** A focus in a word or two: its one case, else its step, else its title. */
  private String shortName(FocusArea area, CoachPayload payload) {
    if (area.getCodes().size() == 1
        && payload.value("cases." + area.getCodes().get(0) + ".mean_ms") != null) {
      return Utils.toSmartCubeCaseHeadline(activity, area.getCodes().get(0));
    }
    String family = CoachFocusFigure.familyOf(area);
    return family != null ? Utils.toSmartCubeStepLocalizedName(activity, family, 0) : title(area);
  }

  private void scrollTo(View target) {
    int y = 0;
    View view = target;
    ViewParent parent = view.getParent();
    while (parent instanceof View && !(parent instanceof ScrollView)) {
      y += view.getTop();
      view = (View) parent;
      parent = view.getParent();
    }
    if (parent instanceof ScrollView) {
      int margin = activity.getResources().getDimensionPixelSize(R.dimen.space_m);
      ((ScrollView) parent).smoothScrollTo(0, Math.max(y - margin, 0));
    }
  }

  /** The standing practice sets, one outlined card per family, into their own container. */
  public void setPractice(LinearLayout into, List<CoachPractice.Set> sets) {
    into.removeAllViews();
    for (CoachPractice.Set set : sets) {
      View card = set.isLearning() ? learningCard(into, set) : practiceCard(into, set);
      if (card != null) {
        into.addView(card);
      }
    }
  }

  /**
   * The practice sets as an install without the coach reads them: the same cards with blank
   * charts, since which cases cost it the most is the coach's to say.
   *
   * @param learnNext the step whose cases to learn next are drawn too, "pll" or "oll", or null for
   *     a solver with both learnt
   */
  public void setPracticeTeaser(LinearLayout into, String learnNext) {
    into.removeAllViews();
    for (String family : TEASER_FAMILIES) {
      into.addView(teaserCard(into, activity.getString(R.string.coach_practice_set,
          Utils.toSmartCubeStepLocalizedName(activity, family, 0)), TEASER_CASES,
          R.drawable.coach_practice_target_dots));
    }
    if (learnNext != null) {
      // Five dots: a learning case clears on five clean reps in a row.
      into.addView(teaserCard(into, activity.getString(learnNextTitle(learnNext)),
          Options.INSTANCE.getLearnNextCount(), R.drawable.coach_learning_target_dots));
    }
  }

  private static int learnNextTitle(String family) {
    return "pll".equals(family) ? R.string.coach_learn_next_pll : R.string.coach_learn_next;
  }

  private View teaserCard(LinearLayout parent, String title, int cases, int dots) {
    View card = inflater.inflate(R.layout.coach_practice_set, parent, false);
    ((TextView) card.findViewById(R.id.tvCoachPracticeTitle)).setText(title);
    LinearLayout row = card.findViewById(R.id.llCoachPracticeCases);
    for (int i = 0; i < cases; i++) {
      View silhouette = inflater.inflate(R.layout.coach_practice_silhouette, row, false);
      ((ImageView) silhouette.findViewById(R.id.ivCoachPracticeDots)).setImageResource(dots);
      row.addView(silhouette);
    }
    card.findViewById(R.id.buCoachPracticeDrill).setVisibility(View.GONE);
    return card;
  }

  /**
   * The PLLs or OLLs to learn next, as many as the solver picks in the title's pill. Null once every
   * case the service offered has been cleared in a drill since.
   */
  private View learningCard(LinearLayout parent, final CoachPractice.Set set) {
    final Set<String> learned = Options.INSTANCE.getLearnedCases();
    final int offered = set.countToLearn(learned);
    Set<String> own = Options.INSTANCE.getLearnPick(set.getFamily() + "_");
    if (offered == 0 && own == null) {
      return null;
    }
    final View card = inflater.inflate(R.layout.coach_practice_set, parent, false);
    ((TextView) card.findViewById(R.id.tvCoachPracticeTitle)).setText(
        learnNextTitle(set.getFamily()));
    bindChoose(card, set, learned);
    if (own != null) {
      // The user's own pick stays as chosen; a case cleared since is ticked, not dropped.
      card.findViewById(R.id.tvCoachPracticeNote).setVisibility(View.VISIBLE);
      bindLearning(card, set.withCases(new ArrayList<String>(own)), learned);
      return card;
    }
    final int shown = Math.min(Options.INSTANCE.getLearnNextCount(), offered);
    bindLearning(card, set.firstToLearn(shown, learned), learned);

    final Spinner spinner = card.findViewById(R.id.spCoachPracticeCount);
    List<String> counts = new ArrayList<String>();
    for (int i = 1; i <= offered; i++) {
      counts.add(activity.getResources().getQuantityString(R.plurals.coach_learn_next_count, i, i));
    }
    // The list marks the count on show, as the graph's period pill does.
    ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity,
        R.layout.pill_spinner_item, counts) {
      @Override
      public View getDropDownView(int position, View convertView, ViewGroup group) {
        TextView row = (TextView) super.getDropDownView(position, convertView, group);
        row.setTextColor(ContextCompat.getColor(activity,
            position == spinner.getSelectedItemPosition() ? R.color.lightblue
                : R.color.secondary_text));
        return row;
      }
    };
    adapter.setDropDownViewResource(R.layout.pill_spinner_dropdown_item);
    spinner.setAdapter(adapter);
    spinner.setSelection(shown - 1, false);
    spinner.setVisibility(View.VISIBLE);
    spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      private int count = shown;

      @Override
      public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
        if (position + 1 != count) {
          count = position + 1;
          Options.INSTANCE.setLearnNextCount(count);
          bindLearning(card, set.firstToLearn(count, learned), learned);
        }
      }

      @Override
      public void onNothingSelected(AdapterView<?> adapterView) {
      }
    });
    return card;
  }

  /** The chip in the step's colour that opens the case picker on this family. */
  private void bindChoose(View card, final CoachPractice.Set set, final Set<String> learned) {
    TextView choose = card.findViewById(R.id.tvCoachPracticeChoose);
    final String family = set.getFamily() + "_";
    int colour = StepPalette.cfop(activity).colorFor(family);
    choose.setTextColor(colour);
    GradientDrawable chip = new GradientDrawable();
    chip.setCornerRadius(12 * activity.getResources().getDisplayMetrics().density);
    chip.setColor((colour & 0x00FFFFFF) | (CHOOSE_CHIP_ALPHA << 24));
    choose.setBackground(chip);
    choose.setVisibility(View.VISIBLE);
    choose.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        CoachPractice.Set suggested =
            set.firstToLearn(Options.INSTANCE.getLearnNextCount(), learned);
        DialogUtils.showFragment((FragmentActivity) activity, DrillCasesDialog.newLearning(family,
            suggested == null ? Collections.<String>emptyList() : suggested.getCases()));
      }
    });
  }

  private void bindLearning(View card, final CoachPractice.Set shown, Set<String> learned) {
    LinearLayout rows = card.findViewById(R.id.llCoachPracticeCases);
    rows.removeAllViews();
    rows.setOrientation(LinearLayout.VERTICAL);
    // Rows of five, the last padded so its charts keep the others' width.
    List<String> cases = shown.getCases();
    for (int from = 0; from < cases.size(); from += LEARNING_ROW) {
      LinearLayout row = new LinearLayout(activity);
      row.setOrientation(LinearLayout.HORIZONTAL);
      if (from > 0) {
        row.setPadding(0, activity.getResources().getDimensionPixelSize(R.dimen.space_s), 0, 0);
      }
      List<String> slice = cases.subList(from, Math.min(from + LEARNING_ROW, cases.size()));
      addCaseCharts(row, slice, learned);
      for (int i = slice.size(); i < LEARNING_ROW && cases.size() > LEARNING_ROW; i++) {
        row.addView(new View(activity), new LinearLayout.LayoutParams(0, 0, 1));
      }
      rows.addView(row);
    }
    Button button = card.findViewById(R.id.buCoachPracticeDrill);
    button.setText(withPlay(button, R.string.coach_learn_cases));
    button.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        launch(shown.getDrill());
      }
    });
  }

  private View practiceCard(LinearLayout parent, final CoachPractice.Set set) {
    View card = inflater.inflate(R.layout.coach_practice_set, parent, false);
    ((TextView) card.findViewById(R.id.tvCoachPracticeTitle)).setText(activity.getString(
        R.string.coach_practice_set,
        Utils.toSmartCubeStepLocalizedName(activity, set.getFamily(), 0)));
    addCaseCharts((LinearLayout) card.findViewById(R.id.llCoachPracticeCases), set.getCases());
    Button button = card.findViewById(R.id.buCoachPracticeDrill);
    button.setText(withPlay(button, set.getCases().size() == 1 ? R.string.coach_drill_case
        : R.string.coach_drill_cases));
    button.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        launch(set.getDrill());
      }
    });
    return card;
  }

  /** One small chart per case, each over its short name, sharing the row equally. */
  private void addCaseCharts(LinearLayout row, List<String> codes) {
    addCaseCharts(row, codes, Collections.<String>emptySet());
  }

  /** @param ticked cases whose name carries a tick, as learned */
  private void addCaseCharts(LinearLayout row, List<String> codes, Set<String> ticked) {
    for (String code : codes) {
      View cell = inflater.inflate(R.layout.coach_practice_case, row, false);
      LastLayerDiagram diagram = LastLayerDiagram.forCase(code);
      LastLayerCaseView chart = cell.findViewById(R.id.vCoachPracticeChart);
      chart.setDiagram(diagram);
      chart.setVisibility(diagram == null ? View.INVISIBLE : View.VISIBLE);
      ((TextView) cell.findViewById(R.id.tvCoachPracticeName)).setText(ticked.contains(code)
          ? activity.getString(R.string.learn_pick_known_case, shortName(code)) : shortName(code));
      cell.setOnClickListener(caseOpener(code));
      row.addView(cell);
    }
  }

  /** A case as a cuber writes it under its chart: Rb, 21. */
  private static String shortName(String code) {
    String name = code.substring(code.indexOf('_') + 1);
    return name.isEmpty() ? name : name.substring(0, 1).toUpperCase(Locale.US) + name.substring(1);
  }

  private View areaCard(int rank, FocusArea area, CoachPayload payload, List<String> undealable) {
    View card = inflater.inflate(R.layout.coach_focus_area, container, false);
    int colour = colour(area);
    header(card, rank, CoachFocusFigure.familyOf(area), colour);
    PlanCardLight.apply(card, R.drawable.coach_focus_card, colour);
    ((TextView) card.findViewById(R.id.tvCoachAreaTitle)).setText(title(area));
    CoachFocusFigure figure = CoachFocusFigure.of(activity, area, payload);
    showFigure(card, figure, colour);

    String text = area.getText() != null ? area.getText() : observation(area, payload);
    TextView tvText = card.findViewById(R.id.tvCoachAreaText);
    tvText.setText(text);
    tvText.setVisibility(text == null ? View.GONE : View.VISIBLE);

    LinearLayout cases = card.findViewById(R.id.llCoachAreaCases);
    Set<String> shown = new HashSet<String>();
    for (String code : area.getCodes()) {
      if (payload.value("cases." + code + ".mean_ms") != null) {
        if (figure == null || !figure.coversCases) {
          cases.addView(caseRow(cases, area.getReason(), payload, code));
          shown.add(code);
        }
      } else if (payload.value("f2l_habits." + code + ".extra_moves") != null) {
        cases.addView(pairRow(cases, payload, code));
      }
    }

    DrillSpec drill = bindDrill(card, area, undealable);
    // Every case the drill deals is shown: a card naming some gets rows for the rest, a card about
    // a step gets the charts. Not for a cross or a pair drill, which have no flat picture to show.
    if (drill != null && drill.getType() != DrillSpec.Type.CROSS
        && drill.getType() != DrillSpec.Type.F2L_PAIR
        && (figure == null || !figure.coversCases)) {
      List<String> bare = new ArrayList<String>();
      boolean named = cases.getChildCount() > 0;
      for (String code : drill.getCases()) {
        if (shown.contains(code)) {
          continue;
        }
        if (named && payload.value("cases." + code + ".mean_ms") != null) {
          cases.addView(caseRow(cases, null, payload, code));
        } else {
          bare.add(code);
        }
      }
      if (!bare.isEmpty()) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, activity.getResources().getDimensionPixelSize(R.dimen.space_s), 0, 0);
        addCaseCharts(row, bare);
        cases.addView(row);
      }
    }
    return card;
  }

  /** The focus's number in its step's colour, and the step over its title. */
  private void header(View card, int rank, String family, int colour) {
    TextView tvRank = card.findViewById(R.id.tvCoachAreaRank);
    tvRank.setText(String.valueOf(rank));
    tint(tvRank, colour);
    TextView tvFamily = card.findViewById(R.id.tvCoachAreaFamily);
    tvFamily.setText(
        family == null ? null : Utils.toSmartCubeStepLocalizedName(activity, family, 0));
    tvFamily.setTextColor(colour);
    tvFamily.setVisibility(family == null ? View.GONE : View.VISIBLE);
  }

  private void showFigure(View card, CoachFocusFigure figure, int colour) {
    LinearLayout bars = card.findViewById(R.id.llCoachAreaBars);
    card.findViewById(R.id.llCoachAreaFigure)
        .setVisibility(figure == null ? View.GONE : View.VISIBLE);
    boolean compared = figure != null && (figure.split != null || !figure.bars.isEmpty());
    bars.setVisibility(compared ? View.VISIBLE : View.GONE);
    if (figure == null) {
      return;
    }
    SpannableString number = new SpannableString(
        figure.suffix == null ? figure.number : figure.number + figure.suffix);
    if (figure.suffix != null) {
      number.setSpan(new RelativeSizeSpan(0.6f), figure.number.length(), number.length(),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    ((TextView) card.findViewById(R.id.tvCoachAreaNumber)).setText(number);
    fill(card, R.id.tvCoachAreaUnit, figure.unit);
    fill(card, R.id.tvCoachAreaSub, figure.sub);
    LastLayerDiagram diagram =
        figure.chartCode == null ? null : LastLayerDiagram.forCase(figure.chartCode);
    LastLayerCaseView chart = card.findViewById(R.id.vCoachAreaChart);
    chart.setDiagram(diagram);
    chart.setVisibility(diagram == null ? View.GONE : View.VISIBLE);
    if (figure.split != null) {
      bars.addView(split(bars, figure.split, colour));
      return;
    }
    double max = 0;
    for (CoachFocusFigure.Bar bar : figure.bars) {
      max = Math.max(max, bar.value);
    }
    for (CoachFocusFigure.Bar bar : figure.bars) {
      bars.addView(bar(bars, bar, max, colour));
    }
  }

  /** One side of a comparison, its bar to the scale of the largest side. */
  private View bar(LinearLayout parent, CoachFocusFigure.Bar bar, double max, int colour) {
    View row = inflater.inflate(R.layout.coach_bar_row, parent, false);
    int filled = max <= 0 ? 0 : (int) Math.round(SCALE * bar.value / max);
    weigh(row.findViewById(R.id.vCoachBarFill), filled);
    weigh(row.findViewById(R.id.vCoachBarRest), SCALE - filled);
    row.findViewById(R.id.vCoachBarFill).getBackground().mutate()
        .setTint(bar.own ? colour : wash(colour));
    int ink = ContextCompat.getColor(activity, bar.own ? R.color.white : R.color.secondary_text);
    TextView label = row.findViewById(R.id.tvCoachBarLabel);
    label.setText(bar.label);
    label.setTextColor(ink);
    TextView value = row.findViewById(R.id.tvCoachBarValue);
    value.setText(bar.shown);
    value.setTextColor(ink);
    if (bar.own) {
      value.setTypeface(value.getTypeface(), Typeface.BOLD);
    }
    return row;
  }

  private View split(LinearLayout parent, CoachFocusFigure.Bar[] parts, int colour) {
    View split = inflater.inflate(R.layout.coach_split_bar, parent, false);
    int[] bars = {R.id.vCoachSplitFirst, R.id.vCoachSplitSecond};
    int[] labels = {R.id.tvCoachSplitFirst, R.id.tvCoachSplitSecond};
    double total = parts[0].value + parts[1].value;
    for (int i = 0; i < 2; i++) {
      View part = split.findViewById(bars[i]);
      weigh(part, total <= 0 ? 1 : (int) Math.round(SCALE * parts[i].value / total));
      part.getBackground().mutate().setTint(parts[i].own ? colour : wash(colour));
      ((TextView) split.findViewById(labels[i])).setText(parts[i].label);
    }
    return split;
  }

  private static void weigh(View view, int weight) {
    ((LinearLayout.LayoutParams) view.getLayoutParams()).weight = weight;
  }

  /** The second side of a comparison, and the first part of a split, as every split bar fades it. */
  private static int wash(int colour) {
    return StepPalette.wash(colour);
  }

  private static void tint(View view, int colour) {
    view.getBackground().mutate().setTint(colour);
  }

  private int colour(FocusArea area) {
    return stepColour(CoachFocusFigure.familyOf(area));
  }

  /** A step's colour, as the step bar draws it, or the accent for none. */
  private int stepColour(String family) {
    int colour = R.color.lightblue;
    if ("cross".equals(family)) {
      colour = R.color.step_cross;
    } else if ("f2l".equals(family)) {
      colour = R.color.step_f2l;
    } else if ("oll".equals(family)) {
      colour = R.color.step_oll;
    } else if ("pll".equals(family)) {
      colour = R.color.step_pll;
    }
    return ContextCompat.getColor(activity, colour);
  }

  private CharSequence withPlay(Button button, int text) {
    return withMark(button, R.drawable.ic_drill_play, text);
  }

  /** A button's text behind a small mark in the text's own colour. */
  private CharSequence withMark(Button button, int mark, int text) {
    SpannableString said = new SpannableString("\u00A0\u00A0" + activity.getString(text));
    Drawable drawn = ContextCompat.getDrawable(activity, mark).mutate();
    int size = Math.round(button.getTextSize() * 0.9f);
    drawn.setBounds(0, 0, size, size);
    drawn.setTint(button.getCurrentTextColor());
    said.setSpan(new CenteredImageSpan(drawn), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    return said;
  }

  /**
   * The kind of focus as its title says it. The step it is about is drawn over it, so it is never
   * written into it.
   */
  private String title(FocusArea area) {
    if (area.getReason() == null) {
      return activity.getString(R.string.coach_reason_unknown);
    }
    switch (area.getReason()) {
      case SLOW_CASE:
        return activity.getString(R.string.coach_reason_slow_case);
      case SLOW_UNDER_PRESSURE:
        return activity.getString(R.string.coach_reason_slow_under_pressure);
      case RECOGNITION_HEAVY:
        return activity.getString(R.string.coach_reason_recognition_heavy);
      case TWO_LOOK_OLL:
      case TWO_LOOK_PLL:
        return activity.getString(R.string.coach_reason_two_look_oll);
      case SLOW_STEP:
        return activity.getString(R.string.coach_reason_slow_step);
      case STUCK_PIECES:
        return activity.getString(R.string.coach_reason_stuck_pieces);
      case SLOW_FIRST_PAIR:
        return activity.getString(R.string.coach_reason_slow_first_pair);
      case LONG_PAIR:
        return activity.getString(R.string.coach_reason_long_pair);
      case STALLING:
        return activity.getString(R.string.coach_reason_stalling);
      case UNEVEN_STEP:
        return activity.getString(R.string.coach_reason_uneven_step);
      default:
        return activity.getString(R.string.coach_reason_inconsistent_case);
    }
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
    if (area.getReason() == FocusArea.Reason.SLOW_STEP && !area.getCodes().isEmpty()) {
      String code = area.getCodes().get(0);
      StepShares shares = StepShares.of(payload);
      Double took = shares.actual(code);
      Double should = shares.expected(code);
      if (took == null || should == null) {
        return null;
      }
      return activity.getString(R.string.coach_step_share_body,
          Utils.toSmartCubeStepLocalizedName(activity, code, 0), percent(took), percent(should));
    }
    if ((area.getReason() == FocusArea.Reason.TWO_LOOK_OLL
        || area.getReason() == FocusArea.Reason.TWO_LOOK_PLL) && !area.getCodes().isEmpty()) {
      return twoLook(payload, area.getCodes().get(0));
    }
    if (area.getReason() == FocusArea.Reason.STUCK_PIECES) {
      Double share = payload.value("f2l_groups.f2l_stuck.share");
      Double stuck = payload.value("f2l_groups.f2l_stuck.mean_ms");
      Double basic = payload.value("f2l_groups.f2l_basic.mean_ms");
      return share == null || stuck == null || basic == null ? null
          : activity.getString(R.string.coach_stuck_body, percent(share), time(stuck), time(basic));
    }
    if (area.getReason() == FocusArea.Reason.SLOW_FIRST_PAIR) {
      Double first = payload.value("f2l_groups.f2l_first.recognition_ms");
      Double later = payload.value("f2l_groups.f2l_later.recognition_ms");
      return first == null || later == null ? null
          : activity.getString(R.string.coach_first_pair_body, time(first), time(later));
    }
    if (area.getReason() == FocusArea.Reason.STALLING && !area.getCodes().isEmpty()) {
      String code = area.getCodes().get(0);
      Double stalls = payload.value("turning." + code + ".stalls");
      Double stallMs = payload.value("turning." + code + ".stall_ms");
      Double mean = payload.value("families." + code + ".mean_ms");
      return stalls == null || stallMs == null || mean == null ? null
          : activity.getString(R.string.coach_stalling_body,
              Utils.toSmartCubeStepLocalizedName(activity, code, 0), moves(stalls), time(stallMs),
              time(mean));
    }
    if (area.getReason() == FocusArea.Reason.UNEVEN_STEP && !area.getCodes().isEmpty()) {
      String code = area.getCodes().get(0);
      Double mean = payload.value("families." + code + ".mean_ms");
      Double spread = payload.value("families." + code + ".std_dev_ms");
      Double best = payload.value("families." + code + ".best_ms");
      return mean == null || spread == null || best == null ? null
          : activity.getString(R.string.coach_uneven_body,
              Utils.toSmartCubeStepLocalizedName(activity, code, 0), time(mean), time(spread),
              time(best));
    }
    if (area.getReason() == FocusArea.Reason.LONG_PAIR) {
      Double solves = payload.value("f2l_moves.count");
      Double turned = payload.value("f2l_moves.turned_moves");
      Double usual = payload.value("f2l_moves.usual_moves");
      return solves == null || turned == null || usual == null ? null
          : activity.getString(R.string.coach_long_pair_body, solves.intValue(), moves(turned),
              moves(usual));
    }
    return null;
  }

  /**
   * What a last layer step costs, how often it took two looks, and how many of its cases are behind
   * that. The three read as one paragraph and any of them may be missing, so they are joined rather
   * than formatted together.
   */
  private String twoLook(CoachPayload payload, String family) {
    StringBuilder said = new StringBuilder();
    String step = Utils.toSmartCubeStepLocalizedName(activity, family, 0);
    StepShares shares = StepShares.of(payload);
    if (overBaseline(shares, family)) {
      said.append(activity.getString(R.string.coach_two_look_share, step,
          percent(shares.actual(family)), percent(shares.expected(family))));
    }
    Double looks = payload.value(twoLookCountPath(family));
    Double solves = payload.value("windows.families");
    if (looks != null && solves != null) {
      append(said, activity.getString(R.string.coach_two_look_body, step, looks.intValue(),
          solves.intValue()));
    }
    Double toLearn = payload.value("to_learn_cases." + family + ".count");
    if (toLearn != null && toLearn.intValue() > 0) {
      append(said, activity.getString(R.string.coach_two_look_to_learn, toLearn.intValue()));
    }
    return said.length() == 0 ? null : said.toString();
  }

  /** Where a payload counts the solves that took the step in two looks. */
  static String twoLookCountPath(String family) {
    return "pll".equals(family) ? "two_look_pll_count" : "two_look_count";
  }

  private static void append(StringBuilder said, String sentence) {
    if (said.length() > 0) {
      said.append(' ');
    }
    said.append(sentence);
  }

  private View caseRow(LinearLayout parent, FocusArea.Reason reason, CoachPayload payload,
      final String code) {
    View row = inflater.inflate(R.layout.coach_case_row, parent, false);

    LastLayerDiagram diagram = LastLayerDiagram.forCase(code);
    LastLayerCaseView chart = row.findViewById(R.id.vCoachCaseChart);
    chart.setDiagram(diagram);
    chart.setVisibility(diagram == null ? View.GONE : View.VISIBLE);

    ((TextView) row.findViewById(R.id.tvCoachCaseName))
        .setText(Utils.toSmartCubeCaseHeadline(activity, code));
    fill(row, R.id.tvCoachCaseFigures, figures(reason, payload, code));
    fill(row, R.id.tvCoachCaseCost, cost(reason, payload, code));
    row.setOnClickListener(caseOpener(code));
    return row;
  }

  /** Opens the case with its algorithms, where one can be starred or unstarred. */
  private View.OnClickListener caseOpener(final String code) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        DialogUtils.showFragment((FragmentActivity) activity,
            CaseAlgorithmsDialog.newInstance(code));
      }
    };
  }

  /**
   * An F2L case, named by the algorithm its extra moves are counted over and never by its number,
   * which is the site's and not something a solver knows it by. The button shows the case itself.
   */
  private View pairRow(LinearLayout parent, CoachPayload payload, final String code) {
    View row = inflater.inflate(R.layout.coach_pair_row, parent, false);
    fill(row, R.id.tvCoachCaseName, CaseAlgorithms.shortestListed(code));
    Double extra = payload.value("f2l_habits." + code + ".extra_moves");
    fill(row, R.id.tvCoachCaseFigures,
        activity.getString(R.string.coach_pair_extra, moves(extra)));
    Double count = payload.value("f2l_habits." + code + ".count");
    Double offList = payload.value("f2l_habits." + code + ".off_list_rate");
    fill(row, R.id.tvCoachCaseCost, count == null || offList == null ? null
        : activity.getString(R.string.coach_pair_off_list, count.intValue(), rounded(offList)));
    row.findViewById(R.id.buCoachPairCase).setOnClickListener(caseOpener(code));
    return row;
  }

  /** What the case costs, said the way its reason wants it said. */
  private String figures(FocusArea.Reason reason, CoachPayload payload, String code) {
    if (reason == FocusArea.Reason.SLOW_UNDER_PRESSURE) {
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
  private String cost(FocusArea.Reason reason, CoachPayload payload, String code) {
    if (reason == FocusArea.Reason.INCONSISTENT_CASE) {
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
   *
   * @return the drill whose button is shown, or null where there is no button
   */
  private DrillSpec bindDrill(View card, FocusArea area, List<String> undealable) {
    Button button = card.findViewById(R.id.buCoachAreaDrill);
    if (area.getDrill() == null) {
      return null;
    }
    final DrillSpec spec;
    try {
      spec = DrillSpec.fromJson(area.getDrill());
    } catch (IllegalArgumentException e) {
      return null;
    }
    // A button over a case nothing can deal opens a drill with nothing in it.
    for (String code : spec.getCases()) {
      if (undealable.contains(code)) {
        return null;
      }
    }
    button.setVisibility(View.VISIBLE);
    // A cross drill is named by no case at all, so counting its case list would label it "these cases".
    button.setText(withPlay(button, spec.getType() == DrillSpec.Type.CROSS
        ? R.string.drill_cross_title
        : spec.getType() == DrillSpec.Type.F2L_LOOKAHEAD ? R.string.coach_drill_lookahead
        : spec.isLearning() ? R.string.coach_learn_cases
        : spec.getCases().size() == 1 ? R.string.coach_drill_case : R.string.coach_drill_cases));
    button.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        launch(spec);
      }
    });
    return spec;
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
      // The coach cannot know the solver's cross face; the setup screen's standing answer is it.
      // Untimed whatever the plan says: planning the whole cross at all comes before planning it fast.
      String face = DrillSetupActivity.layerFace().opposite().name();
      intent = new Intent(activity, CrossDrillActivity.class);
      intent.putExtra(CrossDrillActivity.EXTRA_SPEC, DrillSpec.cross(spec.getId(), face,
          spec.getReps(), 0, spec.getLabel()).toJson());
    } else {
      intent = new Intent(activity, DrillActivity.class);
      intent.putExtra(DrillActivity.EXTRA_SPEC, spec.toJson());
      intent.putExtra(DrillActivity.EXTRA_LAYER_FACE, DrillSetupActivity.layerFace().name());
      intent.putExtra(DrillActivity.EXTRA_SOLVE_TYPE, solveType);
    }
    intent.putExtra(DrillScreenActivity.EXTRA_RECORDING, true);
    activity.startActivity(intent);
  }

  /** A mean of moves, to the tenth the payload carries. */
  private static String moves(Double moves) {
    return String.format(Locale.getDefault(), "%.1f", moves.doubleValue());
  }

  private String time(Double ms) {
    return FormatterService.INSTANCE.formatSolveTime(Long.valueOf(Math.round(ms.doubleValue())));
  }

  /**
   * Whether the last layer is worth pricing, which is not the same question as whether it is taken
   * twice: a solver can two-look half his solves and still land under the one-look baseline. Saying
   * what it costs where it costs nothing reads as an accusation the figure beside it disproves.
   *
   * <p>The two shares must differ once rounded, since the sentence prints them rounded and "16%
   * where 16% is usual" says nothing at all.
   */
  private static boolean overBaseline(StepShares shares, String family) {
    Double took = shares.actual(family);
    Double should = shares.expected(family);
    return took != null && should != null && rounded(took) > rounded(should);
  }

  private static int rounded(Double share) {
    return (int) Math.round(share.doubleValue() * 100);
  }

  private String percent(Double share) {
    return activity.getString(R.string.coach_percent, Integer.valueOf(rounded(share)));
  }
}
