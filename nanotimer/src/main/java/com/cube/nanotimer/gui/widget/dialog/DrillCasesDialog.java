package com.cube.nanotimer.gui.widget.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.App;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.cases.CaseGroup;
import com.cube.nanotimer.cases.CaseSet;
import com.cube.nanotimer.cases.CaseSets;
import com.cube.nanotimer.gui.widget.NanoTimerDialogFragment;
import com.cube.nanotimer.gui.widget.cases.CaseFilterBar;
import com.cube.nanotimer.gui.widget.cases.CaseTile;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.session.CaseKnowledge;
import com.cube.nanotimer.util.DrillCasePreset;
import com.cube.nanotimer.util.helper.GUIUtils;
import com.cube.nanotimer.util.view.StepPalette;
import com.cube.nanotimer.util.view.ViewSegments;
import com.cube.nanotimer.vo.CaseHistory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Which cases of a family a drill runs, picked off a chart of all of them.
 *
 * <p>The whole point of picking is that most people know some of a set and not the rest, so drilling
 * all 57 OLLs when you know eleven of them spends most of the session on cases you cannot attempt.
 * The pick is kept, since it is a fact about the user rather than about this drill.
 *
 * <p>Every tap writes through to the preferences rather than being gathered up and saved at the end.
 * There is nothing to confirm — a case is either in the set or it is not — and it means the picker
 * survives being turned sideways or backed out of with the same answer it was showing.
 *
 * <p>A pick worth coming back to can be given a name, which is all a preset is, reached from the
 * bookmark on the title line.
 *
 * <p><b>The stage is the mark.</b> A picked case is a lit tile and an unpicked one is the same
 * picture turned down behind a hairline, rather than the other way round: by default all 57 are
 * picked, so a ring on a picked case was a ring on almost everything and the handful actually
 * turned off were left to be found by their absence.
 */
