package com.cube.nanotimer.gui.widget.cases;

import android.content.Context;
import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;
import com.cube.nanotimer.cases.CaseGroup;
import com.cube.nanotimer.cases.CaseSet;
import com.cube.nanotimer.cases.CaseSets;
import com.cube.nanotimer.gui.widget.LastLayerCaseView;
import com.cube.nanotimer.gui.widget.PairCaseView;
import com.cube.nanotimer.smartcube.step.CaseAlgorithms;
import com.cube.nanotimer.smartcube.step.LastLayerDiagram;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A set's group filter: one chip ("Shape ▾" for OLL, "Group ▾" for the others) that opens every
 * group at once, each with its picture and how many of its cases are known. Analysis and the drill
 * picker carry it; the library will. It filters, it never sections: a list keeps its own order.
 *
 * <p>A list rather than a row of chips: fourteen OLL shapes do not fit a scrolling row, and the
 * picker orders them biggest first with the count beside each, which only a list can show.
 */
public final class CaseFilterBar {

  /** Told the group the reader narrowed to, or null for the whole set. */
  public interface Listener {
    void onGroupPicked(CaseGroup group);
  }

  /** Two rows of seven fit a landscape screen; seven across a portrait one leave no room to read. */
  private static final int COLUMNS = 4;
  private static final int COLUMNS_WIDE = 7;
  /** A group the other filters leave empty: still pickable, but read as holding nothing. */
  private static final float EMPTY_ALPHA = 0.4f;

  private final Context context;
  private final CaseSet set;
  private final Listener listener;
  private final Set<String> known = new HashSet<String>();
  /** The cases the screen's other filters let through, or null. */
  private Set<String> matching;
  private List<CaseGroup> offered;
  private CaseGroup group;
  private TextView chip;

  public CaseFilterBar(Context context, CaseSet set, Listener listener) {
    this.context = context;
    this.set = set;
    this.listener = listener;
    offer(null);
  }

  /**
   * Offers only the groups holding one of these cases, for a list that shows a part of the set.
   * Null offers every group. A picked group that is no longer offered is let go.
   */
  public CaseFilterBar offer(Collection<String> cases) {
    List<CaseGroup> groups = new ArrayList<CaseGroup>();
    for (CaseGroup candidate : set.groups()) {
      if (cases == null || !Collections.disjoint(candidate.getCases(), cases)) {
        groups.add(candidate);
      }
    }
    if (!set.sectioned()) { // a sectioned set's groups keep the order its sections are drawn in
      Collections.sort(groups, new Comparator<CaseGroup>() {
        @Override
        public int compare(CaseGroup a, CaseGroup b) {
          int size = b.getCases().size() - a.getCases().size();
          return size != 0 ? size : a.getName(context).compareTo(b.getName(context));
        }
      });
    }
    offered = groups;
    if (group != null && !offered.contains(group)) {
      group = null;
    }
    return this;
  }

  /** The cases counted as known in the picker. */
  public CaseFilterBar known(Collection<String> knownCases) {
    known.clear();
    known.addAll(knownCases);
    return this;
  }

  /**
   * The cases the screen's other filters let through: "All" carries their count and a group holding
   * none of them is dimmed. Null for neither.
   */
  public CaseFilterBar matching(Collection<String> cases) {
    matching = cases == null ? null : new HashSet<String>(cases);
    return this;
  }

  public boolean hasGroups() {
    return !offered.isEmpty();
  }

  /** The group narrowed to, or null for the whole set. */
  public CaseGroup getGroup() {
    return group;
  }

  public CaseFilterBar setGroup(CaseGroup picked) {
    group = picked;
    paintChip();
    return this;
  }

  /** Whether a case passes the filter. */
  public boolean shows(String caseCode) {
    return group == null || group.contains(caseCode);
  }

  /** The chip, inflated into a row the caller lays out. */
  public TextView chip(LayoutInflater inflater, ViewGroup row) {
    return chip(inflater, row, R.layout.case_filter_chip);
  }

