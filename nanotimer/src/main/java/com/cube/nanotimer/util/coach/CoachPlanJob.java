package com.cube.nanotimer.util.coach;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.cube.nanotimer.App;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.coach.PlanSchedule;
import com.cube.nanotimer.gui.AnalysisActivity;
import com.cube.nanotimer.services.db.DataCallback;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.SolveType;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The weekly plan, asked for with no screen. The weekly job, the check at every start of the app
 * and the one that follows a session all run this, and it asks only when {@link PlanSchedule} says
 * a plan may be asked for.
 */
final class CoachPlanJob {

  /** How long a refusal that names no wait keeps the job from asking again. */
  static final long REFUSED_WAIT_MS = 24L * 60 * 60 * 1000;

  /** Longer than the client waits for the service, with the reading of the history on top. */
  private static final long ANSWER_WAIT_MS = 5L * 60 * 1000;

  private static final String PLANS_CHANNEL = "coach_plans";
  private static final String REMINDERS_CHANNEL = "coach_reminders";
  private static final int PLAN_READY_NOTIF_ID = 2;
  private static final int REMINDER_NOTIF_ID = 3;

  enum Outcome {
    /** A plan was saved, or there is nothing to do until something changes. */
    DONE,
    /** The coach could not be reached or was busy: worth another try soon. */
    RETRY
  }

  private CoachPlanJob() {
  }

  /**
   * Blocks until the ask is answered, so never call it on the main thread.
   *
   * @param debugSolveTypeId a solve type to ask for now, whatever the schedule says and whether or
   *     not the solver agreed to weekly plans, or -1 for the weekly plan
   * @param onScreen whether the solver is looking at the Plan tab, where a first plan does not
   *     wait for the session to end
   */
  static Outcome run(Context context, int debugSolveTypeId, boolean onScreen)
      throws InterruptedException {
    // The process may have been started for this alone, with no screen to have set either.
    Utils.updateContextWithPrefsLocale(context);
    App.INSTANCE.setApplicationContext(context);
    CoachClient client = CoachClient.configured(context);
    if (client == null) {
      return Outcome.DONE;
    }
    boolean debug = debugSolveTypeId != -1;
    if (!debug && client.accessEnded()) {
      return Outcome.DONE; // no plan is coming, so no reminder of the solves one needs either
    }
    if (CoachBeta.of(context).isStale(System.currentTimeMillis())) {
      client.checkBeta();
    }
    boolean owesRating = !debug && client.owesRating();
    SolveType solveType = debug ? eligible(debugSolveTypeId) : flagged();
    if (solveType == null) {
      return Outcome.DONE;
    }
    long now = System.currentTimeMillis();
    long lastPlanAt = CoachPlans.lastPlanAt(now);
    if (owesRating) { // rating the plan runs the job again
      remind(context, solveType, schedule(solveType, lastPlanAt, now), now, true);
      return Outcome.DONE;
    }
    if (!debug) {
      if (held(Options.INSTANCE.getCoachAskAgainAt(), now)) {
        return Outcome.DONE;
      }
      PlanSchedule schedule = schedule(solveType, lastPlanAt, now);
      if (!schedule.mayAsk(onScreen)) {
        if (schedule.getState() == PlanSchedule.State.QUIET) {
          CoachPlanWorker.runIn(context, schedule.getQuietUntil() - now); // the session is still on
        }
        remind(context, solveType, schedule, now, false);
        return Outcome.DONE;
      }
    }
    Answer answer = new Answer();
    boolean started = CoachAsk.ask(client, solveType, CubeMethod.CFOP, lastPlanAt, answer);
    if (!answer.latch.await(ANSWER_WAIT_MS, TimeUnit.MILLISECONDS)) {
      return Outcome.RETRY;
    }
    if (answer.failure == null) {
      if (started) { // a plan somebody else asked for is theirs to announce
        notifyPlanReady(context, solveType);
      }
      CoachPlanWorker.scheduleAfterPlan(context, true);
      return Outcome.DONE;
    }
    long wait = waitAfter(answer.failure);
    if (wait == 0) {
      return Outcome.RETRY;
    }
    if (!debug) {
      Options.INSTANCE.setCoachAskAgainAt(System.currentTimeMillis() + wait);
    }
    return Outcome.DONE;
  }

  /**
   * How long to leave the coach alone after an ask that brought no plan, 0 to try again soon. A
   * refusal is not asked again at every start of the app: it would be refused, or bought, again.
   */
  static long waitAfter(CoachReply failure) {
    if (failure.getKind() == CoachReply.Kind.UNREACHABLE) {
      return 0;
    }
    if (failure.getKind() == CoachReply.Kind.REFUSED) {
      switch (failure.getRefusal()) {
        case RATE_LIMITED:
        case UPSTREAM_UNAVAILABLE:
        case STORE_UNAVAILABLE:
        case PURCHASE_UNAVAILABLE:
          return 0; // the service's trouble, not this request's
        default:
          break;
      }
    }
    int seconds = failure.getRetryAfterSeconds();
    return seconds > 0 ? seconds * 1000L : REFUSED_WAIT_MS;
  }

  /** Whether a refusal still keeps the job from asking. One over a week ahead is a clock set back. */
  static boolean held(long askAgainAt, long now) {
    return askAgainAt > now && askAgainAt - now <= PlanSchedule.PERIOD_MS;
  }

  private static SolveType flagged() throws InterruptedException {
    return CoachPlans.flagged(solveTypes());
  }

  private static SolveType eligible(int solveTypeId) throws InterruptedException {
    for (SolveType solveType : solveTypes()) {
      if (solveType.getId() == solveTypeId) {
        return CoachPlans.eligible(solveType) ? solveType : null;
      }
    }
    return null;
  }

