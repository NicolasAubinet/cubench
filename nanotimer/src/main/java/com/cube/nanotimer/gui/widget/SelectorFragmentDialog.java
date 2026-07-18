package com.cube.nanotimer.gui.widget;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
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
import com.cube.nanotimer.R;

import java.util.ArrayList;
import java.util.List;

public class SelectorFragmentDialog extends NanoTimerDialogFragment {

  protected static final String ARG_ID = "id";
  protected static final String ARG_TITLE = "title";
  protected static final String ARG_TOUCHOUT = "touchout";
  protected static final String ARG_ITEMS = "items";
  protected static final String ARG_DRAWABLE_IDS = "drawableIds";

  protected SelectionHandler handler;
  protected int id;
  protected List<String> liItems;
  protected List<Integer> liDrawableIds;
  protected ArrayAdapter<String> adapter;

  public static SelectorFragmentDialog newInstance(int id, ArrayList<String> items,
                                                   String title, boolean cancelTouchOutside, SelectionHandler handler) {
    return newInstance(id, items, null, title, cancelTouchOutside, handler);
  }

  public static SelectorFragmentDialog newInstance(int id, ArrayList<String> items, ArrayList<Integer> drawableIds,
                                                   String title, boolean cancelTouchOutside, SelectionHandler handler) {
    SelectorFragmentDialog f = new SelectorFragmentDialog();
    f.handler = handler;
    Bundle bundle = new Bundle();
    bundle.putInt(ARG_ID, id);
    bundle.putBoolean(ARG_TOUCHOUT, cancelTouchOutside);
    bundle.putStringArrayList(ARG_ITEMS, items);
    if (title != null) {
      bundle.putString(ARG_TITLE, title);
    }
    if (drawableIds != null) {
      bundle.putIntegerArrayList(ARG_DRAWABLE_IDS, drawableIds);
    }
    f.setArguments(bundle);
    return f;
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    View v = getActivity().getLayoutInflater().inflate(R.layout.simple_list, null);
    ListView lvItems = (ListView) v.findViewById(R.id.lvItems);

    Bundle args = getArguments();
    id = args.getInt(ARG_ID);
    String title = args.getString(ARG_TITLE);
    boolean cancelOnTouchOutside = args.getBoolean(ARG_TOUCHOUT);
    liItems = args.getStringArrayList(ARG_ITEMS);
    liDrawableIds = args.getIntegerArrayList(ARG_DRAWABLE_IDS);

    adapter = getNewAdapter();
    lvItems.setAdapter(adapter);

    final AlertDialog dialog = new AlertDialog.Builder(getActivity(), R.style.NanoTimerDialogTheme).setView(v).create();
    dialog.setCanceledOnTouchOutside(cancelOnTouchOutside);

    if (title != null) {
      TextView tvTitle = new TextView(getContext());
      tvTitle.setText(title);
      tvTitle.setTextColor(ContextCompat.getColor(getContext(), R.color.white));
      tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
      tvTitle.setTypeface(tvTitle.getTypeface(), Typeface.BOLD);
      tvTitle.setLetterSpacing(0.02f);
      float density = getResources().getDisplayMetrics().density;
      tvTitle.setPadding((int) (16 * density), (int) (16 * density), (int) (16 * density), (int) (8 * density));
      tvTitle.setGravity(Gravity.CENTER_VERTICAL);
      dialog.setCustomTitle(tvTitle);
    }

    lvItems.setOnItemClickListener(new OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        dialog.dismiss();
        handler.itemSelected(id, i);
      }
    });

    return dialog;
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
    handler.itemSelected(id, -1);
  }

  protected ArrayAdapter<String> getNewAdapter() {
    return new CustomAdapter(getActivity(), R.layout.simple_list_item, liItems);
  }

  protected class CustomAdapter extends ArrayAdapter<String> {
    public CustomAdapter(Context context, int id, List<String> list) {
      super(context, id, list);
    }

    public View getView(final int position, View convertView, ViewGroup parent) {
      LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      View view;
      if (liDrawableIds == null) {
        view = inflater.inflate(R.layout.simple_list_item, parent, false);
      } else {
        view = inflater.inflate(R.layout.drawable_simple_list_item, parent, false);
      }

      if (position >= 0 && position < liItems.size()) {
        String item = liItems.get(position);
        if (item != null) {
          if (liDrawableIds != null) {
            ImageView image = (ImageView) view.findViewById(R.id.imgImage);
            image.setImageResource(liDrawableIds.get(position));
          }
          TextView tvName = (TextView) view.findViewById(R.id.tvItem);
          tvName.setText(item);
        }
      }
      return view;
    }
  }

}
