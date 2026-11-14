package com.cube.nanotimer.util.usage;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.cube.nanotimer.Options;
import com.cube.nanotimer.cube.SmartCubeGate;

import java.util.concurrent.TimeUnit;

/**
 * Algorithm usage sharing, as the rest of the app sees it: when the daily send runs, and what
 * turning it off does.
 *
 * <p>Smart-cube users only: nothing is scheduled, and so nothing is ever sent, until a cube has
 * connected. On by default; the smart-cube settings turn it off.
 */
public final class UsageSharing {

  private static final String DAILY = "usage_daily";
  private static final String FORGET_PREFIX = "usage_forget_";
  static final String SHARING_ID = "sharing_id";
  private static final long BACKOFF_MINUTES = 30;

  private UsageSharing() {
  }

  /** Whether this install shares: a smart-cube user, sharing on, in a build that has the coach. */
  public static boolean isActive(Context context) {
    return isOffered(context) && Options.INSTANCE.isUsageSharingOn()
        && Options.INSTANCE.getSmartCubeLastId() != null;
  }

  /** Whether this build can share at all, which is when the settings show the switch. */
  public static boolean isOffered(Context context) {
    return SmartCubeGate.ENABLED && UsageClient.configured(context) != null;
  }

  /** Keeps the daily send in place while sharing is active. At app start and on a connection. */
  public static void schedule(Context context) {
    if (!isActive(context)) {
      return;
    }
    WorkManager.getInstance(context.getApplicationContext()).enqueueUniquePeriodicWork(DAILY,
        ExistingPeriodicWorkPolicy.KEEP,
        new PeriodicWorkRequest.Builder(UsageWorker.class, 1, TimeUnit.DAYS)
            .setConstraints(online())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build());
  }

  /** The switch was turned on again: a new sharing id starts with the next send. */
  public static void turnedOn(Context context) {
    schedule(context);
  }

  /**
   * The switch was turned off: the daily send stops, the service is asked to delete what it holds
   * under this install's sharing id (tried until it goes through), and the id is forgotten here.
   */
  public static void turnedOff(Context context) {
    WorkManager manager = WorkManager.getInstance(context.getApplicationContext());
    manager.cancelUniqueWork(DAILY);
    String sharingId = Options.INSTANCE.peekUsageSharingId();
    Options.INSTANCE.clearUsageSharing();
    if (sharingId == null) {
      return; // nothing was ever sent under one
    }
    // One piece of work per id: turning it on and off again must not cancel an earlier id's forget.
    manager.enqueueUniqueWork(FORGET_PREFIX + sharingId, ExistingWorkPolicy.KEEP,
        new OneTimeWorkRequest.Builder(UsageForgetWorker.class)
            .setConstraints(online())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .setInputData(new Data.Builder().putString(SHARING_ID, sharingId).build())
            .build());
  }

  private static Constraints online() {
    return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
  }
}
