package com.cube.nanotimer.gui.widget;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
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
 * folded into a row of chips.
 *
 * <p>Every row carries a colour of its own, which is what keeps a list of twelve from reading as
 * twelve of the same thing: a puzzle's is its own, a solve type's says what kind it is. The colour
 * runs through the row's mark, its tile and, on the row already selected, its wash and its bar.
 *
 * <p>An optional footer row (Edit solve types) reports as position {@code names.size()}.
 */
public class SelectorListDialog extends NanoTimerDialogFragment {

  private static final String ARG_ID = "id";
  private static final String ARG_EYEBROW = "eyebrow";
  private static final String ARG_TITLE = "title";
  private static final String ARG_HEAD_ICON = "headIcon";
  private static final String ARG_HEAD_COLOR = "headColor";
  private static final String ARG_NAMES = "names";
  private static final String ARG_COUNTS = "counts";
  private static final String ARG_ICONS = "icons";
  private static final String ARG_COLORS = "colors";
  private static final String ARG_SELECTED = "selected";
  private static final String ARG_FOOTER = "footer";
  private static final String ARG_FOOTER_ICON = "footerIcon";

  private static final float ROW_RADIUS_DP = 10f;
  private static final float TILE_RADIUS_DP = 10f;
  private static final float BAR_RADIUS_DP = 2f;
  private static final int WASH_ALPHA = 0x2E;
  private static final int WASH_ALPHA_SELECTED = 0x4F;
  private static final int ROW_ALPHA_SELECTED = 0x24;

  private SelectionHandler handler;
  private int id;
  private int selectedIndex;
  private List<String> names;
  private List<String> counts;
  private List<Integer> iconIds;
  private List<Integer> colorIds;
  private String footerLabel;
  private int footerIcon;
  private float density;

  /**
   * @param counts     how many solves are in each row, "" for none
   * @param iconIds    one drawable per row, 0 for none
   * @param colorIds   one colour resource per row
   * @param footer     label for a trailing action row, null for none
   * @param footerIcon drawable for that row, 0 for none
   */
  public static SelectorListDialog newInstance(int id, ArrayList<String> names,
                                               ArrayList<String> counts, ArrayList<Integer> iconIds,
                                               ArrayList<Integer> colorIds, int selectedIndex,
                                               String footer, int footerIcon, SelectionHandler handler) {
    SelectorListDialog f = new SelectorListDialog();
    f.handler = handler;
    Bundle bundle = new Bundle();
    bundle.putInt(ARG_ID, id);
    bundle.putStringArrayList(ARG_NAMES, names);
    bundle.putStringArrayList(ARG_COUNTS, counts);
    bundle.putIntegerArrayList(ARG_ICONS, iconIds);
    bundle.putIntegerArrayList(ARG_COLORS, colorIds);
    bundle.putInt(ARG_SELECTED, selectedIndex);
    bundle.putString(ARG_FOOTER, footer);
    bundle.putInt(ARG_FOOTER_ICON, footerIcon);
    f.setArguments(bundle);
    return f;
  }

