package com.cube.nanotimer.gui.widget;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;

import com.cube.nanotimer.R;

import java.util.ArrayList;
import java.util.List;

/**
 * A picker that stays a list, because the order of its rows is information: cube types come in a
 * fixed order and solve types in the one the user dragged them into, and neither survives being
 * folded into a row of chips. What the list adds over a spinner is per-row detail: a mark for what
 * kind of thing it is, how many solves it holds, and a bar against the one you are already on.
 *
 * <p>An optional footer row (Edit solve types) reports as position {@code names.size()}.
 */
public class SelectorListDialog extends NanoTimerDialogFragment {

  private static final String ARG_ID = "id";
  private static final String ARG_TITLE = "title";
  private static final String ARG_NAMES = "names";
  private static final String ARG_FIGURES = "figures";
  private static final String ARG_ICONS = "icons";
  private static final String ARG_SELECTED = "selected";
  private static final String ARG_FOOTER = "footer";
  private static final String ARG_FOOTER_ICON = "footerIcon";

  private SelectionHandler handler;
  private int id;
  private int selectedIndex;
  private List<String> names;
  private List<String> figures;
  private List<Integer> iconIds;
  private String footerLabel;
  private int footerIcon;

  /**
   * @param figures  one short figure per row (a solve count), "" for none
   * @param iconIds  one drawable per row, 0 for none
   * @param footer     label for a trailing action row, null for none
   * @param footerIcon drawable for that row, 0 for none
   */
  public static SelectorListDialog newInstance(int id, String title, ArrayList<String> names,
                                               ArrayList<String> figures, ArrayList<Integer> iconIds,
                                               int selectedIndex, String footer, int footerIcon,
                                               SelectionHandler handler) {
    SelectorListDialog f = new SelectorListDialog();
    f.handler = handler;
    Bundle bundle = new Bundle();
    bundle.putInt(ARG_ID, id);
    bundle.putString(ARG_TITLE, title);
    bundle.putStringArrayList(ARG_NAMES, names);
    bundle.putStringArrayList(ARG_FIGURES, figures);
    bundle.putIntegerArrayList(ARG_ICONS, iconIds);
    bundle.putInt(ARG_SELECTED, selectedIndex);
    bundle.putString(ARG_FOOTER, footer);
    bundle.putInt(ARG_FOOTER_ICON, footerIcon);
    f.setArguments(bundle);
    return f;
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    View v = getActivity().getLayoutInflater().inflate(R.layout.simple_list, null);
    ListView lvItems = (ListView) v.findViewById(R.id.lvItems);

    Bundle args = getArguments();
    id = args.getInt(ARG_ID);
    names = args.getStringArrayList(ARG_NAMES);
    figures = args.getStringArrayList(ARG_FIGURES);
    iconIds = args.getIntegerArrayList(ARG_ICONS);
    selectedIndex = args.getInt(ARG_SELECTED);
    footerLabel = args.getString(ARG_FOOTER);
    footerIcon = args.getInt(ARG_FOOTER_ICON);

    final List<String> rows = new ArrayList<>(names);
    if (footerLabel != null) {
      rows.add(footerLabel);
    }
    lvItems.setAdapter(new RowAdapter(getActivity(), rows));

    final AlertDialog dialog = new AlertDialog.Builder(getActivity(), R.style.NanoTimerDialogTheme).setView(v).create();
    dialog.setCanceledOnTouchOutside(true);
    dialog.setCustomTitle(buildTitle(args.getString(ARG_TITLE)));

    lvItems.setOnItemClickListener(new OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        dialog.dismiss();
        // Null after the process was restored under the dialog: there is nothing left to tell.
        if (handler != null) {
          handler.itemSelected(id, i);
        }
      }
    });
    // A long list opens on the row you are on rather than at the top.
    if (selectedIndex > 0) {
      lvItems.setSelection(selectedIndex);
    }

    return dialog;
  }

  private TextView buildTitle(String title) {
    TextView tvTitle = new TextView(getContext());
    tvTitle.setText(title);
    tvTitle.setTextColor(ContextCompat.getColor(getContext(), R.color.white));
    tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
    tvTitle.setTypeface(tvTitle.getTypeface(), Typeface.BOLD);
    tvTitle.setLetterSpacing(0.02f);
    float density = getResources().getDisplayMetrics().density;
    tvTitle.setPadding((int) (16 * density), (int) (16 * density), (int) (16 * density), (int) (8 * density));
    tvTitle.setGravity(Gravity.CENTER_VERTICAL);
    return tvTitle;
  }

  @Override
  public void show(FragmentManager manager, String tag) {
    if (manager.findFragmentByTag(tag) == null) {
      super.show(manager, tag);
    }
  }

  @Override
  public void onCancel(DialogInterface dialog) {
    super.onCancel(dialog);
    if (handler != null) {
      handler.itemSelected(id, -1);
    }
  }

  private class RowAdapter extends ArrayAdapter<String> {
    private final LayoutInflater inflater;

    RowAdapter(Context context, List<String> rows) {
      super(context, R.layout.selector_list_item, rows);
      inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      View view = convertView;
      if (view == null) {
        view = inflater.inflate(R.layout.selector_list_item, parent, false);
      }

      boolean isFooter = position >= names.size();
      boolean isSelected = !isFooter && position == selectedIndex;

      view.setBackgroundResource(isSelected ? R.drawable.selector_row_selected : R.drawable.selector_row);
      view.findViewById(R.id.selectionBar).setVisibility(isSelected ? View.VISIBLE : View.INVISIBLE);

      ImageView icon = (ImageView) view.findViewById(R.id.imgIcon);
      Integer iconId = isFooter ? footerIcon : iconIds == null ? null : iconIds.get(position);
      if (iconId != null && iconId != 0) {
        icon.setImageResource(iconId);
        // The trailing action is not one of the things being chosen, so its mark recedes with it.
        icon.setColorFilter(ContextCompat.getColor(getContext(),
          isFooter ? R.color.secondary_text : R.color.lightblue), PorterDuff.Mode.SRC_IN);
        icon.setVisibility(View.VISIBLE);
      } else {
        icon.setVisibility(View.GONE);
      }

      TextView tvName = (TextView) view.findViewById(R.id.tvName);
      tvName.setText(getItem(position));
      tvName.setTextColor(ContextCompat.getColor(getContext(),
        isSelected ? R.color.lightblue : isFooter ? R.color.secondary_text : R.color.white));
      tvName.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);

      TextView tvFigure = (TextView) view.findViewById(R.id.tvFigure);
      String figure = (isFooter || figures == null) ? "" : figures.get(position);
      tvFigure.setText(figure);

      return view;
    }
  }

}
