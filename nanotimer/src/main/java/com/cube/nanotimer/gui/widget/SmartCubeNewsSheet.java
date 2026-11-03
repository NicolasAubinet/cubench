package com.cube.nanotimer.gui.widget;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import com.cube.nanotimer.R;
import com.cube.nanotimer.cube.SmartCubeGate;
import com.cube.nanotimer.cube.SmartCubeManager;
import com.cube.nanotimer.util.coach.CoachBetaInvite;
import com.cube.nanotimer.util.coach.CoachBetaLook;
import com.cube.nanotimer.util.helper.DialogUtils;
import com.cube.nanotimer.util.view.EnterAnimation;
import com.cube.nanotimer.util.view.SmartCubeRadarView;
import java.util.ArrayList;
import java.util.List;

/**
 * What the smart cube brings, shown once to a user who updates into it, in place of that update's
 * release notes. In a coach beta build ({@link CoachBetaInvite}) the same sheet leads with the
 * coach's free offer instead, opens the first time the app starts, and can be opened again from the
 * smart-cube sheet.
 */
public class SmartCubeNewsSheet extends NanoTimerBottomSheetFragment {

  private static final String ARG_BETA = "beta";
  private static final String NEWS_SEEN_KEY = "smart_cube_news_seen";
  private static final String BETA_SEEN_KEY = "coach_beta_invite_seen";

  // The rows arrive once the sheet has risen, not behind it.
  private static final long ENTER_LEAD_MS = 220;

  private static final int BETA_HERO_DP = 112;
  private static final int BETA_GLYPH_DP = 42;

  /** The smart cube's features, icon, title and sentence, in the order they are listed. */
  private static final int[][] FEATURES = {
      {R.drawable.ic_intro_timing, R.string.news_timing_title, R.string.news_timing_body},
      {R.drawable.ic_intro_scramble, R.string.news_scramble_title, R.string.news_scramble_body},
      {R.drawable.ic_intro_breakdown, R.string.news_breakdown_title, R.string.news_breakdown_body},
      {R.drawable.ic_solve_replay, R.string.news_replay_title, R.string.news_replay_body},
      {R.drawable.menu_analysis, R.string.news_analysis_title, R.string.news_analysis_body},
      {R.drawable.ic_menu_drill, R.string.news_drills_title, R.string.news_drills_body}};

  private static final int[] COACH_FEATURE =
      {R.drawable.menu_coach, R.string.news_coach_title, R.string.news_coach_body};