  /**
   * What the head of the picker says: what is being chosen, over the puzzle it is being chosen on,
   * wearing that puzzle's mark and colour. Naming the context rather than the current row keeps the
   * header from repeating a line the list is about to show anyway.
   */
  public SelectorListDialog setHeader(String eyebrow, String title, int iconRes, int colorRes) {
    Bundle args = getArguments();
    args.putString(ARG_EYEBROW, eyebrow);
    args.putString(ARG_TITLE, title);
    args.putInt(ARG_HEAD_ICON, iconRes);
    args.putInt(ARG_HEAD_COLOR, colorRes);
    return this;
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    density = getResources().getDisplayMetrics().density;

    View v = getActivity().getLayoutInflater().inflate(R.layout.simple_list, null);
    ListView lvItems = (ListView) v.findViewById(R.id.lvItems);

    Bundle args = getArguments();
    id = args.getInt(ARG_ID);
    names = args.getStringArrayList(ARG_NAMES);
    counts = args.getStringArrayList(ARG_COUNTS);
    iconIds = args.getIntegerArrayList(ARG_ICONS);
    colorIds = args.getIntegerArrayList(ARG_COLORS);
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
    dialog.setCustomTitle(buildHeader(args));

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

  private View buildHeader(Bundle args) {
    View header = getActivity().getLayoutInflater().inflate(R.layout.selector_list_header, null);
    int colorRes = args.getInt(ARG_HEAD_COLOR);
    int color = ContextCompat.getColor(getContext(), colorRes == 0 ? R.color.lightblue : colorRes);

    TextView tvEyebrow = (TextView) header.findViewById(R.id.tvHeaderEyebrow);
    tvEyebrow.setText(args.getString(ARG_EYEBROW));
    tvEyebrow.setTextColor(color);
    ((TextView) header.findViewById(R.id.tvHeaderTitle)).setText(args.getString(ARG_TITLE));

    int iconRes = args.getInt(ARG_HEAD_ICON);
    if (iconRes != 0) {
      ImageView icon = (ImageView) header.findViewById(R.id.imgHeaderIcon);
      icon.setImageResource(iconRes);
      icon.setColorFilter(color, PorterDuff.Mode.SRC_IN);
      header.findViewById(R.id.headerTile).setBackground(tile(color, true));
    } else {
      header.findViewById(R.id.headerTile).setVisibility(View.GONE);
    }
    return header;
  }

  private int colorAt(int position) {
    if (colorIds == null || position < 0 || position >= colorIds.size()) {
      return ContextCompat.getColor(getContext(), R.color.lightblue);
    }
    return ContextCompat.getColor(getContext(), colorIds.get(position));
  }

  private static int withAlpha(int color, int alpha) {
    return (color & 0x00FFFFFF) | (alpha << 24);
  }

  private GradientDrawable rounded(int color, float radiusDp) {
    GradientDrawable shape = new GradientDrawable();
    shape.setCornerRadius(radiusDp * density);
    shape.setColor(color);
    return shape;
  }

  private Drawable tile(int color, boolean selected) {
    return rounded(withAlpha(color, selected ? WASH_ALPHA_SELECTED : WASH_ALPHA), TILE_RADIUS_DP);
  }

  /** A rounded ripple in the row's own colour, over a wash of it when the row is the selected one. */
  private Drawable rowBackground(int color, boolean selected) {
    Drawable content = selected ? rounded(withAlpha(color, ROW_ALPHA_SELECTED), ROW_RADIUS_DP) : null;
    return new RippleDrawable(ColorStateList.valueOf(color), content,
      rounded(Color.WHITE, ROW_RADIUS_DP));
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
      int color = isFooter
        ? ContextCompat.getColor(getContext(), R.color.secondary_text) : colorAt(position);

      view.setBackground(rowBackground(color, isSelected));

      View bar = view.findViewById(R.id.selectionBar);
      bar.setBackground(rounded(color, BAR_RADIUS_DP));
      bar.setVisibility(isSelected ? View.VISIBLE : View.INVISIBLE);

      ImageView icon = (ImageView) view.findViewById(R.id.imgIcon);
      View tile = view.findViewById(R.id.iconTile);
      // Boxed on both branches: mixing int and Integer here would unbox the null one instead.
      Integer iconId = isFooter ? Integer.valueOf(footerIcon)
        : (iconIds == null ? null : iconIds.get(position));
      if (iconId != null && iconId != 0) {
        icon.setImageResource(iconId);
        icon.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        // The trailing action is not one of the things being chosen, so it gets no tile.
        tile.setBackground(isFooter ? null : tile(color, isSelected));
        tile.setVisibility(View.VISIBLE);
      } else {
        tile.setVisibility(View.GONE);
      }

      TextView tvName = (TextView) view.findViewById(R.id.tvName);
      tvName.setText(getItem(position));
      tvName.setTextColor(isSelected || isFooter
        ? color : ContextCompat.getColor(getContext(), R.color.white));
      tvName.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);

      TextView tvCount = (TextView) view.findViewById(R.id.tvCount);
      String count = (isFooter || counts == null) ? "" : counts.get(position);
      tvCount.setText(count);
      tvCount.setVisibility(count.isEmpty() ? View.GONE : View.VISIBLE);

      return view;
    }
  }

}