  private static List<SolveType> solveTypes() throws InterruptedException {
    Held<List<SolveType>> solveTypes = new Held<List<SolveType>>();
    App.INSTANCE.getService().getSolveTypes(CubeType.THREE_BY_THREE, solveTypes);
    return solveTypes.get();
  }

  private static PlanSchedule schedule(SolveType solveType, long lastPlanAt, long now)
      throws InterruptedException {
    Held<PlanSchedule> schedule = new Held<PlanSchedule>();
    App.INSTANCE.getService().getPlanSchedule(solveType, CubeMethod.CFOP, lastPlanAt, now,
        schedule);
    return schedule.get();
  }

  /**
   * Tells the solver once per plan, the day before the next one is due, how many solves it still
   * needs, or that it waits for the last plan's rating. A session holds it back, so the job comes
   * back when the session is over.
   */
  private static void remind(Context context, SolveType solveType, PlanSchedule schedule,
      long now, boolean owesRating) {
    long lastNudgeAt = Options.INSTANCE.getCoachLastNudgeAt();
    if (!Options.INSTANCE.isCoachNudgeOn() || !schedule.nudgeOwed(lastNudgeAt, owesRating)) {
      return;
    }
    if (!schedule.nudgeDue(lastNudgeAt, owesRating)) {
      CoachPlanWorker.runIn(context, schedule.getQuietUntil() - now);
      return;
    }
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
      return; // not counted as told: it is shown if notifications are allowed later
    }
    Options.INSTANCE.setCoachLastNudgeAt(now);
    int needed = schedule.getSolvesNeeded();
    String type = Utils.toSolveTypeLocalizedName(context, solveType.getName());
    Resources res = context.getResources();
    int days = schedule.getState() == PlanSchedule.State.NOT_DUE
        ? calendarDaysBetween(now, schedule.getDueAt()) : -1;
    String title;
    String text;
    if (days == 0 || days == 1) {
      title = context.getString(days == 0 ? R.string.coach_reminder_due_today
          : R.string.coach_reminder_due_tomorrow);
      if (!owesRating) {
        text = res.getQuantityString(R.plurals.coach_reminder_on_time, needed, needed, type);
      } else if (needed > 0) {
        text = res.getQuantityString(R.plurals.coach_reminder_rate_on_time, needed, needed, type);
      } else {
        text = context.getString(R.string.coach_reminder_rate_first);
      }
    } else if (owesRating) {
      title = context.getString(R.string.coach_reminder_held_title);
      text = needed > 0
          ? res.getQuantityString(R.plurals.coach_reminder_rate_and_solve, needed, needed, type)
          : context.getString(R.string.coach_fb_due_held_body);
    } else {
      // Past due (a session held the reminder back), or a clock change: no day to name.
      title = res.getQuantityString(R.plurals.coach_reminder_title, needed, needed);
      text = context.getString(R.string.coach_reminder_text, type);
    }
    notify(context, REMINDERS_CHANNEL, R.string.coach_channel_reminders, REMINDER_NOTIF_ID,
        title, text, solveType);
  }

  /** How many midnights lie between two times, in the device's time zone. */
  private static int calendarDaysBetween(long from, long to) {
    Calendar day = Calendar.getInstance();
    day.setTimeInMillis(from);
    int count = 0;
    while (true) {
      day.set(Calendar.HOUR_OF_DAY, 0);
      day.set(Calendar.MINUTE, 0);
      day.set(Calendar.SECOND, 0);
      day.set(Calendar.MILLISECOND, 0);
      day.add(Calendar.DAY_OF_MONTH, 1);
      if (day.getTimeInMillis() > to || count > 7) {
        return count;
      }
      count++;
    }
  }

  private static void notifyPlanReady(Context context, SolveType solveType) {
    notify(context, PLANS_CHANNEL, R.string.coach_channel_plans, PLAN_READY_NOTIF_ID,
        context.getString(R.string.coach_plan_ready_title),
        context.getString(R.string.coach_plan_ready_text), solveType);
  }

  /** Opens the Plan tab on top of whatever the app is showing, rather than restarting its task. */
  private static void notify(Context context, String channel, int channelName, int id,
      String title, String text, SolveType solveType) {
    NotificationManager manager =
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      manager.createNotificationChannel(new NotificationChannel(channel,
          context.getString(channelName), NotificationManager.IMPORTANCE_DEFAULT));
    }
    Intent open = new Intent(context, AnalysisActivity.class)
        .putExtra(AnalysisActivity.EXTRA_SOLVE_TYPE, solveType)
        .putExtra(AnalysisActivity.EXTRA_TAB, AnalysisActivity.TAB_PLAN)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    manager.notify(id, new NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_stat_coach)
        .setColor(ContextCompat.getColor(context, R.color.cube_blue))
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
        .setContentIntent(PendingIntent.getActivity(context, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
        .setAutoCancel(true)
        .build());
  }

  /** A service read, waited for. */
  private static final class Held<T> extends DataCallback<T> {
    private final CountDownLatch latch = new CountDownLatch(1);
    private T value;

    @Override
    public void onData(T data) {
      value = data;
      latch.countDown();
    }

    T get() throws InterruptedException {
      latch.await();
      return value;
    }
  }

  private static final class Answer implements CoachAsk.Listener {
    private final CountDownLatch latch = new CountDownLatch(1);
    private CoachReply failure;

    @Override
    public void onAnswer(CoachReply failure, int solveCount) {
      this.failure = failure;
      latch.countDown();
    }
  }
}
