package com.cube.nanotimer.util.coach;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.cube.nanotimer.Options;
import com.cube.nanotimer.coach.PlanSchedule;
import com.cube.nanotimer.vo.SolveType;

import java.util.concurrent.TimeUnit;

/**
 * When {@link CoachPlanJob} runs: once a week, on the day the next plan is due, at every start of
 * the app, and once a session on the plans' solve type has gone quiet. The last two share one
 * piece of unique work, so a solve pushes back whatever was waiting.
 */
public final class CoachPlanWorker extends Worker {

  private static final String WEEKLY = "coach_plan_weekly";
  private static final String ONCE = "coach_plan_once";
  private static final String REMINDER = "coach_plan_reminder";
  private static final String DUE = "coach_plan_due";
  private static final String DEBUG_SOLVE_TYPE = "debug_solve_type";
  private static final String ON_SCREEN = "on_screen";

  /** After this many tries in a row the job waits for its next occasion. */
  private static final int MAX_ATTEMPTS = 5;
  /** Past the quiet period, so the job does not find the session still on by a few milliseconds. */
  private static final long QUIET_MARGIN_MS = 5_000;

  public CoachPlanWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
    super(context, parameters);
  }

  @NonNull
  @Override
  public Result doWork() {
    try {
      CoachPlanJob.Outcome outcome = CoachPlanJob.run(getApplicationContext(),
          getInputData().getInt(DEBUG_SOLVE_TYPE, -1), getInputData().getBoolean(ON_SCREEN, false));
      return outcome == CoachPlanJob.Outcome.RETRY && getRunAttemptCount() + 1 < MAX_ATTEMPTS
          ? Result.retry() : Result.success();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Result.retry();
    }
  }

  /** Keeps the weekly job in place and checks whether a plan is due now. */
  public static void onAppStart(Context context) {
    if (!Options.INSTANCE.isCoachPlansOn() || CoachClient.configured(context) == null) {
      return;
    }
    WorkManager manager = WorkManager.getInstance(context.getApplicationContext());
    manager.enqueueUniquePeriodicWork(WEEKLY, ExistingPeriodicWorkPolicy.KEEP,
        new PeriodicWorkRequest.Builder(CoachPlanWorker.class, 7, TimeUnit.DAYS)
            .setConstraints(online())
            .build());
    check(context);
    scheduleAfterPlan(context, false);
  }

  /**
   * Runs the job again on the two days no other run is sure to fall on: the day the solver is to
   * be reminded of the solves the next plan needs, and the day that plan is due, which a solver
   * who already has the solves may spend away from the app.
   *
   * @param newPlan whether a plan was just written, which moves both days
   */
  static void scheduleAfterPlan(Context context, boolean newPlan) {
    long now = System.currentTimeMillis();
    long lastPlanAt = CoachPlans.lastPlanAt(now);
    if (lastPlanAt == 0) {
      return; // no plan yet: the first one waits for solves, not for a day
    }
    runAt(context, REMINDER, lastPlanAt + PlanSchedule.NUDGE_AFTER_MS - now, newPlan);
    runAt(context, DUE, lastPlanAt + PlanSchedule.PERIOD_MS - now, newPlan);
  }

  private static void runAt(Context context, String name, long wait, boolean replace) {
    if (wait <= 0) {
      return; // that day has come: every run of the job looks at it from here on
    }
    WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(name,
        replace ? ExistingWorkPolicy.REPLACE : ExistingWorkPolicy.KEEP, once(wait, Data.EMPTY));
  }

  /**
   * Checks whether a plan is due now. A check already waiting for a session to end is kept: it is
   * the same check, at a better time.
   */
  public static void check(Context context) {
    WorkManager.getInstance(context.getApplicationContext())
        .enqueueUniqueWork(ONCE, ExistingWorkPolicy.KEEP, once(0, Data.EMPTY));
  }

  /**
   * A beta member rated the plan the next one waited on, so whatever wait the service's refusal
   * set is over.
   */
  public static void onRated(Context context) {
    Options.INSTANCE.setCoachAskAgainAt(0);
    check(context);
  }

  /** Checks now, for a solver who is looking at the Plan tab: a first plan does not wait. */
  public static void checkFromScreen(Context context) {
    Data onScreen = new Data.Builder().putBoolean(ON_SCREEN, true).build();
    WorkManager.getInstance(context.getApplicationContext())
        .enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, once(0, onScreen));
  }

  /** Checks again once the session has gone quiet, each solve of the plans' type pushing it back. */
  public static void onSolveSaved(Context context, SolveType solveType) {
    if (!Options.INSTANCE.isCoachPlansOn()
        || solveType.getId() != Options.INSTANCE.getCoachPlansSolveType()) {
      return;
    }
    long now = System.currentTimeMillis();
    long lastPlanAt = CoachPlans.lastPlanAt(now);
    if (lastPlanAt != 0 && now + PlanSchedule.QUIET_MS < lastPlanAt + PlanSchedule.PERIOD_MS) {
      return; // no plan can be due by then
    }
    runIn(context, PlanSchedule.QUIET_MS);
  }

  /** Asks for the solve type now, whatever the schedule says: debug and owner token builds only. */
  public static void askNow(Context context, SolveType solveType) {
    Data solveTypeId = new Data.Builder().putInt(DEBUG_SOLVE_TYPE, solveType.getId()).build();
    WorkManager.getInstance(context.getApplicationContext())
        .enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, once(0, solveTypeId));
  }

  /** Runs the job once the wait is over, in place of any run still waiting. */
  static void runIn(Context context, long waitMs) {
    WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(ONCE,
        ExistingWorkPolicy.REPLACE, once(waitMs + QUIET_MARGIN_MS, Data.EMPTY));
  }

  private static OneTimeWorkRequest once(long delayMs, Data input) {
    return new OneTimeWorkRequest.Builder(CoachPlanWorker.class)
        .setConstraints(online())
        .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
        .setInputData(input)
        .build();
  }

  private static Constraints online() {
    return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
  }
}
