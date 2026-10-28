package com.cube.nanotimer.gui.widget;

import android.content.Intent;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.cube.nanotimer.App;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.coach.CoachPayloadBuilder;
import com.cube.nanotimer.coach.PlanSchedule;
import com.cube.nanotimer.cube.SolveTypeMethod;
import com.cube.nanotimer.gui.SolveTypesActivity;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.util.coach.CoachAccess;
import com.cube.nanotimer.util.coach.CoachBilling;
import com.cube.nanotimer.util.coach.CoachClient;
import com.cube.nanotimer.util.coach.CoachOffers;
import com.cube.nanotimer.util.coach.CoachPlanWorker;
import com.cube.nanotimer.util.coach.CoachPlans;
import com.cube.nanotimer.util.coach.CoachRead;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.SolveType;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Where the coach stands for this install, said on the Plan tab.
 *
 * <p>An install that has the coach reads a card above everything else: when the next plan comes
 * on the plans' own solve type, where the plans go on another one, and that they are paused with
 * no solve type to carry them. The countdown moves into the plan's own card once there is a plan.
 * One whose access has ended reads there that it has, with the way back to the purchase.
 *
 * <p>An install that never had the coach reads the offer inside the card that explains a plan:
 * how far it is from the solves a first plan needs, then the button that opens the purchase.
 */
public class CoachPlanStatus {

  private static final String SHEET_TAG = "coach_purchase";

  private final FragmentActivity activity;
  private final ViewGroup tab;
  private final View status;
  private final SolveType solveType;
  /** Null in a build with no coach to ask: without one there is nothing to say or to sell. */
  private final CoachClient client;
  /** Null in a build with no coach. */
  private final CoachBilling billing;
  /** Once per screen: a check that ends without a plan must not start the next one. */
  private boolean checked;
  /** Where the card sits on its own, and where it moves while a written plan is on screen. */
  private final int homeIndex;
  private ViewGroup dock;

  /**
   * @param tab the Plan tab's own layout
   * @param solveType the solve type the screen is showing
   */
  public CoachPlanStatus(FragmentActivity activity, ViewGroup tab, SolveType solveType,
      CoachClient client, CoachBilling billing) {
    this.activity = activity;
    this.tab = tab;
    this.status = tab.findViewById(R.id.llCoachPlanStatus);
    this.homeIndex = tab.indexOfChild(status);
    this.solveType = solveType;
    this.client = client;
    this.billing = billing;
  }

  /**
   * Where the countdown to the next plan goes while a written plan is on screen: into that plan's
   * card. Everything else the card says stays on top of the tab.
   *
   * @param dock the plan card's slot, or null when no plan is shown
   */
  public void dockIn(ViewGroup dock) {
    if (this.dock != null && dock == null) {
      place(false);
    }
    this.dock = dock;
  }

  /** Reads the access and the schedule again and redraws. Main thread only. */
  public void refresh() {
    if (client == null) {
      hide();
      showNoOffer();
      return;
    }
    boolean cfop = solveType.getCubeTypeId() == CubeType.THREE_BY_THREE.getId()
        && SolveTypeMethod.of(solveType) == CubeMethod.CFOP;
    if (client.hasAccess()) {
      showNoOffer();
      if (!Options.INSTANCE.isCoachPlansOn()) {
        // A build token asks for nothing, so its weekly plans turn on where they are looked for.
        CoachPlans.turnOn(activity, solveType, new Runnable() {
          @Override
          public void run() {
            show(new Runnable() {
              @Override
              public void run() {
                refresh();
              }
            });
          }
        });
      } else if (cfop) {
        loadSchedule();
      } else {
        hide();
      }
    } else if (client.accessEnded() || Options.INSTANCE.isCoachPlansOn()) {
      // Weekly plans that are on with nothing to let the install in: it was let in before.
      showNoOffer();
      if (cfop) {
        showEnded();
      } else {
        hide();
      }
    } else {
      hide();
      if (CoachPlans.eligible(solveType)) {
        loadOffer();
      } else {
        showNoOffer();
      }
    }
  }

  private void loadSchedule() {
    App.INSTANCE.getService().getSolveTypes(CubeType.THREE_BY_THREE,
        new DataCallback<List<SolveType>>() {
          @Override
          public void onData(List<SolveType> solveTypes) {
            final boolean none = CoachPlans.eligible(solveTypes).isEmpty();
            final SolveType flagged = CoachPlans.flagged(solveTypes);
            if (flagged == null || flagged.getId() != solveType.getId()) {
              show(new Runnable() {
                @Override
                public void run() {
                  showElsewhere(flagged, none);
                }
              });
              return;
            }
            long now = System.currentTimeMillis();
            App.INSTANCE.getService().getPlanSchedule(flagged, CubeMethod.CFOP,
                CoachPlans.lastPlanAt(now), now, new DataCallback<PlanSchedule>() {
                  @Override
                  public void onData(final PlanSchedule schedule) {
                    show(new Runnable() {
                      @Override
                      public void run() {
                        showNext(schedule);
                      }
                    });
                  }
                });
          }
        });
  }

