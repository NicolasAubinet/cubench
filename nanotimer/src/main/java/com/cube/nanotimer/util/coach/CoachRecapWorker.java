package com.cube.nanotimer.util.coach;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.cube.nanotimer.App;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.coach.PlanSchedule;
import com.cube.nanotimer.coach.StoredCoachPlan;
import com.cube.nanotimer.session.CoachDay;
import com.cube.nanotimer.util.helper.Utils;
import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveType;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * The evening recap: once a local day, from the time the solver chose (19:00 unless changed), and
 * only once the session is over, no solve of the plan's type for {@link PlanSchedule#QUIET_MS}. A
 * day with no solve of that type gets none, and a recap still held at midnight is dropped. Coach
 * subscribers with weekly plans and a plan only. Computed on the phone: nothing is sent anywhere.
 */
public final class CoachRecapWorker extends Worker {

  private static final String NAME = "coach_recap";
  private static final String CHANNEL = "coach_recap";
  private static final int NOTIF_ID = 4;
  private static final long QUIET_MARGIN_MS = 5_000;

  public CoachRecapWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
    super(context, parameters);
  }

  @NonNull
  @Override
  public Result doWork() {
    Context context = getApplicationContext();
    try {
      long wait = run(context);
      if (wait > 0) {
        enqueue(context, wait, ExistingWorkPolicy.REPLACE);
      } else {
        schedule(context, false);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Result.retry();
    }
    return Result.success();
  }

  /**
   * Puts the next run at the next recap time, or takes it off when there is to be no recap. Past
   * today's time with no recap yet, it runs now: the run that was due may have been lost.
   */
  public static void schedule(Context context) {
    schedule(context, true);
  }

  private static void schedule(Context context, boolean catchUp) {
    if (!wanted(context)) {
      WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(NAME);
      return;
    }
    long now = System.currentTimeMillis();
    long at = nextRunAt(now, Options.INSTANCE.getCoachRecapMinutes(),
        Options.INSTANCE.getCoachLastRecapDay(), catchUp);
    enqueue(context, at - now, ExistingWorkPolicy.REPLACE);
  }

  /**
   * A solve of the plan's type after the recap time, on a day with no recap yet, brings the recap
   * to the end of its session, each solve pushing it back.
   */
  public static void onSolveSaved(Context context, SolveType solveType) {
    if (!wanted(context) || solveType.getId() != Options.INSTANCE.getCoachPlansSolveType()) {
      return;
    }
    long now = System.currentTimeMillis();
    if (Options.INSTANCE.getCoachLastRecapDay() == CoachDays.dayKey(now)
        || now < recapAt(now, Options.INSTANCE.getCoachRecapMinutes())) {
      return; // the run already waiting at the recap time reads the session then
    }
    enqueue(context, PlanSchedule.QUIET_MS + QUIET_MARGIN_MS, ExistingWorkPolicy.REPLACE);
  }

  /**
   * When to run next: at today's recap time if today has had no recap and the time is ahead, or
   * now if it has passed and {@code catchUp}; else at tomorrow's.
   */
  static long nextRunAt(long now, int recapMinutes, int lastRecapDay, boolean catchUp) {
    long today = recapAt(now, recapMinutes);
    if (lastRecapDay != CoachDays.dayKey(now) && (now < today || catchUp)) {
      return Math.max(today, now);
    }
    Calendar tomorrow = Calendar.getInstance();
    tomorrow.setTimeInMillis(CoachDays.dayStart(now));
    tomorrow.add(Calendar.DAY_OF_MONTH, 1);
    return recapAt(tomorrow.getTimeInMillis(), recapMinutes);
  }

  /** The recap time on the local day holding the given time. */
  static long recapAt(long time, int recapMinutes) {
    Calendar at = Calendar.getInstance();
    at.setTimeInMillis(CoachDays.dayStart(time));
    at.set(Calendar.HOUR_OF_DAY, recapMinutes / 60);
    at.set(Calendar.MINUTE, recapMinutes % 60);
    return at.getTimeInMillis();
  }

  /**
   * Sends today's recap if it is due. Blocks on the service, so never on the main thread.
   *
   * @return how long to wait for a session to end before trying again, 0 when done for today
   */
  static long run(Context context) throws InterruptedException {
    Utils.updateContextWithPrefsLocale(context);
    App.INSTANCE.setApplicationContext(context);
    long now = System.currentTimeMillis();
    if (!wanted(context) || Options.INSTANCE.getCoachLastRecapDay() == CoachDays.dayKey(now)
        || now < recapAt(now, Options.INSTANCE.getCoachRecapMinutes())) {
      return 0;
    }
    SolveType solveType = CoachPlanJob.flagged();
    if (solveType == null) {
      return 0;
    }
    CoachPlanJob.Held<StoredCoachPlan> plan = new CoachPlanJob.Held<StoredCoachPlan>();
    App.INSTANCE.getService().getCoachPlan(solveType, plan);
    StoredCoachPlan stored = plan.get();
    if (stored == null) {
      return 0;
    }
    PlanSchedule schedule =
        CoachPlanJob.schedule(solveType, CoachPlans.lastPlanAt(now), now);
    long quietUntil = schedule.getQuietUntil();
    if (quietUntil > now) {
      return quietUntil - now + QUIET_MARGIN_MS;
    }
    CoachPlanJob.Held<CoachDay> day = new CoachPlanJob.Held<CoachDay>();
    CoachDays.read(solveType, stored, CoachDays.dayStart(now), day);
    CoachDayText text = new CoachDayText(context);
    String title = text.recapTitle(day.get());
    if (title == null || !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
      return 0; // no solve today, or nowhere to show it
    }
    Options.INSTANCE.setCoachLastRecapDay(CoachDays.dayKey(now));
    CoachPlanJob.notify(context, CHANNEL, R.string.coach_channel_recap, NOTIF_ID, title,
        text.recapText(day.get()), solveType);
    return 0;
  }

  private static boolean wanted(Context context) {
    if (!Options.INSTANCE.isCoachPlansOn() || !Options.INSTANCE.isCoachRecapOn()) {
      return false;
    }
    CoachClient client = CoachClient.configured(context);
    return client != null && client.hasAccess();
  }

  private static void enqueue(Context context, long delayMs, ExistingWorkPolicy policy) {
    WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(NAME, policy,
        new OneTimeWorkRequest.Builder(CoachRecapWorker.class)
            .setInitialDelay(Math.max(delayMs, 0), TimeUnit.MILLISECONDS)
            .build());
  }
}
