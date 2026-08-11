package com.cube.nanotimer.gui.widget.dialog;

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
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.gui.widget.LastLayerCaseView;
import com.cube.nanotimer.gui.widget.NanoTimerDialogFragment;
import com.cube.nanotimer.smartcube.step.LastLayerCaseNames;
import com.cube.nanotimer.smartcube.step.LastLayerDiagram;
import com.cube.nanotimer.smartcube.step.LastLayerScrambles;
import com.cube.nanotimer.util.DrillCasePreset;
import com.cube.nanotimer.util.helper.GUIUtils;
import com.cube.nanotimer.util.view.ViewSegments;

import java.util.ArrayList;
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

  /** Of the screen: how tall the picker may grow, leaving the dialog theme its own margins. */
  private static final float MAX_HEIGHT = 0.92f;

  private String family;
  private final List<String> cases = new ArrayList<String>();
  private final List<LastLayerDiagram> charts = new ArrayList<LastLayerDiagram>();
  private final Set<String> picked = new LinkedHashSet<String>();
  private List<DrillCasePreset> presets;

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

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    family = getArguments().getString(ARG_FAMILY);
    cases.clear();
    charts.clear();
    picked.clear();
    for (String code : LastLayerScrambles.cases()) {
      if (code.startsWith(family)) {
        cases.add(code);
        charts.add(LastLayerDiagram.forCase(code));
      }
    }
    Set<String> stored = Options.INSTANCE.getDrillCases(family);
    picked.addAll(stored == null ? cases : stored);
    presets = Options.INSTANCE.getDrillCasePresets(family);

    View view = LayoutInflater.from(getActivity()).inflate(R.layout.drill_cases_dialog, null);
    tvCount = view.findViewById(R.id.tvCasesCount);
    tvAll = view.findViewById(R.id.tvCasesAll);
    tvNone = view.findViewById(R.id.tvCasesNone);
    ((TextView) view.findViewById(R.id.tvCasesTitle)).setText(family.startsWith("oll")
        ? R.string.drill_cases_title_oll : R.string.drill_cases_title_pll);
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
        toggle(cases.get(position));
      }
    });
    // Holding a case shows it instead of picking it. Tapping has to stay the pick, since picking is
    // what the screen is for and 57 of them is a lot of taps to make twice.
    grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
      @Override
      public boolean onItemLongClick(AdapterView<?> parent, View item, int position, long id) {
        CaseAlgorithmsDialog.newInstance(cases.get(position))
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
    int rows = (cases.size() + columns - 1) / columns;
    int height = Math.min(rows, Math.max(1, room / pitch)) * pitch - spacing;
    if (height != grid.getLayoutParams().height) {
      grid.getLayoutParams().height = height;
      grid.requestLayout();
    }
  }

  @Override
  public void onDismiss(DialogInterface dialog) {
    super.onDismiss(dialog);
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

  private void pickAll(boolean all) {
    picked.clear();
    if (all) {
      picked.addAll(cases);
    }
    save();
  }

  /** Everything ticked is stored as no choice at all, so a case added later is drilled too. */
  private void save() {
    Options.INSTANCE.setDrillCases(family,
        picked.size() == cases.size() ? null : new LinkedHashSet<String>(picked));
    adapter.notifyDataSetChanged();
    refreshCount();
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
      spanned.setSpan(new ForegroundColorSpan(ContextCompat.getColor(getActivity(),
              family.startsWith("oll") ? R.color.step_oll : R.color.step_pll)),
          at, at + count.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    tvCount.setText(spanned);
    markSegment(tvAll, picked.size() == cases.size());
    markSegment(tvNone, picked.isEmpty());
    refreshPresets(preset);
  }

  /**
   * The bookmark fills in the family's colour while the pick stands at a preset, which is the whole
   * of how a preset is shown.
   */
  private void refreshPresets(DrillCasePreset preset) {
    buPresets.setImageResource(preset == null ? R.drawable.ic_preset : R.drawable.ic_preset_on);
    buPresets.setColorFilter(ContextCompat.getColor(getActivity(), preset != null
        ? (family.startsWith("oll") ? R.color.step_oll : R.color.step_pll)
        : R.color.secondary_text));
  }

  private void markSegment(TextView segment, boolean on) {
    ViewSegments.style(segment, on);
  }

  private class CasesAdapter extends BaseAdapter {

    @Override
    public int getCount() {
      return cases.size();
    }

    @Override
    public Object getItem(int position) {
      return cases.get(position);
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View recycled, ViewGroup parent) {
      View cell = recycled;
      if (cell == null) {
        cell = LayoutInflater.from(getActivity())
            .inflate(R.layout.drill_cases_cell, parent, false);
      }
      String code = cases.get(position);
      boolean on = picked.contains(code);

      LastLayerCaseView chart = cell.findViewById(R.id.vCaseChart);
      chart.setDiagram(charts.get(position));
      // Turned down rather than hidden: an unpicked case still has to be findable to be picked
      // again. The chart dims itself, so the name and the edge keep the colours they were given.
      chart.setDimmed(!on);

      TextView name = cell.findViewById(R.id.tvCaseName);
      name.setText(LastLayerCaseNames.shortName(code));
      name.setTextColor(ContextCompat.getColor(getActivity(),
          on ? R.color.case_name : R.color.case_name_off));

      TextView shape = cell.findViewById(R.id.tvCaseShape);
      String shapeName = LastLayerCaseNames.shape(code);
      shape.setText(shapeName == null ? "" : shapeName);
      // A PLL has no shape, and an empty line still took its row on every one of the 21.
      shape.setVisibility(shapeName == null ? View.GONE : View.VISIBLE);
      shape.setTextColor(ContextCompat.getColor(getActivity(),
          on ? R.color.case_shape : R.color.case_shape_off));
      return cell;
    }
  }
}
