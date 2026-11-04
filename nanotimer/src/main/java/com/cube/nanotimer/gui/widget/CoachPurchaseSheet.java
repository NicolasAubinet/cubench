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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.cube.nanotimer.App;
import com.cube.nanotimer.R;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.util.coach.CoachBeta;
import com.cube.nanotimer.util.coach.CoachBetaLook;
import com.cube.nanotimer.util.coach.CoachBetaReply;
import com.cube.nanotimer.util.coach.CoachBilling;
import com.cube.nanotimer.util.coach.CoachClient;
import com.cube.nanotimer.util.coach.CoachOffers;
import com.cube.nanotimer.util.coach.CoachPlans;
import com.cube.nanotimer.util.coach.CoachRefusal;
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
    implements CoachBilling.Listener, CoachBeta.Listener {

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
        if (sheet() == CoachBetaLook.Sheet.JOIN) {
          join();
        } else {
          buy();
        }
      }
    });
    content.findViewById(R.id.buCoachBetaClose).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        dismiss();
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
    CoachBeta.addListener(this);
    billing.readPlans(true);
    onBillingChanged();
  }

  @Override
  public void onStop() {
    if (billing != null) {
      billing.removeListener(this);
    }
    CoachBeta.removeListener(this);
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

  /** A new beta status, which may turn the sheet from the beta to the purchase or back. */
  @Override
  public void onBetaChanged() {
    if (isAdded()) {
      onBillingChanged();
    }
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
    CoachPlansForDialog.pick(requireActivity(), picked, new CoachPlansForDialog.Picked() {
      @Override
      public void picked(SolveType choice) {
        if (isAdded()) {
          picked = choice;
          showSends();
        }
      }
    });
  }

  /** The base plans as Play lists them, or that Play is being asked, or that it could not say. */
  private void showPlans() {
    if (showBeta(sheet())) {
      return;
    }
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

  /** Which sheet the last beta status calls for: the purchase, Join, or a full beta. */
  private CoachBetaLook.Sheet sheet() {
    CoachBetaLook look = CoachBetaLook.of(requireContext());
    return look == null ? CoachBetaLook.Sheet.PAID : look.sheet();
  }

  /**
   * While the coach is in beta: one Free tile in place of the prices, and Join, or a full beta
   * with only Close. Every part is set both ways, so a status that ends the beta while the sheet is
   * open turns it back into the purchase.
   *
   * @return whether the beta was drawn, leaving nothing for the prices to do
   */
  private boolean showBeta(CoachBetaLook.Sheet sheet) {
    boolean beta = sheet != CoachBetaLook.Sheet.PAID;
    boolean join = sheet == CoachBetaLook.Sheet.JOIN;
    content.findViewById(R.id.flCoachBetaTile).setVisibility(beta ? View.VISIBLE : View.GONE);
    content.findViewById(R.id.llCoachBetaLoop).setVisibility(join ? View.VISIBLE : View.GONE);
    content.findViewById(R.id.tvCoachBetaLoopNote).setVisibility(join ? View.VISIBLE : View.GONE);
    content.findViewById(R.id.llCoachBetaMeanwhile)
        .setVisibility(beta && !join ? View.VISIBLE : View.GONE);
    content.findViewById(R.id.llCoachPurchaseHow)
        .setVisibility(beta && !join ? View.GONE : View.VISIBLE);
    content.findViewById(R.id.tvCoachPurchaseNote).setVisibility(beta ? View.GONE : View.VISIBLE);
    content.findViewById(R.id.buCoachBetaClose)
        .setVisibility(beta && !join ? View.VISIBLE : View.GONE);
    TextView button = content.findViewById(R.id.buCoachPurchase);
    button.setVisibility(beta && !join ? View.GONE : View.VISIBLE);
    content.findViewById(R.id.llCoachPurchasePlans).setVisibility(beta ? View.GONE : View.VISIBLE);
    if (!beta) {
      return false;
    }
    ((LinearLayout) content.findViewById(R.id.llCoachPurchasePlans)).removeAllViews();
    content.findViewById(R.id.pbCoachPurchase).setVisibility(View.GONE);
    content.findViewById(R.id.tvCoachPurchaseUnavailable).setVisibility(View.GONE);
    content.findViewById(R.id.tvCoachPurchaseFree).setVisibility(View.GONE);
    // The tile and its badge are lit for an open beta and greyed for a full one.
    content.findViewById(R.id.llCoachBetaTile).setSelected(join);
    content.findViewById(R.id.tvCoachBetaBadge).setSelected(join);
    TextView title = content.findViewById(R.id.tvCoachBetaTileTitle);
    title.setText(join ? R.string.coach_beta_free : R.string.coach_beta_full_title);
    title.setTextSize(join ? 30 : 21);
    ((TextView) content.findViewById(R.id.tvCoachBetaTileSub))
        .setText(join ? R.string.coach_beta_free_while : R.string.coach_beta_full_body);
    content.findViewById(R.id.tvCoachBetaTileNothing)
        .setVisibility(join ? View.VISIBLE : View.GONE);
    showLoop();
    button.setText(R.string.coach_beta_join);
    button.setEnabled(true);
    return true;
  }

  /** Get your plan, practise, rate it, next plan, with the rating lit in amber. */
  private void showLoop() {
    LinearLayout loop = content.findViewById(R.id.llCoachBetaLoop);
    if (loop.getChildCount() > 0) {
      return;
    }
    int[][] steps = {{R.drawable.ic_coach_point_plan, R.string.coach_beta_loop_plan},
        {R.drawable.ic_coach_point_practice, R.string.coach_beta_loop_practise},
        {R.drawable.ic_coach_point_star, R.string.coach_beta_loop_rate},
        {R.drawable.ic_coach_loop_next, R.string.coach_beta_loop_next}};
    LayoutInflater inflater = LayoutInflater.from(requireContext());
    for (int i = 0; i < steps.length; i++) {
      if (i > 0) {
        TextView arrow = new TextView(requireContext());
        arrow.setText("→");
        arrow.setTextColor(ContextCompat.getColor(requireContext(), R.color.secondary_text));
        arrow.setTextSize(13);
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        loop.addView(arrow);
      }
      View step = inflater.inflate(R.layout.coach_beta_loop_step, loop, false);
      ((ImageView) step.findViewById(R.id.imgCoachBetaStep)).setImageResource(steps[i][0]);
      TextView word = step.findViewById(R.id.tvCoachBetaStep);
      word.setText(steps[i][1]);
      if (steps[i][1] == R.string.coach_beta_loop_rate) {
        word.setTextColor(ContextCompat.getColor(requireContext(), R.color.warning));
        word.setTypeface(word.getTypeface(), android.graphics.Typeface.BOLD);
      }
      loop.addView(step);
    }
  }

  /**
   * Joins the beta. Joining is agreeing to the weekly summary, as subscribing is, so the plans turn
   * on for the solve type the card names. A beta that filled or closed meanwhile is asked again,
   * which redraws the sheet as full.
   */
  private void join() {
    final TextView button = content.findViewById(R.id.buCoachPurchase);
    button.setEnabled(false);
    final SolveType forType = picked;
    new Thread(new Runnable() {
      @Override
      public void run() {
        final CoachBetaReply reply = client.joinBeta();
        if (reply.getKind() == CoachBetaReply.Kind.REFUSED) {
          client.checkBeta();
        }
        final Activity activity = getActivity();
        if (activity == null) {
          return;
        }
        activity.runOnUiThread(new Runnable() {
          @Override
          public void run() {
            if (reply.getKind() == CoachBetaReply.Kind.STATUS && reply.isMember()) {
              if (forType != null && CoachPlans.eligible(forType)) {
                CoachPlans.moveTo(forType);
              }
              CoachPlans.turnOn(activity.getApplicationContext(), forType, null);
            } else if (reply.getKind() != CoachBetaReply.Kind.REFUSED
                || (reply.getRefusal() != CoachRefusal.BETA_FULL
                    && reply.getRefusal() != CoachRefusal.BETA_CLOSED)) {
              Toast.makeText(activity, R.string.coach_beta_join_failed, Toast.LENGTH_LONG).show();
            }
            if (isAdded()) {
              onBillingChanged();
            }
          }
        });
      }
    }, "coach-beta-join").start();
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
