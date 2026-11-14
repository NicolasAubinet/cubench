package com.cube.nanotimer.util.usage;

import android.content.Context;

import com.cube.nanotimer.R;
import com.google.android.gms.tasks.Tasks;
import com.google.android.play.core.integrity.IntegrityManagerFactory;
import com.google.android.play.core.integrity.StandardIntegrityManager;
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest;
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider;
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Sends algorithm usage votes to the coach service, and asks it to forget them.
 *
 * <p>Kept apart from {@code CoachClient} on purpose: these requests carry the sharing id and an
 * integrity token, never the Coach ID, a build token or a purchase, so a vote is linked to nothing.
 * The address is the coach's ({@code coach.url}); the Play Integrity cloud project number comes
 * from {@code integrity.project} in {@code local.properties}. A build without either sends nothing.
 */
final class UsageClient {

  private static final int CONNECT_TIMEOUT_MS = 15_000;
  private static final int READ_TIMEOUT_MS = 30_000;
  private static final long INTEGRITY_WAIT_S = 60;

  private final Context context;
  private final String usageUrl;
  private final String forgetUrl;
  private final String sharesUrl;
  private final long cloudProject;

  private UsageClient(Context context, String baseUrl, long cloudProject) {
    this.context = context.getApplicationContext();
    String base = baseUrl.replaceAll("/+$", "");
    this.usageUrl = base + "/v1/usage";
    this.forgetUrl = base + "/v1/usage/forget";
    this.sharesUrl = base + "/v1/usage/shares";
    this.cloudProject = cloudProject;
  }

  /**
   * The published usage snapshot, with nothing sent but the request itself. Blocks.
   *
   * @return its text, or null when the service answered with anything but 200
   */
  String shares() throws IOException {
    HttpURLConnection connection = (HttpURLConnection) new URL(sharesUrl).openConnection();
    try {
      connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
      connection.setReadTimeout(READ_TIMEOUT_MS);
      return connection.getResponseCode() == 200
          ? UsageShares.read(connection.getInputStream()) : null;
    } finally {
      connection.disconnect();
    }
  }

  /** The client this build can send with, or null when it has no coach address. */
  static UsageClient configured(Context context) {
    String url = context.getString(R.string.coach_url);
    if (url.isEmpty()) {
      return null;
    }
    long project;
    try {
      project = Long.parseLong(context.getString(R.string.usage_integrity_project));
    } catch (NumberFormatException e) {
      project = 0;
    }
    return new UsageClient(context, url, project);
  }

  /** Whether a send can carry an integrity token; forgetting needs none. */
  boolean canSend() {
    return cloudProject != 0;
  }

  /**
   * Blocks, so never on the main thread.
   *
   * @return the answer, or null when no integrity token could be had (no Play, no network):
   *     the send waits for the next day
   * @throws IOException when the service could not be reached
   */
  UsageReport.Answer send(UsageReport report, String sharingId)
      throws IOException, InterruptedException {
    String token = integrityToken(report.requestHash());
    if (token == null) {
      return null;
    }
    HttpURLConnection connection = open(usageUrl, sharingId);
    try {
      connection.setRequestProperty("Integrity-Token", token);
      int status = write(connection, report.getBodyBytes());
      return report.read(status, readBody(connection, status));
    } finally {
      connection.disconnect();
    }
  }

  /** @return whether the forget is done with: taken, or refused for good */
  boolean forget(String sharingId) throws IOException {
    HttpURLConnection connection = open(forgetUrl, sharingId);
    try {
      return UsageReport.forgetDone(write(connection, new byte[0]));
    } finally {
      connection.disconnect();
    }
  }

  private String integrityToken(String requestHash) throws InterruptedException {
    try {
      StandardIntegrityManager manager = IntegrityManagerFactory.createStandard(context);
      StandardIntegrityTokenProvider provider = Tasks.await(manager.prepareIntegrityToken(
          PrepareIntegrityTokenRequest.builder().setCloudProjectNumber(cloudProject).build()),
          INTEGRITY_WAIT_S, TimeUnit.SECONDS);
      return Tasks.await(provider.request(StandardIntegrityTokenRequest.builder()
          .setRequestHash(requestHash).build()), INTEGRITY_WAIT_S, TimeUnit.SECONDS).token();
    } catch (ExecutionException | TimeoutException | RuntimeException e) {
      return null;
    }
  }

  private static HttpURLConnection open(String url, String sharingId) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
    connection.setReadTimeout(READ_TIMEOUT_MS);
    connection.setRequestMethod("POST");
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
    connection.setRequestProperty("Sharing-Id", sharingId);
    return connection;
  }

  private static int write(HttpURLConnection connection, byte[] body) throws IOException {
    connection.setFixedLengthStreamingMode(body.length);
    try (OutputStream out = connection.getOutputStream()) {
      out.write(body);
    }
    return connection.getResponseCode();
  }

  private static String readBody(HttpURLConnection connection, int status) throws IOException {
    InputStream in = status < 400 ? connection.getInputStream() : connection.getErrorStream();
    if (in == null) {
      return null;
    }
    try (InputStream stream = in) {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      for (int read; (read = stream.read(buffer)) != -1; ) {
        bytes.write(buffer, 0, read);
      }
      return bytes.toString(StandardCharsets.UTF_8.name());
    }
  }
}
