package com.cube.nanotimer.util.usage;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;

/** Asks the service to delete what it holds under a sharing id, until it has said yes. */
public final class UsageForgetWorker extends Worker {

  public UsageForgetWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
    super(context, parameters);
  }

  @NonNull
  @Override
  public Result doWork() {
    String sharingId = getInputData().getString(UsageSharing.SHARING_ID);
    UsageClient client = UsageClient.configured(getApplicationContext());
    if (sharingId == null || client == null) {
      return Result.success();
    }
    try {
      return client.forget(sharingId) ? Result.success() : Result.retry();
    } catch (IOException e) {
      return Result.retry();
    }
  }
}
