package com.cube.nanotimer.gui.widget;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.R;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * Every solution of a cross drill rep's shortest length, in the pager's order and grouped by the
 * face their first move turns, with a button per face to jump to its group. Picking one closes the
 * sheet and hands its index back; the rep's cube is left to the caller.
 *
 * <p>Asked for because stepping a pager fifty times to find the solutions that start with F is the
 * only way to look for one, and that is how solutions get compared.
 */
public final class CrossSolutionsSheet {

  /** What the user picked. */
  public interface Listener {
    void onPicked(int index);
  }

  /** The order the groups and the face buttons come in, which the solutions are sorted into. */
  public static final String FACE_ORDER = "UDFBRL";

  private static final float LIST_SHARE = 0.62f;

  private CrossSolutionsSheet() {
  }

  /**
   * @param rows each solution as it is read, already sorted into {@link #FACE_ORDER} groups
   * @param faces the face of each solution's first move
   * @param length how many moves each solution takes
   * @param current the solution on screen, marked in the list and scrolled to
   */
  public static void show(Context context, Typeface mono, List<CharSequence> rows,
      List<Character> faces, int length, int current, final Listener listener) {
    final BottomSheetDialog dialog =
        new BottomSheetDialog(context, R.style.NanoTimerBottomSheetTheme);
    View root = LayoutInflater.from(context).inflate(R.layout.cross_solutions_sheet, null);
    ((TextView) root.findViewById(R.id.tvCrossSolutionsCount)).setText(
        context.getString(R.string.drill_cross_all_count, rows.size(), length));

    // The list mixes group headings and solutions; each entry is one or the other.
    final List<Object> entries = new ArrayList<Object>();
    final List<Integer> groupStarts = new ArrayList<Integer>();
    final List<Character> groupFaces = new ArrayList<Character>();
    int currentEntry = 0;
    for (int i = 0; i < rows.size(); i++) {
      if (i == 0 || !faces.get(i).equals(faces.get(i - 1))) {
        int count = 0;
        for (int j = i; j < rows.size() && faces.get(j).equals(faces.get(i)); j++) {
          count++;
        }
        groupStarts.add(entries.size());
        groupFaces.add(faces.get(i));
        entries.add(new Group(faces.get(i), count));
      }
      if (i == current) {
        currentEntry = entries.size();
      }
      entries.add(i);
    }

    final ListView list = root.findViewById(R.id.lvCrossSolutions);
    list.getLayoutParams().height = Math.round(
        context.getResources().getDisplayMetrics().heightPixels * LIST_SHARE);
    list.setAdapter(new Adapter(context, mono, entries, rows, current));
    list.setOnItemClickListener((parent, view, position, id) -> {
      Object entry = entries.get(position);
      if (entry instanceof Integer) {
        dialog.dismiss();
        listener.onPicked((Integer) entry);
      }
    });
    // The heading of the solution on screen at the top, so its neighbours are in view too.
    int currentGroup = 0;
    for (int g = 0; g < groupStarts.size(); g++) {
      if (groupStarts.get(g) <= currentEntry) {
        currentGroup = g;
      }
    }
    list.setSelection(current == 0 ? 0 : groupStarts.get(currentGroup));

    LinearLayout buttons = root.findViewById(R.id.llCrossSolutionsFaces);
    float density = context.getResources().getDisplayMetrics().density;
    final List<TextView> chips = new ArrayList<TextView>();
    for (int g = 0; g < groupFaces.size(); g++) {
      final int start = groupStarts.get(g);
      TextView chip = new TextView(context);
      chip.setText(String.valueOf(groupFaces.get(g)));
      chip.setTypeface(mono);
      chip.setTextSize(15);
      chip.setGravity(Gravity.CENTER);
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
          Math.round(36 * density), Math.round(34 * density));
      params.leftMargin = Math.round(6 * density);
      chip.setLayoutParams(params);
      chips.add(chip);
      final int index = g;
      chip.setOnClickListener(v -> {
        list.setSelection(start);
        light(context, chips, index);
      });
      buttons.addView(chip);
    }
    light(context, chips, currentGroup);

    dialog.setContentView(root);
    dialog.getBehavior().setSkipCollapsed(true);
    dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
    dialog.show();
  }

  /** The face button of the group in view in the accent, the others quiet. */
  private static void light(Context context, List<TextView> chips, int lit) {
    float density = context.getResources().getDisplayMetrics().density;
    for (int i = 0; i < chips.size(); i++) {
      boolean on = i == lit;
      GradientDrawable shape = new GradientDrawable();
      shape.setCornerRadius(10 * density);
      shape.setColor(
          ContextCompat.getColor(context, on ? R.color.lightblue : R.color.white_wash_10));
      chips.get(i).setBackground(shape);
      chips.get(i).setTextColor(
          ContextCompat.getColor(context, on ? R.color.on_accent : R.color.gray200));
    }
  }

  private static final class Group {
    final char face;
    final int count;

    Group(char face, int count) {
      this.face = face;
      this.count = count;
    }
  }

  private static final class Adapter extends BaseAdapter {

    private final Context context;
    private final Typeface mono;
    private final List<Object> entries;
    private final List<CharSequence> rows;
    private final int current;

    Adapter(Context context, Typeface mono, List<Object> entries, List<CharSequence> rows,
        int current) {
      this.context = context;
      this.mono = mono;
      this.entries = entries;
      this.rows = rows;
      this.current = current;
    }

    @Override
    public int getCount() {
      return entries.size();
    }

    @Override
    public Object getItem(int position) {
      return entries.get(position);
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public int getViewTypeCount() {
      return 2;
    }

    @Override
    public int getItemViewType(int position) {
      return entries.get(position) instanceof Group ? 0 : 1;
    }

    @Override
    public boolean isEnabled(int position) {
      return getItemViewType(position) == 1;
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
      Object entry = entries.get(position);
      LayoutInflater inflater = LayoutInflater.from(context);
      if (entry instanceof Group) {
        if (view == null) {
          view = inflater.inflate(R.layout.cross_solutions_header, parent, false);
          ((TextView) view.findViewById(R.id.tvCrossGroupFace)).setTypeface(mono, Typeface.BOLD);
        }
        Group group = (Group) entry;
        ((TextView) view.findViewById(R.id.tvCrossGroupFace)).setText(String.valueOf(group.face));
        ((TextView) view.findViewById(R.id.tvCrossGroupCount)).setText(context.getResources()
            .getQuantityString(R.plurals.drill_cross_group_solutions, group.count, group.count));
        // The first group needs no rule above it: the face buttons already end that block.
        ((ViewGroup) view).getChildAt(0).setVisibility(position == 0 ? View.GONE : View.VISIBLE);
        return view;
      }
      if (view == null) {
        view = inflater.inflate(R.layout.cross_solutions_row, parent, false);
        ((TextView) view.findViewById(R.id.tvCrossSolutionMoves)).setTypeface(mono);
      }
      int index = (Integer) entry;
      boolean on = index == current;
      ((TextView) view.findViewById(R.id.tvCrossSolutionIndex)).setText(String.valueOf(index + 1));
      TextView moves = view.findViewById(R.id.tvCrossSolutionMoves);
      moves.setText(rows.get(index));
      moves.setTextColor(ContextCompat.getColor(context, on ? R.color.lightblue : R.color.gray200));
      view.setBackgroundResource(on ? R.drawable.cross_solution_current : 0);
      return view;
    }
  }
}
