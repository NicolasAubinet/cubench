package com.cube.nanotimer.gui.widget;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.cube.nanotimer.App;
import com.cube.nanotimer.R;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.util.coach.CoachBilling;
import com.cube.nanotimer.util.coach.CoachClient;
import com.cube.nanotimer.util.coach.CoachOffers;
import com.cube.nanotimer.util.coach.CoachPlans;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.SolveType;

import java.util.Collections;
import java.util.List;

/**
 * Where the coach is bought: the two base plans at the prices Play lists for this buyer, and what
 * subscribing sends every week. Subscribing is agreeing to that, so the weekly plans turn on with
 * the purchase and are written for the solve type the text names.
 */
public class CoachPurchaseSheet extends NanoTimerBottomSheetFragment
    implements CoachBilling.Listener {

  private static final String ARG_SOLVE_TYPE = "solveType";
  private static final String STATE_PLAN = "plan";
  private static final String STATE_PICKED = "picked";

  private CoachBilling billing;
  private CoachClient client;
  private View content;
  private List<SolveType> eligible = Collections.emptyList();
  private SolveType picked;
  private String basePlan = CoachBilling.YEARLY;

  /** @param solveType the solve type the plans are offered for first, when it can carry them */
  public static CoachPurchaseSheet newInstance(SolveType solveType) {
    CoachPurchaseSheet sheet = new CoachPurchaseSheet();
    Bundle bundle = new Bundle();
    bundle.putSerializable(ARG_SOLVE_TYPE, solveType);
    sheet.setArguments(bundle);
    return sheet;
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    content = inflater.inflate(R.layout.coach_purchase_sheet, container, false);
    billing = CoachBilling.of(requireContext());
    client = CoachClient.configured(requireContext());
    picked = (SolveType) requireArguments().getSerializable(ARG_SOLVE_TYPE);
    if (savedInstanceState != null) {
      basePlan = savedInstanceState.getString(STATE_PLAN, basePlan);
      picked = (SolveType) savedInstanceState.getSerializable(STATE_PICKED);
    }
    content.findViewById(R.id.llCoachPurchaseType).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        pickType();
      }
    });
    content.findViewById(R.id.tvCoachPurchasePrivacy).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Utils.openPrivacyPolicy(requireContext());
      }
    });
    content.findViewById(R.id.buCoachPurchase).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        buy();
      }
    });
    showSends();
    showPlans();
    loadTypes();
    return content;
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putString(STATE_PLAN, basePlan);
    outState.putSerializable(STATE_PICKED, picked);
  }

  @Override
  public void onStart() {
    super.onStart();
    if (billing == null) {
      dismiss(); // a build with no coach has nothing to sell
      return;
    }
    billing.addListener(this);
    billing.readPlans(true);
    onBillingChanged();
  }

  @Override
  public void onStop() {
    if (billing != null) {
      billing.removeListener(this);
    }
    super.onStop();
  }

  @Override
  protected float maxHeightRatio() {
    return 0.9f; // the upload text is read in full, and the button stays pinned under it
  }

  @Override
  public void onBillingChanged() {
    if (client.hasAccess()) {
      askToNotify(requireActivity());
      dismissAllowingStateLoss(); // before Android 9 the state is saved ahead of onStop
      return;
    }
    showPlans();
  }

  /** The solve types the plans can be written for, the one offered first kept when it is one. */
  private void loadTypes() {
    App.INSTANCE.getService().getSolveTypes(CubeType.THREE_BY_THREE,
        new DataCallback<List<SolveType>>() {
          @Override
          public void onData(final List<SolveType> solveTypes) {
            final Activity activity = getActivity();
            if (activity == null) {
              return;
            }
            activity.runOnUiThread(new Runnable() {
              @Override
              public void run() {
                if (!isAdded()) {
                  return;
                }
                eligible = CoachPlans.eligible(solveTypes);
                SolveType offered = picked;
                picked = null;
                for (SolveType solveType : eligible) {
                  if (picked == null || solveType.getId() == offered.getId()) {
                    picked = solveType;
                  }
                }
                if (picked == null) {
                  picked = offered; // none can carry the plans, which then wait for one that can
                }
                showSends();
              }
            });
          }
        });
  }

  private void showSends() {
    String name = name(picked);
    // With one solve type to write them for there is nothing to choose.
    content.findViewById(R.id.llCoachPurchaseType)
        .setVisibility(eligible.size() > 1 ? View.VISIBLE : View.GONE);
    ((TextView) content.findViewById(R.id.tvCoachPurchaseType)).setText(name + " ▾");
    ((TextView) content.findViewById(R.id.tvCoachPurchaseSends))
        .setText(getString(R.string.coach_sends_body, name));
  }

  private void pickType() {
    String[] names = new String[eligible.size()];
    for (int i = 0; i < names.length; i++) {
      names[i] = name(eligible.get(i));
    }
    new AlertDialog.Builder(requireActivity(), R.style.NanoTimerDialogTheme)
        .setTitle(R.string.coach_plans_for)
        .setSingleChoiceItems(names, eligible.indexOf(picked),
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                picked = eligible.get(which);
                showSends();
              }
            })
        .show();
  }

  /** The base plans as Play lists them, or that Play is being asked, or that it could not say. */
  private void showPlans() {
    List<CoachOffers.Offer> plans = billing == null ? null : billing.getPlans();
    LinearLayout rows = content.findViewById(R.id.llCoachPurchasePlans);
    rows.removeAllViews();
    content.findViewById(R.id.pbCoachPurchase)
        .setVisibility(plans == null ? View.VISIBLE : View.GONE);
    content.findViewById(R.id.tvCoachPurchaseUnavailable)
        .setVisibility(plans != null && plans.isEmpty() ? View.VISIBLE : View.GONE);
    TextView free = content.findViewById(R.id.tvCoachPurchaseFree);
    free.setVisibility(View.GONE);
    TextView button = content.findViewById(R.id.buCoachPurchase);
    button.setEnabled(false);
    button.setText(R.string.coach_purchase_subscribe);
    if (plans == null || plans.isEmpty()) {
      return;
    }
    CoachPriceText text = new CoachPriceText(getResources());
    CoachOffers.Offer selected = plans.get(0);
    for (CoachOffers.Offer plan : plans) {
      if (plan.getBasePlan().equals(basePlan)) {
        selected = plan;
      }
    }
    basePlan = selected.getBasePlan();
    LayoutInflater inflater = LayoutInflater.from(requireContext());
    int gap = getResources().getDimensionPixelSize(R.dimen.coach_purchase_tile_gap);
    for (final CoachOffers.Offer plan : plans) {
      View tile = inflater.inflate(R.layout.coach_purchase_plan, rows, false);
      ((TextView) tile.findViewById(R.id.tvCoachPurchasePlanName)).setText(
          CoachBilling.YEARLY.equals(plan.getBasePlan()) ? R.string.coach_plan_yearly
              : R.string.coach_plan_monthly);
      setOrHide((TextView) tile.findViewById(R.id.tvCoachPurchasePlanTag), text.tag(plan));
      ((TextView) tile.findViewById(R.id.tvCoachPurchasePlanPrice)).setText(text.price(plan));
      setOrHide((TextView) tile.findViewById(R.id.tvCoachPurchasePlanPer), text.per(plan));
      setOrHide((TextView) tile.findViewById(R.id.tvCoachPurchasePlanThen), text.then(plan));
      if (rows.getChildCount() > 0) {
        ((ViewGroup.MarginLayoutParams) tile.getLayoutParams()).setMarginStart(gap);
      }
      // Both pass down to the card and its badge, so the badge is part of what is tapped.
      tile.setSelected(plan == selected);
      tile.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          basePlan = plan.getBasePlan();
          showPlans();
        }
      });
      rows.addView(tile);
    }
    setOrHide(free, text.free(selected));
    button.setEnabled(true);
    button.setText(text.button(selected));
  }

  private void buy() {
    // Kept before the purchase, which is where turning the plans on reads it from.
    if (CoachPlans.eligible(picked)) {
      CoachPlans.moveTo(picked);
    }
    billing.buy(requireActivity(), basePlan);
  }

  /** The plan and the reminder arrive as notifications, which Android 13 and later ask about. */
  private static void askToNotify(Activity activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        && ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(activity,
          new String[] {Manifest.permission.POST_NOTIFICATIONS}, 0);
    }
  }

  private static void setOrHide(TextView view, String text) {
    view.setVisibility(text == null ? View.GONE : View.VISIBLE);
    view.setText(text);
  }

  private String name(SolveType solveType) {
    return Utils.toSolveTypeLocalizedName(requireContext(), solveType.getName());
  }
}
