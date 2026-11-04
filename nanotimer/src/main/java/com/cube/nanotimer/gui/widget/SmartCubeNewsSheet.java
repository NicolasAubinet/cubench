package com.cube.nanotimer.gui.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import com.cube.nanotimer.R;
import com.cube.nanotimer.cube.SmartCubeGate;
import com.cube.nanotimer.cube.SmartCubeManager;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.view.EnterAnimation;
import com.cube.nanotimer.util.view.SmartCubeRadarView;
import java.util.List;

/**
 * What the smart cube brings, shown once to a user who updates into it, in place of that update's
 * release notes.
 */
public class SmartCubeNewsSheet extends NanoTimerBottomSheetFragment {

  private static final String NEWS_SEEN_KEY = "smart_cube_news_seen";

  // The rows arrive once the sheet has risen, not behind it.
  private static final long ENTER_LEAD_MS = 220;

  /**
   * Shows the sheet if this launch calls for it.
   *
   * @param firstLaunch whether this is the app's first start since it was installed
   * @return whether it was shown, in which case the release notes are not
   */
  public static boolean showOnLaunch(Context context, boolean firstLaunch) {
    if (!(context instanceof FragmentActivity) || !SmartCubeGate.ENABLED) {
      return false;
    }
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    if (prefs.getBoolean(NEWS_SEEN_KEY, false)) {
      return false;
    }
    prefs.edit().putBoolean(NEWS_SEEN_KEY, true).apply();
    if (firstLaunch) {
      return false; // a new user meets the smart cube on its own sheet: there is no "news" to them
    }
    show((FragmentActivity) context);
    return true;
  }

  public static void show(FragmentActivity activity) {
    DialogUtils.showFragment(activity, new SmartCubeNewsSheet());
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
    View v = inflater.inflate(R.layout.smart_cube_news_sheet, container, false);

    ((TextView) v.findViewById(R.id.tvNewsPill)).setText(R.string.news_pill);
    ((TextView) v.findViewById(R.id.tvNewsTitle)).setText(R.string.news_title);
    ((TextView) v.findViewById(R.id.tvNewsBody)).setText(R.string.news_body);

    List<View> rows = SmartCubeFeatures.fill(v, inflater);

    v.findViewById(R.id.tvNewsAllNotes).setOnClickListener(view -> {
      dismiss();
      ReleaseNotes.showReleaseNotesDialog(requireActivity());
    });
    Button later = v.findViewById(R.id.buNewsLater);
    Button connect = v.findViewById(R.id.buNewsConnect);
    later.setOnClickListener(view -> dismiss());
    // A cube already linked needs no connecting.
    if (SmartCubeManager.INSTANCE.isConnected()) {
      connect.setVisibility(View.GONE);
      later.setText(R.string.close);
    } else {
      connect.setOnClickListener(view -> {
        dismiss();
        DialogUtils.showFragment(requireActivity(), new SmartCubeConnectDialog());
      });
    }

    ((SmartCubeRadarView) v.findViewById(R.id.smartCubeHeroRadar)).showSearching();
    EnterAnimation.stagger(ENTER_LEAD_MS, rows.toArray(new View[0]));
    return v;
  }
}
