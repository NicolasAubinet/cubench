package com.cube.nanotimer.gui.widget.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;

import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.gui.widget.LastLayerCaseView;
import com.cube.nanotimer.gui.widget.NanoTimerDialogFragment;
import com.cube.nanotimer.smartcube.step.LastLayerCaseNames;
import com.cube.nanotimer.smartcube.step.LastLayerDiagram;
import com.cube.nanotimer.smartcube.step.LastLayerScrambles;

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
 */
public class DrillCasesDialog extends NanoTimerDialogFragment {

  /** Told when the picker closes, so the screen behind it can say what is now picked. */
  public interface Listener {
    void onDrillCasesPicked(String family);
  }

  private static final String ARG_FAMILY = "family";

  /** Of the screen: how much of it the grid takes, so a long family scrolls in a card. */
  private static final float GRID_HEIGHT = 0.62f;

  private String family;
  private final List<String> cases = new ArrayList<String>();
  private final List<LastLayerDiagram> charts = new ArrayList<LastLayerDiagram>();
  private final Set<String> picked = new LinkedHashSet<String>();

  private TextView tvCount;
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

    View view = LayoutInflater.from(getActivity()).inflate(R.layout.drill_cases_dialog, null);
    tvCount = view.findViewById(R.id.tvCasesCount);
    adapter = new CasesAdapter();
    GridView grid = view.findViewById(R.id.gvCases);
    // Measured off the screen rather than left to the dialog: inside one, a grid asked to fill
    // what is left of a card that is itself sized to its contents comes out with no height at all.
    grid.getLayoutParams().height = (int) (getResources().getDisplayMetrics().heightPixels
        * GRID_HEIGHT);
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
    view.findViewById(R.id.tvCasesAll).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        pickAll(true);
      }
    });
    view.findViewById(R.id.tvCasesNone).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        pickAll(false);
      }
    });
    refreshCount();

    return new AlertDialog.Builder(getActivity(), R.style.NanoTimerDialogTheme)
        .setTitle(family.startsWith("oll") ? R.string.drill_cases_title_oll
            : R.string.drill_cases_title_pll)
        .setView(view)
        .setPositiveButton(R.string.close, null)
        .create();
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

  private void refreshCount() {
    tvCount.setText(getString(R.string.drill_cases_count, picked.size(), cases.size()));
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
      LastLayerCaseView chart = cell.findViewById(R.id.vCaseChart);
      chart.setDiagram(charts.get(position));
      ((TextView) cell.findViewById(R.id.tvCaseName))
          .setText(LastLayerCaseNames.shortName(code));
      TextView shape = cell.findViewById(R.id.tvCaseShape);
      String shapeName = LastLayerCaseNames.shape(code);
      shape.setText(shapeName == null ? "" : shapeName);

      boolean on = picked.contains(code);
      cell.setBackgroundResource(on ? R.drawable.case_cell_picked : R.drawable.case_cell);
      // Dimmed rather than hidden: an unpicked case still has to be findable to be picked again.
      cell.setAlpha(on ? 1f : 0.4f);
      return cell;
    }
  }
}