  /** The chip in a layout of its own size, {@code case_filter_chip_small} inside a dialog. */
  public TextView chip(LayoutInflater inflater, ViewGroup row, int layoutResId) {
    chip = (TextView) inflater.inflate(layoutResId, row, false);
    chip.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        pick();
      }
    });
    paintChip();
    return chip;
  }

  private void paintChip() {
    if (chip == null) {
      return;
    }
    chip.setText(context.getString(R.string.analysis_window_chip,
        group == null ? context.getString(isShapes() ? R.string.analysis_shape_title
            : R.string.case_filter_group) : group.getName(context)));
    chip.setSelected(group != null);
  }

  private boolean isShapes() {
    return set == CaseSets.OLL;
  }

  private void pick() {
    LayoutInflater inflater = LayoutInflater.from(context);
    View body = inflater.inflate(R.layout.analysis_shape_picker, null);
    LinearLayout grid = body.findViewById(R.id.llShapeGrid);
    final AlertDialog dialog = new AlertDialog.Builder(context, R.style.NanoTimerDialogTheme)
        .setTitle(isShapes() ? R.string.analysis_shape_title : R.string.case_filter_group)
        .setView(body)
        .create();

    int complete = 0;
    for (CaseGroup each : set.groups()) {
      complete += known.containsAll(each.getCases()) ? 1 : 0;
    }
    TextView groupsKnown = body.findViewById(R.id.tvShapeGroupsKnown);
    groupsKnown.setText(context.getString(R.string.analysis_shape_groups_known, complete,
        set.groups().size()));
    // An F2L pair never gets a standing, so its set says nothing rather than "0 known".
    final boolean standings = set.picture() != CaseSet.Picture.PAIR;
    groupsKnown.setVisibility(standings ? View.VISIBLE : View.GONE);
    TextView all = body.findViewById(R.id.tvShapeAll);
    String allLabel = context.getString(isShapes() ? R.string.analysis_shape_all
        : R.string.case_filter_all_groups);
    all.setText(matching == null ? allLabel
        : context.getString(R.string.known_algorithms_status_count, allLabel, matching.size()));
    all.setSelected(group == null);
    all.setOnClickListener(picking(dialog, null));
    int columns = context.getResources().getConfiguration().orientation
        == Configuration.ORIENTATION_LANDSCAPE ? COLUMNS_WIDE : COLUMNS;
    LinearLayout row = null;
    for (int i = 0; i < offered.size(); i++) {
      if (i % columns == 0) {
        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        grid.addView(row);
      }
      CaseGroup each = offered.get(i);
      View tile = inflater.inflate(R.layout.analysis_shape_tile, row, false);
      String first = each.getCases().get(0);
      if (set.picture() == CaseSet.Picture.PAIR) {
        tile.findViewById(R.id.vShapeChart).setVisibility(View.GONE);
        PairCaseView pair = tile.findViewById(R.id.vShapePair);
        pair.setVisibility(View.VISIBLE);
        pair.setPicture(CaseAlgorithms.pairPicture(first));
      } else {
        ((LastLayerCaseView) tile.findViewById(R.id.vShapeChart))
            .setDiagram(LastLayerDiagram.forCase(first));
      }
      int here = 0;
      if (matching != null) {
        for (String code : each.getCases()) {
          here += matching.contains(code) ? 1 : 0;
        }
        tile.setAlpha(here == 0 ? EMPTY_ALPHA : 1f);
      }
      ((TextView) tile.findViewById(R.id.tvShapeName)).setText(each.getName(context));
      int knownHere = 0;
      for (String code : each.getCases()) {
        knownHere += known.contains(code) ? 1 : 0;
      }
      boolean whole = knownHere == each.getCases().size();
      TextView count = tile.findViewById(R.id.tvShapeCount);
      count.setText(context.getString(R.string.analysis_shape_known, knownHere,
          each.getCases().size()));
      count.setVisibility(standings ? View.VISIBLE : View.GONE);
      if (whole) {
        count.setTextColor(ContextCompat.getColor(context, R.color.lightblue));
      }
      tile.setSelected(each == group);
      tile.setOnClickListener(picking(dialog, each));
      row.addView(tile);
    }
    // Empty cells keep the last row's tiles the width of the others.
    for (int left = (columns - offered.size() % columns) % columns; left > 0; left--) {
      View spacer = inflater.inflate(R.layout.analysis_shape_tile, row, false);
      spacer.setVisibility(View.INVISIBLE);
      row.addView(spacer);
    }
    dialog.show();
  }

  private View.OnClickListener picking(final AlertDialog dialog, final CaseGroup picked) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        dialog.dismiss();
        setGroup(picked);
        listener.onGroupPicked(picked);
      }
    };
  }
}