  /**
   * The purchase is offered once a solve type has the solves a first plan needs, so the free weeks
   * are not spent waiting for one. Until then the card counts the solves of the type on screen.
   */
  private void loadOffer() {
    App.INSTANCE.getService().getSolveTypes(CubeType.THREE_BY_THREE,
        new DataCallback<List<SolveType>>() {
          @Override
          public void onData(List<SolveType> solveTypes) {
            count(CoachPlans.eligible(solveTypes), new ArrayList<Integer>());
          }
        });
  }

  /** Counts one solve type after the other, the service answering each on a thread of its own. */
  private void count(final List<SolveType> eligible, final List<Integer> counts) {
    if (counts.size() < eligible.size()) {
      App.INSTANCE.getService().getCoachSolveCount(eligible.get(counts.size()), CubeMethod.CFOP,
          new DataCallback<Integer>() {
            @Override
            public void onData(Integer count) {
              counts.add(count);
              count(eligible, counts);
            }
          });
      return;
    }
    int here = 0;
    SolveType ready = null;
    for (int i = 0; i < eligible.size(); i++) {
      boolean shown = eligible.get(i).getId() == solveType.getId();
      if (shown) {
        here = counts.get(i);
      }
      if (counts.get(i) >= CoachPayloadBuilder.PLAN_FLOOR && (ready == null || shown)) {
        ready = eligible.get(i);
      }
    }
    final int solves = here;
    final SolveType offered = ready;
    show(new Runnable() {
      @Override
      public void run() {
        // The purchase may have been made while the solves were counted.
        if (client.hasAccess()) {
          return;
        }
        if (offered == null) {
          showGate(solves);
        } else {
          showOffer(offered);
        }
      }
    });
  }

