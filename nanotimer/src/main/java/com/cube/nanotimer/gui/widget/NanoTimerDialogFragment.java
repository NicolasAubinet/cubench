package com.cube.nanotimer.gui.widget;

import android.content.res.Configuration;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import com.cube.nanotimer.util.helper.Utils;

public class NanoTimerDialogFragment extends DialogFragment {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Utils.updateContextWithPrefsLocale(getContext());
  }

  /**
   * The fragment or activity that showed this dialog, as the callback it implements. Looked up
   * rather than passed in the arguments: Android saves those when the app goes to the background,
   * and a host in there is serialized whole, which crashes on its views.
   */
  protected <T> T host(Class<T> type) {
    Fragment parent = getParentFragment();
    return type.isInstance(parent) ? type.cast(parent) : type.cast(requireActivity());
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    Utils.updateContextWithPrefsLocale(getContext());
  }
}