public class DrillCasesDialog extends NanoTimerDialogFragment
    implements DrillPresetsDialog.Listener {

  /** Told when the picker closes, so the screen behind it can say what is now picked. */
  public interface Listener {
    void onDrillCasesPicked(String family);
  }

  private static final String ARG_FAMILY = "family";
  private static final String ARG_SUGGESTED = "suggested";

  /** Of the screen: how tall the picker may grow, leaving the dialog theme its own margins. */
  private static final float MAX_HEIGHT = 0.92f;

  private String family;
  private final List<String> cases = new ArrayList<String>();
  private final Set<String> picked = new LinkedHashSet<String>();
  private List<DrillCasePreset> presets;

  /** Choosing the cases to learn next rather than a drill's cases; null when not. */
  private List<String> suggested;
  private final Set<String> known = new HashSet<String>();
  private final List<String> shown = new ArrayList<String>();
  private CheckBox cbShowKnown;
  private CaseFilterBar filter;

  private TextView tvCount;
  private TextView tvAll;
  private TextView tvNone;
  private ImageButton buPresets;
  private GridView grid;
  private CasesAdapter adapter;

  /** @param family the case code prefix, {@code "oll_"} or {@code "pll_"} */
  public static DrillCasesDialog newInstance(String family) {
    DrillCasesDialog frag = new DrillCasesDialog();
    Bundle args = new Bundle();
    args.putString(ARG_FAMILY, family);
    frag.setArguments(args);
    return frag;
  }

  /**
   * The same picker choosing the cases to learn next. "Suggested" takes the place of "All", known
   * cases are left out unless asked for, and the pick is kept apart from a drill's.
   *
   * @param family the case code prefix, {@code "oll_"} or {@code "pll_"}
   * @param suggested the cases the coach suggests now, ticked while the user follows it
   */
  public static DrillCasesDialog newLearning(String family, List<String> suggested) {
    DrillCasesDialog frag = newInstance(family);
    frag.getArguments().putStringArrayList(ARG_SUGGESTED, new ArrayList<String>(suggested));
    return frag;
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    family = getArguments().getString(ARG_FAMILY);
    suggested = getArguments().getStringArrayList(ARG_SUGGESTED);
    cases.clear();
    picked.clear();
    for (String code : CaseSets.ofFamily(family).cases()) {
      cases.add(code);
    }
    if (isLearning()) {
      Set<String> own = Options.INSTANCE.getLearnPick(family);
      picked.addAll(own == null ? suggested : own);
      known.addAll(Options.INSTANCE.getLearnedCases());
    } else {
      Set<String> stored = Options.INSTANCE.getDrillCases(family);
      picked.addAll(stored == null ? cases : stored);
    }
    presets = Options.INSTANCE.getDrillCasePresets(family);
    loadKnown();

    View view = LayoutInflater.from(getActivity()).inflate(R.layout.drill_cases_dialog, null);
    tvCount = view.findViewById(R.id.tvCasesCount);
    tvAll = view.findViewById(R.id.tvCasesAll);
    tvNone = view.findViewById(R.id.tvCasesNone);
    cbShowKnown = view.findViewById(R.id.cbCasesShowKnown);
    TextView title = view.findViewById(R.id.tvCasesTitle);
    if (isLearning()) {
      title.setText(family.startsWith("oll") ? R.string.coach_learn_next
          : R.string.coach_learn_next_pll);
      tvAll.setText(R.string.learn_pick_suggested);
      cbShowKnown.setVisibility(View.VISIBLE);
      cbShowKnown.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton button, boolean checked) {
          refreshShown();
        }
      });
    } else {
      title.setText(family.startsWith("oll") ? R.string.drill_cases_title_oll
          : R.string.drill_cases_title_pll);
    }
    filter = new CaseFilterBar(getActivity(), CaseSets.ofFamily(family),
        new CaseFilterBar.Listener() {
          @Override
          public void onGroupPicked(CaseGroup group) {
            refreshShown();
            refreshCount();
          }
        }).known(known);
    LinearLayout filters = view.findViewById(R.id.llCasesFilters);
    filters.addView(filter.chip(LayoutInflater.from(getActivity()), filters,
        R.layout.case_filter_chip_small));
    refreshShown();
    adapter = new CasesAdapter();
    grid = view.findViewById(R.id.gvCases);
    // A first guess, corrected to whole rows once there is a laid-out cell to measure. Inside a
    // dialog a grid asked to fill what is left of a card that is itself sized to its contents comes
    // out with no height at all, so it can never simply be given the room.
    grid.getLayoutParams().height = (int) (getResources().getDisplayMetrics().heightPixels
        * MAX_HEIGHT / 2);
    grid.setAdapter(adapter);
    grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> parent, View item, int position, long id) {
        toggle(shown.get(position));
      }
    });
    // Holding a case shows it instead of picking it. Tapping has to stay the pick, since picking is
    // what the screen is for and 57 of them is a lot of taps to make twice.
    grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
      @Override
      public boolean onItemLongClick(AdapterView<?> parent, View item, int position, long id) {
        CaseAlgorithmsDialog.newInstance(shown.get(position))
            .show(getParentFragmentManager(), "caseAlgorithms");
        return true;
      }
    });
    tvAll.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        pickAll(true);
      }
    });
    tvNone.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        pickAll(false);
      }
    });
    buPresets = view.findViewById(R.id.buCasePresets);
    buPresets.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        DrillPresetsDialog.show(getActivity(), family, picked, DrillCasesDialog.this);
      }
    });
    buPresets.setVisibility(isLearning() ? View.GONE : View.VISIBLE);
    refreshCount();

    return new AlertDialog.Builder(getActivity(), R.style.NanoTimerDialogTheme)
        .setView(view)
        .setPositiveButton(R.string.close, null)
        .create();
  }

  @Override
  public void onStart() {
    super.onStart();
    grid.getViewTreeObserver().addOnGlobalLayoutListener(
        new ViewTreeObserver.OnGlobalLayoutListener() {
          @Override
          public void onGlobalLayout() {
            fitGrid();
          }
        });
  }

  /**
   * The grid takes whole rows and no more room than the dialog has. Left to a fraction of the
   * screen it cut the last row in half and left a dead band above CLOSE, on a family of 21 as
   * surely as on one of 57.
   */
  private void fitGrid() {
    Dialog dialog = getDialog();
    if (dialog == null || dialog.getWindow() == null || grid.getChildCount() == 0) {
      return;
    }
    int spacing = getResources().getDimensionPixelSize(R.dimen.case_grid_spacing);
    int pitch = grid.getChildAt(0).getHeight() + spacing;
    int columns = grid.getNumColumns();
    if (pitch <= spacing || columns <= 0) {
      return;
    }
    int chrome = dialog.getWindow().getDecorView().getHeight() - grid.getHeight();
    int room = (int) (getResources().getDisplayMetrics().heightPixels * MAX_HEIGHT) - chrome;
    int rows = (shown.size() + columns - 1) / columns;
    int height = Math.min(rows, Math.max(1, room / pitch)) * pitch - spacing;
    if (height != grid.getLayoutParams().height) {
      grid.getLayoutParams().height = height;
      grid.requestLayout();
    }
  }

  @Override
  public void onDismiss(DialogInterface dialog) {
    super.onDismiss(dialog);
    if (isLearning() && picked.isEmpty()) {
      Options.INSTANCE.setLearnPick(family, null); // nothing chosen is no choice: back to the suggestion
    }
    if (getActivity() instanceof Listener) {
      ((Listener) getActivity()).onDrillCasesPicked(family);
    }
  }

  private void toggle(String code) {
    if (!picked.remove(code)) {
      picked.add(code);
    }
    save();
  }

  /** @param all every case, or when choosing what to learn, the suggestion */
  /** With a group picked, only its shown cases are picked or let go; the rest keep their state. */
  private void pickAll(boolean all) {
    List<String> scope = filter.getGroup() == null ? cases : shown;
    picked.removeAll(scope);
    if (all) {
      for (String code : isLearning() ? suggested : cases) {
        if (scope.contains(code)) {
          picked.add(code);
        }
      }
    }
    save();
  }

  /**
   * Everything ticked is stored as no choice at all, so a case added later is drilled too. A pick
   * of what to learn that matches the suggestion is stored the same way, so it keeps following it.
   */
  private void save() {
    if (isLearning()) {
      boolean following = picked.equals(new HashSet<String>(suggested));
      Options.INSTANCE.setLearnPick(family, following ? null : inCaseOrder(picked));
    } else {
      Options.INSTANCE.setDrillCases(family,
          picked.size() == cases.size() ? null : new LinkedHashSet<String>(picked));
    }
    refreshShown(); // a known case shown only for being picked goes once it is not
    refreshCount();
  }

  private boolean isLearning() {
    return suggested != null;
  }

  private Set<String> inCaseOrder(Set<String> codes) {
    Set<String> ordered = new LinkedHashSet<String>();
    for (String code : cases) {
      if (codes.contains(code)) {
        ordered.add(code);
      }
    }
    return ordered;
  }

  /** Known cases count as learned for the drill too: from the solves, or cleared in a drill. */
  private void loadKnown() {
    App.INSTANCE.getService().getCaseHistory(0, new DataCallback<CaseHistory>() {
      @Override
      public void onData(final CaseHistory history) {
        final Activity activity = getActivity();
        if (activity == null) {
          return;
        }
        activity.runOnUiThread(new Runnable() {
          @Override
          public void run() {
            for (CaseKnowledge stepCase : history.getCases()) {
              if (stepCase.getStatus() == CaseKnowledge.Status.KNOWN) {
                known.add(stepCase.getCode());
              }
            }
            if (adapter != null && isAdded()) {
              filter.known(known);
              refreshShown();
            }
          }
        });
      }
    });
  }

  /** The cases on the grid: all of them, but for known ones nobody asked to see. A picked one stays. */
  private void refreshShown() {
    shown.clear();
    boolean showKnown = cbShowKnown == null || cbShowKnown.isChecked();
    for (String code : cases) {
      boolean hidden = isLearning() && !showKnown && known.contains(code)
          && !picked.contains(code);
      if (!hidden && filter.shows(code)) {
        shown.add(code);
      }
    }
    if (adapter != null) {
      adapter.notifyDataSetChanged();
    }
  }

  @Override
  public void onPresetApplied(Set<String> presetCases) {
    picked.clear();
    picked.addAll(presetCases);
    save();
  }

  @Override
  public void onPresetsChanged() {
    presets = Options.INSTANCE.getDrillCasePresets(family);
    refreshCount();
  }

  /**
   * The count with the picked number in the family's own colour, the preset the pick stands at if
   * it stands at one, and the two shortcuts showing which of them it stands at.
   */
  private void refreshCount() {
    DrillCasePreset preset = DrillCasePreset.matching(presets, picked);
    String count = String.valueOf(picked.size());
    String text = preset == null
        ? getString(R.string.drill_cases_count, picked.size(), cases.size())
        : getString(R.string.drill_cases_count_preset, picked.size(), cases.size(),
            preset.getName());
    SpannableString spanned = new SpannableString(text);
    int at = text.indexOf(count);
    if (at >= 0) {
      spanned.setSpan(new ForegroundColorSpan(StepPalette.cfop(getActivity()).colorFor(family)),
          at, at + count.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    tvCount.setText(spanned);
    if (filter.getGroup() != null) {
      int pickedShown = 0;
      for (String code : shown) {
        pickedShown += picked.contains(code) ? 1 : 0;
      }
      markSegment(tvAll, !isLearning() && pickedShown == shown.size());
      markSegment(tvNone, pickedShown == 0);
    } else {
      markSegment(tvAll, isLearning() ? Options.INSTANCE.getLearnPick(family) == null
          : picked.size() == cases.size());
      markSegment(tvNone, picked.isEmpty());
    }
    refreshPresets(preset);
  }

  /**
   * The bookmark fills in the family's colour while the pick stands at a preset, which is the whole
   * of how a preset is shown.
   */
  private void refreshPresets(DrillCasePreset preset) {
    buPresets.setImageResource(preset == null ? R.drawable.ic_preset : R.drawable.ic_preset_on);
    buPresets.setColorFilter(preset != null ? StepPalette.cfop(getActivity()).colorFor(family)
        : ContextCompat.getColor(getActivity(), R.color.secondary_text));
  }

  private void markSegment(TextView segment, boolean on) {
    ViewSegments.style(segment, on);
  }

  private class CasesAdapter extends BaseAdapter {

    @Override
    public int getCount() {
      return shown.size();
    }

    @Override
    public Object getItem(int position) {
      return shown.get(position);
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View recycled, ViewGroup parent) {
      String code = shown.get(position);
      CaseSet set = CaseSets.ofCase(code);
      CaseTile tile = CaseTile.of(recycled, LayoutInflater.from(getActivity()), parent)
          .show(set, code).picked(picked.contains(code));
      if (isLearning() && known.contains(code)) {
        tile.name(getString(R.string.learn_pick_known_case, set.name(code)));
      }
      return tile.view();
    }
  }
}
