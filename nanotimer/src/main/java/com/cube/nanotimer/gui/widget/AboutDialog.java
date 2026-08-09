package com.cube.nanotimer.gui.widget;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import androidx.fragment.app.FragmentManager;
import android.view.View;
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
    ((TextView) v.findViewById(R.id.tvAppVersion)).setText("v" + Utils.getAppVersion(getActivity()));

    final AlertDialog dialog = new AlertDialog.Builder(getActivity(), R.style.NanoTimerDialogTheme).setView(v).create();
    dialog.setCanceledOnTouchOutside(true);

    return dialog;
  }

  @Override
  public void show(FragmentManager manager, String tag) {
    if (manager.findFragmentByTag(tag) == null) {
      super.show(manager, tag);
    }
  }

}
