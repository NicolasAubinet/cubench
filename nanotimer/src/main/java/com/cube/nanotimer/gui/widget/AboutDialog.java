package com.cube.nanotimer.gui.widget;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import androidx.fragment.app.FragmentManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;
import com.cube.nanotimer.R;
import com.cube.nanotimer.util.helper.Utils;

public class AboutDialog extends NanoTimerDialogFragment {

  public static AboutDialog newInstance() {
    return new AboutDialog();
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    View v = getActivity().getLayoutInflater().inflate(R.layout.about_dialog, null);

    TextView tvAppName = (TextView) v.findViewById(R.id.tvAppName);
    tvAppName.setText(tvAppName.getText().toString() + " v" + Utils.getAppVersion(getActivity()));

    final AlertDialog dialog = new AlertDialog.Builder(getActivity(), R.style.NanoTimerDialogTheme).setView(v).create();
    dialog.setCanceledOnTouchOutside(true);
    dialog.setCustomTitle(getTitleView());

    return dialog;
  }

  @Override
  public void show(FragmentManager manager, String tag) {
    if (manager.findFragmentByTag(tag) == null) {
      super.show(manager, tag);
    }
  }

  private View getTitleView() {
    LinearLayout view = new LinearLayout(getActivity());
    view.setOrientation(LinearLayout.VERTICAL);

    LinearLayout titleLayout = new LinearLayout(getActivity());
    titleLayout.setOrientation(LinearLayout.HORIZONTAL);
    titleLayout.setPadding(8, 8, 8, 8);
    titleLayout.setGravity(Gravity.CENTER_VERTICAL);
    titleLayout.setBackgroundColor(getResources().getColor(R.color.graybg));

    ImageView img = new ImageView(getActivity());
    img.setImageResource(R.drawable.icon);

    TextView tvTitle = new TextView(getActivity());
    tvTitle.setPadding(30, 0, 0, 0);
    tvTitle.setText(R.string.app_name);
    tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 25);

    titleLayout.addView(img);
    titleLayout.addView(tvTitle);

    LinearLayout separator = new LinearLayout(getActivity());
    separator.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 3));
    separator.setBackgroundColor(getResources().getColor(R.color.iceblue));

    view.addView(titleLayout);
    view.addView(separator);

    return view;
  }

}