  private void show(final Runnable draw) {
    activity.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (!activity.isFinishing()) {
          draw.run();
        }
      }
    });
  }

  /** The strip on the plans' own solve type: when the next plan comes. */
  private void showNext(PlanSchedule schedule) {
    boolean writing = CoachRead.of(solveType).getState() == CoachRead.State.READING;
    long heldUntil = CoachPlans.heldUntil(System.currentTimeMillis());
    boolean waiting = writing;
    // A count of days or solves sits beside the label. A sentence takes the row for itself.
    CharSequence value = null;
    String sentence = null;
    if (writing) {
      sentence = activity.getString(R.string.coach_writing);
    } else {
      switch (schedule.getState()) {
        case NOT_DUE:
          String days = quantity(R.plurals.coach_days, schedule.getDaysUntilDue());
          value = schedule.getSolvesNeeded() == 0 ? lit(days) : TextUtils.concat(lit(days),
              " · ", lit(quantity(R.plurals.coach_more_solves, schedule.getSolvesNeeded())));
          break;
        case NEEDS_SOLVES:
          value = lit(quantity(R.plurals.coach_more_solves, schedule.getSolvesNeeded()));
          break;
        case QUIET:
          value = activity.getString(R.string.coach_strip_session);
          waiting = true;
          break;
        default:
          if (heldUntil != 0) {
            sentence = activity.getString(R.string.coach_next_retry,
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(new Date(heldUntil)));
          } else {
            value = activity.getString(R.string.coach_strip_waiting);
            waiting = true;
          }
          break;
      }
    }
    showCard(false);
    place(true);
    TextView label = status.findViewById(R.id.tvCoachPlanStatusLabel);
    label.setVisibility(value == null ? View.GONE : View.VISIBLE);
    label.setText(schedule.isFirst() ? R.string.coach_strip_first : R.string.coach_strip_next);
    TextView text = status.findViewById(R.id.tvCoachPlanStatus);
    text.setText(value == null ? sentence : value);
    text.setGravity(value == null ? Gravity.START : Gravity.END);
    text.setTextColor(ContextCompat.getColor(activity, R.color.secondary_text));
    text.setTextSize(13.5f);
    text.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
    status.findViewById(R.id.pbCoachPlanStatus).setVisibility(waiting ? View.VISIBLE : View.GONE);
    showMeter(schedule, value != null);

    // A first plan does not wait for the session to end while the solver is looking at the tab.
    if (!checked && !writing && heldUntil == 0 && schedule.mayAsk(true)) {
      checked = true;
      CoachPlanWorker.checkFromScreen(activity);
    }
  }

  /** How many of the solves the next plan waits for are done, while it waits for any. */
  private void showMeter(PlanSchedule schedule, boolean counting) {
    ProgressBar meter = status.findViewById(R.id.pbCoachPlanMeter);
    int needed = schedule.getSolvesNeeded();
    meter.setVisibility(counting && needed > 0 ? View.VISIBLE : View.GONE);
    int of = schedule.isFirst() ? CoachPayloadBuilder.PLAN_FLOOR : CoachPayloadBuilder.SINCE_FLOOR;
    meter.setMax(of);
    meter.setProgress(Math.max(of - needed, 0));
  }

  /**
   * On a solve type the plans are not written for: where they go, or that they go nowhere.
   *
   * @param none whether no solve type at all can carry the plans
   */
  private void showElsewhere(SolveType flagged, boolean none) {
    showCard(false);
    TextView text = showTitle(flagged == null ? activity.getString(R.string.coach_plans_paused)
        : activity.getString(R.string.coach_plans_written_for,
            Utils.toSolveTypeLocalizedName(activity, flagged.getName())));
    if (flagged != null) {
      return;
    }
    text.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_plans_paused, 0, 0, 0);
    text.setCompoundDrawablePadding(
        Math.round(6 * activity.getResources().getDisplayMetrics().density));
    String why = activity.getString(R.string.coach_plans_paused_body);
    TextView body = status.findViewById(R.id.tvCoachPlanStatusBody);
    body.setText(none ? why + " " + activity.getString(R.string.coach_plans_paused_none) : why);
    body.setVisibility(View.VISIBLE);
    final boolean here = CoachPlans.eligible(solveType);
    TextView button = status.findViewById(R.id.buCoachPlanStatus);
    button.setText(here
        ? activity.getString(R.string.coach_plans_resume_here,
            Utils.toSolveTypeLocalizedName(activity, solveType.getName()))
        : activity.getString(R.string.solve_types));
    button.setVisibility(View.VISIBLE);
    button.setBackgroundResource(here ? R.drawable.dialog_action_button
        : R.drawable.dialog_action_button_ghost);
    button.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (here) {
          moveTo(solveType);
        } else {
          activity.startActivity(new Intent(activity, SolveTypesActivity.class)
              .putExtra("cubeType", CubeType.THREE_BY_THREE));
        }
      }
    });
  }

  /**
   * The access has ended: no plan is written any more, and the purchase is one tap away. It is
   * called a subscription only when one was bought, a code's access having an end of its own.
   */
  private void showEnded() {
    showCard(true);
    showTitle(activity.getString(CoachAccess.of(activity).getPurchaseToken() == null
        ? R.string.coach_ended_title_access : R.string.coach_ended_title));
    TextView body = status.findViewById(R.id.tvCoachPlanStatusBody);
    body.setText(R.string.coach_ended_body);
    body.setVisibility(View.VISIBLE);
    TextView button = status.findViewById(R.id.buCoachPlanStatus);
    button.setText(R.string.coach_ended_button);
    button.setVisibility(View.VISIBLE);
    button.setBackgroundResource(R.drawable.dialog_action_button_ghost);
    button.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        openPurchase(solveType);
      }
    });
    billing.readPlans(false); // asked now, so the sheet opens on its prices
  }

  /** The card with nothing in it but its first line, which the caller fills the rest of. */
  private void showCard(boolean ended) {
    place(false);
    status.setVisibility(View.VISIBLE);
    status.setBackgroundResource(ended ? R.drawable.coach_card_ended : R.drawable.analysis_card);
    status.findViewById(R.id.pbCoachPlanStatus).setVisibility(View.GONE);
    status.findViewById(R.id.tvCoachPlanStatusLabel).setVisibility(View.GONE);
    status.findViewById(R.id.pbCoachPlanMeter).setVisibility(View.GONE);
    status.findViewById(R.id.tvCoachPlanStatusBody).setVisibility(View.GONE);
    status.findViewById(R.id.buCoachPlanStatus).setVisibility(View.GONE);
  }

  private void hide() {
    place(false);
    status.setVisibility(View.GONE);
  }

  /**
   * Moves the card into the plan's card, where it loses its own ground and says what the next plan
   * will answer, or back to the top of the tab.
   */
  private void place(boolean docked) {
    ViewGroup target = docked && dock != null ? dock : tab;
    if (status.getParent() != target) {
      ((ViewGroup) status.getParent()).removeView(status);
      if (target == tab) {
        tab.addView(status, homeIndex);
      } else {
        target.addView(status);
      }
    }
    boolean inDock = target != tab;
    if (dock != null) {
      dock.setVisibility(inDock ? View.VISIBLE : View.GONE);
    }
    status.findViewById(R.id.tvCoachPlanStatusLoop)
        .setVisibility(inDock ? View.VISIBLE : View.GONE);
    ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) status.getLayoutParams();
    margins.bottomMargin =
        inDock ? 0 : activity.getResources().getDimensionPixelSize(R.dimen.space_m);
    status.setLayoutParams(margins);
    if (inDock) {
      status.setBackground(null);
      status.setPadding(0, dp(12), 0, 0);
    } else {
      status.setPadding(dp(16), dp(12), dp(16), dp(14));
    }
  }

  private int dp(int value) {
    return Math.round(value * activity.getResources().getDisplayMetrics().density);
  }

  private TextView showTitle(String title) {
    TextView text = status.findViewById(R.id.tvCoachPlanStatus);
    text.setText(title);
    text.setGravity(Gravity.START);
    text.setTextColor(ContextCompat.getColor(activity, R.color.white));
    text.setTextSize(16);
    text.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
    return text;
  }

  /** @param solves how many smart cube solves the solve type on screen has */
  private void showGate(int solves) {
    int floor = CoachPayloadBuilder.PLAN_FLOOR;
    int more = floor - solves;
    tab.findViewById(R.id.tvAnalysisPlanCites).setVisibility(View.GONE);
    showPoints(true);
    tab.findViewById(R.id.llCoachOffer).setVisibility(View.GONE);
    tab.findViewById(R.id.llCoachGate).setVisibility(View.VISIBLE);
    ((TextView) tab.findViewById(R.id.tvCoachGateTitle))
        .setText(activity.getString(R.string.coach_gate_title, floor));
    ((TextView) tab.findViewById(R.id.tvCoachGateCount))
        .setText(activity.getString(R.string.coach_gate_count, solves, floor));
    ProgressBar meter = tab.findViewById(R.id.pbCoachGate);
    meter.setMax(floor);
    meter.setProgress(solves);
    ((TextView) tab.findViewById(R.id.tvCoachGateNote)).setText(activity.getResources()
        .getQuantityString(R.plurals.coach_gate_note, more, more,
            Utils.toSolveTypeLocalizedName(activity, solveType.getName())));
  }

  /** @param offered the solve type with enough solves, which the purchase offers the plans for */
  private void showOffer(final SolveType offered) {
    tab.findViewById(R.id.tvAnalysisPlanCites).setVisibility(View.GONE);
    showPoints(true);
    tab.findViewById(R.id.llCoachGate).setVisibility(View.GONE);
    tab.findViewById(R.id.llCoachOffer).setVisibility(View.VISIBLE);
    // Without Play's prices the button still opens the sheet, which says they could not be read.
    List<CoachOffers.Offer> plans = billing.getPlans();
    CoachPriceText text = new CoachPriceText(activity.getResources());
    TextView button = tab.findViewById(R.id.buCoachOffer);
    button.setText(plans == null ? activity.getString(R.string.coach_offer_try)
        : text.offerButton(plans));
    button.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        openPurchase(offered);
      }
    });
    String note = plans == null ? null : text.offerNote(plans);
    TextView noteView = tab.findViewById(R.id.tvCoachOfferNote);
    noteView.setText(note);
    noteView.setVisibility(note == null ? View.GONE : View.VISIBLE);
    billing.readPlans(false);
  }

  /** For an install with the coach, or with no coach to have. */
  private void showNoOffer() {
    tab.findViewById(R.id.tvAnalysisPlanCites).setVisibility(View.VISIBLE);
    showPoints(false);
    tab.findViewById(R.id.llCoachGate).setVisibility(View.GONE);
    tab.findViewById(R.id.llCoachOffer).setVisibility(View.GONE);
  }

  /** What the coach gives, for an install that could still buy it; the sentence once it has. */
  private void showPoints(boolean points) {
    tab.findViewById(R.id.llAnalysisPlanPoints).setVisibility(points ? View.VISIBLE : View.GONE);
    tab.findViewById(R.id.tvAnalysisPlanPrivate).setVisibility(points ? View.VISIBLE : View.GONE);
    tab.findViewById(R.id.tvAnalysisPlanBody).setVisibility(points ? View.GONE : View.VISIBLE);
  }

  private void openPurchase(SolveType offered) {
    if (activity.getSupportFragmentManager().findFragmentByTag(SHEET_TAG) == null) {
      CoachPurchaseSheet.newInstance(offered)
          .show(activity.getSupportFragmentManager(), SHEET_TAG);
    }
  }

  /** Only from a pause, where the plans are written for nothing and none can be lost. */
  private void moveTo(SolveType to) {
    CoachPlans.moveTo(to);
    checked = false;
    refresh();
  }

  private String quantity(int plural, int count) {
    return activity.getResources().getQuantityString(plural, count, count);
  }

  /** A figure in white, which is what the eye is after on the strip. */
  private CharSequence lit(String figure) {
    SpannableString text = new SpannableString(figure);
    text.setSpan(new ForegroundColorSpan(ContextCompat.getColor(activity, R.color.white)), 0,
        figure.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    return text;
  }
}