  /** What the coach does, as the beta offer lists it. */
  private static final int[][] COACH_POINTS = {
      {R.drawable.ic_coach_point_plan, R.string.beta_coach_plan_title, R.string.beta_coach_plan_body},
      {R.drawable.ic_coach_target, R.string.beta_coach_drills_title,
          R.string.beta_coach_drills_body},
      {R.drawable.ic_coach_point_practice, R.string.beta_coach_learn_title,
          R.string.beta_coach_learn_body}};

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
    String key = CoachBetaInvite.ENABLED ? BETA_SEEN_KEY : NEWS_SEEN_KEY;
    if (prefs.getBoolean(key, false)) {
      return false;
    }
    prefs.edit().putBoolean(key, true).apply();
    if (firstLaunch && !CoachBetaInvite.ENABLED) {
      return false; // a new user meets the smart cube on its own sheet: there is no "news" to them
    }
    show((FragmentActivity) context);
    return true;
  }

  public static void show(FragmentActivity activity) {
    SmartCubeNewsSheet sheet = new SmartCubeNewsSheet();
    Bundle args = new Bundle();
    args.putBoolean(ARG_BETA, CoachBetaInvite.ENABLED);
    sheet.setArguments(args);
    DialogUtils.showFragment(activity, sheet);
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
    View v = inflater.inflate(R.layout.smart_cube_news_sheet, container, false);
    boolean beta = getArguments() != null && getArguments().getBoolean(ARG_BETA);

    ((TextView) v.findViewById(R.id.tvNewsPill)).setText(beta ? R.string.beta_coach_pill
        : R.string.news_pill);
    ((TextView) v.findViewById(R.id.tvNewsTitle)).setText(beta ? R.string.beta_coach_title
        : R.string.news_title);
    ((TextView) v.findViewById(R.id.tvNewsBody)).setText(beta ? R.string.beta_coach_body
        : R.string.news_body);
    if (beta) {
      ((ImageView) v.findViewById(R.id.imgNewsGlyph)).setImageResource(R.drawable.menu_coach);
      // A smaller hero, so the offer's button shows without scrolling.
      resize(v.findViewById(R.id.newsHero), BETA_HERO_DP);
      resize(v.findViewById(R.id.imgNewsGlyph), BETA_GLYPH_DP);
    }

    List<View> rows = new ArrayList<View>();
    LinearLayout features = v.findViewById(R.id.newsFeatures);
    for (int[] feature : FEATURES) {
      rows.add(addRow(inflater, features, feature));
    }
    if (beta) {
      v.findViewById(R.id.newsCoachCard).setVisibility(View.VISIBLE);
      v.findViewById(R.id.tvNewsFeaturesLabel).setVisibility(View.VISIBLE);
      LinearLayout points = v.findViewById(R.id.newsCoachPoints);
      for (int[] point : COACH_POINTS) {
        addRow(inflater, points, point);
      }
      v.findViewById(R.id.buNewsCoachApply).setOnClickListener(view -> apply());
    } else {
      rows.add(addRow(inflater, features, COACH_FEATURE));
    }
    // Read when drawn, so a beta that has ended takes its line away.
    CoachBetaLook look = CoachBetaLook.of(requireContext());
    v.findViewById(R.id.llNewsCoachBeta).setVisibility(
        !beta && look != null && look.newsLine() ? View.VISIBLE : View.GONE);

    v.findViewById(R.id.tvNewsAllNotes).setOnClickListener(view -> {
      dismiss();
      ReleaseNotes.showReleaseNotesDialog(requireActivity());
    });
    Button later = v.findViewById(R.id.buNewsLater);
    Button connect = v.findViewById(R.id.buNewsConnect);
    later.setOnClickListener(view -> {
      if (beta) {
        DialogUtils.showInfoMessage(requireContext(), getString(R.string.beta_coach_later_hint));
      }
      dismiss();
    });
    // The beta's one loud button is the offer, and a cube already linked needs no connecting.
    if (beta || SmartCubeManager.INSTANCE.isConnected()) {
      connect.setVisibility(View.GONE);
      later.setText(beta ? R.string.beta_coach_not_now : R.string.close);
    } else {
      connect.setOnClickListener(view -> {
        dismiss();
        DialogUtils.showFragment(requireActivity(), new SmartCubeConnectDialog());
      });
    }

    ((SmartCubeRadarView) v.findViewById(R.id.newsRadar)).showSearching();
    EnterAnimation.stagger(ENTER_LEAD_MS, rows.toArray(new View[0]));
    return v;
  }

  private static View addRow(LayoutInflater inflater, LinearLayout parent, int[] row) {
    View view = inflater.inflate(R.layout.smart_cube_news_row, parent, false);
    ((ImageView) view.findViewById(R.id.imgNewsRow)).setImageResource(row[0]);
    ((TextView) view.findViewById(R.id.tvNewsRowTitle)).setText(row[1]);
    ((TextView) view.findViewById(R.id.tvNewsRowBody)).setText(row[2]);
    parent.addView(view);
    return view;
  }

  private static void resize(View view, int dp) {
    ViewGroup.LayoutParams lp = view.getLayoutParams();
    lp.width = lp.height = Math.round(view.getResources().getDisplayMetrics().density * dp);
    view.setLayoutParams(lp);
  }

  private void apply() {
    try {
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(CoachBetaInvite.APPLY_URL)));
    } catch (ActivityNotFoundException e) {
      DialogUtils.showInfoMessage(requireContext(), CoachBetaInvite.APPLY_URL);
    }
  }
}
