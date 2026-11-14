package com.cube.nanotimer.util.usage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * One send of algorithm usage votes to the coach service ({@code POST /v1/usage}), and how its
 * answer is read. Pure, so the body, its hash and every answer are unit-tested.
 *
 * <p>The Play Integrity token is bound to the body by its {@link #requestHash}, the hex SHA-256 of
 * the body's exact UTF-8 bytes, so the body is written once and those very bytes are sent.
 */
public final class UsageReport {

  static final int USAGE_VERSION = 1;
  /** The service takes at most this many cases a send; the rest go the next day. */
  static final int MAX_VOTES = 150;

  /** What a send's answer means for the votes it carried. */
  public enum Kind {
    /** Received: the cases in {@link #getTaken} are reported, the others were dropped. */
    TAKEN,
    /** Refused for this send (integrity failed, a body the service will not read): not retried. */
    DROPPED,
    /** The service's trouble or a limit: the same send is tried again later. */
    RETRY
  }

  private final Map<String, String> votes;
  private final String body;

  private UsageReport(Map<String, String> votes, String body) {
    this.votes = votes;
    this.body = body;
  }

  /** @param votes case to algorithm, as {@code AlgorithmVotes.changedSince} gave them */
  public static UsageReport of(Map<String, String> votes) {
    Map<String, String> sent = new LinkedHashMap<String, String>();
    for (Map.Entry<String, String> vote : new TreeMap<String, String>(votes).entrySet()) {
      if (sent.size() == MAX_VOTES) {
        break;
      }
      sent.put(vote.getKey(), vote.getValue());
    }
    // Written by hand: a JSONObject's key order differs between Android and the JVM.
    StringBuilder body = new StringBuilder("{\"usage_version\":").append(USAGE_VERSION)
        .append(",\"votes\":{");
    boolean first = true;
    for (Map.Entry<String, String> vote : sent.entrySet()) {
      body.append(first ? "" : ",").append(JSONObject.quote(vote.getKey())).append(':')
          .append(JSONObject.quote(vote.getValue()));
      first = false;
    }
    body.append("}}");
    return new UsageReport(Collections.unmodifiableMap(sent), body.toString());
  }

  public Map<String, String> getVotes() {
    return votes;
  }

  public String getBody() {
    return body;
  }

  public byte[] getBodyBytes() {
    return body.getBytes(StandardCharsets.UTF_8);
  }

  /** The integrity token's request hash: lowercase hex SHA-256 of the body's UTF-8 bytes. */
  public String requestHash() {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(getBodyBytes());
      StringBuilder hex = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        hex.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e); // every Java and Android has SHA-256
    }
  }

  /** How the service's answer to this send is read. */
  public Answer read(int status, String answerBody) {
    if (status == 202 || status == 200) {
      Map<String, String> taken = new LinkedHashMap<String, String>();
      for (String caseCode : takenCases(answerBody)) {
        if (votes.containsKey(caseCode)) {
          taken.put(caseCode, votes.get(caseCode));
        }
      }
      return new Answer(Kind.TAKEN, taken);
    }
    return new Answer(kindOfFailure(status), Collections.<String, String>emptyMap());
  }

  /** A refused send: only a limit or the service's own trouble is worth sending again. */
  static Kind kindOfFailure(int status) {
    return status == 429 || status >= 500 ? Kind.RETRY : Kind.DROPPED;
  }

  /** How the answer to {@code POST /v1/usage/forget} is read: done, or tried again later. */
  static boolean forgetDone(int status) {
    return status >= 200 && status < 300 || kindOfFailure(status) == Kind.DROPPED;
  }

  private static List<String> takenCases(String answerBody) {
    List<String> taken = new ArrayList<String>();
    if (answerBody == null) {
      return taken;
    }
    try {
      JSONArray cases = new JSONObject(answerBody).optJSONArray("taken");
      for (int i = 0; cases != null && i < cases.length(); i++) {
        taken.add(cases.getString(i));
      }
    } catch (JSONException e) {
      taken.clear(); // nothing marked: the same votes go again on the next send
    }
    return taken;
  }

  /** The answer to one send. */
  public static final class Answer {
    private final Kind kind;
    private final Map<String, String> taken;

    Answer(Kind kind, Map<String, String> taken) {
      this.kind = kind;
      this.taken = taken;
    }

    public Kind getKind() {
      return kind;
    }

    /** The votes the service took, to mark as reported. Empty unless {@link Kind#TAKEN}. */
    public Map<String, String> getTaken() {
      return taken;
    }
  }
}
