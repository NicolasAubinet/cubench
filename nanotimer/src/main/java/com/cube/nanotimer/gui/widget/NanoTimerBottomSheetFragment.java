package com.cube.nanotimer.gui.widget;

import android.app.Dialog;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import com.cube.nanotimer.R;
import com.cube.nanotimer.util.helper.Utils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * A sheet that rises from the bottom edge, for detail that has outgrown a dialog: it is dismissed by
 * dragging it back down, which stays within reach however tall it grows, where tapping outside a
 * dialog does not. Carries the same locale handling as {@link NanoTimerDialogFragment}.
 */
public class NanoTimerBottomSheetFragment extends BottomSheetDialogFragment {

  /** Sheets stop short of the top so the screen behind stays visible, and the sheet reads as one. */
  private static final float MAX_HEIGHT_RATIO = 0.8f;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Utils.updateContextWithPrefsLocale(getContext());
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    Utils.updateContextWithPrefsLocale(getContext());
  }

  @Override
  public int getTheme() {
    return R.style.NanoTimerBottomSheetTheme;
  }

  /**
   * Opens at its full height rather than at a peek: this is detail asked for, not a preview to pull
   * up. Skipping the collapsed state also means one drag down dismisses it instead of two.
   */
  protected void setUpSheet(Dialog dialog) {
    View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
    if (sheet == null) {
      return;
    }
    BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
    behavior.setMaxHeight(maxHeightPx());
    behavior.setSkipCollapsed(true);
    behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
  }

  private int maxHeightPx() {
    return (int) (getResources().getDisplayMetrics().heightPixels * MAX_HEIGHT_RATIO);
  }

  @Override
  public void onStart() {
    super.onStart();
    Dialog dialog = getDialog();
    if (dialog instanceof BottomSheetDialog) {
      setUpSheet(dialog);
    }
  }
}
