package com.cube.nanotimer.util.coach;

import android.content.Context;
import com.cube.nanotimer.Options;
import com.cube.nanotimer.R;
import com.cube.nanotimer.coach.CoachPayload;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Asks the coach service for a plan. The one place the app talks to the network.
 *
 * <p>The address and token come from {@code coach.url} and {@code coach.token} in
 * {@code local.properties}. A build made without the address has no coach to ask; the token is the
 * owner's own device's.
 *
 * <p>Every request also carries the install's Coach ID and, once Play has shown it one, its
 * purchase. When the coach says that purchase has ended, nothing more is sent: see
 * {@link #accessEnded}.
 */
public final class CoachClient {

  private static final int CONNECT_TIMEOUT_MS = 15_000;
  // A model call takes tens of seconds, and the service answers only once it is written.
  private static final int READ_TIMEOUT_MS = 120_000;

  // No model behind it, so it answers at once or not at all.
  private static final int PRACTICE_READ_TIMEOUT_MS = 15_000;

  private final String planUrl;
  private final String practiceUrl;
  private final String redeemUrl;
  private final String reportUrl;
  private final String token;
  private final String coachId;
  private final CoachAccess access;

  CoachClient(String baseUrl, String token, String coachId, CoachAccess access) {
    String base = baseUrl.replaceAll("/+$", "");
    this.planUrl = base + "/v1/plan";
    this.practiceUrl = base + "/v1/practice";
    this.redeemUrl = base + "/v1/redeem";
    this.reportUrl = base + "/v1/reconstruction-report";
    this.token = token;
    this.coachId = coachId;
    this.access = access;
  }

  /**
   * The client this build was configured with, or null when it was built without a coach. A build
   * with no token is known to the service by its Coach ID alone.
   */
  public static CoachClient configured(Context context) {
    String url = context.getString(R.string.coach_url);
    String token = context.getString(R.string.coach_token);
    return url.isEmpty() ? null
        : new CoachClient(url, token, Options.INSTANCE.getCoachId(), CoachAccess.of(context));
  }

  /**
   * Whether this install has the coach, as far as the app can tell: a build token, a purchase the
   * coach has not ended, or an entered code that still runs. The coach decides at every request;
   * this is what the app offers and shows.
   */
  public boolean hasAccess() {
    return !token.isEmpty() || access.letsIn(System.currentTimeMillis());
  }

  /**
   * Whether the coach has said this install's subscription has ended, after which no summary is
   * sent until Play shows a purchase again. A build with a token or an install with a code is let
   * in by that, so a purchase that ended says nothing about it.
   */
  public boolean accessEnded() {
    return access.hasEnded() && !hasAccess();
  }

  /**
   * Blocks until the service answers or gives up, so never call it on the main thread.
   *
   * @param requestBody what {@link #requestBody} wrote, sent as it was kept: the service knows a
   *     repeated question by the payload's exact text
   */
  public CoachReply requestPlan(String requestBody) {
    if (accessEnded()) {
      return CoachReply.refused(CoachRefusal.NO_ACCESS);
    }
    try {
      Answer answer = post(planUrl, requestBody, READ_TIMEOUT_MS);
      CoachReply reply = answer.body == null ? CoachReply.unreadable()
          : CoachReply.parse(answer.status, answer.body);
      noteRefusal(reply, answer);
      return reply;
    } catch (IOException e) {
      return CoachReply.unreachable();
    }
  }

  /**
   * The standing practice sets for a payload, as the service sent them, or null for any failure:
   * the section keeps what it last had. Blocks, like {@link #requestPlan}.
   */
  public String requestPractice(String requestBody) {
    if (accessEnded()) {
      return null;
    }
    try {
      Answer answer = post(practiceUrl, requestBody, PRACTICE_READ_TIMEOUT_MS);
      if (answer.status == 200) {
        return answer.body;
      }
      if (answer.body != null) {
        noteRefusal(CoachReply.parse(answer.status, answer.body), answer);
      }
      return null;
    } catch (IOException e) {
      return null;
    }
  }

  /**
   * Enters an access code for this install. Blocks, like {@link #requestPlan}.
   *
   * @param code as it was typed: the service reads it whatever its case, spaces or dashes
   */
  public CoachCodeReply redeem(String code) {
    try {
      Answer answer = post(redeemUrl, new JSONObject().put("code", code).toString(),
          PRACTICE_READ_TIMEOUT_MS);
      CoachCodeReply reply = answer.body == null ? CoachCodeReply.unreadable()
          : CoachCodeReply.parse(answer.status, answer.body);
      if (reply.getKind() == CoachCodeReply.Kind.GRANTED) {
        access.codeAccepted(reply.getUntil());
      }
      return reply;
    } catch (IOException e) {
      return CoachCodeReply.unreachable();
    } catch (JSONException e) {
      throw new IllegalStateException(e); // a string always goes into an object
    }
  }

  /** What the service said to a report: its status, and the wait it asked for on a 429. */
  public static final class ReportAnswer {
    public final int status;
    /** 0 when the service asked for no particular wait. */
    public final int retryAfterS;

    ReportAnswer(int status, int retryAfterS) {
      this.status = status;
      this.retryAfterS = retryAfterS;
    }
  }

  /**
   * Sends a reconstruction report, which every install may do, subscriber or not. Blocks, like
   * {@link #requestPlan}.
   *
   * @throws IOException when the service could not be reached, which is worth another try
   */
  public ReportAnswer postReport(String report) throws IOException {
    // A report needs no access, so it carries none: the Coach ID is all the policy says it sends.
    Answer answer = post(reportUrl, report, PRACTICE_READ_TIMEOUT_MS, false);
    int retryAfterS = 0;
    if (answer.body != null) {
      try {
        JSONObject error = new JSONObject(answer.body).optJSONObject("error");
        retryAfterS = error == null ? 0 : error.optInt("retry_after_s", 0);
      } catch (JSONException e) {
        // Not the service's own answer, so no wait was asked for.
      }
    }
    return new ReportAnswer(answer.status, retryAfterS);
  }

  /**
   * Keeps a "no access" against the purchase that was sent, which is what stops the asking. An
   * install nothing lets in has also lost whatever a code gave it.
   */
  private void noteRefusal(CoachReply reply, Answer answer) {
    if (reply.getKind() != CoachReply.Kind.REFUSED) {
      return;
    }
    if (reply.getRefusal() == CoachRefusal.NO_ACCESS) {
      // The coach reads a code's access before a purchase, so it had none to read.
      access.codeEnded();
      access.refused(answer.purchaseSent, System.currentTimeMillis());
    } else if (reply.getRefusal() == CoachRefusal.UNAUTHORIZED) {
      access.codeEnded();
    }
  }

  private static final class Answer {
    final int status;
    final String body;
    /** The purchase the request carried, null when it carried none. */
    final String purchaseSent;

    Answer(int status, String body, String purchaseSent) {
      this.status = status;
      this.body = body;
      this.purchaseSent = purchaseSent;
    }
  }

  private Answer post(String url, String requestBody, int readTimeoutMs) throws IOException {
    return post(url, requestBody, readTimeoutMs, true);
  }

  /** @param withAccess false sends the Coach ID alone, without the build token or the purchase */
  private Answer post(String url, String requestBody, int readTimeoutMs, boolean withAccess)
      throws IOException {
    HttpURLConnection connection = null;
    try {
      byte[] body = requestBody.getBytes(StandardCharsets.UTF_8);
      connection = (HttpURLConnection) new URL(url).openConnection();
      connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
      connection.setReadTimeout(readTimeoutMs);
      connection.setRequestMethod("POST");
      connection.setDoOutput(true);
      connection.setFixedLengthStreamingMode(body.length);
      if (withAccess && !token.isEmpty()) {
        connection.setRequestProperty("Authorization", "Bearer " + token);
      }
      connection.setRequestProperty("Coach-Id", coachId);
      String purchase = withAccess ? access.getPurchaseToken() : null;
      if (!CoachAccess.sendable(purchase)) {
        purchase = null;
      }
      if (purchase != null) {
        connection.setRequestProperty("Purchase-Token", purchase);
      }
      connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
      try (OutputStream out = connection.getOutputStream()) {
        out.write(body);
      }
      int status = connection.getResponseCode();
      InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
      return new Answer(status, in == null ? null : read(in), purchase);
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  /** The payload a request body carries, which is what a plan that answers it is kept beside. */
  static CoachPayload payloadOf(String requestBody) {
    try {
      return CoachPayload.parse(new JSONObject(requestBody).getJSONObject("payload").toString());
    } catch (JSONException e) {
      throw new IllegalArgumentException("Not a coach request: " + e.getMessage(), e);
    }
  }

  /** The stream rides beside the payload, never in it: the model is shown the payload alone. */
  static String requestBody(CoachPayload payload, String stream) {
    try {
      return new JSONObject()
          .put("payload", new JSONObject(payload.toJson()))
          .put("stream", stream)
          .toString();
    } catch (JSONException e) {
      throw new IllegalStateException(e); // toJson wrote it, so it parses
    }
  }

  /**
   * A practice request: the payload, and beside it the cases cleared in a learning drill, which no
   * solve figure holds and which the "learn next" set moves past.
   */
  static String practiceBody(CoachPayload payload, Collection<String> learned) {
    try {
      return new JSONObject()
          .put("payload", new JSONObject(payload.toJson()))
          .put("learned_cases", new JSONArray(learned))
          .toString();
    } catch (JSONException e) {
      throw new IllegalStateException(e); // toJson wrote it, so it parses
    }
  }

  private static String read(InputStream in) throws IOException {
    try (InputStream stream = in) {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      int count;
      while ((count = stream.read(buffer)) != -1) {
        bytes.write(buffer, 0, count);
      }
      return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }
  }
}
