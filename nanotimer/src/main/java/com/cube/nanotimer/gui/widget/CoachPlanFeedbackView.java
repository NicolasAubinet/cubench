package com.cube.nanotimer.gui.widget;

import android.app.Activity;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.cube.nanotimer.App;
import com.cube.nanotimer.R;
import com.cube.nanotimer.coach.StoredCoachPlan;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.util.coach.CoachBeta;
import com.cube.nanotimer.util.coach.CoachBetaLook;
import com.cube.nanotimer.util.coach.CoachClient;
import com.cube.nanotimer.util.coach.CoachPlanFeedback;
import com.cube.nanotimer.util.coach.CoachPlanWorker;
import com.cube.nanotimer.vo.SolveType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the solver is asked about the plan on screen, drawn over {@link CoachPlanView}'s cards: an
 * answer at the foot of each focus, the stars in a card that closes the tab, and on a plan that
 * follows another, whether that one was worked on.
 *
 * <p>Drawn only in a feedback mode that asks ({@link CoachBetaLook#asksFeedback}), and only on the
 * plan the service last sent, the one a rating goes to. In required mode Send waits for every
 * answer; in optional mode only the stars are needed, since the service keeps nothing without them.
 * Answers are kept as a draft on the device until they are sent.
 */
public class CoachPlanFeedbackView {

  private static final int STAR_COUNT = 5;
  private static final int AMBER = 0xFFF5B62A;
  private static final int STAR_OFF = 0xFF64727A;

  private final Activity activity;
  private final SolveType solveType;
  private final LayoutInflater inflater;
  private final List<View> focusBlocks = new ArrayList<View>();

  private StoredCoachPlan stored;
  private LinearLayout areas;
  private View hero;
  private String planId;
  private CoachPlanFeedback feedback;
  private CoachBetaLook look;
  private View rateCard;
  private View lastWeek;
  /** A sent rating opened again by a tap on its card. */
  private boolean editing;
  /** The focus outlined in amber after a tap on Send that it was missing for. */
  private int flagged = -1;
  private boolean sending;
  /** Each focus's reasons folded or not, by index; unset is folded once anything is picked. */
  private final Map<Integer, Boolean> folded = new HashMap<Integer, Boolean>();

  public CoachPlanFeedbackView(Activity activity, SolveType solveType) {
    this.activity = activity;
    this.solveType = solveType;
    this.inflater = LayoutInflater.from(activity);
    // A focus answered without ever being on screen is a signal the report reads.
    activity.getWindow().getDecorView().getViewTreeObserver().addOnScrollChangedListener(
        new ViewTreeObserver.OnScrollChangedListener() {
          @Override
          public void onScrollChanged() {
            noteSeen();
          }
        });
  }

  /**
   * Draws the questions on the plan, or takes them away. Call after {@link CoachPlanView#setPlan}
   * and {@link CoachPlanView#setHero}, whose cards they hang from.
   *
   * @param areas the focus cards, in the plan's order
   * @param hero the plan's own card
   */
  public void show(StoredCoachPlan stored, LinearLayout areas, View hero) {
    if (this.stored == null || stored == null || stored.getWrittenAt() != this.stored.getWrittenAt()) {
      editing = false;
      flagged = -1;
    }
    this.stored = stored;
    this.areas = areas;
    this.hero = hero;
    detach(rateCard);
    detach(lastWeek);
    for (View block : focusBlocks) {
      detach(block);
    }
    focusBlocks.clear();
    rateCard = null;
    lastWeek = null;
    look = CoachBetaLook.of(activity);
    hero.findViewById(R.id.tvAnalysisPlanHeroTag)
        .setVisibility(stored != null && look != null && look.planTag() ? View.VISIBLE : View.GONE);
    CoachBeta beta = CoachBeta.of(activity);
    if (stored == null || look == null || !look.asksFeedback()
        || !beta.isLastPlan(solveType.getId(), stored.getWrittenAt())) {
      return;
    }
    if (!beta.getPlanId().equals(planId)) {
      folded.clear();
    }
    planId = beta.getPlanId();
    feedback = beta.getFeedback(planId);
    feedback.opened(System.currentTimeMillis());
    save();
    boolean open = editing || !beta.isRated(planId);
    int count = Math.min(stored.getPlan().getFocus().size(), areas.getChildCount());
    for (int i = 0; i < count; i++) {
      View block = inflater.inflate(R.layout.coach_fb_focus, (ViewGroup) areas.getChildAt(i), false);
      ((ViewGroup) areas.getChildAt(i)).addView(block);
      block.setVisibility(open ? View.VISIBLE : View.GONE);
      focusBlocks.add(block);
      bindFocus(block, i);
    }
    rateCard = inflater.inflate(R.layout.coach_fb_rate, areas, false);
    areas.addView(rateCard);
    bindRate(open);
    if (open && beta.getPreviousPlanId() != null) {
      showLastWeek();
    }
  }

  /** Brings the stars' card into view, from the plan card's "Rate now". */
  public void scrollToRate() {
    if (rateCard != null) {
      scrollTo(rateCard);
    }
  }

  private void bindFocus(final View block, final int index) {
    final CoachPlanFeedback.FocusAnswer answer = feedback.getFocus(index);
    CoachPlanFeedback.Answer given = answer == null ? null : answer.getAnswer();
    int picked = given == CoachPlanFeedback.Answer.YES ? 0
        : given == CoachPlanFeedback.Answer.NOT_QUITE ? 1 : -1;
    choice((LinearLayout) block.findViewById(R.id.llFbChoice),
        new int[] {R.string.coach_fb_yes, R.string.coach_fb_not_quite}, picked, flagged == index,
        new Picked() {
          @Override
          public void picked(int which) {
            feedback.answer(index, which == 0 ? CoachPlanFeedback.Answer.YES
                : CoachPlanFeedback.Answer.NOT_QUITE);
            folded.put(index, false); // being answered: a redraw must not fold it
            feedback.seen(index);
            if (flagged == index) {
              flagged = -1;
            }
            save();
            bindFocus(block, index);
            bindRate(true);
          }
        });
    View why = block.findViewById(R.id.llFbWhy);
    why.setVisibility(picked == 1 ? View.VISIBLE : View.GONE);
    if (picked != 1) {
      return;
    }
    RadioGroup reasons = block.findViewById(R.id.rgFbWhy);
    reasons.setOnCheckedChangeListener(null);
    reasons.removeAllViews();
    final CoachPlanFeedback.Reason[] all = CoachPlanFeedback.Reason.values();
    int[] words = {R.string.coach_fb_reason_wrong, R.string.coach_fb_reason_already,
        R.string.coach_fb_reason_unclear, R.string.coach_fb_reason_too_hard,
        R.string.coach_fb_reason_too_boring, R.string.coach_fb_reason_not_worth};
    bindFold(block, index, answer, all, words);
    for (int i = 0; i < all.length; i++) {
      RadioButton option = new RadioButton(activity);
      option.setId(View.generateViewId());
      option.setText(words[i]);
      option.setTextSize(13.5f);
      option.setTextColor(ContextCompat.getColor(activity, R.color.white));
      option.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFFFFD27A));
      reasons.addView(option);
      if (answer.getReasons().contains(all[i])) {
        option.setChecked(true);
      }
    }
    reasons.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(RadioGroup group, int checkedId) {
        int at = group.indexOfChild(group.findViewById(checkedId));
        if (at >= 0) {
          feedback.pickReason(index, all[at]);
          save();
        }
      }
    });
    EditText text = block.findViewById(R.id.etFbFocusText);
    bindText(text, answer.getText(), new Typed() {
      @Override
      public void typed(String value) {
        feedback.setFocusText(index, value);
      }
    });
  }

  /** The reasons' header: tapping it folds them to one line naming the picked one, or opens them. */
  private void bindFold(final View block, final int index, CoachPlanFeedback.FocusAnswer answer,
      CoachPlanFeedback.Reason[] all, int[] words) {
    String picked = "";
    for (int i = 0; i < all.length; i++) {
      if (answer.getReasons().contains(all[i])) {
        picked = activity.getString(words[i]);
      }
    }
    Boolean choice = folded.get(index);
    final boolean fold = choice != null ? choice
        : !picked.isEmpty() || (answer.getText() != null && !answer.getText().isEmpty());
    block.findViewById(R.id.llFbWhyBody).setVisibility(fold ? View.GONE : View.VISIBLE);
    TextView pickedView = block.findViewById(R.id.tvFbWhyPicked);
    pickedView.setText(fold ? picked : "");
    ((ImageView) block.findViewById(R.id.ivFbWhyFold))
        .setImageResource(fold ? R.drawable.ic_chevron_down : R.drawable.ic_chevron_up);
    block.findViewById(R.id.llFbWhyHead).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (!fold) {
          ((InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE))
              .hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
        folded.put(index, !fold);
        bindFocus(block, index);
      }
    });
  }

  /**
   * The stars and Send, or once sent and not reopened, the thanks with the stars given. In
   * required mode Send looks off until everything is answered, but a tap still says what is
   * missing rather than doing nothing.
   */
  private void bindRate(boolean open) {
    if (rateCard == null) {
      return;
    }
    TextView title = rateCard.findViewById(R.id.tvFbRateTitle);
    TextView sub = rateCard.findViewById(R.id.tvFbRateSub);
    title.setText(open ? R.string.coach_fb_stars_title : R.string.coach_fb_thanks);
    sub.setText(open ? look.starsSubtitle() : R.string.coach_fb_thanks_sub);
    showStars((LinearLayout) rateCard.findViewById(R.id.llFbStars), open);
    EditText text = rateCard.findViewById(R.id.etFbText);
    text.setVisibility(open ? View.VISIBLE : View.GONE);
    TextView send = rateCard.findViewById(R.id.buFbSend);
    send.setVisibility(open ? View.VISIBLE : View.GONE);
    rateCard.setOnClickListener(open ? null : new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        editing = true;
        show(stored, areas, hero);
      }
    });
    if (!open) {
      return;
    }
    bindText(text, feedback.getText(), new Typed() {
      @Override
      public void typed(String value) {
        feedback.setText(value);
      }
    });
    send.setAlpha(missing().isEmpty() && feedback.isRated() && !sending ? 1f : 0.45f);
    send.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        send();
      }
    });
  }

  private void showStars(LinearLayout row, final boolean open) {
    row.removeAllViews();
    for (int i = 1; i <= STAR_COUNT; i++) {
      final int stars = i;
      TextView star = new TextView(activity);
      star.setText("★");
      star.setTextSize(open ? 36 : 24);
      star.setTextColor(i <= feedback.getStars() ? AMBER : STAR_OFF);
      star.setPadding(dp(open ? 6 : 3), 0, dp(open ? 6 : 3), 0);
      if (open) {
        star.setContentDescription(String.valueOf(i));
        star.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            feedback.rate(stars, System.currentTimeMillis());
            save();
            bindRate(true);
          }
        });
      }
      row.addView(star);
    }
  }

  /** The focuses still unanswered, which only required mode waits for. */
  private List<Integer> missing() {
    List<Integer> missing = new ArrayList<Integer>();
    if (!look.holds()) {
      return missing;
    }
    for (int i = 0; i < focusBlocks.size(); i++) {
      CoachPlanFeedback.FocusAnswer answer = feedback.getFocus(i);
      if (answer == null || answer.getAnswer() == null) {
        missing.add(i);
      }
    }
    return missing;
  }

  private void send() {
    if (sending) {
      return;
    }
    List<Integer> missing = missing();
    if (!missing.isEmpty()) {
      String message;
      if (missing.size() == 1) {
        message = activity.getString(R.string.coach_fb_answer_one, focusTitle(missing.get(0)));
      } else if (missing.size() == 2) {
        message = activity.getString(R.string.coach_fb_answer_two, focusTitle(missing.get(0)),
            focusTitle(missing.get(1)));
      } else {
        message = activity.getResources().getQuantityString(R.plurals.coach_fb_answer_many,
            missing.size(), missing.size());
      }
      Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
      flagged = missing.get(0);
      bindFocus(focusBlocks.get(flagged), flagged);
      scrollTo(focusBlocks.get(flagged));
      return;
    }
    if (!feedback.isRated()) {
      Toast.makeText(activity, R.string.coach_fb_pick_stars, Toast.LENGTH_SHORT).show();
      scrollTo(rateCard);
      return;
    }
    final CoachClient client = CoachClient.configured(activity);
    if (client == null) {
      return;
    }
    sending = true;
    bindRate(true);
    final String id = planId;
    final StoredCoachPlan plan = stored;
    App.INSTANCE.getService().getPlanDrillReps(plan, new DataCallback<List<Integer>>() {
      @Override
      public void onData(final List<Integer> reps) {
        activity.runOnUiThread(new Runnable() {
          @Override
          public void run() {
            for (int i = 0; i < reps.size(); i++) {
              if (reps.get(i) != null) {
                feedback.setDrillReps(i, reps.get(i));
              }
            }
            post(client, id, feedback.toBody(plan.getPlan()));
          }
        });
      }
    });
  }

  private void post(final CoachClient client, final String id, final String body) {
    new Thread(new Runnable() {
      @Override
      public void run() {
        final CoachClient.FeedbackOutcome outcome = client.sendFeedback(id, body);
        activity.runOnUiThread(new Runnable() {
          @Override
          public void run() {
            sending = false;
            if (outcome == CoachClient.FeedbackOutcome.SENT
                || outcome == CoachClient.FeedbackOutcome.GONE) {
              editing = false;
              folded.clear();
              CoachPlanWorker.onRated(activity);
              Toast.makeText(activity, R.string.coach_fb_thanks_toast, Toast.LENGTH_SHORT).show();
              // The rating's own change notice redraws the tab, collapsed to the thanks.
            } else {
              Toast.makeText(activity, R.string.coach_fb_send_failed, Toast.LENGTH_LONG).show();
              if (activity.isFinishing()) {
                return;
              }
              bindRate(true);
            }
          }
        });
      }
    }, "coach-feedback").start();
  }

  /** Under the win line: Yes, A bit, Not really, with what got in the way after the last two. */
  private void showLastWeek() {
    View win = hero.findViewById(R.id.tvAnalysisPlanWin);
    ViewGroup parent = (ViewGroup) win.getParent();
    lastWeek = inflater.inflate(R.layout.coach_fb_last_week, parent, false);
    parent.addView(lastWeek, parent.indexOfChild(win) + 1);
    bindLastWeek();
  }

  private void bindLastWeek() {
    CoachPlanFeedback.LastWeek given = feedback.getLastWeek();
    final CoachPlanFeedback.LastWeek[] all = CoachPlanFeedback.LastWeek.values();
    int picked = given == null ? -1 : given.ordinal();
    choice((LinearLayout) lastWeek.findViewById(R.id.llFbChoice),
        new int[] {R.string.coach_fb_yes, R.string.coach_fb_a_bit, R.string.coach_fb_not_really},
        picked, false, new Picked() {
          @Override
          public void picked(int which) {
            feedback.setLastWeek(all[which]);
            save();
            bindLastWeek();
          }
        });
    EditText text = lastWeek.findViewById(R.id.etFbLastWeekText);
    boolean why = picked > 0;
    text.setVisibility(why ? View.VISIBLE : View.GONE);
    if (why) {
      bindText(text, feedback.getLastWeekText(), new Typed() {
        @Override
        public void typed(String value) {
          feedback.setLastWeekText(value);
        }
      });
    }
  }

  private interface Picked {
    void picked(int which);
  }

  private interface Typed {
    void typed(String value);
  }

  /**
   * The one choice control the questions share: a joined, bordered row of parts, the picked one
   * filled green for the first ("Yes") and amber for the others.
   *
   * @param picked the part picked, or -1
   * @param flag whether to outline it in amber, as a required answer still missing
   */
  private void choice(LinearLayout row, int[] labels, int picked, boolean flag,
      final Picked listener) {
    row.removeAllViews();
    GradientDrawable frame = new GradientDrawable();
    frame.setCornerRadius(dp(10));
    frame.setStroke(dp(flag ? 2 : 1.5f), flag ? AMBER : 0xFF7F939D);
    row.setBackground(frame);
    int pad = dp(1.5f);
    row.setPadding(pad, pad, pad, pad);
    for (int i = 0; i < labels.length; i++) {
      if (i > 0) {
        View divider = new View(activity);
        divider.setBackgroundColor(0xFF7F939D);
        row.addView(divider, new LinearLayout.LayoutParams(dp(1.5f),
            ViewGroup.LayoutParams.MATCH_PARENT));
      }
      final int which = i;
      TextView part = new TextView(activity);
      part.setText(labels[i]);
      part.setGravity(Gravity.CENTER);
      part.setTextSize(14);
      part.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
      part.setPadding(dp(15), dp(8), dp(15), dp(8));
      int fill = 0xFF3A454C;
      int ink = ContextCompat.getColor(activity, R.color.white);
      if (i == picked) {
        fill = i == 0 ? 0xFF3F5A45 : 0xFF5C4A2C;
        ink = i == 0 ? ContextCompat.getColor(activity, R.color.green_soft) : 0xFFFFD27A;
        part.setText((i == 0 ? "✓ " : "") + activity.getString(labels[i]));
      }
      GradientDrawable ground = new GradientDrawable();
      float r = dp(8.5f);
      boolean first = i == 0;
      boolean last = i == labels.length - 1;
      ground.setCornerRadii(new float[] {first ? r : 0, first ? r : 0, last ? r : 0, last ? r : 0,
          last ? r : 0, last ? r : 0, first ? r : 0, first ? r : 0});
      ground.setColor(fill);
      part.setBackground(ground);
      part.setTextColor(ink);
      part.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          listener.picked(which);
        }
      });
      row.addView(part);
    }
  }

  private void bindText(EditText field, String value, final Typed typed) {
    Object old = field.getTag();
    if (old instanceof TextWatcher) {
      field.removeTextChangedListener((TextWatcher) old);
    }
    String now = value == null ? "" : value;
    if (!now.equals(field.getText().toString())) {
      field.setText(now);
    }
    TextWatcher watcher = new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
      }

      @Override
      public void afterTextChanged(Editable s) {
        typed.typed(s.toString());
        save();
      }
    };
    field.addTextChangedListener(watcher);
    field.setTag(watcher);
  }

  private String focusTitle(int index) {
    TextView title = areas.getChildAt(index).findViewById(R.id.tvCoachAreaTitle);
    return title == null ? String.valueOf(index + 1) : title.getText().toString();
  }

  private void noteSeen() {
    if (feedback == null || focusBlocks.isEmpty()) {
      return;
    }
    Rect visible = new Rect();
    boolean changed = false;
    for (int i = 0; i < focusBlocks.size(); i++) {
      View block = focusBlocks.get(i);
      if (block.isShown() && block.getGlobalVisibleRect(visible)) {
        changed |= feedback.seen(i);
      }
    }
    if (changed) {
      save();
    }
  }

  private void save() {
    if (feedback != null && planId != null) {
      CoachBeta.of(activity).saveFeedback(planId, feedback);
    }
  }

  private void scrollTo(View target) {
    int y = 0;
    View view = target;
    ViewParent parent = view.getParent();
    while (parent instanceof View && !(parent instanceof ScrollView)) {
      y += view.getTop();
      view = (View) parent;
      parent = view.getParent();
    }
    if (parent instanceof ScrollView) {
      ((ScrollView) parent).smoothScrollTo(0, Math.max(y - dp(16), 0));
    }
  }

  private static void detach(View view) {
    if (view != null && view.getParent() != null) {
      ((ViewGroup) view.getParent()).removeView(view);
    }
  }

  private int dp(float value) {
    return Math.round(value * activity.getResources().getDisplayMetrics().density);
  }
}
